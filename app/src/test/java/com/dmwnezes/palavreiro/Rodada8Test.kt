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
        val b = Msg.Again(99, 5, newSeries = true, mode = 'b')
        assertEquals(mapOf("t" to "again", "seed" to "2r", "at" to 5, "s" to 1, "m" to "b"), keys(Multi.encode(b)))
        assertEquals(b, Multi.decode(Multi.encode(b)))
        assertEquals(b, Multi.decode("""{"t":"again","seed":"2r","at":5,"s":1,"m":"b"}"""))
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
        val toBomb = Multi.againFor('t', 'b', seriesOver = false, seed = 77, at = 1)
        assertEquals(Msg.Again(77, 1, true, 'b'), toBomb)
        assertEquals(Msg.Again(77, 1, false, 'a'), Multi.againFor('a', 'a', seriesOver = true, seed = 77, at = 1))

        // Quem recebe: modo novo → sala com outro código e série zerada.
        val (r1, reset1) = Multi.applyAgain(room, toBomb, seriesOver = false)
        assertEquals('b', r1.mode); assertEquals(77L, r1.seed); assertTrue(reset1)
        assertEquals("abc123xyz789-b-25", r1.code)
        assertEquals(room.topic, r1.topic) // mesmo tópico: continua a mesma sala
        assertEquals(r1, Multi.parse(r1.code))
        // Mesmo modo: série continua.
        val (r2, reset2) = Multi.applyAgain(room, same, seriesOver = false)
        assertEquals('t', r2.mode); assertFalse(reset2)
        // Sem "m": mantém o modo; série acabada zera.
        val (r3, reset3) = Multi.applyAgain(r1, Msg.Again(5, 1), seriesOver = true)
        assertEquals('b', r3.mode); assertTrue(reset3)
        // A sessão guardada usa o código novo.
        assertEquals('b', Multi.Session(r1.code, true, "id", "Ana", 0).room!!.mode)
    }

    @Test
    fun botaoDaRevanche() {
        assertEquals("Próxima rodada", Multi.againLabel('t', 't', false))
        assertEquals("Nova série", Multi.againLabel('b', 'b', true))
        assertEquals("Revanche", Multi.againLabel('a', 'a', false))
        assertEquals("Jogar Bomba-Relógio", Multi.againLabel('t', 'b', false))
        assertEquals("Jogar Anagrama", Multi.againLabel('b', 'a', true))
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
        val b = MatchStory.bomb("Daniel", "Ana", Multi.Series(1, 0), "me", 7)
        assertEquals("Bomba-Relógio 💣", b.modeLabel)
        assertEquals("Daniel venceu a rodada", b.result)
        assertEquals("7 palavras antes de explodir 💥", b.detail)
        assertEquals("1 palavra antes de explodir 💥", MatchStory.bomb(null, "Ana", Multi.Series(), "opp", 1).detail)
        val a = MatchStory.anagram("Daniel", "Ana", 6, 4)
        assertEquals("Anagrama 🔤", a.modeLabel)
        assertEquals("Daniel  6 × 4  Ana", a.headline)
        assertEquals("🏆 Daniel venceu!", a.result)
        assertEquals("10 rodadas · 6 × 4", a.detail)
        assertEquals("Empate", MatchStory.anagram("Daniel", "Ana", 5, 5).result)
        assertEquals("🏆 Ana venceu!", MatchStory.anagram("", "Ana", 3, 7).result)
    }
}
