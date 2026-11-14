package com.example.expensetracker

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpenseTrackerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Home(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

private fun hasSms(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

@Composable
fun Home(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick by TxBus.tick.collectAsState()
    var txns by remember { mutableStateOf(emptyList<Txn>()) }
    var skipped by remember { mutableStateOf(0) }
    var info by remember { mutableStateOf("") }
    var granted by remember { mutableStateOf(hasSms(ctx)) }
    val fmt = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH) }

    // Reload whenever a new message is saved (tick changes) or an import finishes
    LaunchedEffect(tick) {
        val (list, s) = withContext(Dispatchers.IO) {
            val db = Db.get(ctx)
            db.all() to db.skippedCount()
        }
        txns = list
        skipped = s
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasSms(ctx)
    }

    Column(modifier.padding(16.dp)) {
        Text("Expense tracker", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.padding(4.dp))

        Row {
            if (!granted) {
                Button(onClick = {
                    launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                }) { Text("Allow SMS access") }
            } else {
                Button(onClick = {
                    scope.launch {
                        info = "Importing..."
                        val r = withContext(Dispatchers.IO) { SmsImporter.backfill(ctx) }
                        info = "Scanned ${r.scanned}: ${r.added} added, ${r.duplicates} already saved, ${r.ignored} not transactions"
                        TxBus.tick.value++
                    }
                }) { Text("Import old messages") }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    val text = withContext(Dispatchers.IO) { Db.get(ctx).recentBodies(20) }
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("messages", text))
                    info = "Copied the 20 newest messages"
                }
            }) { Text("Copy 20 messages") }
        }

        if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.bodySmall)
        Text(
            "${txns.size} transactions, $skipped messages could not be read",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyColumn {
            items(txns, key = { it.id }) { t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.payee, maxLines = 1)
                        Text(fmt.format(Date(t.timeMillis)), style = MaterialTheme.typography.bodySmall)
                    }
                    val sign = if (t.direction == "DEBIT") "-" else "+"
                    Text("$sign₹" + "%.2f".format(t.amountPaise / 100.0))
                }
                HorizontalDivider()
            }
        }
    }
}