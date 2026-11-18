package com.example.expensetracker

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun hasSms(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

@Composable
fun SettingsScreen(onBack: () -> Unit, onCategories: () -> Unit, onNicknames: () -> Unit, onUnknown: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick by TxBus.tick.collectAsState()
    var granted by remember { mutableStateOf(hasSms(ctx)) }
    var info by remember { mutableStateOf("") }
    var unsortedOnly by remember { mutableStateOf(Prefs.unsortedOnly(ctx)) }
    var counts by remember { mutableStateOf(0 to 0) }
    var remOn by remember { mutableStateOf(Prefs.reminderOn(ctx)) }
    var remWeekly by remember { mutableStateOf(Prefs.reminderWeekly(ctx)) }
    var remDay by remember { mutableStateOf(Prefs.reminderDay(ctx)) }
    var remHour by remember { mutableStateOf(Prefs.reminderHour(ctx)) }
    var remMin by remember { mutableStateOf(Prefs.reminderMinute(ctx)) }
    fun saveReminder() {
        Prefs.setReminder(ctx, remWeekly, remDay, remHour, remMin)
        Reminders.schedule(ctx)
    }
    var weatherOn by remember { mutableStateOf(Prefs.weatherOn(ctx)) }
    var wifiOnly by remember { mutableStateOf(Prefs.wifiOnly(ctx)) }
    var lat by remember { mutableStateOf(Prefs.lat(ctx)) }
    var lon by remember { mutableStateOf(Prefs.lon(ctx)) }

    LaunchedEffect(tick) {
        counts = withContext(Dispatchers.IO) {
            val db = Db.get(ctx)
            db.txnCount() to db.skippedCount()
        }
    }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasSms(ctx)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack) { Text("\u2039 Dashboard") }
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCategories) { Text("Edit categories") }
            Button(onClick = onNicknames) { Text("Nicknames") }
        }

        Text("SMS import", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            "${counts.first} payments saved, ${counts.second} messages could not be read",
            style = MaterialTheme.typography.bodySmall
        )
        if (!granted) {
            Button(onClick = {
                smsLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
            }, modifier = Modifier.padding(top = 8.dp)) { Text("Allow SMS access") }
        } else {
            Button(onClick = {
                scope.launch {
                    info = "Importing..."
                    val r = withContext(Dispatchers.IO) { SmsImporter.backfill(ctx) }
                    info = "Scanned ${r.scanned}: ${r.added} added, ${r.duplicates} already saved, ${r.ignored} not transactions"
                    TxBus.tick.value++
                }
            }, modifier = Modifier.padding(top = 8.dp)) { Text("Import old messages") }
        }
        if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.bodySmall)
        val liveAt = Prefs.liveAt(ctx)
        Text(
            if (liveAt == 0L) "Live listener: has not heard any message yet"
            else "Live listener last heard: " +
                java.text.SimpleDateFormat("d MMM, h:mm:ss a", java.util.Locale.ENGLISH).format(java.util.Date(liveAt)) +
                " (" + Prefs.liveNote(ctx) + ")",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        OutlinedButton(onClick = { openBackgroundSettings(ctx) }, modifier = Modifier.padding(top = 4.dp)) {
            Text("Allow background access (Autostart)")
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("Notifications", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scope.launch {
                        val t = withContext(Dispatchers.IO) { Db.get(ctx).lastTxn() }
                        if (t != null) Notifier.show(ctx, t) else info = "No payments yet"
                    }
                }
            }) { Text("Send test notification") }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("Dashboard", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Show only unsorted payments in the list", modifier = Modifier.weight(1f).padding(end = 12.dp))
            Switch(checked = unsortedOnly, onCheckedChange = {
                unsortedOnly = it
                Prefs.setUnsortedOnly(ctx, it)
                TxBus.tick.value++
            })
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("Reminder", style = MaterialTheme.typography.titleMedium)
        Text(
            "A notification like \"4 unsorted payments\". It stays quiet when there is nothing to sort.",
            style = MaterialTheme.typography.bodySmall
        )
        SwitchRow("Remind me", remOn) {
            remOn = it
            Prefs.setReminderOn(ctx, it)
            Reminders.schedule(ctx)
        }
        if (remOn) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!remWeekly) Button(onClick = { }) { Text("Daily") }
                else OutlinedButton(onClick = { remWeekly = false; saveReminder() }) { Text("Daily") }
                if (remWeekly) Button(onClick = { }) { Text("Weekly") }
                else OutlinedButton(onClick = { remWeekly = true; saveReminder() }) { Text("Weekly") }
            }
            if (remWeekly) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, label ->
                        val day = i + 1 // Calendar numbers days from Sunday = 1
                        val on = remDay == day
                        Box(
                            Modifier.weight(1f).height(38.dp)
                                .background(
                                    if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { remDay = day; saveReminder() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val h12 = if (remHour % 12 == 0) 12 else remHour % 12
                Text("At $h12:${"%02d".format(remMin)} ${if (remHour < 12) "AM" else "PM"}")
                Row {
                    TextButton(onClick = { remHour = (remHour + 23) % 24; saveReminder() }) { Text("Hour -") }
                    TextButton(onClick = { remHour = (remHour + 1) % 24; saveReminder() }) { Text("Hour +") }
                    TextButton(onClick = { remMin = (remMin + 45) % 60; saveReminder() }) { Text("Min -") }
                    TextButton(onClick = { remMin = (remMin + 15) % 60; saveReminder() }) { Text("Min +") }
                }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val n = withContext(Dispatchers.IO) { Reminders.unsortedCount(ctx) }
                    if (n > 0) Notifier.showReminder(ctx, n) else info = "Nothing unsorted right now"
                }
            }, modifier = Modifier.padding(top = 4.dp)) { Text("Send a test reminder") }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("Weather", style = MaterialTheme.typography.titleMedium)
        Text(
            "Shows the real weather on the bubble screen. Each payment's weather is downloaded once, in the " +
                    "background, then saved with the payment, so it is never downloaded again.",
            style = MaterialTheme.typography.bodySmall
        )
        SwitchRow("Show real weather", weatherOn) { weatherOn = it; Prefs.setWeatherOn(ctx, it) }
        SwitchRow("Download on Wi-Fi only", wifiOnly) { wifiOnly = it; Prefs.setWifiOnly(ctx, it) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = lat, onValueChange = { lat = it }, label = { Text("Latitude") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = lon, onValueChange = { lon = it }, label = { Text("Longitude") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val la = lat.toDoubleOrNull()
                val lo = lon.toDoubleOrNull()
                if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                    info = "Enter a valid latitude and longitude"
                } else {
                    Prefs.setLocation(ctx, lat.trim(), lon.trim())
                    info = "Location saved"
                }
            }) { Text("Save location") }
            Button(onClick = {
                scope.launch {
                    info = "Fetching..."
                    info = withContext(Dispatchers.IO) { WeatherSync.run(ctx, force = true) }.message
                }
            }) { Text("Fetch now") }
        }
        Text("The default location is Virar.", style = MaterialTheme.typography.bodySmall)

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("Troubleshooting", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onUnknown, modifier = Modifier.padding(top = 8.dp)) { Text("View unknown messages") }
        OutlinedButton(onClick = {
            scope.launch {
                val text = withContext(Dispatchers.IO) { Db.get(ctx).recentBodies(20) }
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("messages", text))
                info = "Copied the 20 newest messages"
            }
        }, modifier = Modifier.padding(top = 8.dp)) { Text("Copy 20 newest messages") }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}