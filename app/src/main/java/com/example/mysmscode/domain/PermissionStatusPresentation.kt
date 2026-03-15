package com.example.mysmscode.domain

data class AppPermissionSnapshot(
    val receiveSmsGranted: Boolean,
    val readSmsGranted: Boolean,
    val postNotificationsGranted: Boolean,
    val notificationPermissionRequired: Boolean,
) {
    val missingPermissions: List<String>
        get() = buildList {
            if (!receiveSmsGranted) add("Receive SMS")
            if (!readSmsGranted) add("Read SMS")
            if (notificationPermissionRequired && !postNotificationsGranted) add("Post notifications")
        }

    val canStartMonitoring: Boolean
        get() = missingPermissions.isEmpty()
}

data class PermissionUiState(
    val title: String,
    val message: String,
    val actionLabel: String,
    val canStartMonitoring: Boolean,
)

fun buildPermissionUiState(snapshot: AppPermissionSnapshot): PermissionUiState {
    return if (snapshot.canStartMonitoring) {
        PermissionUiState(
            title = "Permissions ready",
            message = "All required permissions are granted.",
            actionLabel = "Permissions Ready",
            canStartMonitoring = true,
        )
    } else {
        PermissionUiState(
            title = "Permissions required",
            message = "Grant these permissions before monitoring can start: ${snapshot.missingPermissions.joinToString()}.",
            actionLabel = "Grant Permissions",
            canStartMonitoring = false,
        )
    }
}
