package com.enderstorage.overseer.config

import com.enderstorage.sentinel.dto.serializers.UUIDSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.UUID

@Configuration
class SerializationConfig {

    @Bean
    fun json(): Json = Json {
        // Prevents 400 Bad Request if NiFi adds extra metadata fields
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        serializersModule = SerializersModule {
            contextual(UUID::class, UUIDSerializer)
        }
    }
}
