package com.example.mysmscode.domain

data class ForwardMessage(
    val senderNumber: String,
    val messageBody: String,
    val matchedKeyword: String?,
    val receivedAt: Long,
)