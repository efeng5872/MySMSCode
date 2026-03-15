package com.example.mysmscode.network

import com.example.mysmscode.domain.ForwardAttemptStatus
import com.example.mysmscode.domain.ForwardDispatchResult
import com.example.mysmscode.domain.ForwardMessage
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

class WebhookPayloadFactory {

    fun createCommonText(message: ForwardMessage): String {
        return buildString {
            appendLine("Sender: ${message.senderNumber}")
            appendLine("ReceivedAt: ${message.receivedAt}")
            appendLine("MatchedKeyword: ${message.matchedKeyword.orEmpty()}")
            append("Message: ${message.messageBody}")
        }
    }

    fun createFeishuPayload(message: ForwardMessage): String {
        return "{" +
            "\"msg_type\":\"text\"," +
            "\"content\":{\"text\":\"${escape(createCommonText(message))}\"}" +
            "}"
    }

    fun createWeComPayload(message: ForwardMessage): String {
        return "{" +
            "\"msgtype\":\"text\"," +
            "\"text\":{\"content\":\"${escape(createCommonText(message))}\"}" +
            "}"
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }
}

data class WebhookPostResult(
    val responseCode: Int,
    val responseMessage: String?,
)

class WebhookDispatcher(
    private val payloadFactory: WebhookPayloadFactory = WebhookPayloadFactory(),
    private val poster: (String, String) -> WebhookPostResult = ::postJson,
) {
    fun dispatch(robot: RobotEndpoint, message: ForwardMessage): ForwardDispatchResult {
        val payload = when (robot.type) {
            RobotType.FEISHU -> payloadFactory.createFeishuPayload(message)
            RobotType.WECOM -> payloadFactory.createWeComPayload(message)
        }

        return try {
            val postResult = poster(robot.webhookUrl, payload)
            val success = postResult.responseCode in 200..299
            ForwardDispatchResult(
                robotId = robot.id,
                channel = robot.type.name,
                status = if (success) ForwardAttemptStatus.SUCCESS else ForwardAttemptStatus.FAILED,
                responseCode = postResult.responseCode.toString(),
                responseMessage = postResult.responseMessage,
                recoverable = !success,
            )
        } catch (error: Exception) {
            ForwardDispatchResult(
                robotId = robot.id,
                channel = robot.type.name,
                status = ForwardAttemptStatus.FAILED,
                responseCode = null,
                responseMessage = error.message ?: error::class.java.simpleName,
                recoverable = error is UnknownHostException || error is java.io.IOException,
            )
        }
    }
}

private fun postJson(url: String, payload: String): WebhookPostResult {
    val connection = URL(url).openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload)
        }
        val responseCode = connection.responseCode
        val responseMessage = connection.responseMessage
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        stream?.use { it.readBytes() }
        WebhookPostResult(responseCode = responseCode, responseMessage = responseMessage)
    } finally {
        connection.disconnect()
    }
}