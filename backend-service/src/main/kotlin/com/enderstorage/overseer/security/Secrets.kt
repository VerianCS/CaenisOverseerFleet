package com.enderstorage.overseer.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class Secrets(@Value("\${caenis.encryption-key}") key: String) {
    private val secretKey = SecretKeySpec(Base64.getDecoder().decode(key).also { require(it.size == 32) { "Encryption key must be 32 base64-encoded bytes" } }, "AES")
    fun encrypt(value: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }
    fun decrypt(value: String): String {
        val bytes = Base64.getDecoder().decode(value)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    fun randomToken() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    fun hash(value: String) = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
    fun alias(id: String): String {
        val derivation=javax.crypto.Mac.getInstance("HmacSHA256")
        derivation.init(SecretKeySpec(secretKey.encoded,"HmacSHA256"))
        val aliasKey=derivation.doFinal("caenis-player-pseudonyms-v1".toByteArray(Charsets.UTF_8))
        val mac=javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(aliasKey,"HmacSHA256"))
        return "player-" + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(id.toByteArray(Charsets.UTF_8))).take(12)
    }
}
