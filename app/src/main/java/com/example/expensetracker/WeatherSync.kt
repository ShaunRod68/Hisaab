package com.example.expensetracker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Downloads the weather for payments that have none yet, in one small request, and saves it with each payment.
 * A payment is never fetched twice. Nothing is downloaded when every recent payment already has its weather.
 */
object WeatherSync {

    data class Result(val updated: Int, val message: String)

    private const val DAY = 86_400_000L

    private fun onWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** Call from a background thread. Never throws. */
    fun run(ctx: Context, force: Boolean = false): Result {
        if (!Prefs.weatherOn(ctx)) return Result(0, "Weather is switched off")
        val now = System.currentTimeMillis()
        val db = Db.get(ctx)
        val missing = db.missingWeather(now - 90L * DAY)
        if (missing.isEmpty()) return Result(0, "Every recent payment already has its weather")
        if (!force && now - Prefs.lastWeatherTry(ctx) < 15 * 60_000L) return Result(0, "Tried a moment ago")
        if (Prefs.wifiOnly(ctx) && !onWifi(ctx)) return Result(0, "Waiting for Wi-Fi")
        Prefs.setLastWeatherTry(ctx, now)
        return try {
            fetch(ctx, db, missing, now)
        } catch (e: Exception) {
            Result(0, "Could not reach the weather service")
        }
    }

    private fun fetch(ctx: Context, db: Db, missing: List<Pair<Long, Long>>, now: Long): Result {
        val oldest = missing.minOf { it.second }
        val pastDays = (((now - oldest) / DAY).toInt() + 1).coerceIn(1, 92)
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=${Prefs.lat(ctx)}&longitude=${Prefs.lon(ctx)}" +
                    "&hourly=weather_code,temperature_2m&past_days=$pastDays&forecast_days=1&timezone=auto"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 8_000
        val text = try {
            if (conn.responseCode != 200) return Result(0, "Weather service replied ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }

        val json = JSONObject(text)
        val offsetMs = json.optLong("utc_offset_seconds", 0L) * 1000L
        val hourly = json.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val codes = hourly.getJSONArray("weather_code")
        val temps = hourly.getJSONArray("temperature_2m")
        val byHour = HashMap<String, Pair<Int, Float?>>()
        for (i in 0 until times.length()) {
            if (codes.isNull(i)) continue
            val temp = if (temps.isNull(i)) null else temps.getDouble(i).toFloat()
            byHour[times.getString(i)] = codes.getInt(i) to temp
        }

        // The service answers in the place's local time, so shift each payment's time before looking it up
        val keyFmt = SimpleDateFormat("yyyy-MM-dd'T'HH':00'", Locale.US)
        keyFmt.timeZone = TimeZone.getTimeZone("UTC")
        var updated = 0
        for ((id, time) in missing) {
            val hit = byHour[keyFmt.format(Date(time + offsetMs))]
            if (hit != null) {
                db.setWeather(id, hit.first, hit.second)
                updated++
            } else {
                db.setWeather(id, -1, null) // no data for that hour: do not ask again
            }
        }
        return Result(updated, "Added weather to $updated payments")
    }
}