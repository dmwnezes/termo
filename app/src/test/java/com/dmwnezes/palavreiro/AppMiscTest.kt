package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.data.Stats
import com.dmwnezes.palavreiro.game.Achievement
import com.dmwnezes.palavreiro.game.AchievementWatch
import com.dmwnezes.palavreiro.game.Achievements
import com.dmwnezes.palavreiro.game.Flash
import com.dmwnezes.palavreiro.game.QuizData
import com.dmwnezes.palavreiro.game.Records
import com.dmwnezes.palavreiro.game.Words
import com.dmwnezes.palavreiro.system.WidgetKit
import com.dmwnezes.palavreiro.ui.Dest
import com.dmwnezes.palavreiro.ui.LetterBurst
import com.dmwnezes.palavreiro.ui.recordCards
import com.dmwnezes.palavreiro.update.AutoUpdate
import com.dmwnezes.palavreiro.update.Release
import com.dmwnezes.palavreiro.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class AppMiscTest {
    private val defs = QuizData.definitions(File("../shared/definicoes.txt").readText())

    // ---------- Palavra Relâmpago (widget) ----------

    @Test
    fun relampagoRodadaTemTresPalavrasDiferentesComAResposta() {
        var s = Flash.next(defs, null, Random(1))
        repeat(300) {
            assertEquals(3, s.options.size)
            assertTrue(s.def in s.options)
            assertEquals(3, s.options.map { Words.normalize(defs[it].word) }.toSet().size)
            assertFalse(s.answered)
            val n = Flash.next(defs, Flash.answer(s, 0), Random(it))
            assertNotEquals(s.def, n.def)
            s = n
        }
    }

    @Test
    fun relampagoAcertoSomaErroZeraRecordeFica() {
        val r = Random(7)
        var s = Flash.next(defs, null, r)
        s = Flash.answer(s, s.correctIndex)
        assertTrue(s.wasRight); assertEquals(1, s.streak); assertEquals(1, s.best)
        // Responder de novo não muda nada.
        assertEquals(s, Flash.answer(s, (s.correctIndex + 1) % 3))
        s = Flash.next(defs, s, r); s = Flash.answer(s, s.correctIndex)
        s = Flash.next(defs, s, r); s = Flash.answer(s, s.correctIndex)
        assertEquals(3, s.streak); assertEquals(3, s.best); assertEquals(3, s.right); assertEquals(3, s.played)
        s = Flash.next(defs, s, r)
        assertEquals("a sequência continua na próxima", 3, s.streak)
        s = Flash.answer(s, (s.correctIndex + 2) % 3)
        assertFalse(s.wasRight); assertEquals(0, s.streak); assertEquals(3, s.best); assertEquals(4, s.played)
        // Pular sem responder zera a sequência.
        var t = Flash.answer(Flash.next(defs, s, r), 0).let { Flash.next(defs, it.copy(chosen = it.correctIndex, streak = 2), r) }
        assertEquals(2, t.streak)
        t = Flash.next(defs, t, r)
        assertEquals(0, t.streak)
    }

    @Test
    fun relampagoSalvaELe() {
        val s = Flash.answer(Flash.next(defs, null, Random(3)), 1).copy(best = 9, right = 4, played = 11)
        assertEquals(s, Flash.decode(Flash.encode(s), defs.size))
        assertNull(Flash.decode(null, defs.size))
        assertNull(Flash.decode("lixo", defs.size))
        assertNull("índices fora da lista", Flash.decode(Flash.encode(s), 2))
    }

    // ---------- Atualização automática ----------

    private fun rel(tag: String) = Release(tag, Updater.numberFromTag(tag), "", "https://x/Palavreiro.apk", 1)

    @Test
    fun comparaVersoes() {
        assertEquals(12, Updater.numberFromTag("v1.0.12"))
        assertTrue(Updater.isNewer("v1.0.13", 12))
        assertFalse(Updater.isNewer("v1.0.12", 12))
        assertFalse(Updater.isNewer("v1.0.9", 12))
        assertFalse(Updater.isNewer("lixo", 12))
    }

    @Test
    fun decideOProximoPasso() {
        assertEquals(AutoUpdate.Action.NONE, AutoUpdate.decide(12, null, true, null, false))
        assertEquals(AutoUpdate.Action.NONE, AutoUpdate.decide(12, rel("v1.0.13"), enabled = false, null, false))
        assertEquals(AutoUpdate.Action.NONE, AutoUpdate.decide(12, rel("v1.0.12"), true, null, false))
        assertEquals(AutoUpdate.Action.DOWNLOAD, AutoUpdate.decide(12, rel("v1.0.13"), true, null, false))
        assertEquals("baixado, mas o arquivo sumiu", AutoUpdate.Action.DOWNLOAD, AutoUpdate.decide(12, rel("v1.0.13"), true, "v1.0.13", false))
        assertEquals("baixado de uma versão mais velha", AutoUpdate.Action.DOWNLOAD, AutoUpdate.decide(12, rel("v1.0.14"), true, "v1.0.13", true))
        assertEquals(AutoUpdate.Action.INSTALL, AutoUpdate.decide(12, rel("v1.0.13"), true, "v1.0.13", true))
    }

    @Test
    fun leAAtualizacaoPendente() {
        assertEquals("v1.0.3" to "/data/x/Palavreiro-v1.0.3.apk", AutoUpdate.parsePending("v1.0.3|/data/x/Palavreiro-v1.0.3.apk"))
        assertNull(AutoUpdate.parsePending(null))
        assertNull(AutoUpdate.parsePending("v1.0.3|"))
        assertNull(AutoUpdate.parsePending("|/x"))
        assertNull(AutoUpdate.parsePending("semseparador"))
    }

    // ---------- Conquistas novas ----------

    private fun ach(title: String, p: Int) = Achievement("⭐", title, "", p, 1)

    @Test
    fun primeiraVezNaoAvisaENaoRepete() {
        val list = listOf(ach("A", 1), ach("B", 0), ach("C", 1))
        // Primeira vez (sem "ach_seen"): nada a avisar, as já desbloqueadas viram vistas.
        val (fresh0, seen0) = AchievementWatch.newlyUnlocked(list, null)
        assertTrue(fresh0.isEmpty())
        assertEquals(setOf("A", "C"), seen0)
        // Nada mudou: nada a avisar.
        assertTrue(AchievementWatch.newlyUnlocked(list, seen0).first.isEmpty())
        // B desbloqueou: avisa só B, uma vez.
        val later = listOf(ach("A", 1), ach("B", 1), ach("C", 1))
        val (fresh1, seen1) = AchievementWatch.newlyUnlocked(later, seen0)
        assertEquals(listOf("B"), fresh1.map { it.title })
        assertEquals(setOf("A", "B", "C"), seen1)
        assertTrue(AchievementWatch.newlyUnlocked(later, seen1).first.isEmpty())
    }

    @Test
    fun variasDeUmaVezComAsConquistasReais() {
        val before = Achievements.all(Stats(), Stats())
        val seen = AchievementWatch.newlyUnlocked(before, null).second
        assertTrue(seen.isEmpty())
        val after = Achievements.all(
            Stats(played = 3, won = 3, streak = 3, maxStreak = 3, firstTry = 1, dist = List(9) { if (it == 0) 1 else 0 }),
            Stats(), r = Records(mapOf("conn_won" to 1)),
        )
        val fresh = AchievementWatch.newlyUnlocked(after, seen).first.map { it.title }
        assertEquals(listOf("Primeira palavra", "De primeira", "Rapidinho", "Três dias seguidos", "Conectado"), fresh)
    }

    // ---------- Easter egg ----------

    @Test
    fun letrasVoamEFicamNaTela() {
        val w = 720f; val h = 1560f; val tile = 68f
        val b = LetterBurst(1, 360f, 1480f, w, h, tile)
        assertEquals(26, b.letters.toSet().size)
        assertEquals(setOf(0, 1, 2), b.colors.toSet())
        b.advance(0.4f)
        // Saíram para cima, espalhadas para os dois lados.
        assertTrue(b.y.average() < 1300)
        assertTrue(b.x.any { it < 300f } && b.x.any { it > 420f })
        repeat(40) {
            b.advance(0.1f)
            for (i in b.x.indices) {
                assertTrue(b.x[i] in tile / 2 - 0.01f..w - tile / 2 + 0.01f)
                assertTrue(b.y[i] in tile / 2 - 0.01f..h - tile / 2 + 0.01f)
            }
        }
        // Depois de ~4 s a maioria já caiu no chão.
        assertTrue(b.y.count { it > h - tile } > b.y.size / 2)
    }

    // ---------- Widgets e recordes ----------

    @Test
    fun tamanhoDoWidget() {
        val spec = WidgetKit.Spec(180, 100, 220, 240)
        assertEquals(WidgetKit.Size.SMALL, WidgetKit.bucket(110, 110, spec))
        assertEquals(WidgetKit.Size.MEDIUM, WidgetKit.bucket(250, 110, spec))
        assertEquals(WidgetKit.Size.LARGE, WidgetKit.bucket(250, 260, spec))
        assertEquals(WidgetKit.Size.SMALL, WidgetKit.bucket(110, 400, spec))
    }

    @Test
    fun recordesTemTodosOsJogos() {
        val cards = recordCards(Stats(won = 5), Records(mapOf("mp_won" to 1, "mp_played" to 3, "ws_best" to 75, "sa_best" to 4, "syn_best" to 9)))
        assertEquals(
            listOf(Dest.INFINITO, Dest.CONEXOES, Dest.CACA, Dest.DEFINICAO, Dest.SINONIMOS, Dest.INTRUSO, Dest.ORTOGRAFIA, Dest.MESTRE, Dest.TRIVIA, Dest.MEMORIA, Dest.MATIZ, Dest.PODIO, Dest.AMPULHETA, Dest.SUDOKU, Dest.XADREZ, Dest.ARQUIVO, Dest.DESAFIAR),
            cards.map { it.dest },
        )
        val byDest = cards.associateBy { it.dest }
        assertEquals("1:15", byDest[Dest.CACA]!!.value)
        assertEquals("9", byDest[Dest.SINONIMOS]!!.value)
        assertEquals("vitória", byDest[Dest.DESAFIAR]!!.unit)
        assertEquals("em 3 partidas", byDest[Dest.DESAFIAR]!!.detail)
        assertNull("sem recorde mostra traço", byDest[Dest.INTRUSO]!!.value)
    }
}
