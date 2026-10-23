package com.flowy.link

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.flowy.link.R

class LinkWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, views(context)) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LinkWidget::class.java))
            ids.forEach { id -> manager.updateAppWidget(id, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val session = loadSession(context)
            val views = RemoteViews(context.packageName, R.layout.link_widget)
            views.setTextViewText(R.id.widget_name, session?.name ?: "未连接电脑")
            views.setTextViewText(R.id.widget_status, if (session == null) "打开应用后连接" else "已配对 · 不含对话正文")
            return views
        }
    }
}
