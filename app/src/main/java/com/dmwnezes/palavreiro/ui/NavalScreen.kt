package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.Naval
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ======================================================================
// Peças da tela (solo e com amigo): mar 10×10 e as frotas com cápsulas
// ======================================================================

/**
 * Mar 10×10 com letras e números. [ships] = navios desenhados (os meus, ou os afundados do outro);
 * [shots] = casa → acerto?; [sunk] = casas de navios afundados; [mine] = mar meu (acerto em vermelho).
 */
@Composable
internal fun NavalBoard(
    ships: List<List<Int>>, shots: Map<Int, Boolean>, sunk: Set<Int>, mine: Boolean, modifier: Modifier = Modifier,
    aim: Int = -1, labels: Boolean = true, enabled: Boolean = false, onTap: (Int) -> Unit = {},
) {
    val owner = HashMap<Int, Int>()
    ships.forEachIndexed { k, s -> s.forEach { owner[it] = k } }
    BoxWithConstraints(modifier) {
        val gap = if (maxWidth < 220.dp) 1.5.dp else 3.dp
        val cell = if (labels) (maxWidth - gap * 10) / 10.65f else (maxWidth - gap * 9) / 10f
        val font = (cell.value * 0.46f).sp
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            if (labels) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Spacer(Modifier.width(cell * 0.65f))
                for (c in 0 until Naval.N) Box(Modifier.width(cell).height(cell * 0.6f), contentAlignment = Alignment.Center) {
                    Text("ABCDEFGHIJ"[c].toString(), color = Night.muted, fontSize = (cell.value * 0.32f).sp, fontWeight = FontWeight.SemiBold)
                }
            }
            for (r in 0 until Naval.N) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (labels) Box(Modifier.width(cell * 0.65f).height(cell), contentAlignment = Alignment.Center) {
                    Text("${r + 1}", color = Night.muted, fontSize = (cell.value * 0.32f).sp, fontWeight = FontWeight.SemiBold)
                }
                for (c in 0 until Naval.N) {
                    val i = r * Naval.N + c
                    val shot = shots[i]
                    val isShip = i in owner
                    val isSunk = i in sunk
                    val bg = when {
                        isSunk -> Night.purple
                        shot == true -> if (mine) Night.red else Night.correct
                        shot == false -> Night.keyAbsent
                        isShip -> Night.flagBlue
                        else -> Night.surfaceHigh
                    }
                    // Cápsula: cantos redondos só nas pontas do navio.
                    val round = cell / 2
                    val q = if (cell < 20.dp) 2.dp else 4.dp
                    val base = if (cell < 20.dp) 3.dp else 8.dp
                    val shape = if (isShip) {
                        val k = owner[i]
                        fun has(x: Int) = owner[x] == k
                        val up = r > 0 && has(i - Naval.N); val dn = r < Naval.N - 1 && has(i + Naval.N)
                        val lf = c > 0 && has(i - 1); val rt = c < Naval.N - 1 && has(i + 1)
                        RoundedCornerShape(if (!up && !lf) round else q, if (!up && !rt) round else q, if (!dn && !rt) round else q, if (!dn && !lf) round else q)
                    } else RoundedCornerShape(base)
                    Box(
                        Modifier.size(cell).clip(shape).background(bg)
                            .then(if (i == aim) Modifier.border(3.dp, Night.present, shape) else Modifier)
                            .clickable(enabled = enabled && shot == null && !isSunk) { onTap(i) }
                            .semantics { contentDescription = Naval.name(i) + when { isSunk -> ", afundado"; shot == true -> ", acerto"; shot == false -> ", água"; else -> "" } },
                        contentAlignment = Alignment.Center,
                    ) {
                        val t = if (shot == true || isSunk) "✕" else if (shot == false) "•" else ""
                        if (t.isNotEmpty()) Text(
                            t, color = if (shot == false) Night.muted else DarkInk, fontSize = font, lineHeight = font,
                            fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

private val DarkInk = androidx.compose.ui.graphics.Color(0xFF1A1438)

/** Situação de cada navio: (casas atingidas, afundado). Do outro só sei os afundados. */
internal fun navalFleetState(fleet: List<List<Int>>?, hitSet: Set<Int>, sunkIdx: Set<Int>): List<Pair<Int, Boolean>> =
    fleet?.map { s -> s.count { it in hitSet }.let { it to (it == s.size) } } ?: Naval.LENS.indices.map { 0 to (it in sunkIdx) }

/** Lista da frota (design D): nome (opcional) e a cápsula de cada navio. */
@Composable
internal fun NavalFleet(title: String, state: List<Pair<Int, Boolean>>, mine: Boolean, names: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (names) 6.dp else 5.dp)) {
        Text(title.uppercase(), color = Night.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, maxLines = 1)
        Naval.SHIPS.forEachIndexed { k, (nm, len) ->
            val (hits, sunk) = state.getOrElse(k) { 0 to false }
            Column(
                Modifier.semantics { contentDescription = "$nm: " + when { sunk -> "afundado"; mine && hits > 0 -> "$hits de $len atingidas"; else -> "no mar" } },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (names) Text(
                    nm, color = if (sunk) Night.purple else Night.text, fontSize = 13.sp, maxLines = 1,
                    textDecoration = if (sunk) TextDecoration.LineThrough else null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (j in 0 until len) {
                        val c = when { sunk -> Night.purple; mine && j < hits -> Night.red; mine -> Night.flagBlue; else -> Night.outline }
                        val shape = when (j) { 0 -> RoundedCornerShape(6.dp, 2.dp, 2.dp, 6.dp); len - 1 -> RoundedCornerShape(2.dp, 6.dp, 6.dp, 2.dp); else -> RoundedCornerShape(2.dp) }
                        Box(Modifier.size(12.dp, 9.dp).clip(shape).background(c))
                    }
                }
            }
        }
    }
}

/** Um mar visto de quem atirou: tiros, casas afundadas e quais navios afundaram. */
private class Sea(val shots: Map<Int, Boolean>, val sunk: Set<Int>, val sunkIdx: Set<Int>, val hit: Set<Int>) {
    val left get() = Naval.LENS.size - sunkIdx.size
}
private fun seaOf(fleet: List<List<Int>>, list: List<Int>): Sea {
    val set = list.toHashSet()
    val shots = list.associateWith { c -> fleet.any { c in it } }
    val idx = fleet.indices.filter { k -> fleet[k].all { it in set } }.toSet()
    return Sea(shots, idx.flatMap { fleet[it] }.toSet(), idx, set)
}

/** Moldura do jogo no celular (layout A + frotas ao lado): mar do outro, meu mar pequeno e as duas frotas. */
@Composable
private fun NavalLayout(
    status: String, statusMine: Boolean, oppLabel: String, oppShort: String,
    foeShips: List<List<Int>>, foe: Sea, myFleet: List<List<Int>>, mine: Sea, aim: Int, canTap: Boolean, onTap: (Int) -> Unit,
    footer: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(status, color = if (statusMine) Night.correct else Night.muted, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 2)
            Text("$oppShort ${foe.left} · Você ${mine.left}", color = Night.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surface).padding(horizontal = 8.dp, vertical = 12.dp)) {
            Text("Mar $oppLabel", color = Night.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
            NavalBoard(foeShips, foe.shots, foe.sunk, mine = false, modifier = Modifier.fillMaxWidth(), aim = aim, enabled = canTap, onTap = onTap)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1.15f).clip(Shapes.card).background(Night.surface).padding(12.dp)) {
                Text("Sua frota", color = Night.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                NavalBoard(myFleet, mine.shots, mine.sunk, mine = true, modifier = Modifier.fillMaxWidth(), labels = false)
            }
            Column(Modifier.weight(1f).clip(Shapes.card).background(Night.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NavalFleet(oppShort, navalFleetState(null, emptySet(), foe.sunkIdx), mine = false, names = false)
                NavalFleet("Você", navalFleetState(myFleet, mine.hit, mine.sunkIdx), mine = true, names = false)
            }
        }
        footer()
        Spacer(Modifier.height(10.dp))
    }
}

// ======================================================================
// Solo: contra o computador
// ======================================================================

private const val NAVAL_HELP = "Afunde os 5 navios do computador antes que ele afunde os seus.\n\n" +
    "Toque numa casa do mar dele para mirar e toque de novo para atirar: verde é acerto, • é água e lilás é navio afundado. " +
    "Vocês atiram um de cada vez. Os navios são retos e nunca encostam um no outro, nem na diagonal.\n\n" +
    "Fácil atira quase ao acaso, Médio mira em volta dos acertos e Difícil calcula onde é mais provável haver navio."

/** Partida salva do solo. [ps] = meus tiros; [cs] = tiros do computador; [counted] = resultado já contado. */
data class NavalSave(
    val level: Naval.Level, val me: List<List<Int>>, val ai: List<List<Int>>, val ps: List<Int>, val cs: List<Int>,
    val resigned: Boolean = false, val counted: Boolean = false,
) {
    fun encode(): String = JSONObject().put("l", level.key).put("me", fl(me)).put("ai", fl(ai))
        .put("ps", JSONArray(ps)).put("cs", JSONArray(cs)).put("r", resigned).put("k", counted).toString()
    companion object {
        private fun fl(f: List<List<Int>>) = JSONArray().apply { f.forEach { put(JSONArray(it)) } }
        private fun ints(a: JSONArray) = (0 until a.length()).map { a.getInt(it) }
        fun decode(t: String?): NavalSave? = runCatching {
            val o = JSONObject(t ?: return null)
            fun fleet(k: String) = o.getJSONArray(k).let { a -> (0 until a.length()).map { ints(a.getJSONArray(it)) } }
            NavalSave(
                Naval.Level.entries.first { it.key == o.getString("l") }, fleet("me"), fleet("ai"),
                ints(o.getJSONArray("ps")), ints(o.getJSONArray("cs")), o.optBoolean("r"), o.optBoolean("k"),
            ).takeIf { Naval.valid(it.me) && Naval.valid(it.ai) }
        }.getOrNull()
    }
}

@Composable
private fun LevelPicker(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(Shapes.pill).background(Night.surface).padding(4.dp)) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(Shapes.pill).background(if (on) Night.accent else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onPick(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(l, color = if (on) DarkInk else Night.muted, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
        }
    }
}

/** Batalha Naval contra o computador (Fácil / Médio / Difícil), com a partida salva ao sair. */
@Composable
fun NavalScreen(store: Store?, feedback: Feedback?, onBack: () -> Unit, initial: NavalSave? = null, rnd: Random = Random.Default) {
    var game by remember { mutableStateOf(initial ?: NavalSave.decode(store?.text("naval_save"))) }
    var level by remember { mutableStateOf(game?.level ?: runCatching { Naval.Level.valueOf(store?.text("naval_level") ?: "") }.getOrDefault(Naval.Level.MEDIO)) }
    var preview by remember { mutableStateOf(Naval.place(rnd)) }
    var showHelp by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var aim by remember { mutableIntStateOf(-1) }
    val toast = rememberToast()
    fun persist(g: NavalSave?) { game = g; store?.setText("naval_save", g?.encode()) }

    val g = game
    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Batalha Naval", onBack, onHelp = { showHelp = true })
            if (g == null) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(8.dp))
                    Text("Nível do computador", color = Night.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    LevelPicker(Naval.Level.entries.map { it.label }, level.ordinal) { level = Naval.Level.entries[it] }
                    Spacer(Modifier.height(16.dp))
                    Text("Sua frota", color = Night.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surface).padding(10.dp)) {
                        NavalBoard(preview, emptyMap(), emptySet(), mine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Sortear de novo", Night.surfaceHigh, Night.text, Modifier.weight(1f)) { preview = Naval.place(rnd); feedback?.type() }
                        PillButton("Começar", Night.correct, modifier = Modifier.weight(1f)) {
                            store?.setText("naval_level", level.name)
                            persist(NavalSave(level, preview, Naval.place(rnd), emptyList(), emptyList()))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Vitórias: " + Naval.Level.entries.joinToString(" · ") { "${it.label} ${store?.int("naval_won_${it.key}") ?: 0}" },
                        color = Night.muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                }
            } else {
                val foe = seaOf(g.ai, g.ps)
                val mine = seaOf(g.me, g.cs)
                val outcome = when { g.resigned -> "opp"; foe.left == 0 -> "me"; mine.left == 0 -> "opp"; else -> null }
                val myTurn = outcome == null && g.ps.size == g.cs.size
                // Vez do computador.
                LaunchedEffect(g.ps.size, g.cs.size, outcome) {
                    if (outcome != null || g.cs.size >= g.ps.size) return@LaunchedEffect
                    delay(650)
                    val left = Naval.LENS.filterIndexed { k, _ -> k !in mine.sunkIdx }
                    val c = Naval.aim(mine.shots, mine.sunkIdx.map { g.me[it] }, left, g.level, rnd)
                    val r = Naval.fire(g.me, g.cs, c)
                    if (r.sunk) toast.show("O computador afundou seu ${Naval.SHIPS[r.ship].first.lowercase()}", 1800)
                    if (r.hit) feedback?.invalid() else feedback?.type()
                    persist(g.copy(cs = g.cs + c))
                }
                LaunchedEffect(outcome) {
                    val o = outcome ?: return@LaunchedEffect
                    if (!g.counted) {
                        if (o == "me") store?.add("naval_won_${g.level.key}")
                        store?.logActivity()
                        persist(g.copy(counted = true))
                        if (o == "me") feedback?.win() else feedback?.lose()
                    }
                    delay(900)
                    showEnd = true
                }
                val live by rememberUpdatedState(g)
                Box(Modifier.weight(1f)) {
                    NavalLayout(
                        status = when (outcome) {
                            "me" -> "Você venceu! ⚓"; "opp" -> if (g.resigned) "Você desistiu" else "O computador venceu"
                            else -> if (myTurn) (if (aim >= 0) "Toque de novo em ${Naval.name(aim)}" else "Sua vez · escolha uma casa") else "Computador mirando…"
                        },
                        statusMine = myTurn, oppLabel = "do computador", oppShort = "Computador",
                        foeShips = foe.sunkIdx.map { g.ai[it] } + (if (outcome != null) g.ai else emptyList()), foe = foe,
                        myFleet = g.me, mine = mine, aim = aim, canTap = myTurn,
                        onTap = { c ->
                            val cur = live
                            if (c in cur.ps || cur.ps.size != cur.cs.size) return@NavalLayout
                            if (aim != c) { aim = c; feedback?.type(); return@NavalLayout }
                            aim = -1
                            val r = Naval.fire(cur.ai, cur.ps, c)
                            if (r.sunk) toast.show("Você afundou o ${Naval.SHIPS[r.ship].first.lowercase()}!", 1800)
                            if (r.hit) feedback?.turn() else feedback?.type()
                            persist(cur.copy(ps = cur.ps + c))
                        },
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (outcome == null) PillButton("Desistir", Night.surfaceHigh, Night.text, Modifier.weight(1f)) { confirm = true }
                            else PillButton("Nova partida", Night.correct, modifier = Modifier.weight(1f)) { showEnd = false; preview = Naval.place(rnd); persist(null) }
                        }
                    }
                }
                if (confirm && outcome == null) {
                    BottomSheet({ confirm = false }) {
                        SheetTitle("Desistir da partida?", "O computador ganha esta.")
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Continuar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) { confirm = false }
                            PillButton("Desistir", Night.red, modifier = Modifier.weight(1f)) { confirm = false; persist(g.copy(resigned = true)) }
                        }
                    }
                }
                if (showEnd && outcome != null) {
                    val hits = g.ps.count { c -> g.ai.any { c in it } }
                    BottomSheet({ showEnd = false }) {
                        SheetTitle(if (outcome == "me") "Você venceu! 🏆" else "O computador venceu", "Nível ${g.level.label}" + if (g.resigned) " · você desistiu" else "")
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem("${g.ps.size}", "Tiros")
                            StatItem("$hits", "Acertos")
                            StatItem("${store?.int("naval_won_${g.level.key}") ?: 0}", "Vitórias no ${g.level.label}")
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Ver mares", Night.surfaceHigh, Night.text, Modifier.weight(1f)) { showEnd = false }
                            PillButton("Nova partida", Night.correct, modifier = Modifier.weight(1f)) { showEnd = false; preview = Naval.place(rnd); persist(null) }
                        }
                    }
                }
            }
        }
        ToastView(toast, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 70.dp))
        if (showHelp) {
            BottomSheet({ showHelp = false }) {
                SheetTitle("Como jogar Batalha Naval")
                Spacer(Modifier.height(12.dp))
                Text(NAVAL_HELP, color = Night.text, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                PillButton("Entendi", Night.correct, modifier = Modifier.fillMaxWidth()) { showHelp = false }
            }
        }
    }
}

// ======================================================================
// Com amigo (modo n): mesmas mensagens do site (js/multi-naval.js)
// ======================================================================

/** Batalha Naval com amigo. [msgs] = mensagens da rodada na ordem do tópico. */
@Composable
internal fun NavalPlay(msgs: List<Msg>, d: Duel, initialFleet: List<List<Int>>? = null) {
    val now = rememberNow(d.clock, d.round, 200)
    val opp = d.oppId ?: "?"
    val hostId = if (d.isHost) d.myId else opp
    val guestId = if (d.isHost) opp else d.myId
    val first = Naval.firstId(d.n, hostId, guestId)
    // shortcut: a frota fica só na memória da tela; se o app for fechado no meio da rodada, ela é sorteada de novo.
    var fleet by remember(d.round) { mutableStateOf(initialFleet ?: Naval.place()) }
    val st = Naval.state(msgs, hostId, guestId, first, d.myId)
    val counting = now < d.localStart
    val placing = d.myId !in st.ready
    val turn = Naval.turn(st, first, hostId, guestId)
    var sentAt by remember(d.round) { mutableIntStateOf(-1) }
    var aim by remember(d.round) { mutableIntStateOf(-1) }
    var showEnd by remember(d.round) { mutableStateOf(false) }
    var confirm by remember(d.round) { mutableStateOf(false) }
    var fleetSent by remember(d.round) { mutableStateOf(false) }
    val answered = remember(d.round) { HashSet<Int>() }
    val myTurn = !counting && turn == d.myId && sentAt != st.shots.size
    val outcome = when {
        st.winner != null -> if (st.winner == d.myId) "me" else "opp"
        d.oppLeft -> "me"
        else -> null
    }

    // Responde sozinho os tiros que levei (o amigo nunca vê a minha frota antes do fim).
    LaunchedEffect(st.shots.size) {
        st.shots.forEachIndexed { k, x ->
            if (x.by != d.myId && x.res == null && answered.add(k)) {
                val before = st.shots.take(k).filter { it.by != d.myId }.map { it.c }
                val f = Naval.fire(fleet, before, x.c)
                d.send(Msg.Res(d.myId, k, x.c, f.hit, f.ship, if (f.sunk) fleet[f.ship] else emptyList(), f.all))
            }
        }
    }
    // Som do tiro respondido.
    val resolvedCount = st.shots.count { it.res != null }
    LaunchedEffect(resolvedCount) {
        val x = st.shots.lastOrNull { it.res != null } ?: return@LaunchedEffect
        if (resolvedCount == 0) return@LaunchedEffect
        val r = x.res!!
        if (x.by == d.myId) { if (r.hit) d.feedback?.turn() else d.feedback?.type() }
        else { if (r.hit) d.feedback?.invalid() else d.feedback?.type() }
    }
    LaunchedEffect(outcome, d.round) {
        val o = outcome ?: return@LaunchedEffect
        d.onDecided(o)
        if (!fleetSent && st.winner != null) { fleetSent = true; d.send(Msg.Fleet(d.myId, fleet)) }
        if (o == "me") d.feedback?.win() else d.feedback?.lose()
        delay(1300)
        showEnd = true
    }

    // Os dois mares do meu ponto de vista.
    val foeShots = HashMap<Int, Boolean>(); val foeSunk = HashSet<Int>(); val foeIdx = HashSet<Int>(); val foeShips = mutableListOf<List<Int>>()
    val myShots = HashMap<Int, Boolean>()
    st.shots.forEach { x ->
        val r = x.res ?: return@forEach
        if (x.by == d.myId) {
            foeShots[x.c] = r.hit
            if (r.sunk.isNotEmpty()) { foeSunk += r.sunk; foeShips += r.sunk; if (r.ship >= 0) foeIdx += r.ship }
        } else myShots[x.c] = r.hit
    }
    val mySea = seaOf(fleet, myShots.keys.toList())
    val foe = Sea(foeShots, foeSunk, foeIdx, emptySet())
    val live by rememberUpdatedState(Triple(st, myTurn, aim))

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Você × ${d.oppName}", d.onLeave, trailing = { ReactAnchor(d.reactions); SeriesPill(d.series) })
            val status = when {
                outcome == "me" -> if (st.resigned != null) "${d.oppName} desistiu. Você venceu!" else if (st.winner == null) "${d.oppName} saiu. Você venceu!" else "Você venceu! ⚓"
                outcome == "opp" -> if (st.resigned == d.myId) "Você desistiu" else "${d.oppName} venceu"
                !d.online -> "Reconectando…"
                counting -> if (first == d.myId) "Você atira primeiro" else "${d.oppName} atira primeiro"
                placing -> "Posicione sua frota"
                !st.bothReady -> "Esperando ${d.oppName} posicionar…"
                myTurn -> if (aim >= 0) "Toque de novo em ${Naval.name(aim)}" else "Sua vez · escolha uma casa"
                turn == d.myId -> "Atirando…"
                else -> "Vez de ${d.oppName}"
            }
            Box(Modifier.weight(1f)) {
                NavalLayout(
                    status = status, statusMine = myTurn || outcome == "me", oppLabel = "de ${d.oppName}", oppShort = d.oppName,
                    foeShips = foeShips + (st.oppFleet ?: emptyList()), foe = foe, myFleet = fleet, mine = mySea, aim = aim, canTap = myTurn,
                    onTap = { c ->
                        val (s, can, a) = live
                        if (!can || s.shots.any { it.by == d.myId && it.c == c }) return@NavalLayout
                        if (a != c) { aim = c; d.feedback?.type(); return@NavalLayout }
                        aim = -1; sentAt = s.shots.size
                        d.send(Msg.Shot(d.myId, s.shots.size, c))
                    },
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        when {
                            outcome != null -> if (!showEnd) PillButton("Ver resultado", Night.correct, modifier = Modifier.weight(1f)) { showEnd = true }
                            placing -> {
                                PillButton("Sortear de novo", Night.surfaceHigh, Night.text, Modifier.weight(1f), enabled = !counting) { fleet = Naval.place(); d.feedback?.type() }
                                PillButton("Pronto", Night.correct, modifier = Modifier.weight(1f), enabled = !counting) { d.send(Msg.Ready(d.myId)) }
                            }
                            else -> PillButton("Desistir", Night.surfaceHigh, Night.text, Modifier.weight(1f), enabled = !counting) { confirm = true }
                        }
                    }
                }
            }
        }
        if (confirm && outcome == null) {
            BottomSheet({ confirm = false }) {
                SheetTitle("Desistir da rodada?", "${d.oppName} ganha esta rodada.")
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Continuar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) { confirm = false }
                    PillButton("Desistir", Night.red, modifier = Modifier.weight(1f)) { confirm = false; d.send(Msg.Resign(d.myId)) }
                }
            }
        }
        if (outcome != null && showEnd) {
            val mineShots = st.shots.filter { it.by == d.myId && it.res != null }
            val detail = "${mineShots.size} tiros, ${mineShots.count { it.res!!.hit }} acertos"
            val sub = when {
                st.resigned == opp -> "${d.oppName} desistiu"
                st.resigned == d.myId -> "Você desistiu"
                st.winner == null -> "${d.oppName} saiu da partida"
                else -> detail
            }
            SeriesEndSheet(
                mode = Multi.NAVAL, outcome = outcome, oppName = d.oppName, subtitle = sub,
                myLine = "${mySea.left} navios no mar", oppLine = "${foe.left} navios no mar",
                isHost = d.isHost, series = d.series, onAgain = d.onAgain, onLeave = d.onLeave, onClose = { showEnd = false },
                story = MatchStory.series(Multi.NAVAL, d.myName, d.oppName, d.series, outcome, detail),
                shareDetail = detail, myName = d.myName, next = d.next,
            )
        }
    }
}
