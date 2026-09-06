package com.recallos.ui

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recallos.data.RecallOsDatabase
import com.recallos.llm.LocalLlmEngine
import com.recallos.search.EmbeddingEngine
import com.recallos.search.SearchRepository
import com.recallos.search.SearchResult
import kotlinx.coroutines.launch

@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val embeddingEngine = remember(appContext) { EmbeddingEngine(appContext) }
    val llmEngine = remember(appContext) { LocalLlmEngine(appContext) }
    val repository = remember(appContext, embeddingEngine) {
        SearchRepository(RecallOsDatabase.getInstance(appContext).memoryItemDao(), embeddingEngine)
    }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<String?>(null) }
    var citedSource by remember { mutableStateOf<Int?>(null) }

    val runSearch: (String) -> Unit = { searchText ->
        scope.launch {
            isSearching = true
            answer = null
            citedSource = null
            try {
                val rankedResults = runCatching {
                    repository.rankedSemanticSearch(searchText)
                }.getOrElse {
                    Log.e("RecallOSSearch", "Semantic search failed", it)
                    emptyList()
                }
                results = rankedResults
                if (rankedResults.isNotEmpty()) {
                    isThinking = true
                    runCatching {
                        llmEngine.synthesizeAnswer(searchText, rankedResults.map(SearchResult::item))
                    }.onSuccess { generatedAnswer ->
                        answer = generatedAnswer
                        citedSource = sourceNumber(generatedAnswer)
                    }.onFailure {
                        Log.w("RecallOSSearch", "Local answer synthesis unavailable", it)
                    }
                }
            } finally {
                isSearching = false
                isThinking = false
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spokenText.isNullOrBlank()) {
            query = spokenText
            runSearch(spokenText)
        }
    }
    val launchSpeech = {
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your RecallOS search")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            ) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            } else {
                Log.w(
                    "RecallOSVoice",
                    "On-device speech recognition unavailable; using standard recognition fallback.",
                )
            }
        }
        runCatching { speechLauncher.launch(speechIntent) }
            .onFailure {
                Log.e("RecallOSVoice", "Speech recognizer could not be launched", it)
            }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchSpeech()
        else Log.w("RecallOSVoice", "RECORD_AUDIO permission was denied")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = query,
                onValueChange = { query = it },
                label = { Text("Search memories") },
                singleLine = true,
            )
            IconButton(
                modifier = Modifier.padding(top = 8.dp),
                onClick = {
                    if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        launchSpeech()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_btn_speak_now),
                    contentDescription = "Search by voice",
                )
            }
            Button(
                modifier = Modifier.padding(top = 8.dp),
                enabled = query.isNotBlank() && !isSearching,
                onClick = { runSearch(query) },
            ) {
                Text("Search")
            }
        }

        if (isSearching || isThinking) {
            Text(if (isThinking) "Thinking..." else "Searching...")
            CircularProgressIndicator()
        }

        if (query.isBlank() && results.isEmpty()) {
            Text(
                text = "Ask RecallOS anything you've saved",
                style = MaterialTheme.typography.titleLarge,
                color = RecallMuted,
            )
        } else if (answer != null) {
            Text(
                text = answer.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RecallYellow)
                    .padding(12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            citedSource?.let { source ->
                results.getOrNull(source - 1)?.let { result ->
                    Text(
                        text = "Source",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    SearchResultRow(result, source - 1, citedSource)
                }
            }
        } else if (results.isEmpty() && query.isNotBlank()) {
            Text("No matching screenshots")
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(results, key = { it.item.id }) { result ->
                SearchResultRow(result, resultIndex = results.indexOf(result), citedSource = citedSource)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: SearchResult, resultIndex: Int, citedSource: Int?) {
    val bitmap = remember(result.item.sourceUri) {
        BitmapFactory.decodeFile(Uri.parse(result.item.sourceUri).path)
    }
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = if (citedSource == resultIndex + 1) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        leadingContent = {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Screenshot",
                    modifier = Modifier.size(72.dp),
                )
            }
        },
        headlineContent = {
            Text(
                text = result.item.caption?.ifBlank { "Untitled screenshot" }
                    ?: "Untitled screenshot",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text("Similarity ${(result.similarity * 100).coerceAtLeast(0f).formatScore()}%")
        },
    )
}

private fun Float.formatScore(): String = "%.1f".format(this)

private fun sourceNumber(answer: String): Int? =
    """Source:\s*\[(\d+)]""".toRegex(RegexOption.IGNORE_CASE)
        .find(answer)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
