package com.example.mysmscode

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.mysmscode.domain.CreateProcessingOutcomeUseCase
import com.example.mysmscode.domain.FinalizeForwardingOutcomeUseCase
import com.example.mysmscode.domain.ForwardAttemptStatus
import com.example.mysmscode.domain.ForwardDispatchResult
import com.example.mysmscode.domain.ForwardMessage
import com.example.mysmscode.domain.ProcessIncomingSmsUseCase
import com.example.mysmscode.domain.RetryFailedAttemptUseCase
import com.example.mysmscode.domain.RetryableAttempt
import com.example.mysmscode.domain.SmsSource
import com.example.mysmscode.domain.buildProcessingTrace
import com.example.mysmscode.network.WebhookDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MonitoringForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processingUseCase = ProcessIncomingSmsUseCase()
    private val outcomeUseCase = CreateProcessingOutcomeUseCase()
    private val finalizeOutcomeUseCase = FinalizeForwardingOutcomeUseCase()
    private val retryFailedAttemptUseCase = RetryFailedAttemptUseCase()
    private val webhookDispatcher = WebhookDispatcher()
    private val retryMutex = Mutex()
    private var retryLoopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_service_active)))
        DebugTraceLogger.d("service_created action=foreground_start")
        ensureRetryLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureRetryLoop()
        DebugTraceLogger.d("service_start action=${intent?.action ?: "null"} startId=$startId")
        when (intent?.action) {
            ACTION_PROCESS_SMS -> {
                val senderNumber = intent.getStringExtra(EXTRA_SENDER_NUMBER).orEmpty()
                val messageBody = intent.getStringExtra(EXTRA_MESSAGE_BODY).orEmpty()
                if (senderNumber.isNotBlank() && messageBody.isNotBlank()) {
                    val sourceName = intent.getStringExtra(EXTRA_SMS_SOURCE)
                    val source = sourceName?.let { runCatching { SmsSource.valueOf(it) }.getOrNull() } ?: SmsSource.REAL_SMS
                    serviceScope.launch {
                        handleIncomingSms(senderNumber, messageBody, source)
                        processDueRetries()
                    }
                } else {
                    DebugTraceLogger.w("service_start ignored blank payload for action=$ACTION_PROCESS_SMS")
                }
            }

            ACTION_START_MONITORING -> {
                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_monitoring_started)))
                DebugTraceLogger.d("service_monitoring_requested")
                serviceScope.launch {
                    processDueRetries()
                }
            }

            ACTION_STOP_MONITORING -> {
                DebugTraceLogger.d("service_monitoring_stop_requested")
                stopForeground(STOP_FOREGROUND_REMOVE)
                showStoppedNotification()
                stopSelf()
            }

            ACTION_RETRY_ATTEMPT -> {
                val attemptId = intent.getLongExtra(EXTRA_ATTEMPT_ID, -1L)
                if (attemptId > 0L) {
                    serviceScope.launch {
                        handleRetryAttempt(attemptId)
                    }
                } else {
                    DebugTraceLogger.w("service_retry ignored invalid attemptId=$attemptId")
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        retryLoopJob?.cancel()
        serviceScope.cancel()
        DebugTraceLogger.d("service_destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureRetryLoop() {
        if (retryLoopJob?.isActive == true) {
            return
        }
        retryLoopJob = serviceScope.launch {
            while (isActive) {
                processDueRetries()
                delay(RETRY_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun handleIncomingSms(senderNumber: String, messageBody: String, source: SmsSource) {
        val container = (application as MySmsCodeApplication).container
        val rules = container.senderRuleRepository.getAll()
        val robots = container.robotRepository.getAll()
        val retryPolicyConfig = container.settingsRepository.getRetryPolicyConfig()
        val processingResult = processingUseCase.process(
            senderNumber = senderNumber,
            messageBody = messageBody,
            rules = rules,
            robots = robots,
        )
        DebugTraceLogger.d(buildProcessingTrace(source, processingResult))
        val attemptedAt = System.currentTimeMillis()
        val initialOutcome = outcomeUseCase.create(
            senderNumber = senderNumber,
            messageBody = messageBody,
            source = source,
            processingResult = processingResult,
            receivedAt = attemptedAt,
        )
        if (initialOutcome != null) {
            val forwardMessage = ForwardMessage(
                senderNumber = senderNumber,
                messageBody = messageBody,
                matchedKeyword = initialOutcome.record.matchedKeyword,
                receivedAt = initialOutcome.record.receivedAt,
            )
            val dispatchResults = initialOutcome.attempts.mapNotNull { attempt ->
                robots.firstOrNull { it.id == attempt.robotId && it.enabled }?.let { robot ->
                    webhookDispatcher.dispatch(robot, forwardMessage)
                }
            }
            val finalOutcome = if (initialOutcome.attempts.isEmpty()) {
                initialOutcome
            } else {
                finalizeOutcomeUseCase.finalize(initialOutcome, dispatchResults, attemptedAt, retryPolicyConfig)
            }
            container.processingRepository.saveOutcome(finalOutcome)
            notifyStatus(getString(R.string.notification_processed, finalOutcome.record.senderNumber, smsStatusLabel(this, finalOutcome.record.toPreview())))
        } else {
            DebugTraceLogger.d("processing_result source=$source status=IGNORED attempts=0 sender=$senderNumber")
        }
    }

    private suspend fun handleRetryAttempt(attemptId: Long) {
        retryMutex.withLock {
            val container = (application as MySmsCodeApplication).container
            val failedAttempt = container.processingRepository.getRetryableAttemptById(attemptId) ?: run {
                DebugTraceLogger.w("retry_lookup missing attemptId=$attemptId")
                notifyStatus(getString(R.string.notification_retry_skipped_not_found))
                return
            }
            executeRetry(container, failedAttempt)
        }
    }

    private suspend fun processDueRetries() {
        retryMutex.withLock {
            val container = (application as MySmsCodeApplication).container
            val dueAttempts = container.processingRepository.getDueRetryableAttempts(System.currentTimeMillis(), limit = 20)
            if (dueAttempts.isNotEmpty()) {
                DebugTraceLogger.d("retry_due count=${dueAttempts.size}")
            }
            dueAttempts.forEach { attempt ->
                executeRetry(container, attempt)
            }
        }
    }

    private suspend fun executeRetry(container: AppContainer, failedAttempt: RetryableAttempt) {
        val retryPolicyConfig = container.settingsRepository.getRetryPolicyConfig()
        val robot = container.robotRepository.getAll().firstOrNull { it.id == failedAttempt.robotId && it.enabled }
        val dispatchResult = if (robot == null) {
            ForwardDispatchResult(
                robotId = failedAttempt.robotId,
                channel = failedAttempt.robotType.name,
                status = ForwardAttemptStatus.FAILED,
                responseCode = null,
                responseMessage = "Robot endpoint is missing or disabled.",
                recoverable = false,
            )
        } else {
            webhookDispatcher.dispatch(
                robot = robot,
                message = ForwardMessage(
                    senderNumber = failedAttempt.senderNumber,
                    messageBody = failedAttempt.messageBody,
                    matchedKeyword = failedAttempt.matchedKeyword,
                    receivedAt = failedAttempt.receivedAt,
                ),
            )
        }
        val execution = retryFailedAttemptUseCase.retry(
            failedAttempt = failedAttempt,
            dispatchResult = dispatchResult,
            attemptedAt = System.currentTimeMillis(),
            retryPolicyConfig = retryPolicyConfig,
        )
        container.processingRepository.saveRetryExecution(failedAttempt, execution)
        DebugTraceLogger.d(
            "retry_execution sender=${failedAttempt.senderNumber} channel=${failedAttempt.robotType.name} status=${execution.nextAttempt.status} attempt=${execution.nextAttempt.attemptNumber} nextRetryAt=${execution.nextAttempt.nextRetryAt ?: "none"}"
        )
        notifyStatus(getString(R.string.notification_retried, failedAttempt.senderNumber, getString(notificationRobotTypeLabelRes(failedAttempt.robotType)), getString(notificationForwardStatusLabelRes(execution.nextAttempt.status))))
    }


    private fun com.example.mysmscode.domain.SmsRecordDraft.toPreview(): com.example.mysmscode.domain.SmsRecordPreview =
        com.example.mysmscode.domain.SmsRecordPreview(
            senderNumber = senderNumber,
            messageBody = messageBody,
            status = status.name,
            source = source.name,
            receivedAt = receivedAt,
        )
    private fun notifyStatus(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun buildNotification(contentText: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(contentText)
        .setOngoing(true)
        .build()


    private fun showStoppedNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_stopped_title))
            .setContentText(getString(R.string.notification_stopped_message))
            .setAutoCancel(true)
            .setTimeoutAfter(STOPPED_NOTIFICATION_TIMEOUT_MS)
            .build()
        notificationManager.notify(STOPPED_NOTIFICATION_ID, notification)
    }
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "monitoring_channel"
        private const val NOTIFICATION_ID = 1001
        private const val STOPPED_NOTIFICATION_ID = 1002
        private const val STOPPED_NOTIFICATION_TIMEOUT_MS = 5_000L
        private const val ACTION_START_MONITORING = "com.example.mysmscode.action.START_MONITORING"
        private const val ACTION_STOP_MONITORING = "com.example.mysmscode.action.STOP_MONITORING"
        private const val ACTION_PROCESS_SMS = "com.example.mysmscode.action.PROCESS_SMS"
        private const val ACTION_RETRY_ATTEMPT = "com.example.mysmscode.action.RETRY_ATTEMPT"
        private const val EXTRA_SENDER_NUMBER = "extra_sender_number"
        private const val EXTRA_MESSAGE_BODY = "extra_message_body"
        private const val EXTRA_ATTEMPT_ID = "extra_attempt_id"
        private const val EXTRA_SMS_SOURCE = "extra_sms_source"
        private const val RETRY_POLL_INTERVAL_MS = 30_000L

        fun startMonitoring(context: Context) {
            val intent = Intent(context, MonitoringForegroundService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, MonitoringForegroundService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun enqueueIncomingSms(context: Context, senderNumber: String, messageBody: String) {
            enqueueSms(context, senderNumber, messageBody, SmsSource.REAL_SMS)
        }

        fun enqueueSimulation(context: Context, senderNumber: String, messageBody: String) {
            enqueueSms(context, senderNumber, messageBody, SmsSource.SIMULATION)
        }

        private fun enqueueSms(context: Context, senderNumber: String, messageBody: String, source: SmsSource) {
            val intent = Intent(context, MonitoringForegroundService::class.java).apply {
                action = ACTION_PROCESS_SMS
                putExtra(EXTRA_SENDER_NUMBER, senderNumber)
                putExtra(EXTRA_MESSAGE_BODY, messageBody)
                putExtra(EXTRA_SMS_SOURCE, source.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun retryFailedAttempt(context: Context, attemptId: Long) {
            val intent = Intent(context, MonitoringForegroundService::class.java).apply {
                action = ACTION_RETRY_ATTEMPT
                putExtra(EXTRA_ATTEMPT_ID, attemptId)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}




