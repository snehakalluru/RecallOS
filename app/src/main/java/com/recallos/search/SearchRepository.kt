package com.recallos.search

import com.recallos.data.MemoryItem
import com.recallos.data.MemoryItemDao
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlin.math.sqrt

data class SearchResult(
    val item: MemoryItem,
    val similarity: Float,
)

class SearchRepository(
    private val dao: MemoryItemDao,
    private val embeddingEngine: EmbeddingEngine,
) {
    suspend fun semanticSearch(query: String, topK: Int = 5): List<MemoryItem> =
        rankedSemanticSearch(query, topK).map(SearchResult::item)

    suspend fun rankedSemanticSearch(query: String, topK: Int = 5): List<SearchResult> {
        if (query.isBlank() || topK <= 0) return emptyList()

        val queryVector = runCatching { embeddingEngine.embed(query) }
            .onFailure { Log.e(TAG, "Query embedding failed; using keyword fallback", it) }
            .getOrNull()
            ?: return keywordFallback(query, topK)
        val semanticResults = dao.findEmbeddedItems()
            .mapNotNull { item ->
                runCatching {
                    item.embeddingVector?.let { bytes ->
                        SearchResult(item, cosineSimilarity(queryVector, EmbeddingEngine.deserialize(bytes)))
                    }
                }.getOrNull()
            }
            .sortedByDescending(SearchResult::similarity)
            .take(topK)

        if (semanticResults.isNotEmpty()) return semanticResults

        return keywordFallback(query, topK)
            .take(topK)
    }

    private suspend fun keywordFallback(query: String, topK: Int): List<SearchResult> =
        runCatching { dao.searchFts4(query.trim()).first() }
            .onFailure { Log.e(TAG, "Keyword fallback failed", it) }
            .getOrDefault(emptyList())
            .take(topK)
            .map { SearchResult(it, 0f) }

    private fun cosineSimilarity(left: FloatArray, right: FloatArray): Float {
        if (left.size != right.size) return 0f
        var dot = 0f
        var leftMagnitude = 0f
        var rightMagnitude = 0f
        left.indices.forEach { index ->
            dot += left[index] * right[index]
            leftMagnitude += left[index] * left[index]
            rightMagnitude += right[index] * right[index]
        }
        val denominator = sqrt(leftMagnitude) * sqrt(rightMagnitude)
        return if (denominator == 0f) 0f else dot / denominator
    }

    private companion object {
        const val TAG = "RecallOSSearch"
    }
}