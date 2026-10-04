import com.android.build.api.variant.ApplicationAndroidComponentsExtension

plugins {
    id("com.android.application")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "org.mavuno.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.mavuno.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        // 10.0.2.2 is the host machine from the Android emulator.
        buildConfigField("String", "BACKEND_URL", "\"${providers.gradleProperty("mavuno.backendUrl").getOrElse("http://10.0.2.2:8000")}\"")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Side-loaded hackathon build; replace with a real key before any distribution.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources { noCompress += listOf("tflite", "opus") }
}

/** Copies the repo's content/ folder (minus test fixtures) into the APK under assets/content/. */
val copyContent = tasks.register<Sync>("copyContentAssets") {
    from(rootProject.extra["contentDir"] as String) { exclude("fixtures/**") }
    into(layout.buildDirectory.dir("generated/contentAssets/content"))
}

extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
    variant.sources.assets?.addStaticSourceDirectory(layout.buildDirectory.dir("generated/contentAssets").get().asFile.path)
}
tasks.named("preBuild") { dependsOn(copyContent) }

dependencies {
    implementation(project(":contentpack"))

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("net.zetetic:sqlcipher-android:4.19.1@aar")
    implementation("androidx.sqlite:sqlite:2.7.1")

    implementation("androidx.work:work-runtime-ktx:2.12.0")

    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")

    implementation("com.google.ai.edge.litert:litert:1.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}
