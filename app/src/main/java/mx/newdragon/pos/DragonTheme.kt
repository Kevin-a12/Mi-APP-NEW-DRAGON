package mx.newdragon.pos

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val DragonRed = Color(0xFFBF2029)
val DragonCream = Color(0xFFF7F7F5)
val DragonInk = Color(0xFF252725)
val DragonGold = Color(0xFF855D23)
val DragonMuted = Color(0xFF60645F)
val DragonLine = Color(0xFFD9DDD7)
val DragonRose = Color(0xFFFCE8E7)
val DragonGreen = Color(0xFF245C3B)
val DragonGreenLight = Color(0xFFEDF5EF)

private val DragonColors = lightColorScheme(
    primary = DragonRed, onPrimary = Color.White,
    primaryContainer = DragonRose, onPrimaryContainer = DragonRed,
    secondary = DragonInk, onSecondary = Color.White,
    secondaryContainer = DragonRose, onSecondaryContainer = DragonInk,
    tertiary = DragonGreen, onTertiary = Color.White,
    tertiaryContainer = DragonGreenLight, onTertiaryContainer = DragonGreen,
    background = DragonCream, onBackground = DragonInk,
    surface = Color.White, onSurface = DragonInk,
    surfaceVariant = Color(0xFFF0F2EE), onSurfaceVariant = DragonMuted,
    surfaceTint = Color.Transparent,
    surfaceBright = Color.White, surfaceDim = DragonCream,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFBF9),
    surfaceContainer = Color(0xFFF4F5F2), surfaceContainerHigh = Color(0xFFF0F2EE),
    surfaceContainerHighest = Color(0xFFE8EBE5),
    outline = Color(0xFF797F77), outlineVariant = DragonLine,
    error = Color(0xFFAE2428), onError = Color.White,
    errorContainer = Color(0xFFFCE9E8), onErrorContainer = Color(0xFF7C2024),
    inverseSurface = DragonInk, inverseOnSurface = Color.White,
    inversePrimary = Color(0xFFFFB4B3), scrim = Color.Black
)

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp
)

@Composable fun DragonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DragonColors,
        typography = Typography(
            headlineLarge = type(32, 38, FontWeight.Bold),
            headlineMedium = type(28, 34, FontWeight.Bold),
            headlineSmall = type(24, 30, FontWeight.Bold),
            titleLarge = type(22, 28, FontWeight.Bold),
            titleMedium = type(16, 22, FontWeight.SemiBold),
            titleSmall = type(14, 20, FontWeight.SemiBold),
            bodyLarge = type(16, 24), bodyMedium = type(14, 20), bodySmall = type(12, 18),
            labelLarge = type(14, 20, FontWeight.Medium),
            labelMedium = type(12, 16, FontWeight.Medium),
            labelSmall = type(11, 16, FontWeight.Medium)
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(12.dp),
            extraLarge = RoundedCornerShape(16.dp)
        ),
        content = content
    )
}
