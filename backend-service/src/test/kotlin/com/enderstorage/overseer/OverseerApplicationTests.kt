package com.enderstorage.overseer

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class OverseerApplicationTests : PostgisIntegrationTest() {

    // Flyway migrates the real schema and `ddl-auto: validate` checks every entity against it.
    @Test
    fun contextLoads() {
    }

}
