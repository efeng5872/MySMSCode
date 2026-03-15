package com.example.mysmscode.domain

data class SmsRecordPreview(
    val senderNumber: String,
    val messageBody: String,
    val status: String,
    val source: String,
    val receivedAt: Long,
)