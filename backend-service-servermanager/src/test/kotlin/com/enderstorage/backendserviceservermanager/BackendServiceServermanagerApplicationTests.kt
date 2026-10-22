package com.enderstorage.backendserviceservermanager

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["caenis.manager.key=test-manager-key-0123456789abcdefghij"])
class BackendServiceServermanagerApplicationTests {

    @Test
    fun contextLoads() {
    }

}
