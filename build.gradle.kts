plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    id("com.diffplug.spotless") version "6.25.0"
    application
}

/**
 * Formatting is ktlint's official style, driven from .editorconfig.
 *
 * The targets below deliberately name only our own sources. The game submodule is
 * read-only: its files are compiled into this project but must never be rewritten,
 * or the next tag bump turns into a merge conflict against our own formatter.
 */
spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.0.1")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.0.1")
    }
}

kotlin {
    jvmToolchain(21)
}

/**
 * The game is a submodule pinned to a tag (see .gitmodules). Its simulators are
 * plain Kotlin — no Android types — so we compile the handful of files we need
 * straight into this JVM project instead of dragging in the Android toolchain.
 *
 * Keep [gameSources] minimal: every file added here has to stay Android-free.
 */
val gameSources = listOf(
    "com/fantasyidler/simulator/SkillSimulator.kt",
    "com/fantasyidler/simulator/XpTable.kt",
    // PrestigeBoosts carries the node math (sum over paths of the max owned tier);
    // reusing it beats reimplementing a rule the game may retune.
    "com/fantasyidler/simulator/PrestigeBoosts.kt",
    "com/fantasyidler/data/json/GatheringData.kt",
    "com/fantasyidler/data/json/PrestigeData.kt",
    "com/fantasyidler/data/model/SessionFrame.kt",
)

sourceSets.main {
    kotlin.srcDir("game/app/src/main/kotlin")
    // Include filters apply to every srcDir, so our own sources need patterns too.
    // The second is a shim standing in for a game class we can't compile (see the file).
    kotlin.setIncludes(
        gameSources + listOf(
            "com/puggan/idlefantasytester/**",
            "com/fantasyidler/data/model/PlayerFlags.kt",
        )
    )
}

dependencies {
    // JSON for the game's own assets, YAML for our hand-written plan files.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.charleskorn.kaml:kaml:0.61.0")
}

application {
    mainClass.set("com.puggan.idlefantasytester.MainKt")
}
