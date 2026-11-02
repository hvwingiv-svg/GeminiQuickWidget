package com.custom.geminiquickwidget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class QuickOverlayActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var editInput: EditText
    private var tts: TextToSpeech? = null
    private var isLiveVoiceMode = false

    companion object {
        private const val REQ_VOICE_INPUT = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_overlay)

        tts = TextToSpeech(this, this)

        val outsideScrim = findViewById<FrameLayout>(R.id.overlay_outside_scrim)
        editInput = findViewById(R.id.overlay_edit_input)

        // Tap anywhere outside to stop typing and close keyboard
        outsideScrim.setOnClickListener {
            stopTypingAndClose()
        }

        // Stream every keystroke directly into the Home Screen Widget
        editInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val prefs = getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean(GeminiPillWidgetProvider.KEY_IS_TYPING, true)
                    .putString(GeminiPillWidgetProvider.KEY_LIVE_DRAFT, s?.toString() ?: "")
                    .apply()
                GeminiPillWidgetProvider.refreshAllWidgets(this@QuickOverlayActivity)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        editInput.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE || isEnter) {
                val q = editInput.text.toString().trim()
                if (q.isNotEmpty()) {
                    submitPromptOrApiKey(q)
                } else {
                    stopTypingAndClose()
                }
                true
            } else {
                false
            }
        }

        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleLaunchIntent(intent)
        }
    }

    private fun handleLaunchIntent(intent: Intent) {
        val prefs = getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        when (intent.getStringExtra("LAUNCH_MODE")) {
            "SEND_NOW" -> {
                val draft = editInput.text.toString().trim().ifEmpty {
                    prefs.getString(GeminiPillWidgetProvider.KEY_LIVE_DRAFT, "")?.trim() ?: ""
                }
                if (draft.isNotEmpty()) {
                    submitPromptOrApiKey(draft)
                } else {
                    stopTypingAndClose()
                }
            }
            "MIC" -> {
                isLiveVoiceMode = false
                startVoiceRecognition()
            }
            "LIVE" -> {
                isLiveVoiceMode = true
                startVoiceRecognition()
            }
            else -> {
                // Direct in-widget typing mode: activate live cursor on the Home Screen widget and open keyboard
                val existingDraft = prefs.getString(GeminiPillWidgetProvider.KEY_LIVE_DRAFT, "") ?: ""
                prefs.edit()
                    .putBoolean(GeminiPillWidgetProvider.KEY_IS_TYPING, true)
                    .apply()
                GeminiPillWidgetProvider.refreshAllWidgets(this)

                editInput.setText(existingDraft)
                editInput.setSelection(editInput.text.length)
                editInput.requestFocus()
                editInput.postDelayed({
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(editInput, InputMethodManager.SHOW_FORCED)
                }, 80)
            }
        }
    }

    private fun stopTypingAndClose() {
        val prefs = getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(GeminiPillWidgetProvider.KEY_IS_TYPING, false)
            .putString(GeminiPillWidgetProvider.KEY_LIVE_DRAFT, "")
            .apply()
        GeminiPillWidgetProvider.refreshAllWidgets(this)
        hideKeyboard()
        finish()
        overridePendingTransition(0, 0)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editInput.windowToken, 0)
    }

    private fun startVoiceRecognition() {
        try {
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, if (isLiveVoiceMode) "Gemini Live — Speak now" else "Ask Gemini...")
            }
            startActivityForResult(speechIntent, REQ_VOICE_INPUT)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice input not available on this device", Toast.LENGTH_SHORT).show()
            stopTypingAndClose()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VOICE_INPUT) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spoken = matches?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    submitPromptOrApiKey(spoken)
                    return
                }
            }
            stopTypingAndClose()
        }
    }

    private fun submitPromptOrApiKey(input: String) {
        val prefs = getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(GeminiPillWidgetProvider.KEY_IS_TYPING, false)
            .putString(GeminiPillWidgetProvider.KEY_LIVE_DRAFT, "")
            .apply()
        hideKeyboard()

        // Allow saving Gemini API key directly by typing/pasting an AIza... key into the widget
        if (input.startsWith("AIza") && input.length >= 25 && !input.contains(" ")) {
            prefs.edit()
                .putString(GeminiPillWidgetProvider.KEY_API_KEY, input)
                .putString(GeminiPillWidgetProvider.KEY_LAST_ANSWER, "✦ Gemini API Key saved! Ask anything in the widget.")
                .apply()
            GeminiPillWidgetProvider.refreshAllWidgets(this)
            finish()
            overridePendingTransition(0, 0)
            return
        }

        prefs.edit()
            .putString(GeminiPillWidgetProvider.KEY_LAST_ANSWER, "✦ Thinking about \"$input\"...")
            .apply()
        GeminiPillWidgetProvider.refreshAllWidgets(this)

        val apiKey = prefs.getString(GeminiPillWidgetProvider.KEY_API_KEY, "") ?: ""
        val appContext = applicationContext

        thread {
            val answer = if (apiKey.length > 10) {
                callGeminiRestApi(input, apiKey)
            } else {
                "⚡ Paste your Gemini API key (starts with AIza...) directly into the widget bar and press Send to enable live Gemini 2.5 Flash answers."
            }

            prefs.edit().putString(GeminiPillWidgetProvider.KEY_LAST_ANSWER, answer).apply()
            GeminiPillWidgetProvider.refreshAllWidgets(appContext)

            if (isLiveVoiceMode) {
                runOnUiThread {
                    tts?.speak(answer.take(300), TextToSpeech.QUEUE_FLUSH, null, "gemini_live_utt")
                }
            }
        }

        if (!isLiveVoiceMode) {
            finish()
            overridePendingTransition(0, 0)
        }
    }

    private fun callGeminiRestApi(prompt: String, apiKey: String): String {
        return try {
            val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 15000
            }

            val payload = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().apply {
                        put("parts", JSONArray().put(
                            JSONObject().put("text", "$prompt (Answer concisely for a mobile home screen widget.)")
                        ))
                    }
                ))
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val rawResponse = reader.readText()
            reader.close()

            val json = JSONObject(rawResponse)
            json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        } catch (e: Exception) {
            "Could not reach Gemini API (${e.localizedMessage}). Paste a valid AIza... key into the widget."
        }
    }

    override fun onStop() {
        super.onStop()
        val prefs = getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(GeminiPillWidgetProvider.KEY_IS_TYPING, false)) {
            prefs.edit().putBoolean(GeminiPillWidgetProvider.KEY_IS_TYPING, false).apply()
            GeminiPillWidgetProvider.refreshAllWidgets(this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
