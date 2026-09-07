package com.enderstorage.overseer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class OverseerApplication

fun main(args: Array<String>) { runApplication<OverseerApplication>(*args) }
