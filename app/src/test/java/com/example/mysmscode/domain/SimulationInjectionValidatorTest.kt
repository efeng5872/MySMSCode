package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimulationInjectionValidatorTest {

    @Test
    fun validate_returnsRequestWhenSenderAndMessageArePresent() {
        val result = validateSimulationInjection(
            senderNumber = " 10690001 ",
            messageBody = " test message ",
        )

        require(result is SimulationInjectionValidation.Valid)
        assertEquals("10690001", result.request.senderNumber)
        assertEquals("test message", result.request.messageBody)
    }

    @Test
    fun validate_rejectsBlankSenderNumber() {
        val result = validateSimulationInjection(
            senderNumber = "   ",
            messageBody = "message",
        )

        require(result is SimulationInjectionValidation.Invalid)
        assertEquals("模拟发送号码不能为空。", result.reason)
    }

    @Test
    fun validate_rejectsBlankMessageBody() {
        val result = validateSimulationInjection(
            senderNumber = "10690001",
            messageBody = "   ",
        )

        require(result is SimulationInjectionValidation.Invalid)
        assertEquals("模拟短信内容不能为空。", result.reason)
    }
}
