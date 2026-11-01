package com.arabicjarvis.ai

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class JarvisWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.jarvis_widget)
            fun launch(voice: Boolean): PendingIntent {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = if (voice) ACTION_VOICE else ACTION_CHAT
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                return PendingIntent.getActivity(context, if (voice) 1001 else 1002, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            views.setOnClickPendingIntent(R.id.widget_voice, launch(true))
            views.setOnClickPendingIntent(R.id.widget_chat, launch(false))
            manager.updateAppWidget(id, views)
        }
    }
    companion object {
        const val ACTION_VOICE = "com.arabicjarvis.ai.WIDGET_VOICE"
        const val ACTION_CHAT = "com.arabicjarvis.ai.WIDGET_CHAT"
    }
}
