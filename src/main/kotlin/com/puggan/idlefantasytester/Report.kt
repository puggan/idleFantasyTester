package com.puggan.idlefantasytester

import com.fantasyidler.simulator.XpTable

/** Renders [PlanRunner.Result]s as plain text: time spent, levels reached, xp rate. */
object Report {
    fun printPlan(result: PlanRunner.Result) {
        val plan = result.plan
        println()
        println("── ${plan.name} ${"─".repeat(max(0, 58 - plan.name.length))}")
        println("   seed ${plan.seed}")
        result.bonuses.forEach { (skill, bonuses) ->
            println("   $skill: ${bonuses.describe().joinToString(", ")}")
        }
        println()
        println(ROW.format("step", "sessions", "xp gained", "xp/h", "levels", "time"))
        println("   " + "─".repeat(RULE))

        result.steps.forEach { step ->
            println(
                ROW.format(
                    step.step.label.take(LABEL),
                    step.sessionsRun.toString(),
                    formatXp(step.xpGained),
                    formatXp(xpPerHour(step.xpGained, step.elapsedMs)),
                    "${step.levelBefore} → ${step.levelAfter}",
                    formatDuration(step.elapsedMs),
                ),
            )
            // Surface surprises only: an unremarkable "ran all N sessions" is noise.
            if (step.stoppedBecause.startsWith("no XP") || step.stoppedBecause.startsWith("hit the")) {
                println("       ! ${step.stoppedBecause}")
            }
            // A respec changes the bonuses mid-plan; the header alone would mislead.
            if (step.step.prestige != null) {
                println("       respec: ${step.bonuses.describe().joinToString(", ")}")
            }
            // A "best" step always breaks down, even when it resolved to a single
            // activity: which course it settled on is the thing being asked about.
            // A fixed-activity step with one entry would just repeat its own row.
            if (step.activities.size > 1 || (step.step.activity == Plan.BEST && step.activities.isNotEmpty())) {
                step.activities.forEach { use ->
                    println(
                        SUB_ROW.format(
                            use.activity + actionRate(use),
                            use.sessions.toString(),
                            formatXp(use.xp),
                            formatXp(xpPerHour(use.xp, use.elapsedMs)),
                            "${use.levelFrom} → ${use.levelTo}",
                            formatDuration(use.elapsedMs),
                        ),
                    )
                }
            }
        }

        println("   " + "─".repeat(RULE))
        println(
            ROW.format(
                "total",
                result.totalSessions.toString(),
                formatXp(result.totalXp),
                formatXp(xpPerHour(result.totalXp, result.totalMs)),
                "",
                formatDuration(result.totalMs),
            ),
        )
        println()
        println("   levels reached:")
        result.finalLevels.toSortedMap().forEach { (skill, level) ->
            val from = result.startLevels[skill] ?: 1
            val xp = result.finalXp[skill] ?: 0L
            val next = if (level >= 99) "maxed" else "${formatXp(XpTable.xpToNextLevel(xp))} xp to ${level + 1}"
            println("     %-12s %2d → %2d   %10s xp   (%s)".format(skill, from, level, formatXp(xp), next))
        }
        println()
        println("   time spent: ${formatDuration(result.totalMs)} over ${result.totalSessions} sessions")
    }

    /** Side-by-side totals; only worth printing when more than one plan ran. */
    fun printComparison(results: List<PlanRunner.Result>) {
        println()
        println("── comparison ─────────────────────────────────────────────")
        val width = results.maxOf { it.plan.name.length }.coerceIn(20, 60)
        val row = "   %-${width}s %9s %11s %9s %12s"
        println(row.format("plan", "sessions", "xp", "xp/h", "time"))
        println("   " + "─".repeat(width + 45))
        results.sortedBy { it.totalMs }.forEach { r ->
            println(
                row.format(
                    r.plan.name.take(width),
                    r.totalSessions.toString(),
                    formatXp(r.totalXp),
                    formatXp(xpPerHour(r.totalXp, r.totalMs)),
                    formatDuration(r.totalMs),
                ),
            )
        }
        println()
    }

    /**
     * Label widths differ by the 4 characters the sub-row's indent and arrow add,
     * so both tables' numeric columns land on the same screen positions.
     */
    private const val LABEL = 32
    private const val ROW = "   %-${LABEL}s %8s %11s %9s %10s %12s"
    private const val SUB_ROW = "     ↳ %-${LABEL - 4}s %8s %11s %9s %10s %12s"

    private const val RULE = 86

    /**
     * Actions/min suffix for an activity row, e.g. " 5/min" or " 5-6/min" when a
     * tool upgrade landed partway. Blank when the skill has no tool.
     */
    private fun actionRate(use: PlanRunner.ActivityUse): String =
        when {
            use.actionsMax <= 0 -> ""
            use.actionsMin == use.actionsMax -> " ${use.actionsMax}/min"
            else -> " ${use.actionsMin}-${use.actionsMax}/min"
        }

    /** XP per in-game hour; 0 for a step that ran no sessions. */
    private fun xpPerHour(
        xp: Long,
        elapsedMs: Long,
    ): Long = if (elapsedMs <= 0L) 0L else (xp * 3_600_000.0 / elapsedMs).toLong()

    /** In-game wall clock. Minutes stay visible at every scale: plans separated by
     *  under an hour are exactly the comparisons this tool exists to make. */
    fun formatDuration(ms: Long): String {
        val minutes = ms / 60_000
        val days = minutes / 1440
        val hours = (minutes % 1440) / 60
        val mins = minutes % 60
        return when {
            days > 0 -> "${days}d ${hours}h ${mins}m"
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }
    }

    private fun formatXp(xp: Long): String =
        when {
            xp >= 1_000_000 -> "%.1fM".format(xp / 1_000_000.0)
            xp >= 10_000 -> "%.0fk".format(xp / 1_000.0)
            else -> xp.toString()
        }

    private fun max(
        a: Int,
        b: Int,
    ) = if (a > b) a else b
}
