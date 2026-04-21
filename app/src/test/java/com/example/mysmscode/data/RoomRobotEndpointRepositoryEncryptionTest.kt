package com.example.mysmscode.data

import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomRobotEndpointRepositoryEncryptionTest {

    @Test
    fun save_storesWebhookAsCipherText() = runBlocking {
        val dao = FakeRobotEndpointDao()
        val repository = RoomRobotEndpointRepository(dao, FakeWebhookCipher())

        repository.save(
            RobotEndpoint(
                id = 0L,
                name = "测试飞书机器人",
                type = RobotType.FEISHU,
                enabled = true,
                webhookUrl = "https://open.feishu.cn/open-apis/bot/v2/hook/example",
            )
        )

        val storedWebhook = dao.getAll().single().webhookUrl
        assertTrue(storedWebhook.startsWith("enc:"))
        assertNotEquals("https://open.feishu.cn/open-apis/bot/v2/hook/example", storedWebhook)
    }

    @Test
    fun findById_returnsPlainTextWebhookAfterDecryptingCipherText() = runBlocking {
        val dao = FakeRobotEndpointDao()
        val cipher = FakeWebhookCipher()
        val encryptedWebhook = cipher.encrypt("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=demo")
        dao.insert(
            RobotEndpointEntity(
                id = 0L,
                name = "测试企业微信机器人",
                type = RobotType.WECOM,
                enabled = true,
                webhookUrl = encryptedWebhook,
                createdAt = 1L,
                updatedAt = 2L,
            )
        )
        val repository = RoomRobotEndpointRepository(dao, cipher)

        val robot = repository.findById(1L)

        assertEquals("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=demo", robot?.webhookUrl)
    }

    @Test
    fun getAll_migratesPlainTextWebhookToCipherTextOnRead() = runBlocking {
        val dao = FakeRobotEndpointDao()
        dao.insert(
            RobotEndpointEntity(
                id = 0L,
                name = "历史明文机器人",
                type = RobotType.FEISHU,
                enabled = true,
                webhookUrl = "https://open.feishu.cn/open-apis/bot/v2/hook/plain",
                createdAt = 10L,
                updatedAt = 20L,
            )
        )
        val repository = RoomRobotEndpointRepository(dao, FakeWebhookCipher())

        val robots = repository.getAll()

        assertEquals("https://open.feishu.cn/open-apis/bot/v2/hook/plain", robots.single().webhookUrl)
        assertTrue(dao.getAll().single().webhookUrl.startsWith("enc:"))
    }

    @Test
    fun save_returnsFailedWhenEncryptionThrows() = runBlocking {
        val dao = FakeRobotEndpointDao()
        val repository = RoomRobotEndpointRepository(dao, ThrowingWebhookCipher())

        val result = repository.save(
            RobotEndpoint(
                id = 0L,
                name = "加密失败机器人",
                type = RobotType.FEISHU,
                enabled = true,
                webhookUrl = "https://open.feishu.cn/open-apis/bot/v2/hook/fail",
            )
        )

        assertEquals(RepositorySaveResult.Failed, result)
    }
}

private class FakeWebhookCipher : WebhookCipher {
    override fun isEncrypted(value: String): Boolean = value.startsWith("enc:")

    override fun encrypt(plainText: String): String = if (plainText.isBlank() || isEncrypted(plainText)) {
        plainText
    } else {
        "enc:$plainText"
    }

    override fun decrypt(storedValue: String): String = storedValue.removePrefix("enc:")
}

private class FakeRobotEndpointDao : RobotEndpointDao {
    private val entities = linkedMapOf<Long, RobotEndpointEntity>()
    private var nextId = 1L

    override suspend fun insert(robot: RobotEndpointEntity): Long {
        if (entities.values.any { it.name == robot.name }) {
            throw IllegalStateException("duplicate name")
        }
        val assignedId = if (robot.id == 0L) nextId++ else robot.id
        entities[assignedId] = robot.copy(id = assignedId)
        return assignedId
    }

    override suspend fun update(robot: RobotEndpointEntity) {
        if (entities.values.any { it.id != robot.id && it.name == robot.name }) {
            throw IllegalStateException("duplicate name")
        }
        entities[robot.id] = robot
    }

    override suspend fun deleteById(id: Long) {
        entities.remove(id)
    }

    override suspend fun findById(id: Long): RobotEndpointEntity? = entities[id]

    override suspend fun getAll(): List<RobotEndpointEntity> = entities.values.toList()
}

private class ThrowingWebhookCipher : WebhookCipher {
    override fun isEncrypted(value: String): Boolean = false

    override fun encrypt(plainText: String): String {
        throw IllegalStateException("cipher unavailable")
    }

    override fun decrypt(storedValue: String): String = storedValue
}
