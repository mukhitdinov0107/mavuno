plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":fusion"))
    testImplementation(kotlin("test"))
    testImplementation(testFixtures(project(":fusion")))
}

tasks.test {
    useJUnitPlatform()
    systemProperty("mavuno.contentDir", rootProject.extra["contentDir"] as String)
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
