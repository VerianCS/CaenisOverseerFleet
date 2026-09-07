package com.enderstorage.overseer.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val auth: AuthService,
    @Value("\${caenis.secure-cookies}") private val secure: Boolean
) {
    @Bean
    fun security(http: HttpSecurity): SecurityFilterChain {
        // 1. Store CSRF token in a cookie readable by Next.js
        val csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
        csrfRepository.setCookieCustomizer { it.secure(secure).sameSite("Strict").path("/") }

        // 2. Crucial for Spring Security 6 + SPAs: tells Spring to accept the plain token from headers
        val csrfHandler = CsrfTokenRequestAttributeHandler()
        csrfHandler.setCsrfRequestAttributeName(null)

        http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf {
                it.csrfTokenRepository(csrfRepository)
                    .csrfTokenRequestHandler(csrfHandler)
                    .ignoringRequestMatchers("/api/v1/agent/**") // Agents use HMAC signing, not browser CSRF
            }
            .authorizeHttpRequests {
                it.requestMatchers("/api/v1/auth/login", "/api/v1/auth/csrf", "/api/v1/agent/**", "/actuator/health").permitAll()
                    .requestMatchers("/api/v1/fleet", "/api/v1/fleet/**", "/api/v1/audit/**", "/api/v1/commands/**").hasAnyRole("SUPERADMIN", "MODERATOR")
                    .requestMatchers("/api/v1/users/**", "/api/v1/settings/**", "/actuator/**").hasRole("SUPERADMIN")
                    .requestMatchers("/api/v1/**", "/ws/**").authenticated()
                    .anyRequest().denyAll()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ -> response.sendError(401) }
                it.accessDeniedHandler { _, response, _ -> response.sendError(403) }
            }
            // 3. Authenticate session FIRST, so the user identity is populated before CsrfFilter checks permissions
            .addFilterBefore(object : OncePerRequestFilter() {
                override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
                    val token = request.cookies?.firstOrNull { it.name == "caenis_session" }?.value
                    val account = token?.let { auth.authenticate(it) }

                    if (account != null) {
                        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                            account, token, listOf(SimpleGrantedAuthority("ROLE_" + account.role.name))
                        )
                    }
                    try { chain.doFilter(request, response) } finally { SecurityContextHolder.clearContext() }
                }
            }, CsrfFilter::class.java)

        return http.build()
    }
}