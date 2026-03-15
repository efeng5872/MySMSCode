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
        assertEquals("Sender number is required for simulation.", result.reason)
    }

    @Test
    fun validate_rejectsBlankMessageBody() {
        val result = validateSimulationInjection(
            senderNumber = "10690001",
            messageBody = "   ",
        )

        require(result is SimulationInjectionValidation.Invalid)
        assertEquals("Message body is required for simulation.", result.reason)
    }
}