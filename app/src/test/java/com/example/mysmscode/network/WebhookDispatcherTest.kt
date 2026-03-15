package com.example.mysmscode.network

import com.example.mysmscode.domain.ForwardAttemptStatus
import com.example.mysmscode.domain.ForwardDispatchResult
import com.example.mysmscode.domain.ForwardMessage
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WebhookDispatcherTest {

    @Test
    fun dispatch_postsFeishuPayloadAndReturnsSuccess() {
        var capturedUrl = ""
        var capturedPayload = ""
        val dispatcher = WebhookDispatcher(
            payloadFactory = WebhookPayloadFactory(),
            poster = { url, payload ->
                capturedUrl = url
                capturedPayload = payload
                WebhookPostResult(responseCode = 200, responseMessage = "ok")
            }
        )

        val result = dispatcher.dispatch(
            robot = RobotEndpoint(
                id = 1L,
                name = "Ops",
                type = RobotType.FEISHU,
                webhookUrl = "https://example.com/feishu",
                enabled = true,
            ),
            message = ForwardMessage(
                senderNumber = "10690001",
                messageBody = "Code 1234",
                matchedKeyword = "code",
                receivedAt = 123456789L,
            )
        )

        assertEquals("https://example.com/feishu", capturedUrl)
        assertTrue(capturedPayload.contains("msg_type"))
        assertEquals(ForwardAttemptStatus.SUCCESS, result.status)
        assertEquals("200", result.responseCode)
    }

    @Test
    fun dispatch_returnsFailedResultWhenPosterThrows() {
        val dispatcher = WebhookDispatcher(
            payloadFactory = WebhookPayloadFactory(),
            poster = { _, _ -> throw IOException("timeout") }
        )

        val result = dispatcher.dispatch(
            robot = RobotEndpoint(
                id = 2L,
                name = "Ops WeCom",
                type = RobotType.WECOM,
                webhookUrl = "https://example.com/wecom",
                enabled = true,
            ),
            message = ForwardMessage(
                senderNumber = "10690001",
                messageBody = "Code 1234",
                matchedKeyword = "code",
                receivedAt = 123456789L,
            )
        )

        assertEquals(
            ForwardDispatchResult(
                robotId = 2L,
                channel = "WECOM",
                status = ForwardAttemptStatus.FAILED,
                responseCode = null,
                responseMessage = "timeout",
                recoverable = true,
            ),
            result,
        )
    }
}
