package com.example.mysmscode.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreWebhookCipher(
    @Suppress("UNUSED_PARAMETER") context: Context,
) : WebhookCipher {
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    override fun isEncrypted(value: String): Boolean = value.startsWith(ENCRYPTED_PREFIX)

    override fun encrypt(plainText: String): String {
        if (plainText.isBlank()) {
            return plainText
        }
        if (isEncrypted(plainText)) {
            return plainText
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val payload = cipher.iv + encrypted
        return ENCRYPTED_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    override fun decrypt(storedValue: String): String {
        if (storedValue.isBlank() || !isEncrypted(storedValue)) {
            return storedValue
        }
        val payload = Base64.decode(storedValue.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
        require(payload.size > IV_SIZE_BYTES) { "Webhook 密文格式无效。" }
        val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
        val cipherText = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        return String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "mysmscode_webhook_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ENCRYPTED_PREFIX = "enc:"
        private const val IV_SIZE_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
