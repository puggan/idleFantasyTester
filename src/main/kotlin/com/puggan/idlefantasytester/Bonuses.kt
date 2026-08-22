package com.puggan.idlefantasytester

import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.simulator.PrestigeBoosts

/**
 * Resolves a [Loadout] into the multipliers the game applies, split by *when* the
 * game applies them:
 *
 *  - [petBoostPct] and [floorReductionMin] go into `simulateAgility`, shaping the
 *    frames themselves.
 *  - [capeMultiplier] and [xpMultiplier] are award-time, applied to the finished
 *    session's XP. This mirrors PlayerRepository.applySessionResults, where
 *    awarded = floor(floor(frameXp * cape) * xpMultiplier).
 *
 * Getting that split wrong would misreport every plan, since the sim's own level
 * progression depends on the XP actually banked.
 */
class Bonuses(private val loadout: Loadout, private val skill: String) {

    private val flags = PlayerFlags(
        prestigeNodes = loadout.prestige,
        characterRace = loadout.race,
    )

    private val trees = GameData.prestigeTrees

    /** Prestige xp_pct for this skill, e.g. 10.0 for XP Boost I+II. */
    val prestigeXpPct: Double =
        PrestigeBoosts.effectTotal(trees, flags, skill, PrestigeBoosts.XP_PCT)

    /** Agility Endurance nodes; lowers the level-99 session floor. */
    val floorReductionMin: Double =
        PrestigeBoosts.effectTotal(trees, flags, GameData.AGILITY, PrestigeBoosts.SESSION_FLOOR_MIN)

    /** Pet XP percent, strengthened by any pet_boost_pct nodes (BoostRepository.boostedPetPct). */
    val petBoostPct: Int = run {
        val base = GameData.petBoostPct(loadout.pets, skill)
        if (base <= 0) return@run base
        val pct = PrestigeBoosts.effectTotal(trees, flags, skill, PrestigeBoosts.PET_BOOST_PCT)
        if (pct <= 0.0) base else (base * (1.0 + pct / 100.0)).toInt().coerceAtLeast(base)
    }

    /**
     * Cape multiplier (PlayerRepository.resolveCapeMultiplier). Skill and guild cape
     * bonuses add, then Cape Mastery nodes scale the total for non-combat skills.
     */
    val capeMultiplier: Double = run {
        val bonus = GameData.capeBonusTotal(loadout.capes, skill)
        if (bonus <= 0.0) return@run 1.0
        val scaling = PrestigeBoosts.effectTotal(trees, flags, skill, PrestigeBoosts.CAPE_SCALING)
            .toInt().coerceAtLeast(1)
        1.0 + bonus * scaling
    }

    /**
     * Best owned tool for [level] on an activity requiring [activityLevelRequired],
     * or null when none is owned or usable.
     *
     * Note the agility call site in QueuedSessionStarter does *not* multiply this by
     * the prestige tool_eff_pct nodes, unlike mining/woodcutting/fishing — and the
     * agility tree has no such nodes anyway.
     */
    fun bestTool(level: Int, activityLevelRequired: Int): Pair<String, Float>? =
        if (loadout.tools.isEmpty()) null
        else GameData.bestAgilityTool(loadout.tools, level, activityLevelRequired)

    /** Blessing XP multiplier, or 1.0 with no blessing. No prayer cape modelled yet. */
    val blessingMultiplier: Double =
        loadout.blessing?.let { GameData.blessingXpMultiplier(it) } ?: 1.0

    /**
     * Award-time XP multiplier at [elapsedMs] into the run
     * (BoostRepository.xpMultiplier: 2x boost x blessing x prestige).
     *
     * Time matters because both timed buffs outlive only part of a long grind.
     */
    fun xpMultiplier(elapsedMs: Long): Double {
        val boost = if (postPrestigeBoostActive(elapsedMs)) 2.0 else 1.0
        val blessing = if (blessingActive(elapsedMs)) blessingMultiplier else 1.0
        return boost * blessing * (1.0 + prestigeXpPct / 100.0)
    }

    fun blessingActive(elapsedMs: Long): Boolean = when {
        loadout.blessing == null -> false
        loadout.blessingRenewed  -> true
        else                     -> elapsedMs < GameData.BLESSING_DURATION_MS
    }

    fun postPrestigeBoostActive(elapsedMs: Long): Boolean =
        loadout.postPrestigeXpBoost && elapsedMs < POST_PRESTIGE_BOOST_MS

    /** Applies the award-time chain exactly as the game does, truncating at each step. */
    fun awardXp(rawXp: Long, elapsedMs: Long): Long {
        val afterCape = if (capeMultiplier > 1.0) (rawXp * capeMultiplier).toLong() else rawXp
        return (afterCape * xpMultiplier(elapsedMs)).toLong()
    }

    /** One-line summary for the report header. */
    fun describe(): List<String> = buildList {
        if (petBoostPct > 0) add("pets ${loadout.pets.joinToString("+")} +$petBoostPct%")
        if (loadout.tools.isNotEmpty()) add("${loadout.tools.size} tools (best equipped per course)")
        if (capeMultiplier > 1.0) add("cape x${"%.2f".format(capeMultiplier)}")
        loadout.blessing?.let {
            val renewal = if (loadout.blessingRenewed) "renewed" else "24h only"
            add("$it x${"%.2f".format(blessingMultiplier)} ($renewal)")
        }
        if (prestigeXpPct > 0) add("prestige xp +${prestigeXpPct.toInt()}%")
        if (floorReductionMin > 0) add("endurance -${floorReductionMin}min floor")
        if (loadout.postPrestigeXpBoost) add("post-prestige 2x (48h)")
        if (isEmpty()) add("no bonuses")
    }

    private companion object {
        /** The game's post-prestige double-XP window. */
        const val POST_PRESTIGE_BOOST_MS = 48 * 3_600_000L
    }
}
