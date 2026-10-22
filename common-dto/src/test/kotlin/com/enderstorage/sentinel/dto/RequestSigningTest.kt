package com.enderstorage.sentinel.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RequestSigningTest {
    private val secret = "agent-secret-0123456789abcdef0123456789"
    private val nonce = "00000000-0000-0000-0000-000000000001"
    private val body = """{"x":1}""".toByteArray()

    @Test
    fun `matches an independently computed HMAC-SHA256 vector`() {
        // Computed with Python's hmac/hashlib over the same canonical string.
        assertEquals("OQ5KLjZbHoveSZ8FneShEN2T-XNL_QgZLfUbN2UmRyA",
            RequestSigning.sign(secret, "srv-test-01", "mining", "1700000000000", nonce, body))
    }

    @Test
    fun `every signed field changes the signature`() {
        val base = RequestSigning.sign(secret, "srv-test-01", "mining", "1700000000000", nonce, body)
        assertNotEquals(base, RequestSigning.sign(secret + "x", "srv-test-01", "mining", "1700000000000", nonce, body))
        assertNotEquals(base, RequestSigning.sign(secret, "srv-test-02", "mining", "1700000000000", nonce, body))
        assertNotEquals(base, RequestSigning.sign(secret, "srv-test-01", "heartbeat", "1700000000000", nonce, body))
        assertNotEquals(base, RequestSigning.sign(secret, "srv-test-01", "mining", "1700000000001", nonce, body))
        assertNotEquals(base, RequestSigning.sign(secret, "srv-test-01", "mining", "1700000000000", nonce.replace('1', '2'), body))
        assertNotEquals(base, RequestSigning.sign(secret, "srv-test-01", "mining", "1700000000000", nonce, """{"x":2}""".toByteArray()))
    }

    @Test
    fun `equal is exact`() {
        assertTrue(RequestSigning.equal("abc", "abc"))
        assertFalse(RequestSigning.equal("abc", "abd"))
        assertFalse(RequestSigning.equal("abc", "ab"))
    }
}
