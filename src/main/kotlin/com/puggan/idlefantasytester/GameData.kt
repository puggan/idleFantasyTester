package com.puggan.idlefantasytester

import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.json.PrestigeSkillTreeData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loads the game's data straight out of the pinned submodule, so the numbers we
 * simulate against are the ones the tagged build ships.
 */
object GameData {
    const val AGILITY = "agility"

    /** Blessings run 24h before Church/Monument bonuses (TownRepository.blessingDurationMs). */
    const val BLESSING_DURATION_MS = 24 * 3_600_000L

    /** Overridable for the odd run against a working copy elsewhere. */
    private val gameRoot: File =
        File(System.getenv("IDLE_FANTASY_GAME") ?: "game/app/src/main")

    private val assetRoot = File(gameRoot, "assets/data")

    private val json = Json { ignoreUnknownKeys = true }

    private fun asset(name: String): String {
        val file = File(assetRoot, name)
        require(file.isFile) {
            "Missing ${file.path} — is the game submodule checked out? " +
                "Try: git submodule update --init"
        }
        return file.readText()
    }

    // ------------------------------------------------------------------
    // Assets
    // ------------------------------------------------------------------

    /** agility_courses.json, keyed by course key ("beginner_course", …). */
    val agilityCourses: Map<String, AgilityCourseData> by lazy {
        json.decodeFromString<Map<String, AgilityCourseData>>(asset("agility_courses.json"))
    }

    /** prestige_paths.json — a list of per-skill trees, keyed here by skill. */
    val prestigeTrees: Map<String, PrestigeSkillTreeData> by lazy {
        json.decodeFromString<List<PrestigeSkillTreeData>>(asset("prestige_paths.json"))
            .associateBy { it.skill }
    }

    private val equipment: Map<String, EquipmentEntry> by lazy {
        json.decodeFromString<Map<String, EquipmentEntry>>(asset("equipment.json"))
    }

    private val pets: Map<String, PetEntry> by lazy {
        json.decodeFromString<Map<String, PetEntry>>(asset("pets.json"))
    }

    /** The handful of equipment fields the cape and tool math need. */
    @Serializable
    private data class EquipmentEntry(
        val slot: String = "",
        @SerialName("cape_skill") val capeSkill: String? = null,
        @SerialName("cape_bonus") val capeBonus: Double = 0.0,
        @SerialName("agility_efficiency") val agilityEfficiency: Float? = null,
        val requirements: Map<String, Int> = emptyMap(),
    )

    @Serializable
    private data class PetEntry(
        @SerialName("effect_type") val effectType: String = "",
        @SerialName("boosted_skill") val boostedSkill: String = "",
        @SerialName("boost_percent") val boostPercent: Int = 0,
    )

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    /** The highest-requirement agility course unlocked at [agilityLevel]. */
    fun bestAgilityCourseFor(agilityLevel: Int): Map.Entry<String, AgilityCourseData> =
        agilityCourses.entries
            .filter { it.value.levelRequired <= agilityLevel }
            .maxByOrNull { it.value.levelRequired }
            ?: agilityCourses.entries.minByOrNull { it.value.levelRequired }!!

    /**
     * Summed XP boost percent [petIds] grant [skill]; pets boosting "all" count for
     * every skill. Additive, as QueuedSessionStarter.gatheringPetBoost has it.
     */
    fun petBoostPct(
        petIds: List<String>,
        skill: String,
    ): Int =
        petIds.sumOf { id ->
            val pet = pets[id] ?: error("Unknown pet '$id'. Known: ${pets.keys.joinToString(", ")}")
            if (pet.boostedSkill == skill || pet.boostedSkill == "all") pet.boostPercent else 0
        }

    /**
     * Summed cape bonus for [skill]: the best matching skill cape plus the best
     * matching guild cape, as resolveCapeMultiplier does.
     */
    fun capeBonusTotal(
        capeKeys: List<String>,
        skill: String,
    ): Double {
        var bestSkill = 0.0
        var bestGuild = 0.0
        capeKeys.forEach { key ->
            val cape = equipment[key] ?: error("Unknown cape '$key'")
            if (cape.capeSkill != skill || cape.capeBonus <= 0.0) return@forEach
            if (key.endsWith("_guild_cape")) {
                bestGuild = maxOf(bestGuild, cape.capeBonus)
            } else {
                bestSkill = maxOf(bestSkill, cape.capeBonus)
            }
        }
        return bestSkill + bestGuild
    }

    // ------------------------------------------------------------------
    // Tools
    // ------------------------------------------------------------------

    /**
     * Tool tier boundaries, from the game's util/ToolEfficiency.kt.
     *
     * That file is an extension on GameDataRepository, which is a Hilt/Room type
     * we cannot compile here, so this rule is restated rather than reused. If the
     * game retunes tiers or the per-tier bonus, this needs updating with it.
     */
    private val TOOL_TIERS = listOf(1, 15, 30, 55, 70, 85)

    private fun tierIndex(level: Int): Int = TOOL_TIERS.indexOfLast { it <= level }.coerceAtLeast(0)

    /**
     * Efficiency of [toolKey] used on an activity requiring [activityLevelRequired].
     *
     * A tool outranking the activity gets +25% per tier of difference, so the same
     * hook is worth more on courses well below it.
     */
    fun agilityToolEfficiency(
        toolKey: String,
        activityLevelRequired: Int,
    ): Float {
        val tool = equipment[toolKey] ?: error("Unknown tool '$toolKey'")
        val base = tool.agilityEfficiency ?: 1.0f
        if (activityLevelRequired <= 0) return base
        val toolReqLevel = tool.requirements[AGILITY] ?: 1
        val tierDiff = tierIndex(toolReqLevel) - tierIndex(activityLevelRequired)
        return if (tierDiff > 0) base * (1.0f + 0.25f * tierDiff) else base
    }

    /**
     * Best owned hook for [agilityLevel] on a course requiring [activityLevelRequired].
     *
     * Picked by resulting efficiency rather than by tier, because the tierDiff bonus
     * can make a lower-requirement tool win: the shadow hook (req 40, base 2.5)
     * beats runite (req 85, base 2.25) on any course both can work.
     */
    fun bestAgilityTool(
        owned: List<String>,
        agilityLevel: Int,
        activityLevelRequired: Int,
    ): Pair<String, Float>? =
        owned.filter { key ->
            val tool = equipment[key] ?: error("Unknown tool '$key'")
            require(tool.slot == "grappling_hook") { "'$key' is not a grappling hook" }
            (tool.requirements[AGILITY] ?: 1) <= agilityLevel
        }.map { it to agilityToolEfficiency(it, activityLevelRequired) }
            .maxByOrNull { it.second }

    /** XP multiplier of a church blessing, e.g. 1.37 for divine_grace. */
    fun blessingXpMultiplier(key: String): Double {
        val blessing =
            blessings[key]
                ?: error("Unknown blessing '$key'. XP blessings: ${blessings.keys.joinToString(", ")}")
        return blessing
    }

    /**
     * XP blessings, read out of the game's ChurchRepository source.
     *
     * They live in a Kotlin companion object rather than in an asset, and that file
     * pulls in Hilt so it cannot be compiled here — but the declarations are
     * uniform enough to read directly, which keeps the values tied to the checked
     * out tag instead of being copied into this repo and going stale.
     */
    private val blessings: Map<String, Double> by lazy {
        val source = File(gameRoot, "kotlin/com/fantasyidler/repository/ChurchRepository.kt")
        require(source.isFile) { "Missing ${source.path} — is the game submodule checked out?" }
        val pattern = Regex("""BlessingData\(\s*"(\w+)"\s*,\s*\d+\s*,\s*BlessingType\.XP\s*,\s*([\d.]+)f\s*\)""")
        val found =
            pattern.findAll(source.readText())
                .associate { it.groupValues[1] to it.groupValues[2].toDouble() }
        check(found.isNotEmpty()) {
            "Found no XP blessings in ${source.path}. The game's declaration format " +
                "probably changed — update the pattern in GameData.blessings."
        }
        found
    }
}
