package com.custom.geminiquickwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

class GeminiPillWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        when (intent.action) {
            ACTION_CLEAR_INLINE -> {
                prefs.edit().remove(KEY_LAST_ANSWER).apply()
                refreshAllWidgets(context)
            }
            ACTION_CYCLE_STYLE -> {
                val current = prefs.getInt(KEY_VISUAL_STYLE, STYLE_SOLID)
                val next = (current + 1) % 3
                val styleLabel = when (next) {
                    STYLE_FROSTED -> "Frosted Glass"
                    STYLE_REFRACTIVE -> "Refractive Glass"
                    else -> "Solid Color"
                }
                prefs.edit()
                    .putInt(KEY_VISUAL_STYLE, next)
                    .putString(KEY_LAST_ANSWER, "✦ Aesthetic: $styleLabel")
                    .apply()
                refreshAllWidgets(context)
            }
        }
    }

    companion object {
        const val PREFS_NAME = "gemini_quick_widget_prefs"
        const val KEY_LAST_ANSWER = "last_inline_answer"
        const val KEY_API_KEY = "gemini_api_key"
        const val KEY_VISUAL_STYLE = "visual_style_mode"
        const val KEY_IS_TYPING = "is_live_typing"
        const val KEY_LIVE_DRAFT = "live_draft_text"

        const val STYLE_SOLID = 0
        const val STYLE_FROSTED = 1
        const val STYLE_REFRACTIVE = 2

        const val ACTION_CLEAR_INLINE = "com.custom.geminiquickwidget.ACTION_CLEAR_INLINE"
        const val ACTION_CYCLE_STYLE = "com.custom.geminiquickwidget.ACTION_CYCLE_STYLE"

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, GeminiPillWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private fun dpToPx(context: Context, dp: Int): Int {
            return (dp * context.resources.displayMetrics.density).toInt()
        }

        private fun buildOverlayIntent(context: Context, mode: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, QuickOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("LAUNCH_MODE", mode)
            }
            return PendingIntent.getActivity(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildBroadcastIntent(context: Context, action: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, GeminiPillWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_gemini_pill)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // 1. Apply Visual Aesthetic (Solid Color, Frosted Glass, Refractive Glass)
            val style = prefs.getInt(KEY_VISUAL_STYLE, STYLE_SOLID)
            val pillBg = when (style) {
                STYLE_FROSTED -> R.drawable.bg_gemini_pill_frosted
                STYLE_REFRACTIVE -> R.drawable.bg_gemini_pill_refractive
                else -> R.drawable.bg_gemini_pill
            }
            val cardBg = when (style) {
                STYLE_FROSTED -> R.drawable.bg_response_card_frosted
                STYLE_REFRACTIVE -> R.drawable.bg_response_card_refractive
                else -> R.drawable.bg_response_card
            }
            views.setInt(R.id.widget_pill_root, "setBackgroundResource", pillBg)
            views.setInt(R.id.widget_answer_card, "setBackgroundResource", cardBg)

            // 2. Dynamic Extreme Resizing Adaptation
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220)
            val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 60)

            // Width adaptation: hide secondary icons when shrunk narrow so layout never breaks
            when {
                minW < 105 -> {
                    views.setViewVisibility(R.id.widget_btn_plus, View.GONE)
                    views.setViewVisibility(R.id.widget_btn_mic, View.GONE)
                }
                minW < 165 -> {
                    views.setViewVisibility(R.id.widget_btn_plus, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_btn_mic, View.GONE)
                }
                else -> {
                    views.setViewVisibility(R.id.widget_btn_plus, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_btn_mic, View.VISIBLE)
                }
            }

            // Height & Typography adaptation: micro strip (28dp) up to full-screen tall
            val isUltraShort = minH < 48
            val isTall = minH > 100

            val padH = when {
                minW < 105 -> dpToPx(context, 6)
                isTall -> dpToPx(context, 16)
                else -> dpToPx(context, 12)
            }
            val padV = when {
                isUltraShort -> dpToPx(context, 2)
                isTall -> dpToPx(context, 10)
                else -> dpToPx(context, 6)
            }
            views.setViewPadding(R.id.widget_pill_root, padH, padV, padH, padV)

            val promptTextSp = when {
                isUltraShort || minW < 105 -> 13f
                isTall -> 18f
                else -> 16f
            }
            views.setTextViewTextSize(R.id.widget_btn_prompt, TypedValue.COMPLEX_UNIT_SP, promptTextSp)
            views.setInt(R.id.widget_btn_prompt, "setMaxLines", if (isTall) 3 else 1)

            // 3. Direct In-Widget Live Typing State
            val isTyping = prefs.getBoolean(KEY_IS_TYPING, false)
            val liveDraft = prefs.getString(KEY_LIVE_DRAFT, "") ?: ""

            if (isTyping) {
                val displayText = if (liveDraft.isEmpty()) {
                    "Type prompt or AIza... key▍"
                } else {
                    "${liveDraft}▍"
                }
                views.setTextViewText(R.id.widget_btn_prompt, displayText)
                views.setTextColor(R.id.widget_btn_prompt, 0xFFFFFFFF.toInt())
                views.setImageViewResource(R.id.widget_right_icon, R.drawable.ic_send)
                views.setOnClickPendingIntent(
                    R.id.widget_btn_live,
                    buildOverlayIntent(context, "SEND_NOW", 106)
                )
            } else {
                views.setTextViewText(R.id.widget_btn_prompt, context.getString(R.string.whats_next))
                val idleColor = if (style == STYLE_REFRACTIVE) 0xFFF1F3F4.toInt() else 0xFFC4C7C5.toInt()
                views.setTextColor(R.id.widget_btn_prompt, idleColor)
                views.setImageViewResource(R.id.widget_right_icon, R.drawable.ic_waveform)
                views.setOnClickPendingIntent(
                    R.id.widget_btn_live,
                    buildOverlayIntent(context, "LIVE", 104)
                )
            }

            // Wire buttons directly on the Home Screen widget
            views.setOnClickPendingIntent(
                R.id.widget_btn_plus,
                buildBroadcastIntent(context, ACTION_CYCLE_STYLE, 101)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_prompt,
                buildOverlayIntent(context, "TEXT", 102)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_mic,
                buildOverlayIntent(context, "MIC", 103)
            )

            // 4. Inline Answer Card inside the Home Screen Widget
            val lastAnswer = prefs.getString(KEY_LAST_ANSWER, null)
            if (!lastAnswer.isNullOrBlank() && minH >= 42) {
                views.setViewVisibility(R.id.widget_answer_card, View.VISIBLE)
                views.setTextViewText(R.id.widget_answer_text, lastAnswer)

                val answerMaxLines = when {
                    minH < 65 -> 2
                    minH < 100 -> 4
                    else -> ((minH - 50) / 14).coerceIn(5, 30)
                }
                views.setInt(R.id.widget_answer_text, "setMaxLines", answerMaxLines)
                val answerTextSp = if (minH < 65) 11.5f else 13.5f
                views.setTextViewTextSize(R.id.widget_answer_text, TypedValue.COMPLEX_UNIT_SP, answerTextSp)

                views.setOnClickPendingIntent(
                    R.id.widget_btn_clear,
                    buildBroadcastIntent(context, ACTION_CLEAR_INLINE, 105)
                )
            } else {
                views.setViewVisibility(R.id.widget_answer_card, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
