package com.example.expensetracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnknownScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var messages by remember { mutableStateOf(emptyList<Db.UnknownMessage>()) }
    var countInput by remember { mutableStateOf("20") }
    var info by remember { mutableStateOf("") }

    var filterExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    val filterOptions = listOf("All", "Skipped", "Unknown Payee")

    suspend fun reload() {
        messages = withContext(Dispatchers.IO) {
            Db.get(ctx).unknownMessages()
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    val filteredMessages = remember(messages, selectedFilter) {
        when (selectedFilter) {
            "Skipped" -> messages.filter { it.source == "Skipped" }
            "Unknown Payee" -> messages.filter { it.source == "Unknown Payee" }
            else -> messages
        }
    }

    val dateFmt = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH) }

    Column(modifier.padding(horizontal = 16.dp)) {
        TextButton(onClick = onBack) { Text("\u2039 Settings") }
        Text("Unknown Messages", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Showing ${filteredMessages.size} of ${messages.size} total messages. " +
                    "Use these to tune and improve the parser regex.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ExposedDropdownMenuBox(
            expanded = filterExpanded,
            onExpandedChange = { filterExpanded = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            OutlinedTextField(
                value = selectedFilter,
                onValueChange = {},
                readOnly = true,
                label = { Text("Filter") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = filterExpanded,
                onDismissRequest = { filterExpanded = false }
            ) {
                filterOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            selectedFilter = option
                            filterExpanded = false
                        }
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = countInput,
                onValueChange = { countInput = it },
                label = { Text("Count to copy") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val n = countInput.toIntOrNull() ?: 20
                    val toCopy = filteredMessages.take(n)
                    if (toCopy.isEmpty()) {
                        info = "No messages to copy"
                    } else {
                        val sb = StringBuilder()
                        toCopy.forEachIndexed { idx, m ->
                            sb.append("--- Message ${idx + 1} (${m.source}) ---\n")
                            sb.append("Sender: ${m.sender}\n")
                            sb.append("Time: ${dateFmt.format(Date(m.timeMillis))}\n")
                            sb.append("Body: ${m.body}\n\n")
                        }
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("unknown_messages", sb.toString()))
                        info = "Copied ${toCopy.size} messages to clipboard"
                    }
                }
            ) {
                Text("Copy")
            }
        }

        if (info.isNotEmpty()) {
            Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        }

        LazyColumn(Modifier.weight(1f)) {
            items(filteredMessages, key = { it.id }) { msg ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(msg.sender, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(msg.source, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Text(dateFmt.format(Date(msg.timeMillis)), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text(msg.body, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
                HorizontalDivider()
            }
        }
    }
}
