package com.smarthome.control.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

/**
 * The player, its phase, and the lifecycle rules that keep it from becoming a battery leak.
 *
 * Kept apart from the screen because it is the one piece of this feature with a resource to
 * release: a decoder left running behind a backgrounded app is the fastest way to make the
 * whole thing feel heavy halfway through a demo.
 */
class CameraPlayback(
    val player: ExoPlayer?,
    val phase: PlaybackPhase,
    val elapsedMillis: Long,
)

/**
 * Builds an ExoPlayer for [streamUri] and reports what it is actually doing.
 *
 * The phase is read from the player rather than assumed from the URI, which is what lets the
 * badge stay honest — see [cameraPresentation].
 *
 * No audio: cameras here are visual only, so the audio focus is never requested and no
 * volume control is rendered. The stream loops indefinitely, because a mock feed that stops
 * after thirty seconds looks like a camera that died.
 */
@Composable
fun rememberCameraPlayback(streamUri: String?): CameraPlayback {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var phase by remember(streamUri) { mutableStateOf(PlaybackPhase.Connecting) }
    var elapsed by remember(streamUri) { mutableLongStateOf(0L) }

    // The preview pane has no decoder and an artboard should render the state it was asked
    // for, not whatever a real player would manage against a URL that does not resolve.
    if (inPreview || streamUri.isNullOrBlank()) {
        return CameraPlayback(player = null, phase = PlaybackPhase.Failed, elapsedMillis = 0L)
    }

    val player = remember(streamUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUri))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                phase = when (state) {
                    Player.STATE_READY -> PlaybackPhase.Playing
                    // BUFFERING after playback has begun is a stall, not a fresh connection.
                    // The distinction is what decides whether the last frame is held.
                    Player.STATE_BUFFERING ->
                        if (phase == PlaybackPhase.Playing) PlaybackPhase.Stalled else PlaybackPhase.Connecting
                    Player.STATE_IDLE -> PlaybackPhase.Connecting
                    else -> phase
                }
            }

            // A failed stream is not an error state the user should have to act on: section
            // 5 wants the fallback to a snapshot to be automatic and silent, and `Failed` is
            // how that is signalled to the presentation.
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                phase = PlaybackPhase.Failed
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Pauses with the screen and resumes on return, without tearing the player down — a
    // rebuild would restart the stream from the beginning on every rotation.
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player) {
        while (true) {
            elapsed = player.currentPosition.coerceAtLeast(0L)
            delay(ElapsedTickMillis)
        }
    }

    return CameraPlayback(player = player, phase = phase, elapsedMillis = elapsed)
}

/** `14:32:07` — the elapsed playback clock, in tabular figures at the call site. */
fun formatElapsed(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private const val ElapsedTickMillis = 1_000L
