pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "mavuno"

// Pure-Kotlin modules. The Android module (:mobile) is added once the Android SDK is set up.
include(":fusion", ":contentpack")
