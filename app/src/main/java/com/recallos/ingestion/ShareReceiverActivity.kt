package com.recallos.ingestion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageUris = intent.sharedImageUris()
        if (imageUris.isEmpty()) {
            finish()
            return
        }

        lifecycleScope.launch {
            runCatching {
                val repository = ImageIngestionRepository(applicationContext)
                imageUris.forEach { imageUri ->
                    val memoryId = repository.saveImageForProcessing(imageUri)
                    repository.processMemoryItem(memoryId)
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

    private fun Intent.sharedImageUris(): List<Uri> {
        val uris = buildList {
            if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
                @Suppress("DEPRECATION")
                getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::add)
                clipData?.let { clip ->
                    for (index in 0 until clip.itemCount) {
                        clip.getItemAt(index).uri?.let(::add)
                    }
                }
            }
        }
        if (action != Intent.ACTION_SEND_MULTIPLE) return uris.distinct()

        @Suppress("DEPRECATION")
        return (getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty() + uris).distinct()
    }
}
