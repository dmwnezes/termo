package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.game.Forca
import com.dmwnezes.palavreiro.game.Multi
import com.dmwnezes.palavreiro.game.Multi.Msg
import kotlinx.coroutines.delay

/**
 * Forca com amigo (modo "f"): na RODADA par o anfitrião escolhe a palavra; na ímpar, o convidado.
 * [initialWord]/[initialTheme] = palavra já escolhida (para as imagens de teste).
 */
@Composable
internal fun ForcaPlay(msgs: List<Msg>, d: Duel, initialWord: String? = null) {
    val now = rememberNow(d.clock, d.round, 200)
    val hostChooses = Forca.chooserIsHost(d.n)
    val opp = d.oppId ?: "?"
    val chooserId = if (hostChooses == d.isHost) d.myId else opp
    val guesserId = if (chooserId == d.myId) opp else d.myId
    val iChoose = chooserId == d.myId
    val st = Forca.state(msgs, d.n, chooserId, guesserId)
    var word by remember(d.round) { mutableStateOf(initialWord) }
    val answered = remember(d.round) { mutableSetOf<Char>() }
    var triesAnswered by remember(d.round) { mutableIntStateOf(0) }
    var wordSent by remember(d.round) { mutableStateOf(false) }
    var showEnd by remember(d.round) { mutableStateOf(false) }
    val toast = rememberToast()
    val outcome: String? = if (!st.over) null else if (st.solved) (if (iChoose) "opp" else "me") else (if (iChoose) "me" else "opp")

    // Quem escolheu responde sozinho: posições de cada letra e o resultado de cada chute.
    LaunchedEffect(msgs.size, word, st.secret != null) {
        val w = word ?: return@LaunchedEffect
        if (!iChoose || st.secret == null) return@LaunchedEffect
        val tries = ArrayList<Msg.Try>()
        for (m in msgs) {
            if (m is Msg.Guess && m.n == d.n && m.id == guesserId && !st.answered(m.letter) && answered.add(m.letter)) {
                d.send(Msg.Reveal(d.myId, d.n, m.letter, Forca.positions(w, m.letter)))
            }
            if (m is Msg.Try && m.n == d.n && m.id == guesserId) tries += m
        }
        while (triesAnswered < tries.size) {
            d.send(Msg.TryRes(d.n, Forca.tryOk(tries[triesAnswered].word, w)))
            triesAnswered++
        }
    }
    // Fim: quem escolheu manda a palavra original.
    LaunchedEffect(st.over) {
        val w = word
        if (st.over && iChoose && w != null && !wordSent) { wordSent = true; d.send(Msg.Word(d.n, w)) }
    }
    val wrongTries = st.tries.count { it.second == false }
    LaunchedEffect(wrongTries) { if (wrongTries > 0 && !iChoose) { toast.show("Não é essa!"); d.feedback?.invalid() } }
    LaunchedEffect(outcome, d.round) {
        val o = outcome ?: return@LaunchedEffect
        d.onDecided(o)
        if (o == "me") d.feedback?.win() else d.feedback?.lose()
        delay(1800)
        showEnd = true
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Você × ${d.oppName}", d.onLeave, trailing = { ReactAnchor(d.reactions); SeriesPill(d.series) })
            OppBar(
                d.oppName,
                when {
                    !d.online -> "Reconectando…"
                    d.oppLeft -> "saiu"
                    iChoose -> if (st.secret == null) "vai adivinhar" else "adivinhando…"
                    st.secret == null -> "escolhendo a palavra…"
                    else -> "escolheu a palavra"
                },
                false,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    st.secret == null && iChoose -> ChooseWord(d.oppName) { w, th ->
                        word = Forca.clean(w)
                        d.send(Msg.Secret(d.myId, d.n, th.trim().take(Forca.THEME_MAX), Forca.mask(w)))
                    }
                    st.secret == null -> WaitChooser(d.oppName, now - d.localStart >= Forca.WAIT_MS, d.onLeave)
                    else -> Board(st, iChoose, word, d.oppName, outcome, showEnd, onShowEnd = { showEnd = true }, onGuess = { l ->
                        if (!st.over && l !in st.guessed) { d.feedback?.type(); d.send(Msg.Guess(d.myId, d.n, l)) }
                    }, onTry = { w -> if (!st.over && w.isNotBlank()) d.send(Msg.Try(d.myId, d.n, w.trim().uppercase())) })
                }
            }
        }
        ToastView(toast, Modifier.align(Alignment.TopCenter).padding(top = 110.dp))
        if (outcome != null && showEnd) {
            val original = (st.word ?: word ?: st.shown).uppercase()
            val chooserLine = "escolheu a palavra"
            val guesserLine = if (st.solved) "adivinhou com ${st.errors} ${if (st.errors == 1) "erro" else "erros"}" else "errou 6 vezes"
            SeriesEndSheet(
                mode = Multi.FORCA, outcome = outcome, oppName = d.oppName, subtitle = "A palavra era $original",
                myLine = if (iChoose) chooserLine else guesserLine, oppLine = if (iChoose) guesserLine else chooserLine,
                isHost = d.isHost, series = d.series, onAgain = d.onAgain, onLeave = d.onLeave, onClose = { showEnd = false },
                story = MatchStory.series(Multi.FORCA, d.myName, d.oppName, d.series, outcome, "Palavra: $original"),
                shareDetail = "Palavra: $original", myName = d.myName, next = d.next,
            )
        }
    }
}

/** Quem escolhe: palavra (2 a 20 letras, com acento, espaço e hífen) e tema (obrigatório). */
@Composable
private fun ChooseWord(oppName: String, onReady: (String, String) -> Unit) {
    var w by remember { mutableStateOf("") }
    var th by remember { mutableStateOf("") }
    val okWord = Forca.validWord(w)
    val okTheme = Forca.validTheme(th)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(10.dp))
        Gallows(0, false, Modifier.size(118.dp))
        Spacer(Modifier.height(8.dp))
        Text("Escolha a palavra", color = Night.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text("$oppName vai tentar adivinhar letra por letra.", color = Night.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        val colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Night.text, unfocusedTextColor = Night.text, focusedBorderColor = Night.accent, unfocusedBorderColor = Night.outline,
            cursorColor = Night.accent, focusedLabelColor = Night.accent, unfocusedLabelColor = Night.muted,
            errorBorderColor = Night.red, errorLabelColor = Night.red, errorTextColor = Night.text, errorCursorColor = Night.red,
        )
        OutlinedTextField(
            value = w, onValueChange = { w = it.take(26) }, singleLine = true, label = { Text("Palavra") },
            placeholder = { Text("Ex.: pão de queijo") }, isError = w.isNotBlank() && !okWord,
            supportingText = { Text(if (w.isNotBlank() && !okWord) "Use de 2 a 20 letras (acento, espaço e hífen valem)." else "Ela não aparece para $oppName.") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = colors,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = th, onValueChange = { th = it.take(Forca.THEME_MAX) }, singleLine = true, label = { Text("Tema") },
            placeholder = { Text("Ex.: Comida") },
            supportingText = { Text("${th.trim().length}/${Forca.THEME_MAX} · obrigatório") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = colors,
        )
        Spacer(Modifier.height(10.dp))
        if (okWord) {
            WordTiles(Forca.clean(w).uppercase(), hidden = emptySet(), highlight = emptySet(), maxTile = 30.dp)
            Spacer(Modifier.height(14.dp))
        }
        PillButton("Pronto", Night.correct, modifier = Modifier.fillMaxWidth(), enabled = okWord && okTheme) { onReady(w, th) }
        Spacer(Modifier.height(16.dp))
    }
}

/** Quem adivinha esperando a palavra; depois de 90 s, oferece sair. */
@Composable
private fun WaitChooser(oppName: String, late: Boolean, onLeave: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Gallows(0, false, Modifier.size(140.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            if (late) "Esperando $oppName escolher…" else "$oppName está escolhendo a palavra…",
            color = Night.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        if (late) {
            Spacer(Modifier.height(16.dp))
            PillButton("Sair da partida", Night.surfaceHigh, Night.text, onClick = onLeave)
        }
    }
}

/** Tabuleiro da rodada (os dois lados): tema, boneco, palavra e, para quem adivinha, o teclado e o chute. */
@Composable
private fun Board(
    st: Forca.State, iChoose: Boolean, word: String?, oppName: String, outcome: String?, showEnd: Boolean,
    onShowEnd: () -> Unit, onGuess: (Char) -> Unit, onTry: (String) -> Unit,
) {
    val secret = st.secret!!
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Tema: ${secret.theme}", color = Night.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(Shapes.pill).background(Night.surfaceHigh).padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text("Erros ${st.errors}/${Forca.MAX_ERRORS}", color = if (st.errors >= 4) Night.red else Night.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Gallows(st.errors, st.lost, Modifier.size(if (iChoose || st.over) 150.dp else 132.dp))
        // A palavra: quem escolheu vê inteira (as letras achadas em verde); quem adivinha vê a máscara.
        val original = st.word ?: word
        if (st.over && original != null) {
            WordTiles(Forca.clean(original).uppercase(), hidden = emptySet(), highlight = if (st.solved) Forca.clean(original).indices.toSet() else emptySet(), maxTile = 34.dp)
        } else if (iChoose && word != null) {
            val shownPos = st.shown.indices.filter { st.shown[it] != '.' && secret.mask[it] == '.' }.toSet()
            WordTiles(word.uppercase(), hidden = emptySet(), highlight = shownPos, maxTile = 34.dp, dimOthers = true)
        } else {
            WordTiles(st.shown, hidden = st.shown.indices.filter { st.shown[it] == '.' }.toSet(), highlight = emptySet(), maxTile = 34.dp)
        }
        Spacer(Modifier.height(10.dp))
        if (st.over) {
            Text(
                when {
                    st.solved && !iChoose -> "Você adivinhou!"
                    st.solved -> "$oppName adivinhou!"
                    !iChoose -> "Enforcado!"
                    else -> "$oppName não adivinhou!"
                },
                color = if (outcome == "me") Night.correct else Night.red, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
            )
            if (!showEnd) {
                Spacer(Modifier.height(12.dp))
                PillButton("Ver resultado", Night.correct, onClick = onShowEnd)
            }
        } else if (iChoose) {
            // Letras que o amigo já tentou.
            Text("$oppName está adivinhando…", color = Night.muted, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            GuessedLetters(st)
        } else GuessPanel(st, onGuess, onTry)
    }
}

/** Quem adivinha: chute da palavra e o teclado A–Z. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.GuessPanel(st: Forca.State, onGuess: (Char) -> Unit, onTry: (String) -> Unit) {
    Spacer(Modifier.weight(1f))
    var trying by remember { mutableStateOf(false) }
    var guess by remember { mutableStateOf("") }
    val pending = st.guessed.filter { !st.answered(it) }.toSet()
    if (trying) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = guess, onValueChange = { guess = it.take(26) }, singleLine = true, label = { Text("Chute a palavra") },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Night.text, unfocusedTextColor = Night.text, focusedBorderColor = Night.accent, unfocusedBorderColor = Night.outline,
                    cursorColor = Night.accent, focusedLabelColor = Night.accent, unfocusedLabelColor = Night.muted,
                ),
            )
            Spacer(Modifier.width(8.dp))
            PillButton("Chutar", Night.correct, enabled = guess.isNotBlank()) { onTry(guess); guess = ""; trying = false }
        }
        Spacer(Modifier.height(6.dp))
        Text("Chute errado conta 1 erro.", color = Night.muted, fontSize = 12.sp)
    } else {
        PillButton("Chutar a palavra", Night.surfaceHigh, Night.text, Modifier.fillMaxWidth()) { trying = true }
    }
    Spacer(Modifier.height(10.dp))
    AlphaKeyboard(st, pending, onGuess)
    Spacer(Modifier.height(10.dp))
}

/** Letras tentadas: acertos em verde, erros riscados em vermelho. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuessedLetters(st: Forca.State) {
    if (st.guessed.isEmpty() && st.tries.isEmpty()) {
        Text("Nenhuma letra ainda.", color = Night.muted, fontSize = 14.sp)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        st.guessed.forEach { l ->
            val c = when { l in st.hits -> Night.correct; l in st.misses -> Night.red; else -> Night.surfaceHigh }
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c), contentAlignment = Alignment.Center) {
                Text(l.toString(), color = if (c == Night.surfaceHigh) Night.text else InkDark, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
    st.tries.forEach { (w, ok) ->
        Spacer(Modifier.height(6.dp))
        Text("Chute: $w " + when (ok) { true -> "✓"; false -> "✗"; null -> "…" }, color = if (ok == false) Night.red else Night.text, fontSize = 14.sp)
    }
}

/** Teclado A–Z (7 por linha). */
@Composable
private fun AlphaKeyboard(st: Forca.State, pending: Set<Char>, onGuess: (Char) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ('A'..'Z').chunked(7).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                line.forEach { l ->
                    val bg = when { l in st.hits -> Night.correct; l in st.misses -> Night.keyAbsent; l in pending -> Night.accent; else -> Night.key }
                    val used = l in st.guessed || l in pending
                    Box(
                        Modifier.weight(1f).height(46.dp).clip(Shapes.key).background(bg).clickable(enabled = !used) { onGuess(l) }
                            .semantics { contentDescription = "Letra $l" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            l.toString(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                            color = when { l in st.hits || l in pending -> InkDark; l in st.misses -> Night.muted.copy(alpha = 0.5f); else -> Night.text },
                        )
                    }
                }
                repeat(7 - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A palavra em quadradinhos, quebrando nas palavras. [hidden] = casas vazias (máscara); [highlight] = casas em verde.
 * Espaço separa as palavras; hífen aparece como um traço.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WordTiles(text: String, hidden: Set<Int>, highlight: Set<Int>, maxTile: Dp, dimOthers: Boolean = false) {
    // Grupos de casas por palavra (índices no texto).
    val groups = ArrayList<List<Int>>()
    var cur = ArrayList<Int>()
    text.indices.forEach { i -> if (text[i] == ' ') { if (cur.isNotEmpty()) groups += cur; cur = ArrayList() } else cur += i }
    if (cur.isNotEmpty()) groups += cur
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val gap = 4.dp
        val longest = groups.maxOfOrNull { it.size } ?: 1
        val size = minOf(maxTile, (maxWidth - gap * (longest - 1)) / longest)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(size * 0.6f, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            groups.forEach { g ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    g.forEach { i ->
                        val ch = text[i]
                        if (ch == '-') {
                            Box(Modifier.size(width = size * 0.5f, height = size), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(width = size * 0.4f, height = 3.dp).clip(Shapes.pill).background(Night.muted))
                            }
                        } else {
                            val hide = i in hidden
                            val hl = i in highlight
                            Box(
                                Modifier.size(size).clip(RoundedCornerShape(size * 0.26f))
                                    .background(if (hl) Night.correct else Night.surfaceHigh)
                                    .then(if (hide) Modifier.border(1.5.dp, Night.outline, RoundedCornerShape(size * 0.26f)) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!hide) Text(
                                    ch.toString(), fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.ExtraBold,
                                    color = if (hl) InkDark else Night.text, modifier = Modifier.alpha(if (dimOthers && !hl) 0.6f else 1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Boneco da forca, arredondado nas cores do app; [errors] partes (cabeça, corpo, braços, pernas). */
@Composable
internal fun Gallows(errors: Int, lost: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 100f
        val w = 5f * u
        val pole = Night.outline
        drawLine(pole, Offset(14f * u, 92f * u), Offset(54f * u, 92f * u), w, StrokeCap.Round)
        drawLine(pole, Offset(26f * u, 92f * u), Offset(26f * u, 10f * u), w, StrokeCap.Round)
        drawLine(pole, Offset(26f * u, 10f * u), Offset(66f * u, 10f * u), w, StrokeCap.Round)
        drawLine(pole, Offset(26f * u, 26f * u), Offset(40f * u, 10f * u), w * 0.7f, StrokeCap.Round)
        drawLine(Night.muted, Offset(66f * u, 10f * u), Offset(66f * u, 22f * u), w * 0.6f, StrokeCap.Round)
        val fig = if (lost) Night.red else Night.present
        val fw = 4.6f * u
        if (errors >= 1) drawCircle(fig, 9f * u, Offset(66f * u, 31f * u), style = Stroke(fw))
        if (errors >= 2) drawLine(fig, Offset(66f * u, 40f * u), Offset(66f * u, 63f * u), fw, StrokeCap.Round)
        if (errors >= 3) drawLine(fig, Offset(66f * u, 46f * u), Offset(55f * u, 56f * u), fw, StrokeCap.Round)
        if (errors >= 4) drawLine(fig, Offset(66f * u, 46f * u), Offset(77f * u, 56f * u), fw, StrokeCap.Round)
        if (errors >= 5) drawLine(fig, Offset(66f * u, 63f * u), Offset(57f * u, 78f * u), fw, StrokeCap.Round)
        if (errors >= 6) drawLine(fig, Offset(66f * u, 63f * u), Offset(75f * u, 78f * u), fw, StrokeCap.Round)
    }
}

