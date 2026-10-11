package com.dmwnezes.palavreiro.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.dmwnezes.palavreiro.MainActivity
import com.dmwnezes.palavreiro.R
import com.dmwnezes.palavreiro.data.Store

/**
 * Peças comuns dos widgets: tamanhos (pequeno/médio/grande), escolha do layout e atalhos para o app.
 * No Android 12+ o sistema troca o layout sozinho ao redimensionar (mapa de tamanhos);
 * antes disso, o layout é escolhido pelo tamanho informado em onAppWidgetOptionsChanged.
 */
object WidgetKit {
    enum class Size { SMALL, MEDIUM, LARGE }

    /** A partir de que tamanho (dp) cada layout é usado. */
    data class Spec(val mediumW: Int, val mediumH: Int, val largeW: Int, val largeH: Int)

    fun bucket(widthDp: Int, heightDp: Int, spec: Spec): Size = when {
        widthDp >= spec.largeW && heightDp >= spec.largeH -> Size.LARGE
        widthDp >= spec.mediumW && heightDp >= spec.mediumH -> Size.MEDIUM
        else -> Size.SMALL
    }

    /** Layouts para cada tamanho do widget [id]. */
    fun views(context: Context, manager: AppWidgetManager?, id: Int, spec: Spec, make: (Size) -> RemoteViews): RemoteViews {
        if (Build.VERSION.SDK_INT >= 31) {
            return RemoteViews(
                mapOf(
                    SizeF(40f, 40f) to make(Size.SMALL),
                    SizeF(spec.mediumW.toFloat(), spec.mediumH.toFloat()) to make(Size.MEDIUM),
                    SizeF(spec.largeW.toFloat(), spec.largeH.toFloat()) to make(Size.LARGE),
                )
            )
        }
        val o: Bundle? = manager?.getAppWidgetOptions(id)
        val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        return make(if (w == 0 || h == 0) Size.MEDIUM else bucket(w, h, spec))
    }

    fun ids(context: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, cls)) ?: IntArray(0)

    fun open(context: Context, dest: String, code: Int): PendingIntent =
        PendingIntent.getActivity(
            context, code,
            Intent(context, MainActivity::class.java).putExtra("dest", dest).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Widget "Hoje": sequência e o que já foi jogado hoje (pequeno, médio e grande). */
class Widget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        update(context, manager, id)
    }

    companion object {
        val SPEC = WidgetKit.Spec(mediumW = 150, mediumH = 180, largeW = 250, largeH = 310)

        /** Atualiza todos os widgets do app (Hoje, Sequência, Próxima palavra e Palavra Relâmpago). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            WidgetKit.ids(context, Widget::class.java).forEach { update(context, manager, it) }
            StreakWidget.refresh(context)
            NextWordWidget.refresh(context)
            FlashWidget.refresh(context)
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val s = DailyStatus.read(Store(context))
            manager.updateAppWidget(id, WidgetKit.views(context, manager, id, SPEC) { views(context, it, s) })
        }

        /** Layout do tamanho pedido com os dados do dia. */
        fun views(context: Context, size: WidgetKit.Size, s: DailyStatus): RemoteViews {
            val layout = when (size) {
                WidgetKit.Size.SMALL -> R.layout.widget_today_small
                WidgetKit.Size.MEDIUM -> R.layout.widget
                WidgetKit.Size.LARGE -> R.layout.widget_today_large
            }
            val v = RemoteViews(context.packageName, layout)
            v.setTextViewText(R.id.w_streak, "🔥 ${s.streak}")
            v.setOnClickPendingIntent(R.id.w_root, WidgetKit.open(context, "HOME", 10))
            if (size == WidgetKit.Size.SMALL) {
                v.setTextViewText(R.id.w_count, "${s.doneCount}")
                v.setTextViewText(R.id.w_total, " de ${s.total} hoje")
                val done = listOf(s.termo, s.dueto, s.quarteto, s.conexoes, s.caca)
                listOf(R.id.w_d1, R.id.w_d2, R.id.w_d3, R.id.w_d4, R.id.w_d5).forEachIndexed { i, id ->
                    v.setInt(id, "setBackgroundResource", if (done[i]) R.drawable.wsq_green else R.drawable.wday_none)
                }
                return v
            }
            v.setTextViewText(R.id.w_progress, if (s.doneCount == s.total) "Tudo feito hoje! 🎉" else "${s.doneCount} de ${s.total} hoje")
            fun mark(done: Boolean) = if (done) "✓" else "○"
            v.setTextViewText(R.id.w_termo, "${mark(s.termo)}  Termo")
            v.setTextViewText(R.id.w_dueto, "${mark(s.dueto)}  Dueto")
            v.setTextViewText(R.id.w_quarteto, "${mark(s.quarteto)}  Quarteto")
            v.setTextViewText(R.id.w_conexoes, "${mark(s.conexoes)}  Conexões")
            v.setTextViewText(R.id.w_caca, "${mark(s.caca)}  Caça-Palavras")
            v.setOnClickPendingIntent(R.id.w_termo, WidgetKit.open(context, "TERMO", 11))
            v.setOnClickPendingIntent(R.id.w_dueto, WidgetKit.open(context, "DUETO", 12))
            v.setOnClickPendingIntent(R.id.w_quarteto, WidgetKit.open(context, "QUARTETO", 13))
            v.setOnClickPendingIntent(R.id.w_conexoes, WidgetKit.open(context, "CONEXOES", 14))
            v.setOnClickPendingIntent(R.id.w_caca, WidgetKit.open(context, "CACA", 15))
            if (size == WidgetKit.Size.LARGE) {
                v.setProgressBar(R.id.w_bar, s.total, s.doneCount, false)
                // Botão leva ao próximo desafio que falta.
                val next = listOf(s.termo to "TERMO", s.dueto to "DUETO", s.quarteto to "QUARTETO", s.conexoes to "CONEXOES", s.caca to "CACA")
                    .firstOrNull { !it.first }?.second
                val label = mapOf("TERMO" to "Jogar o Termo do dia", "DUETO" to "Jogar o Dueto", "QUARTETO" to "Jogar o Quarteto", "CONEXOES" to "Jogar o Conexões", "CACA" to "Jogar o Caça-Palavras")
                v.setTextViewText(R.id.w_play, next?.let { label[it] } ?: "Abrir o Palavreiro")
                v.setOnClickPendingIntent(R.id.w_play, WidgetKit.open(context, next ?: "HOME", 16))
            }
            return v
        }
    }
}
