package com.dmwnezes.palavreiro.system

import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.data.Store
import java.time.LocalDate

/** Resumo do dia usado pelo widget e pelo lembrete. */
data class DailyStatus(
    val termo: Boolean,
    val dueto: Boolean,
    val quarteto: Boolean,
    val conexoes: Boolean,
    val caca: Boolean,
    val streak: Int,
) {
    val doneCount: Int get() = listOf(termo, dueto, quarteto, conexoes, caca).count { it }

    companion object {
        fun read(store: Store, today: LocalDate = LocalDate.now()): DailyStatus {
            val d = today.toString()
            val stats = store.stats(Mode.DIARIO)
            // A sequência só vale se o último acerto foi hoje ou ontem.
            val day = com.dmwnezes.palavreiro.game.Words.dayIndex(today)
            val streak = if (stats.lastWinDay >= day - 1) stats.streak else 0
            return DailyStatus(
                termo = store.text("daily_done_${Mode.DIARIO.key}") == d,
                dueto = store.text("daily_done_${Mode.DUETO.key}") == d,
                quarteto = store.text("daily_done_${Mode.QUARTETO.key}") == d,
                conexoes = store.text("conn_done") == d,
                caca = store.text("ws_done") == d,
                streak = streak,
            )
        }
    }
}
