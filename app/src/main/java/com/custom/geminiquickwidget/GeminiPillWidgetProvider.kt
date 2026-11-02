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

/**
 * Zero-Idle-Drain Home Screen Widget Provider (updatePeriodMillis = 0).
 * Never runs background polling or timers. Supports dynamic extreme resizing,
 * 3 visual aesthetics with multi-layer drop shadows, and in-place morphing
 * into the 5-button Gemini Live dock.
 */
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
        if (intent.action == ACTION_EXIT_LIVE) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LIVE_MODE_ACTIVE, false)
                .apply()
            refreshAllWidgets(context)
        }
    }

    companion object {
        const val PREFS_NAME = "gemini_quick_widget_prefs"
        const val KEY_API_KEY = "gemini_api_key"
        const val KEY_VISUAL_STYLE = "visual_style_mode"
        const val KEY_MODEL_NAME = "selected_gemini_model"
        const val KEY_LIVE_MODE_ACTIVE = "is_gemini_live_dock_active"

        const val STYLE_SOLID = 0
        const val STYLE_FROSTED = 1
        const val STYLE_REFRACTIVE = 2

        const val ACTION_EXIT_LIVE = "com.custom.geminiquickwidget.ACTION_EXIT_LIVE"

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

            val isLiveDock = prefs.getBoolean(KEY_LIVE_MODE_ACTIVE, false)
            val style = prefs.getInt(KEY_VISUAL_STYLE, STYLE_REFRACTIVE)

            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220)
            val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 60)

            if (isLiveDock) {
                // In-Place Gemini Live 5-Button Dock Mode
                views.setViewVisibility(R.id.widget_pill_root, View.GONE)
                views.setViewVisibility(R.id.widget_live_dock_root, View.VISIBLE)

                // Dynamic width adaptation for narrow widgets in Live mode
                if (minW < 150) {
                    views.setViewVisibility(R.id.widget_live_btn_video, View.GONE)
                    views.setViewVisibility(R.id.widget_live_btn_share, View.GONE)
                } else if (minW < 210) {
                    views.setViewVisibility(R.id.widget_live_btn_video, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_live_btn_share, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_live_btn_video, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_live_btn_share, View.VISIBLE)
                }

                views.setOnClickPendingIntent(
                    R.id.widget_live_btn_video,
                    buildOverlayIntent(context, "LIVE_VIDEO", 201)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_live_btn_share,
                    buildOverlayIntent(context, "LIVE_SHARE", 202)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_live_aurora_pill,
                    buildOverlayIntent(context, "LIVE", 203)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_live_btn_mic,
                    buildOverlayIntent(context, "LIVE", 204)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_live_btn_close,
                    buildBroadcastIntent(context, ACTION_EXIT_LIVE, 205)
                )
            } else {
                // Clean Standard Floating Pill Mode (No unwanted box above the pill)
                views.setViewVisibility(R.id.widget_live_dock_root, View.GONE)
                views.setViewVisibility(R.id.widget_pill_root, View.VISIBLE)

                val pillBg = when (style) {
                    STYLE_FROSTED -> R.drawable.bg_gemini_pill_frosted
                    STYLE_REFRACTIVE -> R.drawable.bg_gemini_pill_refractive
                    else -> R.drawable.bg_gemini_pill
                }
                views.setInt(R.id.widget_pill_root, "setBackgroundResource", pillBg)

                // Dynamic extreme resize scaling (40dp x 28dp micro strip to full screen)
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

                val isUltraShort = minH < 48
                val isTall = minH > 100

                val padH = when {
                    minW < 105 -> dpToPx(context, 8)
                    isTall -> dpToPx(context, 18)
                    else -> dpToPx(context, 14)
                }
                val padV = when {
                    isUltraShort -> dpToPx(context, 3)
                    isTall -> dpToPx(context, 14)
                    else -> dpToPx(context, 8)
                }
                views.setViewPadding(R.id.widget_pill_root, padH, padV, padH, padV + dpToPx(context, 2))

                val promptTextSp = when {
                    isUltraShort || minW < 105 -> 13f
                    isTall -> 19f
                    else -> 17f
                }
                views.setTextViewTextSize(R.id.widget_btn_prompt, TypedValue.COMPLEX_UNIT_SP, promptTextSp)
                val idleColor = if (style == STYLE_REFRACTIVE) 0xFFF8FAFC.toInt() else 0xFFD6D8DA.toInt()
                views.setTextColor(R.id.widget_btn_prompt, idleColor)

                views.setOnClickPendingIntent(
                    R.id.widget_btn_plus,
                    buildOverlayIntent(context, "PLUS", 101)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_prompt,
                    buildOverlayIntent(context, "TEXT", 102)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_mic,
                    buildOverlayIntent(context, "MIC", 103)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_live,
                    buildOverlayIntent(context, "LIVE", 104)
                )
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
