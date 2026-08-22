package com.puggan.idlefantasytester

import com.fantasyidler.simulator.SkillSimulator
import kotlin.math.roundToInt
import kotlin.random.Random

/** Agility: laps on an obstacle course, XP only on successful laps. */
object AgilityRunner : SkillRunner {
    override val skill = "agility"

    override fun validate(
        step: Plan.Step,
        where: String,
    ) {
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
        val (courseKey, course) =
            if (step.activity == Plan.BEST) {
                GameData.bestAgilityCourseFor(level).toPair()
            } else {
                step.activity to GameData.agilityCourses.getValue(step.activity)
            }

        // Which hook wins depends on the course, not just the level, so it is
        // re-picked per session alongside the course itself.
        val tool = bonuses.bestTool(level, course.levelRequired)

        val result =
            SkillSimulator.simulateAgility(
                courseData = course,
                startXp = state.xpOf(skill),
                agilityLevel = level,
                // Step values are per-step overrides; the loadout is the plan-wide default.
                floorReductionMin = step.floorReductionMin ?: bonuses.floorReductionMin,
                petBoostPct = step.petBoostPct ?: bonuses.petBoostPct,
                toolEfficiency = step.toolEfficiency ?: tool?.second ?: 1.0f,
                chronosMultiplier = step.chronosMultiplier,
                random = random,
            )

        val efficiency = step.toolEfficiency ?: tool?.second ?: 1.0f
        return SkillRunner.Session(
            activity = courseKey,
            xpBySkill = mapOf(skill to result.frames.sumOf { it.xpGain.toLong() }),
            durationMs = result.durationMs,
            frames = result.frames,
            actionsPerMinute = lapsPerMinute(efficiency),
        )
    }

    /**
     * Mirrors the lap count SkillSimulator derives internally; its LAPS_PER_MINUTE
     * is private, so the base is restated here to report what a tool actually bought.
     *
     * The rounding is the point: efficiency buys laps in whole steps, so 1.25x and
     * 1.5x both land on 3 laps and the tiers between them are worth nothing.
     */
    private fun lapsPerMinute(efficiency: Float): Int =
        (BASE_LAPS_PER_MINUTE * efficiency).roundToInt().coerceAtLeast(1)

    private const val BASE_LAPS_PER_MINUTE = 2
}
