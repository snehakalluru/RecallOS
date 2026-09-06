package com.recallos.llm

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VisualCaptionEngine(
    private val context: Context,
) {
    private val modelFile = File(
        context.filesDir,
        "models/gemma-3n-E2B-it-int4.litertlm",
    )

    suspend fun caption(imageUri: Uri): String = withContext(Dispatchers.Default) {
        check(modelFile.isFile) {
            "Vision model is missing. Push it to ${modelFile.absolutePath} before the demo."
        }
        val bitmap = BitmapFactory.decodeFile(imageUri.path)
            ?: error("Unable to decode screenshot for visual captioning: $imageUri")
        val image = BitmapImageBuilder(bitmap).build()
        val inference = LlmInference.createFromOptions(
            context,
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(96)
                .setMaxNumImages(1)
                .setMaxTopK(40)
                .build(),
        )
        try {
            val session = LlmInferenceSession.createFromOptions(
                inference,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.2f)
                    .setRandomSeed(7)
                    .setGraphOptions(
                        GraphOptions.builder()
                            .setEnableVisionModality(true)
                            .build()
                    )
                    .build(),
            )
            try {
                session.addQueryChunk(PROMPT)
                session.addImage(image)
                session.generateResponse().trim()
            } finally {
                session.close()
            }
        } finally {
            inference.close()
            bitmap.recycle()
        }
    }

    companion object {
        private const val PROMPT =
            "Describe what is visually shown in this image in one concise sentence: " +
                "people, clothing type and colors, watches, accessories, food types, setting, objects, " +
                "landmarks, and likely place or travel location when visually recognizable. " +
                "Mention uncertainty when a place or object cannot be identified. " +
                "Do not describe any text content, only visual elements."
    }
}