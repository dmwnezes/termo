package com.dmwnezes.palavreiro.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * PRNG MINSTD (Lehmer) dos jogos diários da rodada 12, igual ao do site:
 * x = semente mod 2147483647 (0 vira 1); cada [next] faz x = x * 48271 mod 2147483647 e devolve o novo x.
 */
class Minstd(seed: Long) {
    private var x = Math.floorMod(seed, M).let { if (it == 0L) 1L else it }
    fun next(): Long { x = x * 48271 % M; return x }

    companion object {
        const val M = 2147483647L
        /** Semente do dia: (dia + 1) * 7919. */
        fun ofDay(day: Long) = Minstd((day + 1) * 7919)
    }
}

/** Embaralha (Fisher–Yates de trás para frente: j = next mod (i + 1)), igual ao site. */
fun <T> List<T>.shuffledBy(r: Minstd): List<T> {
    val a = toMutableList()
    for (i in a.size - 1 downTo 1) {
        val j = (r.next() % (i + 1)).toInt()
        val t = a[i]; a[i] = a[j]; a[j] = t
    }
    return a
}

/** Cor 0xRRGGBB. */
object MatizColor {
    fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
    fun rgb(r: Int, g: Int, b: Int) = (r shl 16) or (g shl 8) or b

    /** sRGB (D65) → Lab (branco D65: 0.95047, 1, 1.08883). */
    fun lab(c: Int): DoubleArray {
        fun lin(v: Int): Double { val s = v / 255.0; return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        val r = lin(c shr 16 and 255); val g = lin(c shr 8 and 255); val b = lin(c and 255)
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
        fun f(t: Double) = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116
        return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    /** ΔE CIE76: distância no espaço Lab. */
    fun deltaE(a: Int, b: Int): Double {
        val p = lab(a); val q = lab(b)
        return sqrt((p[0] - q[0]).pow(2) + (p[1] - q[1]).pow(2) + (p[2] - q[2]).pow(2))
    }

    /** Nota 0–100 da rodada. */
    fun score(target: Int, guess: Int): Int = (100 - deltaE(target, guess) * 1.5).coerceAtLeast(0.0).roundToInt()

    /** Quadradinho do compartilhamento. */
    fun emoji(score: Int) = when { score >= 90 -> "🟩"; score >= 70 -> "🟨"; else -> "⬛" }

    /** As 5 cores do dia: 3 números seguidos do MINSTD por cor (R, G, B = x mod 256). */
    fun daily(day: Long): List<Int> {
        val r = Minstd.ofDay(day)
        return List(MatizGame.ROUNDS) { rgb((r.next() % 256).toInt(), (r.next() % 256).toInt(), (r.next() % 256).toInt()) }
    }

    fun random(rnd: Random): List<Int> = List(MatizGame.ROUNDS) { rnd.nextInt(0x1000000) }
}

/** Matiz: memorize cada uma das 5 cores e recrie com R, G e B. */
class MatizGame(val colors: List<Int>) {
    val guesses = mutableStateListOf<Int>()
    val scores = mutableStateListOf<Int>()
    /** Muda a cada palpite (para a tela reagir). */
    var tick by mutableIntStateOf(0)
        private set

    val round: Int get() = guesses.size
    val over: Boolean get() = guesses.size >= colors.size
    val total: Int get() = scores.sum()
    val target: Int? get() = colors.getOrNull(round)

    fun guess(c: Int): Int {
        val t = target ?: return 0
        val s = MatizColor.score(t, c)
        guesses += c; scores += s; tick++
        return s
    }

    fun share(daily: Boolean) = "Matiz${if (daily) "" else " Infinito"} $total/${colors.size * 100} 🎨\n" + scores.joinToString("") { MatizColor.emoji(it) }

    companion object { const val ROUNDS = 5 }
}
