package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

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
        assertEquals("配置异常", preview.statusLabel())
        assertEquals("转发成功", preview.copy(status = "SUCCESS").statusLabel())
        assertEquals("关键字未命中", preview.copy(status = "NOT_MATCHED").statusLabel())
    }

    @Test
    fun sourceLabel_mapsKnownSourcesToReadableText() {
        assertEquals("模拟注入", preview.sourceLabel())
        assertEquals("收到短信", preview.copy(source = "REAL_SMS").sourceLabel())
    }
}
