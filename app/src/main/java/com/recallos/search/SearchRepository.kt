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

        val tokenResults = tokenFallback(query, topK)
        if (tokenResults.isNotEmpty()) return tokenResults

        val bestSemanticScore = semanticResults.firstOrNull()?.similarity ?: 0f
        if (bestSemanticScore >= SEMANTIC_MATCH_THRESHOLD) {
            val secondSemanticScore = semanticResults.getOrNull(1)?.similarity ?: 0f
            val scoreSpread = bestSemanticScore - secondSemanticScore
            if (scoreSpread < SEMANTIC_SPREAD_THRESHOLD) return emptyList()
            val relevanceFloor = maxOf(SEMANTIC_MATCH_THRESHOLD, bestSemanticScore * 0.72f)
            return semanticResults.filter { it.similarity >= relevanceFloor }
        }

        return semanticResults.filter { it.similarity >= SEMANTIC_MATCH_THRESHOLD }
            .ifEmpty { keywordFallback(query, topK) }
            .take(topK)
    }

    private suspend fun keywordFallback(query: String, topK: Int): List<SearchResult> =
        runCatching { dao.searchFts4(query.trim()).first() }
            .onFailure { Log.e(TAG, "Keyword fallback failed", it) }
            .getOrDefault(emptyList())
            .take(topK)
            .map { SearchResult(it, 0f) }

    private suspend fun tokenFallback(query: String, topK: Int): List<SearchResult> {
        val queryTokens = meaningfulTokens(query)
        if (queryTokens.isEmpty()) return emptyList()

        return dao.findAll()
            .mapNotNull { item ->
                val documentTokens = meaningfulTokens(
                    buildString {
                        append(item.rawOcrText)
                        append(' ')
                        append(item.caption.orEmpty())
                        append(' ')
                        append(item.visualCaption.orEmpty())
                        append(' ')
                        append(item.tags)
                    }
                )
                val matches = queryTokens.count(documentTokens::contains)
                val score = matches.toFloat() / queryTokens.size
                if (score < TOKEN_MATCH_THRESHOLD) null else SearchResult(item, score)
            }
            .sortedByDescending(SearchResult::similarity)
            .take(topK)
    }

    private fun meaningfulTokens(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .map { token ->
                when {
                    token.endsWith("ies") && token.length > 4 -> token.dropLast(3) + "y"
                    token.endsWith("ing") && token.length > 5 -> token.dropLast(3)
                    token.endsWith("ed") && token.length > 4 -> token.dropLast(2)
                    token.endsWith("es") && token.length > 4 -> token.dropLast(2)
                    token.endsWith("s") && token.length > 3 -> token.dropLast(1)
                    else -> token
                }
            }
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .toSet()

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
        const val SEMANTIC_MATCH_THRESHOLD = 0.25f
        const val SEMANTIC_SPREAD_THRESHOLD = 0.03f
        const val TOKEN_MATCH_THRESHOLD = 0.25f
        val STOP_WORDS = setOf(
            "the", "and", "for", "find", "show", "with", "that", "this", "from", "about",
            "screenshot", "having", "image", "picture", "photo",
        )
    }
}