package com.example.mysmscode.data

interface WebhookCipher {
    fun isEncrypted(value: String): Boolean
    fun encrypt(plainText: String): String
    fun decrypt(storedValue: String): String
}

class PassthroughWebhookCipher : WebhookCipher {
    override fun isEncrypted(value: String): Boolean = false

    override fun encrypt(plainText: String): String = plainText

    override fun decrypt(storedValue: String): String = storedValue
}
