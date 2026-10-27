package com.rupeeflow.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.rupeeflow.app.MainActivity
import com.rupeeflow.app.R
import com.rupeeflow.app.container
import com.rupeeflow.app.data.Snapshot
import com.rupeeflow.app.notify.Notifier
import com.rupeeflow.core.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Home-screen widget: today's safe-to-spend plus one-tap Add and Voice buttons. */
class QuickAddWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.update(context, context.container.snapshot())
            } catch (_: Exception) {
                WidgetUpdater.render(context, null)
            } finally {
                pending.finish()
            }
        }
    }
}

object WidgetUpdater {
    fun update(context: Context, snap: Snapshot) = render(context, snap)

    fun render(context: Context, snap: Snapshot?) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, QuickAddWidget::class.java))
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_quick_add)
        if (snap == null || !snap.settings.onboarded) {
            views.setTextViewText(R.id.widget_amount, "—")
            views.setTextViewText(R.id.widget_sub, "Open RupeeFlow to set up")
        } else if (snap.settings.hideAmounts) {
            views.setTextViewText(R.id.widget_amount, "₹ ••••")
            views.setTextViewText(R.id.widget_sub, "Amounts hidden")
        } else {
            val g = snap.settings.indianGrouping
            val left = snap.safe.leftToday
            views.setTextViewText(R.id.widget_amount, Money.format(maxOf(0, left), g))
            views.setTextViewText(R.id.widget_sub,
                "left today · spent ${Money.format(snap.spentToday, g)}" + if (left < 0) " (over by ${Money.format(-left, g)})" else "")
        }
        views.setOnClickPendingIntent(R.id.widget_root, launch(context, null, 31))
        views.setOnClickPendingIntent(R.id.widget_add, launch(context, Notifier.ACTION_QUICK_ADD, 32))
        views.setOnClickPendingIntent(R.id.widget_voice, launch(context, Notifier.ACTION_VOICE_ADD, 33))
        manager.updateAppWidget(ids, views)
    }

    private fun launch(context: Context, action: String?, code: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            this.action = action ?: Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** Quick Settings tile: pull down the shade, tap, type amount. */
class QuickAddTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val i = Intent(this, MainActivity::class.java).apply {
            action = Notifier.ACTION_QUICK_ADD
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 41, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}
