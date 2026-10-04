plugins {
    id("com.android.application") version "9.4.1" apply false
    kotlin("jvm") version "2.2.20" apply false
    kotlin("plugin.serialization") version "2.2.20" apply false
    kotlin("plugin.compose") version "2.2.20" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}

/** Repo-level content/ folder (strings, cards, fusion_weights.json, fixtures). */
extra["contentDir"] = rootProject.projectDir.resolve("../content").canonicalPath
