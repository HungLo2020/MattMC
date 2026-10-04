# Tree logs and roots

Use logs and stems for pillars, craft wood or hyphae blocks, or strip them with an axe for another building finish. This guide covers **44 timber blocks across eleven vanilla families**, plus the two mangrove root blocks. [Oak](Oak.md) retains its tree-growing guide and [Oak Log](../items/OakLog.md#crafting-conversions) its wood recipes; [Wood Construction](WoodConstruction.md) owns planks and construction shapes.

## Logs, wood, stems, and hyphae

Each row lists four distinct registered blocks. All IDs below use the `minecraft:` namespace. The two ordinary forms strip into their corresponding stripped forms; already stripped blocks have no further stripping step. Each matching item is registered separately. [Overworld registration][s1] · [Pale Oak Wood][s2] · [Warped forms][s3] · [Crimson forms][s4] · [Item forms][s5]

| Family | Log or stem | Stripped log or stem | Wood or hyphae | Stripped wood or hyphae |
| --- | --- | --- | --- | --- |
| <span id="oak-timber">Oak</span> | `oak_log` | [`stripped_oak_log`](../items/StrippedOakLog.md) | `oak_wood` | [`stripped_oak_wood`](../items/StrippedOakWood.md) |
| <span id="spruce-timber">Spruce</span> | `spruce_log` | [`stripped_spruce_log`](../items/StrippedSpruceLog.md) | `spruce_wood` | [`stripped_spruce_wood`](../items/StrippedSpruceWood.md) |
| <span id="birch-timber">Birch</span> | `birch_log` | [`stripped_birch_log`](../items/StrippedBirchLog.md) | `birch_wood` | [`stripped_birch_wood`](../items/StrippedBirchWood.md) |
| <span id="jungle-timber">Jungle</span> | `jungle_log` | [`stripped_jungle_log`](../items/StrippedJungleLog.md) | `jungle_wood` | [`stripped_jungle_wood`](../items/StrippedJungleWood.md) |
| <span id="acacia-timber">Acacia</span> | `acacia_log` | [`stripped_acacia_log`](../items/StrippedAcaciaLog.md) | `acacia_wood` | [`stripped_acacia_wood`](../items/StrippedAcaciaWood.md) |
| <span id="cherry-timber">Cherry</span> | `cherry_log` | [`stripped_cherry_log`](../items/StrippedCherryLog.md) | `cherry_wood` | [`stripped_cherry_wood`](../items/StrippedCherryWood.md) |
| <span id="dark_oak-timber">Dark Oak</span> | `dark_oak_log` | [`stripped_dark_oak_log`](../items/StrippedDarkOakLog.md) | `dark_oak_wood` | [`stripped_dark_oak_wood`](../items/StrippedDarkOakWood.md) |
| <span id="pale_oak-timber">Pale Oak</span> | `pale_oak_log` | [`stripped_pale_oak_log`](../items/StrippedPaleOakLog.md) | `pale_oak_wood` | [`stripped_pale_oak_wood`](../items/StrippedPaleOakWood.md) |
| <span id="mangrove-timber">Mangrove</span> | `mangrove_log` | [`stripped_mangrove_log`](../items/StrippedMangroveLog.md) | `mangrove_wood` | [`stripped_mangrove_wood`](../items/StrippedMangroveWood.md) |
| <span id="crimson-timber">Crimson</span> | [`crimson_stem`](../items/CrimsonStem.md) | [`stripped_crimson_stem`](../items/StrippedCrimsonStem.md) | [`crimson_hyphae`](../items/CrimsonHyphae.md) | [`stripped_crimson_hyphae`](../items/StrippedCrimsonHyphae.md) |
| <span id="warped-timber">Warped</span> | [`warped_stem`](../items/WarpedStem.md) | [`stripped_warped_stem`](../items/StrippedWarpedStem.md) | [`warped_hyphae`](../items/WarpedHyphae.md) | [`stripped_warped_hyphae`](../items/StrippedWarpedHyphae.md) |

### Mining and placement

An **axe** is the efficient tool for all 44 forms, but none requires a special tool or material tier to drop. Ordinary mining returns **one matching block item**; stripped blocks stay stripped. There is no additional Fortune yield or Silk Touch alternative in these tables. Explosion survival is checked separately. All have **hardness 2 and blast resistance 2**. See the complete [loot evidence](#recipe-and-loot-evidence) below. [Axe mining tag][s6] · [Log families][s7] · [Timber properties][s8] · [Strength values][s9] · [Harvest eligibility][s10] · [Player mining dispatch][s11]

All four forms are **axis-oriented full cubes**: clicking a top or bottom face gives a vertical axis; clicking a side gives that horizontal axis. They remain in place without a supporting block and have no waterlogged state. Wood and hyphae retain the same axis property as logs and stems. [Pillar placement and state][s12] · [Default support and collision][s13]

### Stripping

Use an unbroken axe on an ordinary log, wood, stem, or hyphae block to change it to its stripped partner. This preserves the axis, requests **one point of axe wear**, and creates no bark item. The map contains all 22 ordinary-to-stripped pairs in this guide. If an offhand shield takes priority, use secondary interaction, normally sneaking. [Axes and Hoes](../mechanics/AxesAndHoes.md#stripping-wood-and-bamboo) covers the shared tool interaction and [Durability](../mechanics/Durability.md) covers MattMC's retained broken tools. [Stripping map and interaction][s14] · [Axis retention][s15] · [Broken item-use guard][s16]

### Crafting choices

For each non-oak row above, put **four identical ordinary logs or stems in a 2 × 2 square** to make **three corresponding wood or hyphae blocks**. The parallel stripped recipe requires four identical stripped logs or stems and produces three stripped wood or hyphae. Ordinary and stripped inputs cannot be mixed. [Oak Log](../items/OakLog.md#crafting-conversions) holds the two oak layouts. Every family's two recipe files are linked in the evidence table below.

All four forms in a row are accepted by that family's plank recipe. Follow [Wood Construction's material table](WoodConstruction.md#planks-and-materials) for those recipes. Making wood or hyphae first spends four input blocks for three, so it reduces the amount left for planks and, for burnable families, direct fuel. Stripping a placed block preserves its block count. [Oak ingredient family][s17] · [Crimson ingredient family][s18] · [Stripping callback][s19]

## Mangrove Roots

**`minecraft:mangrove_roots`** is a full-collision root block. It uses an **axe** for faster mining, needs no correct-tool tier, and drops **one Mangrove Roots** item without requiring Silk Touch. Hardness and blast resistance are **0.7**. It has no facing or axis and does not need an ongoing supporting block. [Root registration][s20] · [Roots loot][s21] · [Axe tag][s22] · [Default cube and support][s23]

Roots can hold **source water**: placement in a source-water cell sets waterlogged, and a Water Bucket can fill a dry block. An empty bucket can recover that water. The placement test is the exact source-water fluid type, so flowing water is not the same input. Waterlogging does not turn roots into Muddy Mangrove Roots. The checked muddy-root recipe is described below. [Root waterlogging][s24] · [Waterlogging and bucket pickup][s25]

Neither ordinary nor muddy roots appears in the axe stripping map. Ordinary roots also are **not** a log-tag source for keeping natural leaves alive, and are not a plank or charcoal ingredient. [Complete stripping map][s26] · [Block log tag][s27] · [Mangrove log inputs][s28] · [Charcoal inputs][s29]

## Muddy Mangrove Roots

**`minecraft:muddy_mangrove_roots`** is the axis-oriented, non-waterloggable form. Craft **one Mud + one Mangrove Roots**, anywhere in a crafting grid, to make **one Muddy Mangrove Roots**. This recipe uses exact items, not a log or dirt tag. A **shovel** is its efficient tool. It drops **one matching block** even without a correct-tool tier, with no Silk Touch or Fortune alternative. It has **hardness 0.7 and blast resistance 0.7**, remains unsupported, and takes its axis from the clicked face. [Muddy-root recipe][s30] · [Muddy-root loot][s31] · [Shovel mining tag][s32] · [Muddy-root registration][s33] · [Axis placement][s34]

Muddy roots belong to the **dirt block tag**, which makes them valid ground for vegetation whose support callback accepts that tag, including planted Mangrove Propagules. This does not make them a log or a general wood-recipe ingredient. [Tree Leaves](TreeLeaves.md#mangrove-propagules) covers the propagule's collection and planting requirements. [Dirt tag][s35] · [Vegetation support][s36]

## Fire and fuel

| Selected material | Ordinary fire behavior | Default furnace fuel | Charcoal ingredient |
| --- | --- | ---: | --- |
| All 36 Overworld log/wood forms | Flammable, including stripped forms | 300 burn ticks each | Yes |
| All 8 Crimson/Warped stem/hyphae forms | No ordinary fire-consumption entry; no lava-ignition property | None | No |
| Mangrove Roots | Flammable when dry | 300 burn ticks | No |
| Muddy Mangrove Roots | No ordinary fire-consumption entry; no lava-ignition property | None | No |

The fuel table first adds log-tag items, then explicitly removes **non-flammable wood**, including all eight Nether forms. Ordinary roots receive their own fuel entry. These are furnace-fuel rules, not claims that dropped items survive fire or lava. The active fire routine makes a waterlogged root's ignition and burn odds zero. [Fire registration][s37] · [Waterlogged fire checks][s38] · [Fuel values][s39] · [Excluded Nether fuels][s40] · [Server fuel setup][s41] · [Furnace fuel lookup][s42]

Use [Charcoal](../items/Charcoal.md#making-charcoal) for the smelting recipe and processing details. Its burnable-log tag accepts all four forms of the nine Overworld families. Converting those to planks gives more direct furnace fuel, while converting to wood first loses one of every four blocks. [Oak Planks](../items/OakPlanks.md#fuel) explains the fuel arithmetic and interruption caveat. [Burnable-log tag][s43] · [Charcoal recipe][s44]

## Scope and related pages

This is a placed-material guide, not a tree-location or growth-height guide. [Oak](Oak.md) keeps its existing growth coverage. [Pewen](Pewen.md) documents its imported recipe and tool exceptions; the matrices here do not include Ancient or Pewen materials. Bamboo's construction conversion is covered by [Bamboo](Bamboo.md) and [Wood Construction](WoodConstruction.md). [Rooted Dirt](SoilSandAndGravel.md#rooted-dirt-and-hanging-roots) is a separate soil block; Nether decorative roots and wart blocks are outside this timber-and-leaf batch.

Related: [Tree Leaves](TreeLeaves.md) · [Mangrove Roots item](../items/MangroveRoots.md) · [Muddy Mangrove Roots item](../items/MuddyMangroveRoots.md) · [Mining](../mechanics/Mining.md) · [Blocks](Blocks.md)

## Recipe and loot evidence

All 44 timber loot tables return one matching item with an explosion-survival condition. The recipe links prove the exact four-input, three-output layouts; the ingredient-tag and plank-recipe links verify all four forms remain usable for their family's planks.

??? info "Checked timber recipes, ingredient tags, and all matching drops"

    | Family | Wood / hyphae recipes | Plank inputs and recipe | Loot: ordinary log/stem; stripped; ordinary wood/hyphae; stripped |
    | --- | --- | --- | --- |
    | Oak | [Ordinary][s45] · [Stripped][s46] | [Tag][s47] · [Planks][s48] | [1][s49] · [2][s50] · [3][s51] · [4][s52] |
    | Spruce | [Ordinary][s53] · [Stripped][s54] | [Tag][s55] · [Planks][s56] | [1][s57] · [2][s58] · [3][s59] · [4][s60] |
    | Birch | [Ordinary][s61] · [Stripped][s62] | [Tag][s63] · [Planks][s64] | [1][s65] · [2][s66] · [3][s67] · [4][s68] |
    | Jungle | [Ordinary][s69] · [Stripped][s70] | [Tag][s71] · [Planks][s72] | [1][s73] · [2][s74] · [3][s75] · [4][s76] |
    | Acacia | [Ordinary][s77] · [Stripped][s78] | [Tag][s79] · [Planks][s80] | [1][s81] · [2][s82] · [3][s83] · [4][s84] |
    | Cherry | [Ordinary][s85] · [Stripped][s86] | [Tag][s87] · [Planks][s88] | [1][s89] · [2][s90] · [3][s91] · [4][s92] |
    | Dark Oak | [Ordinary][s93] · [Stripped][s94] | [Tag][s95] · [Planks][s96] | [1][s97] · [2][s98] · [3][s99] · [4][s100] |
    | Pale Oak | [Ordinary][s101] · [Stripped][s102] | [Tag][s103] · [Planks][s104] | [1][s105] · [2][s106] · [3][s107] · [4][s108] |
    | Mangrove | [Ordinary][s109] · [Stripped][s110] | [Tag][s111] · [Planks][s112] | [1][s113] · [2][s114] · [3][s115] · [4][s116] |
    | Crimson | [Ordinary][s117] · [Stripped][s118] | [Tag][s119] · [Planks][s120] | [1][s121] · [2][s122] · [3][s123] · [4][s124] |
    | Warped | [Ordinary][s125] · [Stripped][s126] | [Tag][s127] · [Planks][s128] | [1][s129] · [2][s130] · [3][s131] · [4][s132] |

## Sources and verification

Source-reviewed on **2026-10-02** at `688be47ebeaa38beb06130efddf19484a8b81580`. All selected registrations, 23 producing recipes across the timber/root/leaf scope, and 58 loot tables were checked against bundled data, alongside the active placement, stripping, harvesting, fire, and fuel paths. No in-game mining, crafting, fire, or waterlogging test was run. Data packs and server settings can change recipes, tags, loot, and fire behavior.

[s1]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java#L388-L550
[s2]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java#L177-L181
[s3]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java#L5452-L5463
[s4]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java#L5507-L5518
[s5]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/Items.java#L220-L267
[s6]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[s7]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/logs.json
[s8]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java#L7162-L7173
[s9]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[s10]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[s11]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[s12]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L13-L57
[s13]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[s14]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/AxeItem.java#L31-L100
[s15]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/AxeItem.java#L129-L132
[s16]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L370
[s17]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/oak_logs.json
[s18]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/crimson_stems.json
[s19]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/AxeItem.java#L69-L84
[s20]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java#L403-L416
[s21]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/mangrove_roots.json
[s22]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[s23]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[s24]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/MangroveRootsBlock.java#L19-L73
[s25]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L20-L51
[s26]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/AxeItem.java#L31-L55
[s27]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/logs.json
[s28]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/mangrove_logs.json
[s29]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/logs_that_burn.json
[s30]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/muddy_mangrove_roots.json
[s31]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/muddy_mangrove_roots.json
[s32]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[s33]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java#L417-L421
[s34]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L48-L57
[s35]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/dirt.json
[s36]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L24-L49
[s37]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/FireBlock.java#L363-L401
[s38]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/FireBlock.java#L222-L233
[s39]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[s40]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/non_flammable_wood.json
[s41]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L340
[s42]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L264-L266
[s43]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/logs_that_burn.json
[s44]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/smelting/charcoal.json
[s45]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/oak_wood.json
[s46]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/stripped_oak_wood.json
[s47]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/oak_logs.json
[s48]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/oak_planks.json
[s49]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/oak_log.json
[s50]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_oak_log.json
[s51]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/oak_wood.json
[s52]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_oak_wood.json
[s53]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/spruce_wood.json
[s54]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/stripped_spruce_wood.json
[s55]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/spruce_logs.json
[s56]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/spruce_planks.json
[s57]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/spruce_log.json
[s58]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_spruce_log.json
[s59]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/spruce_wood.json
[s60]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_spruce_wood.json
[s61]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/birch_wood.json
[s62]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/stripped_birch_wood.json
[s63]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/birch_logs.json
[s64]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/birch_planks.json
[s65]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/birch_log.json
[s66]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_birch_log.json
[s67]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/birch_wood.json
[s68]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_birch_wood.json
[s69]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/jungle_wood.json
[s70]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/stripped_jungle_wood.json
[s71]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/jungle_logs.json
[s72]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/jungle_planks.json
[s73]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/jungle_log.json
[s74]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_jungle_log.json
[s75]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/jungle_wood.json
[s76]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_jungle_wood.json
[s77]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/acacia_wood.json
[s78]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/stripped_acacia_wood.json
[s79]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/acacia_logs.json
[s80]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/acacia_planks.json
[s81]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/acacia_log.json
[s82]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_acacia_log.json
[s83]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/acacia_wood.json
[s84]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_acacia_wood.json
[s85]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/cherry_wood.json
[s86]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/stripped_cherry_wood.json
[s87]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/cherry_logs.json
[s88]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/cherry_planks.json
[s89]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/cherry_log.json
[s90]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_cherry_log.json
[s91]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/cherry_wood.json
[s92]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_cherry_wood.json
[s93]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/dark_oak_wood.json
[s94]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/stripped_dark_oak_wood.json
[s95]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/dark_oak_logs.json
[s96]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/dark_oak_planks.json
[s97]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_log.json
[s98]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_dark_oak_log.json
[s99]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_wood.json
[s100]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_dark_oak_wood.json
[s101]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/pale_oak_wood.json
[s102]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/stripped_pale_oak_wood.json
[s103]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/pale_oak_logs.json
[s104]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/pale_oak_planks.json
[s105]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_log.json
[s106]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_pale_oak_log.json
[s107]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_wood.json
[s108]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_pale_oak_wood.json
[s109]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/mangrove_wood.json
[s110]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/stripped_mangrove_wood.json
[s111]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/mangrove_logs.json
[s112]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/mangrove_planks.json
[s113]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/mangrove_log.json
[s114]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_mangrove_log.json
[s115]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/mangrove_wood.json
[s116]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_mangrove_wood.json
[s117]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/crimson_hyphae.json
[s118]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/stripped_crimson_hyphae.json
[s119]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/crimson_stems.json
[s120]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/crimson_planks.json
[s121]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/crimson_stem.json
[s122]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_crimson_stem.json
[s123]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/crimson_hyphae.json
[s124]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_crimson_hyphae.json
[s125]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/warped_hyphae.json
[s126]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/stripped_warped_hyphae.json
[s127]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/warped_stems.json
[s128]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/warped_planks.json
[s129]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/warped_stem.json
[s130]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_warped_stem.json
[s131]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/warped_hyphae.json
[s132]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/stripped_warped_hyphae.json
