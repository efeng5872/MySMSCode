package com.example.mysmscode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.example.mysmscode.domain.buildIncomingSmsTrace
import com.example.mysmscode.domain.shouldProcessIncomingSms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IncomingSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            DebugTraceLogger.w("incoming_sms ignored because no message parts were present")
            return
        }

        val senderNumber = messages.firstNotNullOfOrNull(SmsMessage::getDisplayOriginatingAddress).orEmpty()
        val messageBody = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        if (senderNumber.isNotBlank() && messageBody.isNotBlank()) {
            DebugTraceLogger.d(
                buildIncomingSmsTrace(
                    senderNumber = senderNumber,
                    messageBody = messageBody,
                    partCount = messages.size,
                )
            )
            val pendingResult = goAsync()
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settingsRepository = (appContext as MySmsCodeApplication).container.settingsRepository
                    val state = settingsRepository.getMonitoringState()
                    if (state.shouldProcessIncomingSms()) {
                        MonitoringForegroundService.enqueueIncomingSms(appContext, senderNumber, messageBody)
                    } else {
                        DebugTraceLogger.d(
                            "incoming_sms ignored because monitoring is disabled sender=$senderNumber enabled=${state.monitoringEnabled} stoppedByUser=${state.stoppedByUser}"
                        )
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            DebugTraceLogger.w("incoming_sms ignored because sender or body was blank")
        }
    }
}
