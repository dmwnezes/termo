package com.dmwnezes.palavreiro.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.DefineDuel
import com.dmwnezes.palavreiro.game.Definition
import com.dmwnezes.palavreiro.game.HuntDuel
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.SearchTheme
import com.dmwnezes.palavreiro.game.WordSearchData
import com.dmwnezes.palavreiro.game.TermoGame
import com.dmwnezes.palavreiro.game.Words
import com.dmwnezes.palavreiro.system.Ntfy
import com.dmwnezes.palavreiro.system.RoomKeeper
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Phase { CREATE, WAITING, JOIN, WAIT_START, FULL, INVALID, PLAYING }

private val Ink = Color(0xFF14102C)

/**
 * Jogar com amigo: cria uma sala (nome + modo), gera um link e os dois jogam ao mesmo tempo.
 * Você vê as cores das tentativas do amigo (sem as letras); quem acertar primeiro ganha.
 * [joinCode] = código do link (?mp=...) quando você foi convidado.
 */
@OptIn(DelicateCoroutinesApi::class)
@Composable
fun MultiScreen(
    words: Words,
    store: Store?,
    ntfy: Ntfy,
    feedback: Feedback?,
    onBack: () -> Unit,
    joinCode: String? = null,
    meanings: Map<String, String> = emptyMap(),
    /** Sala guardada para retomar (o app foi fechado enquanto você mandava o link). */
    resume: Multi.Session? = null,
    /** Temas do Caça-Palavras (caca.txt + caca-extra.txt) e definições do Qual é a Palavra?; null = lê dos assets. */
    themes: List<SearchTheme>? = null,
    definitions: List<Definition>? = null,
) {
    val context = LocalContext.current
    val themePool = remember(themes) {
        themes ?: runCatching {
            fun asset(n: String) = context.assets.open(n).bufferedReader().use { it.readText() }
            WordSearchData.parse(asset("caca.txt")) + WordSearchData.parse(asset("caca-extra.txt"))
        }.getOrDefault(emptyList())
    }
    val defs = remember(definitions) {
        definitions ?: runCatching { DefineDuel.parse(context.assets.open("definicoes.txt").bufferedReader().use { it.readText() }) }.getOrDefault(emptyList())
    }
    val scope = rememberCoroutineScope()
    val myId = remember { resume?.id ?: Multi.randomId(8) }
    val joinRoom = remember(joinCode) { joinCode?.let(Multi::parse) }
    var phase by remember {
        mutableStateOf(
            when {
                resume != null -> if (resume.host) Phase.WAITING else Phase.WAIT_START
                joinCode == null -> Phase.CREATE
                joinRoom == null -> Phase.INVALID
                else -> Phase.JOIN
            }
        )
    }
    var room by remember { mutableStateOf(resume?.room ?: joinRoom) }
    var isHost by remember { mutableStateOf(resume?.host ?: (joinCode == null)) }
    var name by remember { mutableStateOf(resume?.name ?: store?.text("mp_name").orEmpty()) }
    // Quando a sala foi criada (para os 10 minutos em que ela fica guardada).
    var keptAt by remember { mutableLongStateOf(resume?.at ?: 0L) }
    var expired by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(false) }
    var modeCode by remember { mutableStateOf(store?.text("mp_mode")?.firstOrNull()?.takeIf { it in Multi.CODES } ?: 'd') }
    /** Rodadas escolhidas para cada modo (guardadas em "mp_rounds_<modo>"). */
    fun savedRounds(code: Char): Int = Multi.rounds(code, store?.int("mp_rounds_$code")?.takeIf { it > 0 })
    var pickRounds by remember { mutableIntStateOf(savedRounds(modeCode)) }
    // Rodadas da partida em jogo (melhor de N ou N rodadas): vêm do hello do anfitrião e mudam no "again".
    var rounds by remember { mutableIntStateOf(Multi.defaultRounds(joinRoom?.mode ?: resume?.room?.mode ?: modeCode)) }
    // Link antigo de um jogo que saiu (Bomba-Relógio).
    val removedLink = remember(joinCode) { joinRoom == null && Multi.removedGame(joinCode) }
    var scanning by remember { mutableStateOf(false) }
    var oppId by remember { mutableStateOf<String?>(null) }
    var oppName by remember { mutableStateOf<String?>(null) }
    var joined by remember { mutableStateOf(resume?.host == false) }
    var startedOthers by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(true) }
    // Rodada atual (muda na revanche).
    var round by remember { mutableIntStateOf(0) }
    var localStart by remember { mutableLongStateOf(0L) }
    val oppRows = remember { mutableStateListOf<String>() }
    val ends = remember { mutableStateListOf<Msg.End>() }
    // Jogos próprios: mensagens da rodada atual (zeradas a cada start/again), com a hora de chegada.
    val roundMsgs = remember { mutableStateListOf<com.dmwnezes.palavreiro.game.Timed>() }
    // Caça-Palavras: grade de cada RODADA (start/again da sala) e os temas já usados (anfitrião).
    val grids = remember { mutableStateMapOf<Int, Msg.Grid>() }
    val usedThemes = remember { mutableStateListOf<String>() }
    var oppLeft by remember { mutableStateOf(false) }
    var recordedRound by remember { mutableIntStateOf(-1) }
    var showEnd by remember { mutableStateOf(false) }
    var replayTrigger by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Melhor de 3: placar da série e a última rodada já contada nele.
    var series by remember { mutableStateOf(Multi.Series(best = rounds)) }
    var scoredRound by remember { mutableIntStateOf(-1) }
    // Revanche: modo e rodadas escolhidos pelo anfitrião para o próximo jogo (null = os mesmos).
    var nextMode by remember { mutableStateOf<Char?>(null) }
    var nextRounds by remember { mutableStateOf<Int?>(null) }
    val reactions = rememberReactionState()
    val toast = rememberToast()

    fun send(m: Msg) { val r = room ?: return; scope.launch { ntfy.publish(r.topic, Multi.encode(m)) } }
    /** Manda em sequência (a ordem no tópico importa: start/again antes do grid). */
    fun sendAll(vararg ms: Msg) { val r = room ?: return; scope.launch { ms.forEach { ntfy.publish(r.topic, Multi.encode(it)) } } }
    reactions.onSend = { e -> send(Msg.React(myId, e, System.currentTimeMillis())) }
    /** Anfitrião, Caça-Palavras: grade da RODADA [n] com um tema que ainda não saiu na sala. */
    fun gridFor(n: Int): Msg.Grid? = runCatching {
        HuntDuel.build(themePool, usedThemes.toSet(), n).also { usedThemes += it.theme }
    }.getOrNull()
    /** Anfitrião: revanche no modo escolhido (trocar de modo ou de rodadas zera a série). */
    fun again() {
        val r = room ?: return
        val next = nextMode ?: r.mode
        val nr = nextRounds ?: if (next == r.mode) rounds else savedRounds(next)
        val m = Multi.againFor(r.mode, next, series.over, Multi.newSeed(), System.currentTimeMillis(), rounds, nr)
        // RODADA do próximo jogo = quantos start/again a sala já teve.
        val g = if (next == Multi.HUNT) gridFor(round) else null
        if (g != null) sendAll(m, g) else send(m)
    }

    /** Guarda a sala por 10 minutos (sobrevive ao app fechado) e liga o aviso. */
    fun keep() {
        val r = room ?: return
        if (keptAt == 0L) keptAt = System.currentTimeMillis()
        store?.let { RoomKeeper.save(context, it, Multi.Session(r.code, isHost, myId, name.trim(), keptAt)) }
    }
    fun forget() { store?.let { RoomKeeper.clear(context, it) } }
    /** Saída de propósito: avisa o amigo, esquece a sala e volta. */
    fun leave() {
        if (!left) {
            left = true
            room?.let { r -> if (joined || isHost) if (phase != Phase.CREATE && phase != Phase.INVALID) GlobalScope.launch { ntfy.publish(r.topic, Multi.encode(Msg.Bye(myId))) } }
        }
        forget()
        onBack()
    }

    fun beginRound(at: Long, newSeries: Boolean) {
        forget() // a partida começou: a sala não precisa mais ficar guardada
        if (newSeries) series = Multi.Series(best = rounds)
        val t = System.currentTimeMillis()
        localStart = if (kotlin.math.abs(t - at) < 2500) at + 3000 else t + 3000
        oppRows.clear(); ends.clear(); roundMsgs.clear(); showEnd = false
        nextMode = null; nextRounds = null; reactions.open = false
        round++
        phase = Phase.PLAYING
    }

    fun process(m: Msg) {
        when (m) {
            is Msg.Hello -> if (m.id == myId) {
                // Sala retomada pelo anfitrião: as rodadas estão no hello dele.
                if (m.host && phase != Phase.PLAYING) room?.let { rounds = Multi.rounds(it.mode, m.rounds); series = Multi.Series(best = rounds) }
            } else {
                if (m.host) {
                    if (!isHost) {
                        oppId = m.id; oppName = m.name
                        if (phase != Phase.PLAYING) room?.let { rounds = Multi.rounds(it.mode, m.rounds); series = Multi.Series(best = rounds) }
                    }
                } else if (isHost && oppId == null) {
                    oppId = m.id; oppName = m.name
                    val guest = m.id
                    // Sala retomada: espera o histórico todo chegar; se o start já foi mandado antes, não manda de novo.
                    scope.launch {
                        if (resume != null) delay(3000)
                        if (phase == Phase.WAITING) {
                            val st = Msg.Start(System.currentTimeMillis(), guest)
                            val g = if (room?.mode == Multi.HUNT) gridFor(0) else null
                            if (g != null) sendAll(st, g) else send(st)
                        }
                    }
                }
            }
            is Msg.Start -> when {
                isHost && m.guest == oppId -> beginRound(m.at, true)
                !isHost && m.guest == myId -> beginRound(m.at, true)
                !isHost -> { startedOthers = true; if (phase == Phase.WAIT_START || phase == Phase.JOIN) { phase = Phase.FULL; forget() } }
            }
            is Msg.Row -> if (m.id == oppId && phase == Phase.PLAYING && m.row == oppRows.size) oppRows.add(m.marks)
            is Msg.End -> if (phase == Phase.PLAYING && (m.id == myId || m.id == oppId) && ends.none { it.id == m.id }) {
                ends.add(m)
                val o = Multi.outcome(ends, myId, oppId, oppLeft)
                if (o != null && scoredRound != round) { scoredRound = round; series = series.plus(o) }
            }
            // Série acabada + revanche de um app antigo (sem "s") também zera o placar.
            // Com "m" o modo pode mudar: a sala vira SALA-NOVOMODO-SEED e a série zera.
            is Msg.Again -> if (phase == Phase.PLAYING) {
                val r = room ?: return
                val res = Multi.applyAgain(r, m, series.over, rounds)
                val nr = res.room
                room = nr; oppLeft = false; rounds = res.rounds
                val fresh = res.fresh
                if (nr.mode != r.mode && !isHost) toast.show("Novo jogo: ${Multi.modeName(nr.mode)}", 2500)
                beginRound(m.at, fresh)
            }
            is Msg.Bye -> if (m.id == oppId) oppLeft = true
            is Msg.Solve, is Msg.Found, is Msg.Cell, is Msg.Move, is Msg.Resign -> if (phase == Phase.PLAYING) {
                val id = when (m) { is Msg.Solve -> m.id; is Msg.Found -> m.id; is Msg.Cell -> m.id; is Msg.Move -> m.id; is Msg.Resign -> m.id; else -> null }
                if (id == myId || id == oppId) roundMsgs.add(com.dmwnezes.palavreiro.game.Timed(System.currentTimeMillis(), m))
            }
            is Msg.Skip, is Msg.TimeUp -> if (phase == Phase.PLAYING) roundMsgs.add(com.dmwnezes.palavreiro.game.Timed(System.currentTimeMillis(), m))
            // Grade do Caça-Palavras: guardada pela RODADA (pode chegar logo depois do start/again).
            is Msg.Grid -> if (HuntDuel.valid(m) && m.n !in grids) grids[m.n] = m
            // Reações: só as frescas e do amigo (a minha já apareceu na hora).
            is Msg.React -> if (phase == Phase.PLAYING && m.id == oppId && Multi.showReaction(m, myId, System.currentTimeMillis())) {
                reactions.show(m.emoji, oppName ?: "Amigo", mine = false)
                feedback?.react()
            }
        }
    }

    // Escuta a sala (histórico + ao vivo) enquanto a tela estiver aberta.
    val topic = room?.topic
    val listening = phase != Phase.CREATE && phase != Phase.INVALID
    LaunchedEffect(topic, listening) {
        if (topic == null || !listening) return@LaunchedEffect
        ntfy.events(topic).collect { e ->
            when (e) {
                is Ntfy.Event.Connection -> online = e.online
                is Ntfy.Event.Message -> Multi.decode(e.text)?.let(::process)
            }
        }
    }
    // Sair da tela sem ser de propósito (app fechado pelo sistema) só avisa o amigo no meio da partida;
    // na sala de espera, ela continua guardada por 10 minutos.
    DisposableEffect(Unit) {
        onDispose { room?.let { r -> if (!left && phase == Phase.PLAYING) GlobalScope.launch { ntfy.publish(r.topic, Multi.encode(Msg.Bye(myId))) } } }
    }
    BackHandler { leave() }
    // Contagem dos 10 minutos da sala de espera.
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(phase, keptAt) {
        while ((phase == Phase.WAITING || phase == Phase.WAIT_START) && keptAt > 0) {
            nowSec = System.currentTimeMillis()
            if (nowSec >= keptAt + Multi.KEEP_MS) { expired = true; forget(); break }
            delay(1000)
        }
    }
    // Relógio da contagem 3-2-1.
    LaunchedEffect(phase, round) {
        while (phase == Phase.PLAYING && System.currentTimeMillis() < localStart + 200) { now = System.currentTimeMillis(); delay(100) }
        now = System.currentTimeMillis()
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        when (phase) {
            Phase.CREATE -> Lobby("Jogar com amigo", ::leave) {
                Text(modeRules(modeCode, pickRounds, create = true), color = Night.muted, fontSize = 15.sp)
                Spacer(Modifier.height(18.dp))
                NameField(name) { name = it }
                Spacer(Modifier.height(16.dp))
                Text("Modo", color = Night.muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                val codes = Multi.CODES
                ChipTabs(codes.map(Multi::chipName), codes.indexOf(modeCode)) { modeCode = codes[it]; pickRounds = savedRounds(modeCode) }
                Spacer(Modifier.height(14.dp))
                Text("Rodadas", color = Night.muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                val opts = Multi.roundOptions(modeCode)
                ChipTabs(opts.map { Multi.roundsLabel(modeCode, it) }, opts.indexOf(pickRounds)) { pickRounds = opts[it] }
                Spacer(Modifier.height(22.dp))
                PillButton("Criar partida", Night.correct, modifier = Modifier.fillMaxWidth(), enabled = Multi.validName(name)) {
                    val n = name.trim(); store?.setText("mp_name", n); store?.setText("mp_mode", modeCode.toString())
                    store?.setInt("mp_rounds_$modeCode", pickRounds)
                    room = Multi.newRoom(modeCode); isHost = true; phase = Phase.WAITING; expired = false; keptAt = 0L
                    rounds = pickRounds; series = Multi.Series(best = rounds)
                    send(Msg.Hello(myId, n, host = true, rounds = pickRounds))
                    keep()
                }
                if (name.isNotEmpty() && !Multi.validName(name)) {
                    Spacer(Modifier.height(8.dp)); Text("O nome precisa ter de 2 a 16 letras.", color = Night.red, fontSize = 13.sp)
                }
                // Entrar numa partida que alguém criou: cola o link (ou o código) e entra na sala.
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(Night.outline.copy(alpha = 0.5f)))
                    Text("  ou entre numa partida  ", color = Night.muted, fontSize = 13.sp)
                    Box(Modifier.weight(1f).height(1.dp).background(Night.outline.copy(alpha = 0.5f)))
                }
                Spacer(Modifier.height(14.dp))
                var pasted by remember { mutableStateOf("") }
                var bad by remember { mutableStateOf(false) }
                fun enter(text: String) {
                    val r = Multi.fromPasted(text)
                    if (r == null) { bad = true; return }
                    bad = false; room = r; isHost = false; oppName = null; rounds = Multi.defaultRounds(r.mode); phase = Phase.JOIN
                }
                if (scanning) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { scanning = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                    ) {
                        QrScanner(
                            onResult = { t -> scanning = false; pasted = t; enter(t) },
                            onClose = { scanning = false },
                            accept = { Multi.fromPasted(it) != null },
                        )
                    }
                }
                OutlinedTextField(
                    value = pasted, onValueChange = { pasted = it; bad = false }, singleLine = true,
                    label = { Text("Link da partida") }, placeholder = { Text("Cole aqui o link que seu amigo mandou") },
                    modifier = Modifier.fillMaxWidth(), isError = bad,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Night.text, unfocusedTextColor = Night.text, focusedBorderColor = Night.accent,
                        unfocusedBorderColor = Night.outline, cursorColor = Night.accent, focusedLabelColor = Night.accent, unfocusedLabelColor = Night.muted,
                        errorBorderColor = Night.red, errorLabelColor = Night.red, errorTextColor = Night.text, errorCursorColor = Night.red,
                    ),
                )
                if (bad) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (Multi.removedGame(pasted)) REMOVED_TEXT else "Esse link não é de uma partida do Palavreiro.",
                        color = Night.red, fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Colar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        pasted = clip
                        if (Multi.fromPasted(clip) != null) enter(clip) else bad = clip.isNotBlank()
                    }
                    PillButton("📷 Ler QR", Night.surfaceHigh, Night.text, Modifier.weight(1f)) { scanning = true }
                }
                Spacer(Modifier.height(10.dp))
                PillButton("Entrar na partida", Night.accent, modifier = Modifier.fillMaxWidth(), enabled = pasted.isNotBlank()) { enter(pasted) }
            }
            Phase.WAITING -> Lobby("Jogar com amigo", ::leave) {
                val r = room!!
                Text("${Multi.modeName(r.mode)}${Multi.roundsSuffix(r.mode, rounds)} · mande o link para seu amigo", color = Night.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(r.link, color = Night.accent, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surface).padding(14.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Compartilhar", Night.correct, modifier = Modifier.weight(1f)) {
                        val text = "Bora jogar ${Multi.modeName(r.mode)}${Multi.roundsSuffix(r.mode, rounds)} no Palavreiro? ${r.link}"
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartilhar"))
                    }
                    PillButton("Copiar link", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Palavreiro", r.link))
                    }
                }
                Spacer(Modifier.height(20.dp))
                // QR da sala: o amigo do lado entra só apontando a câmera.
                Column(
                    Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surface).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    QrCode(r.link, Modifier.size(196.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Amigo do lado? Ele aponta a câmera aqui e entra.", color = Night.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(14.dp))
                if (expired) {
                    Column(Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surface).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("A sala expirou.", color = Night.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        PillButton("Criar outra sala", Night.correct, modifier = Modifier.fillMaxWidth()) {
                            expired = false; keptAt = 0L; oppId = null; oppName = null; phase = Phase.CREATE
                        }
                    }
                } else {
                    if (keptAt > 0) {
                        Text(
                            "A sala fica aberta por mais ${formatMs(keptAt + Multi.KEEP_MS - nowSec)}, mesmo se você sair para mandar o link.",
                            color = Night.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    WaitingDots(if (oppName != null) "${oppName} entrou!" else "Esperando seu amigo entrar")
                }
                if (!online) { Spacer(Modifier.height(10.dp)); Text("Reconectando…", color = Night.muted, fontSize = 13.sp) }
            }
            Phase.JOIN -> Lobby("Jogar com amigo", ::leave) {
                val r = room!!
                Text(
                    (if (oppName != null) "$oppName te chamou para ${Multi.withArticle(r.mode)}" else "Você foi chamado para ${Multi.withArticle(r.mode)}") +
                        Multi.roundsSuffix(r.mode, rounds),
                    color = Night.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(modeRules(r.mode, rounds, create = false), color = Night.muted, fontSize = 15.sp)
                Spacer(Modifier.height(18.dp))
                NameField(name) { name = it }
                Spacer(Modifier.height(20.dp))
                PillButton("Entrar", Night.correct, modifier = Modifier.fillMaxWidth(), enabled = Multi.validName(name)) {
                    val n = name.trim(); store?.setText("mp_name", n)
                    if (startedOthers) { phase = Phase.FULL; return@PillButton }
                    joined = true; phase = Phase.WAIT_START
                    send(Msg.Hello(myId, n, host = false))
                    keep()
                }
            }
            Phase.WAIT_START -> Lobby("Jogar com amigo", ::leave) {
                Spacer(Modifier.height(30.dp))
                if (expired) {
                    Text("A sala expirou.", color = Night.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Text("${oppName ?: "Seu amigo"} não começou a partida a tempo. Peça um link novo.", color = Night.muted, fontSize = 15.sp)
                    Spacer(Modifier.height(20.dp))
                    PillButton("Voltar", Night.correct, modifier = Modifier.fillMaxWidth(), onClick = ::leave)
                } else WaitingDots("Esperando ${oppName ?: "seu amigo"} começar")
                if (!online) { Spacer(Modifier.height(10.dp)); Text("Reconectando…", color = Night.muted, fontSize = 13.sp) }
            }
            Phase.FULL -> Lobby("Jogar com amigo", ::leave) {
                Text("A partida já está cheia", color = Night.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text("Esse link já está sendo usado por outras duas pessoas. Crie a sua partida!", color = Night.muted, fontSize = 15.sp)
                Spacer(Modifier.height(20.dp))
                PillButton("Voltar", Night.correct, modifier = Modifier.fillMaxWidth(), onClick = ::leave)
            }
            Phase.INVALID -> Lobby("Jogar com amigo", ::leave) {
                Text("Link inválido", color = Night.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text(if (removedLink) REMOVED_TEXT else "Peça para seu amigo mandar o link de novo.", color = Night.muted, fontSize = 15.sp)
                Spacer(Modifier.height(20.dp))
                PillButton("Voltar", Night.correct, modifier = Modifier.fillMaxWidth(), onClick = ::leave)
            }
            Phase.PLAYING -> Box(Modifier.fillMaxSize()) {
                val cur = room!!.mode
                val next = if (isHost) {
                    val sel = nextMode ?: cur
                    NextGame(
                        cur, sel, { nextMode = it; nextRounds = if (it == cur) null else savedRounds(it) },
                        currentRounds = rounds, rounds = nextRounds ?: if (sel == cur) rounds else savedRounds(sel),
                        pickRounds = { nextRounds = it; store?.setInt("mp_rounds_$sel", it) },
                    )
                } else null
                val me = name.trim()
                if (!Multi.isTermo(room!!.mode)) {
                    val r = room!!
                    val counting = now < localStart
                    // Resultado (uma vez por rodada/partida): placar da série e contadores.
                    val decided: (String) -> Unit = { o ->
                        if (Multi.hasSeries(r.mode) && scoredRound != round) { scoredRound = round; series = series.plus(o) }
                        if (recordedRound != round) {
                            recordedRound = round
                            store?.add("mp_played"); if (o == "me") store?.add("mp_won"); store?.logActivity()
                        }
                    }
                    val duel = Duel(
                        myId = myId, oppId = oppId, oppName = oppName ?: "Amigo", isHost = isHost, round = round, n = round - 1,
                        localStart = localStart, series = series, online = online, oppLeft = oppLeft, send = ::send, onDecided = decided,
                        onAgain = ::again, onLeave = ::leave, myName = me, next = next, reactions = reactions, feedback = feedback,
                    )
                    Box(Modifier.fillMaxSize()) {
                        when (r.mode) {
                            Multi.ANAGRAM -> AnagramPlay(
                                words, feedback, r.seed, round, myId, oppId, oppName ?: "Amigo", isHost, roundMsgs, localStart, online, oppLeft,
                                send = ::send, onDecided = decided,
                                onAgain = ::again,
                                onLeave = ::leave, myName = me, next = next, reactions = reactions, rounds = rounds,
                            )
                            Multi.DEFINE -> DefinePlay(defs, r.seed, rounds, roundMsgs, duel)
                            Multi.HUNT -> HuntPlay(grids[round - 1], roundMsgs.map { it.msg }, duel)
                            Multi.TICTAC -> TicTacPlay(roundMsgs.map { it.msg }, duel)
                            Multi.CHECKERS -> CheckersPlay(roundMsgs.map { it.msg }, duel)
                        }
                        if (counting) Countdown(((localStart - now + 999) / 1000).toInt().coerceIn(1, 3))
                    }
                } else {
                    val r = room!!
                    val boards = Multi.boards(r.mode)
                    val mode = Multi.MODES.getValue(r.mode)
                    val game = remember(r.seed, round) { TermoGame(mode, words, store = null, fixed = Multi.words(words.answers, r.seed, boards)) }
                    val outcome = Multi.outcome(ends, myId, oppId, oppLeft)
                    val counting = now < localStart
                    // Envia cada tentativa revelada (só as cores).
                    var sent by remember(game) { mutableIntStateOf(0) }
                    val revealed = if (game.revealingRow >= 0) game.revealingRow else game.rows.size
                    LaunchedEffect(game, revealed) {
                        while (sent < revealed) { send(Msg.Row(myId, sent, Multi.rowMarks(game, sent))); sent++ }
                    }
                    // Resultado: conta uma vez por rodada e abre a folha.
                    LaunchedEffect(outcome, round) {
                        if (outcome == null || recordedRound == round) return@LaunchedEffect
                        recordedRound = round
                        store?.add("mp_played"); if (outcome == "me") store?.add("mp_won"); store?.logActivity()
                        if (outcome == "opp") feedback?.lose()
                        delay(if (game.over) 1500 else 600)
                        showEnd = true
                    }
                    Box(Modifier.fillMaxSize()) {
                        GameScreen(
                            game = game,
                            feedback = feedback,
                            onBack = ::leave,
                            onPlayInfinite = {},
                            onHelp = {},
                            meanings = meanings,
                            multiplayer = true,
                            titleOverride = "Você × ${oppName ?: "amigo"}",
                            titleTrailing = { ReactAnchor(reactions); SeriesPill(series) },
                            header = {
                                OpponentStrip(
                                    name = oppName ?: "Amigo", boards = boards, maxTries = mode.maxTries, rows = oppRows,
                                    status = oppStatus(oppRows.size, mode.maxTries, ends.firstOrNull { it.id == oppId }, oppLeft),
                                    online = online,
                                )
                            },
                            inputEnabled = !counting && outcome == null,
                            replayTrigger = replayTrigger,
                            onFinished = { won -> send(Msg.End(myId, won, game.rows.size, System.currentTimeMillis() - localStart)) },
                        )
                        if (counting) Countdown(((localStart - now + 999) / 1000).toInt().coerceIn(1, 3))
                        if (!showEnd && outcome != null) {
                            PillButton("Ver resultado", Night.correct, modifier = Modifier.align(Alignment.Center)) { showEnd = true }
                        }
                    }
                    if (showEnd && outcome != null) {
                        EndSheet(
                            outcome = outcome, oppName = oppName ?: "Amigo", game = game,
                            mine = ends.firstOrNull { it.id == myId }, theirs = ends.firstOrNull { it.id == oppId },
                            isHost = isHost, meanings = meanings, series = series,
                            onAgain = ::again,
                            onReplay = { showEnd = false; replayTrigger++ },
                            onLeave = ::leave,
                            onClose = { showEnd = false },
                            mode = r.mode, myName = me, next = next,
                        )
                    }
                }
                // Reações (por cima da contagem e da folha de fim) e o aviso de jogo novo.
                val gap = when (room!!.mode) { Multi.ANAGRAM, Multi.DEFINE, Multi.HUNT, Multi.TICTAC, Multi.CHECKERS -> 84.dp; else -> 112.dp }
                ReactionOverlay(reactions, gap)
                ToastView(toast, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 96.dp))
            }
        }
    }
}

/** Aviso do link antigo da Bomba-Relógio. */
private const val REMOVED_TEXT = "Esse jogo saiu do Palavreiro. Crie uma partida nova!"

/** Explicação curta do modo nas telas de criar e de entrar, com as rodadas escolhidas. */
private fun modeRules(code: Char, rounds: Int, create: Boolean): String {
    val series = when (rounds) {
        1 -> " Uma partida só: quem vencer leva o troféu 🏆"
        else -> " Melhor de $rounds: quem vencer ${(rounds + 1) / 2} rodadas leva o troféu 🏆"
    }
    return when (code) {
        Multi.ANAGRAM -> "$rounds rodadas com as mesmas 5 letras embaralhadas para os dois: quem achar a palavra primeiro leva o ponto. Vence quem fizer mais pontos 🏆"
        Multi.DEFINE -> "$rounds rodadas com a mesma definição para os dois: quem acertar a palavra primeiro leva o ponto. Aos 20 s aparece a 1ª letra e aos 40 s a última. Vence quem fizer mais pontos 🏆"
        Multi.HUNT -> "A mesma grade para os dois: cada palavra é de quem achar primeiro. 5 minutos por rodada; vence a rodada quem pegar mais palavras." + series
        Multi.TICTAC -> "Jogo da Velha ao vivo: X sempre começa e os símbolos trocam a cada rodada. Velha não conta." + series
        Multi.CHECKERS -> "Damas com as regras brasileiras: captura obrigatória, lei da maioria e dama voadora. Brancas começam e as cores trocam a cada rodada." + series
        else -> (if (create) "Partida ao vivo: quem acertar primeiro ganha. Você vê as cores das tentativas do seu amigo, mas não as letras."
        else "Quem acertar primeiro ganha. Vocês veem as cores das tentativas um do outro, sem as letras.") + series
    }
}

private fun oppStatus(rows: Int, max: Int, end: Msg.End?, left: Boolean): String = when {
    end != null && end.won -> "acertou em ${end.tries}!"
    end != null -> "errou"
    left -> "saiu"
    else -> "jogando · $rows/$max"
}

@Composable
private fun Lobby(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar(title, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            content()
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.take(16)) }, singleLine = true,
        label = { Text("Seu nome") }, placeholder = { Text("Ex.: Daniel") },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Night.text, unfocusedTextColor = Night.text, focusedBorderColor = Night.accent,
            unfocusedBorderColor = Night.outline, cursorColor = Night.accent, focusedLabelColor = Night.accent, unfocusedLabelColor = Night.muted,
        ),
    )
}

@Composable
private fun WaitingDots(text: String) {
    val t = rememberInfiniteTransition(label = "espera")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FiveSquares(square = 14.dp, gap = 4.dp, animated = true)
        Spacer(Modifier.height(14.dp))
        Text("$text…", color = Night.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.alpha(a), textAlign = TextAlign.Center)
    }
}

@Composable
internal fun Countdown(n: Int) {
    Box(
        Modifier.fillMaxSize().background(Night.bgBottom.copy(alpha = 0.72f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", color = Night.text, fontSize = 96.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Faixa do adversário: nome, situação e os mini tabuleiros dele (só cores, sem letras). */
@Composable
internal fun OpponentStrip(name: String, boards: Int, maxTries: Int, rows: List<String>, status: String, online: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(Shapes.card).background(Night.surface).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Night.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (online) status else "Reconectando…", color = Night.muted, fontSize = 13.sp, maxLines = 1)
        }
        val cell: Dp = when (boards) { 1 -> 9.dp; 2 -> 7.dp; else -> 5.dp }
        val gap: Dp = if (boards == 4) 1.dp else 2.dp
        Row(horizontalArrangement = Arrangement.spacedBy(if (boards == 4) 4.dp else 6.dp)) {
            for (b in 0 until boards) {
                // Linhas deste tabuleiro: param depois que ele foi resolvido (marcas vazias).
                val mine = rows.map { it.split('|').getOrElse(b) { "" } }.takeWhile { it.isNotEmpty() }
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (r in 0 until maxTries) {
                        val m = mine.getOrNull(r)
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (c in 0 until 5) {
                                val color = when (m?.getOrNull(c)) { 'c' -> Night.correct; 'p' -> Night.present; 'a' -> Night.keyAbsent; else -> null }
                                Box(
                                    Modifier.size(cell).clip(RoundedCornerShape(cell * 0.25f))
                                        .then(if (color != null) Modifier.background(color) else Modifier.border(1.dp, Night.outline.copy(alpha = 0.6f), RoundedCornerShape(cell * 0.25f)))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EndSheet(
    outcome: String, oppName: String, game: TermoGame, mine: Msg.End?, theirs: Msg.End?, isHost: Boolean,
    meanings: Map<String, String>, series: Multi.Series, onAgain: () -> Unit, onReplay: () -> Unit, onLeave: () -> Unit, onClose: () -> Unit,
    /** Modo da partida ('t', 'd' ou 'q'), meu nome (cartão dos Stories) e a escolha do próximo jogo (anfitrião). */
    mode: Char = 't', myName: String? = null, next: NextGame? = null,
) {
    val context = LocalContext.current
    BottomSheet(onClose) {
        val words = (if (game.boards > 1) "As palavras eram " else "A palavra era ") + game.answerDisplay()
        val champ = series.winner
        if (champ != null) {
            // Fim da série: troféu para quem levou.
            Trophy(
                if (series.best <= 1) (if (champ == "me") "Você venceu!" else "$oppName venceu") else if (champ == "me") "Você levou a série!" else "$oppName levou a série",
                (if (series.best <= 1) "1 partida" else "Melhor de ${series.best}") + " · ${series.score}", champ == "me",
            )
            Spacer(Modifier.height(10.dp))
            Text(
                when (outcome) { "me" -> "Você venceu a última rodada. "; "opp" -> "$oppName venceu a última rodada. "; else -> "" } + words,
                color = Night.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SheetTitle(
                when (outcome) { "me" -> "Você venceu a rodada!"; "opp" -> "$oppName venceu a rodada"; else -> "Empate" },
                words,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Série: Você ${series.score} $oppName · ${series.label}",
                color = Night.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        fun line(e: Msg.End?) = when {
            e == null -> "ainda jogando"
            e.won -> "acertou em ${e.tries} · ${formatMs(e.ms)}"
            else -> "errou · ${formatMs(e.ms)}"
        }
        ResultLine("Você", line(mine), outcome == "me")
        ResultLine(oppName, line(theirs), outcome == "opp")
        Spacer(Modifier.height(18.dp))
        EndButtons(
            isHost = isHost, next = next, sameLabel = if (champ != null) "Nova série" else "Próxima rodada", seriesOver = series.over,
            onAgain = onAgain, onLeave = onLeave,
            onShare = {
                val res = when {
                    champ == "me" -> "Levei a série por ${series.score} 🏆"
                    champ == "opp" -> "Perdi a série por ${series.opp} × ${series.me} ⚔️"
                    outcome == "me" -> "Venci em ${mine?.tries ?: game.rows.size} tentativas ⚔️ (série ${series.score})"
                    outcome == "opp" -> "Perdi ⚔️ (série ${series.score})"
                    else -> "Empate ⚔️ (série ${series.score})"
                }
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Palavreiro · Partida com $oppName\n$res"), "Compartilhar"))
            },
            onStory = { StoryCard.shareMatch(context, MatchStory.termo(mode, myName, oppName, series, outcome, mine, theirs)) },
            onReplay = onReplay,
        )
    }
}

/**
 * Escolha do próximo jogo na folha de fim (só o anfitrião): [current] = modo de agora, [selected] = escolhido;
 * [currentRounds]/[rounds] = rodadas de agora e as escolhidas para o próximo jogo.
 */
class NextGame(
    val current: Char, val selected: Char, val pick: (Char) -> Unit,
    val currentRounds: Int = Multi.defaultRounds(current), val rounds: Int = Multi.defaultRounds(selected), val pickRounds: (Int) -> Unit = {},
) {
    /** Muda alguma coisa em relação à partida de agora? */
    val changed: Boolean get() = selected != current || rounds != currentRounds
}

/** "Próximo jogo" + pílulas dos 8 modos (duas linhas) e, pequeno, o seletor de rodadas. */
@Composable
internal fun NextGamePicker(n: NextGame) {
    Text("Próximo jogo", color = Night.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    Column(Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surfaceHigh).padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (line in Multi.CODES.chunked(4)) {
            Row(Modifier.fillMaxWidth()) {
                for (code in line) {
                    val sel = code == n.selected
                    Text(
                        Multi.chipName(code), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1,
                        color = if (sel) Ink else Night.muted,
                        // Nomes mais longos (Quarteto, Anagrama) ganham mais espaço.
                        modifier = Modifier.weight(Multi.chipName(code).length + 3f).clip(Shapes.pill).background(if (sel) Night.accent else Color.Transparent)
                            .clickable { n.pick(code) }.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Rodadas", color = Night.muted, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
        Row(Modifier.weight(1f).clip(Shapes.pill).background(Night.surfaceHigh).padding(3.dp)) {
            for (r in Multi.roundOptions(n.selected)) {
                val sel = r == n.rounds
                Text(
                    Multi.roundsLabel(n.selected, r), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center,
                    color = if (sel) Ink else Night.muted,
                    modifier = Modifier.weight(1f).clip(Shapes.pill).background(if (sel) Night.accent else Color.Transparent)
                        .clickable { n.pickRounds(r) }.padding(vertical = 5.dp),
                )
            }
        }
    }
}

/**
 * Botões da folha de fim (todos os modos): próximo jogo (anfitrião), revanche/sair,
 * Stories/Compartilhar e, no Termo, o replay.
 */
@Composable
internal fun EndButtons(
    isHost: Boolean, next: NextGame?, sameLabel: String, seriesOver: Boolean,
    onAgain: () -> Unit, onLeave: () -> Unit, onShare: () -> Unit, onStory: () -> Unit, onReplay: (() -> Unit)? = null,
) {
    if (isHost && next != null) {
        NextGamePicker(next)
        Spacer(Modifier.height(12.dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isHost) {
            val label = when {
                next != null && next.selected != next.current -> Multi.againLabel(next.current, next.selected, seriesOver)
                next != null && next.changed -> if (Multi.pointsMode(next.current)) "Revanche" else "Nova série"
                else -> sameLabel
            }
            PillButton(label, Night.correct, modifier = Modifier.weight(1f), onClick = onAgain)
        } else PillButton("Esperando o anfitrião", Night.correct, modifier = Modifier.weight(1f), enabled = false) {}
        PillButton("Sair", Night.surfaceHigh, Night.text, Modifier.weight(0.45f), onClick = onLeave)
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("📸 Stories", Night.accent, modifier = Modifier.weight(1f), onClick = onStory)
        PillButton("Compartilhar", Night.surfaceHigh, Night.text, Modifier.weight(1f), onClick = onShare)
    }
    if (onReplay != null) {
        Spacer(Modifier.height(10.dp))
        PillButton("▶ Replay", Night.surfaceHigh, Night.text, Modifier.fillMaxWidth(), onClick = onReplay)
    }
}

@Composable
internal fun ResultLine(who: String, text: String, winner: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Shapes.card).background(if (winner) Night.correct.copy(alpha = 0.22f) else Night.surfaceHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text((if (winner) "🏆 " else "") + who, color = Night.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text, color = Night.muted, fontSize = 14.sp)
    }
}

private fun formatMs(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

/** Placar da série ao lado do título: "1 × 0", dourado quando alguém leva. */
@Composable
internal fun SeriesPill(series: Multi.Series) {
    val gold = Color(0xFFF5C84C)
    Column(
        Modifier.padding(end = 10.dp).clip(Shapes.pill).background(if (series.over) gold.copy(alpha = 0.2f) else Night.surfaceHigh)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text((if (series.over) "🏆 " else "") + series.score, color = if (series.over) gold else Night.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        if (series.label.isNotEmpty()) Text(series.label, color = Night.muted, fontSize = 9.sp, lineHeight = 10.sp)
    }
}

/** Troféu do fim da série, com um leve balanço. */
@Composable
internal fun Trophy(title: String, subtitle: String, mine: Boolean) {
    val t = rememberInfiniteTransition(label = "trofeu")
    val s by t.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🏆", fontSize = 64.sp, modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s; alpha = if (mine) 1f else 0.75f })
        Spacer(Modifier.height(6.dp))
        Text(title, color = Night.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(subtitle, color = Color(0xFFF5C84C), fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
