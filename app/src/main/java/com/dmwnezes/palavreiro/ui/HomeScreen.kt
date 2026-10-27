package com.dmwnezes.palavreiro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.dmwnezes.palavreiro.game.Multi

/** Destinos da tela inicial. */
enum class Dest { TERMO, INFINITO, DUETO, QUARTETO, CONEXOES, CACA, DEFINICAO, SINONIMOS, DESAFIAR, MESTRE, INTRUSO, ORTOGRAFIA, ARQUIVO, TRIVIA, MEMORIA,
    MATIZ, PODIO, AMPULHETA, SUDOKU, XADREZ, NAVAL }

/** Informação de cada cartão da tela inicial. */
private data class GameCardInfo(
    val dest: Dest,
    val title: String,
    val subtitle: String,
    val badge: String?,
)

/**
 * Tela inicial: abas por categoria, um cartão por jogo, créditos e barra fixa com Jogos, Amigos e Perfil.
 * [badges] traz o selo de cada jogo diário ("Novo", "Feito hoje ✓").
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    badges: Map<Dest, String?>,
    onOpen: (Dest) -> Unit,
    onProfile: () -> Unit,
    today: com.dmwnezes.palavreiro.system.DailyStatus? = null,
    celebrate: Boolean = false,
    scroll: androidx.compose.foundation.ScrollState = rememberScrollState(),
) {
    val info = listOf(
        GameCardInfo(Dest.TERMO, "Termo", "Uma palavra nova por dia", badges[Dest.TERMO]),
        GameCardInfo(Dest.DUETO, "Dueto", "Duas palavras, 7 tentativas", badges[Dest.DUETO]),
        GameCardInfo(Dest.QUARTETO, "Quarteto", "Quatro palavras, 9 tentativas", badges[Dest.QUARTETO]),
        GameCardInfo(Dest.CONEXOES, "Conexões", "Separe 16 palavras em 4 grupos", badges[Dest.CONEXOES]),
        GameCardInfo(Dest.CACA, "Caça-Palavras", "Ache as palavras do tema do dia", badges[Dest.CACA]),
        GameCardInfo(Dest.INTRUSO, "Intruso", "Ache a palavra que não pertence ao grupo", null),
        GameCardInfo(Dest.ORTOGRAFIA, "Certo ou Errado", "A palavra está escrita certo?", null),
        GameCardInfo(Dest.DEFINICAO, "Qual é a Palavra?", "Descubra a palavra pela definição", null),
        GameCardInfo(Dest.SINONIMOS, "Sinônimo ou Antônimo", "Mesmo sentido ou o contrário? Leia a pergunta!", null),
        GameCardInfo(Dest.MEMORIA, "Jogo da Memória", "Ache os pares de sinônimos", null),
        GameCardInfo(Dest.MESTRE, "Mestre Mandou", "Obedeça só quando o mestre mandar", null),
        GameCardInfo(Dest.MATIZ, "Matiz", "Memorize a cor e recrie com R, G e B", badges[Dest.MATIZ]),
        GameCardInfo(Dest.SUDOKU, "Quadrante", "Sudoku: um por nível todo dia, ou infinitos", null),
        GameCardInfo(Dest.XADREZ, "Xeque", "Xadrez contra o computador", null),
        GameCardInfo(Dest.NAVAL, "Batalha Naval", "Afunde a frota do computador ou do amigo", null),
        GameCardInfo(Dest.TRIVIA, "Trivia", "Gire a roleta e junte as 6 medalhas", null),
        GameCardInfo(Dest.PODIO, "Pódio", "Monte o top 5 em 5 tentativas", badges[Dest.PODIO]),
        GameCardInfo(Dest.AMPULHETA, "Ampulheta", "Encaixe os fatos na linha do tempo", badges[Dest.AMPULHETA]),
    ).associateBy { it.dest }
    // Mesmo layout do site no celular: abas por categoria, cartões em duas colunas e barra fixa embaixo.
    val cats = listOf(
        HomeCat("Palavras", "Jogos de Palavras", Night.correct, listOf(Dest.TERMO, Dest.DUETO, Dest.QUARTETO, Dest.CONEXOES, Dest.CACA, Dest.INTRUSO, Dest.ORTOGRAFIA, Dest.DEFINICAO, Dest.SINONIMOS, Dest.MEMORIA)),
        HomeCat("Lógica", "Jogos de Lógica", Night.blue, listOf(Dest.MESTRE, Dest.MATIZ, Dest.SUDOKU, Dest.XADREZ, Dest.NAVAL)),
        HomeCat("Conhecimento", "Jogos de Conhecimento", Night.present, listOf(Dest.TRIVIA, Dest.PODIO, Dest.AMPULHETA)),
    )
    var tab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val cur = cats[tab]
    Box(Modifier.fillMaxSize().background(Night.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp)) {
                Text("Palavreiro", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Night.text)
                Spacer(Modifier.height(6.dp))
                FiveSquares(square = 10.dp, gap = 3.dp)
            }
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.weight(1f).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                today?.let { TodayPanel(it, onOpen) }
                CategoryTabs(cats, tab) { tab = it }
                Text(cur.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Night.text, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
                cur.games.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { d -> GameTile(info.getValue(d), Modifier.weight(1f)) { onOpen(d) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                CreatorCredit(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp))
            }
            BottomNav(onFriend = { onOpen(Dest.DESAFIAR) }, onProfile = onProfile)
        }
        if (celebrate) Confetti(1) {}
    }
}

private data class HomeCat(val short: String, val title: String, val color: Color, val games: List<Dest>)

/** Seletor Palavras / Lógica / Conhecimento; a aba escolhida ganha a cor da categoria. */
@Composable
private fun CategoryTabs(cats: List<HomeCat>, selected: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Night.surface).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cats.forEachIndexed { i, c ->
            val on = i == selected
            val bg by androidx.compose.animation.animateColorAsState(if (on) c.color else Color.Transparent, label = "aba")
            Column(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(bg)
                    .clickable(role = androidx.compose.ui.semantics.Role.Tab) { onPick(i) }
                    .semantics { this.selected = on },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val fg = if (on) Color(0xFF1A1438) else Night.muted
                Text(c.short, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, softWrap = false)
                Text("${c.games.size} jogos", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(alpha = 0.85f), maxLines = 1)
            }
        }
    }
}

/** Cartão do jogo em meia largura: ícone, selo no canto, nome e descrição. */
@Composable
private fun GameTile(info: GameCardInfo, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(172.dp)
            .clip(Shapes.card)
            .background(Night.surface)
            .border(1.dp, Night.outline.copy(alpha = 0.45f), Shapes.card)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) { CardIcon(info.dest) }
            Text(info.title, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, color = Night.text, maxLines = 2)
            Text(info.subtitle, fontSize = 13.sp, lineHeight = 16.sp, color = Night.muted, maxLines = 3)
        }
        info.badge?.let {
            val fresh = it == "Novo"
            Text(
                it, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                color = if (fresh) Color(0xFF1A1438) else Night.text,
                modifier = Modifier.align(Alignment.TopEnd).clip(Shapes.pill)
                    .background(if (fresh) Night.present else Night.surfaceHigh)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** Barra fixa embaixo: Jogos (tela atual), Amigos (partida ao vivo) e Perfil. */
@Composable
private fun BottomNav(onFriend: () -> Unit, onProfile: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp).height(68.dp).clip(RoundedCornerShape(34.dp)).background(Night.surfaceHigh),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(Icons.Rounded.GridView, "Jogos", true) {}
        NavItem(Icons.Rounded.Group, "Amigos", false, onFriend)
        NavItem(Icons.Rounded.Person, "Perfil", false, onProfile)
    }
}

@Composable
private fun NavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val c = if (on) Night.present else Night.muted
    Column(
        Modifier.size(width = 76.dp, height = 56.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = c)
    }
}

/**
 * Painel "Hoje" compacto: uma linha com os 5 desafios do dia em bolinhas (verde = feito; toque abre o jogo),
 * a contagem à direita ("Tudo feito hoje 🎉" quando completa) e o atalho para o Arquivo.
 */
@Composable
private fun TodayPanel(status: com.dmwnezes.palavreiro.system.DailyStatus, onOpen: (Dest) -> Unit) {
    val items = listOf(
        TodayItem(Dest.TERMO, "Termo", "T", status.termo), TodayItem(Dest.DUETO, "Dueto", "D", status.dueto),
        TodayItem(Dest.QUARTETO, "Quarteto", "Q", status.quarteto), TodayItem(Dest.CONEXOES, "Conexões", "C", status.conexoes),
        TodayItem(Dest.CACA, "Caça-Palavras", "CP", status.caca),
    )
    val done = items.count { it.done }
    val all = done == items.size
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Night.surface)
            .border(1.dp, Night.outline.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!all) {
                Text("Hoje", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Night.text)
                Spacer(Modifier.width(10.dp))
            }
            items.forEach { TodayDot(it) { onOpen(it.dest) } }
            Spacer(Modifier.weight(1f))
            Text(
                if (all) "Tudo feito hoje 🎉" else "$done/${items.size}",
                fontSize = if (all) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                color = if (all) Night.correct else Night.muted,
                modifier = androidx.compose.ui.Modifier.semantics { contentDescription = if (all) "Tudo feito hoje" else "$done de ${items.size} desafios feitos hoje" },
            )
            IconButton(onClick = { onOpen(Dest.ARQUIVO) }, modifier = Modifier.size(40.dp)) {
                Text("🗂️", fontSize = 17.sp, modifier = Modifier.semantics { contentDescription = "Arquivo de desafios" })
            }
        }
        Box(Modifier.padding(end = 10.dp).fillMaxWidth().height(3.dp).clip(Shapes.pill).background(Night.bgBottom)) {
            Box(Modifier.fillMaxWidth(done / items.size.toFloat()).height(3.dp).clip(Shapes.pill).background(Night.correct))
        }
    }
}

private data class TodayItem(val dest: Dest, val name: String, val short: String, val done: Boolean)

/** Bolinha de um desafio do dia: cheia e verde com ✓ quando feito, contorno com a inicial quando não. */
@Composable
private fun TodayDot(item: TodayItem, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${item.name}: ${if (item.done) "feito" else "falta fazer"}" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .then(if (item.done) Modifier.background(Night.correct) else Modifier.border(1.5.dp, Night.outline, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (item.done) "✓" else item.short, fontSize = if (item.short.length > 1 && !item.done) 9.sp else 11.sp, lineHeight = 11.sp,
                fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false,
                color = if (item.done) Color(0xFF1A1438) else Night.muted,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Night.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 6.dp))
}

/** Desenhos pequenos de cada cartão (também usados nos recordes do Perfil). */
@Composable
internal fun CardIcon(dest: Dest) {
    when (dest) {
        Dest.TERMO, Dest.INFINITO -> MiniBoard(12.dp, 0)
        Dest.DUETO -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { MiniBoard(7.dp, 0); MiniBoard(7.dp, 2) }
        Dest.QUARTETO -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { MiniBoard(7.dp, 0); MiniBoard(7.dp, 1) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { MiniBoard(7.dp, 2); MiniBoard(7.dp, 3) }
        }
        Dest.CONEXOES -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Night.levels.forEach { c ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(4) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c)) } }
            }
        }
        Dest.CACA -> Box(Modifier.size(49.dp)) {
            // Grade 3×3 de letras com a marcação na diagonal (centro da 1ª à última casa).
            Canvas(Modifier.fillMaxSize()) {
                val c = size.width * 7.5f / 49f
                drawLine(Night.present.copy(alpha = 0.7f), Offset(c, c), Offset(size.width - c, size.height - c), strokeWidth = size.width * 0.28f, cap = StrokeCap.Round)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("CAS", "OLE", "PAJ").forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        line.forEach {
                            Box(Modifier.size(15.dp), contentAlignment = Alignment.Center) {
                                Text("$it", color = Night.text, fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
        Dest.DEFINICAO -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.width(46.dp).height(5.dp).clip(Shapes.pill).background(Night.muted.copy(alpha = 0.6f)))
            Box(Modifier.width(34.dp).height(5.dp).clip(Shapes.pill).background(Night.muted.copy(alpha = 0.6f)))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(null, 'A', null, null).forEach { ch ->
                    Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(if (ch != null) Night.present else Night.surfaceHigh))
                }
            }
        }
        Dest.DESAFIAR -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            // Dois mini tabuleiros lado a lado: a partida com amigo.
            MiniBoard(8.dp, 0); MiniBoard(8.dp, 3)
        }
        Dest.INTRUSO -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(Night.surfaceHigh, Night.surfaceHigh, Night.red, Night.surfaceHigh, Night.surfaceHigh).forEach {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(it))
            }
        }
        Dest.ORTOGRAFIA -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("✓" to Night.correct, "✗" to Night.red).forEach { (t, c) ->
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(c), contentAlignment = Alignment.Center) {
                    Text(t, color = Color(0xFF1A1438), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Dest.ARQUIVO -> Text("🗂️", fontSize = 26.sp)
        Dest.MESTRE -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("👑", fontSize = 20.sp)
            Pill("TOQUE", Night.present)
        }
        Dest.SINONIMOS -> SynAntIcon()
        Dest.TRIVIA -> TriviaWheel(0f, 50.dp, pointer = false)
        Dest.MEMORIA -> MemoryIcon()
        Dest.MATIZ -> MatizIcon()
        Dest.PODIO -> PodioIcon()
        Dest.AMPULHETA -> AmpulhetaIcon()
        Dest.SUDOKU -> SudokuIcon()
        Dest.XADREZ -> ChessHomeIcon(44.dp)
        Dest.NAVAL -> GameIcon(Multi.NAVAL, 46.dp)
    }
}

/** Sudoku: bloco 3×3 com alguns números (um lilás, escrito pelo jogador). */
@Composable
private fun SudokuIcon() {
    val digits = listOf("5", "", "3", "", "7", "", "1", "", "9")
    Column(Modifier.clip(RoundedCornerShape(8.dp)).background(Night.surfaceHigh).padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (c in 0 until 3) {
                val d = digits[r * 3 + c]
                Box(Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(if (r * 3 + c == 4) Night.accent else Night.surface), contentAlignment = Alignment.Center) {
                    Text(d, color = if (r * 3 + c == 4) Color(0xFF1A1438) else Night.text, fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Jogo da Memória: duas cartas viradas (roxas) e um par achado (verde). */
@Composable
private fun MemoryIcon() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(listOf(false, true), listOf(true, false)).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                line.forEach { open ->
                    Box(
                        Modifier.size(width = 20.dp, height = 24.dp).clip(RoundedCornerShape(6.dp)).background(if (open) Night.correct else Night.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (open) "=" else "?", color = Color(0xFF1A1438), fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

/** Sinônimo ou Antônimo: pílula verde "=" sobre pílula vermelha "≠", com um quadradinho de palavra ao lado de cada. */
@Composable
private fun SynAntIcon() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("=" to Night.correct, "≠" to Night.red).forEach { (sym, c) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(width = 14.dp, height = 20.dp).clip(RoundedCornerShape(5.dp)).background(Night.surfaceHigh))
                Box(Modifier.size(width = 32.dp, height = 20.dp).clip(Shapes.pill).background(c), contentAlignment = Alignment.Center) {
                    Text(sym, color = Color(0xFF1A1438), fontSize = 16.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color) {
    Text(
        text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        color = if (bg == Night.correct || bg == Night.red || bg == Night.present) Color(0xFF1A1438) else Night.text,
        modifier = Modifier.clip(Shapes.pill).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun MiniBoard(cell: Dp, seed: Int) {
    val gap = 2.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(3) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(3) { c ->
                    val v = (r * 3 + c + seed) % 5
                    val col = when {
                        r == 2 -> Night.correct
                        v == 0 -> Night.present
                        v == 3 -> Night.correct
                        else -> Night.absent
                    }
                    Box(Modifier.size(cell).clip(RoundedCornerShape(cell * 0.3f)).background(col))
                }
            }
        }
    }
}
