package com.example.expensetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DAY = 86_400_000L

private enum class Period(val label: String, val unit: String) {
    WEEK("Week", "week"), MONTH("Month", "month"), YEAR("Year", "year")
}

private data class DashData(
    val bins: List<Long>,
    val prevBins: List<Long>,
    val count: Int,
    val total: Long,
    val prevTotal: Long,
    val cats: Map<String, Long>,
    val items: List<ItemTotal>,
    val unsortedPaise: Long,
    val unsortedCount: Int,
    val payments: List<Txn>,
    val debits: List<Txn>,
    val splitRows: List<SplitRow>,
    val names: Map<String, String>
)

/** What the person tapped on a chart, so the payments behind it can be listed. */
private sealed class Sel {
    data class Bar(val index: Int) : Sel()
    data class Cat(val key: String) : Sel()
    data class Item(val cat: String, val item: String) : Sel()
    object Unsorted : Sel()
}

private data class DetailRow(val txnId: Long, val name: String, val time: Long, val label: String, val paise: Long)

// ---- period maths: offset 0 is the current week/month/year, -1 the one before, and so on ----

private fun periodStart(period: Period, offset: Int): Calendar {
    val c = Calendar.getInstance()
    c.firstDayOfWeek = Calendar.MONDAY
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    when (period) {
        Period.WEEK -> { c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); c.add(Calendar.WEEK_OF_YEAR, offset) }
        Period.MONTH -> { c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, offset) }
        Period.YEAR -> { c.set(Calendar.DAY_OF_YEAR, 1); c.add(Calendar.YEAR, offset) }
    }
    return c
}

private fun periodEnd(period: Period, start: Calendar): Long {
    val e = start.clone() as Calendar
    when (period) {
        Period.WEEK -> e.add(Calendar.WEEK_OF_YEAR, 1)
        Period.MONTH -> e.add(Calendar.MONTH, 1)
        Period.YEAR -> e.add(Calendar.YEAR, 1)
    }
    return e.timeInMillis
}

private fun binCount(period: Period, start: Calendar): Int = when (period) {
    Period.WEEK -> 7
    Period.MONTH -> start.getActualMaximum(Calendar.DAY_OF_MONTH)
    Period.YEAR -> 12
}

private fun binOf(period: Period, start: Calendar, time: Long): Int {
    if (period == Period.YEAR) {
        val c = Calendar.getInstance()
        c.timeInMillis = time
        return c.get(Calendar.MONTH)
    }
    return ((time - start.timeInMillis) / DAY).toInt()
}

private fun binLabels(period: Period, start: Calendar): List<String> = when (period) {
    Period.WEEK -> listOf("M", "T", "W", "T", "F", "S", "S")
    Period.MONTH -> (1..binCount(period, start)).map { if (it == 1 || it % 5 == 0) "$it" else "" }
    Period.YEAR -> listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
}

private fun binName(period: Period, start: Calendar, i: Int): String {
    val c = start.clone() as Calendar
    return if (period == Period.YEAR) {
        c.add(Calendar.MONTH, i)
        SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(c.time)
    } else {
        c.add(Calendar.DAY_OF_YEAR, i)
        SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(c.time)
    }
}

private fun periodTitle(period: Period, start: Calendar, end: Long): String {
    val last = Date(end - 1L)
    return when (period) {
        Period.WEEK -> SimpleDateFormat("d MMM", Locale.ENGLISH).format(start.time) + " - " +
                SimpleDateFormat("d MMM", Locale.ENGLISH).format(last)
        Period.MONTH -> SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(start.time)
        Period.YEAR -> SimpleDateFormat("yyyy", Locale.ENGLISH).format(start.time)
    }
}

private fun cumulative(bins: List<Long>, upTo: Int): List<Long> {
    var run = 0L
    return bins.take(upTo + 1).map { run += it; run }
}

@Composable
fun Dashboard(onSort: (Long?) -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick by TxBus.tick.collectAsState()
    var period by remember { mutableStateOf(Period.MONTH) }
    var offset by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf<DashData?>(null) }
    var sel by remember { mutableStateOf<Sel?>(null) }
    var open by remember { mutableStateOf<Long?>(null) }
    var nameInput by remember { mutableStateOf("") }
    val unsortedOnly = remember(tick) { Prefs.unsortedOnly(ctx) }
    val fmt = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH) }

    val startCal = remember(period, offset) { periodStart(period, offset) }
    val endMs = remember(period, offset) { periodEnd(period, startCal) }

    LaunchedEffect(tick, period, offset) {
        data = withContext(Dispatchers.IO) {
            val db = Db.get(ctx)
            val s = startCal.timeInMillis
            val prevCal = periodStart(period, offset - 1)
            val ps = prevCal.timeInMillis
            val pe = periodEnd(period, prevCal)

            fun bins(cal: Calendar, pts: List<Pair<Long, Long>>): List<Long> {
                val n = binCount(period, cal)
                val arr = LongArray(n)
                pts.forEach { (t, a) -> arr[binOf(period, cal, t).coerceIn(0, n - 1)] += a }
                return arr.toList()
            }

            val pts = db.debitPoints(s, endMs)
            val prevPts = db.debitPoints(ps, pe)
            val (unsortedPaise, unsortedCount) = db.unsortedStats(s, endMs)
            DashData(
                bins = bins(startCal, pts),
                prevBins = bins(prevCal, prevPts),
                count = pts.size,
                total = pts.sumOf { it.second },
                prevTotal = prevPts.sumOf { it.second },
                cats = db.categoryTotals(s, endMs),
                items = db.itemTotals(s, endMs),
                unsortedPaise = unsortedPaise,
                unsortedCount = unsortedCount,
                payments = db.txnsBetween(s, endMs),
                debits = db.debitTxns(s, endMs),
                splitRows = db.splitRows(s, endMs),
                names = db.merchantMap()
            )
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.outline
    val title = periodTitle(period, startCal, endMs)

    LazyColumn(modifier.padding(horizontal = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dashboard", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onSettings) { Text("\u2699", fontSize = 26.sp) }
            }
            Button(onClick = { onSort(null) }, modifier = Modifier.padding(bottom = 8.dp)) {
                Text("Sort unsorted bubbles")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Period.values().forEach { p ->
                    if (period == p) {
                        Button(onClick = { period = p; offset = 0; sel = null }) { Text(p.label) }
                    } else {
                        OutlinedButton(onClick = { period = p; offset = 0; sel = null }) { Text(p.label) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { offset -= 1; sel = null }) { Text("\u2039", fontSize = 24.sp) }
                Text(title, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { offset += 1; sel = null }, enabled = offset < 0) { Text("\u203A", fontSize = 24.sp) }
            }
        }

        val d = data
        if (d != null) {
            val now = System.currentTimeMillis()
            val sMs = startCal.timeInMillis
            val totalDays = ((endMs - sMs) / DAY).toInt()
            val upTo = if (offset == 0) binOf(period, startCal, now).coerceIn(0, d.bins.size - 1) else d.bins.size - 1
            val daysElapsed = if (offset == 0) (((now - sMs) / DAY).toInt() + 1).coerceIn(1, totalDays) else totalDays
            val curCum = cumulative(d.bins, upTo)
            val prevCum = cumulative(d.prevBins, d.prevBins.lastIndex)
            val prevSamePoint = prevCum.getOrNull(upTo) ?: d.prevTotal
            val change = if (prevSamePoint > 0L) (d.total - prevSamePoint) * 100f / prevSamePoint else null
            val avgPerDay = d.total / daysElapsed
            val catSum = d.cats.values.sum()
            val unsortedSlice = (d.total - catSum).coerceAtLeast(0L)

            // The payments behind whatever was tapped
            val rows: List<DetailRow> = when (val s = sel) {
                is Sel.Bar -> d.debits
                    .filter { binOf(period, startCal, it.timeMillis).coerceIn(0, d.bins.size - 1) == s.index }
                    .map { DetailRow(it.id, it.name, it.timeMillis, "", it.amountPaise) }
                is Sel.Cat -> d.splitRows.filter { it.catKey == s.key }
                    .map { DetailRow(it.txnId, Merchants.resolve(it.payee, d.names), it.time, it.item ?: "whole", it.paise) }
                is Sel.Item -> d.splitRows.filter { it.catKey == s.cat && it.item == s.item }
                    .map { DetailRow(it.txnId, Merchants.resolve(it.payee, d.names), it.time, "", it.paise) }
                Sel.Unsorted -> d.debits.filter { it.sortedPaise < it.amountPaise }
                    .map { DetailRow(it.id, it.name, it.timeMillis, "unsorted", it.amountPaise - it.sortedPaise) }
                null -> emptyList()
            }

            item {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            if (offset == 0) "Spent this ${period.unit}" else "Spent in $title",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(formatRupees(d.total), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                        if (change != null) {
                            val up = change > 0f
                            Text(
                                (if (up) "\u25B2 " else "\u25BC ") + "%.0f".format(kotlin.math.abs(change)) + "% vs " +
                                        (if (offset == 0) "the same point last ${period.unit}" else "the ${period.unit} before"),
                                color = if (up) Color(0xFFE5484D) else Color(0xFF30A46C),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (offset == 0 && daysElapsed < totalDays) {
                            Text(
                                "At this pace: ${formatRupees(d.total / daysElapsed * totalDays)} by the end of the ${period.unit}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatBox("Per day", formatRupees(avgPerDay))
                            StatBox("Payments", "${d.count}")
                            StatBox("Unsorted", formatRupees(d.unsortedPaise) + " (${d.unsortedCount})")
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            if (period == Period.YEAR) "Spending by month" else "Spending by day",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Highest: ${formatRupees(d.bins.maxOrNull() ?: 0L)}. Tap a bar to see its payments.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        BarChart(
                            values = d.bins,
                            labels = binLabels(period, startCal),
                            color = primary,
                            highlight = tertiary,
                            selected = (sel as? Sel.Bar)?.index ?: -1,
                            selectedColor = secondary,
                            onSelect = { i -> sel = if (sel == Sel.Bar(i)) null else Sel.Bar(i) }
                        )
                        val s = sel
                        if (s is Sel.Bar) {
                            DetailList(binName(period, startCal, s.index), rows, { onSort(it) }, { sel = null })
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Running total", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(primary, CircleShape))
                            Text(" this ${period.unit}   ", fontSize = 12.sp)
                            Box(Modifier.size(10.dp).background(muted, CircleShape))
                            Text(" the ${period.unit} before", fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        PaceChart(curCum, prevCum, primary, muted, grid)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Where it went", style = MaterialTheme.typography.titleMedium)
                        Text("Tap a category to see its payments.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        val slices = d.cats.entries.map { (key, paise) ->
                            paise to (Categories.byKey(key)?.color ?: Color.Gray)
                        } + listOf(unsortedSlice to Color(0xFF9AA0A6))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            DonutChart(slices, formatRupees(d.total), "spent", grid)
                        }
                        Spacer(Modifier.height(8.dp))
                        val shownTotal = (catSum + unsortedSlice).coerceAtLeast(1L)
                        d.cats.forEach { (key, paise) ->
                            val c = Categories.byKey(key)
                            LegendRow(
                                c?.name ?: key, c?.color ?: Color.Gray, paise, shownTotal,
                                selected = sel == Sel.Cat(key),
                                onClick = { sel = if (sel == Sel.Cat(key)) null else Sel.Cat(key) }
                            )
                        }
                        if (unsortedSlice > 0L) {
                            LegendRow(
                                "Unsorted", Color(0xFF9AA0A6), unsortedSlice, shownTotal,
                                selected = sel == Sel.Unsorted,
                                onClick = { sel = if (sel == Sel.Unsorted) null else Sel.Unsorted }
                            )
                        }
                        val s = sel
                        if (s is Sel.Cat) {
                            DetailList(Categories.byKey(s.key)?.name ?: s.key, rows, { onSort(it) }, { sel = null })
                        } else if (s == Sel.Unsorted) {
                            DetailList("Unsorted", rows, { onSort(it) }, { sel = null })
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Items", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "How often you buy each one, and what it costs over a year at this pace. Tap one to see its payments.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (d.items.isEmpty()) {
                            Text("Sort payments into items and they show up here.", modifier = Modifier.padding(top = 8.dp))
                        }
                        val maxItem = d.items.maxOfOrNull { it.paise } ?: 1L
                        d.items.take(10).forEach { row ->
                            val c = Categories.byKey(row.catKey)
                            val perWeek = row.count * 7f / daysElapsed
                            val yearly = row.paise * 365L / daysElapsed
                            val thisSel = Sel.Item(row.catKey, row.item ?: "")
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(
                                        if (sel == thisSel) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { sel = if (sel == thisSel) null else thisSel }
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    FitText(
                                        row.item ?: "", Modifier.weight(1f), maxSize = 15.sp,
                                        style = TextStyle(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(formatRupees(row.paise), fontWeight = FontWeight.SemiBold)
                                }
                                Box(
                                    Modifier.fillMaxWidth((row.paise.toFloat() / maxItem).coerceIn(0.02f, 1f)).height(8.dp)
                                        .background(c?.color ?: Color.Gray, RoundedCornerShape(4.dp))
                                )
                                Text(
                                    "${row.count} times, about ${"%.1f".format(perWeek)} a week, ${formatRupees(yearly)} a year",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        val s = sel
                        if (s is Sel.Item) {
                            DetailList(s.item, rows, { onSort(it) }, { sel = null })
                        }
                    }
                }
            }

            val shown = if (unsortedOnly) d.payments.filter { it.sortedPaise < it.amountPaise } else d.payments
            item {
                Text(
                    "Payments (${shown.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }

            items(shown, key = { it.id }) { t ->
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                open = if (open == t.id) null else t.id
                                nameInput = if (t.name == t.payee) "" else t.name
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            FitText(t.name, Modifier.fillMaxWidth(), maxSize = 16.sp)
                            val status = when {
                                t.sortedPaise >= t.amountPaise -> "Sorted"
                                t.sortedPaise > 0 -> "Partly sorted"
                                else -> "Unsorted"
                            }
                            Text("${fmt.format(Date(t.timeMillis))}, $status", style = MaterialTheme.typography.bodySmall)
                        }
                        val sign = if (t.direction == "DEBIT") "-" else "+"
                        Text(
                            sign + formatRupees(t.amountPaise),
                            color = if (t.direction == "DEBIT") DebitRed else CreditGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (open == t.id) {
                        Text(t.body, style = MaterialTheme.typography.bodySmall)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Nickname for ${t.payee}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { Db.get(ctx).setMerchant(t.payee, nameInput) }
                                    TxBus.tick.value++
                                }
                            }) { Text("Save") }
                        }
                        Button(onClick = { onSort(t.id) }, modifier = Modifier.padding(bottom = 8.dp)) {
                            Text("Sort this payment")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun LegendRow(name: String, color: Color, paise: Long, total: Long, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(name, modifier = Modifier.weight(1f).padding(start = 8.dp), fontSize = 14.sp)
        Text(formatRupees(paise), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "%.0f".format(paise * 100f / total) + "%",
            modifier = Modifier.width(44.dp), fontSize = 12.sp, textAlign = TextAlign.End
        )
    }
}

/** The payments behind a tapped bar, category or item. Tap one to open it in the bubble screen. */
@Composable
private fun DetailList(title: String, rows: List<DetailRow>, onOpen: (Long) -> Unit, onClose: () -> Unit) {
    val fmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH) }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$title: ${rows.size} payments, ${formatRupees(rows.sumOf { it.paise })}",
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) { Text("Close") }
        }
        if (rows.isEmpty()) Text("Nothing here.", style = MaterialTheme.typography.bodySmall)
        rows.take(200).forEach { r ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(r.txnId) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    FitText(r.name, Modifier.fillMaxWidth(), maxSize = 14.sp)
                    Text(
                        fmt.format(Date(r.time)) + if (r.label.isNotEmpty()) ", ${r.label}" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text("-" + formatRupees(r.paise), color = DebitRed, fontWeight = FontWeight.SemiBold)
            }
        }
        if (rows.size > 200) Text("Showing the 200 most recent.", style = MaterialTheme.typography.bodySmall)
    }
}