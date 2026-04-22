package com.example.mysmscode

import android.util.Log

object DebugTraceLogger {
    private const val TAG = "MySMSCodeTrace"

    fun d(message: String) {
        runCatching {
            Log.d(TAG, message)
        }
    }

    fun w(message: String) {
        runCatching {
            Log.w(TAG, message)
        }
    }
}
