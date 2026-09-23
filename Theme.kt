package app.linkharvest.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * DealHunter90's brand colours: one fixed palette rather than per-device dynamic colour, so the
 * app looks the same — and recognisably itself — on every phone. Indigo carries the brand; the
 * warm coral is reserved for calls to action and discount badges, so a 90%-off tag always stands
 * out on the page.
 */
private val Indigo = Color(0xFF4F46E5)
private val IndigoLight = Color(0xFFC7C4FF)
private val Coral = Color(0xFFFF6D3F)
private val CoralLight = Color(0xFFFFB49B)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E0FF),
    onPrimaryContainer = Color(0xFF191064),
    secondary = Color(0xFF5B5D7E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E0FA),
    onSecondaryContainer = Color(0xFF181A2E),
    tertiary = Coral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCC),
    onTertiaryContainer = Color(0xFF3A1200),
    background = Color(0xFFFBFAFF),
    onBackground = Color(0xFF1B1B23),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B23),
    surfaceVariant = Color(0xFFE6E5F2),
    onSurfaceVariant = Color(0xFF47474F),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF78767F),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF261D80),
    primaryContainer = Color(0xFF3A3097),
    onPrimaryContainer = Color(0xFFE3E0FF),
    secondary = Color(0xFFC4C4E8),
    onSecondary = Color(0xFF2C2D45),
    secondaryContainer = Color(0xFF434465),
    onSecondaryContainer = Color(0xFFE1E0FA),
    tertiary = CoralLight,
    onTertiary = Color(0xFF5C1900),
    tertiaryContainer = Color(0xFF7D2C00),
    onTertiaryContainer = Color(0xFFFFDBCC),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF47474F),
    onSurfaceVariant = Color(0xFFC9C5D0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF928F99),
)

// Starts from Material 3's default type scale and sharpens just the weights that carry the most
// visual weight on screen, so headings and labels read as deliberately designed rather than default.
private val DefaultType = Typography()
private val AppTypography = Typography(
    headlineMedium = DefaultType.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = DefaultType.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = DefaultType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = DefaultType.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelSmall = DefaultType.labelSmall.copy(letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LinkHarvestTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
