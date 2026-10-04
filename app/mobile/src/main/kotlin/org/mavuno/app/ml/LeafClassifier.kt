package org.mavuno.app.ml

import android.content.Context
import android.graphics.Bitmap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mavuno.fusion.LeafClass
import org.mavuno.fusion.PhotoObservation
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/** On-device leaf classifier (PRD 7.1). Returns calibrated class probabilities. */
interface LeafClassifier {
    val available: Boolean
    val version: String

    /** Null when no model is installed. */
    fun classify(bitmap: Bitmap): PhotoObservation?

    companion object {
        const val MODEL_ASSET = "content/model/leaf.tflite"
        const val META_ASSET = "content/model/leaf_model.json"

        fun load(context: Context): LeafClassifier =
            runCatching { TfliteLeafClassifier(context) }.getOrElse { NoModelClassifier }
    }
}

/** Used until ml/ produces a model. Photos are kept but are never evidence, so fusion abstains. */
object NoModelClassifier : LeafClassifier {
    override val available = false
    override val version = "none"
    override fun classify(bitmap: Bitmap): PhotoObservation? = null
}

/** Written by ml/export next to leaf.tflite. */
@Serializable
data class LeafModelMeta(
    val version: String,
    val labels: List<String>,
    @SerialName("input_size") val inputSize: Int = 224,
    /** Real-valued pixel range the model was trained on, e.g. [0, 255] or [-1, 1]. */
    @SerialName("input_range") val inputRange: List<Float> = listOf(0f, 255f),
    /** "logits" or "probs". */
    val output: String = "logits",
    /** Fitted by temperature scaling on the validation split. */
    val temperature: Double = 1.0,
)

class TfliteLeafClassifier(context: Context) : LeafClassifier {
    private val meta: LeafModelMeta =
        Json { ignoreUnknownKeys = true }.decodeFromString(context.assets.open(LeafClassifier.META_ASSET).bufferedReader().readText())
    private val labels = meta.labels.map(LeafClass::fromId)
    private val interpreter: Interpreter = context.assets.openFd(LeafClassifier.MODEL_ASSET).use { fd ->
        FileInputStream(fd.fileDescriptor).channel.use { ch ->
            Interpreter(ch.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength), Interpreter.Options().setNumThreads(2))
        }
    }

    override val available = true
    override val version: String = meta.version

    @Synchronized
    override fun classify(bitmap: Bitmap): PhotoObservation {
        val input = interpreter.getInputTensor(0)
        val output = interpreter.getOutputTensor(0)
        val size = meta.inputSize
        val scaled = downscale(centerSquare(bitmap), size)
        val pixels = IntArray(size * size).also { scaled.getPixels(it, 0, size, 0, 0, size, size) }

        val (lo, hi) = meta.inputRange
        val inQ = input.quantizationParams()
        val buf = ByteBuffer.allocateDirect(input.numBytes()).order(ByteOrder.nativeOrder())
        for (px in pixels) {
            for (channel in intArrayOf((px shr 16) and 0xFF, (px shr 8) and 0xFF, px and 0xFF)) {
                val real = lo + channel / 255f * (hi - lo)
                when (input.dataType()) {
                    DataType.FLOAT32 -> buf.putFloat(real)
                    DataType.UINT8 -> buf.put((real / inQ.scale + inQ.zeroPoint).roundToInt().coerceIn(0, 255).toByte())
                    DataType.INT8 -> buf.put((real / inQ.scale + inQ.zeroPoint).roundToInt().coerceIn(-128, 127).toByte())
                    else -> error("Unsupported input type ${input.dataType()}")
                }
            }
        }
        buf.rewind()

        val outBuf = ByteBuffer.allocateDirect(output.numBytes()).order(ByteOrder.nativeOrder())
        interpreter.run(buf, outBuf)
        outBuf.rewind()
        val outQ = output.quantizationParams()
        val raw = DoubleArray(labels.size) {
            when (output.dataType()) {
                DataType.FLOAT32 -> outBuf.float.toDouble()
                DataType.UINT8 -> ((outBuf.get().toInt() and 0xFF) - outQ.zeroPoint) * outQ.scale.toDouble()
                DataType.INT8 -> (outBuf.get().toInt() - outQ.zeroPoint) * outQ.scale.toDouble()
                else -> error("Unsupported output type ${output.dataType()}")
            }
        }
        val logits = if (meta.output == "probs") DoubleArray(raw.size) { ln(raw[it].coerceAtLeast(1e-6)) } else raw
        val probs = softmax(logits, meta.temperature)
        val best = probs.indices.maxBy { probs[it] }
        return PhotoObservation(labels[best], probs[best])
    }

    private fun softmax(logits: DoubleArray, temperature: Double): DoubleArray {
        val scaled = logits.map { it / temperature }
        val max = scaled.max()
        val exps = scaled.map { exp(it - max) }
        val sum = exps.sum()
        return DoubleArray(exps.size) { exps[it] / sum }
    }

    /**
     * Halve until within 2× of the target, then one bilinear step. A single bilinear scale from a
     * multi-megapixel photo samples only a few pixels and aliases; halving averages them, close to the
     * antialiased resize used in training and evaluation (PIL). Without it, confidence on large photos
     * drifted by up to 0.27 from the evaluated model (ClassifierParityTest).
     */
    private fun downscale(square: Bitmap, target: Int): Bitmap {
        var b = square
        while (b.width >= target * 2) b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
        return if (b.width == target) b else Bitmap.createScaledBitmap(b, target, target, true)
    }

    private fun centerSquare(b: Bitmap): Bitmap {
        val side = minOf(b.width, b.height)
        return Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
    }
}
