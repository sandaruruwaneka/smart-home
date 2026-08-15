package com.smarthome.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.smarthome.control.ui.gallery.DesignSystemGallery

/**
 * SCS 3311 — Smart Home Monitoring & Control System.
 *
 * At this stage the app hosts the design-system deliverable only (master prompt section 13):
 * the colour swatch sheet, the type scale, the ten components in every state, and the
 * priority-hierarchy comparison. Screens arrive in the build order given in section 14.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DesignSystemGallery()
        }
    }
}
