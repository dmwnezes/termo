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

    /** Modos da partida no estilo Termo: letra do link → modo do jogo. */
    val MODES = linkedMapOf('t' to Mode.DIARIO, 'd' to Mode.DUETO, 'q' to Mode.QUARTETO)
    const val ANAGRAM = 'a'
    const val HUNT = 'c'
    const val DEFINE = 'p'
    const val TICTAC = 'v'
    const val CHECKERS = 'x'
    const val ADEDONHA = 's'
    const val TRIVIA = 'g'
    const val FORCA = 'f'
    /** Bomba-Relógio: saiu do Palavreiro (link antigo com "b" → aviso). */
    const val REMOVED = 'b'
    /** Todas as letras de modo aceitas no link, na ordem dos cartões da tela de criar. */
    val CODES = listOf('t', 'd', 'q', ANAGRAM, HUNT, DEFINE, FORCA, ADEDONHA, TRIVIA, TICTAC, CHECKERS)
    /** Seções da tela de criar: título → modos. */
    val SECTIONS = listOf(
        "Palavras" to listOf('t', 'd', 'q', ANAGRAM, HUNT, DEFINE, FORCA, ADEDONHA),
        "Conhecimento" to listOf(TRIVIA),
        "Tabuleiro" to listOf(TICTAC, CHECKERS),
    )
    fun modeName(code: Char): String = when (code) {
        'd' -> "Dueto"; 'q' -> "Quarteto"; ANAGRAM -> "Anagrama"; HUNT -> "Caça-Palavras"
        DEFINE -> "Qual é a Palavra?"; TICTAC -> "Jogo da Velha"; CHECKERS -> "Damas"
        ADEDONHA -> "Adedonha"; TRIVIA -> "Trivia"; FORCA -> "Forca"; else -> "Termo"
    }
    /** Nome curto (grade compacta do "Próximo jogo"). */
    fun chipName(code: Char): String = when (code) {
        HUNT -> "Caça"; DEFINE -> "Palavra?"; TICTAC -> "Velha"; else -> modeName(code)
    }
    /** Linha curta de descrição do cartão do jogo. */
    fun blurb(code: Char): String = when (code) {
        'd' -> "Duas palavras ao mesmo tempo"; 'q' -> "Quatro palavras de uma vez"
        ANAGRAM -> "Desembaralhe antes do amigo"; HUNT -> "A mesma grade para os dois"
        DEFINE -> "Adivinhe a palavra pela definição"; FORCA -> "Um escolhe a palavra, o outro adivinha"
        ADEDONHA -> "Uma letra, várias categorias"; TRIVIA -> "Perguntas de 6 temas"
        TICTAC -> "Três em linha, ao vivo"; CHECKERS -> "Regras brasileiras"
        else -> "Uma palavra, quem acertar primeiro"
    }
    /** "um Termo", "uma partida de Damas", "uma Adedonha", "um Qual é a Palavra?". */
    fun withArticle(code: Char): String = when (code) {
        CHECKERS -> "uma partida de Damas"
        ADEDONHA, TRIVIA, FORCA -> "uma " + modeName(code)
        else -> "um " + modeName(code)
    }
    /** Modos que seguem o fluxo do Termo (Termo, Dueto, Quarteto); os outros têm jogo próprio. */
    fun isTermo(code: Char): Boolean = code in MODES
    fun boards(code: Char): Int = MODES[code]?.boards ?: 1

    // ---------- rodadas (melhor de N ou N rodadas) ----------
    /** Modos de pontos (Anagrama, Qual é a Palavra?, Adedonha e Trivia): N rodadas; os outros jogam uma série melhor de N. */
    fun pointsMode(code: Char): Boolean = code == ANAGRAM || code == DEFINE || code == ADEDONHA || code == TRIVIA
    fun roundOptions(code: Char): List<Int> = when {
        code == ADEDONHA -> listOf(3, 5, 7)
        pointsMode(code) -> listOf(5, 10, 15)
        else -> listOf(1, 3, 5)
    }
    fun defaultRounds(code: Char): Int = when {
        code == ADEDONHA -> 5
        pointsMode(code) -> 10
        else -> 3
    }
    /** Valor do "r" válido para o modo; qualquer outro (ou nenhum) vira o padrão. */
    fun rounds(code: Char, r: Int?): Int = r?.takeIf { it in roundOptions(code) } ?: defaultRounds(code)
    fun roundsLabel(code: Char, n: Int): String = when {
        pointsMode(code) -> "$n rodadas"
        n == 1 -> "1 partida"
        else -> "Melhor de $n"
    }
    /** Complemento do convite: " · melhor de 3", " · 15 rodadas" ou nada (1 partida). */
    fun roundsSuffix(code: Char, n: Int): String = when {
        pointsMode(code) -> " · $n rodadas"
        n == 1 -> ""
        else -> " · melhor de $n"
    }

    /** Dados do link ?mp=ROOM-MODO-SEED. */
    data class Room(val room: String, val mode: Char, val seed: Long) {
        val topic: String get() = TOPIC_PREFIX + room
        val code: String get() = "$room-$mode-${seed.toString(36)}"
        val link: String get() = "$SITE?mp=$code"
        fun withSeed(s: Long) = copy(seed = s)
    }

    fun parse(code: String?): Room? {
        val p = code?.trim()?.lowercase()?.split('-') ?: return null
        if (p.size != 3) return null
        val room = p[0]; val mode = p[1].singleOrNull(); val seed = p[2].toLongOrNull(36)
        if (!room.matches(Regex("[a-z0-9]{6,32}")) || mode == null || mode !in CODES || seed == null || seed !in 0..2147483646L) return null
        return Room(room, mode, seed)
    }

    /** Aceita o link inteiro (…?mp=CODIGO…), só o código, ou um texto com o link no meio (mensagem do WhatsApp). */
    fun fromPasted(text: String?): Room? {
        val t = text?.trim().orEmpty()
        Regex("[?&]mp=([A-Za-z0-9-]+)").find(t)?.let { return parse(it.groupValues[1].lowercase()) }
        Regex("\\b([a-z0-9]{12}-[tdqacpvxsgf]-[0-9a-z]{1,7})\\b").find(t.lowercase())?.let { return parse(it.groupValues[1]) }
        return null
    }

    /** Link (ou código) de um jogo que saiu do Palavreiro (Bomba-Relógio, "b"). */
    fun removedGame(text: String?): Boolean {
        val t = text?.trim()?.lowercase().orEmpty()
        val code = Regex("[?&]mp=([a-z0-9-]+)").find(t)?.groupValues?.get(1)
            ?: Regex("\\b([a-z0-9]{6,32}-b-[0-9a-z]{1,7})\\b").find(t)?.groupValues?.get(1) ?: return false
        val p = code.split('-')
        return p.size == 3 && p[1] == "b" && p[0].matches(Regex("[a-z0-9]{6,32}")) && p[2].toLongOrNull(36) != null
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
        /** [rounds] = "r" do anfitrião (1/3/5 ou 5/10/15); null = padrão do modo. */
        data class Hello(val id: String, val name: String, val host: Boolean, val rounds: Int? = null) : Msg
        data class Start(val at: Long, val guest: String) : Msg
        data class Row(val id: String, val row: Int, val marks: String) : Msg
        data class End(val id: String, val won: Boolean, val tries: Int, val ms: Long) : Msg
        /**
         * [newSeries] = começa uma série nova (placar volta a 0 × 0).
         * [mode] = modo da próxima partida ("m"); null = mensagem antiga, mantém o modo atual.
         * [rounds] = "r" quando o anfitrião muda o modo/as rodadas; null = mantém (ou padrão do modo novo).
         */
        data class Again(val seed: Long, val at: Long, val newSeries: Boolean = false, val mode: Char? = null, val rounds: Int? = null) : Msg
        data class Bye(val id: String) : Msg
        /** Anagrama e Qual é a Palavra?: [id] acertou a rodada [round]. */
        data class Solve(val id: String, val round: Int, val ms: Long) : Msg
        /** Anagrama e Qual é a Palavra?: ninguém acertou a rodada [round] a tempo. */
        data class Skip(val round: Int) : Msg
        /** Reação rápida: [emoji] (um de [REACTIONS]) mandado por [id] na hora [at] (relógio de quem mandou). */
        data class React(val id: String, val emoji: String, val at: Long) : Msg
        /** Caça-Palavras: grade da rodada [n] (start/again da sala), mandada pelo anfitrião. */
        data class Grid(val n: Int, val theme: String, val rows: List<String>, val words: List<String>) : Msg
        /** Caça-Palavras: [id] achou [word] (maiúsculas sem acento) na rodada [n]. */
        data class Found(val id: String, val n: Int, val word: String) : Msg
        /** Caça-Palavras: acabou o tempo da rodada [n]. */
        data class TimeUp(val n: Int) : Msg
        /** Jogo da Velha: [id] marcou a casa [cell] (0..8) na jogada [n]. */
        data class Cell(val id: String, val n: Int, val cell: Int) : Msg
        /** Damas: [id] fez a jogada [n] pelo caminho [path] (origem e cada casa onde a peça para). */
        data class Move(val id: String, val n: Int, val path: List<Int>) : Msg
        /** Damas: [id] desistiu da rodada. */
        data class Resign(val id: String) : Msg

        // ---------- Rodada 11 ----------
        /** Convidado: sugere o modo [mode] para o próximo jogo. */
        data class Suggest(val id: String, val mode: Char) : Msg
        /** Adedonha: o anfitrião sorteou a letra [letter] da rodada [k] (partida [n]) com as categorias [cats]. */
        data class SRound(val n: Int, val k: Int, val letter: Char, val cats: List<String>) : Msg
        /** Adedonha: [id] deu STOP ("tempo" = acabou o tempo). */
        data class Stop(val id: String, val n: Int, val k: Int) : Msg
        /** Adedonha: respostas de [id] na ordem das categorias. */
        data class Ans(val id: String, val n: Int, val k: Int, val answers: List<String>) : Msg
        /** Adedonha: votos de [id] (1 = vale, 0 = não vale) sobre as respostas do AMIGO. */
        data class Vote(val id: String, val n: Int, val k: Int, val votes: List<Int>) : Msg
        /** Trivia: [id] escolheu a opção [option] (0..3, -1 = não respondeu) da pergunta [round] em [ms]. */
        data class Pick(val id: String, val round: Int, val option: Int, val ms: Long) : Msg
        /** Forca: quem escolheu ([id]) mandou o tema e a máscara (nunca a palavra). */
        data class Secret(val id: String, val n: Int, val theme: String, val mask: String) : Msg
        /** Forca: quem adivinha tocou a letra [letter] (A–Z). */
        data class Guess(val id: String, val n: Int, val letter: Char) : Msg
        /** Forca: resposta automática de quem escolheu: posições da letra na máscara (vazio = erro). */
        data class Reveal(val id: String, val n: Int, val letter: Char, val pos: List<Int>) : Msg
        /** Forca: chute da palavra inteira. */
        data class Try(val id: String, val n: Int, val word: String) : Msg
        /** Forca: resultado do chute. */
        data class TryRes(val n: Int, val ok: Boolean) : Msg
        /** Forca: a palavra original, no fim da rodada. */
        data class Word(val n: Int, val word: String) : Msg
    }

    // ---------- reações rápidas ----------
    val REACTIONS = listOf("😂", "😱", "🔥", "👏", "😈")
    /** Só mostra reações com menos de 10 s (o histórico do tópico traz as velhas ao reconectar). */
    const val REACT_FRESH_MS = 10_000L
    /** No máximo uma reação a cada 1,5 s. */
    const val REACT_GAP_MS = 1_500L

    /** Reação que deve aparecer: do amigo (não minha), com emoji conhecido e fresca. */
    fun showReaction(m: Msg.React, myId: String, now: Long): Boolean =
        m.id != myId && m.emoji in REACTIONS && kotlin.math.abs(now - m.at) < REACT_FRESH_MS

    // ---------- revanche (trocando de modo) ----------
    /** Emoji do modo no cartão dos Stories. */
    fun modeEmoji(code: Char): String = when (code) {
        ANAGRAM -> "🔤"; HUNT -> "🔍"; DEFINE -> "💡"; TICTAC -> "⭕"; CHECKERS -> "♟️"
        ADEDONHA -> "✋"; TRIVIA -> "🧠"; FORCA -> "🎯"; else -> "⚔️"
    }

    /** Modos que contam série (melhor de N). */
    fun hasSeries(code: Char): Boolean = !pointsMode(code)

    /** Texto do botão do anfitrião na folha de fim, conforme o próximo modo escolhido. */
    fun againLabel(current: Char, next: Char, seriesOver: Boolean): String = when {
        next != current -> "Jogar ${modeName(next)}"
        pointsMode(current) -> "Revanche"
        seriesOver -> "Nova série"
        else -> "Próxima rodada"
    }

    /**
     * "again" que o anfitrião manda: sempre com o modo da próxima partida; trocar de modo (ou de rodadas) zera a série.
     * [rounds]/[nextRounds] = rodadas de agora e as escolhidas para a próxima; "r" vai quando muda o modo ou as rodadas.
     */
    fun againFor(current: Char, next: Char, seriesOver: Boolean, seed: Long, at: Long, rounds: Int? = null, nextRounds: Int? = null, force: Boolean = false): Msg.Again {
        // force: sugestão aceita — sempre série nova e com "r" (igual ao site).
        val changed = nextRounds != null && (force || nextRounds != rounds)
        return Msg.Again(
            seed, at, newSeries = next != current || changed || (hasSeries(current) && seriesOver), mode = next,
            rounds = if (next != current || changed) nextRounds else null,
        )
    }

    /** Resultado de um "again" recebido: sala nova, se a série zera e as rodadas da próxima partida. */
    data class AgainResult(val room: Room, val fresh: Boolean, val rounds: Int)

    /** Aplica um "again" recebido: semente nova, modo novo (se veio "m") e rodadas ("r", ou padrão do modo novo). */
    fun applyAgain(room: Room, m: Msg.Again, seriesOver: Boolean, rounds: Int = defaultRounds(room.mode)): AgainResult {
        val mode = m.mode?.takeIf { it in CODES } ?: room.mode
        val nr = when {
            m.rounds != null -> rounds(mode, m.rounds)
            mode != room.mode -> defaultRounds(mode)
            else -> rounds
        }
        return AgainResult(room.copy(mode = mode, seed = m.seed), m.newSeries || seriesOver || mode != room.mode || nr != rounds, nr)
    }

    fun encode(m: Msg): String = when (m) {
        is Msg.Hello -> JSONObject().put("t", "hello").put("id", m.id).put("n", m.name).put("host", m.host).apply { m.rounds?.let { put("r", it) } }
        is Msg.Start -> JSONObject().put("t", "start").put("at", m.at).put("g", m.guest)
        is Msg.Row -> JSONObject().put("t", "row").put("id", m.id).put("r", m.row).put("m", m.marks)
        is Msg.End -> JSONObject().put("t", "end").put("id", m.id).put("won", m.won).put("tries", m.tries).put("ms", m.ms)
        is Msg.Again -> JSONObject().put("t", "again").put("seed", m.seed.toString(36)).put("at", m.at).apply {
            if (m.newSeries) put("s", 1)
            m.mode?.let { put("m", it.toString()) }
            m.rounds?.let { put("r", it) }
        }
        is Msg.Bye -> JSONObject().put("t", "bye").put("id", m.id)
        is Msg.Solve -> JSONObject().put("t", "solve").put("id", m.id).put("r", m.round).put("ms", m.ms)
        is Msg.Skip -> JSONObject().put("t", "skip").put("r", m.round)
        is Msg.React -> JSONObject().put("t", "react").put("id", m.id).put("e", m.emoji).put("at", m.at)
        is Msg.Grid -> JSONObject().put("t", "grid").put("n", m.n).put("th", m.theme).put("g", m.rows.joinToString("|"))
            .put("w", org.json.JSONArray().apply { m.words.forEach { put(it) } })
        is Msg.Found -> JSONObject().put("t", "found").put("id", m.id).put("n", m.n).put("w", m.word)
        is Msg.TimeUp -> JSONObject().put("t", "timeup").put("n", m.n)
        is Msg.Cell -> JSONObject().put("t", "move").put("id", m.id).put("n", m.n).put("c", m.cell)
        is Msg.Move -> JSONObject().put("t", "move").put("id", m.id).put("n", m.n).put("p", org.json.JSONArray().apply { m.path.forEach { put(it) } })
        is Msg.Resign -> JSONObject().put("t", "resign").put("id", m.id)
        is Msg.Suggest -> JSONObject().put("t", "suggest").put("id", m.id).put("m", m.mode.toString())
        is Msg.SRound -> JSONObject().put("t", "sround").put("n", m.n).put("k", m.k).put("l", m.letter.toString()).put("c", jarr(m.cats))
        is Msg.Stop -> JSONObject().put("t", "stop").put("id", m.id).put("n", m.n).put("k", m.k)
        is Msg.Ans -> JSONObject().put("t", "ans").put("id", m.id).put("n", m.n).put("k", m.k).put("a", jarr(m.answers))
        is Msg.Vote -> JSONObject().put("t", "vote").put("id", m.id).put("n", m.n).put("k", m.k).put("v", jarr(m.votes))
        is Msg.Pick -> JSONObject().put("t", "pick").put("id", m.id).put("r", m.round).put("o", m.option).put("ms", m.ms)
        is Msg.Secret -> JSONObject().put("t", "secret").put("id", m.id).put("n", m.n).put("th", m.theme).put("m", m.mask)
        is Msg.Guess -> JSONObject().put("t", "guess").put("id", m.id).put("n", m.n).put("l", m.letter.toString())
        is Msg.Reveal -> JSONObject().put("t", "reveal").put("id", m.id).put("n", m.n).put("l", m.letter.toString()).put("p", jarr(m.pos))
        is Msg.Try -> JSONObject().put("t", "try").put("id", m.id).put("n", m.n).put("w", m.word)
        is Msg.TryRes -> JSONObject().put("t", "tryres").put("n", m.n).put("ok", m.ok)
        is Msg.Word -> JSONObject().put("t", "word").put("n", m.n).put("w", m.word)
    }.toString()

    private fun jarr(items: List<Any>) = org.json.JSONArray().apply { items.forEach { put(it) } }
    private fun strings(o: JSONObject, key: String): List<String> = o.getJSONArray(key).let { a -> (0 until a.length()).map { a.optString(it) } }
    private fun ints(o: JSONObject, key: String): List<Int> = o.getJSONArray(key).let { a -> (0 until a.length()).map { a.getInt(it) } }
    private fun letter(o: JSONObject): Char = o.getString("l").trim().uppercase().single().also { require(it in 'A'..'Z') }

    private fun optRounds(o: JSONObject): Int? = if (o.has("r")) o.optInt("r", -1).takeIf { it > 0 } else null

    fun decode(text: String): Msg? = runCatching {
        val o = JSONObject(text)
        when (o.optString("t")) {
            "hello" -> Msg.Hello(o.getString("id"), o.optString("n").take(16), o.optBoolean("host"), optRounds(o))
            "start" -> Msg.Start(o.getLong("at"), o.optString("g"))
            "row" -> Msg.Row(o.getString("id"), o.getInt("r"), o.getString("m"))
            "end" -> Msg.End(o.getString("id"), o.getBoolean("won"), o.optInt("tries"), o.optLong("ms"))
            "again" -> Msg.Again(
                o.getString("seed").toLong(36), o.optLong("at"), o.optInt("s") == 1,
                o.optString("m").singleOrNull()?.takeIf { it in CODES }, optRounds(o),
            )
            "bye" -> Msg.Bye(o.getString("id"))
            "solve" -> Msg.Solve(o.getString("id"), o.getInt("r"), o.optLong("ms"))
            "skip" -> Msg.Skip(o.getInt("r"))
            "react" -> o.getString("e").takeIf { it in REACTIONS }?.let { Msg.React(o.getString("id"), it, o.getLong("at")) }
            "grid" -> {
                val w = o.getJSONArray("w")
                Msg.Grid(o.getInt("n"), o.optString("th"), o.getString("g").split('|'), (0 until w.length()).map { w.getString(it) })
            }
            "found" -> Msg.Found(o.getString("id"), o.getInt("n"), o.getString("w"))
            "timeup" -> Msg.TimeUp(o.getInt("n"))
            "move" -> if (o.has("p")) {
                val p = o.getJSONArray("p")
                Msg.Move(o.getString("id"), o.getInt("n"), (0 until p.length()).map { p.getInt(it) })
            } else Msg.Cell(o.getString("id"), o.getInt("n"), o.getInt("c"))
            "resign" -> Msg.Resign(o.getString("id"))
            "suggest" -> o.getString("m").singleOrNull()?.takeIf { it in CODES }?.let { Msg.Suggest(o.getString("id"), it) }
            "sround" -> Msg.SRound(o.getInt("n"), o.getInt("k"), letter(o), if (o.has("c")) strings(o, "c") else emptyList())
            "stop" -> Msg.Stop(o.getString("id"), o.getInt("n"), o.getInt("k"))
            "ans" -> Msg.Ans(o.getString("id"), o.getInt("n"), o.getInt("k"), strings(o, "a"))
            "vote" -> Msg.Vote(o.getString("id"), o.getInt("n"), o.getInt("k"), ints(o, "v"))
            "pick" -> Msg.Pick(o.getString("id"), o.getInt("r"), o.optInt("o", -1).takeIf { it in 0..3 } ?: -1, o.optLong("ms").coerceIn(0L, 15_000L))
            "secret" -> Msg.Secret(o.getString("id"), o.getInt("n"), o.optString("th").take(24), o.getString("m"))
            "guess" -> Msg.Guess(o.getString("id"), o.getInt("n"), letter(o))
            "reveal" -> Msg.Reveal(o.getString("id"), o.getInt("n"), letter(o), ints(o, "p"))
            "try" -> Msg.Try(o.getString("id"), o.getInt("n"), o.getString("w"))
            "tryres" -> Msg.TryRes(o.getInt("n"), o.getBoolean("ok"))
            "word" -> Msg.Word(o.getInt("n"), o.getString("w"))
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

    // ---------- melhor de N ----------
    /** Vitórias para levar a série melhor de 3 (padrão). */
    const val SERIES_WINS = 2

    /**
     * Placar da série melhor de [best] (1, 3 ou 5): quem chegar a (best+1)/2 vitórias leva o troféu.
     * Empates não contam.
     */
    data class Series(val me: Int = 0, val opp: Int = 0, val best: Int = 3) {
        val wins: Int get() = (best + 1) / 2
        val over: Boolean get() = me >= wins || opp >= wins
        /** "me", "opp" ou null enquanto a série continua. */
        val winner: String? get() = when { me >= wins -> "me"; opp >= wins -> "opp"; else -> null }
        val score: String get() = "$me × $opp"
        /** "melhor de 3"; vazio quando é uma partida só. */
        val label: String get() = if (best <= 1) "" else "melhor de $best"
        fun plus(outcome: String?): Series = if (over) this else when (outcome) { "me" -> copy(me = me + 1); "opp" -> copy(opp = opp + 1); else -> this }
    }

    // ---------- sala guardada (sobrevive ao app fechado por 10 minutos) ----------
    /** Quanto tempo a sala fica guardada esperando o amigo. */
    const val KEEP_MS = 10 * 60 * 1000L

    /** Sessão salva: igual à do site ("pv-mp-session"): {code, host, id, name, at}. */
    data class Session(val code: String, val host: Boolean, val id: String, val name: String, val at: Long) {
        val room: Room? get() = parse(code)
        val expiresAt: Long get() = at + KEEP_MS
        fun valid(now: Long = System.currentTimeMillis()) = room != null && now < expiresAt && now >= at - 60_000
        fun encode(): String = JSONObject().put("code", code).put("host", host).put("id", id).put("name", name).put("at", at).toString()
        companion object {
            fun decode(text: String?): Session? = runCatching {
                val o = JSONObject(text ?: return null)
                Session(o.getString("code"), o.getBoolean("host"), o.getString("id"), o.optString("name"), o.getLong("at"))
            }.getOrNull()
        }
    }
}
