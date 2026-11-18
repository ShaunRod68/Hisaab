package com.example.expensetracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One bar per value, the tallest highlighted. Tap a bar to select it. Labels sit under the bars. */
@Composable
fun BarChart(
    values: List<Long>,
    labels: List<String>,
    color: Color,
    highlight: Color,
    selected: Int,
    selectedColor: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val top = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val topIndex = values.indexOf(values.maxOrNull() ?: 0L)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (v > 0L) {
                        Box(
                            Modifier.padding(horizontal = 1.5.dp).fillMaxWidth()
                                .fillMaxHeight(maxOf(v.toFloat() / top, 0.02f))
                                .background(
                                    if (i == selected) selectedColor else if (i == topIndex) highlight else color,
                                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                )
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        label, fontSize = 10.sp, maxLines = 1, softWrap = false,
                        modifier = Modifier.wrapContentWidth(unbounded = true)
                    )
                }
            }
        }
    }
}

/** Ring split into slices, with two lines of text in the middle. */
@Composable
fun DonutChart(
    slices: List<Pair<Long, Color>>,
    centerTop: String,
    centerBottom: String,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.first }
    Box(modifier.size(170.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 26.dp.toPx()
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (total <= 0L) {
                drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            } else {
                var start = -90f
                slices.forEach { (v, c) ->
                    val sweep = 360f * v / total
                    if (sweep > 0f) {
                        drawArc(c, start, maxOf(sweep - 1.5f, 0.5f), false, topLeft, arcSize, style = Stroke(stroke))
                    }
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(centerBottom, fontSize = 11.sp)
        }
    }
}

/** Running total through the period (solid) against the whole previous period (dashed). */
@Composable
fun PaceChart(
    current: List<Long>,
    previous: List<Long>,
    lineColor: Color,
    previousColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.fillMaxWidth().height(130.dp)) {
        val top = maxOf(current.maxOrNull() ?: 0L, previous.maxOrNull() ?: 0L, 1L).toFloat()
        val n = maxOf(current.size, previous.size, 2)
        val pad = 6.dp.toPx()
        val w = size.width - pad * 2f
        val h = size.height - pad * 2f

        fun px(i: Int) = pad + w * i / (n - 1)
        fun py(v: Long) = pad + h * (1f - v / top)
        fun line(vs: List<Long>): Path {
            val path = Path()
            vs.forEachIndexed { i, v -> if (i == 0) path.moveTo(px(i), py(v)) else path.lineTo(px(i), py(v)) }
            return path
        }

        for (g in 0..3) {
            val yy = pad + h * g / 3f
            drawLine(gridColor, Offset(pad, yy), Offset(pad + w, yy), strokeWidth = 1f)
        }
        if (previous.isNotEmpty()) {
            drawPath(
                line(previous), previousColor,
                style = Stroke(
                    width = 3.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
                )
            )
        }
        if (current.isNotEmpty()) {
            drawPath(line(current), lineColor, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(lineColor, 5.dp.toPx(), Offset(px(current.lastIndex), py(current.last())))
        }
    }
}