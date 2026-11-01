package com.custom.geminiquickwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CLEAR_INLINE) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_ANSWER)
                .apply()
            refreshAllWidgets(context)
        }
    }

    companion object {
        const val PREFS_NAME = "gemini_quick_widget_prefs"
        const val KEY_LAST_ANSWER = "last_inline_answer"
        const val KEY_API_KEY = "gemini_api_key"
        const val ACTION_CLEAR_INLINE = "com.custom.geminiquickwidget.ACTION_CLEAR_INLINE"

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, GeminiPillWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private fun buildOverlayIntent(context: Context, mode: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, QuickOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("LAUNCH_MODE", mode)
            }
            return PendingIntent.getActivity(
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

            // Wire each zone of the pill to launch the lightweight translucent overlay (never the full Gemini app)
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

            // Display latest answer inline on the Home Screen widget if present
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastAnswer = prefs.getString(KEY_LAST_ANSWER, null)
            if (!lastAnswer.isNullOrBlank()) {
                views.setViewVisibility(R.id.widget_answer_card, View.VISIBLE)
                views.setTextViewText(R.id.widget_answer_text, lastAnswer)

                val clearIntent = Intent(context, GeminiPillWidgetProvider::class.java).apply {
                    action = ACTION_CLEAR_INLINE
                }
                val clearPending = PendingIntent.getBroadcast(
                    context,
                    105,
                    clearIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_clear, clearPending)
            } else {
                views.setViewVisibility(R.id.widget_answer_card, View.GONE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
