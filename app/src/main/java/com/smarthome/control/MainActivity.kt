package com.smarthome.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.smarthome.control.ui.auth.LoginScreen
import com.smarthome.control.ui.gallery.DesignSystemGallery
import com.smarthome.control.ui.theme.SmartHomeTheme

/**
 * SCS 3311 — Smart Home Monitoring & Control System.
 *
 * Screens arrive in the build order given in master prompt section 14. Login is built;
 * the floor list is next.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SmartHomeApp()
        }
    }
}

/**
 * Chooses between the login screen and what follows it.
 *
 * This is a boolean rather than a navigation graph on purpose. There are two destinations
 * and one edge between them; adding Navigation Compose now would mean choosing a route
 * scheme before the screens that have to live in it exist. It becomes a real graph when
 * the floor list lands and there is something to navigate *between*.
 *
 * Until then the post-login destination is the design-system gallery — a placeholder that
 * keeps the section 13 deliverable reachable, and makes it obvious that sign-in worked.
 */
@Composable
private fun SmartHomeApp() {
    var signedIn by rememberSaveable { mutableStateOf(false) }

    if (signedIn) {
        // The gallery applies its own theme, since it has a light/dark toggle of its own.
        DesignSystemGallery()
    } else {
        SmartHomeTheme {
            LoginScreen(onSignedIn = { signedIn = true })
        }
    }
}
