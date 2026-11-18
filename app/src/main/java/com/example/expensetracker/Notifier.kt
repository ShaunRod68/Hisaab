package com.example.expensetracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    private const val CHANNEL = "txns"

    @SuppressLint("MissingPermission") // checked just below
    fun show(ctx: Context, t: Txn) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("New transactions")
                .build()
        )

        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("txnId", t.id)
        }
        val pending = PendingIntent.getActivity(
            ctx, t.id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val amount = formatRupees(t.amountPaise)
        val title = if (t.direction == "DEBIT") "$amount to ${t.name}" else "$amount received from ${t.name}"

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_blob) // placeholder until we have a bubble icon
            .setContentTitle(title)
            .setContentText("Tap to sort it")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(t.id.toInt(), n)
    }

    @SuppressLint("MissingPermission") // checked just below
    fun showReminder(ctx: Context, count: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder("reminders", NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Reminders")
                .build()
        )
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            ctx, 9001, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, "reminders")
            .setSmallIcon(R.drawable.ic_stat_blob)
            .setContentTitle(if (count == 1) "1 unsorted payment" else "$count unsorted payments")
            .setContentText("Tap to sort them")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(9001, n)
    }
}