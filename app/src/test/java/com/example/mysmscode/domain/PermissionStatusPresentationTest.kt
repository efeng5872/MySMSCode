package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStatusPresentationTest {

    @Test
    fun buildPermissionUiState_marksSmsPermissionsAsBlocking() {
        val uiState = buildPermissionUiState(
            AppPermissionSnapshot(
                receiveSmsGranted = false,
                readSmsGranted = true,
                postNotificationsGranted = true,
                notificationPermissionRequired = true,
            )
        )

        assertFalse(uiState.canStartMonitoring)
        assertEquals("需要权限", uiState.title)
        assertTrue(uiState.message.contains("接收短信"))
        assertEquals("申请权限", uiState.actionLabel)
    }

    @Test
    fun buildPermissionUiState_marksNotificationAsRequiredOnlyOnModernAndroid() {
        val uiState = buildPermissionUiState(
            AppPermissionSnapshot(
                receiveSmsGranted = true,
                readSmsGranted = true,
                postNotificationsGranted = false,
                notificationPermissionRequired = true,
            )
        )

        assertFalse(uiState.canStartMonitoring)
        assertTrue(uiState.message.contains("通知权限"))
    }

    @Test
    fun buildPermissionUiState_ignoresNotificationPermissionWhenPlatformDoesNotRequireIt() {
        val uiState = buildPermissionUiState(
            AppPermissionSnapshot(
                receiveSmsGranted = true,
                readSmsGranted = true,
                postNotificationsGranted = false,
                notificationPermissionRequired = false,
            )
        )

        assertTrue(uiState.canStartMonitoring)
        assertEquals("权限已就绪", uiState.title)
        assertEquals("所有必需权限均已授予。", uiState.message)
        assertEquals("权限已就绪", uiState.actionLabel)
    }
}
