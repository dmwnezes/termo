package com.dmwnezes.palavreiro.game

import com.dmwnezes.palavreiro.data.Mode
import org.json.JSONObject
import kotlin.random.Random

/**
 * Partida com amigo (multiplayer ao vivo), igual ao site: as mensagens passam por um tópico do ntfy.sh.
 * Só trafegam o nome, as CORES de cada tentativa e o resultado — nunca as letras.
 */
object Multi {
    const val SITE = "https://dmwnezes.github.io/termo/"
    const val TOPIC_PREFIX = "palavreiro-mp-"

    /** Modos da partida: letra do link → modo do jogo. */
    val MODES = linkedMapOf('t' to Mode.DIARIO, 'd' to Mode.DUETO, 'q' to Mode.QUARTETO)
    fun modeName(code: Char): String = when (code) { 'd' -> "Dueto"; 'q' -> "Quarteto"; else -> "Termo" }
    fun boards(code: Char): Int = MODES[code]?.boards ?: 1

    /** Dados do link ?mp=ROOM-MODO-SEED. */
    data class Room(val room: String, val mode: Char, val seed: Long) {
        val topic: String get() = TOPIC_PREFIX + room
        val code: String get() = "$room-$mode-${seed.toString(36)}"
        val link: String get() = "$SITE?mp=$code"
        fun withSeed(s: Long) = copy(seed = s)
    }

    fun parse(code: String?): Room? {
        val p = code?.trim()?.split('-') ?: return null
        if (p.size != 3) return null
        val room = p[0]; val mode = p[1].singleOrNull(); val seed = p[2].toLongOrNull(36)
        if (!room.matches(Regex("[a-z0-9]{6,32}")) || mode == null || mode !in MODES || seed == null || seed !in 0..2147483646L) return null
        return Room(room, mode, seed)
    }

    /** Aceita o link inteiro (…?mp=CODIGO…), só o código, ou um texto com o link no meio (mensagem do WhatsApp). */
    fun fromPasted(text: String?): Room? {
        val t = text?.trim().orEmpty()
        Regex("[?&]mp=([A-Za-z0-9-]+)").find(t)?.let { return parse(it.groupValues[1].lowercase()) }
        Regex("\\b([a-z0-9]{12}-[tdq]-[0-9a-z]{1,7})\\b").find(t.lowercase())?.let { return parse(it.groupValues[1]) }
        return null
    }

    private const val ALPHA = "abcdefghijklmnopqrstuvwxyz0123456789"
    fun randomId(n: Int, rnd: Random = Random.Default) = buildString { repeat(n) { append(ALPHA[rnd.nextInt(ALPHA.length)]) } }
    fun newSeed(rnd: Random = Random.Default): Long = rnd.nextLong(0, 2147483647L)
    fun newRoom(mode: Char, rnd: Random = Random.Default) = Room(randomId(12, rnd), mode, newSeed(rnd))

    /**
     * Palavras da partida: mesma fórmula do site, sobre a lista completa de respostas (a do Infinito).
     * idx = (SEED*7919 + 104729 + k*577) mod N, pulando repetidas.
     */
    fun words(answers: List<String>, seed: Long, count: Int): List<String> {
        val n = answers.size.toLong()
        val out = LinkedHashSet<String>()
        var k = 0L
        while (out.size < count) {
            val idx = ((seed % n) * 7919 % n + 104729 + k * 577) % n
            out += answers[idx.toInt()]
            k++
        }
        return out.toList()
    }

    /** Marcas de uma tentativa para o adversário: "cpaac|aappc" (tabuleiro já resolvido antes = ""). */
    fun rowMarks(game: TermoGame, row: Int): String = game.answers.indices.joinToString("|") { b ->
        val at = game.solvedAt(b)
        if (at in 0 until row) "" else Rules.evaluate(game.rows[row], game.answers[b]).joinToString("") {
            when (it) { Mark.CORRECT -> "c"; Mark.PRESENT -> "p"; Mark.ABSENT -> "a" }
        }
    }

    // ---------- mensagens ----------
    sealed interface Msg {
        data class Hello(val id: String, val name: String, val host: Boolean) : Msg
        data class Start(val at: Long, val guest: String) : Msg
        data class Row(val id: String, val row: Int, val marks: String) : Msg
        data class End(val id: String, val won: Boolean, val tries: Int, val ms: Long) : Msg
        /** [newSeries] = começa uma série nova (placar volta a 0 × 0). */
        data class Again(val seed: Long, val at: Long, val newSeries: Boolean = false) : Msg
        data class Bye(val id: String) : Msg
    }

    fun encode(m: Msg): String = when (m) {
        is Msg.Hello -> JSONObject().put("t", "hello").put("id", m.id).put("n", m.name).put("host", m.host)
        is Msg.Start -> JSONObject().put("t", "start").put("at", m.at).put("g", m.guest)
        is Msg.Row -> JSONObject().put("t", "row").put("id", m.id).put("r", m.row).put("m", m.marks)
        is Msg.End -> JSONObject().put("t", "end").put("id", m.id).put("won", m.won).put("tries", m.tries).put("ms", m.ms)
        is Msg.Again -> JSONObject().put("t", "again").put("seed", m.seed.toString(36)).put("at", m.at).apply { if (m.newSeries) put("s", 1) }
        is Msg.Bye -> JSONObject().put("t", "bye").put("id", m.id)
    }.toString()

    fun decode(text: String): Msg? = runCatching {
        val o = JSONObject(text)
        when (o.optString("t")) {
            "hello" -> Msg.Hello(o.getString("id"), o.optString("n").take(16), o.optBoolean("host"))
            "start" -> Msg.Start(o.getLong("at"), o.optString("g"))
            "row" -> Msg.Row(o.getString("id"), o.getInt("r"), o.getString("m"))
            "end" -> Msg.End(o.getString("id"), o.getBoolean("won"), o.optInt("tries"), o.optLong("ms"))
            "again" -> Msg.Again(o.getString("seed").toLong(36), o.optLong("at"), o.optInt("s") == 1)
            "bye" -> Msg.Bye(o.getString("id"))
            else -> null
        }
    }.getOrNull()

    /** Nome válido: 2 a 16 caracteres (sem espaços nas pontas). */
    fun validName(n: String): Boolean = n.trim().length in 2..16

    /**
     * Resultado da partida a partir dos fins na ordem em que chegaram no tópico.
     * Devolve null enquanto não há resultado; "me", "opp" ou "draw".
     */
    fun outcome(ends: List<Msg.End>, me: String, opp: String?, oppLeft: Boolean): String? {
        ends.firstOrNull { it.won }?.let { return if (it.id == me) "me" else "opp" }
        val mine = ends.any { it.id == me }
        val theirs = opp != null && ends.any { it.id == opp }
        return when {
            mine && theirs -> "draw"            // os dois erraram
            mine && oppLeft -> "draw"           // eu errei e o outro saiu sem terminar
            else -> null
        }
    }

    // ---------- melhor de 3 ----------
    /** Vitórias para levar a série (melhor de 3; empates não contam). */
    const val SERIES_WINS = 2

    /** Placar da série: soma o resultado de cada rodada; quem chegar a 2 leva o troféu. */
    data class Series(val me: Int = 0, val opp: Int = 0) {
        val over: Boolean get() = me >= SERIES_WINS || opp >= SERIES_WINS
        /** "me", "opp" ou null enquanto a série continua. */
        val winner: String? get() = when { me >= SERIES_WINS -> "me"; opp >= SERIES_WINS -> "opp"; else -> null }
        val score: String get() = "$me × $opp"
        fun plus(outcome: String?): Series = if (over) this else when (outcome) { "me" -> copy(me = me + 1); "opp" -> copy(opp = opp + 1); else -> this }
    }
}
