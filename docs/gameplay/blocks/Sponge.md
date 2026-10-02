# Sponge and Wet Sponge

A **Sponge** removes nearby reachable water and becomes a **Wet Sponge** after a successful absorption. Recover it, dry it, and place it again to keep draining. The two forms are separate registered blocks and items: `minecraft:sponge` and `minecraft:wet_sponge`. This guide covers both forms. [Registration][register] · [Item forms][items] · [Absorption][absorb]

## Getting your first sponges

The checked Survival starting routes supply **Wet Sponges**:

- **Ocean Monument sponge rooms:** the monument's room builder can select a closed upper room that generates Wet Sponges near its ceiling. Room selection depends on the generated layout; a sponge room or a fixed haul is not guaranteed. Inside that room, each of 36 possible columns has a chance to contain a one- or two-block sponge column. [Active monument builder][monument] · [Room selection][rooms] · [Wet Sponge placement][sponge-room]
- **Elder Guardians:** the bundled death-loot table gives **one Wet Sponge** when the kill has player credit. The sponge pool has no Looting multiplier, so Looting does not increase that one drop. The actual loot check uses the recorded last-damaging player; do not rely on an unattended environmental death having that credit. Normal mob-loot rules must allow drops. [Sponge loot pool][elder-loot] · [Player-credit condition][killed-by-player] · [Death-loot dispatch][death-loot] · [Loot context][loot-context] · [Mob-loot rule][mob-loot]

The monument builder adds two wing rooms and a penthouse with Elder Guardian spawn calls; these are the checked structure source of those mobs. Dry the resulting Wet Sponge using either method below. The bundled Sponge recipe is **Wet Sponge → Sponge** in a Furnace. [Monument pieces][rooms] · [Wing spawns][wing-a] · [Wing b][wing-b] · [Penthouse spawn][penthouse] · [Spawn implementation][elder-spawn] · [Drying recipe][recipe]

## Breaking and recovering them

Both forms drop **one item of their current form** when normally mined in Survival. You can recover them **by hand**; a correct tool is not required for drops. A **Hoe** is the speed tool because both forms are in the hoe-mineable tag. Neither Silk Touch nor Fortune changes the simple one-item loot. Both blocks have registered hardness **0.6**. Their explosion-survival loot condition means an explosion is not a guaranteed recovery method. [Registration][register] · [Hoe tag][hoe] · [Tool speed][tool-material] · [Player harvest rule][player-tool] · [Harvest][harvest] · [Dry loot][dry-loot] · [Wet loot][wet-loot]

Breaking a Wet Sponge does **not** dry it. Likewise, placing a Wet Sponge beside more water does not give it another absorption cycle. Its placed behavior only implements ultrawarm drying and dripping particles; those particles do not gradually consume the stored wet state. [Wet Sponge behavior][wet]

## Dry Sponge

A newly placed dry Sponge tries to absorb water immediately. A neighbor change can trigger another attempt while it remains dry. If no eligible water is removed it stays dry; after a successful attempt it becomes Wet Sponge and plays its absorption sound. The normal server placement path calls this block behavior. [Placement and neighbor triggers][absorb] · [Active placement caller][placement]

The search follows **six-direction, face-connected steps** out from the Sponge:

- It reaches at most **six steps** from the Sponge along accepted water-containing positions
- It accepts at most **64 water positions per traversal**: the traversal budget is 65 accepted positions, but the Sponge's own starting position uses the first one
- A skipped position does not pass the search onward. Air, a solid dry wall, or an unsupported water-containing block can therefore interrupt the route
- The limit describes reachable positions, not a promise to clear a 13 × 13 × 13 cube. Diagonally touching water requires a valid route through face-adjacent positions

These limits follow the actual call and its traversal helper, including the counted starting position. [Absorption search][absorb-search] · [Depth, count and skipped-node handling][traversal]

### Which water and blocks are admitted

The first check is membership in the **water fluid tag**. The bundled tag contains source Water and Flowing Water; Lava does not qualify. A matching fluid still needs one of the supported removal paths below. [Water tag][water-tag] · [Removal branches][absorb-search]

| Position encountered | What the Sponge does |
| --- | --- |
| Ordinary source Water | Uses the block's bucket-pickup operation and removes the water |
| Ordinary flowing Water, including non-source levels | Removes the liquid block even when bucket pickup cannot collect that level |
| A waterlogged block with successful bucket pickup | Drains its water through that block's own pickup behavior; the usual waterlogged-block implementation clears `waterlogged` and retains the block unless its survival check destroys it |
| Bubble Column | Uses its bucket-pickup operation, which removes the column block |
| Kelp, Kelp Plant, Seagrass or Tall Seagrass | Runs ordinary block-loot handling, then replaces the plant with air; this is not a Silk Touch or Shears harvest |
| Other water-containing blocks without a successful supported path | Skips the position and does not expand through it |

[Source and flowing liquid pickup][liquid] · [Waterlogged pickup][waterlogged] · [Bubble Column pickup][bubble] · [Exact plant list and no-tool loot call][absorb-search] · [Plant drops][plant-drops]

The bucket-pickup calls are internal removal operations: absorption does **not** give you a Water Bucket for each removed position. A waterlogged Slab is a useful example: Slabs use the waterlogged pickup implementation, so a reachable waterlogged Slab can be drained while the Slab stays in place. Ordinary solid blocks are not generally erased to reach water behind them. [Absorption return handling][absorb-search] · [Slab implementation][slab] · [Waterlogged behavior][waterlogged]

## Wet Sponge

A Wet Sponge is the recoverable result of absorption, regardless of whether the traversal removed one water position or many. There is no partly wet block state or stored bucket count in these block implementations. Choose Furnace drying if you want to recover a Water Bucket, or ultrawarm placement for immediate drying. [Wet transition][absorb] · [Wet Sponge implementation][wet] · [Furnace bucket rule][furnace-bucket]

### Furnace and bucket recovery

Smelt **1 Wet Sponge → 1 Sponge** in an ordinary [Furnace](Furnace.md). The bundled recipe takes **200 processing ticks** and records **0.15 recipe experience** per smelt. That is 10 seconds at 20 game ticks per second while processing continues. It is a smelting recipe; the Furnace selects that recipe type. [Exact recipe][recipe] · [Furnace recipe type][furnace-type] · [Recipe loading][recipes] · [Processing caller][furnace-ticker] · [Furnace tick][furnace-tick]

To collect the removed water, have **one empty Bucket in the fuel slot when the smelt finishes**. The output becomes a dry Sponge and the fuel-slot Bucket becomes **one Water Bucket**. The fuel slot accepts one empty Bucket, but an empty Bucket is not itself fuel: the Furnace needs enough remaining burn time to finish. The output slot also needs room for the dry Sponge. [Fuel-slot rules][fuel-slot] · [Output capacity and completion][furnace-bucket] · [Burn-time processing][furnace-tick]

Two practical ways to arrange this are:

1. Start burning ordinary fuel, then replace any fuel remaining in the slot with an empty Bucket while the Furnace is still lit
2. Use a **Lava Bucket** as fuel. Consuming it leaves an empty Bucket in the fuel slot, which the completed Wet Sponge smelt fills with water

The second method follows the actual fuel-consumption and crafting-remainder path. It yields a Water Bucket for a completed sponge, not a bucket for every water position the Sponge absorbed. Remove the filled Water Bucket and insert another empty Bucket before a later smelt finishes if you want another collection. [Fuel consumption and remainder][furnace-tick] · [Lava fuel][fuel] · [Lava Bucket remainder][buckets] · [One completion conversion][furnace-bucket]

### Drying by Nether placement

Place a Wet Sponge in an **ultrawarm dimension** and it immediately changes to dry Sponge with drying sound and particles. The bundled **Nether** dimension type is ultrawarm; the bundled Overworld, Overworld Caves, End and Primordial Caves types are not. This checks the dimension's `ultrawarm` flag, so custom dimension data can change where it works. Pick up the newly dry block and use it again. [Drying behavior][wet] · [Nether flag][nether] · [Other bundled flags][overworld] · [Overworld caves][overworld-caves] · [End][end] · [Primordial][primordial]

This placement method does not produce a Water Bucket. Using an empty Bucket directly on a placed Wet Sponge also has no water-extraction interaction: Wet Sponge does not implement the bucket-pickup interface. Use the Furnace fuel-slot method for a filled bucket. [Wet block][wet] · [Empty Bucket interaction][bucket-use] · [Furnace conversion][furnace-bucket]

## Practical draining examples

- **A narrow water channel:** place the dry Sponge next to the connected water. In a straight channel with no other branches, the search can reach six water positions away; a seventh requires another placement or a closer Sponge. This is a source-derived reach example, not a measured in-game clearing test. [Depth limit][absorb-search] · [Traversal][traversal]
- **Two pools separated by a dry wall:** drain each side separately. The search cannot travel through the wall or across a gap of air to reach the other pool. [Skipped positions][absorb-search] · [Traversal][traversal]
- **A large flooded room:** divide the job into smaller sections and keep a drying route ready. A single traversal has limited reach and count, and the wet result cannot repeat the search. Remaining water can flow back or regenerate sources under the normal fluid rules, so isolate the section you are draining. [Limits and wet transition][absorb] · [Traversal][traversal] · [Water updates][fluid-tick] · [Source conversion and flow][fluid-state]
- **A waterlogged build:** check what is being drained. A supported waterlogged block can remain, but the four explicitly listed aquatic plants are removed and use their ordinary no-tool loot rules. [Supported paths][absorb-search] · [Waterlogged][waterlogged]

## Sources and verification

Source-reviewed on **2026-10-02** at `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`. The review traced block/item registration, active placement and mining, the breadth-first helper, fluid and tool tags, plant pickup, monument room/spawn selection, player-credit loot, Furnace recipe dispatch, fuel remainders and all bundled ultrawarm flags. Source-derived examples explain the checked logic; no in-game absorption, mining, monument generation, drying, bucket or timing test was run. Data packs and server rules can change recipes, tags and loot.

Related: [Sponge item](../items/Sponge.md) · [Wet Sponge item](../items/WetSponge.md) · [Furnace](Furnace.md) · [Axes and Hoes](../mechanics/AxesAndHoes.md) · [Nether](../dimensions/Nether.md) · [Workstations and utilities](catalog/workstations.md) · [Blocks](Blocks.md)

[register]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L614-L619
[items]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L279-L280
[absorb]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/SpongeBlock.java#L32-L84
[absorb-search]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/SpongeBlock.java#L52-L83
[placement]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L329-L334
[traversal]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/core/BlockPos.java#L459-L491
[wet]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/WetSpongeBlock.java#L14-L72
[monument]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentStructure.java#L30-L55
[rooms]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L140-L206
[sponge-room]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1725-L1760
[wing-a]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1845-L1853
[wing-b]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1883-L1892
[penthouse]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1391-L1399
[elder-spawn]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1534-L1545
[elder-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/entities/elder_guardian.json#L111-L125
[killed-by-player]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[death-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1475
[loot-context]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1528
[mob-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/LivingEntity.java#L562-L567
[hoe]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/mineable/hoe.json#L1-L17
[tool-material]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[dry-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/sponge.json#L1-L21
[wet-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/wet_sponge.json#L1-L21
[water-tag]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/fluid/water.json#L1-L6
[fluid-tick]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L436-L454
[fluid-state]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L166-L203
[liquid]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/LiquidBlock.java#L202-L210
[waterlogged]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[bubble]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/BubbleColumnBlock.java#L191-L195
[plant-drops]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Block.java#L371-L375
[slab]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L31-L43
[recipe]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/smelting/sponge.json#L1-L10
[furnace-type]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java#L11-L16
[furnace-ticker]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/AbstractFurnaceBlock.java#L84-L97
[furnace-tick]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L143-L196
[furnace-bucket]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L210-L261
[fuel-slot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/inventory/FurnaceFuelSlot.java#L15-L27
[fuel]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L33-L48
[buckets]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L1511-L1517
[bucket-use]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/BucketItem.java#L54-L71
[nether]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/dimension_type/the_nether.json#L1-L20
[overworld]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/dimension_type/overworld.json#L1-L24
[overworld-caves]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/dimension_type/overworld_caves.json#L1-L24
[end]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/dimension_type/the_end.json#L1-L20
[primordial]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/dimension_type/primordial_caves.json#L1-L24
[harvest]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[player-tool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[drop-dispatch]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Block.java#L439-L443
[recipes]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
