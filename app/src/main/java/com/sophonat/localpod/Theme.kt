package com.sophonat.localpod

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFFC93A27),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFBE1DC),
    onPrimaryContainer = Color(0xFF5A1409),
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF17171A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17171A),
    surfaceVariant = Color(0xFFECECEF),
    onSurfaceVariant = Color(0xFF5B5B63),
    outline = Color(0xFFB9B9C0),
    outlineVariant = Color(0xFFE0E0E5)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF7A66),
    onPrimary = Color(0xFF1A0905),
    primaryContainer = Color(0xFF4A1E17),
    onPrimaryContainer = Color(0xFFFFD9D2),
    background = Color(0xFF0F0F11),
    onBackground = Color(0xFFF2F2F4),
    surface = Color(0xFF17171A),
    onSurface = Color(0xFFF2F2F4),
    surfaceVariant = Color(0xFF232327),
    onSurfaceVariant = Color(0xFFA5A5AE),
    outline = Color(0xFF55555C),
    outlineVariant = Color(0xFF2A2A2F)
)

// โหมดธีม: system / light / dark จำค่าไว้ใน SharedPreferences
object ThemePrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(context: Context): String = prefs(context).getString("theme", "system") ?: "system"

    fun save(context: Context, mode: String) = prefs(context).edit().putString("theme", mode).apply()

    fun next(mode: String): String = when (mode) {
        "system" -> "light"
        "light" -> "dark"
        else -> "system"
    }
}

@Composable
fun isDark(mode: String): Boolean = when (mode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun LocalPodTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
