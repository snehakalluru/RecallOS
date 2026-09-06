# RecallOS

Native Android memory capture demo built with Kotlin and Jetpack Compose.

## Build

```powershell
.\gradlew.bat :app:assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` on an Android 8.0+ device or
emulator. The project uses `minSdk 26`, `compileSdk 37`, and `targetSdk 37`.

## Demo flow

- Open Capture and choose **Add screenshot**, or share an image from another app.
- RecallOS copies the image into private storage, inserts a `MemoryItem`, and runs
	ML Kit on-device OCR followed by MediaPipe text embeddings.
- In debug builds, use **Load demo data** to index the bundled 16 OCR-friendly samples.
- Search by text or use the microphone button. Search uses cosine similarity with
	Room FTS4 as the keyword fallback.

## Optional local LLM

Search works without the LLM model. To enable synthesized answers, follow
[MODEL_SETUP.md](MODEL_SETUP.md) and place the compatible Gemma `.task` model at
`files/models/gemma-3-1b-it-int4.task` in app-private storage. Missing models fall
back to raw search results.

## Demo asset generation

```powershell
python tools/generate_demo_screenshots.py
```
