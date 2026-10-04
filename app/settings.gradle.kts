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

// :fusion and :contentpack are pure Kotlin; :mobile is the Android app.
include(":fusion", ":contentpack", ":mobile")
