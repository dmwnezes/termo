package com.dmwnezes.palavreiro

import android.content.Context
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.ConnPuzzle
import com.dmwnezes.palavreiro.game.ConnectionsData
import com.dmwnezes.palavreiro.game.Definition
import com.dmwnezes.palavreiro.game.QuizData
import com.dmwnezes.palavreiro.game.SearchTheme
import com.dmwnezes.palavreiro.game.SynPair
import com.dmwnezes.palavreiro.game.WordSearchData
import com.dmwnezes.palavreiro.game.Words
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Objetos únicos do app, criados na primeira abertura. */
object AppGraph {
    lateinit var store: Store
        private set
    lateinit var words: Words
        private set
    lateinit var feedback: Feedback
        private set

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Conteúdo dos outros jogos (arquivos em assets/).
    lateinit var connections: List<ConnPuzzle>
        private set
    lateinit var themes: List<SearchTheme>
        private set
    lateinit var definitions: List<Definition>
        private set
    lateinit var synonyms: List<SynPair>
        private set
    lateinit var families: List<Set<String>>
        private set
    /** Famílias de assunto dos grupos do Conexões (para o modo infinito). */
    lateinit var connFamilies: Map<String, Set<String>>
        private set
    /** Pares de grafia certa/errada do Certo ou Errado. */
    lateinit var spelling: List<com.dmwnezes.palavreiro.game.SpellPair>
        private set
    /** Temas do Caça-Palavras Infinito: os do dia + os extras. */
    lateinit var themesInfinite: List<SearchTheme>
        private set
    /** Significado de cada palavra do Termo (chave sem acento). */
    lateinit var meanings: Map<String, String>
        private set

    private var ready = false

    fun init(context: Context) {
        if (ready) return
        val app = context.applicationContext
        store = Store(app)
        words = Words(app.assets.open("words.js").bufferedReader().use { it.readText() })
        feedback = Feedback(app, store)
        fun asset(name: String) = app.assets.open(name).bufferedReader().use { it.readText() }
        connections = ConnectionsData.parse(asset("conexoes.txt"))
        themes = WordSearchData.parse(asset("caca.txt"))
        connFamilies = ConnectionsData.families(asset("conexoes-familias.txt"))
        themesInfinite = themes + WordSearchData.parse(asset("caca-extra.txt"))
        spelling = com.dmwnezes.palavreiro.game.SpellingData.parse(asset("ortografia.txt"))
        definitions = QuizData.definitions(asset("definicoes.txt"))
        QuizData.synonyms(asset("sinonimos.txt")).let { (p, f) -> synonyms = p; families = f }
        meanings = QuizData.meanings(asset("significados.txt"))
        ready = true
    }
}
