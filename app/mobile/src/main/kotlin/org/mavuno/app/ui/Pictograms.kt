package org.mavuno.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import org.mavuno.app.ui.Palette.Cherry
import org.mavuno.app.ui.Palette.Ink
import org.mavuno.app.ui.Palette.Leaf
import org.mavuno.app.ui.Palette.LeafDeep
import org.mavuno.app.ui.Palette.Paper
import org.mavuno.app.ui.Palette.Sky
import org.mavuno.app.ui.Palette.Soil
import org.mavuno.app.ui.Palette.Sun

/** Flat two-tone pictograms, drawn on a 100×100 grid so answers can be read without reading. */
sealed interface Pic {
    data object Leaf : Pic
    data object Chat : Pic
    data object Drop : Pic
    data object SoilLayers : Pic
    data object Camera : Pic
    data class Tree(val size: Float) : Pic
    data class Scissors(val fresh: Float) : Pic
    data class Sack(val level: Float) : Pic
    data class SunClouds(val clouds: Int) : Pic
    data class Berries(val marks: Int, val holes: Boolean) : Pic
    data class Basket(val level: Float) : Pic
    data object Flower : Pic
    data object GreenBerries : Pic
    data object Question : Pic
    data object CloudUp : Pic
    data object Check : Pic
    data object Clock : Pic
    data object Sliders : Pic
    data object Officer : Pic
    data object Speaker : Pic
    data object Send : Pic
    data object Photos : Pic
    data object Trash : Pic
}

@Composable
fun Pictogram(pic: Pic, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val g = Grid(this)
        when (pic) {
            Pic.Leaf -> g.leaf(50f, 50f, 1f, Leaf)
            Pic.Chat -> g.chat()
            Pic.Drop -> g.drop(50f, 52f, 1f, Sky)
            Pic.SoilLayers -> g.soil()
            Pic.Camera -> g.camera()
            is Pic.Tree -> g.tree(pic.size)
            is Pic.Scissors -> g.scissors(pic.fresh)
            is Pic.Sack -> g.sack(pic.level)
            is Pic.SunClouds -> g.sunClouds(pic.clouds)
            is Pic.Berries -> g.berries(pic.marks, pic.holes)
            is Pic.Basket -> g.basket(pic.level)
            Pic.Flower -> g.flower()
            Pic.GreenBerries -> g.greenBerries()
            Pic.Question -> g.question()
            Pic.CloudUp -> g.cloudUp()
            Pic.Check -> g.check()
            Pic.Clock -> g.clock()
            Pic.Sliders -> g.sliders()
            Pic.Officer -> g.officer()
            Pic.Speaker -> g.speaker()
            Pic.Send -> g.send()
            Pic.Photos -> g.photos()
            Pic.Trash -> g.trash()
        }
    }
}

/** Maps every interview answer to a picture of that answer. */
fun answerPic(question: String, answer: String): Pic = when (question) {
    "q_tree_age" -> Pic.Tree(mapOf("lt5" to 0.4f, "5_15" to 0.6f, "15_30" to 0.8f, "gt30" to 1f).getValue(answer))
    "q_pruning" -> Pic.Scissors(mapOf("this_year" to 1f, "last_year" to 0.7f, "longer" to 0.35f, "never" to 0f).getValue(answer))
    "q_fertilizer" -> Pic.Sack(mapOf("yes" to 0.9f, "little" to 0.35f, "no" to 0f).getValue(answer))
    "q_shade" -> Pic.SunClouds(mapOf("none" to 0, "some" to 1, "heavy" to 3).getValue(answer))
    "q_berry_spots" -> Pic.Berries(mapOf("many" to 6, "few" to 2, "none" to 0).getValue(answer), holes = false)
    "q_berry_holes" -> Pic.Berries(mapOf("many" to 3, "few" to 1, "none" to 0).getValue(answer), holes = true)
    "q_yield_change" -> Pic.Basket(mapOf("half_or_less" to 0.3f, "somewhat_less" to 0.65f, "same" to 1f).getValue(answer))
    "q_when_noticed" -> when (answer) {
        "flowering" -> Pic.Flower
        "berry_growth" -> Pic.GreenBerries
        else -> Pic.Basket(1f)
    }
    else -> Pic.Question
}

private class Grid(val d: DrawScope) {
    val s = d.size.minDimension / 100f
    val ox = (d.size.width - 100f * s) / 2
    val oy = (d.size.height - 100f * s) / 2
    fun o(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    fun sz(w: Float, h: Float) = Size(w * s, h * s)
    fun r(v: Float) = v * s
    fun stroke(w: Float) = Stroke(width = w * s, cap = StrokeCap.Round)
    fun path(block: Path.() -> Unit) = Path().apply(block)
    fun Path.m(x: Float, y: Float) = moveTo(ox + x * s, oy + y * s)
    fun Path.l(x: Float, y: Float) = lineTo(ox + x * s, oy + y * s)
    fun Path.q(cx: Float, cy: Float, x: Float, y: Float) = quadraticTo(ox + cx * s, oy + cy * s, ox + x * s, oy + y * s)
    fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) =
        cubicTo(ox + x1 * s, oy + y1 * s, ox + x2 * s, oy + y2 * s, ox + x * s, oy + y * s)

    fun circle(x: Float, y: Float, rad: Float, color: Color) = d.drawCircle(color, r(rad), o(x, y))
    fun ring(x: Float, y: Float, rad: Float, color: Color, w: Float) = d.drawCircle(color, r(rad), o(x, y), style = stroke(w))
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, w: Float) = d.drawLine(color, o(x1, y1), o(x2, y2), r(w), StrokeCap.Round)
    fun rect(x: Float, y: Float, w: Float, h: Float, rad: Float, color: Color) =
        d.drawRoundRect(color, o(x, y), sz(w, h), CornerRadius(r(rad), r(rad)))

    /** Arabica leaf: pointed oval with a pale midrib and paired veins. */
    fun leaf(cx: Float, cy: Float, k: Float, color: Color) {
        val h = 42f * k
        val w = 26f * k
        d.drawPath(path { m(cx, cy - h); c(cx + w, cy - h * 0.55f, cx + w, cy + h * 0.55f, cx, cy + h); c(cx - w, cy + h * 0.55f, cx - w, cy - h * 0.55f, cx, cy - h); close() }, color)
        line(cx, cy - h * 0.8f, cx, cy + h * 0.9f, Paper.copy(alpha = 0.85f), 3f * k)
        for (i in -1..1) {
            val y = cy + i * h * 0.35f
            line(cx, y, cx + w * 0.55f, y - h * 0.18f, Paper.copy(alpha = 0.6f), 2.2f * k)
            line(cx, y, cx - w * 0.55f, y - h * 0.18f, Paper.copy(alpha = 0.6f), 2.2f * k)
        }
    }

    fun drop(cx: Float, cy: Float, k: Float, color: Color) {
        d.drawPath(path { m(cx, cy - 38f * k); c(cx + 8f * k, cy - 22f * k, cx + 28f * k, cy - 2f * k, cx + 28f * k, cy + 14f * k); c(cx + 28f * k, cy + 30f * k, cx + 15f * k, cy + 40f * k, cx, cy + 40f * k); c(cx - 15f * k, cy + 40f * k, cx - 28f * k, cy + 30f * k, cx - 28f * k, cy + 14f * k); c(cx - 28f * k, cy - 2f * k, cx - 8f * k, cy - 22f * k, cx, cy - 38f * k); close() }, color)
        d.drawCircle(Paper.copy(alpha = 0.55f), r(6f * k), o(cx - 11f * k, cy + 14f * k))
    }

    fun chat() {
        rect(10f, 16f, 80f, 54f, 18f, Sun)
        d.drawPath(path { m(28f, 66f); l(22f, 86f); l(44f, 68f); close() }, Sun)
        for (x in listOf(32f, 50f, 68f)) circle(x, 43f, 6f, Ink)
    }

    fun soil() {
        rect(8f, 52f, 84f, 14f, 7f, Soil)
        rect(8f, 68f, 84f, 12f, 6f, Soil.copy(alpha = 0.7f))
        rect(8f, 82f, 84f, 10f, 5f, Soil.copy(alpha = 0.45f))
        line(50f, 52f, 50f, 30f, Leaf, 4f)
        leafTilt(50f, 28f, -1)
        leafTilt(50f, 36f, 1)
    }

    private fun leafTilt(x: Float, y: Float, dir: Int) {
        d.drawPath(path { m(x, y); q(x + dir * 10f, y - 16f, x + dir * 22f, y - 10f); q(x + dir * 12f, y + 2f, x, y); close() }, Leaf)
    }

    fun camera() {
        rect(8f, 28f, 84f, 56f, 14f, Ink)
        rect(32f, 18f, 36f, 16f, 6f, Ink)
        circle(50f, 56f, 19f, Paper)
        circle(50f, 56f, 12f, Leaf)
        circle(77f, 40f, 4f, Sun)
    }

    fun tree(k: Float) {
        val base = 92f
        val h = 78f * k
        rect(46f, base - h * 0.45f, 8f, h * 0.45f, 3f, Soil)
        val cy = base - h * 0.62f
        val rad = 30f * k
        circle(50f - rad * 0.55f, cy + rad * 0.2f, rad * 0.62f, Leaf)
        circle(50f + rad * 0.55f, cy + rad * 0.2f, rad * 0.62f, Leaf)
        circle(50f, cy - rad * 0.25f, rad * 0.72f, LeafDeep.copy(alpha = 0.9f))
        if (k >= 0.8f) for ((x, y) in listOf(-0.4f to 0.1f, 0.35f to -0.15f, 0.1f to 0.35f)) circle(50f + x * rad, cy + y * rad, 3.5f * k, Cherry)
        rect(20f, base, 60f, 3f, 1.5f, Soil.copy(alpha = 0.35f))
    }

    fun scissors(fresh: Float) {
        val blade = Color(0xFF8A9A90)
        line(30f, 72f, 74f, 22f, blade, 7f)
        line(70f, 72f, 26f, 22f, blade, 7f)
        ring(27f, 78f, 11f, Leaf, 6f)
        ring(73f, 78f, 11f, Leaf, 6f)
        circle(50f, 48f, 4f, Ink)
        // Freshness badge: a full sun-yellow disc for "this year", empty for "never".
        ring(80f, 18f, 12f, Sun, 3f)
        if (fresh > 0f) d.drawArc(Sun, -90f, 360f * fresh, true, o(68f, 6f), sz(24f, 24f))
    }

    fun sack(level: Float) {
        val sack = path { m(28f, 30f); c(14f, 50f, 14f, 92f, 50f, 92f); c(86f, 92f, 86f, 50f, 72f, 30f); close() }
        d.drawPath(sack, Color(0xFFE9DCC4))
        if (level > 0f) clip(92f - 60f * level) { d.drawPath(sack, Soil.copy(alpha = 0.85f)) }
        d.drawPath(sack, Soil, style = stroke(3f))
        rect(30f, 20f, 40f, 12f, 6f, Soil)
        line(40f, 14f, 50f, 22f, Soil, 3f)
        line(60f, 14f, 50f, 22f, Soil, 3f)
    }

    private fun clip(top: Float, block: () -> Unit) {
        d.clipRect(top = oy + top * s) { block() }
    }

    fun sunClouds(clouds: Int) {
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            line(36f + 22f * kotlin.math.cos(a).toFloat(), 36f + 22f * kotlin.math.sin(a).toFloat(), 36f + 30f * kotlin.math.cos(a).toFloat(), 36f + 30f * kotlin.math.sin(a).toFloat(), Sun, 4f)
        }
        circle(36f, 36f, 16f, Sun)
        val spots = listOf(Triple(62f, 52f, 1f), Triple(40f, 70f, 0.9f), Triple(70f, 78f, 0.85f))
        spots.take(clouds).forEach { (x, y, k) -> cloud(x, y, k, Color(0xFFB9C6CF)) }
    }

    fun cloud(x: Float, y: Float, k: Float, color: Color) {
        circle(x - 12f * k, y + 4f * k, 11f * k, color)
        circle(x, y - 4f * k, 15f * k, color)
        circle(x + 14f * k, y + 4f * k, 11f * k, color)
        rect(x - 23f * k, y + 2f * k, 48f * k, 13f * k, 6f * k, color)
    }

    fun berries(marks: Int, holes: Boolean) {
        line(50f, 10f, 50f, 34f, LeafDeep, 4f)
        val pos = listOf(Offset(34f, 52f), Offset(66f, 52f), Offset(50f, 74f))
        pos.forEach { p -> circle(p.x, p.y, 17f, if (holes) Cherry else Leaf) }
        pos.forEach { p -> circle(p.x - 5f, p.y - 6f, 4f, Paper.copy(alpha = 0.45f)) }
        val dots = listOf(Offset(38f, 56f), Offset(62f, 48f), Offset(52f, 78f), Offset(28f, 48f), Offset(70f, 58f), Offset(46f, 70f))
        dots.take(marks).forEach { p ->
            if (holes) { circle(p.x, p.y, 4.5f, Ink); ring(p.x, p.y, 6f, Palette.Soil, 1.5f) } else circle(p.x, p.y, 5f, Ink.copy(alpha = 0.8f))
        }
    }

    fun basket(level: Float) {
        // Cherries heaped above the rim: more cherries for a fuller harvest.
        val heap = listOf(30f to 48f, 42f to 48f, 54f to 48f, 66f to 48f, 36f to 38f, 48f to 38f, 60f to 38f)
        heap.take((level * heap.size).toInt().coerceIn(1, heap.size)).forEach { (x, y) -> circle(x, y, 7f, Cherry) }
        d.drawPath(path { m(16f, 52f); l(84f, 52f); l(74f, 90f); l(26f, 90f); close() }, Color(0xFFC98B4E))
        for (y in listOf(62f, 72f, 82f)) line(20f, y, 80f, y, Soil.copy(alpha = 0.55f), 2.5f)
    }

    fun flower() {
        line(50f, 56f, 50f, 94f, Leaf, 4f)
        leafTilt(50f, 80f, 1)
        for (i in 0 until 5) {
            val a = Math.toRadians(i * 72.0 - 90)
            circle(50f + 16f * kotlin.math.cos(a).toFloat(), 38f + 16f * kotlin.math.sin(a).toFloat(), 12f, Color(0xFFFFFBF0))
            ring(50f + 16f * kotlin.math.cos(a).toFloat(), 38f + 16f * kotlin.math.sin(a).toFloat(), 12f, Color(0xFFE1D6BE), 1.5f)
        }
        circle(50f, 38f, 8f, Sun)
    }

    fun greenBerries() {
        line(10f, 30f, 90f, 30f, Soil, 5f)
        for ((x, y) in listOf(30f to 46f, 44f to 52f, 58f to 46f, 72f to 52f, 38f to 66f, 64f to 66f)) {
            circle(x, y, 10f, Leaf)
            circle(x - 3f, y - 3f, 2.5f, Paper.copy(alpha = 0.5f))
        }
        leafTilt(20f, 30f, -1)
    }

    fun question() {
        circle(50f, 50f, 40f, Palette.LeafMist)
        d.drawPath(path { m(36f, 38f); c(36f, 22f, 64f, 22f, 64f, 38f); c(64f, 50f, 50f, 50f, 50f, 62f) }, Ink, style = stroke(8f))
        circle(50f, 76f, 5f, Ink)
    }

    fun cloudUp() {
        cloud(50f, 50f, 1.3f, Sky)
        line(50f, 72f, 50f, 46f, Paper, 6f)
        line(50f, 44f, 40f, 54f, Paper, 6f)
        line(50f, 44f, 60f, 54f, Paper, 6f)
    }

    fun check() {
        circle(50f, 50f, 40f, Leaf)
        d.drawPath(path { m(30f, 52f); l(44f, 66f); l(70f, 36f) }, Paper, style = stroke(9f))
    }

    fun clock() {
        circle(50f, 50f, 38f, Palette.LeafMist)
        ring(50f, 50f, 38f, Leaf, 6f)
        line(50f, 50f, 50f, 28f, Ink, 6f)
        line(50f, 50f, 66f, 58f, Ink, 6f)
    }

    fun sliders() {
        for ((y, x) in listOf(28f to 34f, 50f to 64f, 72f to 44f)) {
            line(16f, y, 84f, y, Palette.InkSoft, 5f)
            circle(x, y, 9f, Leaf)
            circle(x, y, 4f, Paper)
        }
    }

    fun officer() {
        circle(50f, 34f, 17f, Color(0xFF8D5A3B))
        rect(30f, 12f, 40f, 12f, 6f, Leaf)
        rect(24f, 20f, 52f, 6f, 3f, LeafDeep)
        d.drawPath(path { m(18f, 96f); c(18f, 64f, 30f, 56f, 50f, 56f); c(70f, 56f, 82f, 64f, 82f, 96f); close() }, Leaf)
        rect(56f, 70f, 16f, 20f, 3f, Paper)
        line(59f, 76f, 69f, 76f, Ink, 2f)
        line(59f, 82f, 69f, 82f, Ink, 2f)
    }

    fun speaker() {
        d.drawPath(path { m(16f, 40f); l(32f, 40f); l(52f, 22f); l(52f, 78f); l(32f, 60f); l(16f, 60f); close() }, Leaf)
        d.drawArc(Leaf, -45f, 90f, false, o(46f, 32f), sz(24f, 36f), style = stroke(6f))
        d.drawArc(Leaf, -50f, 100f, false, o(46f, 18f), sz(40f, 64f), style = stroke(6f))
    }

    fun send() {
        d.drawPath(path { m(10f, 48f); l(90f, 14f); l(62f, 88f); l(48f, 58f); close() }, Leaf)
        d.drawPath(path { m(48f, 58f); l(90f, 14f); l(56f, 66f); close() }, LeafDeep)
    }

    fun photos() {
        rect(24f, 14f, 62f, 50f, 8f, Palette.LeafMist)
        rect(14f, 32f, 62f, 54f, 8f, Leaf)
        d.drawPath(path { m(20f, 80f); l(38f, 58f); l(50f, 70f); l(58f, 62f); l(72f, 80f); close() }, Paper.copy(alpha = 0.9f))
        circle(60f, 46f, 6f, Sun)
    }

    fun trash() {
        rect(24f, 30f, 52f, 60f, 6f, Cherry)
        rect(16f, 20f, 68f, 10f, 5f, Cherry)
        rect(40f, 12f, 20f, 10f, 4f, Cherry)
        for (x in listOf(38f, 50f, 62f)) line(x, 42f, x, 80f, Paper.copy(alpha = 0.8f), 4f)
    }
}
