package com.enderstorage.overseer.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

data class LoginRequest(val username: String, val password: String)

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val auth: AuthService, @Value("\${caenis.secure-cookies}") private val secure: Boolean) {
    private data class Attempts(var count: Int, val until: Long)
    private val attempts = ConcurrentHashMap<String, Attempts>()
    @GetMapping("/csrf")
    fun csrf(token: CsrfToken) = mapOf("token" to token.token, "headerName" to token.headerName)
    @PostMapping("/login")
    fun login(@RequestBody input: LoginRequest, request: HttpServletRequest, response: HttpServletResponse): Account {
        println(">>> RECEIVED LOGIN REQUEST FOR USER: ${input.username}")
        require(input.username.length in 1..64 && input.password.toByteArray().size in 1..72)
        synchronized(attempts) {
            val now = System.currentTimeMillis()
            attempts.entries.removeIf { it.value.until < now }
            if (attempts.size > 10000) throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)
            val entry = attempts.computeIfAbsent(request.remoteAddr) { Attempts(0, now + 60000) }
            if (++entry.count > 10) throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Wait one minute.")
        }
        val session = auth.login(input.username, input.password) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password")
        response.addHeader("Set-Cookie", cookie(session.token, Duration.ofHours(8)))
        return session.account
    }
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal account: Account) = account
    @PostMapping("/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse) {
        request.cookies?.firstOrNull { it.name == "caenis_session" }?.value?.let(auth::logout)
        response.addHeader("Set-Cookie", cookie("", Duration.ZERO))
    }
    private fun cookie(value: String, age: Duration) = ResponseCookie.from("caenis_session", value)
        .httpOnly(true).secure(secure).sameSite("Strict").path("/").maxAge(age).build().toString()
}
