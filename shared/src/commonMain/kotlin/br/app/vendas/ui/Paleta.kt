package br.app.vendas.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Cores tiradas do logo: azul elétrico, ciano da seta, prata do escudo e azul-marinho do fundo. */
internal object Paleta {
    val escuro: ColorScheme = darkColorScheme(
        primary = Color(0xFF3D9BFF),
        onPrimary = Color(0xFF001A3D),
        primaryContainer = Color(0xFF0A3D8F),
        onPrimaryContainer = Color(0xFFD6E8FF),
        secondary = Color(0xFF19D3FF),
        onSecondary = Color(0xFF00323D),
        secondaryContainer = Color(0xFF0B4A5E),
        onSecondaryContainer = Color(0xFFC9F3FF),
        tertiary = Color(0xFFB8C4D6),
        onTertiary = Color(0xFF1B2433),
        background = Color(0xFF060E1F),
        onBackground = Color(0xFFE6EDF7),
        surface = Color(0xFF0A1428),
        onSurface = Color(0xFFE6EDF7),
        surfaceVariant = Color(0xFF152340),
        onSurfaceVariant = Color(0xFFB8C4D6),
        outline = Color(0xFF5B7096),
    )

    val claro: ColorScheme = lightColorScheme(
        primary = Color(0xFF0A5CD6),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD6E8FF),
        onPrimaryContainer = Color(0xFF001A3D),
        secondary = Color(0xFF0089B5),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFC9F3FF),
        onSecondaryContainer = Color(0xFF00323D),
        tertiary = Color(0xFF5B6B80),
        background = Color(0xFFF3F7FD),
        onBackground = Color(0xFF0A1428),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0A1428),
        surfaceVariant = Color(0xFFE1E9F5),
        onSurfaceVariant = Color(0xFF3A4A63),
        outline = Color(0xFF7A8BA5),
    )
}
