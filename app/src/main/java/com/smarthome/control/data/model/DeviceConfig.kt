package com.smarthome.control.data.model

import com.smarthome.control.data.ConfigFields
import com.smarthome.control.data.asIntOrNull
import com.smarthome.control.ui.model.DeviceType

/**
 * The type-specific `config` map on a device document -- SCHEMA.md section 5.
 *
 * On the wire this is one map field whose keys differ by `type`, which keeps the device
 * document shape uniform. In Kotlin it is a sealed hierarchy instead, so that the
 * compiler enforces what the wire format only documents: an appliance always has a
 * maximum on-duration, a camera always has both URIs, and an outlet has nothing at all.
 * Reading `config.max_on_duration` off a light stops being expressible.
 *
 * Every variant is parsed leniently and written strictly. Parsing has to survive whatever
 * the simulator or an older build left behind; writing is the app's own doing and can be
 * held to the contract.
 */
sealed interface DeviceConfig {

    /** The device type this config belongs to. Guards against writing a mismatched pair. */
    val deviceType: DeviceType

    /** The map written to the `config` field. Empty is a valid value, not a missing one. */
    fun toMap(): Map<String, Any?>

    /** `OUTLET` configures nothing, but still carries an empty map (section 5). */
    data object Outlet : DeviceConfig {
        override val deviceType: DeviceType get() = DeviceType.OUTLET
        override fun toMap(): Map<String, Any?> = emptyMap()
    }

    /**
     * `MULTI_SWITCH`: how many channels the physical gang plate has.
     *
     * [channelCount] is set at placement and immutable thereafter. Changing it would
     * orphan the usage history of any channel that disappeared, and leave the parent's
     * derived status referring to channels that no longer exist. To change the gang
     * count, the unit is deleted and re-placed -- which is why no repository method
     * updates this field.
     */
    data class MultiSwitch(val channelCount: Int) : DeviceConfig {
        init {
            require(channelCount >= 1) { "a switch bank needs at least one channel" }
        }

        override val deviceType: DeviceType get() = DeviceType.MULTI_SWITCH
        override fun toMap(): Map<String, Any?> = mapOf(ConfigFields.CHANNEL_COUNT to channelCount)
    }

    /**
     * `LIGHT`: an optional daily on/off window, evaluated by the worker (section 10.2).
     *
     * A window whose end is earlier than its start crosses midnight and is valid. Times
     * are local to the home's timezone, which is stored on the user document.
     *
     * [scheduleEnabled] can only be true when both times are present -- enforced here
     * rather than trusted, because a half-written schedule read from Firestore would
     * otherwise become a schedule the worker cannot evaluate.
     */
    data class Light(
        val scheduleEnabled: Boolean,
        val scheduleOn: TimeOfDay?,
        val scheduleOff: TimeOfDay?,
    ) : DeviceConfig {
        init {
            require(!scheduleEnabled || (scheduleOn != null && scheduleOff != null)) {
                "an enabled schedule needs both an on and an off time"
            }
        }

        override val deviceType: DeviceType get() = DeviceType.LIGHT

        override fun toMap(): Map<String, Any?> = mapOf(
            ConfigFields.SCHEDULE_ENABLED to scheduleEnabled,
            ConfigFields.SCHEDULE_ON to scheduleOn?.wireValue,
            ConfigFields.SCHEDULE_OFF to scheduleOff?.wireValue,
        )

        /** Whether the configured window covers [now], accounting for midnight crossing. */
        fun isWithinWindow(now: TimeOfDay): Boolean {
            if (!scheduleEnabled) return false
            val on = scheduleOn ?: return false
            val off = scheduleOff ?: return false
            return on.windowContains(off, now)
        }

        companion object {
            val OFF = Light(scheduleEnabled = false, scheduleOn = null, scheduleOff = null)
        }
    }

    /**
     * `APPLIANCE`: the maximum time the device may stay on, in seconds.
     *
     * This is the single field the safety cutoff enforces, and the contract requires it
     * to be present and non-null for this type -- an appliance without a limit must not
     * be creatable, so there is no nullable variant and no default. Whoever builds the
     * hazard-config sheet has to supply a value.
     *
     * For the demo, set a sample appliance to 60-120 seconds so the cutoff fires on
     * camera within the video's runtime (section 10.1).
     */
    data class Appliance(val maxOnDurationSeconds: Int) : DeviceConfig {
        init {
            require(maxOnDurationSeconds > 0) { "a maximum on-duration must be positive" }
        }

        override val deviceType: DeviceType get() = DeviceType.APPLIANCE

        override fun toMap(): Map<String, Any?> =
            mapOf(ConfigFields.MAX_ON_DURATION to maxOnDurationSeconds)
    }

    /**
     * `CAMERA`: stream and snapshot URIs. Mock URIs are acceptable per the brief.
     *
     * For this type `status` means stream reachability, not power -- so `OFF` on a camera
     * reads as "not reachable", and the UI must not offer to switch it.
     */
    data class Camera(val streamUri: String, val snapshotUri: String) : DeviceConfig {
        override val deviceType: DeviceType get() = DeviceType.CAMERA

        override fun toMap(): Map<String, Any?> = mapOf(
            ConfigFields.STREAM_URI to streamUri,
            ConfigFields.SNAPSHOT_URI to snapshotUri,
        )
    }

    companion object {
        /**
         * Reads the `config` map for a device of [type].
         *
         * Returns null only when the type's mandatory content is missing or unusable --
         * today that means an appliance with no positive `max_on_duration`, which the
         * safety worker could never act on and which the app must not present as safe.
         * Everything else degrades: a light with a broken schedule becomes an unscheduled
         * light, a camera with no URIs becomes a camera with empty ones.
         */
        fun fromMap(type: DeviceType, raw: Map<String, Any?>?): DeviceConfig? {
            val config = raw.orEmpty()
            return when (type) {
                DeviceType.OUTLET -> Outlet

                DeviceType.MULTI_SWITCH -> MultiSwitch(
                    channelCount = config[ConfigFields.CHANNEL_COUNT].asIntOrNull()
                        ?.coerceAtLeast(1)
                        ?: 1,
                )

                DeviceType.LIGHT -> {
                    val on = TimeOfDay.parseOrNull(config[ConfigFields.SCHEDULE_ON] as? String)
                    val off = TimeOfDay.parseOrNull(config[ConfigFields.SCHEDULE_OFF] as? String)
                    val enabled = config[ConfigFields.SCHEDULE_ENABLED] as? Boolean ?: false
                    Light(
                        // Demoted rather than trusted: a schedule missing one edge is not
                        // a schedule, and claiming it is enabled would show the user a
                        // window the worker will never act on.
                        scheduleEnabled = enabled && on != null && off != null,
                        scheduleOn = on,
                        scheduleOff = off,
                    )
                }

                DeviceType.APPLIANCE -> config[ConfigFields.MAX_ON_DURATION].asIntOrNull()
                    ?.takeIf { it > 0 }
                    ?.let(::Appliance)

                DeviceType.CAMERA -> Camera(
                    streamUri = config[ConfigFields.STREAM_URI] as? String ?: "",
                    snapshotUri = config[ConfigFields.SNAPSHOT_URI] as? String ?: "",
                )
            }
        }
    }
}
