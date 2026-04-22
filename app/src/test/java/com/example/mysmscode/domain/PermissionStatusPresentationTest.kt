package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun zh(vararg codes: Int): String = codes.map(Int::toChar).joinToString("")

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
        assertEquals(zh(0x9700, 0x8981, 0x6743, 0x9650), uiState.title)
        assertTrue(uiState.message.contains(zh(0x63A5, 0x6536, 0x77ED, 0x4FE1)))
        assertEquals(zh(0x7533, 0x8BF7, 0x6743, 0x9650), uiState.actionLabel)
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
        assertTrue(uiState.message.contains(zh(0x901A, 0x77E5, 0x6743, 0x9650)))
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
        assertEquals(zh(0x6743, 0x9650, 0x5DF2, 0x5C31, 0x7EEA), uiState.title)
        assertEquals(zh(0x6240, 0x6709, 0x5FC5, 0x9700, 0x6743, 0x9650, 0x5747, 0x5DF2, 0x6388, 0x4E88, 0x3002), uiState.message)
        assertEquals(zh(0x6743, 0x9650, 0x5DF2, 0x5C31, 0x7EEA), uiState.actionLabel)
    }
}
