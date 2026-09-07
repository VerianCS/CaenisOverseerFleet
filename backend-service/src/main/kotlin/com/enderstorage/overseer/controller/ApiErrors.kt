package com.enderstorage.overseer.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun denied() = ResponseEntity.status(403).body(mapOf("message" to "Your role cannot perform this action"))
    @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException::class, org.springframework.http.converter.HttpMessageNotReadableException::class)
    fun malformed() = ResponseEntity.badRequest().body(mapOf("message" to "Malformed request body"))
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(error: IllegalArgumentException) = ResponseEntity.badRequest().body(mapOf("message" to (error.message ?: "Invalid input")))
    @ExceptionHandler(ResponseStatusException::class)
    fun status(error: ResponseStatusException) = ResponseEntity.status(error.statusCode).body(mapOf("message" to (error.reason ?: "Request rejected")))
    @ExceptionHandler(Exception::class)
    fun unexpected(error: Exception): ResponseEntity<Map<String, String>> {
        LoggerFactory.getLogger(javaClass).error("Request failed: {}", error.javaClass.simpleName)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("message" to "The operation could not be completed. Consult the operator logs."))
    }
}
