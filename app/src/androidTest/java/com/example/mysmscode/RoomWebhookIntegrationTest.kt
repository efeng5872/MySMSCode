package com.example.mysmscode

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mysmscode.data.RepositorySaveResult
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.RobotWebhookStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomWebhookIntegrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var harness: AndroidTestHarness

    @Before
    fun setUp() {
        harness = installTestHarness(context, webhookCipher = PrefixWebhookCipher())
    }

    @After
    fun tearDown() {
        harness.close()
    }

    @Test
    fun save_encryptsWebhookInRoomAndFindByIdReturnsPlainText() = runBlocking {
        val now = System.currentTimeMillis()
        val robot = when (
            val result = harness.container.robotRepository.save(
                RobotEndpoint(
                    name = "加密测试机器人",
                    type = RobotType.FEISHU,
                    enabled = true,
                    webhookUrl = "https://open.feishu.cn/open-apis/bot/v2/hook/demo",
                    createdAt = now,
                    updatedAt = now,
                )
            )
        ) {
            is RepositorySaveResult.Success -> result.value
            else -> throw AssertionError("机器人保存失败：$result")
        }

        val storedEntity = harness.database.robotEndpointDao().getAll().single()
        assertTrue(storedEntity.webhookUrl.startsWith("enc:"))

        val loadedRobot = harness.container.robotRepository.findById(robot.id)
        assertEquals("https://open.feishu.cn/open-apis/bot/v2/hook/demo", loadedRobot?.webhookUrl)
    }

    @Test
    fun findById_marksRobotForReentryWhenDecryptFails() = runBlocking {
        harness.close()
        harness = installTestHarness(context, webhookCipher = DecryptFailingTestWebhookCipher())

        harness.database.robotEndpointDao().insert(
            buildStoredRobotEntity(webhookUrl = "enc:broken-payload")
        )

        val loadedRobot = harness.container.robotRepository.findById(1L)

        assertEquals("", loadedRobot?.webhookUrl)
        assertEquals(RobotWebhookStatus.REENTRY_REQUIRED, loadedRobot?.webhookStatus)
    }
}
