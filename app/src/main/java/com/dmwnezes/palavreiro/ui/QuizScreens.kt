package com.dmwnezes.palavreiro.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.DefineGame
import com.dmwnezes.palavreiro.game.Definition
import com.dmwnezes.palavreiro.game.Mark
import com.dmwnezes.palavreiro.game.SynPair
import com.dmwnezes.palavreiro.game.SynonymGame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Qual é a Palavra?: leia a definição e descubra a palavra. */
@Composable
fun DefineScreen(defs: List<Definition>, store: Store?, feedback: Feedback?, onBack: () -> Unit, seed: Long = System.nanoTime()) {
    val game = remember { DefineGame(defs, seed) }
    val toast = rememberToast()
    var best by remember { mutableIntStateOf(store?.int("def_best") ?: 0) }

    fun submit() {
        when (game.submit()) {
            true -> {
                feedback?.win()
                store?.add("def_right")
                store?.logActivity()
                store?.max("def_best", game.streak); best = maxOf(best, game.streak)
                toast.show(listOf("Isso!", "Acertou!", "Boa!", "Mandou bem!").random())
            }
            false -> {
                feedback?.invalid()
                toast.show(if (game.state == DefineGame.State.WRONG) "Era ${game.item.word}" else "Não é essa. Ganhou uma letra!")
            }
            null -> toast.show("Complete a palavra")
        }
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Qual é a Palavra?", onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("${game.streak}", "Sequência")
                    StatItem("${game.points}", "Pontos")
                    StatItem("$best", "Recorde")
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    game.item.text,
                    color = Night.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 27.sp,
                    modifier = Modifier.fillMaxWidth().clip(Shapes.card).background(Night.surface).padding(22.dp),
                )
                Spacer(Modifier.height(20.dp))
                AnswerSlots(game)
                Spacer(Modifier.height(10.dp))
                Text(
                    "${game.target.count { it in 'A'..'Z' }} letras · ${game.maxMisses - game.misses} ${if (game.maxMisses - game.misses == 1) "chance" else "chances"}",
                    color = Night.muted, fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                ToastView(toast)
                if (game.state != DefineGame.State.PLAYING) {
                    Spacer(Modifier.height(12.dp))
                    PillButton(if (game.state == DefineGame.State.RIGHT) "Próxima palavra" else "Começar de novo", Night.correct) { game.next() }
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text("Desistir", color = Night.muted, fontSize = 14.sp, modifier = Modifier.clip(Shapes.pill).clickable { game.giveUp(); toast.show("Era ${game.item.word}") }.padding(8.dp))
                }
            }
            LetterKeyboard(
                enterLabel = "ENVIAR",
                onLetter = { game.type(it); feedback?.type() },
                onEnter = ::submit,
                onDelete = { game.delete(); feedback?.type() },
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AnswerSlots(game: DefineGame) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val n = game.target.length
        val gap = 4.dp
        val size = minOf((maxWidth - gap * (n - 1)) / n, 44.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until n) {
                val ch = game.letterAt(i)
                val isLetter = game.target[i] in 'A'..'Z'
                if (!isLetter) {
                    Text(game.target[i].toString(), color = Night.muted, fontSize = 22.sp, modifier = Modifier.width(size / 2), textAlign = TextAlign.Center)
                } else {
                    val hint = i in game.revealed
                    val bg = when {
                        game.state == DefineGame.State.RIGHT -> Night.correct
                        game.state == DefineGame.State.WRONG -> Night.red
                        hint -> Night.present
                        else -> Night.surfaceHigh
                    }
                    LetterBox(
                        // Mostra a letra com acento quando a palavra termina.
                        if (game.state != DefineGame.State.PLAYING) game.item.word.uppercase().getOrNull(i) ?: ch else ch,
                        size, bg = bg, border = if (bg == Night.surfaceHigh) Night.outline else null,
                        textColor = if (hint && game.state == DefineGame.State.PLAYING) Night.onMark(Mark.PRESENT) else Night.text,
                    )
                }
            }
        }
    }
}

/** Sinônimos em Cadeia: escolha o sinônimo certo antes do tempo acabar. */
@Composable
fun SynonymScreen(
    pairs: List<SynPair>,
    families: List<Set<String>>,
    store: Store?,
    feedback: Feedback?,
    onBack: () -> Unit,
    seed: Long = System.nanoTime(),
    kind: ChainKind = ChainKind.SINONIMOS,
) {
    var round by remember { mutableIntStateOf(0) }
    val game = remember(round) { SynonymGame(pairs, families, seed + round) }
    var best by remember { mutableIntStateOf(store?.int(kind.bestKey) ?: 0) }
    val timer = remember(round) { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var started by remember { mutableStateOf(false) }

    // Barra de tempo de cada pergunta.
    LaunchedEffect(game.question, started, round) {
        if (!started || game.over) return@LaunchedEffect
        timer.snapTo(1f)
        timer.animateTo(0f, tween(game.secondsPerQuestion * 1000, easing = LinearEasing))
        if (game.picked == null && !game.over) {
            game.timeUp()
            feedback?.lose()
        }
    }

    LaunchedEffect(game.over) {
        if (game.over) {
            store?.logActivity()
            store?.max(kind.bestKey, game.chain)
            best = maxOf(best, game.chain)
        }
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar(kind.title, onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("${game.chain}", "Cadeia")
                    StatItem("$best", "Recorde")
                }
                Spacer(Modifier.height(22.dp))
                if (!started) {
                    Text(kind.intro, color = Night.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Aparece uma palavra e quatro opções. Toque na que ${kind.explain}. Você tem ${game.secondsPerQuestion} segundos por palavra, e a cadeia continua até o primeiro erro.",
                        color = Night.muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    PillButton("Começar", Night.correct, modifier = Modifier.fillMaxWidth()) { started = true }
                } else {
                    // Barra de tempo arredondada.
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(Shapes.pill).background(Night.surface)) {
                        Box(
                            Modifier.fillMaxWidth(timer.value).height(10.dp).clip(Shapes.pill)
                                .background(if (timer.value < 0.3f) Night.red else Night.accent)
                        )
                    }
                    Spacer(Modifier.height(26.dp))
                    Text(kind.question, color = Night.muted, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(game.question.word, color = Night.text, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    game.options.forEach { opt ->
                        val isRight = opt == game.question.synonym
                        val target = when {
                            game.picked == null && !game.over -> Night.surfaceHigh
                            isRight -> Night.correct
                            opt == game.picked -> Night.red
                            else -> Night.surface
                        }
                        val bg by animateColorAsState(target, label = "opcao")
                        Text(
                            opt,
                            color = if (bg == Night.correct || bg == Night.red) Color(0xFF1A1438) else Night.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(20.dp)).background(bg)
                                .clickable(enabled = game.picked == null && !game.over) {
                                    if (game.answer(opt)) {
                                        feedback?.reveal(game.chain % 5, Mark.CORRECT)
                                        scope.launch { delay(550); game.next() }
                                    } else {
                                        feedback?.invalid()
                                    }
                                }
                                .padding(vertical = 16.dp),
                        )
                    }
                    if (game.over) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            if (game.picked == null) "O tempo acabou!" else "Fim da cadeia!",
                            color = Night.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        )
                        Text("${game.question.word} ${kind.sign} ${game.question.synonym}", color = Night.muted, fontSize = 15.sp)
                        Spacer(Modifier.height(14.dp))
                        PillButton("Jogar de novo", Night.correct, modifier = Modifier.fillMaxWidth()) { round++ }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** Os dois jogos de cadeia: Sinônimos (mesmo sentido) e Antônimos (sentido contrário). */
enum class ChainKind(val title: String, val intro: String, val explain: String, val question: String, val sign: String, val bestKey: String) {
    SINONIMOS("Sinônimos", "Escolha o sinônimo certo", "tem o mesmo sentido", "Qual é o sinônimo de", "=", "syn_best"),
    ANTONIMOS("Antônimos", "Escolha o antônimo certo", "tem o sentido contrário", "Qual é o antônimo de", "≠", "ant_best"),
}
