package com.puggan.idlefantasytester

import kotlin.random.Random

/**
 * Walks a [Plan] through the game's simulators and records what it cost.
 *
 * Skill-agnostic by design: every step is dispatched to a [SkillRunner], so
 * adding mining means adding a runner, not touching this loop.
 */
object PlanRunner {

    data class StepResult(
        val step: Plan.Step,
        /**
         * What each activity cost, in the order first used. A step asking for
         * "best" walks up several; a fixed one has a single entry.
         */
        val activities: List<ActivityUse>,
        val sessionsRun: Int,
        val xpBySkill: Map<String, Long>,
        val levelBefore: Int,
        val levelAfter: Int,
        val elapsedMs: Long,
        /** Which stop condition ended the step. */
        val stoppedBecause: String,
        /** Bonuses in force for this step; differs from the plan's when it respecs. */
        val bonuses: Bonuses,
    ) {
        val xpGained: Long get() = xpBySkill.values.sum()
    }

    /** One activity's slice of a step: how long it was used and what it bought. */
    data class ActivityUse(
        val activity: String,
        val sessions: Int,
        val xp: Long,
        val elapsedMs: Long,
        val levelFrom: Int,
        val levelTo: Int,
    )

    data class Result(
        val plan: Plan,
        val steps: List<StepResult>,
        val startLevels: Map<String, Int>,
        val finalLevels: Map<String, Int>,
        val finalXp: Map<String, Long>,
        /** Resolved bonuses per skill, for the report header. */
        val bonuses: Map<String, Bonuses>,
    ) {
        val totalMs: Long get() = steps.sumOf { it.elapsedMs }
        val totalXp: Long get() = steps.sumOf { it.xpGained }
        val totalSessions: Int get() = steps.sumOf { it.sessionsRun }
    }

    /**
     * A step with untilLevel but no session cap could otherwise spin forever on an
     * unreachable target; this bounds it at roughly a decade of in-game sessions.
     */
    private const val SESSION_SAFETY_CAP = 100_000

    fun run(plan: Plan): Result {
        val state = PlayerState.from(plan)
        val startLevels = state.skills.associateWith { state.levelOf(it) }
        val random = Random(plan.seed)
        // Per step, not per skill: a step may respec the tree partway through a plan.
        val bonusesBySkill = plan.steps.map { it.skill }.distinct()
            .associateWith { Bonuses(plan.loadout, it) }
        val bonusesByStep = plan.steps.associateWith { step ->
            val loadout = step.prestige?.let { plan.loadout.copy(prestige = it) } ?: plan.loadout
            Bonuses(loadout, step.skill)
        }
        // In-game wall clock across the whole plan; timed buffs are measured against it.
        var clockMs = 0L

        val results = plan.steps.map { step ->
            val runner = SkillRunner.forSkill(step.skill)
                ?: error("skill '${step.skill}' is not implemented yet")
            val bonuses = bonusesByStep.getValue(step)

            val levelBefore = state.levelOf(step.skill)
            val cap = step.sessions ?: SESSION_SAFETY_CAP
            val xpGained = mutableMapOf<String, Long>()
            val activities = linkedMapOf<String, ActivityUse>()
            var elapsedMs = 0L
            var sessionsRun = 0
            var stopped = step.sessions?.let { "ran all $it sessions" } ?: "stopped"

            while (sessionsRun < cap) {
                val level = state.levelOf(step.skill)
                if (step.untilLevel != null && level >= step.untilLevel) {
                    stopped = "reached level ${step.untilLevel}"
                    break
                }
                if (level >= MAX_LEVEL && step.untilLevel != null && step.untilLevel > MAX_LEVEL) {
                    stopped = "capped at level $MAX_LEVEL"
                    break
                }

                val session = runner.runSession(step, state, bonuses, random)
                // Cape, blessing, prestige xp_pct and the 2x boost land here rather than
                // in the sim, matching PlayerRepository.applySessionResults on collect.
                val awarded = session.xpBySkill.mapValues { (_, raw) -> bonuses.awardXp(raw, clockMs) }
                awarded.forEach { (skill, amount) ->
                    state.addXp(skill, amount)
                    xpGained[skill] = (xpGained[skill] ?: 0L) + amount
                }
                val awardedTotal = awarded.values.sum()
                val levelNow = state.levelOf(step.skill)
                activities.merge(
                    session.activity,
                    ActivityUse(session.activity, 1, awardedTotal, session.durationMs, level, levelNow),
                ) { old, new ->
                    old.copy(
                        sessions  = old.sessions + new.sessions,
                        xp        = old.xp + new.xp,
                        elapsedMs = old.elapsedMs + new.elapsedMs,
                        levelTo   = new.levelTo,
                    )
                }
                elapsedMs += session.durationMs
                clockMs += session.durationMs
                sessionsRun++

                if (awardedTotal == 0L) {
                    stopped = "no XP gained — '${session.activity}' yields nothing at this level"
                    break
                }
                if (sessionsRun == SESSION_SAFETY_CAP && step.sessions == null) {
                    stopped = "hit the $SESSION_SAFETY_CAP session safety cap"
                }
            }

            StepResult(
                step           = step,
                activities     = activities.values.toList(),
                sessionsRun    = sessionsRun,
                xpBySkill      = xpGained,
                levelBefore    = levelBefore,
                levelAfter     = state.levelOf(step.skill),
                elapsedMs      = elapsedMs,
                stoppedBecause = stopped,
                bonuses        = bonuses,
            )
        }

        return Result(
            plan        = plan,
            steps       = results,
            startLevels = startLevels,
            finalLevels = state.skills.associateWith { state.levelOf(it) },
            finalXp     = state.snapshot(),
            bonuses     = bonusesBySkill,
        )
    }

    private const val MAX_LEVEL = 99
}
