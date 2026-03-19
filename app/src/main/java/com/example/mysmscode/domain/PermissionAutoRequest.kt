package com.example.mysmscode.domain

fun shouldAutoRequestPermissions(
    snapshot: AppPermissionSnapshot,
    hasRequestedAutomatically: Boolean,
): Boolean {
    return !snapshot.canStartMonitoring && !hasRequestedAutomatically
}
