package com.enderstorage.overseer

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

class PostgisContainer : PostgreSQLContainer<PostgisContainer>(
    DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))

/** Same image, init script and owner/runtime role split as docker-compose.yml; one container per test JVM. */
abstract class PostgisIntegrationTest {
    companion object {
        const val ADMIN_PASSWORD = "integration-admin-password"
        private val db = PostgisContainer()
            .withDatabaseName("caenis_overseer").withUsername("caenis_owner").withPassword("owner-test")
            .withEnv("CAENIS_DATABASE_PASSWORD", "app-test")
            .withCopyFileToContainer(MountableFile.forHostPath("../docker/postgres/01-runtime-user.sh"),
                "/docker-entrypoint-initdb.d/01-runtime-user.sh")
            .also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", db::getJdbcUrl)
            registry.add("spring.datasource.username") { "caenis" }
            registry.add("spring.datasource.password") { "app-test" }
            registry.add("spring.flyway.user") { "caenis_owner" }
            registry.add("spring.flyway.password") { "owner-test" }
            registry.add("caenis.bootstrap-password") { ADMIN_PASSWORD }
        }
    }
}
