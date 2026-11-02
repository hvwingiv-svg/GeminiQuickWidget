package com.custom.geminiquickwidget

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
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
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class QuickOverlayActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var anchorContainer: LinearLayout
    private lateinit var floatingPlusMenu: LinearLayout
    private lateinit var responseCard: LinearLayout
    private lateinit var responseText: TextView
    private lateinit var statusTitle: TextView
    private lateinit var attachmentChipRow: LinearLayout
    private lateinit var attachmentChipLabel: TextView
    private lateinit var floatingPillBar: LinearLayout
    private lateinit var floatingLiveDock: LinearLayout
    private lateinit var auroraVisualizer: AuroraVisualizerView
    private lateinit var editInput: EditText
    private lateinit var apiKeyContainer: LinearLayout
    private lateinit var inputApiKey: EditText
    private lateinit var btnModelSelector: TextView
    private lateinit var btnStyleSelector: TextView
    private lateinit var liveBtnVideo: FrameLayout
    private lateinit var liveBtnShare: FrameLayout
    private lateinit var liveBtnMic: FrameLayout

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isSensorRegistered = false

    private var pillLiquidDrawable: LiquidGlassDrawable? = null
    private var menuLiquidDrawable: LiquidGlassDrawable? = null
    private var cardLiquidDrawable: LiquidGlassDrawable? = null

    private var attachedBase64Data: String? = null
    private var attachedMimeType: String? = null
    private var attachedLabel: String? = null

    private val conversationHistory = JSONArray()
    private var lastModelReply: String = ""

    private var isLiveMode = false
    private var isLiveMicMuted = false
    private var isLiveCameraActive = false
    private var isLaunchingSubPicker = false

    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleTimeoutRunnable = Runnable {
        if (!isFinishing) {
            closeOverlay(keepLiveWidgetState = false)
        }
    }

    companion object {
        private const val REQ_VOICE_INPUT = 9001
        private const val REQ_CAMERA_PHOTO = 9002
        private const val REQ_PICK_GALLERY = 9003
        private const val REQ_PICK_FILE = 9004
        private const val IDLE_TIMEOUT_MS = 60_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

        // Immediately hide the static Home Screen RemoteViews widget so there is NEVER a clone
        setStaticWidgetOverlayState(active = true)

        setContentView(R.layout.activity_quick_overlay)

        val density = resources.displayMetrics.density
        pillLiquidDrawable = LiquidGlassDrawable(999f)
        menuLiquidDrawable = LiquidGlassDrawable(24f * density)
        cardLiquidDrawable = LiquidGlassDrawable(24f * density)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val outsideScrim = findViewById<FrameLayout>(R.id.overlay_outside_scrim)
        anchorContainer = findViewById(R.id.floating_anchor_container)
        floatingPlusMenu = findViewById(R.id.floating_plus_menu)
        responseCard = findViewById(R.id.overlay_response_card)
        responseText = findViewById(R.id.overlay_response_text)
        statusTitle = findViewById(R.id.overlay_status_title)
        attachmentChipRow = findViewById(R.id.attachment_chip_row)
        attachmentChipLabel = findViewById(R.id.attachment_chip_label)
        floatingPillBar = findViewById(R.id.floating_pill_bar)
        floatingLiveDock = findViewById(R.id.floating_live_dock)
        auroraVisualizer = findViewById(R.id.live_aurora_visualizer)
        editInput = findViewById(R.id.overlay_edit_input)
        apiKeyContainer = findViewById(R.id.api_key_container)
        inputApiKey = findViewById(R.id.input_api_key)
        btnModelSelector = findViewById(R.id.btn_model_selector)
        btnStyleSelector = findViewById(R.id.btn_style_selector)
        liveBtnVideo = findViewById(R.id.live_btn_video)
        liveBtnShare = findViewById(R.id.live_btn_share)
        liveBtnMic = findViewById(R.id.live_btn_mic)

        val prefs = getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        inputApiKey.setText(prefs.getString(GeminiPillWidgetProvider.KEY_API_KEY, ""))

        outsideScrim.setOnClickListener {
            closeOverlay(keepLiveWidgetState = isLiveMode)
        }

        // Wire Floating '+' Menu Buttons
        findViewById<ImageView>(R.id.overlay_btn_plus).setOnClickListener {
            resetInactivityTimer()
            floatingPlusMenu.visibility =
                if (floatingPlusMenu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        findViewById<LinearLayout>(R.id.menu_btn_camera).setOnClickListener {
            resetInactivityTimer()
            launchCameraCapture()
        }

        findViewById<LinearLayout>(R.id.menu_btn_gallery).setOnClickListener {
            resetInactivityTimer()
            launchGalleryPicker()
        }

        findViewById<LinearLayout>(R.id.menu_btn_files).setOnClickListener {
            resetInactivityTimer()
            launchFilePicker()
        }

        findViewById<LinearLayout>(R.id.menu_btn_drive).setOnClickListener {
            resetInactivityTimer()
            launchFilePicker()
        }

        btnModelSelector.setOnClickListener {
            resetInactivityTimer()
            val cur = prefs.getString(GeminiPillWidgetProvider.KEY_MODEL_NAME, "gemini-2.5-flash")
            val next = if (cur == "gemini-2.5-flash") "gemini-2.5-pro" else "gemini-2.5-flash"
            prefs.edit().putString(GeminiPillWidgetProvider.KEY_MODEL_NAME, next).apply()
            updateModelAndStyleLabels()
        }

        btnStyleSelector.setOnClickListener {
            resetInactivityTimer()
            val cur = prefs.getInt(
                GeminiPillWidgetProvider.KEY_VISUAL_STYLE,
                GeminiPillWidgetProvider.STYLE_REFRACTIVE
            )
            val next = (cur + 1) % 3
            prefs.edit().putInt(GeminiPillWidgetProvider.KEY_VISUAL_STYLE, next).apply()
            applyVisualStyle()
        }

        findViewById<TextView>(R.id.btn_api_key_toggle).setOnClickListener {
            resetInactivityTimer()
            apiKeyContainer.visibility =
                if (apiKeyContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        findViewById<Button>(R.id.btn_save_key).setOnClickListener {
            resetInactivityTimer()
            prefs.edit()
                .putString(GeminiPillWidgetProvider.KEY_API_KEY, inputApiKey.text.toString().trim())
                .apply()
            apiKeyContainer.visibility = View.GONE
            Toast.makeText(this, "Gemini API Key saved!", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.attachment_chip_remove).setOnClickListener {
            clearAttachment()
        }

        findViewById<TextView>(R.id.btn_new_chat).setOnClickListener {
            resetInactivityTimer()
            while (conversationHistory.length() > 0) {
                conversationHistory.remove(0)
            }
            responseText.text = ""
            responseCard.visibility = View.GONE
        }

        findViewById<TextView>(R.id.btn_tts_speak).setOnClickListener {
            resetInactivityTimer()
            if (lastModelReply.isNotBlank()) {
                speakWithLazyTts(lastModelReply)
            }
        }

        findViewById<TextView>(R.id.btn_copy_response).setOnClickListener {
            resetInactivityTimer()
            if (lastModelReply.isNotBlank()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Gemini Response", lastModelReply))
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<TextView>(R.id.btn_close_response).setOnClickListener {
            responseCard.visibility = View.GONE
        }

        findViewById<ImageView>(R.id.overlay_btn_mic).setOnClickListener {
            resetInactivityTimer()
            isLiveMode = false
            startVoiceRecognition()
        }

        findViewById<FrameLayout>(R.id.overlay_btn_send_or_live).setOnClickListener {
            resetInactivityTimer()
            val q = editInput.text.toString().trim()
            if (q.isNotEmpty() || attachedBase64Data != null) {
                submitPrompt(q.ifEmpty { "Analyze this attachment." })
            } else {
                enterGeminiLiveDock()
            }
        }

        editInput.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE || isEnter) {
                val q = editInput.text.toString().trim()
                if (q.isNotEmpty() || attachedBase64Data != null) {
                    submitPrompt(q.ifEmpty { "Analyze this attachment." })
                }
                true
            } else {
                false
            }
        }

        // Gemini Live 5-Button Dock handlers
        liveBtnVideo.setOnClickListener {
            resetInactivityTimer()
            isLiveCameraActive = !isLiveCameraActive
            liveBtnVideo.setBackgroundResource(
                if (isLiveCameraActive) R.drawable.bg_circle_button_active else R.drawable.bg_circle_button_shadow
            )
            if (isLiveCameraActive) {
                launchCameraCapture()
            }
        }

        liveBtnShare.setOnClickListener {
            resetInactivityTimer()
            launchGalleryPicker()
        }

        auroraVisualizer.setOnClickListener {
            resetInactivityTimer()
            if (!isLiveMicMuted) {
                startVoiceRecognition()
            }
        }

        liveBtnMic.setOnClickListener {
            resetInactivityTimer()
            isLiveMicMuted = !isLiveMicMuted
            liveBtnMic.setBackgroundResource(
                if (isLiveMicMuted) R.drawable.bg_circle_button_active else R.drawable.bg_circle_button_shadow
            )
            auroraVisualizer.setVoiceActive(!isLiveMicMuted)
            if (!isLiveMicMuted) {
                startVoiceRecognition()
            } else {
                tts?.stop()
            }
        }

        findViewById<FrameLayout>(R.id.live_btn_close).setOnClickListener {
            exitGeminiLiveDock()
        }

        updateModelAndStyleLabels()
        applyVisualStyle()
        positionAtExactWidgetBounds(intent)
        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        overridePendingTransition(0, 0)
        setStaticWidgetOverlayState(active = true)
        if (intent != null) {
            setIntent(intent)
            positionAtExactWidgetBounds(intent)
            handleLaunchIntent(intent)
        }
    }

    private fun setStaticWidgetOverlayState(active: Boolean) {
        getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(GeminiPillWidgetProvider.KEY_OVERLAY_ACTIVE, active)
            .apply()
        GeminiPillWidgetProvider.refreshAllWidgets(this)
    }

    /**
     * Locks the live interactive widget to the exact screen coordinates of the Home Screen widget
     * using Gravity.BOTTOM so opening '+' menus or chat cards above the pill never shifts the pill downward.
     */
    private fun positionAtExactWidgetBounds(intent: Intent) {
        val prefs = getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val incomingBounds: Rect? = intent.sourceBounds

        if (incomingBounds != null && incomingBounds.width() > 0 && incomingBounds.bottom > 0) {
            prefs.edit()
                .putInt(GeminiPillWidgetProvider.KEY_SAVED_BOUNDS_LEFT, incomingBounds.left)
                .putInt(GeminiPillWidgetProvider.KEY_SAVED_BOUNDS_TOP, incomingBounds.top)
                .putInt(GeminiPillWidgetProvider.KEY_SAVED_BOUNDS_RIGHT, incomingBounds.right)
                .putInt(GeminiPillWidgetProvider.KEY_SAVED_BOUNDS_BOTTOM, incomingBounds.bottom)
                .apply()
        }

        val savedBottom = prefs.getInt(GeminiPillWidgetProvider.KEY_SAVED_BOUNDS_BOTTOM, -1)
        val savedLeft = prefs.getInt(GeminiPillWidgetProvider.KEY_SAVED_BOUNDS_LEFT, -1)
        val savedRight = prefs.getInt(GeminiPillWidgetProvider.KEY_SAVED_BOUNDS_RIGHT, -1)

        val screenH = resources.displayMetrics.heightPixels
        val screenW = resources.displayMetrics.widthPixels
        val density = resources.displayMetrics.density

        val params = anchorContainer.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

        if (savedBottom > 0) {
            // Keep the bottom of the pill locked to the exact bottom of the Home Screen widget
            // (only ensuring it stays above the keyboard if the widget was placed near the bottom dock)
            val rawBottomMargin = (screenH - savedBottom).coerceAtLeast((16 * density).toInt())
            params.bottomMargin = rawBottomMargin
            params.topMargin = 0

            // Match horizontal margins of the widget on the Home Screen if valid
            if (savedLeft >= 0 && savedRight > savedLeft && (savedRight - savedLeft) >= (140 * density).toInt()) {
                val hPad = ((screenW - (savedRight - savedLeft)) / 2).coerceAtLeast((8 * density).toInt())
                anchorContainer.setPadding(hPad, 0, hPad, 0)
            }
        } else {
            // Fallback to middle of home screen if launched from app drawer icon
            params.bottomMargin = (screenH * 0.44f).toInt()
            params.topMargin = 0
        }
        anchorContainer.layoutParams = params
    }

    private fun handleLaunchIntent(intent: Intent) {
        resetInactivityTimer()
        when (intent.getStringExtra("LAUNCH_MODE")) {
            "PLUS" -> {
                switchToPillBarMode()
                floatingPlusMenu.visibility = View.VISIBLE
            }
            "MIC" -> {
                switchToPillBarMode()
                floatingPlusMenu.visibility = View.GONE
                startVoiceRecognition()
            }
            "LIVE" -> {
                enterGeminiLiveDock()
            }
            "LIVE_VIDEO" -> {
                enterGeminiLiveDock(startVoiceImmediately = false)
                isLiveCameraActive = true
                liveBtnVideo.setBackgroundResource(R.drawable.bg_circle_button_active)
                launchCameraCapture()
            }
            "LIVE_SHARE" -> {
                enterGeminiLiveDock(startVoiceImmediately = false)
                launchGalleryPicker()
            }
            else -> {
                switchToPillBarMode()
                floatingPlusMenu.visibility = View.GONE
                editInput.requestFocus()
                editInput.postDelayed({
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(editInput, InputMethodManager.SHOW_IMPLICIT)
                }, 80)
            }
        }
    }

    private fun switchToPillBarMode() {
        isLiveMode = false
        auroraVisualizer.setRunning(false)
        floatingLiveDock.visibility = View.GONE
        floatingPillBar.visibility = View.VISIBLE
    }

    private fun enterGeminiLiveDock(startVoiceImmediately: Boolean = true) {
        isLiveMode = true
        floatingPlusMenu.visibility = View.GONE
        floatingPillBar.visibility = View.GONE
        floatingLiveDock.visibility = View.VISIBLE
        auroraVisualizer.setRunning(true)
        auroraVisualizer.setVoiceActive(true)

        getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(GeminiPillWidgetProvider.KEY_LIVE_MODE_ACTIVE, true)
            .apply()

        hideKeyboard()
        if (startVoiceImmediately && !isLiveMicMuted) {
            startVoiceRecognition()
        }
    }

    private fun exitGeminiLiveDock() {
        isLiveMode = false
        isLiveCameraActive = false
        auroraVisualizer.setRunning(false)
        tts?.stop()

        getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(GeminiPillWidgetProvider.KEY_LIVE_MODE_ACTIVE, false)
            .apply()

        closeOverlay(keepLiveWidgetState = false)
    }

    private fun updateModelAndStyleLabels() {
        val prefs = getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val model = prefs.getString(GeminiPillWidgetProvider.KEY_MODEL_NAME, "gemini-2.5-flash")
        val isPro = model == "gemini-2.5-pro"
        btnModelSelector.text = if (isPro) "✦ 2.5 Pro ▾" else "✦ 2.5 Flash ▾"
        statusTitle.text = if (isPro) "✦ Gemini 2.5 Pro" else "✦ Gemini 2.5 Flash"

        val style = prefs.getInt(
            GeminiPillWidgetProvider.KEY_VISUAL_STYLE,
            GeminiPillWidgetProvider.STYLE_REFRACTIVE
        )
        btnStyleSelector.text = when (style) {
            GeminiPillWidgetProvider.STYLE_FROSTED -> "◈ Frosted Glass"
            GeminiPillWidgetProvider.STYLE_REFRACTIVE -> "◈ 3D Liquid Glass"
            else -> "◈ Solid Color"
        }
    }

    private fun applyVisualStyle() {
        val prefs = getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val style = prefs.getInt(
            GeminiPillWidgetProvider.KEY_VISUAL_STYLE,
            GeminiPillWidgetProvider.STYLE_REFRACTIVE
        )
        updateModelAndStyleLabels()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (style == GeminiPillWidgetProvider.STYLE_REFRACTIVE || style == GeminiPillWidgetProvider.STYLE_FROSTED) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply {
                    blurBehindRadius = if (style == GeminiPillWidgetProvider.STYLE_REFRACTIVE) 42 else 28
                }
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }

        when (style) {
            GeminiPillWidgetProvider.STYLE_REFRACTIVE -> {
                floatingPillBar.background = pillLiquidDrawable
                floatingPlusMenu.background = menuLiquidDrawable
                responseCard.background = cardLiquidDrawable
                updateSensorRegistration(shouldRegister = true)
            }
            GeminiPillWidgetProvider.STYLE_FROSTED -> {
                floatingPillBar.setBackgroundResource(R.drawable.bg_gemini_pill_frosted)
                floatingPlusMenu.setBackgroundResource(R.drawable.bg_response_card_frosted)
                responseCard.setBackgroundResource(R.drawable.bg_response_card_frosted)
                updateSensorRegistration(shouldRegister = false)
            }
            else -> {
                floatingPillBar.setBackgroundResource(R.drawable.bg_gemini_pill)
                floatingPlusMenu.setBackgroundResource(R.drawable.bg_response_card)
                responseCard.setBackgroundResource(R.drawable.bg_response_card)
                updateSensorRegistration(shouldRegister = false)
            }
        }
    }

    private fun updateSensorRegistration(shouldRegister: Boolean) {
        if (shouldRegister && !isSensorRegistered && accelerometer != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            isSensorRegistered = true
        } else if (!shouldRegister && isSensorRegistered) {
            sensorManager?.unregisterListener(this)
            isSensorRegistered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val nx = (event.values[0] / 7.5f).coerceIn(-1f, 1f)
            val ny = (event.values[1] / 7.5f).coerceIn(-1f, 1f)
            pillLiquidDrawable?.setTilt(nx, ny)
            menuLiquidDrawable?.setTilt(nx, ny)
            cardLiquidDrawable?.setTilt(nx, ny)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun launchCameraCapture() {
        try {
            isLaunchingSubPicker = true
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(intent, REQ_CAMERA_PHOTO)
        } catch (e: Exception) {
            isLaunchingSubPicker = false
            Toast.makeText(this, "Camera unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchGalleryPicker() {
        try {
            isLaunchingSubPicker = true
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
            }
            startActivityForResult(Intent.createChooser(intent, "Select Image"), REQ_PICK_GALLERY)
        } catch (e: Exception) {
            isLaunchingSubPicker = false
            Toast.makeText(this, "Gallery unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchFilePicker() {
        try {
            isLaunchingSubPicker = true
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, REQ_PICK_FILE)
        } catch (e: Exception) {
            isLaunchingSubPicker = false
            Toast.makeText(this, "File picker unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setAttachment(base64: String, mimeType: String, label: String) {
        attachedBase64Data = base64
        attachedMimeType = mimeType
        attachedLabel = label
        attachmentChipLabel.text = "📎 $label"
        attachmentChipRow.visibility = View.VISIBLE
        floatingPlusMenu.visibility = View.GONE
    }

    private fun clearAttachment() {
        attachedBase64Data = null
        attachedMimeType = null
        attachedLabel = null
        attachmentChipRow.visibility = View.GONE
    }

    private fun encodeBitmapDownsampled(bitmap: Bitmap): String {
        val maxDim = 1024
        val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun startVoiceRecognition() {
        try {
            isLaunchingSubPicker = true
            auroraVisualizer.setVoiceActive(true)
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    if (isLiveMode) "Gemini Live — Listening..." else "Ask Gemini..."
                )
            }
            startActivityForResult(speechIntent, REQ_VOICE_INPUT)
        } catch (e: Exception) {
            isLaunchingSubPicker = false
            Toast.makeText(this, "Voice input unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        isLaunchingSubPicker = false
        setStaticWidgetOverlayState(active = true)
        resetInactivityTimer()
        if (resultCode != Activity.RESULT_OK || data == null) {
            auroraVisualizer.setVoiceActive(false)
            return
        }

        when (requestCode) {
            REQ_VOICE_INPUT -> {
                val matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spoken = matches?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    submitPrompt(spoken)
                } else {
                    auroraVisualizer.setVoiceActive(false)
                }
            }
            REQ_CAMERA_PHOTO -> {
                @Suppress("DEPRECATION")
                val bmp = data.extras?.get("data") as? Bitmap
                if (bmp != null) {
                    val b64 = encodeBitmapDownsampled(bmp)
                    setAttachment(b64, "image/jpeg", "Camera Photo")
                    if (isLiveMode) {
                        startVoiceRecognition()
                    }
                }
            }
            REQ_PICK_GALLERY -> {
                val uri: Uri? = data.data
                if (uri != null) {
                    try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val bmp = BitmapFactory.decodeStream(stream)
                            if (bmp != null) {
                                val b64 = encodeBitmapDownsampled(bmp)
                                setAttachment(b64, "image/jpeg", "Gallery Image")
                            }
                        }
                        if (isLiveMode) {
                            startVoiceRecognition()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            REQ_PICK_FILE -> {
                val uri: Uri? = data.data
                if (uri != null) {
                    try {
                        val mime = contentResolver.getType(uri) ?: "text/plain"
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = stream.readBytes()
                            val capped = if (bytes.size > 1_500_000) bytes.copyOf(1_500_000) else bytes
                            val b64 = Base64.encodeToString(capped, Base64.NO_WRAP)
                            setAttachment(b64, mime, "Document")
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not attach file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun submitPrompt(prompt: String) {
        resetInactivityTimer()
        editInput.setText("")
        floatingPlusMenu.visibility = View.GONE
        hideKeyboard()

        val prefs = getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)

        if (prompt.startsWith("AIza") && prompt.length >= 25 && !prompt.contains(" ")) {
            prefs.edit().putString(GeminiPillWidgetProvider.KEY_API_KEY, prompt).apply()
            responseCard.visibility = View.VISIBLE
            responseText.text = "✦ Gemini API Key saved! Ask anything or tap '+' to attach photos/files."
            return
        }

        responseCard.visibility = View.VISIBLE
        responseText.text = "✦ Thinking..."
        auroraVisualizer.setVoiceActive(true)

        val apiKey = prefs.getString(GeminiPillWidgetProvider.KEY_API_KEY, "") ?: ""
        val modelName = prefs.getString(GeminiPillWidgetProvider.KEY_MODEL_NAME, "gemini-2.5-flash") ?: "gemini-2.5-flash"

        val currentBase64 = attachedBase64Data
        val currentMime = attachedMimeType
        clearAttachment()

        thread {
            val answer = if (apiKey.length > 10) {
                callGeminiMultimodalApi(prompt, apiKey, modelName, currentBase64, currentMime)
            } else {
                "⚡ Tap '+' → '🔑 Key' (or paste your AIza... key right into the bar) to connect Gemini 2.5 Flash / Pro."
            }

            lastModelReply = answer

            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                responseCard.visibility = View.VISIBLE
                responseText.text = answer
                auroraVisualizer.setVoiceActive(isLiveMode)
                if (isLiveMode && !isLiveMicMuted) {
                    speakWithLazyTts(answer.take(380))
                }
            }
        }
    }

    private fun callGeminiMultimodalApi(
        prompt: String,
        apiKey: String,
        modelName: String,
        base64Data: String?,
        mimeType: String?
    ): String {
        return try {
            val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 12000
                readTimeout = 20000
            }

            val partsArray = JSONArray()
            if (!base64Data.isNullOrEmpty() && !mimeType.isNullOrEmpty()) {
                partsArray.put(
                    JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", mimeType)
                            put("data", base64Data)
                        })
                    }
                )
            }
            partsArray.put(JSONObject().put("text", prompt))

            val userTurn = JSONObject().apply {
                put("role", "user")
                put("parts", partsArray)
            }
            conversationHistory.put(userTurn)

            while (conversationHistory.length() > 8) {
                conversationHistory.remove(0)
            }

            val payload = JSONObject().apply {
                put("contents", conversationHistory)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val rawResponse = reader.readText()
            reader.close()

            val json = JSONObject(rawResponse)
            val reply = json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            conversationHistory.put(
                JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().put(JSONObject().put("text", reply)))
                }
            )
            reply
        } catch (e: Exception) {
            "Could not reach Gemini API (${e.localizedMessage}). Check your API key in '+' → '🔑 Key'."
        }
    }

    private fun speakWithLazyTts(text: String) {
        if (tts == null) {
            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsReady = true
                    tts?.language = Locale.getDefault()
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gemini_utt")
                }
            }
        } else if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gemini_utt")
        }
    }

    private fun resetInactivityTimer() {
        idleHandler.removeCallbacks(idleTimeoutRunnable)
        idleHandler.postDelayed(idleTimeoutRunnable, IDLE_TIMEOUT_MS)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editInput.windowToken, 0)
    }

    private fun closeOverlay(keepLiveWidgetState: Boolean) {
        idleHandler.removeCallbacks(idleTimeoutRunnable)
        auroraVisualizer.setRunning(false)
        updateSensorRegistration(shouldRegister = false)
        getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(GeminiPillWidgetProvider.KEY_OVERLAY_ACTIVE, false)
            .putBoolean(GeminiPillWidgetProvider.KEY_LIVE_MODE_ACTIVE, keepLiveWidgetState)
            .apply()
        GeminiPillWidgetProvider.refreshAllWidgets(this)
        hideKeyboard()
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        setStaticWidgetOverlayState(active = true)
        resetInactivityTimer()
        val prefs = getSharedPreferences(GeminiPillWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val style = prefs.getInt(
            GeminiPillWidgetProvider.KEY_VISUAL_STYLE,
            GeminiPillWidgetProvider.STYLE_REFRACTIVE
        )
        if (style == GeminiPillWidgetProvider.STYLE_REFRACTIVE) {
            updateSensorRegistration(shouldRegister = true)
        }
        if (isLiveMode) {
            auroraVisualizer.setRunning(true)
        }
    }

    override fun onPause() {
        updateSensorRegistration(shouldRegister = false)
        auroraVisualizer.setRunning(false)
        idleHandler.removeCallbacks(idleTimeoutRunnable)
        if (!isLaunchingSubPicker) {
            setStaticWidgetOverlayState(active = false)
        }
        super.onPause()
    }

    override fun onStop() {
        if (!isLaunchingSubPicker) {
            setStaticWidgetOverlayState(active = false)
        }
        super.onStop()
    }

    override fun onDestroy() {
        idleHandler.removeCallbacks(idleTimeoutRunnable)
        updateSensorRegistration(shouldRegister = false)
        auroraVisualizer.setRunning(false)
        setStaticWidgetOverlayState(active = false)
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
