package com.example.mysmscode.domain

private fun zh(vararg codes: Int): String = codes.map(Int::toChar).joinToString("")

data class AppPermissionSnapshot(
    val receiveSmsGranted: Boolean,
    val postNotificationsGranted: Boolean,
    val notificationPermissionRequired: Boolean,
) {
    val missingPermissions: List<String>
        get() = buildList {
            if (!receiveSmsGranted) add(zh(0x63A5, 0x6536, 0x77ED, 0x4FE1))
            if (notificationPermissionRequired && !postNotificationsGranted) add(zh(0x901A, 0x77E5, 0x6743, 0x9650))
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
            title = zh(0x6743, 0x9650, 0x5DF2, 0x5C31, 0x7EEA),
            message = zh(0x6240, 0x6709, 0x5FC5, 0x9700, 0x6743, 0x9650, 0x5747, 0x5DF2, 0x6388, 0x4E88, 0x3002),
            actionLabel = zh(0x6743, 0x9650, 0x5DF2, 0x5C31, 0x7EEA),
            canStartMonitoring = true,
        )
    } else {
        PermissionUiState(
            title = zh(0x9700, 0x8981, 0x6743, 0x9650),
            message = zh(0x5E94, 0x7528, 0x5F53, 0x524D, 0x8FD8, 0x7F3A, 0x5C11, 0x4EE5, 0x4E0B, 0x6743, 0x9650, 0xFF1A) + snapshot.missingPermissions.joinToString() + zh(0x3002),
            actionLabel = zh(0x7533, 0x8BF7, 0x6743, 0x9650),
            canStartMonitoring = false,
        )
    }
}
