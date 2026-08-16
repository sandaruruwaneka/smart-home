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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.smarthome.control.ui.alerts.AlertsScreen
import com.smarthome.control.ui.auth.LoginScreen
import com.smarthome.control.ui.floor.FloorDashboardScreen
import com.smarthome.control.ui.floor.edit.EditFloorScreen
import com.smarthome.control.ui.gallery.DesignSystemGallery
import com.smarthome.control.ui.home.FloorListScreen
import com.smarthome.control.ui.navigation.AppDestination
import com.smarthome.control.ui.theme.SmartHomeTheme

/**
 * SCS 3311 — Smart Home Monitoring & Control System.
 *
 * Screens arrive in the build order given in master prompt section 14. Login, the floor
 * list, the floor dashboard, all four device sheets, the floor editor and Alerts are built.
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
 * - `Alerts` is real. `Reports` still does nothing: a tab that navigates to an invented
 *   placeholder would be harder to notice as unfinished than one that does not move.
 *
 * The editor is the one destination that carries a *nullable* argument: a null floor id is
 * create mode. Two booleans would let both be true at once, which is a state the editor has
 * no meaning for.
 */
@Composable
private fun SmartHomeApp() {
    var signedIn by rememberSaveable { mutableStateOf(false) }
    var showingGallery by rememberSaveable { mutableStateOf(false) }
    var openFloorId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable(stateSaver = EditorRouteSaver) {
        mutableStateOf<EditorRoute?>(null)
    }
    var showingAlerts by rememberSaveable { mutableStateOf(false) }

    val goHome: (AppDestination) -> Unit = { destination ->
        when (destination) {
            AppDestination.Settings -> showingGallery = true
            AppDestination.Alerts -> showingAlerts = true
            else -> Unit
        }
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

        showingAlerts -> SmartHomeTheme {
            BackHandler { showingAlerts = false }
            AlertsScreen(
                // An alert is read to find out which device it is about, so tapping one goes
                // to that device's floor rather than to the log entry it came from.
                onOpenDevice = { _, floorId ->
                    showingAlerts = false
                    openFloorId = floorId
                },
                onNavigate = { destination ->
                    showingAlerts = destination == AppDestination.Alerts
                    if (destination != AppDestination.Alerts) goHome(destination)
                },
            )
        }

        editing != null -> SmartHomeTheme {
            // No BackHandler here: the editor installs its own, because a back press with
            // unsaved changes has to ask before it throws them away.
            EditFloorScreen(
                floorId = (editing as? EditorRoute.Edit)?.floorId,
                onClose = { editing = null },
            )
        }

        openFloorId != null -> SmartHomeTheme {
            BackHandler { openFloorId = null }
            FloorDashboardScreen(
                floorId = requireNotNull(openFloorId),
                onBack = { openFloorId = null },
                onOpenDevice = { /* Multi-switch, appliance and light sheets — screens 06, 07. */ },
                onEditFloor = { editing = EditorRoute.Edit(it) },
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
                onAddFloor = { editing = EditorRoute.Create },
                onOpenDevice = { _, floorId -> openFloorId = floorId },
                onNavigate = goHome,
            )
        }
    }
}

/** Where the floor editor was opened from, which is also what it does when it gets there. */
private sealed interface EditorRoute {
    data object Create : EditorRoute
    data class Edit(val floorId: String) : EditorRoute
}

/**
 * Survives a rotation, which matters here more than for the other destinations: the editor
 * holds unsaved work, and coming back from a rotation to the floor list would throw it away
 * without the discard dialog ever appearing.
 */
private val EditorRouteSaver = listSaver<EditorRoute?, String>(
    save = { route ->
        when (route) {
            null -> emptyList()
            EditorRoute.Create -> listOf("")
            is EditorRoute.Edit -> listOf(route.floorId)
        }
    },
    restore = { values ->
        values.firstOrNull()?.let { id -> if (id.isEmpty()) EditorRoute.Create else EditorRoute.Edit(id) }
    },
)
