package com.example.expensetracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Txn(
    val id: Long,
    val sender: String,
    val direction: String,
    val amountPaise: Long,
    val payee: String,
    val upiRef: String,
    val timeMillis: Long,
    val body: String
)

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "expense.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
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
                body TEXT NOT NULL)"""
        )
        // Messages from the bank senders that mention money but could not be parsed
        db.execSQL(
            """CREATE TABLE skipped(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                dedupe TEXT NOT NULL UNIQUE,
                sender TEXT NOT NULL,
                time_millis INTEGER NOT NULL,
                body TEXT NOT NULL)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

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

    fun all(limit: Int = 1000): List<Txn> {
        val out = ArrayList<Txn>()
        readableDatabase.rawQuery(
            "SELECT id,sender,direction,amount_paise,payee,upi_ref,time_millis,body " +
                    "FROM txns ORDER BY time_millis DESC LIMIT $limit", null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(Txn(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3),
                    c.getString(4), c.getString(5), c.getLong(6), c.getString(7)))
            }
        }
        return out
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

    companion object {
        @Volatile private var inst: Db? = null
        fun get(ctx: Context): Db =
            inst ?: synchronized(this) { inst ?: Db(ctx.applicationContext).also { inst = it } }
    }
}