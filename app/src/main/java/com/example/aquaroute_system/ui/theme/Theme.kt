    package com.example.aquaroute_system.ui.theme

    import androidx.compose.foundation.isSystemInDarkTheme
    import androidx.compose.material3.MaterialTheme
    import androidx.compose.material3.darkColorScheme
    import androidx.compose.material3.lightColorScheme
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.graphics.Color

    private val DarkColorScheme = darkColorScheme(
        primary = DeepOceanBlue,
        secondary = SkyCyan,
        tertiary = LightSeaGlass
    )

    private val LightColorScheme = lightColorScheme(
        primary = DeepOceanBlue,
        secondary = SkyCyan,
        tertiary = LightSeaGlass,
        background = Color(0xFFC4D7FF), // Splash background
        surface = Color.White,
        onPrimary = Color.White,
        onSecondary = Color.Black,
        onBackground = DarkGray,
        onSurface = DarkGray
    )

    @Composable
    fun AquaRouteTheme(
        darkTheme: Boolean = isSystemInDarkTheme(),
        content: @Composable () -> Unit
    ) {
        val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }