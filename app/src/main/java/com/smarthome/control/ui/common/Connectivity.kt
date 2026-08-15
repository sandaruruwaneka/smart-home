package com.smarthome.control.ui.common

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * True while the phone has no validated internet connection.
 *
 * ### Why this and not Firestore's own cache flag
 *
 * Firestore tags every snapshot with `isFromCache`, which is the more literal answer to
 * "is this last known state". It is also the wrong thing to build the offline bar on: it
 * flips to true for a beat during normal listener churn, and a bar that blinks on a
 * healthy connection teaches the user to ignore it. The platform's own connectivity
 * signal changes only when the connection does.
 *
 * The pairing is what makes the bar honest either way — Firestore serves its local cache
 * while offline, so "no connection" and "showing last known state" are the same fact
 * stated from the two ends.
 *
 * `NET_CAPABILITY_VALIDATED` rather than merely `INTERNET`: a captive-portal hotspot that
 * has associated but not authenticated is, as far as the app's data is concerned, offline.
 */
@Composable
fun rememberIsOffline(): Boolean {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    var offline by remember { mutableStateOf(false) }

    DisposableEffect(context, inPreview) {
        // The preview pane has no real ConnectivityManager, and an artboard should render
        // the state it was asked for rather than the host machine's network.
        val manager = if (inPreview) {
            null
        } else {
            context.getSystemService(ConnectivityManager::class.java)
        }

        if (manager == null) {
            // No manager means no evidence of a problem. Claiming the data is stale
            // without knowing is worse than staying quiet.
            offline = false
            return@DisposableEffect onDispose { }
        }

        fun refresh() {
            offline = !manager.hasValidatedInternet()
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = refresh()
        }

        refresh()
        // Registration can throw on a device whose network stack is in a bad state; a
        // failure here must not take down the home screen, so the bar simply stays down.
        val registered = runCatching { manager.registerDefaultNetworkCallback(callback) }
            .isSuccess

        onDispose {
            if (registered) runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }

    return offline
}

private fun ConnectivityManager.hasValidatedInternet(): Boolean {
    val capabilities = runCatching { getNetworkCapabilities(activeNetwork) }.getOrNull()
        ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
