package com.dmwnezes.palavreiro

import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.Naval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NavalTest {
    @Test fun sorteioSempreValido() {
        val rnd = Random(7)
        repeat(300) { assertTrue(Naval.valid(Naval.place(rnd))) }
        // Navios encostados ou que quebram a linha não valem.
        assertFalse(Naval.valid(listOf(listOf(0, 1, 2, 3, 4), listOf(20, 21, 22, 23), listOf(40, 41, 42), listOf(60, 61, 62), listOf(11, 12))))
        assertFalse(Naval.valid(listOf(listOf(8, 9, 10, 11, 12), listOf(30, 31, 32, 33), listOf(50, 51, 52), listOf(70, 71, 72), listOf(90, 91))))
    }

    @Test fun computadorAfundaTudoEDificilGastaMenos() {
        val rnd = Random(3)
        val avg = Naval.Level.entries.map { lv ->
            (1..40).map {
                val f = Naval.place(rnd); val shots = mutableListOf<Int>(); val map = HashMap<Int, Boolean>()
                while (true) {
                    val sunk = f.indices.filter { k -> f[k].all { it in shots } }
                    val c = Naval.aim(map, sunk.map { f[it] }, Naval.LENS.filterIndexed { k, _ -> k !in sunk }, lv, rnd)
                    assertFalse(c in map)
                    val r = Naval.fire(f, shots, c); shots += c; map[c] = r.hit
                    if (r.all) break
                }
                shots.size
            }.average()
        }
        assertTrue("médias $avg", avg[2] < avg[1] && avg[1] < avg[0])
    }

    @Test fun mensagensIguaisAoSite() {
        val res = Msg.Res("abc", 3, 45, true, 2, listOf(44, 45, 46), false)
        assertEquals(res, Multi.decode(Multi.encode(res)))
        assertEquals(Msg.Shot("abc", 3, 45), Multi.decode("""{"t":"shot","id":"abc","n":3,"c":45}"""))
        assertEquals(Msg.Ready("x"), Multi.decode("""{"t":"ready","id":"x"}"""))
        val f = Naval.place(Random(1))
        assertEquals(Msg.Fleet("x", f), Multi.decode(Multi.encode(Msg.Fleet("x", f))))
        assertNotNull(Multi.parse("abcdefghijkl-n-1a"))
        assertNotNull(Multi.fromPasted("Bora? https://dmwnezes.com/termo/?mp=abcdefghijkl-n-1a"))
    }

    @Test fun rodadaComAmigo() {
        val h = "host"; val g = "conv"
        val first = Naval.firstId(0, h, g)
        assertEquals(h, first)
        val gf = Naval.place(Random(5))
        val msgs = mutableListOf<Msg>(Msg.Ready(h), Msg.Ready(g))
        // O anfitrião atira em todas as casas do convidado, o convidado só na água do canto.
        var st = Naval.state(msgs, h, g, first, h)
        assertEquals(h, Naval.turn(st, first, h, g))
        val targets = gf.flatten(); var k = 0; var gShot = 0
        for (c in targets) {
            msgs += Msg.Shot(h, k, c)
            val before = msgs.filterIsInstance<Msg.Shot>().filter { it.id == h && it.n < k }.map { it.c }
            val r = Naval.fire(gf, before, c)
            msgs += Msg.Res(g, k, c, r.hit, r.ship, if (r.sunk) gf[r.ship] else emptyList(), r.all); k++
            if (r.all) break
            val water = (0 until 100).first { x -> x !in msgs.filterIsInstance<Msg.Shot>().filter { it.id == g }.map { it.c } }
            msgs += Msg.Shot(g, k, water); msgs += Msg.Res(h, k, water, false, -1, emptyList(), false); k++; gShot++
        }
        st = Naval.state(msgs, h, g, first, h)
        assertEquals(h, st.winner)
        assertTrue(st.over)
        // Tiro fora de vez é ignorado.
        val bad = Naval.state(listOf(Msg.Ready(h), Msg.Ready(g), Msg.Shot(g, 0, 5)), h, g, first, h)
        assertTrue(bad.shots.isEmpty())
    }
}
