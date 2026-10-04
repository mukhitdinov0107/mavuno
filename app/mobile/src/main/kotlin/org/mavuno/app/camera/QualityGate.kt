package org.mavuno.app.camera

import android.graphics.Bitmap
import android.graphics.Color

enum class PhotoQuality(val stringId: String?) {
    OK(null),
    BLURRY("photos.retake_blurry"),
    DARK("photos.retake_dark"),
    NO_LEAF("photos.retake_no_leaf"),
}

/**
 * Instant checks before a photo is kept (PRD 5, step 3). Cheap heuristics on a 256 px copy;
 * the classifier's "other" class is the real not-a-leaf gate. Thresholds need tuning on field photos.
 */
object QualityGate {
    private const val SIZE = 256
    private const val MIN_MEAN_LUMA = 45.0
    private const val MIN_LAPLACIAN_VARIANCE = 60.0
    private const val MIN_PLANT_SHARE = 0.20

    fun check(bitmap: Bitmap): PhotoQuality {
        val scale = SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val small = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(8), (bitmap.height * scale).toInt().coerceAtLeast(8), true)
        val w = small.width
        val h = small.height
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }

        val luma = DoubleArray(px.size) { i -> 0.299 * Color.red(px[i]) + 0.587 * Color.green(px[i]) + 0.114 * Color.blue(px[i]) }
        if (luma.average() < MIN_MEAN_LUMA) return PhotoQuality.DARK

        if (laplacianVariance(luma, w, h) < MIN_LAPLACIAN_VARIANCE) return PhotoQuality.BLURRY

        val hsv = FloatArray(3)
        val plant = px.count { c ->
            Color.colorToHSV(c, hsv)
            // Yellow-green through green, with some colour: leaves, including yellowing and rusty ones.
            hsv[0] in 35f..170f && hsv[1] > 0.18f && hsv[2] > 0.15f
        }
        if (plant.toDouble() / px.size < MIN_PLANT_SHARE) return PhotoQuality.NO_LEAF

        return PhotoQuality.OK
    }

    private fun laplacianVariance(luma: DoubleArray, w: Int, h: Int): Double {
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val lap = luma[i - 1] + luma[i + 1] + luma[i - w] + luma[i + w] - 4 * luma[i]
            sum += lap
            sumSq += lap * lap
            n++
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }
}
