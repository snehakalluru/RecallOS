package com.recallos.ingestion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageUri = intent.sharedImageUri()
        if (imageUri == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            runCatching {
                val repository = ImageIngestionRepository(applicationContext)
                val memoryId = repository.saveImageForProcessing(imageUri)
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { repository.processMemoryItem(memoryId) }
                        .onFailure { error ->
                            android.util.Log.e("RecallOSShare", "Failed to run OCR", error)
                        }
                }
            }.onSuccess {
                Toast.makeText(applicationContext, "Saved to RecallOS", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    applicationContext,
                    "Could not save to RecallOS",
                    Toast.LENGTH_SHORT,
                ).show()
                android.util.Log.e("RecallOSShare", "Failed to ingest shared image", error)
            }
            finish()
            overridePendingTransition(0, 0)
        }
    }

    private fun Intent.sharedImageUri(): Uri? {
        if (action != Intent.ACTION_SEND) return null
        @Suppress("DEPRECATION")
        return getParcelableExtra(Intent.EXTRA_STREAM)
    }
}
