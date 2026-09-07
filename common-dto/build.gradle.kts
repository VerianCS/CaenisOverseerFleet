plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    compileOnly("jakarta.validation:jakarta.validation-api:3.1.1")
}
