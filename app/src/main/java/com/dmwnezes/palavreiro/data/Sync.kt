package com.dmwnezes.palavreiro.data

import com.dmwnezes.palavreiro.game.Records
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Código para levar o progresso entre o app e o site (mesmo formato nos dois):
 * "PV1-" + base64url sem "=" do JSON com estatísticas, contadores, atividade, resultados e histórico.
 * Importar MESCLA: nunca apaga o que já existe aqui.
 */
object Sync {
    private const val NO_WIN = -999999L
    /** Nome do modo no código (o Termo do dia é "termo" no site). */
    private val MODES = mapOf("termo" to Mode.DIARIO, "infinito" to Mode.INFINITO, "dueto" to Mode.DUETO, "quarteto" to Mode.QUARTETO)

    private fun statsJson(s: Stats) = JSONObject()
        .put("played", s.played).put("won", s.won).put("streak", s.streak).put("maxStreak", s.maxStreak)
        .put("lastWinDay", if (s.lastWinDay < -1000) NO_WIN else s.lastWinDay)
        .put("firstTry", s.firstTry).put("dist", JSONArray(s.dist))

    private fun statsFrom(o: JSONObject): Stats {
        val d = o.optJSONArray("dist")
        val lw = o.optLong("lastWinDay", NO_WIN)
        return Stats(
            played = o.optInt("played"), won = o.optInt("won"), streak = o.optInt("streak"), maxStreak = o.optInt("maxStreak"),
            lastWinDay = if (lw < -1000) Long.MIN_VALUE else lw, firstTry = o.optInt("firstTry"),
            dist = List(9) { i -> d?.optInt(i) ?: 0 },
        )
    }

    fun export(store: Store): String {
        val stats = JSONObject().apply { MODES.forEach { (k, m) -> put(k, statsJson(store.stats(m))) } }
        val n = JSONObject().apply { Records.KEYS.forEach { put(it, store.int(it)) } }
        val o = JSONObject()
            .put("v", 1)
            .put("stats", stats)
            .put("n", n)
            .put("activity", JSONObject(store.activity()))
            .put("termoDays", JSONObject(store.termoResults()))
            .put("results", store.json("results"))
            .put("history", store.history())
        return "PV1-" + Base64.getUrlEncoder().withoutPadding().encodeToString(o.toString().toByteArray(Charsets.UTF_8))
    }

    /** Mescla o código no progresso local. Devolve false se o código for inválido. */
    fun import(store: Store, code: String): Boolean {
        val clean = code.filterNot { it.isWhitespace() }
        if (!clean.startsWith("PV1-")) return false
        val d = runCatching {
            JSONObject(String(Base64.getUrlDecoder().decode(clean.removePrefix("PV1-").trimEnd('=')), Charsets.UTF_8))
        }.getOrNull() ?: return false
        if (d.optInt("v") != 1) return false

        d.optJSONObject("stats")?.let { st ->
            MODES.forEach { (k, m) ->
                st.optJSONObject(k)?.let { inc -> val s = statsFrom(inc); if (s.played > store.stats(m).played) store.saveStats(m, s) }
            }
        }
        d.optJSONObject("n")?.let { n ->
            Records.KEYS.forEach { k ->
                if (!n.has(k)) return@forEach
                val inc = n.optInt(k); val cur = store.int(k)
                if (k in Records.LOWER_IS_BETTER) { if (inc > 0 && (cur <= 0 || inc < cur)) store.setInt(k, inc) }
                else if (inc > cur) store.setInt(k, inc)
            }
        }
        d.optJSONObject("activity")?.let { a ->
            val cur = store.activity().toMutableMap()
            a.keys().forEach { day -> val v = a.optInt(day); if (v > (cur[day] ?: 0)) cur[day] = v }
            store.setActivity(cur)
        }
        d.optJSONObject("termoDays")?.let { t ->
            val cur = store.termoResults()
            t.keys().forEach { day -> val v = t.optString(day); if (day !in cur && (v == "w" || v == "l")) store.logTermo(day, v == "w") }
        }
        d.optJSONObject("results")?.let { r ->
            val cur = store.json("results")
            r.keys().forEach { k -> val v = r.optString(k); if (!cur.has(k) && (v == "w" || v == "l")) cur.put(k, v) }
            store.setJson("results", cur)
        }
        d.optJSONObject("history")?.let { h ->
            val cur = store.history()
            h.keys().forEach { day ->
                val inc = h.optJSONObject(day) ?: return@forEach
                val mine = cur.optJSONObject(day) ?: JSONObject()
                inc.keys().forEach { f -> if (!mine.has(f)) mine.put(f, inc.optInt(f)) }
                cur.put(day, mine)
            }
            store.setJson("history", cur)
        }
        return true
    }
}
