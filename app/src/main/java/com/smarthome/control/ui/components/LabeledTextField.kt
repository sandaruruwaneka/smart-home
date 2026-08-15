package com.smarthome.control.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smarthome.control.ui.theme.AppBorders
import com.smarthome.control.ui.theme.AppShapes
import com.smarthome.control.ui.theme.AppType
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing
import com.smarthome.control.ui.theme.rememberReducedMotion

/**
 * A single-line text input with its label sitting above the box.
 *
 * Not one of the ten components in master prompt section 8 — it is the form primitive the
 * screens that came after it needed, and it is here rather than inside one screen because
 * the login form, the floor editor and the hazard config sheet all want the same field.
 *
 * ### Why not `OutlinedTextField`
 *
 * Material's field animates its label down into the box when empty and up onto the border
 * when filled. Screen prompt 01 section 4 rules that out: the label must survive the user
 * typing, and a placeholder that vanishes on first keystroke leaves somebody who was
 * interrupted mid-form with no way to tell what the box wanted. The label here is a plain
 * [Text] above the box and never moves.
 *
 * ### Border states
 *
 * Resting 1 dp `outline`, focused 1.5 dp `primary`, error 1.5 dp `stateError`. Error wins
 * over focus: a field the user is fixing should keep telling them it is the broken one.
 */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * Read-only keeps the text at full contrast and the field in the tab order, unlike
     * [enabled] `= false`. That is what section 5 asks for while a sign-in is in flight:
     * the user cannot edit, but they can still read back what they typed.
     */
    readOnly: Boolean = false,
    isError: Boolean = false,
    /**
     * Announced by screen readers as this field's error. The visible message is drawn once
     * beneath the whole field group, so passing it here is what keeps the two associated
     * rather than leaving the text floating loose in the layout (section 8).
     */
    errorMessage: String? = null,
    helperText: String? = null,
    /** Helper text that appears on focus rather than on failure — the password rule. */
    helperVisibleWhenUnfocused: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = SmartHomeTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val instant = rememberReducedMotion()
    val duration = if (instant) 0 else 200

    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> colors.stateError
            focused -> colors.primary
            else -> colors.outline
        },
        animationSpec = tween(duration),
        label = "fieldBorderColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isError || focused) AppBorders.emphasis else AppBorders.hairline,
        animationSpec = tween(duration),
        label = "fieldBorderWidth",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = AppType.label,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (isError && errorMessage != null) error(errorMessage) },
            enabled = enabled,
            readOnly = readOnly,
            textStyle = AppType.body.copy(color = colors.textPrimary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = true,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant, AppShapes.buttonSecondary)
                        .border(borderWidth, borderColor, AppShapes.buttonSecondary)
                        .defaultMinSize(minHeight = 56.dp)
                        .padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Box(modifier = Modifier.weight(1f)) { innerTextField() }
                    trailing?.invoke()
                }
            },
        )

        if (helperText != null && (focused || helperVisibleWhenUnfocused)) {
            Text(
                text = helperText,
                style = AppType.label,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "LabeledTextField · dark", showBackground = true, backgroundColor = 0xFF0E1316, widthDp = 412)
@Composable
private fun LabeledTextFieldPreviewDark() = GalleryPreview(dark = true) {
    LabeledTextField(label = "Email", value = "", onValueChange = {})
    LabeledTextField(label = "Email", value = "user@example.com", onValueChange = {})
    LabeledTextField(
        label = "Email",
        value = "user@example",
        onValueChange = {},
        isError = true,
        errorMessage = "Enter your email address.",
    )
    LabeledTextField(
        label = "Password",
        value = "correct horse",
        onValueChange = {},
        helperText = "At least 8 characters.",
        helperVisibleWhenUnfocused = true,
    )
}

@Preview(name = "LabeledTextField · light", showBackground = true, backgroundColor = 0xFFF5F7F8, widthDp = 412)
@Composable
private fun LabeledTextFieldPreviewLight() = GalleryPreview(dark = false) {
    LabeledTextField(label = "Email", value = "user@example.com", onValueChange = {})
    LabeledTextField(
        label = "Email",
        value = "nope",
        onValueChange = {},
        isError = true,
        errorMessage = "Enter your email address.",
    )
}
