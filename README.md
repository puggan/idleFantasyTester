A test tool for the game [IdleFantasy](https://github.com/tristinbaker/IdleFantasy).

A tool to compare different paths in the game, like what order to level skills, or
what and when to spend prestige points.

Plans are YAML files; `./run-plan` runs them and reports the in-game time they cost
and the levels they reach. Rather than reimplement the game's math, the build
compiles the game's own simulators out of a submodule pinned to a release tag, so
results track a specific version of the game.

## Setup

```bash
git clone --recurse-submodules git@github.com:puggan/idleFantasyTester.git
```

If you already cloned without submodules, `git submodule update --init` fills in
`game/`. Without it every run fails asking for it.

Requires a JDK. Gradle is pinned to 8.7 to match the game, which cannot run on
JDK 26+, so `gradle.properties` points the daemon at JDK 21 — adjust that path if
your JDK lives elsewhere.

## Running

```bash
./run-plan plans/agility-prestige-a-baseline.yaml   # one plan
./run-plan plans/agility-prestige-*.yaml            # several, plus a comparison
./run-plan --list                                   # what's in plans/
```

The first run builds; later runs only rebuild when sources change.

## Writing a plan

```yaml
name: agility 1-99 with the pet and cape
seed: 42                    # fixed seed: two plans see identical luck

loadout:                    # carried through every step
  pets: [graceling_sprite]  # boosts from several pets add up
  capes: [agility_cape]     # skill and guild capes stack
  blessing: divine_grace
  prestige:
    agility: [agility_xp_1, agility_xp_2]

steps:
  - skill: agility
    course: best            # or a course key; "best" re-picks as levels unlock
    untilLevel: 99          # stop condition: untilLevel, sessions, or both
```

Keys are looked up in the game's data at run time, so a typo in a course, pet,
cape or prestige node id fails with the list of valid ones rather than silently
running without the bonus.

### Plan fields

| field | meaning |
| --- | --- |
| `name` | shown in the report |
| `seed` | RNG seed; keep it equal across plans you intend to compare |
| `startLevels` / `startXp` | per skill; default level 1 |
| `loadout.pets` | pet ids; XP boosts add up, `boosted_skill: all` counts everywhere |
| `loadout.capes` | cape item keys; best skill cape + best guild cape |
| `loadout.blessing` | church blessing key, e.g. `divine_grace` |
| `loadout.blessingRenewed` | default true — assumes you re-cast it as it expires |
| `loadout.postPrestigeXpBoost` | default false — the 48h 2x the game grants on prestige |
| `loadout.prestige` | skill → owned node ids |
| `loadout.race` | for race-locked nodes; default human |

### Step fields

| field | meaning |
| --- | --- |
| `skill` | currently only `agility` |
| `target` / `course` | activity key, or `best` |
| `untilLevel` / `sessions` | stop condition; at least one is required |
| `toolEfficiency` | tool multiplier, 1.0 = base |
| `chronosMultiplier` | Chronos Spire duration multiplier, 0.5–1.0 |
| `prestige` | replaces the loadout's nodes from this step on, modelling a respec |
| `petBoostPct` / `floorReductionMin` | override the loadout for this step |
| `note` | replaces the generated step label in the report |

## How bonuses are modelled

The game applies bonuses at two different times, and the tester keeps them apart
because the split changes the result:

- **In the session sim** — pet XP boost and the agility Endurance floor reduction
  are arguments to `simulateAgility`, shaping the frames themselves.
- **At collect time** — cape, blessing, prestige `xp_pct` and the 2x boost are
  applied to the finished session as
  `floor(floor(frameXp * cape) * xpMultiplier)`, mirroring
  `PlayerRepository.applySessionResults`.

Two consequences worth knowing when reading a plan:

- Prestige node values are **totals per tier, not increments**, and the engine
  takes the max owned tier within a path. XP Boost I+II is +10%, not +15% — tier I
  is a prerequisite you pay for, not an addend.
- Timed buffs are measured against the plan's own in-game clock, so a blessing
  that is not renewed expires partway through a multi-day grind.

## Adding a skill

`PlanRunner` dispatches each step to a `SkillRunner` and knows nothing about any
particular skill, so a new one means a new `SkillRunner` registered in that
interface's companion — not changes to the loop. XP lives in a shared
`PlayerState` because skills leak into each other: agility level shortens session
duration for every skill.

## Limitations

- Only agility is implemented.
- Resource costs are ignored: bones for re-casting blessings, and the prestige
  respec cooldown (24h per skill) are not modelled.
- Each plan runs a single seed. Differences of a few percent between plans are
  within the range RNG can reorder; treat close results as ties.
