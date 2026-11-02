package com.custom.geminiquickwidget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * 3D Liquid Glass drawable with multi-layer floating drop shadow,
 * convex specular lens highlight, and tilt-reactive chromatic dispersion refraction.
 */
class LiquidGlassDrawable(
    private val cornerRadiusPx: Float
) : Drawable() {

    private val shadowOuterRect = RectF()
    private val shadowKeyRect = RectF()
    private val glassRect = RectF()
    private val highlightRect = RectF()

    private var tiltX = 0f
    private var tiltY = 0f

    private val outerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(95, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val keyShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(135, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val topConvexHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val chromaticRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    fun setTilt(nx: Float, ny: Float) {
        val clampedX = nx.coerceIn(-1f, 1f)
        val clampedY = ny.coerceIn(-1f, 1f)
        // Battery dead-zone check: only rebuild shaders and redraw on meaningful tilt delta
        if (kotlin.math.abs(clampedX - tiltX) < 0.04f && kotlin.math.abs(clampedY - tiltY) < 0.04f) {
            return
        }
        tiltX = clampedX
        tiltY = clampedY
        rebuildShaders()
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        shadowOuterRect.set(bounds.left + 2f, bounds.top + 6f, bounds.right - 2f, bounds.bottom.toFloat())
        shadowKeyRect.set(bounds.left + 4f, bounds.top + 8f, bounds.right - 4f, bounds.bottom - 1f)
        glassRect.set(bounds.left + 3f, bounds.top + 2f, bounds.right - 3f, bounds.bottom - 6f)
        highlightRect.set(
            glassRect.left + 6f,
            glassRect.top + 2f,
            glassRect.right - 6f,
            glassRect.top + glassRect.height() * 0.48f
        )
        rebuildShaders()
    }

    private fun rebuildShaders() {
        val w = glassRect.width()
        val h = glassRect.height()
        if (w <= 0f || h <= 0f) return

        val shiftX = tiltX * w * 0.28f
        val shiftY = tiltY * h * 0.28f

        // 3D Refractive Glass Body with dynamic caustic shift
        bodyPaint.shader = LinearGradient(
            glassRect.left + shiftX,
            glassRect.top + shiftY,
            glassRect.right - shiftX,
            glassRect.bottom - shiftY,
            intArrayOf(
                Color.argb(120, 125, 211, 252), // Cyan refraction edge
                Color.argb(115, 24, 30, 44),    // Deep translucent glass core
                Color.argb(135, 255, 255, 255), // Moving internal specular band
                Color.argb(115, 26, 28, 40),    // Translucent core
                Color.argb(125, 192, 132, 252)  // Violet prismatic counter-edge
            ),
            floatArrayOf(0f, 0.28f, (0.5f + tiltX * 0.15f).coerceIn(0.32f, 0.68f), 0.74f, 1f),
            Shader.TileMode.CLAMP
        )

        // Top 3D convex specular lens sheen
        topConvexHighlightPaint.shader = LinearGradient(
            highlightRect.left,
            highlightRect.top,
            highlightRect.left,
            highlightRect.bottom,
            Color.argb(135, 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )

        // Prismatic 3D refractive rim
        chromaticRimPaint.shader = LinearGradient(
            glassRect.left + shiftX,
            glassRect.top,
            glassRect.right - shiftX,
            glassRect.bottom,
            intArrayOf(
                Color.argb(235, 255, 255, 255),
                Color.argb(190, 125, 211, 252),
                Color.argb(210, 216, 180, 254),
                Color.argb(230, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (glassRect.isEmpty) return
        canvas.drawRoundRect(shadowOuterRect, cornerRadiusPx, cornerRadiusPx, outerShadowPaint)
        canvas.drawRoundRect(shadowKeyRect, cornerRadiusPx, cornerRadiusPx, keyShadowPaint)
        canvas.drawRoundRect(glassRect, cornerRadiusPx, cornerRadiusPx, bodyPaint)
        canvas.drawRoundRect(highlightRect, cornerRadiusPx, cornerRadiusPx, topConvexHighlightPaint)
        canvas.drawRoundRect(glassRect, cornerRadiusPx, cornerRadiusPx, chromaticRimPaint)
    }

    override fun setAlpha(alpha: Int) {
        bodyPaint.alpha = alpha
        chromaticRimPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bodyPaint.colorFilter = colorFilter
        chromaticRimPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
