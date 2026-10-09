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
import com.dmwnezes.palavreiro.game.Records
import com.dmwnezes.palavreiro.game.TermoGame
import com.dmwnezes.palavreiro.ui.ConnectionsScreen
import com.dmwnezes.palavreiro.ui.DefineScreen
import com.dmwnezes.palavreiro.ui.Dest
import com.dmwnezes.palavreiro.ui.ReverseScreen
import com.dmwnezes.palavreiro.ui.SynonymScreen
import com.dmwnezes.palavreiro.ui.WordSearchScreen
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
    data class Other(val dest: Dest) : Screen
    data object Profile : Screen
}

private fun records(store: com.dmwnezes.palavreiro.data.Store) = Records(
    connWon = store.int("conn_won"), connPerfect = store.int("conn_perfect"),
    wsPlayed = store.int("ws_played"), wsBest = store.int("ws_best"),
    revApp = store.int("rev_appwins"), revUser = store.int("rev_userwins"),
    defBest = store.int("def_best"), defRight = store.int("def_right"),
    synBest = store.int("syn_best"),
)

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
                    val badges = remember(refresh) {
                        val today = java.time.LocalDate.now().toString()
                        fun daily(m: Mode): String {
                            val g = game(m).also { it.refreshIfNewDay() }
                            return if (store.text("daily_done_${m.key}") == today || (g.isDaily && g.over)) "Feito hoje ✓" else "Novo"
                        }
                        mapOf(
                            Dest.TERMO to daily(Mode.DIARIO),
                            Dest.DUETO to daily(Mode.DUETO),
                            Dest.QUARTETO to daily(Mode.QUARTETO),
                            Dest.CONEXOES to if (store.text("conn_done") == today) "Feito hoje ✓" else "Novo",
                            Dest.CACA to if (store.text("ws_done") == today) "Feito hoje ✓" else "Novo",
                        )
                    }
                    HomeScreen(
                        badges = badges,
                        onOpen = { d ->
                            screen = when (d) {
                                Dest.TERMO -> Screen.Game(Mode.DIARIO)
                                Dest.INFINITO -> Screen.Game(Mode.INFINITO)
                                Dest.DUETO -> Screen.Game(Mode.DUETO)
                                Dest.QUARTETO -> Screen.Game(Mode.QUARTETO)
                                else -> Screen.Other(d)
                            }
                        },
                        onProfile = { screen = Screen.Profile },
                    )
                }
                is Screen.Other -> {
                    val back: () -> Unit = { screen = Screen.Home; refresh++ }
                    when (s.dest) {
                        Dest.CONEXOES -> ConnectionsScreen(AppGraph.connections, store, AppGraph.feedback, back)
                        Dest.CACA -> WordSearchScreen(AppGraph.themes, store, AppGraph.feedback, back)
                        Dest.REVERSO -> ReverseScreen(AppGraph.words, store, AppGraph.feedback, back)
                        Dest.DEFINICAO -> DefineScreen(AppGraph.definitions, store, AppGraph.feedback, back)
                        Dest.SINONIMOS -> SynonymScreen(AppGraph.synonyms, AppGraph.families, store, AppGraph.feedback, back)
                        else -> {}
                    }
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
                    stats = remember(refresh) { Mode.entries.associateWith { store.stats(it) } },
                    records = remember(refresh) { records(store) },
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
