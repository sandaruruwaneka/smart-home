package com.smarthome.control.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.common.relativeTime
import com.smarthome.control.ui.components.LabeledTextField
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * The parts of a device control sheet that never vary.
 *
 * Screen prompt 04 section 2 declares its five-slot anatomy normative for all four device
 * sheets, and prompt 06 section 2 says it inherits that anatomy "without deviation". A
 * promise like that is only worth anything if the shared parts are literally shared: two
 * sheets each drawing their own drag handle and their own identity row agree right up until
 * somebody edits one of them, and then the app has two anatomies and no way to notice.
 *
 * So the fixed slots live here — handle, identity, section header, footer — and each sheet
 * supplies only what is actually different: its primary control, and whatever fills slot 3.
 */

/** The 32 x 4 dp grab bar. Every sheet in the app has exactly this one. */
@Composable
fun SheetDragHandle() {
    val colors = SmartHomeTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(HandleWidth)
                .height(HandleHeight)
                .background(colors.outline, RoundedCornerShape(percent = 50)),
        )
    }
}

/**
 * Slot 1 — name, location, overflow.
 *
 * @param subtitle `Ground Floor · R2 C5` for an outlet, `Ground Floor · R4 C3 · 3 gang` for
 *   a switch unit. The sheets differ only in how much they append.
 */
@Composable
fun SheetIdentity(
    name: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val colors = SmartHomeTheme.colors

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = AppType.display,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = subtitle, style = AppType.label, color = colors.textSecondary)
        }
        trailing()
    }
}

/**
 * The overflow button and its menu, with the open/close state where nobody has to think
 * about it.
 *
 * Items receive `dismiss` rather than closing the menu themselves, because every item in
 * every sheet has to close it and one that forgets leaves a menu hanging over the action it
 * just performed.
 */
@Composable
fun SheetOverflowButton(content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit) {
    val colors = SmartHomeTheme.colors
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "More actions",
                tint = colors.textSecondary,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = colors.surface,
        ) {
            content { open = false }
        }
    }
}

@Composable
fun SheetMenuItem(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = SmartHomeTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = AppType.body,
                color = if (destructive) colors.stateError else colors.textPrimary,
            )
        },
        onClick = onClick,
    )
}

/** `TODAY`, `CHANNELS` — the one-word headers that separate the slots. */
@Composable
fun SheetSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppType.label,
        color = SmartHomeTheme.colors.textSecondary,
        modifier = modifier,
    )
}

/**
 * Slot 5 — `Last changed 14 min ago`.
 *
 * Relative under a day and absolute beyond, which is [relativeTime]'s own rule rather than
 * a second one invented here.
 */
@Composable
fun SheetFooter(lastChangedMillis: Long?, nowMillis: Long, modifier: Modifier = Modifier) {
    if (lastChangedMillis == null) return
    Text(
        text = "Last changed ${relativeTime(lastChangedMillis, nowMillis)}",
        style = AppType.label,
        color = SmartHomeTheme.colors.textSecondary,
        modifier = modifier,
    )
}

/** Section 5 of prompt 04 and section 6 of prompt 06: one line, never an empty chart. */
@Composable
fun NoUsageLine(modifier: Modifier = Modifier) {
    Text(
        text = "Not used today.",
        style = AppType.body,
        color = SmartHomeTheme.colors.textSecondary,
        modifier = modifier,
    )
}

@Composable
fun SheetLoadFailure(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SmartHomeTheme.colors
    Column(modifier = modifier) {
        Text(text = message, style = AppType.body, color = colors.stateError)
        TextButton(onClick = onRetry) {
            Text("Try again", style = AppType.label, color = colors.primary)
        }
    }
}

@Composable
fun RenameDeviceDialog(
    currentName: String,
    label: String = "Device name",
    title: String = "Rename device",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = SmartHomeTheme.colors
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        title = { Text(title, style = AppType.sectionHeader) },
        text = { LabeledTextField(label = label, value = name, onValueChange = { name = it }) },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Save", style = AppType.label, color = colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = AppType.label, color = colors.textSecondary)
            }
        },
    )
}

/**
 * A destructive confirmation that names what it is about to destroy.
 *
 * `Delete this device?` is a question the user can answer correctly and still be wrong,
 * because the sheet behind the dialog is covered by it. The name is the whole point, and
 * prompt 06 adds the channel count for the same reason.
 */
@Composable
fun SheetConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = SmartHomeTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        title = { Text(title, style = AppType.sectionHeader) },
        text = { Text(body, style = AppType.body, color = colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, style = AppType.label, color = colors.stateError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = AppType.label, color = colors.textSecondary)
            }
        },
    )
}

/**
 * The artboard frame.
 *
 * `ModalBottomSheet` does not render in the preview pane, so the deliverable artboards draw
 * the same surface, shape, handle and scrim by hand. Shared so that the two sheets' six
 * artboards each are demonstrably the same sheet at the same detent.
 */
@Composable
fun SheetArtboardFrame(content: @Composable () -> Unit) {
    val colors = SmartHomeTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = ScrimAlpha)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, AppShapes.bottomSheet),
        ) {
            SheetDragHandle()
            content()
        }
    }
}

private val HandleWidth = 32.dp
private val HandleHeight = 4.dp

/** Section 3 of prompt 04: the scrim sits at 60 % of `background`. */
const val ScrimAlpha = 0.6f
