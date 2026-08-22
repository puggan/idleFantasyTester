package com.puggan.idlefantasytester

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import java.io.File

/**
 * A hand-written plan: where the character starts, and the steps to walk it
 * through. See plans/ for examples.
 */
@Serializable
data class Plan(
    val name: String,
    /** Starting level per skill, e.g. `agility: 10`. Skills left out start at 1. */
    val startLevels: Map<String, Int> = emptyMap(),
    /** Starting XP per skill; overrides [startLevels] for the skills it names. */
    val startXp: Map<String, Long> = emptyMap(),
    /** Fixed seed keeps two plans comparable — same luck, different choices. */
    val seed: Int = 42,
    /** Pet, capes, blessing and prestige nodes carried through every step. */
    val loadout: Loadout = Loadout.EMPTY,
    val steps: List<Step>,
) {
    /**
     * One stretch of grinding: a skill, an activity, and a stop condition.
     *
     * The knobs below are deliberately generic — [target] is a course for agility,
     * an ore for mining, a tree for woodcutting; [toolEfficiency] is the grappling
     * hook, the pickaxe, the axe. Each [SkillRunner] reads what it needs.
     */
    @Serializable
    data class Step(
        val skill: String,
        /** Activity key, or "best" to re-pick the highest unlocked each session. */
        val target: String? = null,
        /** Alias for [target], easier to read in agility plans. */
        val course: String? = null,
        /** Run until this skill reaches this level. Combine with [sessions] for a cap. */
        val untilLevel: Int? = null,
        /** Run exactly this many sessions (or fewer, if [untilLevel] hits first). */
        val sessions: Int? = null,
        /** Tool multiplier: 1.0 = base, 1.5 = +50% actions/min. */
        val toolEfficiency: Float = 1.0f,
        /** Overrides the loadout's pet boost for this step; null = use the loadout. */
        val petBoostPct: Int? = null,
        /** Overrides the loadout's Endurance floor reduction; null = use the loadout. */
        val floorReductionMin: Double? = null,
        /** Chronos Spire session-duration multiplier, 0.5–1.0. */
        val chronosMultiplier: Float = 1.0f,
        /**
         * Replaces the loadout's prestige nodes from this step on, modelling a respec.
         * The game wipes a skill's nodes wholesale and refunds the points (24h cooldown
         * per skill), so this replaces rather than adds to what the loadout owns.
         */
        val prestige: Map<String, List<String>>? = null,
        /** Free-text note, echoed in the report instead of the generated label. */
        val note: String = "",
    ) {
        /** [target] and [course] are the same field wearing two names. */
        val activity: String get() = target ?: course ?: BEST

        /** Human-readable label for the report's step column. */
        val label: String
            get() = note.ifEmpty {
                val stop = untilLevel?.let { "→ lvl $it" } ?: sessions?.let { "×$it" } ?: "?"
                "$skill $activity $stop"
            }
    }

    companion object {
        /** Activity keyword meaning "whatever the current level unlocks". */
        const val BEST = "best"

        private val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))

        fun load(file: File): Plan {
            require(file.isFile) { "No such plan file: ${file.path}" }
            val plan = yaml.decodeFromString(serializer(), file.readText())
            plan.validate(file.name)
            return plan
        }
    }

    /** Fails fast on plans that would run forever or silently do nothing. */
    private fun validate(fileName: String) {
        require(steps.isNotEmpty()) { "$fileName: plan has no steps" }
        checkPrestigeNodes(loadout.prestige, "$fileName: loadout")
        steps.forEachIndexed { index, step ->
            step.prestige?.let { checkPrestigeNodes(it, "$fileName: step ${index + 1}") }
            val where = "$fileName: step ${index + 1} (${step.skill})"
            require(step.target == null || step.course == null) {
                "$where: set either target or course, not both"
            }
            require(step.untilLevel != null || step.sessions != null) {
                "$where: needs untilLevel, sessions, or both — otherwise it never ends"
            }
            val runner = SkillRunner.forSkill(step.skill)
                ?: error(
                    "$where: skill '${step.skill}' is not implemented yet. " +
                        "Available: ${SkillRunner.available().joinToString(", ")}"
                )
            runner.validate(step, where)
        }
    }

    /**
     * A misspelt node id is simply not owned, which would quietly cost the plan its
     * bonus and make a comparison meaningless — so reject it up front.
     */
    private fun checkPrestigeNodes(nodes: Map<String, List<String>>, where: String) {
        nodes.forEach { (skill, ids) ->
            val tree = GameData.prestigeTrees[skill]
                ?: error("$where: no prestige tree for skill '$skill'")
            val known = tree.paths.flatMap { path -> path.nodes.map { it.id } }
            ids.forEach { id ->
                require(id in known) {
                    "$where: unknown prestige node '$id'. " +
                        "$skill nodes: ${known.joinToString(", ")}"
                }
            }
        }
    }
}
