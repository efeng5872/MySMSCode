package com.example.mysmscode

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.app.ActivityManager
import androidx.room.Room
import com.example.mysmscode.data.AppDatabase
import com.example.mysmscode.data.PassthroughWebhookCipher
import com.example.mysmscode.data.RepositorySaveResult
import com.example.mysmscode.data.RobotEndpointEntity
import com.example.mysmscode.data.WebhookCipher
import com.example.mysmscode.domain.ForwardAttemptStatus
import com.example.mysmscode.domain.ForwardDispatchResult
import com.example.mysmscode.domain.ForwardMessage
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.network.WebhookDispatching
import org.junit.Assert.fail

internal data class DispatchCall(
    val robot: RobotEndpoint,
    val message: ForwardMessage,
)

internal class RecordingWebhookDispatcher(
    private val resultProvider: (RobotEndpoint, ForwardMessage) -> ForwardDispatchResult = { robot, _ ->
        ForwardDispatchResult(
            robotId = robot.id,
            channel = robot.type.name,
            status = ForwardAttemptStatus.SUCCESS,
            responseCode = "200",
            responseMessage = "ok",
            recoverable = false,
        )
    },
) : WebhookDispatching {
    val dispatchCalls = mutableListOf<DispatchCall>()

    override fun dispatch(robot: RobotEndpoint, message: ForwardMessage): ForwardDispatchResult {
        dispatchCalls += DispatchCall(robot, message)
        return resultProvider(robot, message)
    }
}

internal class PrefixWebhookCipher : WebhookCipher {
    override fun isEncrypted(value: String): Boolean = value.startsWith("enc:")

    override fun encrypt(plainText: String): String = if (isEncrypted(plainText)) {
        plainText
    } else {
        "enc:$plainText"
    }

    override fun decrypt(storedValue: String): String = storedValue.removePrefix("enc:")
}

internal class DecryptFailingTestWebhookCipher : WebhookCipher {
    override fun isEncrypted(value: String): Boolean = value.startsWith("enc:")

    override fun encrypt(plainText: String): String = "enc:$plainText"

    override fun decrypt(storedValue: String): String {
        throw IllegalStateException("key missing")
    }
}

internal class AndroidTestHarness(
    val appContext: Context,
    val application: MySmsCodeApplication,
    val container: AppContainer,
    val database: AppDatabase,
    val dispatcher: RecordingWebhookDispatcher,
) {
    fun close() {
        RetryAlarmScheduler(appContext).cancel()
        appContext.stopService(Intent(appContext, MonitoringForegroundService::class.java))
        waitForServiceShutdown(appContext)
        application.containerOverride = null
        database.close()
    }
}

internal fun installTestHarness(
    context: Context,
    webhookCipher: WebhookCipher = PassthroughWebhookCipher(),
    dispatcher: RecordingWebhookDispatcher = RecordingWebhookDispatcher(),
): AndroidTestHarness {
    val application = context.applicationContext as MySmsCodeApplication
    val database = Room.inMemoryDatabaseBuilder(
        application,
        AppDatabase::class.java,
    ).allowMainThreadQueries()
        .build()
    val container = AppContainer(
        application = application,
        databaseOverride = database,
        webhookCipherOverride = webhookCipher,
        webhookDispatcherOverride = dispatcher,
    )
    application.containerOverride = container
    return AndroidTestHarness(
        appContext = application,
        application = application,
        container = container,
        database = database,
        dispatcher = dispatcher,
    )
}

internal fun waitUntil(
    message: String,
    timeoutMs: Long = 5_000L,
    intervalMs: Long = 50L,
    predicate: () -> Boolean,
) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        if (predicate()) {
            return
        }
        Thread.sleep(intervalMs)
    }
    fail(message)
}

internal fun assertStaysTrue(
    message: String,
    durationMs: Long = 800L,
    intervalMs: Long = 50L,
    predicate: () -> Boolean,
) {
    val deadline = SystemClock.elapsedRealtime() + durationMs
    while (SystemClock.elapsedRealtime() < deadline) {
        if (!predicate()) {
            fail(message)
        }
        Thread.sleep(intervalMs)
    }
}

internal fun <T> RepositorySaveResult<T>.requireSuccess(): T = when (this) {
    is RepositorySaveResult.Success -> value
    else -> throw AssertionError("Expected repository save success but was $this")
}

internal fun buildStoredRobotEntity(
    webhookUrl: String,
    name: String = "测试机器人",
): RobotEndpointEntity {
    return RobotEndpointEntity(
        id = 0L,
        name = name,
        type = com.example.mysmscode.domain.RobotType.FEISHU,
        enabled = true,
        webhookUrl = webhookUrl,
        createdAt = 1L,
        updatedAt = 1L,
    )
}

private fun waitForServiceShutdown(
    context: Context,
    timeoutMs: Long = 2_000L,
) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        if (!isMonitoringServiceRunning(context)) {
            return
        }
        Thread.sleep(50L)
    }
}

private fun isMonitoringServiceRunning(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION")
    return activityManager.getRunningServices(Int.MAX_VALUE).any { service ->
        service.service.className == MonitoringForegroundService::class.java.name
    }
}
