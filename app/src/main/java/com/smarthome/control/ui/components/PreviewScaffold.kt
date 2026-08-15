package com.smarthome.control.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smarthome.control.ui.theme.SmartHomeTheme
import com.smarthome.control.ui.theme.Spacing

/**
 * Wrapper for the `@Preview` functions attached to each component.
 *
 * Every component preview goes through this so the Android Studio preview pane shows the
 * component on the real screen background at the real screen padding, rather than on the
 * white default where a dark-first design system looks wrong for reasons that have
 * nothing to do with the component.
 */
@Composable
internal fun GalleryPreview(
    dark: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    SmartHomeTheme(darkTheme = dark) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SmartHomeTheme.colors.background)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content,
        )
    }
}
