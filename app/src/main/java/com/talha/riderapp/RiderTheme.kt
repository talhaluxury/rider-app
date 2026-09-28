package com.talha.riderapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared "Restaurant ecosystem" design tokens. The Restaurant POS app uses the exact same
 * background, glass surfaces, radii and spacing - only the accent differs
 * (Restaurant = warm orange, Rider = cool blue/cyan).
 */
object RiderColors {
    val Bg0 = Color(0xFF070B12)
    val Bg1 = Color(0xFF0B111A)
    val Bg2 = Color(0xFF101925)

    val Blue = Color(0xFF18A8FF)
    val Electric = Color(0xFF39C6FF)
    val Deep = Color(0xFF0878D1)
    val Cyan = Color(0xFF4DE7FF)

    val Glass1 = Color(0x0EFFFFFF) // 5.5%
    val Glass2 = Color(0x16FFFFFF) // 8.5%
    val Glass3 = Color(0x1FFFFFFF) // 12%
    val GlassBorder = Color(0x1AFFFFFF) // 10%
    val GlassBorderActive = Color(0x2EFFFFFF) // 18%

    val TextPrimary = Color(0xFFF5F7FA)
    val TextSecondary = Color(0xB3F5F7FA)
    val TextTertiary = Color(0x80F5F7FA)

    val Green = Color(0xFF4ADE80)
    val Red = Color(0xFFFF5C5C)
    val Amber = Color(0xFFFFD166)
}

object Radius {
    val Small = 12.dp
    val Medium = 16.dp
    val Large = 22.dp
    val ExtraLarge = 28.dp
    val Pill = 999.dp
}

object Spacing {
    val XS = 4.dp
    val SM = 8.dp
    val MD = 12.dp
    val LG = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
}

@Composable
fun RiderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RiderColors.Blue,
            secondary = RiderColors.Cyan,
            background = RiderColors.Bg0,
            surface = RiderColors.Bg1,
            onPrimary = Color.White,
            onBackground = RiderColors.TextPrimary,
            onSurface = RiderColors.TextPrimary
        ),
        content = content
    )
}
