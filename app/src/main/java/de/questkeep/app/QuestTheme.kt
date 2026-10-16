package de.questkeep.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Theme palette layer for SideQuests.
 * The existing "dark" preference remains readable; selectedTheme is an additional preference.
 */
enum class QuestTheme(val storageKey: String, val label: String, val description: String, val isDark: Boolean) {
    PARCHMENT_NIGHT("parchment_night", "Pergament bei Nacht", "Warmes Pergament auf dunklem Leder", true),
    CLASSIC_PARCHMENT("classic_parchment", "Klassisches Pergament", "Helles Papier mit Tintenfarben", false),
    FOREST("forest", "Waldpfad", "Moosgrün und natürliche Erdtöne", true),
    MIDNIGHT_STEEL("midnight_steel", "Mitternachtsstahl", "Kühles Blau und Stahlgrau", true);

    companion object {
        fun fromKey(key: String?): QuestTheme =
            entries.firstOrNull { it.storageKey == key } ?: PARCHMENT_NIGHT
    }
}

data class QuestPalette(
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val onPrimary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val accent: Color,
    val muted: Color
)

fun QuestTheme.palette(): QuestPalette = when (this) {
    QuestTheme.PARCHMENT_NIGHT -> QuestPalette(
        primary = Color(0xFFC69A48), secondary = Color(0xFF8DAD79),
        background = Color(0xFF211A14), surface = Color(0xFF33271D),
        onPrimary = Color(0xFF211A14), onBackground = Color(0xFFE9D9B5),
        onSurface = Color(0xFFE9D9B5), accent = Color(0xFFC69A48),
        muted = Color(0xFFB8A17B)
    )
    QuestTheme.CLASSIC_PARCHMENT -> QuestPalette(
        primary = Color(0xFF75501F), secondary = Color(0xFF526B42),
        background = Color(0xFFF0E3C5), surface = Color(0xFFFFF8E9),
        onPrimary = Color.White, onBackground = Color(0xFF30251A),
        onSurface = Color(0xFF30251A), accent = Color(0xFF8B6327),
        muted = Color(0xFF76654D)
    )
    QuestTheme.FOREST -> QuestPalette(
        primary = Color(0xFFB5C987), secondary = Color(0xFFD3A86C),
        background = Color(0xFF17221B), surface = Color(0xFF24362A),
        onPrimary = Color(0xFF152016), onBackground = Color(0xFFE0E8D8),
        onSurface = Color(0xFFE0E8D8), accent = Color(0xFFB5C987),
        muted = Color(0xFFA8B7A4)
    )
    QuestTheme.MIDNIGHT_STEEL -> QuestPalette(
        primary = Color(0xFF9EBCE0), secondary = Color(0xFFC6A56B),
        background = Color(0xFF171D27), surface = Color(0xFF252F3D),
        onPrimary = Color(0xFF101722), onBackground = Color(0xFFE3EAF3),
        onSurface = Color(0xFFE3EAF3), accent = Color(0xFF9EBCE0),
        muted = Color(0xFFA8B4C4)
    )
}

@Composable
fun SideQuestsTheme(theme: QuestTheme, content: @Composable () -> Unit) {
    val p = theme.palette()
    val scheme = if (theme.isDark) {
        darkColorScheme(
            primary = p.primary, secondary = p.secondary, background = p.background,
            surface = p.surface, onPrimary = p.onPrimary, onBackground = p.onBackground,
            onSurface = p.onSurface
        )
    } else {
        lightColorScheme(
            primary = p.primary, secondary = p.secondary, background = p.background,
            surface = p.surface, onPrimary = p.onPrimary, onBackground = p.onBackground,
            onSurface = p.onSurface
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
