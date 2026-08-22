package com.puggan.idlefantasytester

import java.io.File
import kotlin.system.exitProcess

private val PLAN_DIR = File("plans")

/**
 * Runs plan files and reports the time they cost and the levels they reach.
 *
 * Usage: ./run-plan plans/agility-to-50.yaml [more.yaml …]
 *        ./run-plan --list
 */
fun main(args: Array<String>) {
    when {
        args.isEmpty() || args[0] in listOf("--help", "-h") -> {
            printHelp()
            return
        }
        args[0] == "--list" -> {
            printPlanList()
            return
        }
    }

    val results =
        args.map { path ->
            val file = File(path)
            try {
                PlanRunner.run(Plan.load(file))
            } catch (e: Exception) {
                // Plan files are hand-written; a stack trace helps nobody fix a typo.
                System.err.println("error in ${file.path}: ${e.message}")
                exitProcess(1)
            }
        }

    results.forEach { Report.printPlan(it) }
    if (results.size > 1) Report.printComparison(results)
}

private fun printPlanList() {
    val plans = PLAN_DIR.listFiles { f -> f.extension in listOf("yaml", "yml") }?.sorted().orEmpty()
    if (plans.isEmpty()) {
        println("No plans in ${PLAN_DIR.path}/")
        return
    }
    println("plans in ${PLAN_DIR.path}/:")
    plans.forEach { file ->
        val name = runCatching { Plan.load(file).name }.getOrElse { "(unreadable: ${it.message})" }
        println("   %-32s %s".format(file.name, name))
    }
}

private fun printHelp() {
    println(
        """
        idleFantasyTester — run a plan through the game's own simulators

          ./run-plan <plan.yaml> [plan.yaml …]   run plans, one report each
          ./run-plan --list                      list the plans in plans/
          ./run-plan --help                      this text

        Running more than one plan adds a side-by-side comparison at the end.

        Skills implemented: ${SkillRunner.available().joinToString(", ")}
        """.trimIndent(),
    )
}
