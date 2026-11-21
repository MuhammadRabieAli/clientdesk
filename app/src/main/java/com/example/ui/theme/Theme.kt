package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = StudioCobalt,
    onPrimary = PurePaper,
    primaryContainer = StudioCobaltContainer,
    onPrimaryContainer = DeepCobaltInk,
    secondary = StudioTerracotta,
    onSecondary = PurePaper,
    secondaryContainer = StudioTerracottaContainer,
    onSecondaryContainer = DeepTerracottaInk,
    tertiary = StudioForest,
    onTertiary = PurePaper,
    tertiaryContainer = StudioForestContainer,
    onTertiaryContainer = ObsidianInk,
    background = AlabasterCanvas,
    onBackground = ObsidianInk,
    surface = AlabasterCanvas,
    onSurface = ObsidianInk,
    surfaceVariant = WarmSand,
    onSurfaceVariant = SlateInk,
    surfaceContainerLowest = PurePaper,
    surfaceContainerLow = AlabasterCanvas,
    surfaceContainer = WarmSand,
    surfaceContainerHigh = SoftStone,
    outline = HairlineBorder,
    outlineVariant = SoftStone,
    error = StudioCrimson,
    onError = PurePaper,
    errorContainer = StudioCrimsonContainer,
    onErrorContainer = ObsidianInk,
)

private val DarkColorScheme = darkColorScheme(
    primary = LuminousCobalt,
    onPrimary = CharcoalCanvas,
    primaryContainer = DarkCobaltContainer,
    onPrimaryContainer = BoneInk,
    secondary = LuminousTerracotta,
    onSecondary = CharcoalCanvas,
    secondaryContainer = DarkTerracottaContainer,
    onSecondaryContainer = BoneInk,
    tertiary = LuminousForest,
    onTertiary = CharcoalCanvas,
    tertiaryContainer = DarkForestContainer,
    onTertiaryContainer = BoneInk,
    background = CharcoalCanvas,
    onBackground = BoneInk,
    surface = CharcoalCanvas,
    onSurface = BoneInk,
    surfaceVariant = CharcoalElevated,
    onSurfaceVariant = SilverInk,
    surfaceContainerLowest = CharcoalSurface,
    surfaceContainerLow = CharcoalSurface,
    surfaceContainer = CharcoalElevated,
    surfaceContainerHigh = CharcoalBorder,
    outline = CharcoalBorder,
    outlineVariant = CharcoalElevated,
    error = LuminousCrimson,
    onError = CharcoalCanvas,
)

val ClientDeskShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ClientDeskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ClientDeskTypography,
        shapes = ClientDeskShapes,
        content = content
    )
}
