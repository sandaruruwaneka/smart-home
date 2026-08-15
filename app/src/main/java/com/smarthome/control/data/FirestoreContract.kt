package com.smarthome.control.data

/**
 * Every collection path and field name in SCHEMA.md, in one place.
 *
 * The wire format is the seam between this app, the React simulator and the safety
 * worker. None of the three calls the others; they only agree on these strings. A typo
 * here does not fail to compile and does not throw at runtime -- it silently reads null
 * forever, or writes a field nobody reads. Centralising the strings means a rename is
 * one edit that the compiler checks, instead of a search across every repository.
 *
 * Field naming is `snake_case` on every field, no exceptions (SCHEMA.md section 1).
 */
internal object Collections {
    const val USERS = "users"
    const val FLOORS = "floors"
    const val DEVICES = "devices"
    const val USAGE_EVENTS = "usage_events"
    const val ALERTS = "alerts"

    /** Subcollection of a `MULTI_SWITCH` device document (section 6). */
    const val CHANNELS = "channels"
}

/** Fields carried by more than one collection. */
internal object CommonFields {
    const val OWNER_UID = "owner_uid"
    const val CREATED_AT = "created_at"
    const val STATUS = "status"
    const val TURNED_ON_AT = "turned_on_at"
    const val LAST_CHANGED_AT = "last_changed_at"
}

/** `floors/{floor_id}` -- SCHEMA.md section 3. */
internal object FloorFields {
    const val OWNER_UID = CommonFields.OWNER_UID
    const val NAME = "name"
    const val PLAN_IMAGE_URL = "plan_image_url"
    const val GRID_ROWS = "grid_rows"
    const val GRID_COLS = "grid_cols"
    const val CREATED_AT = CommonFields.CREATED_AT
}

/** `devices/{device_id}` -- SCHEMA.md section 4. */
internal object DeviceFields {
    const val OWNER_UID = CommonFields.OWNER_UID
    const val FLOOR_ID = "floor_id"
    const val TYPE = "type"
    const val NAME = "name"
    const val GRID_X = "grid_x"
    const val GRID_Y = "grid_y"
    const val STATUS = CommonFields.STATUS
    const val TURNED_ON_AT = CommonFields.TURNED_ON_AT
    const val LAST_CHANGED_AT = CommonFields.LAST_CHANGED_AT
    const val LAST_CHANGED_BY = "last_changed_by"
    const val CONFIG = "config"
}

/** Keys inside the `config` map -- SCHEMA.md section 5. */
internal object ConfigFields {
    const val CHANNEL_COUNT = "channel_count"
    const val SCHEDULE_ENABLED = "schedule_enabled"
    const val SCHEDULE_ON = "schedule_on"
    const val SCHEDULE_OFF = "schedule_off"
    const val MAX_ON_DURATION = "max_on_duration"
    const val STREAM_URI = "stream_uri"
    const val SNAPSHOT_URI = "snapshot_uri"
}

/** `devices/{device_id}/channels/{channel_id}` -- SCHEMA.md section 6. */
internal object ChannelFields {
    const val INDEX = "index"
    const val NAME = "name"
    const val STATUS = CommonFields.STATUS
    const val TURNED_ON_AT = CommonFields.TURNED_ON_AT
    const val LAST_CHANGED_AT = CommonFields.LAST_CHANGED_AT
}

/** `usage_events/{event_id}` -- SCHEMA.md section 7. */
internal object UsageEventFields {
    const val OWNER_UID = CommonFields.OWNER_UID
    const val DEVICE_ID = "device_id"
    const val CHANNEL_ID = "channel_id"
    const val STARTED_AT = "started_at"
    const val ENDED_AT = "ended_at"
    const val DURATION_SECONDS = "duration_seconds"
}

/** `alerts/{alert_id}` -- SCHEMA.md section 8. */
internal object AlertFields {
    const val OWNER_UID = CommonFields.OWNER_UID
    const val DEVICE_ID = "device_id"
    const val DEVICE_NAME = "device_name"
    const val FLOOR_ID = "floor_id"
    const val TYPE = "type"
    const val MESSAGE = "message"
    const val CREATED_AT = CommonFields.CREATED_AT
    const val ACKNOWLEDGED = "acknowledged"
}

/** `users/{uid}` -- SCHEMA.md section 9. */
internal object UserFields {
    const val DISPLAY_NAME = "display_name"
    const val EMAIL = "email"
    const val TIMEZONE = "timezone"
    const val CREATED_AT = CommonFields.CREATED_AT
}
