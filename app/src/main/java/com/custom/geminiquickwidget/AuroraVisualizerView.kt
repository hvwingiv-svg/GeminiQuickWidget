package com.custom.geminiquickwidget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hardware-accelerated Gemini Live center pill visualizer matching the full Google Gemini app:
 * Deep black top fading into glowing blue-and-white organic aurora waves at the bottom,
 * with built-in floating drop shadow and battery-saving adaptive frame rate throttling.
 */
class AuroraVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val pillRect = RectF()
    private val shadowRect = RectF()
    private val clipPath = Path()
    private val wavePath1 = Path()
    private val wavePath2 = Path()

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#040508")
        style = Paint.Style.FILL
    }

    private val auroraGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val wavePaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val wavePaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val coreWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.argb(75, 186, 230, 253)
    }

    private var phase = 0f
    private var voiceEnergy = 0.25f
    private var isAnimating = false
    private var lastActiveSpeechTime = 0L

    fun setVoiceActive(active: Boolean) {
        voiceEnergy = if (active) 0.85f else 0.22f
        if (active) {
            lastActiveSpeechTime = System.currentTimeMillis()
        }
        if (isAnimating) {
            postInvalidateOnAnimation()
        }
    }

    fun setRunning(running: Boolean) {
        if (isAnimating == running) return
        isAnimating = running
        if (running) {
            lastActiveSpeechTime = System.currentTimeMillis()
            postInvalidateOnAnimation()
        } else {
            removeCallbacks(frameRunnable)
        }
    }

    private val frameRunnable = Runnable {
        if (isAnimating && isAttachedToWindow && visibility == VISIBLE) {
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = 6f
        pillRect.set(pad, 3f, w - pad, h - 9f)
        shadowRect.set(pad + 1f, 8f, w - pad - 1f, h - 2f)

        val radius = pillRect.height() / 2f
        clipPath.reset()
        clipPath.addRoundRect(pillRect, radius, radius, Path.Direction.CW)

        updateShaders(w.toFloat(), h.toFloat())
    }

    private fun updateShaders(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        auroraGlowPaint.shader = LinearGradient(
            0f, h * 0.25f, 0f, h,
            intArrayOf(
                Color.parseColor("#00040508"),
                Color.parseColor("#441E3A8A"),
                Color.parseColor("#B360A5FA"),
                Color.parseColor("#E6E0F2FE")
            ),
            floatArrayOf(0f, 0.45f, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )

        wavePaint1.shader = LinearGradient(
            0f, h * 0.4f, 0f, h,
            Color.parseColor("#9938BDF8"),
            Color.parseColor("#EEBAE6FD"),
            Shader.TileMode.CLAMP
        )

        wavePaint2.shader = LinearGradient(
            0f, h * 0.5f, 0f, h,
            Color.parseColor("#AA93C5FD"),
            Color.parseColor("#FFFFFFFF"),
            Shader.TileMode.CLAMP
        )

        coreWhitePaint.shader = RadialGradient(
            w * 0.48f,
            h * 0.92f,
            w * 0.48f,
            intArrayOf(
                Color.parseColor("#FFFFFFFF"),
                Color.parseColor("#CC93C5FD"),
                Color.parseColor("#001E3A8A")
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = pillRect.height() / 2f

        // 1. Draw floating drop shadow beneath the pill
        canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)

        // 2. Clip to stadium pill and draw dark void base + glowing blue-white aurora waves
        val saveCount = canvas.save()
        canvas.clipPath(clipPath)

        canvas.drawRect(pillRect, basePaint)
        canvas.drawRect(pillRect, auroraGlowPaint)

        // Organic aurora wave 1
        wavePath1.reset()
        val baseLine1 = pillRect.top + pillRect.height() * (0.64f - voiceEnergy * 0.12f)
        val amp1 = pillRect.height() * (0.08f + voiceEnergy * 0.10f)
        wavePath1.moveTo(pillRect.left, pillRect.bottom)
        var x = pillRect.left
        val step = 12f
        while (x <= pillRect.right + step) {
            val normX = (x - pillRect.left) / pillRect.width()
            val y = baseLine1 +
                sin(normX * 4.2f + phase).toFloat() * amp1 +
                cos(normX * 2.5f - phase * 0.7f).toFloat() * (amp1 * 0.5f)
            wavePath1.lineTo(x, y)
            x += step
        }
        wavePath1.lineTo(pillRect.right, pillRect.bottom)
        wavePath1.close()
        canvas.drawPath(wavePath1, wavePaint1)

        // Luminous white-blue core wave 2
        wavePath2.reset()
        val baseLine2 = pillRect.top + pillRect.height() * (0.74f - voiceEnergy * 0.10f)
        val amp2 = pillRect.height() * (0.06f + voiceEnergy * 0.08f)
        wavePath2.moveTo(pillRect.left, pillRect.bottom)
        x = pillRect.left
        while (x <= pillRect.right + step) {
            val normX = (x - pillRect.left) / pillRect.width()
            val y = baseLine2 +
                cos(normX * 3.6f - phase * 1.15f).toFloat() * amp2
            wavePath2.lineTo(x, y)
            x += step
        }
        wavePath2.lineTo(pillRect.right, pillRect.bottom)
        wavePath2.close()
        canvas.drawPath(wavePath2, wavePaint2)

        // Soft glowing center-bottom bloom matching the screenshot
        canvas.drawRect(pillRect, coreWhitePaint)

        canvas.restoreToCount(saveCount)

        // 3. Subtle specular border
        canvas.drawRoundRect(pillRect, radius, radius, borderPaint)

        // 4. Battery-optimized adaptive frame scheduling:
        // 30fps during active speech, 12fps gentle breathing when silent for >2.5s
        if (isAnimating && isAttachedToWindow && visibility == VISIBLE) {
            val idleMs = System.currentTimeMillis() - lastActiveSpeechTime
            val delayMs = if (idleMs > 2500L) 80L else 32L
            phase += if (idleMs > 2500L) 0.07f else 0.16f
            removeCallbacks(frameRunnable)
            postDelayed(frameRunnable, delayMs)
        }
    }

    override fun onDetachedFromWindow() {
        setRunning(false)
        super.onDetachedFromWindow()
    }
}
