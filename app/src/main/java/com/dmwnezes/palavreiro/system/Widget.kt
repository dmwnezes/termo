package com.dmwnezes.palavreiro.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dmwnezes.palavreiro.MainActivity
import com.dmwnezes.palavreiro.R
import com.dmwnezes.palavreiro.data.Store

/** Widget da tela inicial: sequência e o que já foi jogado hoje. */
class Widget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, Widget::class.java))
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun open(context: Context, dest: String, code: Int): PendingIntent =
            PendingIntent.getActivity(
                context, code,
                Intent(context, MainActivity::class.java).putExtra("dest", dest).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun views(context: Context): RemoteViews {
            val s = DailyStatus.read(Store(context))
            val v = RemoteViews(context.packageName, R.layout.widget)
            v.setTextViewText(R.id.w_streak, if (s.streak > 0) "🔥 ${s.streak}" else "🔥 0")
            v.setTextViewText(R.id.w_progress, "${s.doneCount} de ${s.total} hoje")
            fun mark(done: Boolean) = if (done) "✓" else "•"
            v.setTextViewText(R.id.w_termo, "${mark(s.termo)}  Termo")
            v.setTextViewText(R.id.w_dueto, "${mark(s.dueto)}  Dueto")
            v.setTextViewText(R.id.w_quarteto, "${mark(s.quarteto)}  Quarteto")
            v.setTextViewText(R.id.w_conexoes, "${mark(s.conexoes)}  Conexões")
            v.setOnClickPendingIntent(R.id.w_root, open(context, "HOME", 10))
            v.setOnClickPendingIntent(R.id.w_termo, open(context, "TERMO", 11))
            v.setOnClickPendingIntent(R.id.w_dueto, open(context, "DUETO", 12))
            v.setOnClickPendingIntent(R.id.w_quarteto, open(context, "QUARTETO", 13))
            v.setOnClickPendingIntent(R.id.w_conexoes, open(context, "CONEXOES", 14))
            return v
        }
    }
}
