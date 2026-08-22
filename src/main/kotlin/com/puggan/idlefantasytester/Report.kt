package com.puggan.idlefantasytester

import com.fantasyidler.simulator.XpTable

/** Renders [PlanRunner.Result]s as plain text: time spent, levels reached. */
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
        println(ROW.format("step", "sessions", "xp gained", "levels", "time"))
        println("   " + "─".repeat(69))

        result.steps.forEach { step ->
            println(
                ROW.format(
                    step.step.label.take(28),
                    step.sessionsRun.toString(),
                    formatXp(step.xpGained),
                    "${step.levelBefore} → ${step.levelAfter}",
                    formatDuration(step.elapsedMs),
                )
            )
            // Surface surprises only: an unremarkable "ran all N sessions" is noise.
            if (step.stoppedBecause.startsWith("no XP") || step.stoppedBecause.startsWith("hit the")) {
                println("       ! ${step.stoppedBecause}")
            }
            // A respec changes the bonuses mid-plan; the header alone would mislead.
            if (step.step.prestige != null) {
                println("       respec: ${step.bonuses.describe().joinToString(", ")}")
            }
            // A "best" step walks up several activities; break down where its time went.
            if (step.activities.size > 1) {
                step.activities.forEach { use ->
                    println(
                        SUB_ROW.format(
                            use.activity,
                            use.sessions.toString(),
                            formatXp(use.xp),
                            "${use.levelFrom} → ${use.levelTo}",
                            formatDuration(use.elapsedMs),
                        )
                    )
                }
            }
        }

        println("   " + "─".repeat(69))
        println(
            ROW.format(
                "total",
                result.totalSessions.toString(),
                formatXp(result.totalXp),
                "",
                formatDuration(result.totalMs),
            )
        )
        println()
        println("   levels reached:")
        result.finalLevels.toSortedMap().forEach { (skill, level) ->
            val from = result.startLevels[skill] ?: 1
            val xp = result.finalXp[skill] ?: 0L
            val toNext = XpTable.xpToNextLevel(xp)
            val next = if (level >= 99) "maxed" else "${formatXp(toNext)} xp to ${level + 1}"
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
        println("   %-${width}s %10s %12s %10s".format("plan", "sessions", "xp", "time"))
        println("   " + "─".repeat(width + 35))
        results.sortedBy { it.totalMs }.forEach { r ->
            println(
                "   %-${width}s %10d %12s %10s".format(
                    r.plan.name.take(width),
                    r.totalSessions,
                    formatXp(r.totalXp),
                    formatDuration(r.totalMs),
                )
            )
        }
        println()
    }

    private const val ROW = "   %-28s %8s %12s %10s %10s"

    /** Indented one notch, so activity rows read as children of their step. */
    private const val SUB_ROW = "     ↳ %-24s %8s %12s %10s %10s"

    /** In-game wall clock; plans routinely span weeks, so days are the top unit. */
    fun formatDuration(ms: Long): String {
        val minutes = ms / 60_000
        val days = minutes / 1440
        val hours = (minutes % 1440) / 60
        val mins = minutes % 60
        // Minutes stay visible at every scale: plans separated by under an hour are
        // exactly the comparisons this tool exists to make.
        return when {
            days > 0  -> "${days}d ${hours}h ${mins}m"
            hours > 0 -> "${hours}h ${mins}m"
            else      -> "${mins}m"
        }
    }

    private fun formatXp(xp: Long): String = when {
        xp >= 1_000_000 -> "%.1fM".format(xp / 1_000_000.0)
        xp >= 10_000    -> "%.0fk".format(xp / 1_000.0)
        else            -> xp.toString()
    }

    private fun max(a: Int, b: Int) = if (a > b) a else b
}
