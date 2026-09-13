package dev.gachahub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import dev.gachahub.data.Character
import dev.gachahub.data.Game

/** Shared visual language for all four games. Game accents change the emphasis, not the structure. */
object GachaTokens {
    val canvas = Color(0xFF0A0C11)
    val panel = Color(0xFF141821)
    val panelRaised = Color(0xFF1B202B)
    val outline = Color(0xFF2B3341)
    val muted = Color(0xFF9AA4B5)
    val success = Color(0xFF83D49B)
    val warning = Color(0xFFF2C26B)
    val spacing = 16.dp
    val radius = 24.dp
}

private fun gameAccent(game: Game) = when (game) {
    Game.ZZZ -> Color(0xFFFFC857)
    Game.HSR -> Color(0xFFB9A5FF)
    Game.GENSHIN -> Color(0xFF69D5E8)
    Game.WUWA -> Color(0xFF67D6C2)
}

@Composable
fun GachaTheme(game: Game? = null, content: @Composable () -> Unit) {
    val accent = gameAccent(game ?: Game.ZZZ)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF17130A),
        secondary = accent.copy(alpha = .78f),
        background = GachaTokens.canvas,
        onBackground = Color(0xFFF5F7FA),
        surface = GachaTokens.panel,
        onSurface = Color(0xFFF5F7FA),
        surfaceVariant = GachaTokens.panelRaised,
        onSurfaceVariant = GachaTokens.muted,
        outline = GachaTokens.outline
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(
        displaySmall = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
        headlineSmall = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyMedium = MaterialTheme.typography.bodyMedium.copy(lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.12f),
        labelLarge = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    ), content = content)
}

@Composable
fun GachaCard(modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(GachaTokens.radius),
        colors = CardDefaults.cardColors(containerColor = GachaTokens.panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, GachaTokens.outline.copy(alpha = .72f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(GachaTokens.spacing), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun HeroPanel(game: Game, title: String, subtitle: String, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit = {}) {
    val accent = gameAccent(game)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = .32f), GachaTokens.panel, GachaTokens.panel)))
            .border(1.dp, accent.copy(alpha = .42f), RoundedCornerShape(28.dp))
            .padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        content = {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = GachaTokens.muted)
            }
            content()
        }
    )
}

@Composable
fun MetricPill(label: String, value: String, accent: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = .10f))
            .border(1.dp, accent.copy(alpha = .22f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = GachaTokens.muted)
    }
}

@Composable
fun StatusBadge(text: String, positive: Boolean = true, modifier: Modifier = Modifier) {
    val color = if (positive) GachaTokens.success else GachaTokens.warning
    Surface(modifier = modifier, color = color.copy(alpha = .14f), shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun CharacterArtwork(character: Character, owned: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .30f), GachaTokens.panelRaised)))
            .semantics { contentDescription = "Arte de ${character.name}" },
        contentAlignment = Alignment.Center
    ) {
        Text(character.name.take(2).uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        if (character.image.isNotBlank()) AsyncImage(
            model = character.image,
            contentDescription = character.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (owned) StatusBadge("NA CONTA", modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
    }
}

@Composable
fun ProgressBar(value: Float, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    val animated by animateFloatAsState(value.coerceIn(0f, 1f), label = "progress")
    LinearProgressIndicator(progress = { animated }, modifier = modifier.height(8.dp).clip(RoundedCornerShape(50)), color = accent, trackColor = GachaTokens.outline)
}

@Composable
fun EmptyState(title: String, message: String, action: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier) {
    GachaCard(modifier = modifier) {
        Text("✦", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, color = GachaTokens.muted)
        action?.invoke()
    }
}

@Composable
fun LoadingState(label: String = "Atualizando conteúdo…", modifier: Modifier = Modifier) {
    GachaCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(label, color = GachaTokens.muted)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    GachaCard(modifier) {
        StatusBadge("Atenção", positive = false)
        Text(message, color = GachaTokens.muted)
        onRetry?.let { retry -> OutlinedButton(onClick = retry) { Text("Tentar novamente") } }
    }
}

@Composable
fun AnimatedFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else GachaTokens.panelRaised, label = "chip")
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
}

@Composable
fun GachaNavigationBar(selected: String, onSelect: (String) -> Unit) {
    val destinations = listOf(
        Triple("Resumo", "⌂", "Início"),
        Triple("Personagens", "✦", "Roster"),
        Triple("Planejamento", "▤", "Planner"),
        Triple("Times", "◈", "Squad"),
        Triple("Conta", "⋯", "Mais")
    )
    NavigationBar(containerColor = GachaTokens.panel.copy(alpha = .98f), tonalElevation = 0.dp) {
        destinations.forEach { (route, icon, label) ->
            NavigationBarItem(
                modifier = Modifier.semantics { contentDescription = route },
                selected = selected == route,
                onClick = { onSelect(route) },
                icon = { Text(icon, style = MaterialTheme.typography.titleLarge) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = .14f)
                )
            )
        }
    }
}

@Preview(name = "Gacha Hub dark system", showBackground = true, backgroundColor = 0xFF0A0C11)
@Composable
private fun GachaDesignPreview() {
    GachaTheme(Game.ZZZ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroPanel(Game.ZZZ, "Zenless Zone Zero", "Seu painel de progresso") { StatusBadge("Perfil conectado") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricPill("Possuídos", "31", modifier=Modifier.weight(1f))
                MetricPill("Projetos", "3", modifier=Modifier.weight(1f))
            }
            EmptyState("Nenhum favorito", "Marque personagens no catálogo para vê-los aqui.")
        }
    }
}
