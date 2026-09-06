package com.recallos.search

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EmbeddingEngine(
    context: Context,
) {
    private val embedder: TextEmbedder

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .build()
        val options = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            .build()
        embedder = TextEmbedder.createFromOptions(context, options)
    }

    fun embed(text: String): FloatArray {
        return embedder.embed(text).embeddingResult().embeddings()[0].floatEmbedding()
    }

    fun close() {
        embedder.close()
    }

    companion object {
        const val MODEL_ASSET = "universal_sentence_encoder.tflite"

        fun serialize(vector: FloatArray): ByteArray {
            val buffer = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
            vector.forEach(buffer::putFloat)
            return buffer.array()
        }

        fun deserialize(bytes: ByteArray): FloatArray {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.float }
        }
    }
}