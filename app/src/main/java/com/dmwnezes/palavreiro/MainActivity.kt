package com.dmwnezes.palavreiro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.game.TermoGame
import com.dmwnezes.palavreiro.ui.GameScreen
import com.dmwnezes.palavreiro.ui.HelpSheet
import com.dmwnezes.palavreiro.ui.HomeScreen
import com.dmwnezes.palavreiro.ui.PalavreiroTheme
import com.dmwnezes.palavreiro.ui.ProfileScreen
import com.dmwnezes.palavreiro.ui.SplashCredits
import com.dmwnezes.palavreiro.update.Release
import com.dmwnezes.palavreiro.update.UpdateDialog
import com.dmwnezes.palavreiro.update.Updater

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        setContent { PalavreiroTheme { PalavreiroApp() } }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Game(val mode: Mode) : Screen
    data object Profile : Screen
}

@Composable
fun PalavreiroApp() {
    var splash by rememberSaveable { mutableStateOf(true) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var showHelp by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var foundUpdate by remember { mutableStateOf<Release?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    val store = AppGraph.store
    val games = remember { HashMap<Mode, TermoGame>() }
    fun game(mode: Mode) = games.getOrPut(mode) { TermoGame(mode, AppGraph.words, store) }

    // Checagem automática de atualização ao abrir o app.
    LaunchedEffect(Unit) {
        val updater = Updater(AppGraph.http)
        runCatching { updater.latest() }.getOrNull()?.let { r ->
            if (updater.isNewer(r) && store.skippedUpdate != r.tag) foundUpdate = r
        }
    }

    if (splash) {
        SplashCredits {
            splash = false
            if (!store.seenHelp) { showHelp = true; store.seenHelp = true }
        }
        return
    }

    BackHandler(enabled = screen != Screen.Home || showHelp) {
        if (showHelp) showHelp = false else { screen = Screen.Home; refresh++ }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "tela",
        ) { s ->
            when (s) {
                Screen.Home -> {
                    val daily = remember(refresh) { game(Mode.DIARIO).also { it.refreshIfNewDay() } }
                    HomeScreen(
                        dailyDone = daily.over,
                        dailyWon = daily.won,
                        onPlay = { screen = Screen.Game(it) },
                        onProfile = { screen = Screen.Profile },
                    )
                }
                is Screen.Game -> GameScreen(
                    game = game(s.mode),
                    feedback = AppGraph.feedback,
                    onBack = { screen = Screen.Home; refresh++ },
                    onPlayInfinite = { screen = Screen.Game(Mode.INFINITO) },
                    onHelp = { showHelp = true },
                )
                Screen.Profile -> ProfileScreen(
                    store = store,
                    daily = remember(refresh) { store.stats(Mode.DIARIO) },
                    infinite = remember(refresh) { store.stats(Mode.INFINITO) },
                    onBack = { screen = Screen.Home; refresh++ },
                    onCheckUpdates = { showUpdate = true },
                    onHelp = { showHelp = true },
                )
            }
        }
        if (showHelp) HelpSheet { showHelp = false }
        if (showUpdate) UpdateDialog(onDismiss = { showUpdate = false })
        foundUpdate?.let { r ->
            UpdateDialog(initial = r, onSkip = { store.skippedUpdate = it.tag }, onDismiss = { foundUpdate = null })
        }
    }
}
