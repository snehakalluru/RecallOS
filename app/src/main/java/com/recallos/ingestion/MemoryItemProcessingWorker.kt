package com.recallos.ingestion

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class MemoryItemProcessingWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val memoryId = inputData.getLong(MEMORY_ID, -1L)
        if (memoryId < 0L) return Result.failure()

        return runCatching {
            ImageIngestionRepository(applicationContext).processMemoryItem(memoryId)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            },
        )
    }

    companion object {
        const val MEMORY_ID = "memory_id"
    }
}