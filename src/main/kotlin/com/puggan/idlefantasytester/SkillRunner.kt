package com.puggan.idlefantasytester

import com.fantasyidler.data.model.SessionFrame
import kotlin.random.Random

/**
 * Adapter over one of the game's simulators.
 *
 * [PlanRunner] drives sessions without knowing which skill it is running; each
 * implementation translates a generic [Plan.Step] into the arguments its
 * `SkillSimulator.simulateX` call wants.
 */
interface SkillRunner {

    /** Skill key as written in plan files. */
    val skill: String

    /** Rejects steps this skill can't honour. [where] locates the step for the user. */
    fun validate(step: Plan.Step, where: String)

    /**
     * Simulates one 60-frame session, reading and advancing nothing itself.
     *
     * [bonuses] supplies only the sim-time knobs (pet, endurance floor); award-time
     * multipliers are applied by [PlanRunner], as the game applies them on collect.
     */
    fun runSession(step: Plan.Step, state: PlayerState, bonuses: Bonuses, random: Random): Session

    /**
     * One simulated session's outcome. XP is a map because combat sessions
     * spread XP over several skills.
     */
    data class Session(
        val activity: String,
        val xpBySkill: Map<String, Long>,
        val durationMs: Long,
        val frames: List<SessionFrame>,
        val items: Map<String, Int> = emptyMap(),
        /** Actions per minute the tool bought, 0 when the skill has no tool. */
        val actionsPerMinute: Int = 0,
    ) {
        val totalXp: Long get() = xpBySkill.values.sum()
    }

    companion object {
        private val runners: Map<String, SkillRunner> = listOf(
            AgilityRunner,
        ).associateBy { it.skill }

        fun forSkill(skill: String): SkillRunner? = runners[skill]

        fun available(): List<String> = runners.keys.sorted()
    }
}
