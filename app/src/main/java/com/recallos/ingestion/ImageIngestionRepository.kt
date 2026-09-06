package com.recallos.ingestion

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.recallos.data.MemoryItem
import com.recallos.data.RecallOsDatabase
import com.recallos.llm.VisualCaptionEngine
import com.recallos.search.EmbeddingEngine
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class ImageIngestionRepository(
    private val context: Context,
) {
    private val dao = RecallOsDatabase.getInstance(context).memoryItemDao()

    suspend fun saveImageForProcessing(sourceUri: Uri): Long = withContext(Dispatchers.IO) {
        val savedFile = copyToPrivateStorage(sourceUri)
        val savedUri = Uri.fromFile(savedFile).toString()
        val memoryId = dao.insert(
            MemoryItem(
                sourceUri = savedUri,
                rawOcrText = "PROCESSING",
            )
        )
        Log.i(TAG, "Inserted MemoryItem id=$memoryId sourceUri=$savedUri")
        memoryId
    }

    suspend fun processMemoryItem(memoryId: Long) = withContext(Dispatchers.IO) {
        val item = dao.findProcessingItems().firstOrNull { it.id == memoryId } ?: return@withContext
        val ocrProcessor = OcrProcessor(context)
        val visualCaptionEngine = VisualCaptionEngine(context)
        val embeddingEngine = EmbeddingEngine(context)
        try {
            updateWithOcrAndVisual(item, ocrProcessor, visualCaptionEngine, embeddingEngine)
        } finally {
            ocrProcessor.close()
            embeddingEngine.close()
        }
        Log.i(TAG, "OCR complete for MemoryItem id=$memoryId")
    }

    suspend fun processPendingItems() = withContext(Dispatchers.IO) {
        val ocrProcessor = OcrProcessor(context)
        val visualCaptionEngine = VisualCaptionEngine(context)
        val embeddingEngine = EmbeddingEngine(context)
        try {
            dao.findProcessingItems().forEach { item ->
                runCatching {
                    updateWithOcrAndVisual(item, ocrProcessor, visualCaptionEngine, embeddingEngine)
                    Log.i(TAG, "OCR complete for pending MemoryItem id=${item.id}")
                }.onFailure { error ->
                    Log.e(TAG, "Failed to process pending MemoryItem id=${item.id}", error)
                }
            }
        } finally {
            ocrProcessor.close()
            embeddingEngine.close()
        }
    }

    suspend fun classifyExistingItems() = withContext(Dispatchers.IO) {
        dao.findUnclassified().forEach { item ->
            dao.updateTags(
                item.id,
                ScreenshotClassifier.classify(
                    "${item.rawOcrText} ${item.visualCaption.orEmpty()}"
                ),
            )
        }
    }

    fun enqueueProcessing(memoryId: Long) {
        val request = OneTimeWorkRequestBuilder<MemoryItemProcessingWorker>()
            .setInputData(Data.Builder().putLong(MemoryItemProcessingWorker.MEMORY_ID, memoryId).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    suspend fun seedDemoImages(): Int = withContext(Dispatchers.IO) {
        val demoImages = context.assets.list(DEMO_ASSET_DIRECTORY).orEmpty()
            .filter { it.endsWith(".png", ignoreCase = true) }
        demoImages.forEach { assetName ->
            val savedFile = copyAssetToPrivateStorage("$DEMO_ASSET_DIRECTORY/$assetName")
            dao.insert(
                MemoryItem(
                    sourceUri = Uri.fromFile(savedFile).toString(),
                    rawOcrText = "PROCESSING",
                )
            )
        }
        processPendingItems()
        demoImages.size
    }

    private suspend fun updateWithOcrAndVisual(
        item: MemoryItem,
        ocrProcessor: OcrProcessor,
        visualCaptionEngine: VisualCaptionEngine,
        embeddingEngine: EmbeddingEngine,
    ) = coroutineScope {
        val imageUri = Uri.parse(item.sourceUri)
        val ocrTextDeferred = async { ocrProcessor.process(imageUri) }
        val visualCaptionDeferred = async {
            runCatching { visualCaptionEngine.caption(imageUri) }
                .onFailure { error ->
                    Log.w(TAG, "Visual caption unavailable for MemoryItem id=${item.id}", error)
                }
                .getOrNull()
        }
        val ocrText = ocrTextDeferred.await()
        val caption = ocrText.trim().split(Regex("\\s+")).take(15).joinToString(" ")
        dao.updateOcrResult(item.id, ocrText, caption)
        dao.updateTags(item.id, ScreenshotClassifier.classify(ocrText))

        val visualCaption = visualCaptionDeferred.await().orEmpty()
        dao.updateVisualCaption(item.id, visualCaption)
        dao.updateTags(item.id, ScreenshotClassifier.classify("$ocrText $visualCaption"))
        dao.updateEmbedding(
            item.id,
            EmbeddingEngine.serialize(embeddingEngine.embed("$ocrText $caption ${visualCaption.orEmpty()}")),
        )
    }

    private fun copyToPrivateStorage(sourceUri: Uri): File {
        val extension = extensionFor(sourceUri)
        val targetDir = File(context.filesDir, "memory_images").apply {
            mkdirs()
        }
        val target = File(targetDir, "${System.currentTimeMillis()}-${UUID.randomUUID()}$extension")

        context.contentResolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Unable to open shared image: $sourceUri" }
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return target
    }

    private fun copyAssetToPrivateStorage(assetPath: String): File {
        val targetDir = File(context.filesDir, "memory_images").apply { mkdirs() }
        val target = File(targetDir, "demo-${System.currentTimeMillis()}-${UUID.randomUUID()}.png")
        context.assets.open(assetPath).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun extensionFor(uri: Uri): String {
        return when (context.contentResolver.getType(uri)) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            else -> ".jpg"
        }
    }

    private companion object {
        const val TAG = "RecallOSIngestion"
        const val DEMO_ASSET_DIRECTORY = "demo_samples"
    }
}
