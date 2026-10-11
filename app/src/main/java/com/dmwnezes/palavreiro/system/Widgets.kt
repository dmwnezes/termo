package com.dmwnezes.palavreiro.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import com.dmwnezes.palavreiro.R
import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Definition
import com.dmwnezes.palavreiro.game.Flash
import com.dmwnezes.palavreiro.game.FlashState
import com.dmwnezes.palavreiro.game.QuizData
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Dados do widget Sequência. [week]: resultado do Termo nos últimos 7 dias (mais antigo primeiro): "w", "l" ou null. */
data class StreakData(val streak: Int, val best: Int, val week: List<Pair<LocalDate, String?>>) {
    companion object {
        fun read(store: Store, today: LocalDate = LocalDate.now()): StreakData {
            val results = store.termoResults()
            return StreakData(
                DailyStatus.read(store, today).streak,
                store.stats(Mode.DIARIO).maxStreak,
                (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to results[it.toString()] },
            )
        }
    }
}

/** Widget "Sequência": chama, dias seguidos no Termo e o recorde. */
class StreakWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ids.forEach { update(context, manager, it) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) = update(context, manager, id)

    companion object {
        val SPEC = WidgetKit.Spec(mediumW = 180, mediumH = 100, largeW = 220, largeH = 240)
        private val WEEKDAY = listOf("S", "T", "Q", "Q", "S", "S", "D") // segunda = 1

        fun refresh(context: Context) {
            val m = AppWidgetManager.getInstance(context) ?: return
            WidgetKit.ids(context, StreakWidget::class.java).forEach { update(context, m, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val d = StreakData.read(Store(context))
            manager.updateAppWidget(id, WidgetKit.views(context, manager, id, SPEC) { views(context, it, d) })
        }

        fun views(context: Context, size: WidgetKit.Size, d: StreakData): RemoteViews {
            val v = RemoteViews(
                context.packageName,
                when (size) {
                    WidgetKit.Size.SMALL -> R.layout.widget_streak_small
                    WidgetKit.Size.MEDIUM -> R.layout.widget_streak
                    WidgetKit.Size.LARGE -> R.layout.widget_streak_large
                },
            )
            val one = d.streak == 1
            v.setTextViewText(R.id.w_days, "${d.streak}")
            v.setTextViewText(R.id.w_unit, if (one) "dia" else "dias")
            v.setTextViewText(
                R.id.w_label,
                when {
                    d.streak == 0 -> if (size == WidgetKit.Size.SMALL) "jogue hoje!" else "jogue o Termo hoje!"
                    size == WidgetKit.Size.SMALL -> if (one) "dia seguido" else "dias seguidos"
                    else -> "sequência no Termo"
                },
            )
            v.setTextViewText(R.id.w_best, "recorde ${d.best}")
            v.setOnClickPendingIntent(R.id.w_root, WidgetKit.open(context, "TERMO", 20))
            if (size == WidgetKit.Size.LARGE) {
                val ids = listOf(R.id.w_day1, R.id.w_day2, R.id.w_day3, R.id.w_day4, R.id.w_day5, R.id.w_day6, R.id.w_day7)
                d.week.forEachIndexed { i, (date, r) ->
                    v.setTextViewText(ids[i], WEEKDAY[date.dayOfWeek.value - 1])
                    v.setInt(ids[i], "setBackgroundResource", when (r) { "w" -> R.drawable.wday_win; "l" -> R.drawable.wday_loss; else -> R.drawable.wday_none })
                    v.setTextColor(ids[i], if (r == null) 0xFFA9A2D0.toInt() else 0xFF1A1438.toInt())
                }
                v.setOnClickPendingIntent(R.id.w_play, WidgetKit.open(context, "TERMO", 21))
            }
            return v
        }
    }
}

/** Dados do widget Próxima palavra. */
data class NextWordData(val msLeft: Long, val minutesElapsed: Int, val termoDone: Boolean)

/** Widget "Próxima palavra": contagem regressiva até a meia-noite e botão Jogar. */
class NextWordWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        scheduleMidnight(context)
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) = update(context, manager, id)
    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(midnightIntent(context))
    }

    companion object {
        val SPEC = WidgetKit.Spec(mediumW = 180, mediumH = 115, largeW = 220, largeH = 240)

        fun refresh(context: Context) {
            val m = AppWidgetManager.getInstance(context) ?: return
            val ids = WidgetKit.ids(context, NextWordWidget::class.java)
            ids.forEach { update(context, m, it) }
            if (ids.isNotEmpty()) scheduleMidnight(context)
        }

        fun read(store: Store, now: LocalDateTime = LocalDateTime.now()): NextWordData {
            val zone = ZoneId.systemDefault()
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val left = midnight - now.atZone(zone).toInstant().toEpochMilli()
            return NextWordData(left, NextWordLive.minutesElapsed(now), DailyStatus.read(store, now.toLocalDate()).termo)
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val d = read(Store(context))
            manager.updateAppWidget(id, WidgetKit.views(context, manager, id, SPEC) { views(context, it, d) })
        }

        fun views(context: Context, size: WidgetKit.Size, d: NextWordData, elapsedRealtime: Long = SystemClock.elapsedRealtime()): RemoteViews {
            val v = RemoteViews(
                context.packageName,
                when (size) {
                    WidgetKit.Size.SMALL -> R.layout.widget_next_small
                    WidgetKit.Size.MEDIUM -> R.layout.widget_next
                    WidgetKit.Size.LARGE -> R.layout.widget_next_large
                },
            )
            // O relógio anda sozinho na tela inicial (contagem regressiva do Chronometer).
            v.setChronometer(R.id.w_timer, elapsedRealtime + d.msLeft, null, true)
            v.setChronometerCountDown(R.id.w_timer, true)
            v.setTextViewText(
                R.id.w_status,
                when {
                    size == WidgetKit.Size.SMALL -> if (d.termoDone) "✓ Termo feito" else "Termo: falta"
                    d.termoDone -> "Termo de hoje feito ✓"
                    else -> "Você ainda não jogou o Termo de hoje"
                },
            )
            v.setOnClickPendingIntent(R.id.w_root, WidgetKit.open(context, "HOME", 30))
            v.setOnClickPendingIntent(R.id.w_play, WidgetKit.open(context, "TERMO", 31))
            v.setTextViewText(R.id.w_play, if (d.termoDone) "Jogar mais" else "Jogar")
            if (d.termoDone) v.setOnClickPendingIntent(R.id.w_play, WidgetKit.open(context, "INFINITO", 32))
            if (size == WidgetKit.Size.LARGE) {
                v.setProgressBar(R.id.w_bar, NextWordLive.DAY_MINUTES, d.minutesElapsed, false)
                v.setTextViewText(R.id.w_day, "${d.minutesElapsed * 100 / NextWordLive.DAY_MINUTES}% do dia já passou")
            }
            return v
        }

        private fun midnightIntent(context: Context): PendingIntent {
            val ids = WidgetKit.ids(context, NextWordWidget::class.java)
            return PendingIntent.getBroadcast(
                context, 4210,
                Intent(context, NextWordWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /** Na virada do dia a contagem recomeça (sem acordar o celular). */
        private fun scheduleMidnight(context: Context) {
            val alarm = context.getSystemService(AlarmManager::class.java) ?: return
            val at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 2000
            runCatching { alarm.set(AlarmManager.RTC, at, midnightIntent(context)) }
        }
    }
}

/**
 * Widget jogável "Palavra Relâmpago": uma definição e 3 palavras. Tocar responde (verde/vermelho),
 * a sequência aparece no widget e "Próxima" sorteia outra. Cada widget tem seu próprio jogo.
 */
class FlashWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ids.forEach { update(context, manager, it) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) = update(context, manager, id)

    override fun onDeleted(context: Context, ids: IntArray) {
        val e = prefs(context).edit()
        ids.forEach { e.remove(key(it)) }
        e.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        when (intent.action) {
            ACTION_ANSWER -> if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val defs = definitions(context)
                save(context, id, Flash.answer(state(context, id, defs), intent.getIntExtra(EXTRA_OPTION, -1)))
                AppWidgetManager.getInstance(context)?.let { update(context, it, id) }
            }
            ACTION_NEXT -> if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val defs = definitions(context)
                save(context, id, Flash.next(defs, state(context, id, defs)))
                AppWidgetManager.getInstance(context)?.let { update(context, it, id) }
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.dmwnezes.palavreiro.widget.RELAMPAGO_RESPONDER"
        const val ACTION_NEXT = "com.dmwnezes.palavreiro.widget.RELAMPAGO_PROXIMA"
        const val EXTRA_OPTION = "opcao"
        val SPEC = WidgetKit.Spec(mediumW = 250, mediumH = 300, largeW = 300, largeH = 400)

        private var cache: List<Definition>? = null
        fun definitions(context: Context): List<Definition> = cache ?: QuizData.definitions(
            context.assets.open("definicoes.txt").bufferedReader().use { it.readText() }
        ).also { cache = it }

        private fun prefs(context: Context) = context.getSharedPreferences("widget_relampago", Context.MODE_PRIVATE)
        private fun key(id: Int) = "w_$id"

        /** Estado salvo do widget [id] (ou uma rodada nova). */
        fun state(context: Context, id: Int, defs: List<Definition> = definitions(context)): FlashState =
            Flash.decode(prefs(context).getString(key(id), null), defs.size)
                ?: Flash.next(defs, null).also { save(context, id, it) }

        fun save(context: Context, id: Int, s: FlashState) = prefs(context).edit().putString(key(id), Flash.encode(s)).apply()

        fun refresh(context: Context) {
            val m = AppWidgetManager.getInstance(context) ?: return
            WidgetKit.ids(context, FlashWidget::class.java).forEach { update(context, m, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val defs = definitions(context)
            val s = state(context, id, defs)
            manager.updateAppWidget(id, WidgetKit.views(context, manager, id, SPEC) { views(context, it, id, s, defs) })
        }

        private fun action(context: Context, id: Int, action: String, option: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, id * 8 + option + 1,
                Intent(context, FlashWidget::class.java).setAction(action)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(EXTRA_OPTION, option),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** Tamanho da letra do botão para a palavra caber numa linha (os 3 botões lado a lado no compacto). */
        fun optionTextSize(word: String, size: WidgetKit.Size): Float {
            val (room, max) = when (size) {
                WidgetKit.Size.SMALL -> 60f to 14f
                WidgetKit.Size.MEDIUM -> 200f to 16f
                WidgetKit.Size.LARGE -> 240f to 19f
            }
            // Palavras com hífen quebram em duas linhas ("ESCONDE-/ESCONDE"): vale o pedaço maior.
            val longest = if ('-' in word) word.split('-').maxOf { it.length } + 1 else word.length
            return (room / (0.76f * longest.coerceAtLeast(1))).coerceIn(9f, max)
        }

        fun views(context: Context, size: WidgetKit.Size, id: Int, s: FlashState, defs: List<Definition>): RemoteViews {
            val v = RemoteViews(
                context.packageName,
                when (size) {
                    WidgetKit.Size.SMALL -> R.layout.widget_flash_small
                    WidgetKit.Size.MEDIUM -> R.layout.widget_flash
                    WidgetKit.Size.LARGE -> R.layout.widget_flash_large
                },
            )
            val item = defs[s.def]
            v.setTextViewText(R.id.w_def, item.text)
            v.setTextViewText(R.id.w_streak, "🔥 ${s.streak}")
            v.setTextViewText(R.id.w_best, "recorde ${s.best} · ${s.right} ${if (s.right == 1) "acerto" else "acertos"}")
            val ids = listOf(R.id.w_opt0, R.id.w_opt1, R.id.w_opt2)
            // Mesmo tamanho de letra nos 3 botões (o da palavra mais comprida).
            val textSize = s.options.minOf { optionTextSize(defs[it].word, size) }
            s.options.forEachIndexed { i, d ->
                v.setTextViewText(ids[i], defs[d].word)
                v.setTextViewTextSize(ids[i], android.util.TypedValue.COMPLEX_UNIT_SP, textSize)
                // Só palavras com hífen podem quebrar em duas linhas (no hífen).
                v.setInt(ids[i], "setMaxLines", if ('-' in defs[d].word) 2 else 1)
                val (bg, color) = when {
                    !s.answered -> R.drawable.wbtn to 0xFFF4F1FF.toInt()
                    i == s.correctIndex -> R.drawable.wbtn_right to 0xFF1A1438.toInt()
                    i == s.chosen -> R.drawable.wbtn_wrong to 0xFF1A1438.toInt()
                    else -> R.drawable.wbtn_dim to 0xFF6E679A.toInt()
                }
                v.setInt(ids[i], "setBackgroundResource", bg)
                v.setTextColor(ids[i], color)
                v.setOnClickPendingIntent(ids[i], action(context, id, ACTION_ANSWER, i))
            }
            v.setTextViewText(
                R.id.w_feedback,
                when {
                    !s.answered -> "Qual palavra é essa?"
                    s.wasRight -> if (s.streak > 1) "Certo! ${s.streak} seguidas ⚡" else "Certo! ⚡"
                    else -> "Era ${defs[s.def].word}"
                },
            )
            v.setTextColor(R.id.w_feedback, when { !s.answered -> 0xFFA9A2D0.toInt(); s.wasRight -> 0xFF5FB873.toInt(); else -> 0xFFE5737A.toInt() })
            v.setOnClickPendingIntent(R.id.w_next, action(context, id, ACTION_NEXT, 3))
            // Depois de responder, tocar na definição também passa para a próxima.
            if (s.answered) v.setOnClickPendingIntent(R.id.w_def, action(context, id, ACTION_NEXT, 4))
            v.setTextViewText(R.id.w_next, "Próxima ›")
            return v
        }
    }
}
