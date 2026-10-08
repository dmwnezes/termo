package com.dmwnezes.palavreiro

import android.content.Context
import com.dmwnezes.palavreiro.data.Store
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

    private var ready = false

    fun init(context: Context) {
        if (ready) return
        val app = context.applicationContext
        store = Store(app)
        words = Words(app.assets.open("words.js").bufferedReader().use { it.readText() })
        feedback = Feedback(app, store)
        ready = true
    }
}
