plugins {
    kotlin("jvm") version "2.2.21" apply false
    kotlin("plugin.spring") version "2.2.21" apply false
    kotlin("plugin.jpa") version "2.2.21" apply false
    kotlin("plugin.serialization") version "2.2.21" apply false
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.gradleup.shadow") version "8.3.8" apply false
}
allprojects {
    group = "com.enderstorage"
    version = "1.0.0"
    repositories { mavenCentral() }
}
subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
            compilerOptions { freeCompilerArgs.add("-Xjsr305=strict") }
        }
    }
}
