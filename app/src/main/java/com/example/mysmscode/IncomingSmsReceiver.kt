package com.example.mysmscode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage

class IncomingSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            return
        }

        val senderNumber = messages.firstNotNullOfOrNull(SmsMessage::getDisplayOriginatingAddress).orEmpty()
        val messageBody = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        if (senderNumber.isNotBlank() && messageBody.isNotBlank()) {
            MonitoringForegroundService.enqueueIncomingSms(context, senderNumber, messageBody)
        }
    }
}