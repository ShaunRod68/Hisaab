package com.example.expensetracker

import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.flow.MutableStateFlow

/** Bumped whenever something changes that screens should reload for (new payment, sorted piece, edit). */
object TxBus {
    val tick = MutableStateFlow(0)
}

enum class Outcome { ADDED, DUPLICATE, IGNORED }

object SmsImporter {

    data class Result(val scanned: Int, val added: Int, val duplicates: Int, val ignored: Int)

    /** Same path for old messages (backfill), catch-up and new ones (receiver), so results never differ. */
    fun ingest(db: Db, sender: String, body: String, time: Long): Outcome {
        val p = SmsParser.parse(body)
        val bodyKey = "body:${body.hashCode()}:${body.length}"
        if (p != null) {
            val key = if (p.upiRef.isNotEmpty()) "ref:${p.upiRef}:${p.direction}" else bodyKey
            return if (db.addTxn(key, sender, p, time, body)) Outcome.ADDED else Outcome.DUPLICATE
        }
        if (SmsParser.looksLikeMoney(body)) db.addSkipped(bodyKey, sender, time, body)
        return Outcome.IGNORED
    }

    /** Reads the whole SMS inbox for the bank senders. Call from a background thread. */
    fun backfill(ctx: Context): Result = scan(ctx, 0L)

    /** Only inbox messages newer than the last one seen. Cheap enough to run every time the app opens. */
    fun syncNew(ctx: Context): Result = scan(ctx, maxOf(0L, Prefs.lastSmsDate(ctx) - 60_000L))

    private fun scan(ctx: Context, since: Long): Result {
        val db = Db.get(ctx)
        var scanned = 0
        var added = 0
        var dup = 0
        var ignored = 0
        var newest = 0L
        val cursor = ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "(${Telephony.Sms.ADDRESS} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?) " +
                "AND ${Telephony.Sms.DATE} > ?",
            arrayOf("%KOTAKB%", "%BOBSMS%", "%BOBTXN%", "%HDFC%", "%ICICI%", since.toString()),
            "${Telephony.Sms.DATE} ASC"
        ) ?: return Result(0, 0, 0, 0)

        cursor.use { c ->
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val addr = c.getString(iAddr) ?: continue
                val body = c.getString(iBody) ?: continue
                if (!SmsParser.isBankSender(addr)) continue
                val date = c.getLong(iDate)
                if (date > newest) newest = date
                scanned++
                when (ingest(db, addr, body, date)) {
                    Outcome.ADDED -> added++
                    Outcome.DUPLICATE -> dup++
                    Outcome.IGNORED -> ignored++
                }
            }
        }
        if (newest > Prefs.lastSmsDate(ctx)) Prefs.setLastSmsDate(ctx, newest)
        return Result(scanned, added, dup, ignored)
    }
}
