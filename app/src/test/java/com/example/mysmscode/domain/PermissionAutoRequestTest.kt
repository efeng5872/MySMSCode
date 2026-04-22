package com.example.mysmscode.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionAutoRequestTest {

    @Test
    fun shouldAutoRequestPermissions_returnsTrueWhenPermissionsMissingAndNotRequestedYet() {
        val snapshot = AppPermissionSnapshot(
            receiveSmsGranted = false,
            postNotificationsGranted = true,
            notificationPermissionRequired = true,
        )

        assertTrue(shouldAutoRequestPermissions(snapshot, hasRequestedAutomatically = false))
    }

    @Test
    fun shouldAutoRequestPermissions_returnsFalseWhenAlreadyRequestedAutomatically() {
        val snapshot = AppPermissionSnapshot(
            receiveSmsGranted = false,
            postNotificationsGranted = true,
            notificationPermissionRequired = true,
        )

        assertFalse(shouldAutoRequestPermissions(snapshot, hasRequestedAutomatically = true))
    }

    @Test
    fun shouldAutoRequestPermissions_returnsFalseWhenAllPermissionsAreGranted() {
        val snapshot = AppPermissionSnapshot(
            receiveSmsGranted = true,
            postNotificationsGranted = true,
            notificationPermissionRequired = true,
        )

        assertFalse(shouldAutoRequestPermissions(snapshot, hasRequestedAutomatically = false))
    }
}
