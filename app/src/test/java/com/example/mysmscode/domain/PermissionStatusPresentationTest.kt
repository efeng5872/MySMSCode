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
        assertEquals("Permissions required", uiState.title)
        assertTrue(uiState.message.contains("Receive SMS"))
        assertEquals("Grant Permissions", uiState.actionLabel)
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
        assertTrue(uiState.message.contains("Post notifications"))
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
        assertEquals("Permissions ready", uiState.title)
        assertEquals("All required permissions are granted.", uiState.message)
        assertEquals("Permissions Ready", uiState.actionLabel)
    }
}
