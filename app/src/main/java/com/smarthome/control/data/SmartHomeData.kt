package com.smarthome.control.data

import com.google.firebase.firestore.FirebaseFirestore
import com.smarthome.control.data.repository.AlertRepository
import com.smarthome.control.data.repository.DeviceRepository
import com.smarthome.control.data.repository.FloorRepository
import com.smarthome.control.data.repository.UsageEventRepository
import com.smarthome.control.data.repository.UserRepository

/**
 * The repositories, constructed once and shared.
 *
 * A plain object rather than a dependency-injection framework: there are five
 * dependencies, none of them scoped, and Hilt would add a build plugin and a lot of
 * annotations to solve a problem this project does not have. Every repository still takes
 * its collaborators as constructor parameters with defaults, so a test can substitute a
 * Firestore emulator instance without going through here.
 *
 * Nothing in this file touches Firebase at construction time -- the repositories are
 * `by lazy`, so the first access happens after `FirebaseApp` has initialised.
 */
object SmartHomeData {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    val users: UserRepository by lazy { UserRepository(firestore = firestore) }
    val usageEvents: UsageEventRepository by lazy { UsageEventRepository(firestore) }
    val devices: DeviceRepository by lazy { DeviceRepository(firestore, usageEvents) }
    val floors: FloorRepository by lazy {
        FloorRepository(firestore = firestore, deviceRepository = devices)
    }
    val alerts: AlertRepository by lazy { AlertRepository(firestore) }
}
