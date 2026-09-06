package com.recallos.ui

import android.widget.Toast
import android.util.Log
import com.recallos.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recallos.data.MemoryItem
import com.recallos.data.RecallOsDatabase
import com.recallos.ingestion.ImageIngestionRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun CaptureScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val ingestionRepository = remember(appContext) { ImageIngestionRepository(appContext) }
    val memoryDao = remember(appContext) {
        RecallOsDatabase.getInstance(appContext).memoryItemDao()
    }
    val memoryItems by memoryDao.observeAll().collectAsState(initial = emptyList())
    var isSeeding by remember { mutableStateOf(false) }

    LaunchedEffect(ingestionRepository) {
        runCatching { ingestionRepository.processPendingItems() }
            .onFailure { Log.e("RecallOSIngestion", "Pending processing failed", it) }
        runCatching { ingestionRepository.classifyExistingItems() }
            .onFailure { Log.e("RecallOSIngestion", "Existing item classification failed", it) }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val memoryId = ingestionRepository.saveImageForProcessing(uri)
                ingestionRepository.processMemoryItem(memoryId)
            }.onSuccess {
                snackbarHostState.showSnackbar("Saved to RecallOS")
            }.onFailure {
                Toast.makeText(context, "Could not save to RecallOS", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                )
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = "Add screenshot",
                )
            }

            if (BuildConfig.DEBUG) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSeeding,
                    onClick = {
                        scope.launch {
                            isSeeding = true
                            runCatching { ingestionRepository.seedDemoImages() }
                                .onSuccess { count ->
                                    snackbarHostState.showSnackbar("Loaded $count demo screenshots")
                                }
                                .onFailure { error ->
                                    Toast.makeText(
                                        context,
                                        "Demo data failed: ${error.message}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            isSeeding = false
                        }
                    },
                ) {
                    Text(if (isSeeding) "Loading demo data..." else "Load demo data")
                }
            }

            Text(
                text = "Recent captures",
                style = MaterialTheme.typography.titleMedium,
            )

            if (memoryItems.isEmpty()) {
                Text(
                    text = "No MemoryItem rows yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(memoryItems, key = { it.id }) { item ->
                        MemoryItemRow(item)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryItemRow(item: MemoryItem) {
    ListItem(
        headlineContent = {
            Text("MemoryItem #${item.id} - ${item.statusLabel()}")
        },
        supportingContent = {
            Text(
                text = "${formatCreatedAt(item.createdAt)}\n${item.sourceUri}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

private fun MemoryItem.statusLabel(): String =
    when {
        rawOcrText == "PROCESSING" -> "Reading text..."
        visualCaption == null -> "Understanding image..."
        else -> "Indexed"
    }

private fun formatCreatedAt(createdAt: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(createdAt))
