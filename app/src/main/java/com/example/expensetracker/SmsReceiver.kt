package com.example.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            val db = Db.get(context)
            var added = false

            for ((sender, parts) in messages.groupBy { it.originatingAddress ?: "" }) {
                val now = System.currentTimeMillis()
                if (!SmsParser.isBankSender(sender)) {
                    Prefs.setLive(context, now, "$sender: not a bank sender")
                    continue
                }
                val body = parts.joinToString("") { it.messageBody ?: "" }
                val outcome = SmsImporter.ingest(db, sender, body, parts.first().timestampMillis)
                Prefs.setLive(context, now, "$sender: $outcome")
                if (outcome == Outcome.ADDED) {
                    added = true
                    db.lastTxn()?.let { Notifier.show(context, it) }
                }
            }
            if (added) TxBus.tick.value++
        } catch (e: Exception) {
            Prefs.setLive(context, System.currentTimeMillis(), "error: ${e.javaClass.simpleName} ${e.message}")
        }
    }
}
