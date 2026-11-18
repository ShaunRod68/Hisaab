package com.example.expensetracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

private data class Piece(val id: Int, val amt: Long)
private data class Snap(val pieces: List<Piece>, val splitId: Long?)
private data class Pending(val piece: Piece, val neighbor: Piece, val cat: Category, val item: String)

/** Only payments from the last this-many days are queued as unsorted bubbles. Older ones are on the dashboard. */
private const val QUEUE_DAYS = 30

private val Ink = Color(0xFF1B1B2F)
private val Glow = TextStyle(color = Color.White, shadow = Shadow(Color(0x88000000), Offset(0f, 3f), 8f))

private fun hourOf(ms: Long): Float {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f
}

/** Where each category's line sits on the screen. thick is its width (side lines) or height (top and bottom). */
private fun lineRect(sec: Int, w: Float, h: Float, thick: Float): Rect = when (sec) {
    0 -> Rect(0.15f * w, 0f, 0.85f * w, thick)
    3 -> Rect(0.15f * w, h - thick, 0.85f * w, h)
    5 -> Rect(0f, 0.19f * h, thick, 0.47f * h)
    4 -> Rect(0f, 0.53f * h, thick, 0.81f * h)
    1 -> Rect(w - thick, 0.19f * h, w, 0.47f * h)
    else -> Rect(w - thick, 0.53f * h, w, 0.81f * h)
}

/**
 * Where would the finger end up if it kept going in a straight line? Returns the line whose span is nearest to
 * that point on the screen edge. This works from any starting height, unlike slicing the screen into six wedges.
 */
private fun predictSector(start: Offset, now: Offset, w: Float, h: Float): Int {
    val dx = now.x - start.x
    val dy = now.y - start.y
    var t = Float.MAX_VALUE
    if (dx > 0f) t = minOf(t, (w - start.x) / dx) else if (dx < 0f) t = minOf(t, -start.x / dx)
    if (dy > 0f) t = minOf(t, (h - start.y) / dy) else if (dy < 0f) t = minOf(t, -start.y / dy)
    if (t == Float.MAX_VALUE) return -1
    val hx = start.x + dx * t
    val hy = start.y + dy * t
    var best = -1
    var bestD = Float.MAX_VALUE
    for (cat in Categories.all) {
        val r = lineRect(cat.sector, w, h, 0f)
        val ddx = maxOf(r.left - hx, 0f, hx - r.right)
        val ddy = maxOf(r.top - hy, 0f, hy - r.bottom)
        val dist = ddx * ddx + ddy * ddy
        if (dist < bestD) { bestD = dist; best = cat.sector }
    }
    return best
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BubbleScreen(startId: Long?, onDashboard: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val weatherOn = remember { Prefs.weatherOn(ctx) }

    var queue by remember { mutableStateOf<List<Long>?>(null) }
    var pos by remember { mutableStateOf(0) }
    var txn by remember { mutableStateOf<Txn?>(null) }
    var splits by remember { mutableStateOf(emptyList<Split>()) }
    val pieces = remember { mutableStateListOf<Piece>() }
    val history = remember { mutableStateListOf<Snap>() }
    val ids = remember { AtomicInteger(1) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var allTime by remember { mutableStateOf(false) }

    // gesture state
    var holdId by remember { mutableStateOf<Int?>(null) }
    var holdDelta by remember { mutableStateOf(Offset.Zero) }
    var holdSector by remember { mutableStateOf(-1) }
    var holdItem by remember { mutableStateOf(-1) }
    var trailA by remember { mutableStateOf<Offset?>(null) }
    var trailB by remember { mutableStateOf<Offset?>(null) }
    var contSize by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val rects = remember { HashMap<Int, Rect>() }
    val dateFmt = remember { SimpleDateFormat("EEE, d MMM yyyy, h:mm a", Locale.ENGLISH) }

    val currentId = queue?.getOrNull(pos)

    fun reloadQueue(first: Long?, since: Long = if (allTime) 0L else System.currentTimeMillis() - QUEUE_DAYS * 86_400_000L) {
        scope.launch {
            val q = withContext(Dispatchers.IO) { db.unsortedIds(since) }
            pos = 0
            queue = if (first != null) listOf(first) + q.filter { it != first } else q
        }
    }

    // A new payment arrived (live listener, or the catch-up when the app opened): add it to the queue
    val tick by TxBus.tick.collectAsState()
    LaunchedEffect(tick) {
        if (queue == null) return@LaunchedEffect
        val since = if (allTime) 0L else System.currentTimeMillis() - QUEUE_DAYS * 86_400_000L
        val fresh = withContext(Dispatchers.IO) { db.unsortedIds(since) }
        val q = queue ?: return@LaunchedEffect
        val add = fresh.filter { it !in q }
        if (add.isNotEmpty()) {
            val idle = history.isEmpty() && pending == null && pieces.size <= 1
            queue = add + q
            pos = if (idle) 0 else pos + add.size // idle: show the newest payment first; busy: stay where you are
        }
    }

    // Loads one payment, with whatever part of it is still unsorted as a single fresh blob
    fun loadCurrent(id: Long?) {
        scope.launch {
            pending = null
            history.clear()
            pieces.clear()
            splits = emptyList()
            txn = null
            if (id == null) return@launch
            val (t, s) = withContext(Dispatchers.IO) { db.byId(id) to db.splitsFor(id) }
            txn = t
            splits = s
            if (t != null) {
                val rest = t.amountPaise - s.sumOf { it.paise }
                if (rest > 0) pieces.add(Piece(ids.getAndIncrement(), rest))
            }
        }
    }

    LaunchedEffect(Unit) { reloadQueue(startId) }
    LaunchedEffect(currentId) { loadCurrent(currentId) }

    // Weather for payments that have none yet: one small background download, saved with each payment
    LaunchedEffect(Unit) {
        if (weatherOn) {
            val r = withContext(Dispatchers.IO) { WeatherSync.run(ctx) }
            val id = txn?.id
            if (r.updated > 0 && id != null) txn = withContext(Dispatchers.IO) { db.byId(id) }
        }
    }

    fun next() { pos += 1 }
    fun prev() { if (pos > 0) pos -= 1 }

    // The swipe line can start anywhere. The piece it crosses most is cut where the line crosses it.
    fun tryCut(a: Offset, b: Offset) {
        if ((b - a).getDistance() < 60f) return
        val n = 40
        var bestId = -1
        var bestCount = 0
        var bestY = 0f
        for (piece in pieces) {
            val r = rects[piece.id]?.translate(-origin) ?: continue
            var cnt = 0
            var sumY = 0f
            for (k in 0..n) {
                val f = k / n.toFloat()
                val pt = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
                if (r.contains(pt)) { cnt++; sumY += pt.y }
            }
            if (cnt > bestCount) { bestCount = cnt; bestId = piece.id; bestY = sumY / cnt }
        }
        if (bestId < 0 || bestCount < 3) return
        val target = pieces.firstOrNull { it.id == bestId } ?: return
        val r = rects[bestId]?.translate(-origin) ?: return
        val frac = (bestY - r.top) / r.height
        if (frac < 0.06f || frac > 0.94f || target.amt < 200L) return
        val amt = ((target.amt * frac / 100.0).roundToLong() * 100L).coerceIn(100L, target.amt - 100L)
        history.add(Snap(pieces.toList(), null))
        val i = pieces.indexOfFirst { it.id == bestId }
        pieces[i] = Piece(ids.getAndIncrement(), amt)
        pieces.add(i + 1, Piece(ids.getAndIncrement(), target.amt - amt))
    }

    fun commit(p: Piece, nb: Piece?, cat: Category, item: String?, newAmt: Long) {
        val tid = txn?.id ?: return
        val before = pieces.toList()
        scope.launch {
            val sid = withContext(Dispatchers.IO) { db.addSplit(tid, cat.key, item, newAmt) }
            history.add(Snap(before, sid))
            if (nb != null && newAmt != p.amt) { // the neighbouring piece absorbs the difference
                val j = pieces.indexOfFirst { it.id == nb.id }
                if (j >= 0) pieces[j] = nb.copy(amt = nb.amt - (newAmt - p.amt))
            }
            pieces.removeAll { it.id == p.id }
            splits = withContext(Dispatchers.IO) { db.splitsFor(tid) }
            pending = null
            TxBus.tick.value++
            if (pieces.isEmpty()) { // fully sorted: move on unless you undo or skip first
                delay(650)
                if (pieces.isEmpty() && txn?.id == tid) next()
            }
        }
    }

    fun onDrop(p: Piece, cat: Category, itemIdx: Int) {
        val all = pieces.toList()
        val idx = all.indexOfFirst { it.id == p.id }
        val nb = all.getOrNull(idx + 1) ?: all.getOrNull(idx - 1)
        when {
            itemIdx < 0 -> commit(p, nb, cat, null, p.amt)               // whole blob into the category
            nb == null -> commit(p, null, cat, cat.items[itemIdx], p.amt) // last piece: exact remainder
            else -> pending = Pending(p, nb, cat, cat.items[itemIdx])     // ask for the exact amount
        }
    }

    fun undo() {
        val tid = txn?.id ?: return
        scope.launch {
            pending = null
            if (history.isNotEmpty()) {
                val s = history.removeAt(history.lastIndex)
                val sid = s.splitId
                if (sid != null) withContext(Dispatchers.IO) { db.deleteSplit(sid) }
                pieces.clear()
                pieces.addAll(s.pieces)
                splits = withContext(Dispatchers.IO) { db.splitsFor(tid) }
            } else if (splits.isNotEmpty()) {
                withContext(Dispatchers.IO) { db.deleteLastSplit(tid) }
                loadCurrent(tid)
            }
            TxBus.tick.value++
        }
    }

    // Picks the category line the finger is working on. Near an edge, the line the finger is actually over wins,
    // and the line you are already on is sticky, so a line never gives way before your finger has left it.
    fun updateHold(start: Offset, now: Offset) {
        val w = contSize.width.toFloat()
        val h = contSize.height.toFloat()
        val reach = with(density) { 96.dp.toPx() }  // how close to an edge counts as being on a line
        val slack = with(density) { 56.dp.toPx() }  // extra room past the ends of the line you are already on
        val stick = with(density) { 36.dp.toPx() }  // another line must be clearly closer before it takes over
        val cur = holdSector

        var sec = -1
        var onLine = false
        var bestScore = Float.MAX_VALUE
        for (cat in Categories.all) {
            val r = lineRect(cat.sector, w, h, 0f)
            val m = if (cat.sector == cur) slack else 0f
            val dist: Float
            val inside: Boolean
            when (cat.sector) {
                0 -> { dist = now.y; inside = dist < reach && now.x >= r.left - m && now.x <= r.right + m }
                3 -> { dist = h - now.y; inside = dist < reach && now.x >= r.left - m && now.x <= r.right + m }
                1, 2 -> { dist = w - now.x; inside = dist < reach && now.y >= r.top - m && now.y <= r.bottom + m }
                else -> { dist = now.x; inside = dist < reach && now.y >= r.top - m && now.y <= r.bottom + m }
            }
            if (!inside) continue
            val score = if (cat.sector == cur) dist - stick else dist
            if (score < bestScore) { bestScore = score; sec = cat.sector; onLine = true }
        }
        if (sec < 0) {
            val nearEdge = now.x < reach || now.y < reach || w - now.x < reach || h - now.y < reach
            if (cur >= 0 && nearEdge) {
                sec = cur // in a gap between two lines near an edge: stay on the one you had
            } else if ((now - start).getDistance() > with(density) { 45.dp.toPx() }) {
                sec = predictSector(start, now, w, h)
            }
        }

        var item = -1
        if (sec >= 0 && onLine) {
            val cat = Categories.all.first { it.sector == sec }
            if (cat.items.isNotEmpty()) {
                val r = lineRect(sec, w, h, 0f)
                val horizontal = sec == 0 || sec == 3
                val f = if (horizontal) (now.x - r.left) / r.width else (now.y - r.top) / r.height
                item = (f * cat.items.size).toInt().coerceIn(0, cat.items.size - 1)
            }
        }
        holdSector = sec
        holdItem = item
    }

    val t = txn
    // Without real weather the sky shows a few calm drifting clouds
    val wx = (if (weatherOn) wxOf(t?.weatherCode) else null) ?: Wx.PARTLY
    Box(Modifier.fillMaxSize()) {
        WeatherSky(
            hour = hourOf(t?.timeMillis ?: System.currentTimeMillis()),
            wx = wx,
            paused = holdId != null || trailA != null || pending != null
        )

        Box(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .onSizeChanged { contSize = it }
                .onGloballyPositioned { origin = it.positionInRoot() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (pending != null || txn == null) return@awaitEachGesture
                        val startPiece = pieces.firstOrNull { p ->
                            rects[p.id]?.translate(-origin)?.contains(down.position) == true
                        }?.id

                        // A quick move is a cut swipe. Holding still for a moment picks the blob up.
                        var timedOut = true
                        val slop = withTimeoutOrNull(220L) {
                            val r = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            timedOut = false
                            r
                        }

                        if (timedOut && startPiece != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            holdId = startPiece
                            holdDelta = Offset.Zero
                            holdSector = -1
                            holdItem = -1
                            val start = down.position
                            drag(down.id) { change ->
                                change.consume()
                                holdDelta = change.position - start
                                updateHold(start, change.position)
                            }
                            val dropped = pieces.firstOrNull { it.id == startPiece }
                            val sec = holdSector
                            val item = holdItem
                            holdId = null
                            holdSector = -1
                            holdItem = -1
                            holdDelta = Offset.Zero
                            if (dropped != null && sec >= 0) {
                                onDrop(dropped, Categories.all.first { it.sector == sec }, item)
                            }
                        } else {
                            val first = if (timedOut) {
                                awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            } else slop
                            if (first != null) {
                                var last = first.position
                                trailA = down.position
                                trailB = last
                                drag(first.id) { change ->
                                    last = change.position
                                    trailB = last
                                    change.consume()
                                }
                                trailA = null
                                trailB = null
                                tryCut(down.position, last)
                            }
                        }
                    }
                }
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                // Equal-width side slots keep the counter exactly in the middle
                val n = queue?.size ?: 0
                Row(
                    Modifier.fillMaxWidth().alpha(if (holdId != null) 0f else 1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        TextButton(onClick = onDashboard, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Dashboard", color = Color.White)
                        }
                    }
                    Text(if (n > 0) "${minOf(pos + 1, n)} of $n" else "", style = Glow.copy(fontSize = 13.sp))
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { prev() }, enabled = pos > 0,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("\u2039 Prev", color = Color.White.copy(alpha = if (pos > 0) 1f else 0.4f)) }
                        TextButton(
                            onClick = { next() }, enabled = currentId != null,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("Next \u203A", color = Color.White.copy(alpha = if (currentId != null) 1f else 0.4f)) }
                    }
                }

                if (queue != null && currentId == null) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(top = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("All caught up", style = Glow.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold))
                        Text(
                            if (allTime) "No older unsorted payments found" else "No unsorted payments in the last $QUEUE_DAYS days",
                            style = Glow.copy(fontSize = 13.sp)
                        )
                        var currentQuote by remember { mutableStateOf(Quotes.random()) }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!allTime) {
                                Button(onClick = { allTime = true; reloadQueue(null, 0L) }) { Text("Old Bubbles") }
                            }
                            OutlinedButton(onClick = { currentQuote = Quotes.random(); reloadQueue(null) }) { Text("Check again", color = Color.White) }
                        }

                        Spacer(Modifier.weight(1f))

                        Text(
                            currentQuote,
                            style = Glow.copy(fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                        )
                    }
                }

                if (t != null) {
                    Column(
                        Modifier.fillMaxWidth().alpha(if (holdSector == 0) 0f else 1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (t.direction == "DEBIT") "Paid to" else "Received from", style = Glow.copy(fontSize = 13.sp))
                        FitText(t.name, Modifier.fillMaxWidth(), maxSize = 28.sp, style = Glow.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                        Text(signedRupees(t.amountPaise, t.direction == "DEBIT"), style = Glow.copy(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold))
                        Text(dateFmt.format(Date(t.timeMillis)), style = Glow.copy(fontSize = 13.sp))
                        val shownWx = if (weatherOn) wxOf(t.weatherCode) else null
                        if (shownWx != null) {
                            Text(
                                shownWx.label + (t.tempC?.let { ", ${it.roundToInt()}\u00B0" } ?: ""),
                                style = Glow.copy(fontSize = 13.sp)
                            )
                        }
                    }

                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val total = t.amountPaise.toFloat()
                        val usable = maxHeight * 0.8f
                        if (pieces.isEmpty()) {
                            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("All sorted", style = Glow.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold))
                            }
                        } else {
                            Column(
                                Modifier.align(Alignment.Center).fillMaxWidth(0.62f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pieces.forEach { p ->
                                    key(p.id) {
                                        PieceBlob(
                                            p = p,
                                            credit = t.direction != "DEBIT",
                                            height = maxOf(44.dp, usable * (p.amt / total)),
                                            lifted = holdId == p.id,
                                            delta = { holdDelta },
                                            onBounds = { rects[p.id] = it }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (pieces.isNotEmpty()) {
                        Text(
                            "Swipe across to cut. Hold a blob, then drag toward a line.",
                            style = Glow.copy(fontSize = 12.sp),
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp)
                        )
                    }

                    if (splits.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            splits.forEach { s ->
                                val c = Categories.byKey(s.catKey)
                                Box(
                                    Modifier.background(c?.color ?: Color.White, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "${s.item ?: c?.name ?: s.catKey} ${signedRupees(s.paise, t.direction == "DEBIT")}",
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink
                                    )
                                }
                            }
                        }
                    }
                    Row {
                        TextButton(onClick = { undo() }, enabled = history.isNotEmpty() || splits.isNotEmpty()) {
                            Text("Undo", color = Color.White)
                        }
                        if (splits.isNotEmpty()) {
                            TextButton(onClick = {
                                val tid = t.id
                                scope.launch {
                                    withContext(Dispatchers.IO) { db.clearSplits(tid) }
                                    loadCurrent(tid)
                                    TxBus.tick.value++
                                }
                            }) { Text("Start over", color = Color.White) }
                        }
                    }
                }
            }

            if (holdId != null) {
                CategoryLines(holdSector, holdItem, contSize.width.toFloat(), contSize.height.toFloat())
            }

            Canvas(Modifier.fillMaxSize()) {
                val a = trailA
                val b = trailB
                if (a != null && b != null) {
                    drawLine(
                        Color.White, a, b, strokeWidth = 7f, cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 16f))
                    )
                }
            }

            val pd = pending
            if (pd != null && t != null) {
                Box(Modifier.align(Alignment.BottomCenter).padding(8.dp)) {
                    ConfirmBar(
                        pd = pd,
                        totalPaise = t.amountPaise,
                        onSave = { amt -> commit(pd.piece, pd.neighbor, pd.cat, pd.item, amt) },
                        onCancel = { pending = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun Sky(hour: Float) {
    val colors = when {
        hour < 5f || hour >= 19f -> listOf(Color(0xFF060A1C), Color(0xFF1D2B5E))
        hour < 8f -> listOf(Color(0xFFE8788C), Color(0xFFFFB48A), Color(0xFFFFE3B0))
        hour < 16f -> listOf(Color(0xFF2F7FD6), Color(0xFF7EC4F5), Color(0xFFBFE6FF))
        else -> listOf(Color(0xFF34508F), Color(0xFFE9965A), Color(0xFFFFD28F))
    }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(colors))
        // sun from 6 am to 6 pm, moon from 6 pm to 6 am
        val t = ((hour - 6f + 24f) % 24f) / 12f
        val u = if (t < 1f) t else t - 1f
        val cx = size.width * (0.1f + 0.8f * u)
        val cy = size.height * (0.24f - 0.16f * sin(PI.toFloat() * u))
        drawCircle(
            if (t < 1f) Color(0xFFFFF3B0) else Color(0xFFEEF2FF),
            radius = 32.dp.toPx(),
            center = Offset(cx, cy)
        )
    }
}

@Composable
private fun PieceBlob(
    p: Piece,
    credit: Boolean,
    height: Dp,
    lifted: Boolean,
    delta: () -> Offset,
    onBounds: (Rect) -> Unit
) {
    val r = minOf(34.dp, height / 2)
    val shape = RoundedCornerShape(r)
    Box(
        Modifier.fillMaxWidth().height(height)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer {
                if (lifted) {
                    val d = delta()
                    translationX = d.x
                    translationY = d.y
                    scaleX = 0.8f
                    scaleY = 0.8f
                }
            }
            .drawBehind {
                drawRoundRect(Ink, topLeft = Offset(0f, 7.dp.toPx()), size = size, cornerRadius = CornerRadius(r.toPx()))
            }
            .background(if (credit) Color(0xFF6EE7A8) else Color(0xFFFFD84D), shape)
            .border(4.dp, Ink, shape),
        contentAlignment = Alignment.Center
    ) {
        if (height >= 60.dp) {
            Box(
                Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 10.dp)
                    .size(width = 20.dp, height = 10.dp)
                    .background(Color.White.copy(alpha = 0.85f), CircleShape)
            )
        }
        Text(signedRupees(p.amt, !credit), fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Ink)
    }
}

/** The six colored lines shown while a blob is held. The one you drag toward opens into its item segments. */
@Composable
private fun CategoryLines(sector: Int, item: Int, w: Float, h: Float) {
    val density = LocalDensity.current
    Categories.all.forEach { c ->
        val active = c.sector == sector
        val horizontal = c.sector == 0 || c.sector == 3
        val thick = with(density) { (if (!active) 8.dp else if (horizontal) 48.dp else 96.dp).toPx() }
        val r = lineRect(c.sector, w, h, thick)
        val shape = RoundedCornerShape(16.dp)
        val mod = Modifier
            .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
            .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
            .alpha(if (sector < 0 || active) 0.92f else 0.25f)
            .background(c.color, shape)
            .border(1.5.dp, Ink, shape)
        if (horizontal) {
            Row(mod) {
                c.items.forEachIndexed { i, name ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .background(if (active && i == item) Color.White.copy(alpha = 0.55f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (active) Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        } else {
            Column(mod) {
                c.items.forEachIndexed { i, name ->
                    Box(
                        Modifier.weight(1f).fillMaxWidth()
                            .background(if (active && i == item) Color.White.copy(alpha = 0.55f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (active) Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
    }
}

/** Shown after dropping a piece on an item when another piece is still unsorted: fine-tune the exact amount. */
@Composable
private fun ConfirmBar(pd: Pending, totalPaise: Long, onSave: (Long) -> Unit, onCancel: () -> Unit) {
    val base = pd.piece.amt / 100f
    // The cut can only be as exact as your swipe, so the range follows the size of the whole payment: +-15%
    val d = maxOf(5f, ceil(totalPaise / 100f * 0.15f))
    val lo = minOf(base, maxOf(1f, floor(base - d)))
    val hi = maxOf(base, minOf(ceil(base + d), floor(base + pd.neighbor.amt / 100f - 1f)))
    val adjustable = hi - lo >= 1f
    var v by remember(pd.piece.id) { mutableStateOf(base) }

    fun newAmt(): Long = if (!adjustable || abs(v - base) < 0.5f) pd.piece.amt else v.roundToLong() * 100L

    Column(Modifier.fillMaxWidth().background(Color(0xF00E121E), RoundedCornerShape(20.dp)).padding(14.dp)) {
        Text("${pd.item} in ${pd.cat.name}", color = pd.cat.color, fontWeight = FontWeight.SemiBold)
        Text(formatRupees(newAmt()), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        if (adjustable) {
            Slider(value = v, onValueChange = { v = it }, valueRange = lo..hi)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { v = (v.roundToLong() - 1L).toFloat().coerceIn(lo, hi) }) { Text("-1") }
                TextButton(onClick = { v = (v.roundToLong() + 1L).toFloat().coerceIn(lo, hi) }) { Text("+1") }
                Text(
                    "Next piece becomes ${formatRupees(pd.neighbor.amt - (newAmt() - pd.piece.amt))}",
                    fontSize = 12.sp, color = Color(0xFFB8C0D8)
                )
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Button(
                onClick = { onSave(newAmt()) },
                colors = ButtonDefaults.buttonColors(containerColor = pd.cat.color, contentColor = Ink)
            ) { Text("Save") }
        }
    }
}