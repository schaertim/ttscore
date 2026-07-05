plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ktlint)
}

group = "com.ttscore"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.postgresql)
    implementation(libs.h2)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation("org.flywaydb:flyway-core:10.10.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.10.0")
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.jsoup)
    implementation(libs.exposed.kotlin.datetime)
    implementation("io.ktor:ktor-server-cors")
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation("nl.martijndwars:web-push:5.1.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("com.stripe:stripe-java:32.2.0")
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}

// The Ktor fat jar (buildFatJar → shadowJar) must merge META-INF/services files.
// Otherwise Flyway's ServiceLoader plugin discovery (ResourceTypeProvider) is wiped out
// when duplicate service files from flyway-core + flyway-database-postgresql overwrite each
// other, and every migration is rejected as "Unrecognised migration name format".
// See flyway/flyway#3757 and KTOR-8987. duplicatesStrategy is required with Shadow 9.x.
tasks {
    shadowJar {
        mergeServiceFiles()
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}
