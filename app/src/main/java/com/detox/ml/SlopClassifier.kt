package com.detox.ml

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SlopMeta(val inputSize: Int = 224, val threshold: Float = 0.9f)

data class MlResult(val probAi: Float, val millis: Long)

/**
 * On-device real-vs-AI classifier (TensorFlow Lite runtime; LiteRT-compatible API). Model: assets/slop_classifier.tflite, produced by
 * ml-training/export_tflite.py (input [0,1] RGB, output softmax probabilities, index 1 = AI).
 *
 * If the model asset is missing, [isAvailable] is false and the rest of the app carries on
 * with label detection only. Input layout (NCHW vs NHWC) and tensor types are read from the
 * model, so a different converter setting does not need a code change.
 */
class SlopClassifier(context: Context) : Closeable {

    private val meta: SlopMeta = runCatching {
        context.assets.open(META).bufferedReader().use { Json { ignoreUnknownKeys = true }.decodeFromString<SlopMeta>(it.readText()) }
    }.getOrDefault(SlopMeta())

    val inputSize: Int get() = meta.inputSize
    val threshold: Float get() = meta.threshold

    private val interpreter: Interpreter? = runCatching {
        val fd = context.assets.openFd(MODEL)
        val mapped = FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        Interpreter(mapped, Interpreter.Options().setNumThreads(2))
    }.onFailure { Log.i(TAG, "classifier unavailable: ${it.message}") }.getOrNull()

    val isAvailable: Boolean get() = interpreter != null

    /** Not thread-safe; call from one coroutine at a time (the sampler loop does). */
    @Synchronized
    fun predict(bitmap: Bitmap): MlResult? {
        val interp = interpreter ?: return null
        val t0 = SystemClock.elapsedRealtime()

        val src = if (bitmap.width == inputSize && bitmap.height == inputSize) bitmap
        else Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val inT = interp.getInputTensor(0)
        val shape = inT.shape()
        val nchw = shape.size == 4 && shape[1] == 3
        val q = inT.quantizationParams()
        val input = encodeInput(src, inT.dataType(), q.scale, q.zeroPoint, nchw)
        if (src !== bitmap) src.recycle()

        val outT = interp.getOutputTensor(0)
        val out = ByteBuffer.allocateDirect(outT.numBytes()).order(ByteOrder.nativeOrder())
        interp.run(input, out)
        out.rewind()
        val oq = outT.quantizationParams()
        val probs = decodeOutput(out, outT.dataType(), oq.scale, oq.zeroPoint, outT.numElements())
        val p = if (probs.size >= 2) probs[1] else probs[0]
        return MlResult(p.coerceIn(0f, 1f), SystemClock.elapsedRealtime() - t0)
    }

    private fun encodeInput(bmp: Bitmap, type: DataType, scale: Float, zero: Int, nchw: Boolean): ByteBuffer {
        val n = inputSize * inputSize
        val px = IntArray(n)
        bmp.getPixels(px, 0, inputSize, 0, 0, inputSize, inputSize)

        val bytesPer = if (type == DataType.FLOAT32) 4 else 1
        val buf = ByteBuffer.allocateDirect(n * 3 * bytesPer).order(ByteOrder.nativeOrder())

        fun channel(p: Int, c: Int): Int = when (c) { 0 -> (p shr 16) and 0xFF; 1 -> (p shr 8) and 0xFF; else -> p and 0xFF }
        fun put(v: Int) {
            when (type) {
                DataType.FLOAT32 -> buf.putFloat(v / 255f)
                DataType.UINT8 -> buf.put(if (scale > 0f) ((v / 255f) / scale + zero).roundToInt().coerceIn(0, 255).toByte() else v.toByte())
                DataType.INT8 -> buf.put(((v / 255f) / scale + zero).roundToInt().coerceIn(-128, 127).toByte())
                else -> error("unsupported input type $type")
            }
        }

        // Plain loops: this runs once per sampled frame and counts against the 300 ms budget.
        if (nchw) {
            for (c in 0 until 3) for (i in 0 until n) put(channel(px[i], c))
        } else {
            for (i in 0 until n) for (c in 0 until 3) put(channel(px[i], c))
        }
        buf.rewind()
        return buf
    }

    private fun decodeOutput(buf: ByteBuffer, type: DataType, scale: Float, zero: Int, n: Int): FloatArray =
        FloatArray(n) {
            when (type) {
                DataType.FLOAT32 -> buf.float
                DataType.UINT8 -> ((buf.get().toInt() and 0xFF) - zero) * scale
                DataType.INT8 -> (buf.get().toInt() - zero) * scale
                else -> error("unsupported output type $type")
            }
        }

    override fun close() { interpreter?.close() }

    private companion object {
        const val TAG = "deTOX"
        const val MODEL = "slop_classifier.tflite"
        const val META = "slop_meta.json"
    }
}
