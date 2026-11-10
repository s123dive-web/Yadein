package com.yaadein.data.faces

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.yaadein.domain.faces.Affine
import com.yaadein.domain.faces.FaceMatcher
import com.yaadein.domain.faces.FaceModel
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Face embeddings from the bundled GhostFaceNetV1 model (`assets/faces/ghostfacenet_v1_w1.3_s1_int8.tflite`, MIT,
 * see README → Licences). 112×112 RGB in, 512 floats out; the app L2-normalises the output so cosine similarity is a
 * dot product. CPU with XNNPACK only: no delegates, nothing downloaded, nothing leaves the phone.
 */
@Singleton
class FaceEmbedder @Inject constructor(@ApplicationContext private val context: Context) {
    private val interpreter: Interpreter by lazy {
        val model = loadModel()
        val options = Interpreter.Options().apply {
            numThreads = THREADS
            useXNNPACK = true
        }
        Interpreter(model, options)
    }
    private val input = ByteBuffer.allocateDirect(FaceModel.INPUT_SIZE * FaceModel.INPUT_SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(FaceModel.INPUT_SIZE * FaceModel.INPUT_SIZE)
    private val lock = Any()

    /** Draws the face through [align] into a 112×112 bitmap and embeds it. */
    fun embed(source: Bitmap, align: Affine): FloatArray {
        val n = FaceModel.INPUT_SIZE
        val face = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply { setValues(align.values()) }
        Canvas(face).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        try {
            return embed(face)
        } finally {
            face.recycle()
        }
    }

    /** Embeds an already aligned 112×112 face. */
    fun embed(face112: Bitmap): FloatArray = synchronized(lock) {
        val n = FaceModel.INPUT_SIZE
        require(face112.width == n && face112.height == n) { "face must be ${n}x$n" }
        face112.getPixels(pixels, 0, n, 0, 0, n, n)
        input.rewind()
        for (p in pixels) {
            // Same normalisation as training: (pixel - 127.5) / 128, RGB order.
            input.putFloat((((p shr 16) and 0xFF) - 127.5f) / 128f)
            input.putFloat((((p shr 8) and 0xFF) - 127.5f) / 128f)
            input.putFloat(((p and 0xFF) - 127.5f) / 128f)
        }
        input.rewind()
        val out = Array(1) { FloatArray(FaceModel.EMBEDDING_SIZE) }
        interpreter.run(input, out)
        FaceMatcher.normalise(out[0])
    }

    private fun loadModel(): MappedByteBuffer {
        context.assets.openFd(ASSET).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }

    companion object {
        const val ASSET = "faces/ghostfacenet_v1_w1.3_s1_int8.tflite"
        private const val THREADS = 2
    }
}
