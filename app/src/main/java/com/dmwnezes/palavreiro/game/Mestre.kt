package com.dmwnezes.palavreiro.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** Tipos de ordem do Mestre Mandou. */
enum class OrderType {
    /** "O mestre mandou: toque ..." — toque na única palavra certa. */
    NORMAL,
    /** "O mestre mandou: NÃO toque ..." — toque em qualquer outra. */
    NAO_TOQUE,
    /** "O mestre NÃO mandou: toque ..." — não toque em nada. */
    NAO_MANDOU;

    val isTrick: Boolean get() = this == NAO_MANDOU
}

/** Uma rodada: a ordem, as 4 palavras e quais cumprem o critério. */
data class MestreRound(val type: OrderType, val text: String, val options: List<String>, val targets: Set<String>)

/** Conteúdo usado pelo Mestre Mandou (o mesmo dos outros jogos). */
class MestreData(
    puzzles: List<ConnPuzzle>,
    private val connFamilies: Map<String, Set<String>>,
    val synPairs: List<SynPair>,
    val synFamilies: List<Set<String>>,
    val antPairs: List<SynPair>,
    val antFamilies: List<Set<String>>,
) {
    private val banned = setOf("lacuna", "palavra", "fora")
    val groups: List<ConnGroup> = puzzles.flatMap { it.groups }
        .filter { g -> g.words.size == 4 && connFamilies[g.name]?.let { f -> f.isNotEmpty() && f.none { it in banned } } == true }

    /** Banco para as ordens de letra: palavras únicas (sem espaço/hífen), 4 a 10 letras, sem repetir pela forma sem acento. */
    val bank: List<String> = (puzzles.flatMap { p -> p.groups.flatMap { it.words } } +
        synPairs.flatMap { listOf(it.word, it.synonym) } + antPairs.flatMap { listOf(it.word, it.synonym) })
        .map { it.trim() }
        .filter { w -> w.none { it == ' ' || it == '-' || it == '\'' } && letters(w).length in 4..10 }
        .distinctBy { letters(it) }

    fun familiesOf(name: String): Set<String> = connFamilies[name].orEmpty()

    companion object {
        /** Só as letras, sem acento, em maiúsculas. */
        fun letters(w: String): String = Words.normalize(w).filter { it in 'A'..'Z' }
        private const val ACCENTS = "ÁÀÂÃÉÊÍÓÔÕÚÜáàâãéêíóôõúü"
        fun hasAccent(w: String): Boolean = w.any { it in ACCENTS }
    }
}

/**
 * Mestre Mandou: obedeça só quando a ordem começa com "O mestre mandou".
 * Mistura ordens de letras, de sentido (sinônimo, contrário, grupo) e pegadinhas. 3 vidas.
 */
class MestreGame(private val data: MestreData, private val rnd: Random = Random.Default) {
    var score by mutableIntStateOf(0)
        private set
    var lives by mutableIntStateOf(3)
        private set
    var round by mutableStateOf(newRound())
        private set
    /** Palavras tocadas nesta rodada. */
    val tapped = mutableStateListOf<String>()
    /** null = rodada em andamento; true/false = acertou ou errou. */
    var result by mutableStateOf<Boolean?>(null)
        private set
    var message by mutableStateOf("")
        private set
    val over: Boolean get() = lives <= 0

    /** Segundos desta rodada: começa em 6 s e encurta com os pontos; pegadinhas têm 70% do tempo. */
    val seconds: Double get() = maxOf(2.5, 6.0 - 0.15 * score) * if (round.type.isTrick) 0.7 else 1.0

    fun tap(word: String) {
        if (result != null || over || word in tapped) return
        tapped += word
        val r = round
        when (r.type) {
            OrderType.NORMAL -> if (word in r.targets) win("Isso!") else lose("Não era essa!")
            OrderType.NAO_TOQUE -> if (word in r.targets) lose("O mestre mandou NÃO tocar nessa!") else win("Boa!")
            OrderType.NAO_MANDOU -> lose("Pegadinha! O mestre não mandou.")
        }
    }

    fun timeUp() {
        if (result != null || over) return
        if (round.type.isTrick) win("Boa! O mestre não mandou.") else lose("O tempo acabou!")
    }

    private fun win(msg: String) { result = true; score++; message = msg }
    private fun lose(msg: String) { result = false; lives--; message = msg }

    fun next() {
        if (over) return
        tapped.clear(); result = null; message = ""
        round = newRound()
    }

    // ---------- geração das rodadas ----------

    private class Criterion(val singular: String, val plural: String?, val targets: List<String>, val others: List<String>)

    private fun newRound(): MestreRound {
        val x = rnd.nextDouble()
        val type = when {
            x < 0.62 -> OrderType.NORMAL
            x < 0.80 -> OrderType.NAO_TOQUE
            else -> OrderType.NAO_MANDOU
        }
        // Sempre uma única palavra certa (ou nenhum toque, quando o mestre não mandou).
        val count = 1
        var c: Criterion? = null
        while (c == null) c = criterion(count)
        val text = when (type) {
            OrderType.NORMAL -> "O mestre mandou: toque ${c.singular}"
            OrderType.NAO_TOQUE -> "O mestre mandou: NÃO toque ${c.singular}"
            OrderType.NAO_MANDOU -> "O mestre NÃO mandou: toque ${c.singular}"
        }
        return MestreRound(type, text, (c.targets + c.others).shuffled(rnd), c.targets.toSet())
    }

    /** Sorteia um critério com [count] palavras certas e 4 − count erradas, ou null se não conseguir. */
    private fun criterion(count: Int): Criterion? {
        val letterSide = rnd.nextBoolean()
        return if (letterSide) letterCriterion(count) else meaningCriterion(count)
    }

    private fun pick(list: List<String>, n: Int): List<String>? = if (list.size < n) null else list.shuffled(rnd).take(n)

    private fun build(singular: String, plural: String?, ok: (String) -> Boolean, count: Int, pool: List<String> = data.bank): Criterion? {
        val yes = pick(pool.filter(ok), count) ?: return null
        val no = pick(pool.filter { !ok(it) }, 4 - count) ?: return null
        return Criterion(singular, plural, yes, no)
    }

    private fun letterCriterion(count: Int): Criterion? {
        val L = MestreData::letters
        val seed = data.bank.random(rnd)
        return when (rnd.nextInt(if (count == 1) 6 else 5)) {
            0 -> { val c = L(seed).first(); build("na palavra que começa com $c", "as palavras que começam com $c", { L(it).first() == c }, count) }
            1 -> { val c = L(seed).last(); build("na palavra que termina com $c", "as palavras que terminam com $c", { L(it).last() == c }, count) }
            2 -> {
                val c = "BCDFGHJLMNPQRSTVXZ".random(rnd)
                // Com a letra C, palavras com Ç ficam de fora (para não confundir C com Ç).
                val pool = if (c == 'C') data.bank.filter { 'Ç' !in it.uppercase() } else data.bank
                build("na palavra que tem a letra $c", "as palavras que têm a letra $c", { c in L(it) }, count, pool)
            }
            3 -> build("na palavra com acento", "as palavras com acento", MestreData::hasAccent, count)
            4 -> { val n = L(seed).length; build("na palavra de $n letras", "as palavras de $n letras", { L(it).length == n }, count) }
            else -> {
                val others = pick(data.bank, 3) ?: return null
                val max = others.maxOf { L(it).length }
                val longer = data.bank.filter { L(it).length > max }
                if (longer.isEmpty()) null else Criterion("na palavra mais comprida", null, listOf(longer.random(rnd)), others)
            }
        }
    }

    private fun meaningCriterion(count: Int): Criterion? = when (if (count == 2) 2 else rnd.nextInt(3)) {
        0 -> chain(data.synPairs, data.synFamilies) { "no sinônimo de $it" }
        1 -> chain(data.antPairs, data.antFamilies) { "no contrário de $it" }
        else -> category(count)
    }

    /** Sinônimo ou contrário: mesma regra dos jogos de cadeia para as alternativas erradas. */
    private fun chain(pairs: List<SynPair>, families: List<Set<String>>, phrase: (String) -> String): Criterion? {
        if (pairs.isEmpty()) return null
        fun familyOf(w: String): Set<String> = families.filter { w in it }.flatten().toSet() + w
        val q = pairs.random(rnd)
        val blocked = familyOf(q.word) + familyOf(q.synonym)
        val pool = pairs.map { it.synonym }.distinct().filter { it !in blocked && familyOf(it).none { f -> f in blocked } }
        val wrong = pick(pool, 3) ?: return null
        return Criterion(phrase(q.word), null, listOf(q.synonym), wrong)
    }

    /** Grupo do Conexões: as erradas vêm de grupos sem nenhum assunto em comum. */
    private fun category(count: Int): Criterion? {
        if (data.groups.isEmpty()) return null
        val g = data.groups.random(rnd)
        val fam = data.familiesOf(g.name)
        val inG = g.words.map { Words.normalize(it) }.toSet()
        val pool = data.groups.filter { o -> o.name != g.name && data.familiesOf(o.name).none { it in fam } }
            .flatMap { it.words }.filter { Words.normalize(it) !in inG }.distinctBy { Words.normalize(it) }
        val yes = pick(g.words, count) ?: return null
        val no = pick(pool, 4 - count) ?: return null
        val name = g.name.uppercase()
        return Criterion("na palavra do grupo $name", "as palavras do grupo $name", yes, no)
    }
}
