package com.enderstorage.overseer.config

import com.enderstorage.overseer.security.AuthService
import com.enderstorage.sentinel.dto.RequestSigning
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.core.Authentication
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.*
import org.springframework.web.socket.server.HandshakeInterceptor

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(private val auth: AuthService, @Value("\${caenis.public-origin}") private val origin: String) : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic","/queue")
        registry.setApplicationDestinationPrefixes("/app")
        registry.setUserDestinationPrefix("/user")
    }
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws").setAllowedOrigins(origin).addInterceptors(object: HandshakeInterceptor {
            override fun beforeHandshake(request: ServerHttpRequest, response: ServerHttpResponse, handler: WebSocketHandler, attributes: MutableMap<String,Any>): Boolean {
                val servlet = (request as? ServletServerHttpRequest)?.servletRequest ?: return false
                val xsrf = servlet.cookies?.firstOrNull { it.name == "XSRF-TOKEN" }?.value ?: return false
                attributes["xsrf"] = xsrf
                return servlet.userPrincipal != null
            }
            override fun afterHandshake(request: ServerHttpRequest, response: ServerHttpResponse, handler: WebSocketHandler, exception: Exception?) {}
        })
    }
    override fun configureWebSocketTransport(registry: WebSocketTransportRegistration) {
        registry.setMessageSizeLimit(8192).setSendBufferSizeLimit(524288).setSendTimeLimit(10000).setTimeToFirstMessage(10000)
    }
    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(object: ChannelInterceptor {
            override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
                val header = MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor::class.java) ?: error("Invalid STOMP frame")
                if (header.command == StompCommand.DISCONNECT) return message
                val authentication = header.user as? Authentication ?: error("Authentication required")
                val account = auth.authenticate(authentication.credentials as String) ?: error("Session expired")
                when(header.command) {
                    StompCommand.CONNECT -> require(RequestSigning.equal(header.getFirstNativeHeader("X-XSRF-TOKEN") ?: "",header.sessionAttributes?.get("xsrf") as? String ?: "missing"))
                    StompCommand.SUBSCRIBE -> require(header.destination == "/topic/changes" ||
                        (header.destination == "/user/queue/commands" && account.role.name != "ANALYST")) { "Subscription denied" }
                    StompCommand.SEND -> require(header.destination == "/app/command" && account.role.name != "ANALYST") { "Command denied" }
                    StompCommand.UNSUBSCRIBE, null -> {}
                    else -> error("Unsupported STOMP command")
                }
                return message
            }
        })
    }
}
