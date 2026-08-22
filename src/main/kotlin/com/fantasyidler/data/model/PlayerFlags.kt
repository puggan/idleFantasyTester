package com.fantasyidler.data.model

/**
 * Stand-in for the game's real `PlayerFlags` (game/…/data/model/PlayerModels.kt).
 *
 * We compile the game's [com.fantasyidler.simulator.PrestigeBoosts] to get its node
 * math verbatim, but the file declaring the real PlayerFlags also declares Room
 * entities (SkillSession, QuestProgress, FarmingPatch) that drag in androidx and
 * cannot build in a plain JVM project.
 *
 * This holds only the fields PrestigeBoosts actually reads. That is deliberate: if
 * a future game version has PrestigeBoosts reach for another flag, this stops
 * compiling with an unresolved reference instead of quietly simulating the wrong
 * thing. Add the field here when that happens.
 */
data class PlayerFlags(
    /** Skill → owned node ids, e.g. "agility" to listOf("agility_xp_1"). */
    val prestigeNodes: Map<String, List<String>> = emptyMap(),
    /** Skill → lifetime points earned; only used for the unspent-points math. */
    val prestigePointsEarned: Map<String, Int> = emptyMap(),
    /** Lowercase race key; blank is treated as human, as in the game. */
    val characterRace: String = "human",
)
