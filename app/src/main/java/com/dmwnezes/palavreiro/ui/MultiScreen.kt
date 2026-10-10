package com.dmwnezes.palavreiro.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import com.dmwnezes.palavreiro.game.TermoGame
import com.dmwnezes.palavreiro.game.Words
import com.dmwnezes.palavreiro.system.Ntfy
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myId = remember { Multi.randomId(8) }
    val joinRoom = remember(joinCode) { joinCode?.let(Multi::parse) }
    var phase by remember { mutableStateOf(if (joinCode == null) Phase.CREATE else if (joinRoom == null) Phase.INVALID else Phase.JOIN) }
    var room by remember { mutableStateOf(joinRoom) }
    var isHost by remember { mutableStateOf(joinCode == null) }
    var name by remember { mutableStateOf(store?.text("mp_name").orEmpty()) }
    var modeCode by remember { mutableStateOf(store?.text("mp_mode")?.firstOrNull()?.takeIf { it in Multi.MODES } ?: 'd') }
    var oppId by remember { mutableStateOf<String?>(null) }
    var oppName by remember { mutableStateOf<String?>(null) }
    var joined by remember { mutableStateOf(false) }
    var startedOthers by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(true) }
    // Rodada atual (muda na revanche).
    var round by remember { mutableIntStateOf(0) }
    var localStart by remember { mutableLongStateOf(0L) }
    val oppRows = remember { mutableStateListOf<String>() }
    val ends = remember { mutableStateListOf<Msg.End>() }
    var oppLeft by remember { mutableStateOf(false) }
    var recordedRound by remember { mutableIntStateOf(-1) }
    var showEnd by remember { mutableStateOf(false) }
    var replayTrigger by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun send(m: Msg) { val r = room ?: return; scope.launch { ntfy.publish(r.topic, Multi.encode(m)) } }

    fun beginRound(at: Long) {
        val t = System.currentTimeMillis()
        localStart = if (kotlin.math.abs(t - at) < 2500) at + 3000 else t + 3000
        oppRows.clear(); ends.clear(); showEnd = false
        round++
        phase = Phase.PLAYING
    }

    fun process(m: Msg) {
        when (m) {
            is Msg.Hello -> if (m.id != myId) {
                if (m.host) {
                    if (!isHost) { oppId = m.id; oppName = m.name }
                } else if (isHost && oppId == null) {
                    oppId = m.id; oppName = m.name
                    send(Msg.Start(System.currentTimeMillis(), m.id))
                }
            }
            is Msg.Start -> when {
                isHost && m.guest == oppId -> beginRound(m.at)
                !isHost && m.guest == myId -> beginRound(m.at)
                !isHost -> { startedOthers = true; if (phase == Phase.WAIT_START || phase == Phase.JOIN) phase = Phase.FULL }
            }
            is Msg.Row -> if (m.id == oppId && phase == Phase.PLAYING && m.row == oppRows.size) oppRows.add(m.marks)
            is Msg.End -> if (phase == Phase.PLAYING && (m.id == myId || m.id == oppId) && ends.none { it.id == m.id }) ends.add(m)
            is Msg.Again -> if (phase == Phase.PLAYING) { room = room?.withSeed(m.seed); oppLeft = false; beginRound(m.at) }
            is Msg.Bye -> if (m.id == oppId) oppLeft = true
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
    DisposableEffect(Unit) {
        onDispose { room?.let { r -> if (joined || isHost) GlobalScope.launch { ntfy.publish(r.topic, Multi.encode(Msg.Bye(myId))) } } }
    }
    // Relógio da contagem 3-2-1.
    LaunchedEffect(phase, round) {
        while (phase == Phase.PLAYING && System.currentTimeMillis() < localStart + 200) { now = System.currentTimeMillis(); delay(100) }
        now = System.currentTimeMillis()
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        when (phase) {
            Phase.CREATE -> Lobby("Jogar com amigo", onBack) {
                Text("Partida ao vivo: quem acertar primeiro ganha. Você vê as cores das tentativas do seu amigo, mas não as letras.", color = Night.muted, fontSize = 15.sp)
                Spacer(Modifier.height(18.dp))
                NameField(name) { name = it }
                Spacer(Modifier.height(16.dp))
                Text("Modo", color = Night.muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                val codes = Multi.MODES.keys.toList()
                ChipTabs(codes.map(Multi::modeName), codes.indexOf(modeCode)) { modeCode = codes[it] }
                Spacer(Modifier.height(22.dp))
                PillButton("Criar partida", Night.correct, modifier = Modifier.fillMaxWidth(), enabled = Multi.validName(name)) {
                    val n = name.trim(); store?.setText("mp_name", n); store?.setText("mp_mode", modeCode.toString())
                    room = Multi.newRoom(modeCode); isHost = true; phase = Phase.WAITING
                    send(Msg.Hello(myId, n, host = true))
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
                    bad = false; room = r; isHost = false; oppName = null; phase = Phase.JOIN
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
                if (bad) { Spacer(Modifier.height(6.dp)); Text("Esse link não é de uma partida do Palavreiro.", color = Night.red, fontSize = 13.sp) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Colar", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        pasted = clip
                        if (Multi.fromPasted(clip) != null) enter(clip) else bad = clip.isNotBlank()
                    }
                    PillButton("Entrar na partida", Night.accent, modifier = Modifier.weight(1.4f), enabled = pasted.isNotBlank()) { enter(pasted) }
                }
            }
            Phase.WAITING -> Lobby("Jogar com amigo", onBack) {
                val r = room!!
                Text("${Multi.modeName(r.mode)} · mande o link para seu amigo", color = Night.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(r.link, color = Night.accent, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surface).padding(14.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Compartilhar", Night.correct, modifier = Modifier.weight(1f)) {
                        val text = "Bora jogar ${Multi.modeName(r.mode)} no Palavreiro? Quem acertar primeiro ganha! ${r.link}"
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartilhar"))
                    }
                    PillButton("Copiar link", Night.surfaceHigh, Night.text, Modifier.weight(1f)) {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Palavreiro", r.link))
                    }
                }
                Spacer(Modifier.height(28.dp))
                WaitingDots(if (oppName != null) "${oppName} entrou!" else "Esperando seu amigo entrar")
                if (!online) { Spacer(Modifier.height(10.dp)); Text("Reconectando…", color = Night.muted, fontSize = 13.sp) }
            }
            Phase.JOIN -> Lobby("Jogar com amigo", onBack) {
                val r = room!!
                Text(
                    if (oppName != null) "$oppName te chamou para um ${Multi.modeName(r.mode)}" else "Você foi chamado para um ${Multi.modeName(r.mode)}",
                    color = Night.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(8.dp))
                Text("Quem acertar primeiro ganha. Vocês veem as cores das tentativas um do outro, sem as letras.", color = Night.muted, fontSize = 15.sp)
                Spacer(Modifier.height(18.dp))
                NameField(name) { name = it }
                Spacer(Modifier.height(20.dp))
                PillButton("Entrar", Night.correct, modifier = Modifier.fillMaxWidth(), enabled = Multi.validName(name)) {
                    val n = name.trim(); store?.setText("mp_name", n)
                    if (startedOthers) { phase = Phase.FULL; return@PillButton }
                    joined = true; phase = Phase.WAIT_START
                    send(Msg.Hello(myId, n, host = false))
                }
            }
            Phase.WAIT_START -> Lobby("Jogar com amigo", onBack) {
                Spacer(Modifier.height(30.dp))
                WaitingDots("Esperando ${oppName ?: "seu amigo"} começar")
                if (!online) { Spacer(Modifier.height(10.dp)); Text("Reconectando…", color = Night.muted, fontSize = 13.sp) }
            }
            Phase.FULL -> Lobby("Jogar com amigo", onBack) {
                Text("A partida já está cheia", color = Night.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text("Esse link já está sendo usado por outras duas pessoas. Crie a sua partida!", color = Night.muted, fontSize = 15.sp)
                Spacer(Modifier.height(20.dp))
                PillButton("Voltar", Night.correct, modifier = Modifier.fillMaxWidth(), onClick = onBack)
            }
            Phase.INVALID -> Lobby("Jogar com amigo", onBack) {
                Text("Link inválido", color = Night.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text("Peça para seu amigo mandar o link de novo.", color = Night.muted, fontSize = 15.sp)
                Spacer(Modifier.height(20.dp))
                PillButton("Voltar", Night.correct, modifier = Modifier.fillMaxWidth(), onClick = onBack)
            }
            Phase.PLAYING -> {
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
                        onBack = onBack,
                        onPlayInfinite = {},
                        onHelp = {},
                        meanings = meanings,
                        multiplayer = true,
                        titleOverride = "Você × ${oppName ?: "amigo"}",
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
                        isHost = isHost, meanings = meanings,
                        onAgain = { send(Msg.Again(Multi.newSeed(), System.currentTimeMillis())) },
                        onReplay = { showEnd = false; replayTrigger++ },
                        onLeave = onBack,
                        onClose = { showEnd = false },
                    )
                }
            }
        }
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
private fun Countdown(n: Int) {
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
private fun EndSheet(
    outcome: String, oppName: String, game: TermoGame, mine: Msg.End?, theirs: Msg.End?, isHost: Boolean,
    meanings: Map<String, String>, onAgain: () -> Unit, onReplay: () -> Unit, onLeave: () -> Unit, onClose: () -> Unit,
) {
    val context = LocalContext.current
    BottomSheet(onClose) {
        SheetTitle(
            when (outcome) { "me" -> "Você venceu! 🏆"; "opp" -> "$oppName venceu"; else -> "Empate" },
            (if (game.boards > 1) "As palavras eram " else "A palavra era ") + game.answerDisplay(),
        )
        Spacer(Modifier.height(16.dp))
        fun line(e: Msg.End?) = when {
            e == null -> "ainda jogando"
            e.won -> "acertou em ${e.tries} · ${formatMs(e.ms)}"
            else -> "errou · ${formatMs(e.ms)}"
        }
        ResultLine("Você", line(mine), outcome == "me")
        ResultLine(oppName, line(theirs), outcome == "opp")
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isHost) PillButton("Revanche", Night.correct, modifier = Modifier.weight(1f), onClick = onAgain)
            else PillButton("Esperando o anfitrião", Night.correct, modifier = Modifier.weight(1f), enabled = false) {}
            PillButton("Sair", Night.surfaceHigh, Night.text, Modifier.weight(0.6f), onClick = onLeave)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Compartilhar", Night.accent, modifier = Modifier.weight(1f)) {
                val res = when (outcome) { "me" -> "Venci em ${mine?.tries ?: game.rows.size} tentativas ⚔️"; "opp" -> "Perdi ⚔️"; else -> "Empate ⚔️" }
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Palavreiro · Partida com $oppName\n$res"), "Compartilhar"))
            }
            PillButton("▶ Replay", Night.surfaceHigh, Night.text, Modifier.weight(0.8f), onClick = onReplay)
        }
    }
}

@Composable
private fun ResultLine(who: String, text: String, winner: Boolean) {
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
