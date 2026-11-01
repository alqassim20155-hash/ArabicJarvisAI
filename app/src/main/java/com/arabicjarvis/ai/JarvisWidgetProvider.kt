package com.arabicjarvis.ai

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class JarvisWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.jarvis_widget)
            fun launch(actionName: String, requestCode: Int): PendingIntent {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = actionName
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                return PendingIntent.getActivity(context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            views.setOnClickPendingIntent(R.id.widget_voice, launch(ACTION_VOICE, 1001))
            views.setOnClickPendingIntent(R.id.widget_chat, launch(ACTION_CHAT, 1002))
            views.setOnClickPendingIntent(R.id.widget_start, launch(ACTION_START, 1003))
            views.setOnClickPendingIntent(R.id.widget_stop, launch(ACTION_STOP, 1004))
            views.setOnClickPendingIntent(R.id.widget_status_action, launch(ACTION_STATUS, 1005))
            views.setOnClickPendingIntent(R.id.widget_camera, launch(ACTION_CAMERA, 1006))
            views.setOnClickPendingIntent(R.id.widget_settings, launch(ACTION_SETTINGS, 1007))
            views.setOnClickPendingIntent(R.id.widget_phone, launch(ACTION_PHONE, 1008))
            views.setOnClickPendingIntent(R.id.widget_wifi, launch(ACTION_WIFI, 1009))
            views.setOnClickPendingIntent(R.id.widget_browser, launch(ACTION_BROWSER, 1010))
            views.setOnClickPendingIntent(R.id.widget_maps, launch(ACTION_MAPS, 1011))
            views.setOnClickPendingIntent(R.id.widget_whatsapp, launch(ACTION_WHATSAPP, 1012))
            views.setOnClickPendingIntent(R.id.widget_youtube, launch(ACTION_YOUTUBE, 1013))
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        const val ACTION_VOICE = "com.arabicjarvis.ai.WIDGET_VOICE"
        const val ACTION_CHAT = "com.arabicjarvis.ai.WIDGET_CHAT"
        const val ACTION_START = "com.arabicjarvis.ai.WIDGET_START"
        const val ACTION_STOP = "com.arabicjarvis.ai.WIDGET_STOP"
        const val ACTION_STATUS = "com.arabicjarvis.ai.WIDGET_STATUS"
        const val ACTION_CAMERA = "com.arabicjarvis.ai.WIDGET_CAMERA"
        const val ACTION_SETTINGS = "com.arabicjarvis.ai.WIDGET_SETTINGS"
        const val ACTION_PHONE = "com.arabicjarvis.ai.WIDGET_PHONE"
        const val ACTION_WIFI = "com.arabicjarvis.ai.WIDGET_WIFI"
        const val ACTION_BROWSER = "com.arabicjarvis.ai.WIDGET_BROWSER"
        const val ACTION_MAPS = "com.arabicjarvis.ai.WIDGET_MAPS"
        const val ACTION_WHATSAPP = "com.arabicjarvis.ai.WIDGET_WHATSAPP"
        const val ACTION_YOUTUBE = "com.arabicjarvis.ai.WIDGET_YOUTUBE"

        fun updateStatus(context: Context, text: String) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, JarvisWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.jarvis_widget)
            views.setTextViewText(R.id.widget_status, text)
            manager.partiallyUpdateAppWidget(ids, views)
        }
    }
}
