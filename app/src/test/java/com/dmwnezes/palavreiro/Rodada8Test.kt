package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.ui.MatchStory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rodada 8: reações rápidas, revanche trocando de modo e o cartão dos Stories da partida com amigo. */
class Rodada8Test {
    private fun keys(json: String): Map<String, Any> = JSONObject(json).let { o -> o.keySet().associateWith { o.get(it) } }

    @Test
    fun reacaoFormatoExato() {
        val m = Msg.React("ab12cd34", "🔥", 1791611700000)
        assertEquals(mapOf("t" to "react", "id" to "ab12cd34", "e" to "🔥", "at" to 1791611700000L), keys(Multi.encode(m)))
        assertEquals(m, Multi.decode(Multi.encode(m)))
        // Formato exato do spec (como o site manda).
        assertEquals(m, Multi.decode("""{"t":"react","id":"ab12cd34","e":"🔥","at":1791611700000}"""))
        for (e in listOf("😂", "😱", "🔥", "👏", "😈")) assertEquals(e, (Multi.decode("""{"t":"react","id":"x","e":"$e","at":1}""") as Msg.React).emoji)
        // Emoji fora da lista: ignora.
        assertNull(Multi.decode("""{"t":"react","id":"x","e":"💩","at":1}"""))
        assertNull(Multi.decode("""{"t":"react","id":"x","at":1}"""))
    }

    @Test
    fun reacaoSoFresca() {
        val now = 1_800_000_000_000L
        val fresh = Msg.React("op", "😂", now - 3000)
        assertTrue(Multi.showReaction(fresh, "me", now))
        assertTrue(Multi.showReaction(Msg.React("op", "😂", now + 9999), "me", now)) // relógio do outro adiantado
        assertFalse(Multi.showReaction(Msg.React("op", "😂", now - 10_000), "me", now)) // velha (histórico)
        assertFalse(Multi.showReaction(Msg.React("op", "😂", now - 600_000), "me", now))
        assertFalse(Multi.showReaction(Msg.React("me", "😂", now), "me", now)) // a minha, vinda do tópico
        assertFalse(Multi.showReaction(Msg.React("op", "🙂", now), "me", now))
        assertEquals(1500L, Multi.REACT_GAP_MS)
    }

    @Test
    fun againComModo() {
        val b = Msg.Again(99, 5, newSeries = true, mode = 'x')
        assertEquals(mapOf("t" to "again", "seed" to "2r", "at" to 5, "s" to 1, "m" to "x"), keys(Multi.encode(b)))
        assertEquals(b, Multi.decode(Multi.encode(b)))
        assertEquals(b, Multi.decode("""{"t":"again","seed":"2r","at":5,"s":1,"m":"x"}"""))
        // "b" (Bomba-Relógio) saiu: vira modo desconhecido.
        assertEquals(Msg.Again(99, 5, true, null), Multi.decode("""{"t":"again","seed":"2r","at":5,"s":1,"m":"b"}"""))
        // Sem "s", com "m" igual ao atual.
        assertEquals(mapOf("t" to "again", "seed" to "2r", "at" to 5, "m" to "t"), keys(Multi.encode(Msg.Again(99, 5, mode = 't'))))
        assertEquals(Msg.Again(99, 5, false, 'a'), Multi.decode("""{"t":"again","seed":"2r","at":5,"m":"a"}"""))
        // Sem "m" (app/site antigo) ou com modo desconhecido: mantém o modo atual.
        assertEquals(Msg.Again(99, 5, false, null), Multi.decode("""{"t":"again","seed":"2r","at":5}"""))
        assertEquals(Msg.Again(99, 5, false, null), Multi.decode("""{"t":"again","seed":"2r","at":5,"m":"z"}"""))
        assertFalse(keys(Multi.encode(Msg.Again(99, 5))).containsKey("m"))
    }

    @Test
    fun trocaDeModo() {
        val room = Multi.Room("abc123xyz789", 't', 123456789)
        // O anfitrião sempre manda "m"; trocar de modo zera a série.
        val same = Multi.againFor('t', 't', seriesOver = false, seed = 77, at = 1)
        assertEquals(Msg.Again(77, 1, false, 't'), same)
        assertEquals(Msg.Again(77, 1, true, 't'), Multi.againFor('t', 't', seriesOver = true, seed = 77, at = 1))
        val toBomb = Multi.againFor('t', 'v', seriesOver = false, seed = 77, at = 1)
        assertEquals(Msg.Again(77, 1, true, 'v'), toBomb)
        assertEquals(Msg.Again(77, 1, false, 'a'), Multi.againFor('a', 'a', seriesOver = true, seed = 77, at = 1))

        // Quem recebe: modo novo → sala com outro código e série zerada.
        val (r1, reset1) = Multi.applyAgain(room, toBomb, seriesOver = false)
        assertEquals('v', r1.mode); assertEquals(77L, r1.seed); assertTrue(reset1)
        assertEquals("abc123xyz789-v-25", r1.code)
        assertEquals(room.topic, r1.topic) // mesmo tópico: continua a mesma sala
        assertEquals(r1, Multi.parse(r1.code))
        // Mesmo modo: série continua.
        val (r2, reset2, _) = Multi.applyAgain(room, same, seriesOver = false)
        assertEquals('t', r2.mode); assertFalse(reset2)
        // Sem "m": mantém o modo; série acabada zera.
        val (r3, reset3) = Multi.applyAgain(r1, Msg.Again(5, 1), seriesOver = true)
        assertEquals('v', r3.mode); assertTrue(reset3)
        // A sessão guardada usa o código novo.
        assertEquals('v', Multi.Session(r1.code, true, "id", "Ana", 0).room!!.mode)
    }

    @Test
    fun botaoDaRevanche() {
        assertEquals("Próxima rodada", Multi.againLabel('t', 't', false))
        assertEquals("Nova série", Multi.againLabel('x', 'x', true))
        assertEquals("Revanche", Multi.againLabel('p', 'p', true))
        assertEquals("Revanche", Multi.againLabel('a', 'a', false))
        assertEquals("Jogar Jogo da Velha", Multi.againLabel('t', 'v', false))
        assertEquals("Jogar Anagrama", Multi.againLabel('x', 'a', true))
        assertEquals("Jogar Qual é a Palavra?", Multi.againLabel('c', 'p', false))
        assertEquals("Jogar Dueto", Multi.againLabel('a', 'd', false))
    }

    @Test
    fun textosDoCartao() {
        val s = MatchStory.termo(
            'd', "Daniel", "Ana", Multi.Series(2, 1), "me",
            Msg.End("a", true, 4, 83_000), Msg.End("b", false, 7, 90_000),
        )
        assertEquals("Dueto ⚔️", s.modeLabel)
        assertEquals("Daniel  2 × 1  Ana", s.headline)
        assertEquals("🏆 Daniel levou a série!", s.result)
        assertEquals("Daniel: acertou em 4 · 1:23  |  Ana: errou", s.detail)
        val r = MatchStory.termo('t', "  ", "Ana", Multi.Series(0, 1), "opp", null, Msg.End("b", true, 3, 20_000))
        assertEquals("Você  0 × 1  Ana", r.headline)
        assertEquals("Ana venceu a rodada", r.result)
        assertEquals("Você: jogando  |  Ana: acertou em 3 · 0:20", r.detail)
        assertEquals("Empate", MatchStory.termo('q', "Dan", "Ana", Multi.Series(1, 1), "draw", null, null).result)
        val b = MatchStory.series('c', "Daniel", "Ana", Multi.Series(1, 0), "me", "Palavras: 5 × 3")
        assertEquals("Caça-Palavras 🔍", b.modeLabel)
        assertEquals("Daniel venceu a rodada", b.result)
        assertEquals("Palavras: 5 × 3", b.detail)
        assertEquals("🏆 Ana venceu!", MatchStory.series('x', null, "Ana", Multi.Series(0, 1, best = 1), "opp", "Peças: 0 × 3").result)
        assertEquals("Damas ♟️", MatchStory.series('x', null, "Ana", Multi.Series(), "opp", "").modeLabel)
        val p = MatchStory.points('p', "Daniel", "Ana", 9, 6, 15)
        assertEquals("Qual é a Palavra? 💡", p.modeLabel); assertEquals("15 rodadas · 9 × 6", p.detail)
        val a = MatchStory.anagram("Daniel", "Ana", 6, 4)
        assertEquals("Anagrama 🔤", a.modeLabel)
        assertEquals("Daniel  6 × 4  Ana", a.headline)
        assertEquals("🏆 Daniel venceu!", a.result)
        assertEquals("10 rodadas · 6 × 4", a.detail)
        assertEquals("Empate", MatchStory.anagram("Daniel", "Ana", 5, 5).result)
        assertEquals("🏆 Ana venceu!", MatchStory.anagram("", "Ana", 3, 7).result)
    }
}
