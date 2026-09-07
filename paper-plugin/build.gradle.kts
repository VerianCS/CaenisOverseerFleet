plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

repositories {
    mavenCentral() // Required for kotlin-stdlib and kotlinx-serialization
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation(project(":common-dto"))
}

tasks.shadowJar {
    archiveFileName.set("caenis-overseer.jar")
    archiveClassifier.set("")
    relocate("kotlin", "com.caenis.libs.kotlin")
    relocate("kotlinx.serialization", "com.caenis.libs.serialization")
}

// 1. Disable the plain, unshaded jar
tasks.jar {
    enabled = false
}

// 2. Make ./gradlew build run shadowJar
tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}