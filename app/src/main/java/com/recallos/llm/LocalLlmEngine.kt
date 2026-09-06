package com.recallos.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.recallos.data.MemoryItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalLlmEngine(
    private val context: Context,
) {
    private val modelFile = File(
        context.filesDir,
        "models/gemma-3-1b-it-int4.task",
    )

    suspend fun synthesizeAnswer(
        query: String,
        topResults: List<MemoryItem>,
    ): String = withContext(Dispatchers.Default) {
        require(query.isNotBlank()) { "Query must not be blank." }
        require(topResults.isNotEmpty()) { "At least one search result is required." }
        check(modelFile.isFile) {
            "Local LLM model is missing. Push it to ${modelFile.absolutePath} before the demo."
        }

        val prompt = buildPrompt(query, topResults)
        val inference = LlmInference.createFromOptions(
            context,
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(256)
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
                    .build(),
            )
            try {
                session.addQueryChunk(prompt)
                session.generateResponse()
            } finally {
                session.close()
            }
        } finally {
            inference.close()
        }
    }

    private fun buildPrompt(query: String, results: List<MemoryItem>): String = buildString {
        appendLine("Answer the user's question using ONLY the evidence below. Cite which screenshot each fact came from.")
        appendLine("Question: $query")
        appendLine("Evidence:")
        results.forEachIndexed { index, item ->
            appendLine("${index + 1}. [${item.caption.orEmpty()}]: ${item.rawOcrText}")
        }
        appendLine("Answer concisely, and end with 'Source: [n]' referencing which evidence item supports your answer.")
    }
}