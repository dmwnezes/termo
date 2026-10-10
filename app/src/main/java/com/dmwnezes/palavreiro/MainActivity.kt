package com.dmwnezes.palavreiro

import android.content.Intent
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.dmwnezes.palavreiro.data.Mode
import com.dmwnezes.palavreiro.game.Challenge
import com.dmwnezes.palavreiro.game.Records
import com.dmwnezes.palavreiro.system.Widget
import com.dmwnezes.palavreiro.ui.ChallengeScreen
import com.dmwnezes.palavreiro.game.TermoGame
import com.dmwnezes.palavreiro.ui.ConnectionsScreen
import com.dmwnezes.palavreiro.ui.DefineScreen
import com.dmwnezes.palavreiro.ui.Dest
import com.dmwnezes.palavreiro.ui.ReverseScreen
import com.dmwnezes.palavreiro.ui.SynAntScreen
import com.dmwnezes.palavreiro.ui.ArchiveGame
import com.dmwnezes.palavreiro.ui.ArchiveScreen
import com.dmwnezes.palavreiro.ui.IntruderScreen
import com.dmwnezes.palavreiro.ui.SpellingScreen
import com.dmwnezes.palavreiro.system.DailyStatus
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
        com.dmwnezes.palavreiro.system.CrashLog.install(this)
        AppGraph.init(this)
        if (savedInstanceState == null) Launch.read(intent)
        setContent { PalavreiroTheme { PalavreiroApp() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Launch.read(intent)
    }

    override fun onResume() {
        super.onResume()
        com.dmwnezes.palavreiro.system.RoomKeeper.appVisible = true
    }

    override fun onPause() {
        super.onPause()
        com.dmwnezes.palavreiro.system.RoomKeeper.appVisible = false
        Widget.refresh(this)
        com.dmwnezes.palavreiro.system.NextWordLive.update(this)
    }
}

/** Para onde abrir: atalho do ícone, widget, notificação ou link de desafio. */
object Launch {
    var pending by mutableStateOf<String?>(null)
    var challenge by mutableStateOf<String?>(null)
    /** Código da partida com amigo (?mp=...). */
    var multi by mutableStateOf<String?>(null)
    /** Muda quando o app deve voltar para a sala guardada (aviso "seu amigo entrou"). */
    var resume by mutableIntStateOf(0)

    fun read(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("dest")?.let { pending = it }
        intent.data?.getQueryParameter("d")?.let { code -> Challenge.decode(code)?.let { challenge = it } }
        intent.data?.getQueryParameter("mp")?.let { multi = it }
        if (intent.getBooleanExtra("mp_resume", false)) resume++
    }
}

private sealed interface Screen {
    data object Home : Screen
    /** [free]: aba Infinito do Dueto/Quarteto (partidas livres salvas à parte). */
    data class Game(val mode: Mode, val free: Boolean = false) : Screen
    data class Other(val dest: Dest) : Screen
    data class ChallengeGame(val word: String) : Screen
    data object Profile : Screen
    /** Partida com amigo; [code] = link recebido (null = criar uma partida). */
    data class Multi(val code: String?, val resume: com.dmwnezes.palavreiro.game.Multi.Session? = null) : Screen
    data class Archive(val game: ArchiveGame = ArchiveGame.TERMO) : Screen
    data class ArchivePlay(val game: ArchiveGame, val date: java.time.LocalDate) : Screen
}

private fun records(store: com.dmwnezes.palavreiro.data.Store) = Records(Records.KEYS.associateWith { store.int(it) })

@Composable
fun PalavreiroApp() {
    var splash by rememberSaveable { mutableStateOf(true) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var showHelp by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var foundUpdate by remember { mutableStateOf<Release?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    val store = AppGraph.store
    val games = remember { HashMap<Pair<Mode, Boolean>, TermoGame>() }
    fun game(mode: Mode, free: Boolean = false) =
        games.getOrPut(mode to free) { TermoGame(mode, AppGraph.words, store, freePlay = free) }.also { it.setHard(store.hard) }
    /** A outra aba do seletor "Do dia | Infinito" de cada jogo. */
    fun tab(s: Screen.Game, infinite: Boolean): Screen.Game = when (s.mode) {
        Mode.DIARIO, Mode.INFINITO -> Screen.Game(if (infinite) Mode.INFINITO else Mode.DIARIO)
        else -> Screen.Game(s.mode, free = infinite)
    }

    fun open(d: Dest) {
        screen = when (d) {
            Dest.TERMO -> Screen.Game(Mode.DIARIO)
            Dest.INFINITO -> Screen.Game(Mode.INFINITO)
            Dest.DUETO -> Screen.Game(Mode.DUETO)
            Dest.QUARTETO -> Screen.Game(Mode.QUARTETO)
            Dest.ARQUIVO -> Screen.Archive()
            Dest.DESAFIAR -> Screen.Multi(null)
            else -> Screen.Other(d)
        }
    }

    // Abre o destino pedido por atalho, widget, notificação ou link.
    LaunchedEffect(Launch.pending, Launch.challenge, Launch.multi, Launch.resume, splash) {
        if (splash) return@LaunchedEffect
        Launch.multi?.let { code ->
            Launch.multi = null
            // O link da mesma sala em que você já está guardado: volta para ela em vez de entrar de novo.
            val kept = com.dmwnezes.palavreiro.system.RoomKeeper.session(store)
            val same = kept != null && com.dmwnezes.palavreiro.game.Multi.parse(code)?.room == kept.room?.room
            if (screen !is Screen.Multi || !same) screen = if (same) Screen.Multi(null, kept) else Screen.Multi(code)
            return@LaunchedEffect
        }
        // Sala guardada (o app foi fechado enquanto você mandava o link): volta direto para ela.
        if (screen !is Screen.Multi) com.dmwnezes.palavreiro.system.RoomKeeper.session(store)?.let { screen = Screen.Multi(null, it); return@LaunchedEffect }
        Launch.challenge?.let { screen = Screen.ChallengeGame(it); Launch.challenge = null; return@LaunchedEffect }
        Launch.pending?.let { p ->
            Launch.pending = null
            if (p == "HOME") screen = Screen.Home else runCatching { Dest.valueOf(if (p == "ANTONIMOS") "SINONIMOS" else p) }.getOrNull()?.let(::open)
        }
    }

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
        if (showHelp) showHelp = false
        else { val cur = screen; screen = if (cur is Screen.ArchivePlay) Screen.Archive(cur.game) else Screen.Home; refresh++ }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "tela",
        ) { s ->
            when (s) {
                Screen.Home -> {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    LaunchedEffect(refresh) { com.dmwnezes.palavreiro.system.NextWordLive.update(ctx, store) }
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
                        )
                    }
                    // Painel "Hoje" e confete uma vez por dia quando os 5 desafios estiverem feitos.
                    val status = remember(refresh) { DailyStatus.read(store) }
                    val celebrate = remember(refresh) {
                        val today = java.time.LocalDate.now().toString()
                        (status.doneCount == 5 && store.text("alldone_day") != today).also {
                            if (it) { store.setText("alldone_day", today); store.add("alldone_days") }
                        }
                    }
                    HomeScreen(
                        badges = badges,
                        onOpen = ::open,
                        onProfile = { screen = Screen.Profile },
                        today = status,
                        celebrate = celebrate,
                    )
                }
                is Screen.Other -> {
                    val back: () -> Unit = { screen = Screen.Home; refresh++ }
                    when (s.dest) {
                        Dest.CONEXOES -> ConnectionsScreen(AppGraph.connections, AppGraph.connFamilies, store, AppGraph.feedback, back)
                        Dest.REVERSO -> ReverseScreen(AppGraph.words, store, AppGraph.feedback, back)
                        Dest.DEFINICAO -> DefineScreen(AppGraph.definitions, store, AppGraph.feedback, back)
                        Dest.SINONIMOS -> SynAntScreen(AppGraph.synonyms, AppGraph.families, AppGraph.antonyms, AppGraph.antonymFamilies, store, AppGraph.feedback, back)
                        Dest.DESAFIAR -> {}
                        Dest.INTRUSO -> IntruderScreen(AppGraph.connections, AppGraph.connFamilies, store, AppGraph.feedback, back)
                        Dest.ORTOGRAFIA -> SpellingScreen(AppGraph.spelling, store, AppGraph.feedback, back)
                        Dest.MESTRE -> com.dmwnezes.palavreiro.ui.MestreScreen(AppGraph.mestre, store, AppGraph.feedback, back)
                        else -> {}
                    }
                }
                is Screen.ChallengeGame -> {
                    val g = remember(s.word) { TermoGame(Mode.DESAFIO, AppGraph.words, store, challenge = s.word).also { it.setHard(store.hard) } }
                    GameScreen(
                        game = g,
                        feedback = AppGraph.feedback,
                        onBack = { screen = Screen.Home; refresh++ },
                        onPlayInfinite = { screen = Screen.Game(Mode.INFINITO) },
                        onHelp = { showHelp = true },
                        meanings = AppGraph.meanings,
                    )
                }
                is Screen.Game -> {
                    val inf = s.mode == Mode.INFINITO || s.free
                    GameScreen(
                        game = game(s.mode, s.free).also { it.refreshIfNewDay() },
                        feedback = AppGraph.feedback,
                        onBack = { screen = Screen.Home; refresh++ },
                        onPlayInfinite = { screen = tab(s, true) },
                        onHelp = { showHelp = true },
                        meanings = AppGraph.meanings,
                        infiniteTab = inf,
                        onSwitchTab = { screen = tab(s, it) },
                    )
                }
                is Screen.Multi -> com.dmwnezes.palavreiro.ui.MultiScreen(
                    words = AppGraph.words,
                    store = store,
                    ntfy = AppGraph.ntfy,
                    feedback = AppGraph.feedback,
                    onBack = { screen = Screen.Home; refresh++ },
                    joinCode = s.code,
                    meanings = AppGraph.meanings,
                    resume = s.resume,
                )
                is Screen.Archive -> ArchiveScreen(
                    store = store,
                    onBack = { screen = Screen.Home; refresh++ },
                    onPlay = { g, d -> screen = Screen.ArchivePlay(g, d) },
                    initialGame = s.game,
                )
                is Screen.ArchivePlay -> {
                    val back: () -> Unit = { screen = Screen.Archive(s.game); refresh++ }
                    when (s.game) {
                        ArchiveGame.CONEXOES -> ConnectionsScreen(AppGraph.connections, AppGraph.connFamilies, store, AppGraph.feedback, back, date = s.date, archive = true)
                        else -> {
                            val mode = when (s.game) { ArchiveGame.DUETO -> Mode.DUETO; ArchiveGame.QUARTETO -> Mode.QUARTETO; else -> Mode.DIARIO }
                            val g = remember(s) { TermoGame(mode, AppGraph.words, store, archive = s.date) }
                            GameScreen(
                                game = g,
                                feedback = AppGraph.feedback,
                                onBack = back,
                                onPlayInfinite = { screen = Screen.Game(Mode.INFINITO) },
                                onHelp = { showHelp = true },
                                meanings = AppGraph.meanings,
                            )
                        }
                    }
                }
                Screen.Profile -> ProfileScreen(
                    store = store,
                    stats = remember(refresh) { Mode.entries.associateWith { store.stats(it) } },
                    records = remember(refresh) { records(store) },
                    onBack = { screen = Screen.Home; refresh++ },
                    onCheckUpdates = { showUpdate = true },
                    onHelp = { showHelp = true },
                    onSynced = { refresh++ },
                )
            }
        }
        if (showHelp) HelpSheet { showHelp = false }
        // O app fechou por um erro da última vez: mostra o motivo para copiar e mandar.
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var crash by remember { mutableStateOf(com.dmwnezes.palavreiro.system.CrashLog.take(ctx)) }
        crash?.let { text ->
            com.dmwnezes.palavreiro.ui.BottomSheet({ crash = null }) {
                com.dmwnezes.palavreiro.ui.SheetTitle("O app fechou por um erro", "Desculpe! Copie os detalhes e mande para @dmwnezes para ele corrigir.")
                androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                com.dmwnezes.palavreiro.ui.PillButton("Copiar detalhes", com.dmwnezes.palavreiro.ui.Night.accent, modifier = Modifier.fillMaxWidth()) {
                    ctx.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("Erro do Palavreiro", text))
                    crash = null
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                com.dmwnezes.palavreiro.ui.PillButton("Fechar", com.dmwnezes.palavreiro.ui.Night.surfaceHigh, com.dmwnezes.palavreiro.ui.Night.text, Modifier.fillMaxWidth()) { crash = null }
            }
        }
        if (showUpdate) UpdateDialog(onDismiss = { showUpdate = false })
        foundUpdate?.let { r ->
            UpdateDialog(initial = r, onSkip = { store.skippedUpdate = it.tag }, onDismiss = { foundUpdate = null })
        }
    }
}
