package com.example.expensetracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Txn(
    val id: Long,
    val sender: String,
    val direction: String,   // "DEBIT" or "CREDIT"
    val amountPaise: Long,
    val payee: String,       // raw text from the SMS
    val name: String,        // readable name (your own name, a built-in one, or the raw payee)
    val upiRef: String,
    val timeMillis: Long,
    val body: String,
    val sortedPaise: Long,   // how much of this payment is already assigned to categories
    val weatherCode: Int?,   // weather at the time of payment, saved once (null = not fetched yet)
    val tempC: Float?
)

/** Total spent on one item (or a whole category when item is null) over a period. */
data class ItemTotal(val catKey: String, val item: String?, val paise: Long, val count: Int)

/** A payee and how often you paid it, for the nickname list. */
data class PayeeStat(val payee: String, val count: Int, val debitPaise: Long, val creditPaise: Long)

/** One sorted piece with its payment's details, for the lists behind dashboard charts. */
data class SplitRow(val txnId: Long, val payee: String, val time: Long, val catKey: String, val item: String?, val paise: Long)

/** One piece of a payment assigned to a category (and optionally an item). */
data class Split(val id: Long, val catKey: String, val item: String?, val paise: Long)

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "expense.db", null, 5) {

    private fun createTxnTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE txns(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                dedupe TEXT NOT NULL UNIQUE,
                sender TEXT NOT NULL,
                direction TEXT NOT NULL,
                amount_paise INTEGER NOT NULL,
                payee TEXT NOT NULL,
                upi_ref TEXT NOT NULL,
                time_millis INTEGER NOT NULL,
                body TEXT NOT NULL,
                weather_code INTEGER,
                temp_c REAL)"""
        )
        db.execSQL(
            """CREATE TABLE skipped(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                dedupe TEXT NOT NULL UNIQUE,
                sender TEXT NOT NULL,
                time_millis INTEGER NOT NULL,
                body TEXT NOT NULL)"""
        )
    }

    private fun createMerchants(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS merchants(upi_id TEXT PRIMARY KEY, name TEXT NOT NULL)")
    }

    private fun createSplits(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS splits(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                txn_id INTEGER NOT NULL,
                category TEXT NOT NULL,
                item TEXT,
                amount_paise INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS splits_txn ON splits(txn_id)")
    }

    override fun onCreate(db: SQLiteDatabase) {
        createTxnTables(db)
        createMerchants(db)
        createSplits(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) { // parser changed: wipe and re-import from the inbox
            db.execSQL("DROP TABLE IF EXISTS txns")
            db.execSQL("DROP TABLE IF EXISTS skipped")
            createTxnTables(db)
        }
        if (oldVersion < 5) { // weather saved with each payment
            try { db.execSQL("ALTER TABLE txns ADD COLUMN weather_code INTEGER") } catch (e: Exception) { }
            try { db.execSQL("ALTER TABLE txns ADD COLUMN temp_c REAL") } catch (e: Exception) { }
        }
        createMerchants(db)
        createSplits(db)
    }

    /** Returns true if a new row was added, false if it already existed. */
    fun addTxn(dedupe: String, sender: String, p: Parsed, time: Long, body: String): Boolean {
        val v = ContentValues().apply {
            put("dedupe", dedupe); put("sender", sender); put("direction", p.direction)
            put("amount_paise", p.amountPaise); put("payee", p.payee); put("upi_ref", p.upiRef)
            put("time_millis", time); put("body", body)
        }
        return writableDatabase.insertWithOnConflict("txns", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun addSkipped(dedupe: String, sender: String, time: Long, body: String) {
        val v = ContentValues().apply {
            put("dedupe", dedupe); put("sender", sender); put("time_millis", time); put("body", body)
        }
        writableDatabase.insertWithOnConflict("skipped", null, v, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Re-parses existing transactions using the latest parser rules (e.g. extracts previously unknown payees, corrects direction). */
    fun reparseTxns(): Int {
        var count = 0
        val updates = mutableListOf<Pair<Long, Parsed>>()
        readableDatabase.rawQuery("SELECT id, body FROM txns", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val body = c.getString(1)
                val p = SmsParser.parse(body) ?: continue
                updates.add(id to p)
            }
        }
        if (updates.isNotEmpty()) {
            writableDatabase.beginTransaction()
            try {
                for ((id, p) in updates) {
                    val v = ContentValues().apply {
                        put("direction", p.direction)
                        put("amount_paise", p.amountPaise)
                        put("payee", p.payee)
                        if (p.upiRef.isNotEmpty()) put("upi_ref", p.upiRef)
                    }
                    writableDatabase.update("txns", v, "id = ?", arrayOf(id.toString()))
                    count++
                }
                writableDatabase.setTransactionSuccessful()
            } finally {
                writableDatabase.endTransaction()
            }
        }

        val promote = mutableListOf<Triple<Long, Long, Pair<String, Pair<String, Parsed>>>>()
        readableDatabase.rawQuery("SELECT id, sender, time_millis, body FROM skipped", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val sender = c.getString(1)
                val time = c.getLong(2)
                val body = c.getString(3)
                val p = SmsParser.parse(body) ?: continue
                promote.add(Triple(id, time, Pair(sender, Pair(body, p))))
            }
        }
        for (item in promote) {
            val id = item.first
            val time = item.second
            val sender = item.third.first
            val body = item.third.second.first
            val p = item.third.second.second
            val dedupe = if (p.upiRef.isNotEmpty()) "ref:${p.upiRef}:${p.direction}" else "body:${body.hashCode()}:${body.length}"
            if (addTxn(dedupe, sender, p, time, body)) {
                writableDatabase.delete("skipped", "id = ?", arrayOf(id.toString()))
                count++
            }
        }

        return count
    }

    // ---- your own names for payees ----

    fun merchantMap(): Map<String, String> {
        val out = HashMap<String, String>()
        readableDatabase.rawQuery("SELECT upi_id,name FROM merchants", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1)
        }
        return out
    }

    /** Saves a name for a payee; a blank name removes it. Applies to every transaction with that payee. */
    fun setMerchant(rawPayee: String, name: String) {
        val key = rawPayee.lowercase()
        if (name.isBlank()) {
            writableDatabase.delete("merchants", "upi_id = ?", arrayOf(key))
        } else {
            val v = ContentValues().apply { put("upi_id", key); put("name", name.trim()) }
            writableDatabase.insertWithOnConflict("merchants", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    // ---- splits (where each rupee went) ----

    /** Returns the id of the new split row, so it can be undone precisely. */
    fun addSplit(txnId: Long, catKey: String, item: String?, paise: Long): Long {
        val v = ContentValues().apply {
            put("txn_id", txnId); put("category", catKey); put("item", item); put("amount_paise", paise)
        }
        return writableDatabase.insert("splits", null, v)
    }

    fun deleteSplit(id: Long) {
        writableDatabase.delete("splits", "id = ?", arrayOf(id.toString()))
    }

    // A payment between your own accounts shows up twice with the same UPI ref (one debit, one credit).
    // That is not spending, so the unsorted queue and the dashboard totals skip those.
    private val notSelfTransfer =
        "NOT EXISTS (SELECT 1 FROM txns o WHERE o.upi_ref = t.upi_ref AND o.upi_ref <> '' AND o.direction <> t.direction)"

    private val sortedJoin =
        "LEFT JOIN (SELECT txn_id, SUM(amount_paise) AS s FROM splits GROUP BY txn_id) x ON x.txn_id = t.id"

    /** Payments since the given time that are not fully sorted yet, newest first. */
    fun unsortedIds(since: Long): List<Long> {
        val out = ArrayList<Long>()
        readableDatabase.rawQuery(
            "SELECT t.id FROM txns t $sortedJoin " +
                    "WHERE t.time_millis >= ? AND COALESCE(x.s, 0) < t.amount_paise AND $notSelfTransfer " +
                    "ORDER BY t.time_millis DESC",
            arrayOf(since.toString())
        ).use { c -> while (c.moveToNext()) out.add(c.getLong(0)) }
        return out
    }

    /** (time, paise) of every debit in [start, end), leaving out transfers between your own accounts. */
    fun debitPoints(start: Long, end: Long): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        readableDatabase.rawQuery(
            "SELECT t.time_millis, t.amount_paise FROM txns t " +
                    "WHERE t.direction = 'DEBIT' AND t.time_millis >= ? AND t.time_millis < ? AND $notSelfTransfer",
            arrayOf(start.toString(), end.toString())
        ).use { c -> while (c.moveToNext()) out.add(c.getLong(0) to c.getLong(1)) }
        return out
    }

    /** Money still unsorted (paise) and how many debits that is, in [start, end). */
    fun unsortedStats(start: Long, end: Long): Pair<Long, Int> =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(t.amount_paise - COALESCE(x.s, 0)), 0), COUNT(*) FROM txns t $sortedJoin " +
                    "WHERE t.direction = 'DEBIT' AND t.time_millis >= ? AND t.time_millis < ? " +
                    "AND COALESCE(x.s, 0) < t.amount_paise AND $notSelfTransfer",
            arrayOf(start.toString(), end.toString())
        ).use { c -> if (c.moveToFirst()) c.getLong(0) to c.getInt(1) else 0L to 0 }

    /** Money spent per category in [start, end) (sorted pieces of debits only), biggest first. */
    fun categoryTotals(start: Long, end: Long): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        readableDatabase.rawQuery(
            "SELECT s.category, SUM(s.amount_paise) FROM splits s JOIN txns t ON t.id = s.txn_id " +
                    "WHERE t.direction = 'DEBIT' AND t.time_millis >= ? AND t.time_millis < ? AND $notSelfTransfer " +
                    "GROUP BY s.category ORDER BY 2 DESC",
            arrayOf(start.toString(), end.toString())
        ).use { c -> while (c.moveToNext()) out[c.getString(0)] = c.getLong(1) }
        return out
    }

    /** Money spent per item in [start, end), biggest first. */
    fun itemTotals(start: Long, end: Long): List<ItemTotal> {
        val out = ArrayList<ItemTotal>()
        readableDatabase.rawQuery(
            "SELECT s.category, s.item, SUM(s.amount_paise), COUNT(*) FROM splits s JOIN txns t ON t.id = s.txn_id " +
                    "WHERE t.direction = 'DEBIT' AND t.time_millis >= ? AND t.time_millis < ? " +
                    "AND s.item IS NOT NULL AND $notSelfTransfer " +
                    "GROUP BY s.category, s.item ORDER BY 3 DESC LIMIT 40",
            arrayOf(start.toString(), end.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(ItemTotal(c.getString(0), c.getString(1), c.getLong(2), c.getInt(3)))
        }
        return out
    }

    fun splitsFor(txnId: Long): List<Split> {
        val out = ArrayList<Split>()
        readableDatabase.rawQuery(
            "SELECT id,category,item,amount_paise FROM splits WHERE txn_id = ? ORDER BY id",
            arrayOf(txnId.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(Split(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
        }
        return out
    }

    fun deleteLastSplit(txnId: Long) {
        writableDatabase.execSQL(
            "DELETE FROM splits WHERE id = (SELECT MAX(id) FROM splits WHERE txn_id = ?)",
            arrayOf<Any?>(txnId)
        )
    }

    fun clearSplits(txnId: Long) {
        writableDatabase.delete("splits", "txn_id = ?", arrayOf(txnId.toString()))
    }

    private fun sortedMap(): Map<Long, Long> {
        val out = HashMap<Long, Long>()
        readableDatabase.rawQuery("SELECT txn_id, SUM(amount_paise) FROM splits GROUP BY txn_id", null).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = c.getLong(1)
        }
        return out
    }

    // ---- reading transactions ----

    private val cols = "id,sender,direction,amount_paise,payee,upi_ref,time_millis,body,weather_code,temp_c"

    private fun read(c: Cursor, names: Map<String, String>, sorted: Map<Long, Long>) = Txn(
        c.getLong(0), c.getString(1), c.getString(2), c.getLong(3),
        c.getString(4), Merchants.resolve(c.getString(4), names),
        c.getString(5), c.getLong(6), c.getString(7),
        sorted[c.getLong(0)] ?: 0L,
        if (c.isNull(8)) null else c.getInt(8),
        if (c.isNull(9)) null else c.getFloat(9)
    )

    fun all(limit: Int = 1000): List<Txn> {
        val names = merchantMap()
        val sorted = sortedMap()
        val out = ArrayList<Txn>()
        readableDatabase.rawQuery("SELECT $cols FROM txns ORDER BY time_millis DESC LIMIT $limit", null).use { c ->
            while (c.moveToNext()) out.add(read(c, names, sorted))
        }
        return out
    }

    fun byId(id: Long): Txn? {
        val names = merchantMap()
        val sorted = sortedMap()
        return readableDatabase.rawQuery("SELECT $cols FROM txns WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) read(c, names, sorted) else null
        }
    }

    fun txnsBetween(start: Long, end: Long): List<Txn> {
        val names = merchantMap()
        val sorted = sortedMap()
        val out = ArrayList<Txn>()
        readableDatabase.rawQuery(
            "SELECT $cols FROM txns WHERE time_millis >= ? AND time_millis < ? ORDER BY time_millis DESC",
            arrayOf(start.toString(), end.toString())
        ).use { c -> while (c.moveToNext()) out.add(read(c, names, sorted)) }
        return out
    }

    fun txnCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM txns", null).use { it.moveToFirst(); it.getInt(0) }

    // ---- nicknames ----

    /** Most repeated payees first, with money out and money in kept apart. */
    fun topPayees(limit: Int): List<PayeeStat> {
        val out = ArrayList<PayeeStat>()
        readableDatabase.rawQuery(
            "SELECT payee, COUNT(*), " +
                "SUM(CASE WHEN direction = 'DEBIT' THEN amount_paise ELSE 0 END), " +
                "SUM(CASE WHEN direction = 'CREDIT' THEN amount_paise ELSE 0 END) " +
                "FROM txns GROUP BY payee ORDER BY COUNT(*) DESC, SUM(amount_paise) DESC LIMIT $limit", null
        ).use { c ->
            while (c.moveToNext()) out.add(PayeeStat(c.getString(0), c.getInt(1), c.getLong(2), c.getLong(3)))
        }
        return out
    }

    // ---- items renamed in Settings: everything already sorted follows the new name ----

    fun renameItems(catKey: String, pairs: List<Pair<String, String>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // two steps, so swapping two names cannot mix them up
            pairs.forEachIndexed { i, p ->
                db.execSQL("UPDATE splits SET item = ? WHERE category = ? AND item = ?", arrayOf<Any?>("\u0001$i", catKey, p.first))
            }
            pairs.forEachIndexed { i, p ->
                db.execSQL("UPDATE splits SET item = ? WHERE category = ? AND item = ?", arrayOf<Any?>(p.second, catKey, "\u0001$i"))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---- what is inside a bar or a category on the dashboard ----

    /** Every debit in [start, end), newest first, leaving out transfers between your own accounts. */
    fun debitTxns(start: Long, end: Long): List<Txn> {
        val names = merchantMap()
        val sorted = sortedMap()
        val out = ArrayList<Txn>()
        readableDatabase.rawQuery(
            "SELECT $cols FROM txns t WHERE t.direction = 'DEBIT' AND t.time_millis >= ? AND t.time_millis < ? " +
                    "AND $notSelfTransfer ORDER BY t.time_millis DESC",
            arrayOf(start.toString(), end.toString())
        ).use { c -> while (c.moveToNext()) out.add(read(c, names, sorted)) }
        return out
    }

    /** Every sorted piece of those debits, newest first. */
    fun splitRows(start: Long, end: Long): List<SplitRow> {
        val out = ArrayList<SplitRow>()
        readableDatabase.rawQuery(
            "SELECT s.txn_id, t.payee, t.time_millis, s.category, s.item, s.amount_paise " +
                    "FROM splits s JOIN txns t ON t.id = s.txn_id " +
                    "WHERE t.direction = 'DEBIT' AND t.time_millis >= ? AND t.time_millis < ? AND $notSelfTransfer " +
                    "ORDER BY t.time_millis DESC",
            arrayOf(start.toString(), end.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(SplitRow(c.getLong(0), c.getString(1), c.getLong(2), c.getString(3), c.getString(4), c.getLong(5)))
            }
        }
        return out
    }

    // ---- weather, saved with each payment so it is only ever downloaded once ----

    /** (id, time) of payments since the given time that have no weather yet. */
    fun missingWeather(since: Long): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        readableDatabase.rawQuery(
            "SELECT id, time_millis FROM txns WHERE weather_code IS NULL AND time_millis >= ?",
            arrayOf(since.toString())
        ).use { c -> while (c.moveToNext()) out.add(c.getLong(0) to c.getLong(1)) }
        return out
    }

    fun setWeather(id: Long, code: Int, tempC: Float?) {
        val v = ContentValues().apply {
            put("weather_code", code)
            if (tempC != null) put("temp_c", tempC)
        }
        writableDatabase.update("txns", v, "id = ?", arrayOf(id.toString()))
    }

    fun lastTxn(): Txn? {
        val names = merchantMap()
        val sorted = sortedMap()
        return readableDatabase.rawQuery("SELECT $cols FROM txns ORDER BY id DESC LIMIT 1", null).use { c ->
            if (c.moveToFirst()) read(c, names, sorted) else null
        }
    }

    fun skippedCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM skipped", null).use { it.moveToFirst(); it.getInt(0) }

    /** Raw text of the newest messages (parsed and skipped), for tuning the parser. */
    fun recentBodies(n: Int): String {
        val sb = StringBuilder()
        readableDatabase.rawQuery(
            "SELECT body FROM (SELECT body,time_millis FROM txns UNION ALL " +
                    "SELECT body,time_millis FROM skipped) ORDER BY time_millis DESC LIMIT $n", null
        ).use { c ->
            while (c.moveToNext()) sb.append(c.getString(0)).append("\n\n")
        }
        return sb.toString()
    }

    data class UnknownMessage(
        val id: Long,
        val sender: String,
        val timeMillis: Long,
        val body: String,
        val source: String
    )

    fun unknownMessages(): List<UnknownMessage> {
        val out = ArrayList<UnknownMessage>()
        readableDatabase.rawQuery(
            "SELECT id, sender, time_millis, body, 'Skipped' FROM skipped " +
                    "UNION ALL " +
                    "SELECT id, sender, time_millis, body, 'Unknown Payee' FROM txns WHERE payee = 'Unknown' " +
                    "ORDER BY time_millis DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(UnknownMessage(c.getLong(0), c.getString(1), c.getLong(2), c.getString(3), c.getString(4)))
            }
        }
        return out
    }

    companion object {
        @Volatile private var inst: Db? = null
        fun get(ctx: Context): Db =
            inst ?: synchronized(this) { inst ?: Db(ctx.applicationContext).also { inst = it } }
    }
}