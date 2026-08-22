package com.puggan.idlefantasytester

import com.fantasyidler.simulator.SkillSimulator
import kotlin.random.Random

/** Agility: laps on an obstacle course, XP only on successful laps. */
object AgilityRunner : SkillRunner {

    override val skill = "agility"

    override fun validate(step: Plan.Step, where: String) {
        require(step.activity == Plan.BEST || step.activity in GameData.agilityCourses) {
            "$where: unknown course '${step.activity}'. Known: " +
                "${Plan.BEST}, ${GameData.agilityCourses.keys.joinToString(", ")}"
        }
    }

    override fun runSession(
        step: Plan.Step,
        state: PlayerState,
        bonuses: Bonuses,
        random: Random,
    ): SkillRunner.Session {
        val level = state.levelOf(skill)
        // "best" resolves per session: levelling mid-step unlocks better courses.
        val (courseKey, course) = if (step.activity == Plan.BEST) {
            GameData.bestAgilityCourseFor(level).toPair()
        } else {
            step.activity to GameData.agilityCourses.getValue(step.activity)
        }

        val result = SkillSimulator.simulateAgility(
            courseData        = course,
            startXp           = state.xpOf(skill),
            agilityLevel      = level,
            // Step values are per-step overrides; the loadout is the plan-wide default.
            floorReductionMin = step.floorReductionMin ?: bonuses.floorReductionMin,
            petBoostPct       = step.petBoostPct ?: bonuses.petBoostPct,
            toolEfficiency    = step.toolEfficiency,
            chronosMultiplier = step.chronosMultiplier,
            random            = random,
        )

        return SkillRunner.Session(
            activity   = courseKey,
            xpBySkill  = mapOf(skill to result.frames.sumOf { it.xpGain.toLong() }),
            durationMs = result.durationMs,
            frames     = result.frames,
        )
    }
}
