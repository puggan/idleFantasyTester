package com.puggan.idlefantasytester

import com.fantasyidler.simulator.XpTable

/**
 * The character's XP across every skill, mutated as a plan runs.
 *
 * Shared rather than per-step because skills leak into each other: agility level
 * shortens session duration for *all* skills, so a mining step's wall-clock cost
 * depends on how much agility was trained before it.
 */
class PlayerState(startXp: Map<String, Long> = emptyMap()) {
    private val xpBySkill: MutableMap<String, Long> = startXp.toMutableMap()

    /** Every skill this character has XP in, in the order it was first trained. */
    val skills: Set<String> get() = xpBySkill.keys

    fun xpOf(skill: String): Long = xpBySkill[skill] ?: 0L

    fun levelOf(skill: String): Int = XpTable.levelForXp(xpOf(skill))

    fun addXp(
        skill: String,
        amount: Long,
    ) {
        xpBySkill[skill] = xpOf(skill) + amount
    }

    /** Snapshot for reporting, so the report can't accidentally mutate the run. */
    fun snapshot(): Map<String, Long> = xpBySkill.toMap()

    companion object {
        /**
         * Builds the opening state from a plan's [Plan.startLevels] / [Plan.startXp].
         * Explicit XP wins where both name the same skill, since it is the finer grain.
         */
        fun from(plan: Plan): PlayerState {
            val xp =
                plan.startLevels.mapValues { (_, level) -> XpTable.xpForLevel(level) }
                    .toMutableMap()
            xp.putAll(plan.startXp)
            // Every skill the plan touches should exist at level 1 even if untrained,
            // so reports list it rather than silently omitting it.
            plan.steps.forEach { step -> xp.putIfAbsent(step.skill, 0L) }
            return PlayerState(xp)
        }
    }
}
