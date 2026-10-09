package com.dmwnezes.palavreiro.game

/**
 * Desafio por link: a palavra vai embaralhada no endereço, para não ser lida de cara.
 * O mesmo cálculo existe no site (game.js), então o link abre igual no app e no navegador.
 */
object Challenge {
    const val BASE = "https://dmwnezes.github.io/termo/"

    fun encode(word: String): String {
        val w = Words.normalize(word)
        return w.mapIndexed { i, c -> 'a' + ((c - 'A') + 7 * i + 3).mod(26) }.joinToString("").reversed()
    }

    fun decode(code: String): String? {
        val s = code.trim().lowercase().reversed()
        if (s.length != Words.WORD_LENGTH || s.any { it !in 'a'..'z' }) return null
        return s.mapIndexed { i, c -> 'A' + ((c - 'a') - 7 * i - 3).mod(26) }.joinToString("")
    }

    fun link(word: String): String = BASE + "?d=" + encode(word)
}
