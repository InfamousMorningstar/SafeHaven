package ca.safehaven.intake.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val KioskColors = lightColorScheme(
    primary = Color(0xFF2E6B5E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4EFDF),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFFB4652B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC6),
    onSecondaryContainer = Color(0xFF311300),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1C1B1A),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFFE6E1D9),
    onSurfaceVariant = Color(0xFF4A4640),
    outline = Color(0xFF7B766E),
)

// Large type for a kiosk read at arm's length (plan 3.2: base text 22-24 sp).
private val KioskTypography = Typography(
    displaySmall = TextStyle(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodySmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun SafeHavenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KioskColors, typography = KioskTypography, content = content)
}
