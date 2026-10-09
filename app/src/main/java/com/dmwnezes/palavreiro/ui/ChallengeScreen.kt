package com.dmwnezes.palavreiro.ui

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.Feedback
import com.dmwnezes.palavreiro.game.Challenge
import com.dmwnezes.palavreiro.game.Words

/** Desafiar um amigo: escolha uma palavra e mande o link. */
@Composable
fun ChallengeScreen(words: Words, feedback: Feedback?, onBack: () -> Unit) {
    val context = LocalContext.current
    val toast = rememberToast()
    val letters = remember { mutableStateListOf<Char>() }
    var link by remember { mutableStateOf<String?>(null) }

    fun share(l: String) {
        val text = "Te desafio no Palavreiro! Descubra a minha palavra de 5 letras: $l"
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Enviar desafio"))
    }

    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar("Desafiar um amigo", onBack)
            Column(
                Modifier.weight(1f).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Escolha uma palavra de 5 letras", color = Night.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "O app cria um link. Quem abrir joga um Termo com a sua palavra, no app ou no navegador.",
                    color = Night.muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until Words.WORD_LENGTH) {
                        val ch = letters.getOrNull(i)
                        LetterBox(ch, 54.dp, bg = if (link != null) Night.correct else Night.surfaceHigh, border = if (link != null) null else Night.outline)
                    }
                }
                Spacer(Modifier.height(12.dp))
                ToastView(toast)
                link?.let { l ->
                    Spacer(Modifier.height(8.dp))
                    Text(l, color = Night.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    PillButton("Enviar desafio", Night.correct, modifier = Modifier.fillMaxWidth()) { share(l) }
                    Spacer(Modifier.height(8.dp))
                    PillButton("Escolher outra", Night.surfaceHigh, Night.text, Modifier.fillMaxWidth()) { letters.clear(); link = null }
                }
            }
            if (link == null) {
                LetterKeyboard(
                    enterLabel = "CRIAR",
                    onLetter = { if (letters.size < Words.WORD_LENGTH) { letters += it; feedback?.type() } },
                    onEnter = {
                        val w = letters.joinToString("")
                        when {
                            w.length < Words.WORD_LENGTH -> toast.show("Complete as 5 letras")
                            !words.isAccepted(w) -> { feedback?.invalid(); toast.show("Palavra não aceita") }
                            else -> { feedback?.win(); link = Challenge.link(w) }
                        }
                    },
                    onDelete = { if (letters.isNotEmpty()) letters.removeAt(letters.lastIndex) },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
