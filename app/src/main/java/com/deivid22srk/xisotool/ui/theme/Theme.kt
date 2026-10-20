package com.deivid22srk.xisotool.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val XboxGreen = Color(0xFF107C10)
private val XboxGreenDark = Color(0xFF7BDB74)

private val LightColors = lightColorScheme(
    primary = XboxGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F5B0),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF00658F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC7E7FF),
    onSecondaryContainer = Color(0xFF001E2E),
    tertiary = Color(0xFF6B5E00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8E36A),
    onTertiaryContainer = Color(0xFF211B00),
    background = Color(0xFFF7FBF2),
    onBackground = Color(0xFF181D16),
    surface = Color(0xFFF7FBF2),
    onSurface = Color(0xFF181D16),
    surfaceVariant = Color(0xFFDDE5D8),
    onSurfaceVariant = Color(0xFF414941),
    outline = Color(0xFF717970),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = XboxGreenDark,
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF00530F),
    onPrimaryContainer = Color(0xFFB8F5B0),
    secondary = Color(0xFF82CFFF),
    onSecondary = Color(0xFF00344A),
    secondaryContainer = Color(0xFF004C6B),
    onSecondaryContainer = Color(0xFFC7E7FF),
    tertiary = Color(0xFFDBC64F),
    onTertiary = Color(0xFF383000),
    tertiaryContainer = Color(0xFF514700),
    onTertiaryContainer = Color(0xFFF8E36A),
    background = Color(0xFF10140E),
    onBackground = Color(0xFFE0E4DA),
    surface = Color(0xFF10140E),
    onSurface = Color(0xFFE0E4DA),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BD),
    outline = Color(0xFF8B9388),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

val XisoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private val BaseTypography = Typography()

val XisoTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.5).sp,
    ),
    displayMedium = BaseTypography.displayMedium.copy(
        fontWeight = FontWeight.Black,
        letterSpacing = (-1).sp,
    ),
    displaySmall = BaseTypography.displaySmall.copy(
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun XisoToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = XisoShapes,
        typography = XisoTypography,
        content = content,
    )
}
