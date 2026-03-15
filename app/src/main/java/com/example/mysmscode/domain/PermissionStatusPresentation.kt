package com.example.mysmscode.domain

data class AppPermissionSnapshot(
    val receiveSmsGranted: Boolean,
    val readSmsGranted: Boolean,
    val postNotificationsGranted: Boolean,
    val notificationPermissionRequired: Boolean,
) {
    val missingPermissions: List<String>
        get() = buildList {
            if (!receiveSmsGranted) add("接收短信")
            if (!readSmsGranted) add("读取短信")
            if (notificationPermissionRequired && !postNotificationsGranted) add("通知权限")
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
            title = "权限已就绪",
            message = "所有必需权限均已授予。",
            actionLabel = "权限已就绪",
            canStartMonitoring = true,
        )
    } else {
        PermissionUiState(
            title = "需要权限",
            message = "启动监控前，请先授予以下权限：${snapshot.missingPermissions.joinToString()}。",
            actionLabel = "申请权限",
            canStartMonitoring = false,
        )
    }
}
