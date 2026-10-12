package com.dmwnezes.palavreiro.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** As 6 categorias da roleta da Trivia, com cor e desenho fixos (a medalha de cada uma). */
enum class TriviaCat(val label: String, val emoji: String, val color: Long) {
    CIENCIA("Ciência", "🧪", 0xFF5FB873),
    HISTORIA("História", "📜", 0xFFE6C14F),
    GEOGRAFIA("Geografia", "🌎", 0xFF6FA8E8),
    ESPORTES("Esportes", "⚽", 0xFFF0904A),
    ARTE("Arte", "🎨", 0xFFF07FB0),
    ENTRETENIMENTO("Entretenimento", "🎬", 0xFFB48CF0);

    companion object {
        fun of(name: String): TriviaCat? = Words.normalize(name.trim()).let { n -> entries.firstOrNull { Words.normalize(it.label) == n } }
    }
}

data class TriviaQ(val cat: TriviaCat, val text: String, val right: String, val wrong: List<String>)

object TriviaData {
    /** Linhas CATEGORIA|pergunta|certa|errada1|errada2|errada3 (comentários com #). Linhas inválidas são ignoradas. */
    fun parse(text: String): List<TriviaQ> = text.lines().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
        val p = line.split('|').map { it.trim() }
        if (p.size < 6 || p.any { it.isEmpty() }) return@mapNotNull null
        val cat = TriviaCat.of(p[0]) ?: return@mapNotNull null
        val wrong = p.subList(3, 6).distinct().filter { it != p[2] }
        if (wrong.size < 3) null else TriviaQ(cat, p[1], p[2], wrong)
    }
}

/**
 * Trivia (estilo Perguntados). A cada vez a roleta sorteia uma categoria e vem uma pergunta dela.
 * 3 acertos seguidos dão uma coroa: escolha uma categoria sem medalha e acerte a pergunta da coroa
 * para ganhar a medalha. As 6 medalhas vencem o jogo; 3 erros (vidas) terminam a partida.
 * Nenhuma pergunta se repete na partida; se as perguntas acabarem, a partida termina.
 */
class TriviaGame(questions: List<TriviaQ>, private val rnd: Random = Random.Default) {
    enum class Phase { SPIN, QUESTION, ANSWERED, CROWN, WON, OVER }

    private val decks: Map<TriviaCat, MutableList<TriviaQ>> =
        TriviaCat.entries.associateWith { c -> questions.filter { it.cat == c }.distinctBy { it.text }.shuffled(rnd).toMutableList() }

    /** Perguntas já feitas nesta partida, na ordem. */
    val asked = mutableListOf<TriviaQ>()

    var phase by mutableStateOf(Phase.SPIN)
        private set
    var category by mutableStateOf<TriviaCat?>(null)
        private set
    var question by mutableStateOf<TriviaQ?>(null)
        private set
    var options by mutableStateOf<List<String>>(emptyList())
        private set
    /** A pergunta atual vale uma medalha (pergunta da coroa). */
    var crownRound by mutableStateOf(false)
        private set
    /** Resposta escolhida (null depois de responder = o tempo acabou). */
    var picked by mutableStateOf<String?>(null)
        private set
    var lastRight by mutableStateOf(false)
        private set
    /** Muda a cada giro da roleta (para a animação recomeçar mesmo se cair na mesma categoria). */
    var turn by mutableIntStateOf(0)
        private set

    val maxLives = 3
    var lives by mutableIntStateOf(maxLives)
        private set
    /** Medidor da coroa: acertos seguidos desde a última coroa (0 a 3). */
    var meter by mutableIntStateOf(0)
        private set
    /** Acertos seguidos na partida (o recorde "melhor sequência"). */
    var run by mutableIntStateOf(0)
        private set
    var bestRun by mutableIntStateOf(0)
        private set
    var right by mutableIntStateOf(0)
        private set
    /** As perguntas acabaram antes de a partida terminar. */
    var exhausted by mutableStateOf(false)
        private set

    val medals = mutableStateListOf<TriviaCat>()
    /** Acertos de cada categoria (fora as perguntas da coroa). */
    val progress = mutableStateMapOf<TriviaCat, Int>()

    val won: Boolean get() = phase == Phase.WON
    val over: Boolean get() = phase == Phase.WON || phase == Phase.OVER

    fun left(c: TriviaCat): Int = decks.getValue(c).size
    /** Categorias que a roleta ainda pode sortear. */
    val spinnable: List<TriviaCat> get() = TriviaCat.entries.filter { left(it) > 0 }
    /** Categorias que podem ser escolhidas com a coroa (sem medalha e com pergunta sobrando). */
    val crownChoices: List<TriviaCat> get() = TriviaCat.entries.filter { it !in medals && left(it) > 0 }

    init { spin() }

    /** Gira a roleta: sorteia a categoria (a tela anima e depois chama [ask]). */
    fun spin(): TriviaCat? {
        val c = spinnable.randomOrNull(rnd)
        if (c == null) { exhausted = true; phase = Phase.OVER; return null }
        category = c
        crownRound = false
        question = null
        picked = null
        turn++
        phase = Phase.SPIN
        return c
    }

    /** A roleta parou: mostra a pergunta da categoria sorteada. */
    fun ask() {
        if (phase != Phase.SPIN) return
        draw(category ?: return)
        phase = Phase.QUESTION
    }

    /** Com a coroa: tenta a medalha de [c]. */
    fun chooseCrown(c: TriviaCat): Boolean {
        if (phase != Phase.CROWN || c !in crownChoices) return false
        category = c
        crownRound = true
        draw(c)
        phase = Phase.QUESTION
        return true
    }

    private fun draw(c: TriviaCat) {
        val q = decks.getValue(c).removeAt(0)
        asked += q
        question = q
        options = (q.wrong + q.right).shuffled(rnd)
        picked = null
    }

    /** Responde ([option] null = tempo esgotado). Devolve se acertou. */
    fun answer(option: String?): Boolean {
        val q = question
        if (phase != Phase.QUESTION || q == null) return false
        picked = option
        val ok = option == q.right
        lastRight = ok
        if (ok) {
            right++; run++; bestRun = maxOf(bestRun, run)
            if (crownRound) { if (q.cat !in medals) medals += q.cat; meter = 0 }
            else { progress[q.cat] = (progress[q.cat] ?: 0) + 1; meter = minOf(3, meter + 1) }
        } else {
            lives--; run = 0; meter = 0
        }
        phase = Phase.ANSWERED
        return ok
    }

    /** Depois de mostrar a resposta: vitória, fim, escolha da coroa ou novo giro. */
    fun next() {
        if (phase != Phase.ANSWERED) return
        when {
            medals.size == TriviaCat.entries.size -> phase = Phase.WON
            lives <= 0 -> phase = Phase.OVER
            meter >= 3 && crownChoices.isNotEmpty() -> phase = Phase.CROWN
            else -> { if (meter >= 3) meter = 0; spin() }
        }
    }
}
