package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

private fun zh(vararg codes: Int): String = codes.map(Int::toChar).joinToString("")

class SmsRecordPresentationTest {

    private val preview = SmsRecordPreview(
        senderNumber = "10690001",
        messageBody = "Your code is 123456",
        status = "CONFIGURATION_FAILED",
        source = "SIMULATION",
        receivedAt = 0L,
    )

    @Test
    fun receivedAtLabel_formatsReadableLocalTime() {
        assertEquals(
            "1970-01-01 08:00:00",
            preview.receivedAtLabel(zoneId = ZoneId.of("Asia/Shanghai")),
        )
    }

    @Test
    fun statusLabel_mapsKnownStatusesToReadableText() {
        assertEquals(zh(0x914D, 0x7F6E, 0x5F02, 0x5E38), preview.statusLabel())
        assertEquals(zh(0x8F6C, 0x53D1, 0x6210, 0x529F), preview.copy(status = "SUCCESS").statusLabel())
        assertEquals(zh(0x5173, 0x952E, 0x5B57, 0x672A, 0x547D, 0x4E2D), preview.copy(status = "NOT_MATCHED").statusLabel())
    }

    @Test
    fun sourceLabel_mapsKnownSourcesToReadableText() {
        assertEquals(zh(0x6A21, 0x62DF, 0x6CE8, 0x5165), preview.sourceLabel())
        assertEquals(zh(0x6536, 0x5230, 0x77ED, 0x4FE1), preview.copy(source = "REAL_SMS").sourceLabel())
    }
}
