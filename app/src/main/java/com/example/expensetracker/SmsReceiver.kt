package com.example.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val db = Db.get(context)
        var added = false

        // Long messages arrive in several parts from the same sender; join them first.
        for ((sender, parts) in messages.groupBy { it.originatingAddress ?: "" }) {
            if (!SmsParser.isBankSender(sender)) continue
            val body = parts.joinToString("") { it.messageBody ?: "" }
            if (SmsImporter.ingest(db, sender, body, parts.first().timestampMillis) == Outcome.ADDED) {
                added = true
            }
        }
        if (added) TxBus.tick.value++
    }
}