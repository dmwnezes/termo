package com.dmwnezes.palavreiro.game

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

data class MmItem(val name: String, val value: Long)
data class MmCat(val title: String, val unit: String, val year: Boolean, val items: List<MmItem>) {
    private fun number(v: Long) = if (year) v.toString() else NumberFormat.getIntegerInstance(Locale("pt", "BR")).format(v)
    fun label(v: Long) = if (year || unit.isEmpty()) number(v) else "${number(v)} $unit"
}
data class MmRound(val cat: Int, val a: Int, val b: Int)

/** Mesmas regras do site (js/maismenos.js) e o mesmo arquivo (shared/maismenos.txt). */
object MaisMenos {
    const val ROUNDS = 10

    fun parse(text: String): List<MmCat> {
        val out = ArrayList<MmCat>()
        var title = ""; var unit = ""; var year = false
        var items = ArrayList<MmItem>()
        fun flush() { if (title.isNotEmpty()) out += MmCat(title, unit, year, items) }
        text.lines().map { it.trim() }.forEach { l ->
            if (l.startsWith("## ")) {
                flush()
                val f = l.removePrefix("## ").split('|')
                title = f[0]; unit = f.getOrElse(1) { "" }; year = f.getOrNull(2) == "ano"; items = ArrayList()
            } else if (l.isNotEmpty() && !l.startsWith("#")) {
                val f = l.split('|')
                if (f.size >= 2) f[1].toLongOrNull()?.let { items += MmItem(f[0], it) }
            }
        }
        flush()
        return out
    }

    /** Os valores são aproximados: só vale rodada com diferença clara. */
    private fun fair(c: MmCat, a: MmItem, b: MmItem) =
        if (c.year) abs(a.value - b.value) >= 3 else abs(a.value - b.value) >= 0.1 * maxOf(abs(a.value), abs(b.value))

    /** Próxima rodada usando [next]: tema fora de [recent] e dois itens com diferença clara. */
    fun round(cats: List<MmCat>, next: () -> Long, recent: List<Int>): MmRound {
        repeat(100_000) {
            val ci = (next() % cats.size).toInt()
            if (ci in recent) return@repeat
            val c = cats[ci]
            val a = (next() % c.items.size).toInt()
            val b = (next() % c.items.size).toInt()
            if (a != b && fair(c, c.items[a], c.items[b])) return MmRound(ci, a, b)
        }
        error("sem rodada")
    }

    fun daily(cats: List<MmCat>, day: Long): List<MmRound> {
        val r = Minstd.ofDay(day)
        val out = ArrayList<MmRound>()
        while (out.size < ROUNDS) out += round(cats, r::next, out.map { it.cat })
        return out
    }
}
