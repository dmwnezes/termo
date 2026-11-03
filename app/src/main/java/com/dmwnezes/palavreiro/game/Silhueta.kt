package com.dmwnezes.palavreiro.game

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Um país (kind "p") ou estado ("e"): centro e contorno SVG em caixa 100×100 ("Mx yLx y…Z"). */
data class SilhuetaItem(val name: String, val kind: String, val lat: Double, val lon: Double, val path: String) {
    /** Partes do contorno: cada uma é uma lista de pontos (x, y). */
    val rings: List<List<Pair<Float, Float>>> by lazy {
        path.split('Z').filter { it.isNotBlank() }.map { r ->
            r.removePrefix("M").split('L').map { p -> p.split(' ').let { it[0].toFloat() to it[1].toFloat() } }
        }
    }
}

data class SilhuetaHint(val km: Int, val dir: Int, val pct: Int)

/** Mesmas regras do site (js/silhueta.js) e o mesmo arquivo (shared/silhueta.txt). */
object Silhueta {
    const val TRIES = 6
    val ARROWS = listOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")

    fun parse(text: String): List<SilhuetaItem> = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { l ->
        val f = l.split('|')
        if (f.size < 5) null else SilhuetaItem(f[0], f[1], f[2].toDouble(), f[3].toDouble(), f[4])
    }

    fun dailyIndex(day: Long, n: Int): Int = Math.floorMod(day, n.toLong()).toInt()

    /** Distância em km, direção (0 a 7, de quem chutou até a resposta) e proximidade em %. */
    fun hint(from: SilhuetaItem, to: SilhuetaItem): SilhuetaHint {
        val dLat = Math.toRadians(to.lat - from.lat)
        val dLon = Math.toRadians(to.lon - from.lon)
        val a = Math.toRadians(from.lat)
        val b = Math.toRadians(to.lat)
        val h = sin(dLat / 2).pow(2) + cos(a) * cos(b) * sin(dLon / 2).pow(2)
        val km = Math.round(2 * 6371 * asin(sqrt(h))).toInt()
        val deg = Math.toDegrees(atan2(sin(dLon) * cos(b), cos(a) * sin(b) - sin(a) * cos(b) * cos(dLon)))
        val dir = Math.round(((deg + 360) % 360) / 45).toInt() % 8
        return SilhuetaHint(km, dir, maxOf(0, Math.round(100 - km / 20015.0 * 100).toInt()))
    }

    private fun norm(s: String) = Words.normalize(s).filter { it in 'A'..'Z' || it == ' ' }

    /** Até 6 nomes que contêm o texto, os que começam com ele primeiro. */
    fun matches(options: List<SilhuetaItem>, used: Collection<String>, text: String): List<SilhuetaItem> {
        val v = norm(text)
        if (v.isEmpty()) return emptyList()
        return options.filter { it.name !in used && v in norm(it.name) }.sortedWith(compareBy({ norm(it.name).indexOf(v) }, { it.name })).take(6)
    }
}
