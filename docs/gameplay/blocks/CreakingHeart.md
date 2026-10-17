# Creaking Heart

A **Creaking Heart** can summon one bound [Creaking](../mobs/Creaking.md) and make **Resin Clumps** after qualifying hits on that protector. Put the Heart between **two aligned Pale Oak timber blocks**, wait for the qualifying night window, and leave valid spawning space nearby. The Heart's ID is `minecraft:creaking_heart`; its normal item places the same functional block. This guide owns the placed Heart and its Resin setup; the mob page owns Creaking behavior and combat. [Block/item registrations][heart-block] · [Item][heart-item] · [Placement and alignment][heart-placement] · [Spawn loop][heart-tick]

## Crafting and placing a Heart

At a [Crafting Table](CraftingTable.md), arrange **two unstripped Pale Oak Logs** around **one [Block of Resin](Resin.md#block-of-resin)** in a vertical three-cell column:

```text
Pale Oak Log
Block of Resin
Pale Oak Log
```

This yields **one Creaking Heart**. The recipe names `pale_oak_log` exactly: Pale Oak Wood and stripped variants cannot replace those two ingredients. Pack nine Resin Clumps to make the central Resin block; Resin Bricks are a different material. [Heart recipe][craft-creaking_heart] · [Resin packing][craft-resin_block]

For activation, the accepted timber is broader than the recipe: **Pale Oak Log, Pale Oak Wood, Stripped Pale Oak Log, and Stripped Pale Oak Wood** all belong to the required block tag. Place one immediately on **each end of the Heart's axis**, and give all three blocks the **same axis**. The two timber blocks can be different accepted forms. [Pale Oak block tag][log-tag] · [Exact neighbor test][heart-placement]

| Heart axis | Required neighbors | Timber axis |
| --- | --- | --- |
| `y` | Immediately above and below | `y` |
| `x` | Immediately east and west | `x` |
| `z` | Immediately north and south | `z` |

The Heart takes the axis of the face you click when placing it, like a pillar. A vertical setup is the simplest: place a vertical Pale Oak Log, put the Heart on its top, then place another vertical accepted timber above. The remaining four faces need not be enclosed. Six-sided Pale Oak enclosure only participates in the idle-sound condition; it is not an extra activation requirement. The Heart is a full block without a waterlogged state. [Placement and state definition][heart-placement] · [Idle sound][heart-sound] · [Full-block defaults][full-shape]

## Finding a natural Heart

In the bundled **normal world preset**, the Overworld biome selector includes **Pale Garden**. That biome installs the Pale Garden vegetation placement, whose selector chooses the Creaking-tree branch with a **0.1 chance per selector attempt**. Its tree configuration enables the Heart decorator with probability 1.0. This does **not** guarantee a Heart in exactly one out of every ten visible trees: placement can fail, and the decorator needs a candidate log with log-tag neighbors on all six sides. [Normal preset][normal] · [Biome-preset dispatch][biome-dispatch] · [Pale Garden selection][biome-list] · [Biome features][garden] · [Vegetation placement][garden-placed] · [Tree selector][garden-selector] · [Selector execution][random-selector] · [Selected placement][heart-tree-placed] · [Tree configuration][heart-tree]

The successful decorator replaces one candidate log with a Heart whose initial states are **`axis=y`, `creaking_heart_state=dormant`, `natural=true`**. The tree feature actively calls the registered decorator after a successful tree placement. It does not pre-place Resin around the Heart. [Decorator registration][decorator-reg] · [Tree callback][decorator-dispatch] · [Heart placement][decorator] · [Default axis][heart-state]

**Player-grown Pale Oaks do not generate Hearts through this route.** Their grower selects `pale_oak_bonemeal`, which has an empty decorator list, including when growth comes from ordinary sapling random ticks. Use the [Pale Oak sapling guide](SaplingsAndAzaleas.md#pale-oak-sapling) for planting and space requirements. [Grower selection][grower] · [Random-tick and Bone Meal callers][sapling-callback] · [Feature dispatch][grower-callback] · [Planted-tree configuration][grown-tree]

## Heart states and the night window

A Heart exposes three properties: `axis=x|y|z`, `creaking_heart_state=uprooted|dormant|awake`, and `natural=true|false`. Ordinary item placement starts from **`natural=false`**, including when the item was mined from a natural Heart with Silk Touch. The loot does not copy the original natural state. **Natural is an origin/experience distinction, not a requirement to summon a Creaking or produce Resin.** [Default and ticker][heart-state] · [Placement][heart-placement] · [State names][heart-enum] · [Property name][heart-property] · [Natural property][natural-property] · [Loot][loot-creaking_heart] · [Operating loop][heart-tick]

| State | Practical meaning |
| --- | --- |
| `uprooted` | Inactive; it has no server block-entity ticker. Adding the aligned timber pair can move it to an operating state |
| `dormant` | Rooted but outside the qualifying night window; it cannot start a new protector or a new Resin-production burst |
| `awake` | Rooted during the qualifying night window; spawning and Resin still need their other conditions |

Here, “night” means **day-time ticks 12,600 through 23,400 inclusive**, modulo 24,000, in a dimension whose type has **`natural=true`**. The bundled Overworld qualifies; the Nether and End do not. This test does not inspect local light, weather, the biome at the placed Heart, or an open view of the sky. A dark room or thunderstorm cannot substitute for the time window. A placed Heart can work outside Pale Garden in a qualifying dimension. [Night helper][heart-state] · [Actual time/dimension check][night] · [Overworld][overworld] · [Nether][nether] · [End][end]

State and spawn checks happen periodically while the block entity ticks, so transitions need not occur on the exact tick that the clock crosses a boundary. The countdown resets to 20–24 and uses a post-decrement `< 0` test, giving **22–26 ticks between later checks**, about 1.1–1.3 seconds at 20 TPS. The first scheduled check follows the initial countdown. [Periodic update][heart-tick]

## Spawning space and player distance

An awake Heart tries to summon a protector only when it has no stored protector link and all these requirements pass:

- The server allows monster spawning: **difficulty is not Peaceful**, **`doMobSpawning=true`**, and **`spawnMonsters=true`**
- A **non-spectator player is less than 32 blocks away**, measured from the Heart's integer block coordinates. Creative players qualify for this proximity check
- At least one of the search attempts finds a legal floor and unobstructed, liquid-free space for the mob

The Heart does not use an ordinary biome monster-spawn roll. It asks the spawn helper for **five attempts**, with random horizontal offsets from **−16 to +16** on each axis. Each attempt starts its downward floor search above the Heart and can find a feet position from **8 blocks above to 8 below**. The supporting block must have a full upper collision face and must not be leaves; the mob's full spawn box must fit. The active instance checks add no light-level test. Failure leaves the Heart available to try again at a later update; an awake appearance does not guarantee a spawned protector. [Heart conditions][heart-tick] · [Server delegation][server-spawn] · [Monster-spawning gates][spawn-switch] · [Player filter and strict range][players] · [Five-attempt call][heart-spawn] · [Search and obstruction checks][spawn-search] · [Allowed floor][spawn-floor] · [Inherited spawn-rule check][pathfinder-spawn] · [Creaking override][creaking-spawn-value] · [Mob obstruction check][spawn-mob]

Keep the Heart and surrounding area ticking. Unloaded or non-ticking setups are not promised to progress. A stored protector UUID is resolved back to an entity after loading; an unresolved link has a **30 ticking-tick grace period** before it can be cleared, which can delay replacement spawning. [Ticking gate][chunk-tick] · [Saved link][heart-save] · [Link resolution][heart-recovery]

## Producing Resin

A source-derived production loop is:

1. Keep an awake, working Heart with its bound Creaking and nearby Pale Oak timber
2. Leave empty attachment spaces beside the timber, or suitable source-water/existing-clump spaces
3. Cause a qualifying hit on the bound Creaking that the game credits to a player
4. Harvest the new [Resin Clumps](Resin.md#placing-clumps-and-building-with-resin), then wait for the Heart's cooldown before another production trigger

This is a source-based setup, **not a tested farm design or a guaranteed rate**. The Creaking must still be this Heart's protector, the hurt handler must accept the event, and the resolved responsible player must be non-null. Direct player hits are one route; the attribution code can also resolve a tame-wolf owner or remembered player attribution. A spawn-egg Creaking without a Heart link does not run this Heart-production callback. [Bound hurt callback][creaking-hurt] · [Player attribution][attribution] · [Protector check and cooldown][heart-hurt]

When its emitter cooldown is inactive, the Heart makes **2–3 spread attempts**, only if its current state is **awake**. Each attempt can add **one new clump face**, but may find no valid target. It then starts a **100 ticking-tick cooldown**, approximately five seconds at 20 TPS, even if placement failed. Further qualifying hits during that cooldown do not add another burst or restart it. Trail particles and sounds accompany the response; they are not proof that any Resin was successfully placed. [Burst and cooldown][heart-hurt] · [Countdown and effects][heart-tick]

For each attempt, the Heart searches through **face-connected Pale Oak log-tag blocks up to two steps from the Heart**, with a 64-visited-position cap. It tests sides in shuffled order and stops on the first successful face. The timber's axis does not matter to this spread search; alignment matters separately for rooting the Heart. The new face must be beside one of those timber blocks and can be added into:

- Air, creating a new dry Resin Clump
- A source Water block, creating a waterlogged Resin Clump
- An existing Resin Clump missing that particular face

Flowing water, other blocks, and an already occupied face do not qualify for that placement. **Two or three attempts may produce fewer faces, including none**; harvest existing faces to make room. The production callback has no natural-origin requirement and no `mobGriefing` check. [Spread search and targets][heart-resin] · [Traversal depth/count semantics][bfs] · [Pale Oak tag][log-tag]

## Keeping or removing the protector

The Heart keeps a single protector link. The mob's pathfinding discourages movement beyond **32 blocks from home**; the Heart separately removes its protector when its measured distance is **greater than 34 blocks**. Heart-bound Creakings cannot use portals. [Home pathfinding][creaking-home] · [Distance and removal][heart-tick] · [Distance origin][heart-recovery] · [Portal restriction][creaking-portal]

Outside the qualifying night window, the Heart removes a protector **unless the mob is marked persistent**. A Name Tag sets that persistence flag, but does not keep the Heart awake or enable daytime Resin production. Distance and stuck-player removal still apply. The stuck-player safeguard triggers after more than four consecutive periodic checks find a nearby player's eyes inside the Creaking's bounding box; it is not a five-game-tick timer. [Day/distance/stuck removal][heart-tick] · [Name Tag persistence][name-tag] · [Stuck checks][creaking-stuck] · [Awake-only Resin][heart-hurt]

**Removing one of the required timber blocks is not a reliable immediate shutdown once a protector exists.** The periodic state update becomes uprooted for missing timber only when no protector link remains. With an existing link, it continues using the day/night state. Break or remove the Heart itself to remove its linked protector; the Heart's player-break, explosion and block-entity-removal paths all handle that relationship. The bound mob also checks that its home Heart still recognizes it. [Missing-log condition][heart-tick] · [Player/explosion removal][heart-removal] · [Block-entity removal][heart-unlink] · [Mob link check][creaking-link]

## Collecting a Heart and its drops

The Heart has **10 hardness and 10 blast resistance**. An **axe speeds mining**, but the block has no mandatory correct-tool requirement, so ordinary hand mining can produce its non-Silk drops. Its loot table distinguishes:

| Tool enchantment | Ordinary item result |
| --- | --- |
| Silk Touch I or higher | **1 Creaking Heart** |
| No Silk Touch, no Fortune | **1–3 Resin Clumps** |
| Fortune I / II / III, without Silk Touch | **1–4 / 1–5 / 1–6 Resin Clumps**, respectively |

Fortune adds a uniformly random **0 through enchantment level** to the base 1–3 count; the total is capped at **9**. The clump branch applies explosion decay. Silk Touch takes priority over the clump branch. Mining an item does not carry its protector with it or preserve `natural=true`; re-place it and meet the operating conditions to summon a new protector. [Block properties][heart-block] · [Strength shorthand][strength] · [Axe tag][axe] · [Tool gate][player-gate] · [Complete loot][loot-creaking_heart] · [Fortune calculation][fortune] · [Placement default][heart-state] · [Removal][heart-removal]

Player destruction of a **natural Heart** also awards **20–24 XP**, including when Silk Touch preserves the Heart item. This is a separate block callback, not Creaking mob loot, and it does not award that XP from ordinary player-placed Hearts. It requires a non-spectator player who does not suppress block drops and the server's block-drop rule to allow payout. Player-attributed explosions have a corresponding natural-Heart XP path when they affect the block. [Experience and explosion callbacks][heart-removal] · [XP/drop gamerule gate][drop-rule]

## Comparator output and troubleshooting

A [Comparator](RedstoneComparator.md) reads the linked Creaking's distance, not Resin storage or cooldown. Output is **15 − floor(15 × clamp(distance, 0, 32) / 32)**, measured to the Heart's bottom center. It is strongest nearby and reaches zero at 32 blocks; no protector or an uprooted Heart also gives zero. Therefore zero alone cannot distinguish a distant protector from no protector. Redstone power is not an activation requirement in the Heart's operating callbacks. [Comparator calculation][heart-comparator] · [Distance origin][heart-recovery] · [Block output gate][heart-removal] · [Operating loop][heart-tick]

| Symptom | Check first |
| --- | --- |
| Heart stays uprooted | Both end neighbors must be in the Pale Oak log tag, touch the Heart, and share its axis |
| Heart is dormant | Check the 12,600–23,400 clock window and the dimension's natural flag |
| Heart is awake but no Creaking appears | Check both spawn gamerules, difficulty, non-spectator distance, floor/headroom/liquid, and an existing stored link; attempts can fail |
| Hits show effects but add no Resin | Check awake state, the 100-tick cooldown, player attribution, and vacant eligible faces beside nearby connected timber |
| Removing a log leaves the mob alive | Existing protector links keep the missing-log shutdown condition from uprooting the Heart |
| A recovered Heart gives no natural-heart XP later | Normal item placement uses natural=false |

Related: [Resin materials and construction](Resin.md) · [Creaking mob](../mobs/Creaking.md) · [Pale Oak timber](TreeLogsAndRoots.md#pale_oak-timber) · [Pale Oak planting](SaplingsAndAzaleas.md#pale-oak-sapling) · [Pale Garden moss](MossAndPaleMoss.md#finding-a-starting-supply) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf1c134b3f9ff634490e448fe26c335b90f82227`. Checked the registered Heart block/item/entity/ticker chain, exact crafting and loot, expanded Pale Oak and mining/tier tags, normal-preset Pale Garden tree/decorator wiring, dimension/time and spawn gates, active Creaking hurt attribution, Resin placement traversal, saved protector links, removal, natural-heart XP, and comparator callbacks. No in-game generation, mining, activation, spawning, naming, Resin farming or comparator test was run. Timing assumes a ticking server; data packs and later builds can change the checked data and behavior.

[heart-block]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L1238-L1242
[heart-item]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L475
[heart-placement]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/CreakingHeartBlock.java#L84-L154
[heart-tick]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L71-L145
[craft-creaking_heart]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/creaking_heart.json
[craft-resin_block]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/resin_block.json
[log-tag]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/pale_oak_logs.json
[heart-sound]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/CreakingHeartBlock.java#L73-L80
[full-shape]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[normal]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[biome-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[biome-list]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L87-L95
[garden]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/biome/pale_garden.json
[garden-placed]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/placed_feature/pale_garden_vegetation.json
[garden-selector]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/configured_feature/pale_garden_vegetation.json
[random-selector]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/feature/RandomSelectorFeature.java#L15-L29
[heart-tree-placed]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/placed_feature/pale_oak_creaking_checked.json
[heart-tree]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/configured_feature/pale_oak_creaking.json
[decorator-reg]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/TreeDecoratorType.java#L11
[decorator-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L155-L160
[decorator]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/CreakingHeartDecorator.java#L32-L57
[heart-state]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/CreakingHeartBlock.java#L36-L71
[grower]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L65
[sapling-callback]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/SaplingBlock.java#L45-L72
[grower-callback]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L120-L152
[grown-tree]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/configured_feature/pale_oak_bonemeal.json
[heart-enum]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/properties/CreakingHeartState.java#L5-L8
[heart-property]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/properties/BlockStateProperties.java#L155
[natural-property]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/properties/BlockStateProperties.java#L33
[loot-creaking_heart]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/creaking_heart.json
[night]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/Level.java#L365-L371
[overworld]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/dimension_type/overworld.json
[nether]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/dimension_type/the_nether.json
[end]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/dimension_type/the_end.json
[server-spawn]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerLevel.java#L1766-L1767
[spawn-switch]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/MinecraftServer.java#L1473-L1476
[players]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/EntityGetter.java#L75-L100
[heart-spawn]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L198-L212
[spawn-search]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/util/SpawnUtil.java#L18-L72
[spawn-floor]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/util/SpawnUtil.java#L96-L101
[pathfinder-spawn]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/PathfinderMob.java#L28-L31
[creaking-spawn-value]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L522-L525
[spawn-mob]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/Mob.java#L735-L741
[chunk-tick]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L758-L768
[heart-save]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L342-L354
[heart-recovery]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L147-L195
[creaking-hurt]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L153-L185
[attribution]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1316-L1338
[heart-hurt]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L224-L245
[heart-resin]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L247-L280
[bfs]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/core/BlockPos.java#L459-L490
[creaking-home]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L601-L612
[creaking-portal]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L350-L357
[name-tag]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/NameTagItem.java#L17-L29
[creaking-stuck]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L364-L381
[heart-removal]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/CreakingHeartBlock.java#L161-L205
[heart-unlink]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L305-L325
[creaking-link]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L242-L268
[strength]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[axe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[player-gate]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[fortune]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L156-L165
[drop-rule]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Block.java#L410-L422
[heart-comparator]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L328-L340
