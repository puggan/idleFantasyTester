package com.puggan.idlefantasytester

import kotlinx.serialization.Serializable

/**
 * What the character carries into a plan: pet, capes, blessing, prestige nodes.
 *
 * Everything here is looked up in the game's own data at run time, so the numbers
 * come from the pinned tag rather than from anything hardcoded in this repo.
 */
@Serializable
data class Loadout(
    /** Pet ids, e.g. ["graceling_sprite"] (+10% agility XP). Boosts from several pets add up. */
    val pets: List<String> = emptyList(),
    /** Cape item keys held or worn, e.g. ["agility_cape"]. Skill and guild capes stack. */
    val capes: List<String> = emptyList(),
    /** Church blessing key, e.g. "divine_grace" (x1.37 XP). */
    val blessing: String? = null,
    /**
     * Whether the blessing is re-cast as it expires. Blessings run 24h base while
     * a 1-99 grind runs days, so this is the difference between "buffed once" and
     * "buffed throughout"; the report states which was assumed.
     */
    val blessingRenewed: Boolean = true,
    /**
     * The 48h double-XP boost the game grants on prestiging. Off by default —
     * turn it on to model the run starting the moment you prestige.
     */
    val postPrestigeXpBoost: Boolean = false,
    /** Skill → owned prestige node ids, e.g. agility: [agility_xp_1, agility_xp_2]. */
    val prestige: Map<String, List<String>> = emptyMap(),
    /** Race, for race-locked prestige nodes. */
    val race: String = "human",
) {
    companion object {
        val EMPTY = Loadout()
    }
}
