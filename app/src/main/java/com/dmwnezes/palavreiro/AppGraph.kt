package com.dmwnezes.palavreiro

import android.content.Context
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.ConnPuzzle
import com.dmwnezes.palavreiro.game.ConnectionsData
import com.dmwnezes.palavreiro.game.Definition
import com.dmwnezes.palavreiro.game.QuizData
import com.dmwnezes.palavreiro.game.SynPair
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
    lateinit var definitions: List<Definition>
        private set
    lateinit var synonyms: List<SynPair>
        private set
    lateinit var families: List<Set<String>>
        private set
    /** Antônimos (pares nos dois sentidos) e famílias que bloqueiam alternativas ambíguas. */
    lateinit var antonyms: List<com.dmwnezes.palavreiro.game.SynPair>
        private set
    lateinit var antonymFamilies: List<Set<String>>
        private set
    /** Famílias de assunto dos grupos do Conexões (para o modo infinito). */
    lateinit var connFamilies: Map<String, Set<String>>
        private set
    /** Conteúdo do Mestre Mandou (palavras e grupos dos outros jogos). */
    lateinit var mestre: com.dmwnezes.palavreiro.game.MestreData
        private set
    /** Pares de grafia certa/errada do Certo ou Errado. */
    lateinit var spelling: List<com.dmwnezes.palavreiro.game.SpellPair>
        private set
    /** Significado de cada palavra do Termo (chave sem acento). */
    lateinit var meanings: Map<String, String>
        private set

    /** Mensagens da partida com amigo (ntfy.sh). */
    val ntfy by lazy { com.dmwnezes.palavreiro.system.Ntfy(http) }

    private var ready = false

    /** Palavra com acento para exibir (cai na própria palavra se ainda não carregou). */
    fun displayWord(w: String): String = if (::words.isInitialized) words.display(com.dmwnezes.palavreiro.game.Words.normalize(w)) else w

    fun init(context: Context) {
        if (ready) return
        val app = context.applicationContext
        store = Store(app)
        words = Words(app.assets.open("words.js").bufferedReader().use { it.readText() })
        feedback = Feedback(app, store)
        fun asset(name: String) = app.assets.open(name).bufferedReader().use { it.readText() }
        connections = ConnectionsData.parse(asset("conexoes.txt"))
        connFamilies = ConnectionsData.families(asset("conexoes-familias.txt"))
        spelling = com.dmwnezes.palavreiro.game.SpellingData.parse(asset("ortografia.txt"))
        definitions = QuizData.definitions(asset("definicoes.txt"))
        QuizData.synonyms(asset("sinonimos.txt")).let { (p, f) -> synonyms = p; families = f }
        QuizData.antonyms(asset("antonimos.txt"), families).let { (p, f) -> antonyms = p; antonymFamilies = f }
        meanings = QuizData.meanings(asset("significados.txt"))
        mestre = com.dmwnezes.palavreiro.game.MestreData(connections, connFamilies, synonyms, families, antonyms, antonymFamilies)
        ready = true
    }
}
