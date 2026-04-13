plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Client
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-websockets:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-client-logging:2.3.7")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Kotlin Scripting
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.22")

    // YAML parsing
    implementation("org.yaml:snakeyaml:2.2")

    // Test
    testImplementation(kotlin("test"))

    // Koin DI
    implementation("io.insert-koin:koin-core:3.5.3")

    // OneBot Adapter
    implementation(project(":onebot-adapter"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
