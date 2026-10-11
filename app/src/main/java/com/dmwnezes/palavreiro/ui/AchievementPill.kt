package com.dmwnezes.palavreiro.ui

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Achievement
import com.dmwnezes.palavreiro.game.AchievementWatch
import com.dmwnezes.palavreiro.game.Achievements
import com.dmwnezes.palavreiro.game.Records
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow

/** Conquistas atuais calculadas a partir do que está salvo. */
fun currentAchievements(store: Store): List<Achievement> = Achievements.all(
    store.stats(Mode.DIARIO), store.stats(Mode.INFINITO), store.stats(Mode.DUETO), store.stats(Mode.QUARTETO),
    Records(Records.KEYS.associateWith { store.int(it) }),
)

/**
 * Observa o que o app salva e, quando uma conquista é desbloqueada, mostra a pílula
 * "🏆 Conquista desbloqueada" no topo, por cima de qualquer tela (fila se vierem várias).
 */
@Composable
fun AchievementNotifier(store: Store, onShow: (Achievement) -> Unit = {}) {
    val queue = remember { mutableStateListOf<Achievement>() }
    var changes by remember { mutableIntStateOf(0) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key != "ach_seen") changes++ }
        store.raw.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.raw.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(changes) {
        // Espera a partida terminar de salvar tudo antes de comparar.
        if (changes > 0) delay(400)
        val (fresh, seen) = AchievementWatch.newlyUnlocked(currentAchievements(store), store.achievementsSeen)
        if (fresh.isNotEmpty() || store.achievementsSeen == null) store.achievementsSeen = seen
        queue.addAll(fresh)
    }
    AchievementQueue(queue, onShow)
}

/** Mostra a fila uma por vez: entra deslizando do topo, fica ~3 s e sai. */
@Composable
fun AchievementQueue(queue: MutableList<Achievement>, onShow: (Achievement) -> Unit = {}) {
    var current by remember { mutableStateOf<Achievement?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(queue) {
        while (true) {
            snapshotFlow { queue.size }.first { it > 0 }
            val next = queue.removeAt(0)
            current = next
            visible = true
            onShow(next)
            delay(3000)
            visible = false
            delay(350)
            current = null
        }
    }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible && current != null,
            enter = slideInVertically(tween(320)) { -it * 2 } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(300)) { -it * 2 } + fadeOut(tween(250)),
        ) {
            current?.let { AchievementPill(it) }
        }
    }
}

/** A pílula: "🏆 Conquista desbloqueada · <emoji> <nome>". */
@Composable
fun AchievementPill(a: Achievement, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .shadow(12.dp, Shapes.pill)
            .clip(Shapes.pill)
            .background(Night.surfaceHigh)
            .border(1.5.dp, Night.present.copy(alpha = 0.7f), Shapes.pill)
            .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Night.present), contentAlignment = Alignment.Center) {
            Text("🏆", fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Conquista desbloqueada", fontSize = 12.sp, color = Night.present, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "${a.emoji} ${a.title}", fontSize = 16.sp, color = Night.text, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
