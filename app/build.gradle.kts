plugins {
    kotlin("jvm") version "2.2.20" apply false
    kotlin("plugin.serialization") version "2.2.20" apply false
}

/** Repo-level content/ folder (strings, cards, fusion_weights.json, fixtures). */
extra["contentDir"] = rootProject.projectDir.resolve("../content").canonicalPath
