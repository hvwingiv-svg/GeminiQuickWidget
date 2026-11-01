package com.custom.geminiquickwidget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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

    private lateinit var responseCard: LinearLayout
    private lateinit var responseText: TextView
    private lateinit var statusTitle: TextView
    private lateinit var editInput: EditText
    private lateinit var apiKeyContainer: LinearLayout
    private lateinit var inputApiKey: EditText
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
        responseCard = findViewById(R.id.overlay_response_card)
        responseText = findViewById(R.id.overlay_response_text)
        statusTitle = findViewById(R.id.overlay_status_title)
        editInput = findViewById(R.id.overlay_edit_input)
        apiKeyContainer = findViewById(R.id.api_key_container)
        inputApiKey = findViewById(R.id.input_api_key)

        val btnPlus = findViewById<ImageView>(R.id.overlay_btn_plus)
        val btnMic = findViewById<ImageView>(R.id.overlay_btn_mic)
        val btnSendOrLive = findViewById<FrameLayout>(R.id.overlay_btn_send_or_live)
        val btnApiKeyToggle = findViewById<TextView>(R.id.btn_api_key_toggle)
        val btnSaveKey = findViewById<Button>(R.id.btn_save_key)

        val prefs = getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        inputApiKey.setText(prefs.getString(GeminiPillWidgetProvider.KEY_API_KEY, ""))

        // Tap outside the floating pill to return to Home Screen immediately
        outsideScrim.setOnClickListener { finish() }

        btnApiKeyToggle.setOnClickListener {
            apiKeyContainer.visibility =
                if (apiKeyContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnSaveKey.setOnClickListener {
            prefs.edit().putString(GeminiPillWidgetProvider.KEY_API_KEY, inputApiKey.text.toString().trim()).apply()
            apiKeyContainer.visibility = View.GONE
            Toast.makeText(this, "Gemini API Key saved!", Toast.LENGTH_SHORT).show()
        }

        btnPlus.setOnClickListener {
            responseCard.visibility = View.VISIBLE
            statusTitle.text = "✦ Quick Actions"
            responseText.text = "• Tap the text box and type any question or calculation\n• Tap ⚙️ API Key above to connect your Gemini 2.5 Flash key\n• Tap the Mic or Purple Live icon for voice mode"
        }

        btnMic.setOnClickListener {
            isLiveVoiceMode = false
            startVoiceRecognition()
        }

        btnSendOrLive.setOnClickListener {
            val q = editInput.text.toString().trim()
            if (q.isNotEmpty()) {
                submitPrompt(q)
            } else {
                isLiveVoiceMode = true
                startVoiceRecognition()
            }
        }

        editInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                val q = editInput.text.toString().trim()
                if (q.isNotEmpty()) submitPrompt(q)
                true
            } else {
                false
            }
        }

        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent) {
        when (intent.getStringExtra("LAUNCH_MODE")) {
            "PLUS" -> {
                responseCard.visibility = View.VISIBLE
                statusTitle.text = "✦ Quick Tools & Settings"
                responseText.text = "Type a prompt below, or tap ⚙️ API Key to paste your Gemini API key so answers stream right over your home screen."
            }
            "MIC" -> {
                isLiveVoiceMode = false
                startVoiceRecognition()
            }
            "LIVE" -> {
                isLiveVoiceMode = true
                statusTitle.text = "✦ Gemini Live (Voice Overlay)"
                startVoiceRecognition()
            }
            else -> {
                editInput.requestFocus()
                editInput.postDelayed({
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(editInput, InputMethodManager.SHOW_IMPLICIT)
                }, 120)
            }
        }
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
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VOICE_INPUT && resultCode == Activity.RESULT_OK && data != null) {
            val matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                editInput.setText(spoken)
                submitPrompt(spoken)
            }
        }
    }

    private fun submitPrompt(prompt: String) {
        editInput.setText("")
        responseCard.visibility = View.VISIBLE
        statusTitle.text = "✦ Thinking..."
        responseText.text = " Asking: \"$prompt\"..."

        val prefs = getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(GeminiPillWidgetProvider.KEY_API_KEY, "") ?: ""

        thread {
            val answer = if (apiKey.length > 10) {
                callGeminiRestApi(prompt, apiKey)
            } else {
                "⚡ Quick Answer for \"$prompt\":\n\nTap ⚙️ API Key in the top-right of this card to paste a free Google AI Studio key (gemini-2.5-flash) for live AI responses right on your home screen."
            }

            // Save latest answer to show directly inside the Home Screen Widget too
            prefs.edit().putString(GeminiPillWidgetProvider.KEY_LAST_ANSWER, answer).apply()
            GeminiPillWidgetProvider.refreshAllWidgets(this)

            runOnUiThread {
                statusTitle.text = if (isLiveVoiceMode) "✦ Gemini Live Reply" else "✦ Gemini Inline"
                responseText.text = answer
                if (isLiveVoiceMode) {
                    tts?.speak(answer.take(300), TextToSpeech.QUEUE_FLUSH, null, "gemini_live_utt")
                }
            }
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
            "Could not reach Gemini API (${e.localizedMessage}). Check your API key under ⚙️ API Key."
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
