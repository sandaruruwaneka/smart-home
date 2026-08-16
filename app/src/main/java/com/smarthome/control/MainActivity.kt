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
import com.smarthome.control.ui.camera.CameraScreen
import com.smarthome.control.ui.camera.CameraWallScreen
import com.smarthome.control.ui.floor.FloorDashboardScreen
import com.smarthome.control.ui.floor.edit.EditFloorScreen
import com.smarthome.control.ui.gallery.DesignSystemGallery
import com.smarthome.control.ui.home.FloorListScreen
import com.smarthome.control.ui.navigation.AppDestination
import com.smarthome.control.ui.reports.ReportsScreen
import com.smarthome.control.ui.theme.SmartHomeTheme

/**
 * SCS 3311 — Smart Home Monitoring & Control System.
 *
 * Screens arrive in the build order given in master prompt section 14. All eleven are
 * built; only the design-system gallery behind `Settings` is still standing in for a
 * screen of its own.
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
 * - `Alerts` and `Reports` are both real. Every bottom-bar destination now goes somewhere,
 *   which is the point at which this `when` has earned its replacement by a real graph.
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
    var openCameraId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingCameraWall by rememberSaveable { mutableStateOf(false) }
    var showingReports by rememberSaveable { mutableStateOf(false) }

    val goHome: (AppDestination) -> Unit = { destination ->
        when (destination) {
            AppDestination.Settings -> showingGallery = true
            AppDestination.Alerts -> showingAlerts = true
            AppDestination.Reports -> showingReports = true
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

        openCameraId != null -> SmartHomeTheme {
            BackHandler { openCameraId = null }
            CameraScreen(
                deviceId = requireNotNull(openCameraId),
                onBack = { openCameraId = null },
                // The strip moves between cameras without going back to the floor plan,
                // which is the whole reason it is there.
                onOpenCamera = { openCameraId = it },
                onViewWall = { openCameraId = null; showingCameraWall = true },
                onMoveDevice = { openCameraId = null; openFloorId?.let { id -> editing = EditorRoute.Edit(id) } },
            )
        }

        showingCameraWall -> SmartHomeTheme {
            BackHandler { showingCameraWall = false }
            CameraWallScreen(
                onBack = { showingCameraWall = false },
                onOpenCamera = { showingCameraWall = false; openCameraId = it },
                onGoToFloors = { showingCameraWall = false; openFloorId = null },
            )
        }

        showingReports -> SmartHomeTheme {
            BackHandler { showingReports = false }
            ReportsScreen(
                // A device in the cutoffs list opens on its own floor, where its hazard
                // sheet is one tap away -- adjusting the limit from the evidence.
                onOpenDevice = { showingReports = false },
                onNavigate = { destination ->
                    showingReports = destination == AppDestination.Reports
                    if (destination != AppDestination.Reports) goHome(destination)
                },
            )
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
                // Every other type opens a sheet on the dashboard itself; a camera is the
                // one device you watch rather than control, so it gets a screen.
                onOpenDevice = { openCameraId = it },
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
