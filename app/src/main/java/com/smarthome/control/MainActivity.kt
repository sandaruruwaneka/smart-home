package com.smarthome.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.smarthome.control.ui.auth.LoginScreen
import com.smarthome.control.ui.floor.FloorDashboardScreen
import com.smarthome.control.ui.gallery.DesignSystemGallery
import com.smarthome.control.ui.home.FloorListScreen
import com.smarthome.control.ui.navigation.AppDestination
import com.smarthome.control.ui.theme.SmartHomeTheme

/**
 * SCS 3311 — Smart Home Monitoring & Control System.
 *
 * Screens arrive in the build order given in master prompt section 14. Login and the floor
 * list are built; the floor dashboard is next.
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
 * Chooses between the login screen, the floor list, one floor's dashboard, and the one
 * interim destination.
 *
 * Still remembered values rather than a navigation graph. Three of the four bottom-bar
 * destinations do not exist yet — Alerts is screen 09, Reports 11, Settings 12 — and a
 * route scheme chosen before the screens that have to live in it is a scheme that gets
 * rewritten when they arrive. The floor id below is the first navigation *argument* in the
 * app, and a second one will be enough to make a real graph worth its own commit.
 *
 * Until then:
 *
 * - `Home` is the floor list, and tapping a floor opens its dashboard. Both are real.
 * - `Settings` opens the design-system gallery. It is a placeholder, and an honest one: it
 *   keeps the section 13 deliverable reachable from inside the running app, and system
 *   back returns here.
 * - `Alerts` and `Reports` do nothing yet. A tab that navigates to an invented placeholder
 *   would be harder to notice as unfinished than one that does not move.
 *
 * The device-level actions — opening a control sheet, adding or editing a floor — are wired
 * to nothing for the same reason. Every one of them is a screen with its own prompt still
 * to come.
 */
@Composable
private fun SmartHomeApp() {
    var signedIn by rememberSaveable { mutableStateOf(false) }
    var showingGallery by rememberSaveable { mutableStateOf(false) }
    var openFloorId by rememberSaveable { mutableStateOf<String?>(null) }

    val goHome: (AppDestination) -> Unit = { destination ->
        if (destination == AppDestination.Settings) showingGallery = true
    }

    when {
        !signedIn -> SmartHomeTheme {
            LoginScreen(onSignedIn = { signedIn = true })
        }

        showingGallery -> {
            // The gallery applies its own theme, since it has a light/dark toggle of its own.
            BackHandler { showingGallery = false }
            DesignSystemGallery()
        }

        openFloorId != null -> SmartHomeTheme {
            BackHandler { openFloorId = null }
            FloorDashboardScreen(
                floorId = requireNotNull(openFloorId),
                onBack = { openFloorId = null },
                onOpenDevice = { /* Device control sheet — screens 04, 06, 07. */ },
                onEditFloor = { /* Edit floor — screen 05. */ },
                // Switching floors stays on this screen rather than routing back through
                // Home, which is the whole point of the switcher behind the title.
                onSwitchFloor = { openFloorId = it },
                onNavigate = { destination ->
                    if (destination == AppDestination.Home) openFloorId = null else goHome(destination)
                },
            )
        }

        else -> SmartHomeTheme {
            FloorListScreen(
                onOpenFloor = { openFloorId = it },
                onAddFloor = { /* Edit floor, create mode — screen 05. */ },
                onOpenDevice = { _, floorId -> openFloorId = floorId },
                onNavigate = goHome,
            )
        }
    }
}
