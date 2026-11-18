package com.example.expensetracker

import android.content.Context
import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** One-line text that shrinks until it fits, for very long UPI ids. Give it a bounded width. */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    maxSize: TextUnit = 16.sp,
    minSize: TextUnit = 8.sp,
    style: TextStyle = TextStyle.Default,
    textAlign: TextAlign? = null
) {
    var size by remember(text, maxSize) { mutableStateOf(maxSize) }
    Text(
        text = text,
        modifier = modifier,
        style = style,
        fontSize = size,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { r -> if (r.didOverflowWidth && size > minSize) size = size * 0.9f }
    )
}

/** Small saved settings. */
object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    fun unsortedOnly(ctx: Context): Boolean = p(ctx).getBoolean("unsorted_only", false)
    fun setUnsortedOnly(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("unsorted_only", v).apply()

    // weather: off by default, Wi-Fi only by default, location defaults to Virar
    fun weatherOn(ctx: Context): Boolean = p(ctx).getBoolean("weather_on", false)
    fun setWeatherOn(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("weather_on", v).apply()
    fun wifiOnly(ctx: Context): Boolean = p(ctx).getBoolean("weather_wifi_only", true)
    fun setWifiOnly(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("weather_wifi_only", v).apply()
    fun lat(ctx: Context): String = p(ctx).getString("lat", "19.4559") ?: "19.4559"
    fun lon(ctx: Context): String = p(ctx).getString("lon", "72.8111") ?: "72.8111"
    fun setLocation(ctx: Context, lat: String, lon: String) =
        p(ctx).edit().putString("lat", lat).putString("lon", lon).apply()
    fun lastWeatherTry(ctx: Context): Long = p(ctx).getLong("weather_try", 0L)
    fun setLastWeatherTry(ctx: Context, v: Long) = p(ctx).edit().putLong("weather_try", v).apply()

    // reminder: off by default; day is Calendar's number (1 = Sunday ... 7 = Saturday)
    fun reminderOn(ctx: Context): Boolean = p(ctx).getBoolean("rem_on", false)
    fun setReminderOn(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("rem_on", v).apply()
    fun reminderWeekly(ctx: Context): Boolean = p(ctx).getBoolean("rem_weekly", false)
    fun reminderDay(ctx: Context): Int = p(ctx).getInt("rem_day", 1)
    fun reminderHour(ctx: Context): Int = p(ctx).getInt("rem_hour", 20)
    fun reminderMinute(ctx: Context): Int = p(ctx).getInt("rem_min", 0)
    fun setReminder(ctx: Context, weekly: Boolean, day: Int, hour: Int, minute: Int) =
        p(ctx).edit().putBoolean("rem_weekly", weekly).putInt("rem_day", day)
            .putInt("rem_hour", hour).putInt("rem_min", minute).apply()

    // SMS catch-up and live-listener diagnostics
    fun lastSmsDate(ctx: Context): Long = p(ctx).getLong("last_sms_date", 0L)
    fun setLastSmsDate(ctx: Context, v: Long) = p(ctx).edit().putLong("last_sms_date", v).apply()
    fun liveAt(ctx: Context): Long = p(ctx).getLong("live_at", 0L)
    fun liveNote(ctx: Context): String = p(ctx).getString("live_note", "") ?: ""
    fun setLive(ctx: Context, at: Long, note: String) =
        p(ctx).edit().putLong("live_at", at).putString("live_note", note).apply()
}

/** Money out is red, money in is green, everywhere. */
val DebitRed = androidx.compose.ui.graphics.Color(0xFFE5484D)
val CreditGreen = androidx.compose.ui.graphics.Color(0xFF30A46C)

/** Opens Xiaomi's Autostart page if there is one, otherwise this app's settings page. */
fun openBackgroundSettings(ctx: Context) {
    val tries = listOf(
        Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))
    )
    for (i in tries) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
        }
    }
}
