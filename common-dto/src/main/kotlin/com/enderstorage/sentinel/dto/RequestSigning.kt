package com.enderstorage.sentinel.dto

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object RequestSigning {
    fun sign(secret: String, instance: String, purpose: String, timestamp: String, nonce: String, body: ByteArray): String {
        val hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(body))
        val canonical = listOf(instance, purpose, timestamp, nonce, hash).joinToString("\n")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(UTF_8), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.toByteArray(UTF_8)))
    }
    fun equal(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(UTF_8), b.toByteArray(UTF_8))
}
