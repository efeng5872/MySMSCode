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
import com.example.mysmscode.domain.ForwardMessage
import com.example.mysmscode.domain.ProcessIncomingSmsUseCase
import com.example.mysmscode.domain.SmsSource
import com.example.mysmscode.network.WebhookDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MonitoringForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processingUseCase = ProcessIncomingSmsUseCase()
    private val outcomeUseCase = CreateProcessingOutcomeUseCase()
    private val finalizeOutcomeUseCase = FinalizeForwardingOutcomeUseCase()
    private val webhookDispatcher = WebhookDispatcher()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring skeleton active"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PROCESS_SMS -> {
                val senderNumber = intent.getStringExtra(EXTRA_SENDER_NUMBER).orEmpty()
                val messageBody = intent.getStringExtra(EXTRA_MESSAGE_BODY).orEmpty()
                if (senderNumber.isNotBlank() && messageBody.isNotBlank()) {
                    serviceScope.launch {
                        handleIncomingSms(senderNumber, messageBody)
                    }
                }
            }

            ACTION_START_MONITORING -> {
                startForeground(NOTIFICATION_ID, buildNotification("Monitoring started"))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun handleIncomingSms(senderNumber: String, messageBody: String) {
        val container = (application as MySmsCodeApplication).container
        val rules = container.senderRuleRepository.getAll()
        val robots = container.robotRepository.getAll()
        val processingResult = processingUseCase.process(
            senderNumber = senderNumber,
            messageBody = messageBody,
            rules = rules,
            robots = robots,
        )
        val initialOutcome = outcomeUseCase.create(
            senderNumber = senderNumber,
            messageBody = messageBody,
            source = SmsSource.REAL_SMS,
            processingResult = processingResult,
            receivedAt = System.currentTimeMillis(),
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
                finalizeOutcomeUseCase.finalize(initialOutcome, dispatchResults)
            }
            container.processingRepository.saveOutcome(finalOutcome)
            val recordCount = container.processingRepository.countRecords()
            val text = "Processed ${finalOutcome.record.senderNumber} with ${finalOutcome.record.status.name}. Stored records: $recordCount"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private fun buildNotification(contentText: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("MySMSCode Monitoring")
        .setContentText(contentText)
        .setOngoing(true)
        .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monitoring",
            NotificationManager.IMPORTANCE_LOW,
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "monitoring_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START_MONITORING = "com.example.mysmscode.action.START_MONITORING"
        private const val ACTION_PROCESS_SMS = "com.example.mysmscode.action.PROCESS_SMS"
        private const val EXTRA_SENDER_NUMBER = "extra_sender_number"
        private const val EXTRA_MESSAGE_BODY = "extra_message_body"

        fun startMonitoring(context: Context) {
            val intent = Intent(context, MonitoringForegroundService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun enqueueIncomingSms(context: Context, senderNumber: String, messageBody: String) {
            val intent = Intent(context, MonitoringForegroundService::class.java).apply {
                action = ACTION_PROCESS_SMS
                putExtra(EXTRA_SENDER_NUMBER, senderNumber)
                putExtra(EXTRA_MESSAGE_BODY, messageBody)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}