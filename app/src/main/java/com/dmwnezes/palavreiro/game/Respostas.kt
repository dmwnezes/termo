package com.dmwnezes.palavreiro.game

data class RespItem(val names: List<String>, val pts: Int)
data class RespQ(val title: String, val items: List<RespItem>)

/** Pesquisa e Top 10: mesmas regras do site (js/respostas.js) e os mesmos arquivos. */
object Respostas {
    const val ERRORS = 3

    private val ARTICLE = Regex("^(O|A|OS|AS|UM|UMA) ")

    /** Texto digitado em forma comparável: sem acento, pontuação nem artigo no começo. */
    fun clean(s: String): String =
        Words.normalize(s).map { if (it in 'A'..'Z' || it in '0'..'9' || it == ' ') it else ' ' }.joinToString("")
            .trim().replace(Regex(" +"), " ").replace(ARTICLE, "")

    fun match(items: List<RespItem>, text: String): Int {
        val v = clean(text)
        if (v.isEmpty()) return -1
        return items.indexOfFirst { it.names.any { n -> clean(n) == v } }
    }

    private fun lines(text: String) = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }

    fun parsePesquisa(text: String) = lines(text).map { l ->
        val f = l.split('|')
        RespQ(f[0], f.drop(1).map { x -> val i = x.lastIndexOf(':'); RespItem(x.substring(0, i).split('~'), x.substring(i + 1).toInt()) })
    }

    fun parseTop10(text: String) = lines(text).map { l ->
        val f = l.split('|')
        RespQ(f[0], f.drop(1).map { x -> RespItem(x.split('~'), 1) })
    }

    fun dailyIndex(day: Long, n: Int): Int = Math.floorMod(day, n.toLong()).toInt()
}
