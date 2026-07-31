package com.example.mysmscode

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForegroundServiceConfigurationIntegrationTest {

    @Test
    fun monitoringService_usesSpecialUseForegroundServiceType() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val serviceInfo = packageManager.getServiceInfo(
            ComponentName(context, MonitoringForegroundService::class.java),
            PackageManager.ComponentInfoFlags.of(0L),
        )

        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            serviceInfo.foregroundServiceType,
        )
        assertTrue(
            packageManager.checkPermission(
                Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
                context.packageName,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
}
