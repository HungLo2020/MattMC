# Amethyst, buds and clusters

Preserve **Budding Amethyst** when mining a geode: it is the renewable growth block, and ordinary Survival harvesting cannot recover it even with Silk Touch. Decorative **Blocks of Amethyst** do not grow crystals. Harvest the fully grown **Amethyst Cluster** for Shards, or use Silk Touch to collect the crystal itself. [Registrations][blocks] · [Growth callback][growth] · [Budding loot][loot-budding_amethyst] · [Cluster loot][loot-amethyst_cluster]

## Six registered block forms

| Block and registry ID | Emitted light | Main purpose |
| --- | ---: | --- |
| <span id="amethyst-block">Block of Amethyst, `minecraft:amethyst_block`</span> | 0 | Full building block and vibration resonator |
| <span id="budding-amethyst">Budding Amethyst, `minecraft:budding_amethyst`</span> | 0 | Full block that grows crystals on its faces |
| <span id="small-bud">Small Amethyst Bud, `minecraft:small_amethyst_bud`</span> | 1 | First growth stage |
| <span id="medium-bud">Medium Amethyst Bud, `minecraft:medium_amethyst_bud`</span> | 2 | Second growth stage |
| <span id="large-bud">Large Amethyst Bud, `minecraft:large_amethyst_bud`</span> | 4 | Third growth stage |
| <span id="cluster">Amethyst Cluster, `minecraft:amethyst_cluster`</span> | 5 | Mature, shard-bearing crystal |

All six have **hardness 1.5 and blast resistance 1.5**, and all are in the pickaxe mining-speed tag. The two full blocks require a correct tool for their ordinary drop gate; the buds and cluster do not. A faster tool is not the same as a required drop tool. [Properties and classes][blocks] · [Pickaxe tag][pickaxe] · [Harvest gate][player] [gate]

## Finding a geode

The normal Overworld includes Amethyst Geode placement through checked biome routes such as **Plains**. That biome lists the placed `amethyst_geode` feature, which resolves the registered geode implementation and its configuration. The configured shell has **Smooth Basalt outside, Calcite in the middle, and ordinary/Budding Amethyst on the inner layer**, with air inside and possible buds/clusters. This is an active source route, not a guarantee of a geode beneath a particular player or every chunk. [Normal preset][preset] · [Biome entry][plains] · [Placed feature][placed] · [Configured layers][config] · [Registered implementation][features] [feature]

Use [Calcite and decorative stone](DecorativeStone.md#calcite) and [Basalt](BlackstoneAndBasalt.md#basalt-variants-and-orientation) for those shell materials. The placement's height range describes the attempted feature origin, not the height of every block in the finished shell.

## Growing more crystals

Only **Budding Amethyst** runs the active random-growth callback. Each selected random tick has a **1-in-5 chance** to choose **one of its six directions uniformly**. It then tries the adjacent position:

1. Air or an actual Water block with fluid amount 8 → Small Bud
2. A Small Bud facing outward from this budding block → Medium Bud
3. A matching Medium Bud → Large Bud
4. A matching Large Bud → Amethyst Cluster

The callback does not grow through solid blocks, use arbitrary waterlogged building blocks as empty planting space, or advance a bud facing another direction. A mature cluster has no next stage. A particular face therefore gets a possible advance on one in thirty selected random ticks before occupancy checks; this is not a fixed growth timer. [Exact growth and target checks][growth]

Keep the budding block intact and leave useful faces exposed. Collecting a mature cluster opens its position for the next Small Bud. Crystals placed on ordinary Amethyst or another support **do not grow by themselves**; their class has no random-growth callback. Bone Meal has no growth interface here, and no light or biome condition appears in the budding callback. `randomTickSpeed = 0` stops this natural growth path. [Budding registration][blocks] · [Growth][growth] · [Crystal class][cluster] · [Random dispatch][tick] · [Game rule][rules]

Buds and clusters can be waterlogged. The growth target permits amount-eight Water, including a falling full-water state, but the newly placed crystal's waterlogged flag is set only when the old fluid type is the source-water type. Do not assume every accepted water state is preserved in the same way. The simplest source-based growing layout uses air or source-water cells around the budding block. [Growth target and water transfer][growth]

## Harvesting and protecting the growth block

| What you break | Ordinary result |
| --- | --- |
| Block of Amethyst | 1 block with an unbroken pickaxe, including Wood; wrong tool gives nothing |
| Budding Amethyst | Nothing, including with Silk Touch |
| Small, Medium or Large Bud | 1 matching bud with Silk Touch; otherwise nothing |
| Amethyst Cluster with Silk Touch | 1 Amethyst Cluster, rather than Shards |
| Cluster without Silk Touch, using a listed pickaxe | 4 Shards before Fortune |
| Cluster without Silk Touch or a listed pickaxe | 2 Shards before any explosion decay |

The maximum-harvest item tag explicitly lists **Wood, Stone, Copper, Iron, Gold, Diamond and Netherite Pickaxes**. The cluster's larger yield is an item-tag condition inside loot, not a higher-material-tier requirement. The buds and cluster have no correct-tool gate, so their Silk Touch branch does not require a pickaxe tier. Use a normal unbroken Silk Touch pickaxe when moving crystals for a straightforward setup. [All six loot tables][loot-amethyst_block] [loot-budding_amethyst][] [loot-small_amethyst_bud][] [loot-medium_amethyst_bud][] [loot-large_amethyst_bud][] [loot-amethyst_cluster] · [Maximum-harvest tag][harvest] · [Tier tags][wood] [stone][] [iron][] [diamond]

Fortune applies only to the **four-Shard pickaxe branch**. Fortune I can yield 4 or 8; II can yield 4, 8 or 12; III can yield 4, 8, 12 or 16. These outcomes are not equally likely: the base multiplier has extra weight. Silk Touch takes priority, and Fortune on a different tool does not improve the two-Shard fallback. [Loot branch order][loot-amethyst_cluster] · [Ore-drop multiplier][fortune]

Do not break the supporting block before collecting a valuable bud. Unsupported crystals are removed, and the empty-tool removal path yields **nothing for immature buds or two Shards for a mature cluster**, rather than preserving the crystal item. Budding Amethyst and all four crystal forms have the **piston destroy reaction**: they are not moved intact by that route. Their loot rules still determine what destruction drops. [Support-loss callback][cluster] · [Removal/drop dispatch][block] · [Piston properties][blocks] · [Piston destruction][piston]

Budding Amethyst has a registered inventory form for Creative access, but its empty loot and absence of a producing recipe do not supply a Survival relocation method. This is why preserving the original geode's budding blocks matters. [Item registration][items] · [Creative listing][creative] · [Empty loot][loot-budding_amethyst]

## Placing crystals and making sound

A bud or cluster can face any of the **six directions**, including downward from a ceiling. Click the support face it should project from; that support must have a sturdy face toward the crystal. The same shape class handles all four stages with different sizes. A collected cluster can decorate a non-budding support, but that does not turn the support into a growth block. [Placement, shapes and survival][cluster]

Item placement waterlogs the crystal only when the existing fluid type is source Water. The standard waterlogging interface allows later bucket filling or water pickup. Picking up its water does not collect the crystal item. [Placement fluid rule][cluster] · [Waterlogging interface][waterlog]

Projectiles hitting this family run the inherited Amethyst chime callback on the server. This produces a sound; the callback does not itself output redstone power. Ordinary **Blocks of Amethyst** also belong to `vibration_resonators`: an activated Sculk Sensor checks its six neighboring positions and emits a frequency-matched resonance event and sound at tagged blocks. Budding Amethyst and the crystal forms are not in that resonator tag. This is a specific Sculk interaction, not automatic redstone output from any purple block. [Projectile dispatch][projectile] · [Chime][amethyst] · [Resonator tag][resonators] · [Active resonance caller][sensor]

## Shard recipes and other uses

| Ingredients | Result and arrangement |
| --- | --- |
| 4 Amethyst Shards | 1 Block of Amethyst; 2 × 2 square |
| 4 Shards + 1 ordinary Glass | 2 Tinted Glass; Shards on the four sides of the central Glass |
| 1 Shard + 2 Copper Ingots | 1 Spyglass; vertical Shard above the two Ingots |
| 3 Shards + 1 Sculk Sensor | 1 Calibrated Sculk Sensor; one Shard above and one on each side of the Sensor |

These are the checked direct crafting recipes. The block recipe does not make Budding Amethyst, and there is **no reverse recipe returning Shards from a Block of Amethyst** in the bundled recipe inventory. See [Tinted Glass](GlassAndPanes.md#tinted-glass) for its placed behavior. [Recipes][recipe-amethyst_block] [recipe-tinted_glass][] [recipe-spyglass][] [recipe-calibrated_sculk_sensor]

Shards also carry the **Amethyst armor-trim material** on their item registration. Use the [Smithing guide](../smithing/Smithing.md) for the wider process. A Shard is the bundled item accepted by the **Allay duplication interaction**, which requires a dancing Allay whose duplication flag is ready; successful duplication resets both animals' cooldowns to 6,000 ticks. Simply handing a Shard to an arbitrary Allay does not establish that duplication will occur. [Trim material][items] · [Active trim lookup][trim-lookup] [trim-recipe] · [Duplication tag][allay-tag] · [Interaction and cooldown][allay]

## Sources and verification

Source-reviewed on **2026-10-02** at `beaa5747b36af51b001a13ce8b6648319ba6faf5`. All six registrations and loot tables, exact growth states, tool and Fortune branches, placement/water transfer, piston reaction, geode route, four direct recipes and active item uses were checked. No gameplay growth, harvesting, piston, generation, sound or duplication test was run. Data packs and server ticking can change the relevant resources and timing.

Related: [Amethyst Shard](../items/AmethystShard.md) · [Block of Amethyst](../items/BlockOfAmethyst.md) · [Budding Amethyst](../items/BuddingAmethyst.md) · [Amethyst Cluster](../items/AmethystCluster.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/Items.java
[growth]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/BuddingAmethystBlock.java
[cluster]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/AmethystClusterBlock.java
[amethyst]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/AmethystBlock.java
[projectile]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/entity/projectile/Projectile.java
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[gate]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java
[player]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/entity/player/Player.java
[harvest]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/item/cluster_max_harvestables.json
[fortune]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java
[waterlog]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java
[piston]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java
[block]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/Block.java
[preset]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[plains]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/worldgen/biome/plains.json
[placed]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/worldgen/placed_feature/amethyst_geode.json
[config]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/worldgen/configured_feature/amethyst_geode.json
[feature]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/levelgen/feature/GeodeFeature.java
[features]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java
[tick]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/server/level/ServerLevel.java
[rules]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/GameRules.java
[sensor]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/level/block/SculkSensorBlock.java
[resonators]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/block/vibration_resonators.json
[allay]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java
[allay-tag]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/tags/item/duplicates_allays.json
[loot-amethyst_block]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/amethyst_block.json
[loot-budding_amethyst]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/budding_amethyst.json
[loot-small_amethyst_bud]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/small_amethyst_bud.json
[loot-medium_amethyst_bud]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/medium_amethyst_bud.json
[loot-large_amethyst_bud]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/large_amethyst_bud.json
[loot-amethyst_cluster]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/loot_table/blocks/amethyst_cluster.json
[recipe-amethyst_block]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/amethyst_block.json
[recipe-tinted_glass]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/tinted_glass.json
[recipe-spyglass]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/spyglass.json
[recipe-calibrated_sculk_sensor]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/resources/data/minecraft/recipe/crafting/calibrated_sculk_sensor.json
[creative]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[trim-lookup]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/equipment/trim/TrimMaterials.java
[trim-recipe]: https://github.com/HungLo2020/MattMC/blob/beaa5747b36af51b001a13ce8b6648319ba6faf5/src/main/java/net/minecraft/world/item/crafting/SmithingTrimRecipe.java
