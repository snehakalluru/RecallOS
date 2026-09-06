package com.recallos.llm

import android.content.Context

class MediaPipeLlmEngine(
    private val context: Context,
) {
    suspend fun generate(prompt: String): String {
        check(prompt.isNotBlank()) { "Prompt must not be blank." }
        return "MediaPipe LLM inference is not initialized yet."
    }
}
