# Local LLM demo model

RecallOS keeps the Gemma model outside the APK because the quantized model is too large for a practical hackathon APK.

1. Obtain a compatible MediaPipe GenAI Gemma model, for example `gemma-3-1b-it-int4.task`.
2. Install the debug APK on the phone.
3. Create the model directory and push the file into app-private storage:

```powershell
adb shell run-as com.recallos mkdir -p files/models
adb push .\gemma-3-1b-it-int4.task /data/local/tmp/gemma-3-1b-it-int4.task
adb shell run-as com.recallos cp /data/local/tmp/gemma-3-1b-it-int4.task files/models/gemma-3-1b-it-int4.task
```

Office Kit's file transfer can be used to place the model in the app's local `files/models` directory instead. Without the model, Search still works and shows raw semantic results; only the synthesized answer is omitted.