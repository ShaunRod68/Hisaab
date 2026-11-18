package com.example.expensetracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NicknamesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(emptyList<PayeeStat>()) }
    var names by remember { mutableStateOf(emptyMap<String, String>()) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val db = Db.get(ctx)
                db.topPayees(300) to db.merchantMap()
            }
            rows = loaded.first
            names = loaded.second
        }
    }
    LaunchedEffect(Unit) { reload() }

    val q = query.trim().lowercase()
    val shown = rows.filter {
        q.isEmpty() || it.payee.lowercase().contains(q) || (names[it.payee.lowercase()]?.lowercase()?.contains(q) == true)
    }

    Column(modifier.padding(horizontal = 16.dp)) {
        TextButton(onClick = onBack) { Text("\u2039 Settings") }
        Text("Nicknames", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The payees you pay most often, most repeated first. Tap one to give it a short name. " +
                    "The name shows everywhere, and the original stays underneath.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = query, onValueChange = { query = it }, label = { Text("Search") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.payee }) { row ->
                val nick = names[row.payee.lowercase()]
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                editing = if (editing == row.payee) null else row.payee
                                input = nick ?: ""
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            FitText(nick ?: row.payee, Modifier.fillMaxWidth(), maxSize = 16.sp,
                                style = androidx.compose.ui.text.TextStyle(fontWeight = if (nick != null) FontWeight.SemiBold else FontWeight.Normal))
                            if (nick != null) FitText(row.payee, Modifier.fillMaxWidth(), maxSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${row.count} payments", fontSize = 13.sp)
                            if (row.debitPaise > 0L) {
                                Text("-" + formatRupees(row.debitPaise), color = DebitRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (row.creditPaise > 0L) {
                                Text("+" + formatRupees(row.creditPaise), color = CreditGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (editing == row.payee) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = input, onValueChange = { input = it }, label = { Text("Nickname") },
                                singleLine = true, modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { Db.get(ctx).setMerchant(row.payee, input) }
                                    TxBus.tick.value++
                                    editing = null
                                    reload()
                                }
                            }) { Text("Save") }
                        }
                        if (nick != null) {
                            TextButton(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { Db.get(ctx).setMerchant(row.payee, "") }
                                    TxBus.tick.value++
                                    editing = null
                                    reload()
                                }
                            }) { Text("Remove nickname") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}