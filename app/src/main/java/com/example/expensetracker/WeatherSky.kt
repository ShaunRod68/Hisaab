package com.example.expensetracker

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class Wx(val label: String) {
    CLEAR("Clear"), PARTLY("Partly cloudy"), OVERCAST("Overcast"), FOG("Fog"),
    DRIZZLE("Drizzle"), RAIN("Rain"), STORM("Thunderstorm"), SNOW("Snow")
}

/** WMO weather codes from the weather service. Null or -1 means no weather was saved for the payment. */
fun wxOf(code: Int?): Wx? = when (code) {
    null, -1 -> null
    0, 1 -> Wx.CLEAR
    2 -> Wx.PARTLY
    3 -> Wx.OVERCAST
    45, 48 -> Wx.FOG
    in 51..57 -> Wx.DRIZZLE
    in 61..67, in 80..82 -> Wx.RAIN
    in 71..77, 85, 86 -> Wx.SNOW
    95, 96, 99 -> Wx.STORM
    else -> Wx.PARTLY
}

private class Star(val x: Float, val y: Float, val r: Float, val k: Int)
private class CloudSpec(val seed: Float, val y: Float, val w: Float, val k: Int)
private class Drop(val x: Float, val y: Float, val len: Float, val k: Int)

// Fixed random layouts, so the sky looks the same every time
private val rnd = Random(7)
private val stars = List(46) { Star(rnd.nextFloat(), rnd.nextFloat() * 0.6f, 0.6f + rnd.nextFloat() * 1.4f, 10 + rnd.nextInt(20)) }
private val cloudSpecs = List(9) {
    CloudSpec(rnd.nextFloat(), 0.05f + rnd.nextFloat() * 0.38f, 0.26f + rnd.nextFloat() * 0.26f, 1 + rnd.nextInt(3))
}
private val drops = List(110) { Drop(rnd.nextFloat(), rnd.nextFloat(), 0.025f + rnd.nextFloat() * 0.03f, 1 + rnd.nextInt(2)) }

private fun Color.a(x: Float) = copy(alpha = alpha * x)

private fun skyColors(hour: Float): List<Color> = when {
    hour < 5f || hour >= 19f -> listOf(Color(0xFF060A1C), Color(0xFF1D2B5E))
    hour < 8f -> listOf(Color(0xFFE8788C), Color(0xFFFFB48A), Color(0xFFFFE3B0))
    hour < 16f -> listOf(Color(0xFF2F7FD6), Color(0xFF7EC4F5), Color(0xFFBFE6FF))
    else -> listOf(Color(0xFF34508F), Color(0xFFE9965A), Color(0xFFFFD28F))
}

private fun grayAmount(wx: Wx) = when (wx) {
    Wx.CLEAR -> 0f
    Wx.PARTLY -> 0.05f
    Wx.OVERCAST -> 0.45f
    Wx.FOG -> 0.55f
    Wx.DRIZZLE -> 0.5f
    Wx.RAIN -> 0.6f
    Wx.STORM -> 0.78f
    Wx.SNOW -> 0.4f
}

private fun cloudCount(wx: Wx) = when (wx) {
    Wx.CLEAR -> 2
    Wx.PARTLY -> 4
    Wx.OVERCAST -> 9
    Wx.FOG -> 3
    Wx.DRIZZLE -> 8
    Wx.RAIN -> 9
    Wx.STORM -> 9
    Wx.SNOW -> 7
}

private fun orbAlpha(wx: Wx) = when (wx) {
    Wx.CLEAR, Wx.PARTLY -> 1f
    Wx.OVERCAST -> 0.25f
    Wx.FOG, Wx.DRIZZLE -> 0.3f
    Wx.SNOW -> 0.2f
    Wx.RAIN, Wx.STORM -> 0f
}

/** Cloud body color and its darker underside, for the time of day and weather. */
private fun cloudColors(hour: Float, wx: Wx): Pair<Color, Color> {
    val night = hour < 5f || hour >= 19f
    val heavy = wx == Wx.OVERCAST || wx == Wx.DRIZZLE || wx == Wx.RAIN || wx == Wx.STORM
    return when {
        wx == Wx.STORM -> Color(0xFF4A5063) to Color(0xFF353A4A)
        heavy && night -> Color(0xFF4B5470) to Color(0xFF39405A)
        heavy -> Color(0xFFAEB7C6) to Color(0xFF8D97A8)
        night -> Color(0xFF6A769E) to Color(0xFF4F5A82)
        hour < 8f -> Color(0xFFFFE0D6) to Color(0xFFF0B9B2)
        hour >= 16f -> Color(0xFFFFE9D2) to Color(0xFFF2B98F)
        else -> Color.White to Color(0xFFCFE3F5)
    }
}

/**
 * Sky for the bubble screen: time-of-day gradient, detailed sun and moon, drifting clouds and the weather.
 * Everything is drawn in one canvas on its own layer, and the clocks are only read while drawing, so
 * nothing else on screen is redrawn. While paused (you are cutting or holding a blob) the animation freezes.
 */
@Composable
fun WeatherSky(hour: Float, wx: Wx, paused: Boolean) {
    val clock = rememberInfiniteTransition(label = "sky")
    val slow = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(120_000, easing = LinearEasing)), label = "slow")
    val fast = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "fast")
    val frozen = remember { floatArrayOf(0f, 0f) }
    val night = hour < 5f || hour >= 19f
    val gray = if (night) Color(0xFF181D2A) else Color(0xFF8A94A3)
    val colors = skyColors(hour).map { lerp(it, gray, grayAmount(wx)) }
    val cloudPair = cloudColors(hour, wx)

    Canvas(Modifier.fillMaxSize().graphicsLayer { }) {
        val t = if (paused) frozen[0] else slow.value.also { frozen[0] = it }
        val f = if (paused) frozen[1] else fast.value.also { frozen[1] = it }
        val w = size.width
        val h = size.height

        drawRect(Brush.verticalGradient(colors))

        if (night && (wx == Wx.CLEAR || wx == Wx.PARTLY)) {
            stars.forEach { s ->
                val twinkle = 0.55f + 0.45f * sin(2f * PI.toFloat() * (t * s.k + s.x))
                drawCircle(Color.White.a(twinkle), s.r.dp.toPx(), Offset(s.x * w, s.y * h))
            }
        }

        // sun from 6 am to 6 pm, moon from 6 pm to 6 am
        val phase = ((hour - 6f + 24f) % 24f) / 12f
        val u = if (phase < 1f) phase else phase - 1f
        val orb = Offset(w * (0.12f + 0.76f * u), h * (0.26f - 0.17f * sin(PI.toFloat() * u)))
        val oa = orbAlpha(wx)
        if (oa > 0f) {
            if (phase < 1f) drawSun(orb, 34.dp.toPx(), t, oa) else drawMoon(orb, 30.dp.toPx(), oa)
        }

        for (i in 0 until cloudCount(wx)) {
            val cl = cloudSpecs[i]
            val cw = cl.w * w
            val x = ((cl.seed + t * cl.k) % 1f) * (w + cw) - cw
            val bob = sin(2f * PI.toFloat() * (t * 8f + cl.seed)) * 4.dp.toPx()
            drawCloud(x, cl.y * h + bob, cw, cloudPair.first, cloudPair.second)
        }

        when (wx) {
            Wx.DRIZZLE -> drawRain(f, w, h, 40, 0.55f, Color(0xCCDDEBFF), 1.6f.dp.toPx())
            Wx.RAIN -> drawRain(f, w, h, 80, 1f, Color(0xDDDDEBFF), 2f.dp.toPx())
            Wx.STORM -> {
                drawRain(f, w, h, 110, 1.35f, Color(0xDDCFDDFF), 2.2f.dp.toPx())
                drawLightning(t, w, h)
            }
            Wx.SNOW -> drawSnow(t, w, h)
            Wx.FOG -> drawFog(t, w, h)
            else -> {}
        }
    }
}

private fun DrawScope.drawSun(c: Offset, r: Float, t: Float, a: Float) {
    // soft glow
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0x99FFE9A0).a(a), Color(0x00FFE9A0)), center = c, radius = r * 3.4f),
        radius = r * 3.4f, center = c
    )
    // rays, slowly turning
    val rays = 12
    for (i in 0 until rays) {
        val ang = (i / rays.toFloat() + t * 2f) * 2f * PI.toFloat()
        val inner = r * 1.25f
        val outer = r * (if (i % 2 == 0) 1.95f else 1.65f)
        drawLine(
            Color(0xFFFFC93C).a(a),
            c + Offset(cos(ang) * inner, sin(ang) * inner),
            c + Offset(cos(ang) * outer, sin(ang) * outer),
            strokeWidth = r * 0.17f, cap = StrokeCap.Round
        )
    }
    // shaded disc, outline and highlight
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFF7C2).a(a), Color(0xFFFFD84A).a(a), Color(0xFFFFB72E).a(a)),
            center = c - Offset(r * 0.25f, r * 0.25f), radius = r * 1.5f
        ),
        radius = r, center = c
    )
    drawCircle(Color(0xFF1B1B2F).a(a), r, c, style = Stroke(r * 0.09f))
    drawCircle(Color.White.a(0.5f * a), r * 0.26f, c - Offset(r * 0.4f, r * 0.4f))
}

private fun DrawScope.drawMoon(c: Offset, r: Float, a: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0x66C8D6FF).a(a), Color(0x00C8D6FF)), center = c, radius = r * 3f),
        radius = r * 3f, center = c
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFFFF2).a(a), Color(0xFFDCE1F4).a(a)),
            center = c - Offset(r * 0.3f, r * 0.3f), radius = r * 1.6f
        ),
        radius = r, center = c
    )
    // craters
    listOf(
        Triple(-0.35f, -0.2f, 0.22f), Triple(0.28f, -0.38f, 0.14f), Triple(0.32f, 0.25f, 0.2f),
        Triple(-0.2f, 0.42f, 0.12f), Triple(0.0f, 0.02f, 0.1f)
    ).forEach { (dx, dy, rr) ->
        val cc = c + Offset(dx * r, dy * r)
        drawCircle(Color(0x44808CB0).a(a), r * rr, cc)
        drawCircle(Color.White.a(0.35f * a), r * rr * 0.7f, cc - Offset(r * rr * 0.2f, r * rr * 0.2f))
    }
    drawCircle(Color(0xFF1B1B2F).a(a), r, c, style = Stroke(r * 0.09f))
}

private fun DrawScope.drawCloud(x: Float, y: Float, w: Float, body: Color, under: Color) {
    val h = w * 0.42f
    fun shape(dy: Float, color: Color) {
        drawRoundRect(color, Offset(x, y + h * 0.55f + dy), Size(w, h * 0.45f), CornerRadius(h * 0.225f))
        drawCircle(color, h * 0.45f, Offset(x + w * 0.30f, y + h * 0.58f + dy))
        drawCircle(color, h * 0.60f, Offset(x + w * 0.55f, y + h * 0.45f + dy))
        drawCircle(color, h * 0.40f, Offset(x + w * 0.78f, y + h * 0.62f + dy))
    }
    shape(h * 0.09f, under)
    shape(0f, body)
}

private fun DrawScope.drawRain(f: Float, w: Float, h: Float, count: Int, lenScale: Float, color: Color, stroke: Float) {
    for (i in 0 until count) {
        val d = drops[i]
        val y = ((d.y + f * d.k) % 1f) * (h + 40f) - 20f
        val x = d.x * w - h * 0.1f + y * 0.18f
        val len = d.len * h * lenScale
        drawLine(color, Offset(x, y), Offset(x - len * 0.2f, y + len), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawSnow(t: Float, w: Float, h: Float) {
    for (i in 0 until 60) {
        val d = drops[i]
        val y = ((d.y + t * 40f) % 1f) * (h + 20f) - 10f
        val x = d.x * w + sin(2f * PI.toFloat() * (t * 20f + d.y * 3f)) * 12.dp.toPx()
        drawCircle(Color.White.a(0.9f), (1.5f + d.len * 50f).dp.toPx(), Offset(x, y))
    }
}

private fun DrawScope.drawFog(t: Float, w: Float, h: Float) {
    for (i in 0..3) {
        val cy = h * (0.35f + 0.16f * i) + sin(2f * PI.toFloat() * (t * 6f + i * 0.25f)) * h * 0.012f
        val band = h * 0.14f
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color(0x66FFFFFF), Color.Transparent),
                startY = cy - band, endY = cy + band
            ),
            topLeft = Offset(0f, cy - band), size = Size(w, band * 2f)
        )
    }
}

private fun DrawScope.drawLightning(t: Float, w: Float, h: Float) {
    val cycle = (t * 40f).toInt()
    if (cycle % 3 != 0) return
    val ph = (t * 40f) % 1f
    if (!(ph < 0.04f || (ph > 0.08f && ph < 0.12f))) return
    drawRect(Color.White.a(0.3f))
    val bx = w * (0.25f + 0.5f * ((cycle % 7) / 7f))
    val bolt = Path()
    bolt.moveTo(bx, 0f)
    bolt.lineTo(bx - 0.05f * w, h * 0.12f)
    bolt.lineTo(bx + 0.02f * w, h * 0.13f)
    bolt.lineTo(bx - 0.07f * w, h * 0.30f)
    bolt.lineTo(bx + 0.01f * w, h * 0.31f)
    bolt.lineTo(bx - 0.10f * w, h * 0.50f)
    drawPath(bolt, Color(0xFFFFF3A0), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}