package com.smarthome.control.ui.settings

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The theme choice, kept on the device rather than in Firestore.
 *
 * It is a property of this phone, not of the home: a user signed in on a tablet in a bright
 * kitchen and a phone in a dark hallway wants different answers, and syncing the preference
 * would make one of those devices wrong. Everything else on the settings screen is home
 * state and lives in `users/{uid}`.
 *
 * `SharedPreferences` rather than DataStore — one enum, read once at startup, written on a
 * tap. DataStore would add a dependency and a coroutine to store a string.
 */
object AppearancePreference {
    private const val FileName = "smart_home_prefs"
    private const val Key = "appearance"

    fun read(context: Context): Appearance {
        val stored = context
            .getSharedPreferences(FileName, Context.MODE_PRIVATE)
            .getString(Key, null)
        return Appearance.entries.firstOrNull { it.name == stored } ?: Appearance.Dark
    }

    fun write(context: Context, appearance: Appearance) {
        context
            .getSharedPreferences(FileName, Context.MODE_PRIVATE)
            .edit()
            .putString(Key, appearance.name)
            .apply()
    }
}

/**
 * The preference as state the whole app can read, so changing it repaints immediately
 * rather than on next launch.
 */
@Composable
fun rememberAppearance(): MutableState<Appearance> {
    val context = LocalContext.current
    return remember { mutableStateOf(AppearancePreference.read(context)) }
}

/** Resolves the choice against the system, which only `Follow system` actually consults. */
@Composable
fun Appearance.isDark(): Boolean = when (this) {
    Appearance.Dark -> true
    Appearance.Light -> false
    Appearance.System -> isSystemInDarkTheme()
}
