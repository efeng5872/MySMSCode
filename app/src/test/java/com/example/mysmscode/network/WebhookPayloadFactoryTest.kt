package com.example.mysmscode.network

import com.example.mysmscode.domain.ForwardMessage
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookPayloadFactoryTest {

    private val factory = WebhookPayloadFactory()

    @Test
    fun feishuPayload_containsSenderAndMessageBody() {
        val payload = factory.createFeishuPayload(
            ForwardMessage(
                senderNumber = "10690001",
                messageBody = "Code 1234",
                matchedKeyword = "code",
                receivedAt = 123456789L,
            )
        )

        assertTrue(payload.contains("\"msg_type\":\"text\""))
        assertTrue(payload.contains("10690001"))
        assertTrue(payload.contains("Code 1234"))
    }

    @Test
    fun wecomPayload_containsSenderAndMessageBody() {
        val payload = factory.createWeComPayload(
            ForwardMessage(
                senderNumber = "Bank-01",
                messageBody = "OTP 9988",
                matchedKeyword = "otp",
                receivedAt = 123456789L,
            )
        )

        assertTrue(payload.contains("\"msgtype\":\"text\""))
        assertTrue(payload.contains("Bank-01"))
        assertTrue(payload.contains("OTP 9988"))
    }

    @Test
    fun commonText_includesKeywordAndTimestamp() {
        val text = factory.createCommonText(
            ForwardMessage(
                senderNumber = "Bank-01",
                messageBody = "OTP 9988",
                matchedKeyword = "otp",
                receivedAt = 123456789L,
            )
        )

        assertTrue(text.contains("Bank-01"))
        assertTrue(text.contains("otp"))
        assertTrue(text.contains("123456789"))
    }
}