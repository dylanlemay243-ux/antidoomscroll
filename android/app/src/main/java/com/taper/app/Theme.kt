package com.taper.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Organic design system tokens.
val Cream = Color(0xFFF5EAD8)
val Sand = Color(0xFFEBDDC5)
val Ink = Color(0xFF201E1D)
val Terracotta = Color(0xFFC67139)
val TerracottaDeep = Color(0xFF8C491A)
val TerracottaTint = Color(0xFFFFE1D0)
val Sage = Color(0xFF7A8A5E)
val SageDeep = Color(0xFF56633F)
val SageTint = Color(0xFFF0FAE1)
val Muted = Color(0xFF645C50)
val Rail = Color(0xFFDCD3C4)
val NightSage = Color(0xFF272E1B)

private val scheme = lightColorScheme(
    primary = Terracotta,
    onPrimary = Cream,
    primaryContainer = TerracottaTint,
    onPrimaryContainer = TerracottaDeep,
    secondary = Sage,
    onSecondary = Cream,
    secondaryContainer = SageTint,
    onSecondaryContainer = SageDeep,
    background = Cream,
    onBackground = Ink,
    surface = Sand,
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = Muted,
    outline = Rail,
)

// Caprasimo isn't bundled yet, so headings are heavy default sans for now.
// To match the design system exactly, drop Caprasimo.ttf into res/font
// and set fontFamily on these three styles.
private val typography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun TaperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
