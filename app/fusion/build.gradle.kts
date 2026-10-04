plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-test-fixtures`
}

kotlin { jvmToolchain(17) }

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test"))
    testFixturesApi("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("mavuno.contentDir", rootProject.extra["contentDir"] as String)
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
