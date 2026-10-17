# Dripstone

Use **Pointed Dripstone** for cave spikes, renewable growth, and controlled dripping. Use **Dripstone Blocks** as solid building material and the specific support needed for natural pointed-dripstone growth. Ordinary placement, growth, cauldron filling, and Mud drying have different requirements; a decorative drip is not proof that all four work. [Block registrations][blocks] · [Placement][placement] · [Growth requirements][growth-source] · [Drip source lookup][fluid]

## Pointed Dripstone

**`minecraft:pointed_dripstone`** is the single block and item ID for both upward **stalagmites** and downward **stalactites**. The different shapes are states of that same block, not separate items. Its default state is an upward, dry, unmerged tip. [Item registration][point-item] · [State definition][states] · [Serialized property names][property-names]

| Property | Exact values | What it changes |
| --- | --- | --- |
| `vertical_direction` | `up`, `down` | The direction the spike points |
| `thickness` | `tip`, `tip_merge`, `frustum`, `middle`, `base` | Tip, joined tip, taper, middle, or base shape |
| `waterlogged` | `false`, `true` | Whether the segment contains source Water |

These give **20 registered state combinations**. Neighbor updates select the appropriate connected shapes, so the table is not a promise that every arbitrary combination remains unchanged after placement. [State values][states] · [Thickness names][thickness-values] · [Shape updates][update] · [Thickness calculation][support]

Place it beneath a block with a **sturdy underside**, above a block with a **sturdy upper face**, or extend another pointed-dripstone segment pointing the same way. It does not need a Dripstone Block merely to stay attached. Placement first tries the vertical direction opposite the player's vertical look direction, then the other direction if only that support works; it cannot point sideways. [Placement direction][placement] · [Support and fallback][support]

Touching opposite-facing tips normally join as `tip_merge`. **Secondary-use placement** can keep two previously unmerged tips separate; it does not override a neighbor that is already merged. Adding or removing segments recalculates taper and base shapes. The collision shape is narrow and horizontally offset, not a full cube. [Merge choice and thickness][support] · [Secondary-use flag][placement] · [Collision shapes][shape]

A source Water block at the placement position makes the new segment waterlogged. The shared waterlogging handler also accepts Water Bucket filling and lets an empty Bucket recover that water. Flowing-water placement is not the same source-fluid test. A waterlogged segment holds source Water, but a **waterlogged downward tip cannot perform ordinary cauldron dripping or natural growth**. [Placement and fluid state][placement] · [Bucket waterlogging][waterlogging] · [Dry-tip requirement][drip-tip] · [Growth caller][growth]

## Dripstone Block

**`minecraft:dripstone_block`** is the ordinary full block and matching inventory item. It has **one state, with no facing, thickness, or waterlogged property**. It remains in place when support is removed and has no random-growth callback of its own. [Registration][blocks] · [Ordinary block constructor][ordinary-block] · [Item][full-item] · [Default shape and survival][default-block]

Craft **four Pointed Dripstone in a 2 × 2 square** into **one Dripstone Block**, including in the personal crafting grid. The checked bundled recipes contain no reverse conversion and no recipe for Pointed Dripstone. Keep some pointed pieces to seed a growth setup. [Exact recipe][recipe]

## Finding a starting supply

**Overworld Dripstone Caves** are a checked natural source of both blocks. The Normal preset uses the Overworld biome selector, which includes Dripstone Caves. That biome actively lists large dripstone, dripstone clusters, and pointed-dripstone placements; its generator invokes those placed features. [Normal preset][normal-overworld] · [Preset dispatch][overworld-preset] · [Biome builder][overworld-builder] · [Cave selection][cave-select] · [Biome features][cave-biome] · [Active decoration call][worldgen-call]

The large feature builds full Dripstone Blocks; clusters and the small pointed feature can build Pointed Dripstone and surrounding full-block material. Their placements are biome-filtered and sample heights from the world bottom through **Y=256**, but suitable cave space and supports still decide success. This range is a placement search, not a promise of dripstone at every height. The generation support tag expands to **Stone, Granite, Diorite, Andesite, Tuff, and Deepslate**, separately from the more permissive player-placement support rule. [Large placement][placed-large] · [Cluster placement][placed-cluster] · [Pointed placement][placed-pointed] · [Registered features][feature-register] · [Large output][feature-large] · [Cluster output][feature-cluster] · [Small output][feature-pointed] · [Generation supports][worldgen-utils] · [Replacement tag][worldgen-tag] · [Expanded stone members][base-stone-tag]

MattMC's **[Dry Midlands](../biomes/DryMidlands.md#terrain-and-decorations)** in [Primordial Caves](../dimensions/PrimordialCaves.md) also has biome-linked red-sand geodes containing full Dripstone Blocks and a separate dripstone-patch feature. The patch uses **existing Dripstone Blocks as its only replaceable ground**; it does not turn arbitrary sand or stone into dripstone. Its vegetation selector can choose the pointed feature or a full-block pile. The red-sand geode also protects Air, Stone, Deepslate, Sandstone, and other tagged blocks from replacement, so its configured filling is not a promise of a solid-stone deposit. These are connected generation routes, with terrain-dependent success, not a measured supply rate. [Normal-preset selection][dry-preset] · [Biome entries][dry-biome] · [Geode placement][geode-placed] · [Geode material][geode-config] · [Active geode placement][geode-call] · [Patch placement][dry-placed] · [Patch configuration][dry-config] · [Restricted ground tag][dry-tag] · [Ground check][patch-ground] · [Vegetation choice][dry-selector] · [Protected geode blocks][geode-protected] · [Nested protected stone][primordial-protected] · [Replacement gate][replace-gate]

**Trial Chamber hallway pieces** are another checked source of Pointed Dripstone. The `encounter_4` and `trapped_staircase` templates contain upward pointed segments and are entries in the structure's connected hallway pool. Chamber generation selects and fits pieces, so finding a Trial Chamber does not guarantee either layout or a fixed harvest. These are placed blocks, not a chest-loot reward. [Structure and start pool][trial-structure] · [Structure-set entry][trial-set] · [Jigsaw generation caller][trial-jigsaw] · [Hallway entries][trial-pool] · [Encounter template, binary NBT][trial-encounter] · [Staircase template, binary NBT][trial-staircase]

Trading offers another starting route:

| Seller and condition | Configured base exchange | Uses when that offer is selected |
| --- | --- | ---: |
| [Wandering Trader](../mobs/WanderingTrader.md) with the matching offer | **1 Emerald → 2 Pointed Dripstone** | **5** |
| Level-3 **Mason** [Villager](../mobs/Villager.md#professions-and-job-sites) with the matching offer | **1 Emerald → 4 Dripstone Blocks** | **16** before restocking |

The Wandering Trader selects five entries from the large pool containing Pointed Dripstone. The Mason selects two entries from its seven-entry level-3 pool, so neither material is guaranteed on every relevant trader. The listed cost is the configured base price; the displayed merchant offer is authoritative. See [Trading](../trading/Trading.md) for shared trade behavior. [Wandering listing][wandering] · [Wandering caller][wandering-call] · [Mason pool][mason] · [Villager caller][mason-call] · [Random selection][offer-selection] · [Offer construction][offer-values]

Both items are also listed in Creative. Natural growth below renews Pointed Dripstone once a starter and suitable support are available; crafting the harvest renews full Dripstone Blocks. [Creative entries][creative] · [Growth callback][growth] · [Crafting][recipe]

## Mining, drops, and breaking supports

| Block | Hardness / blast resistance | Ordinary Survival collection |
| --- | --- | --- |
| Pointed Dripstone | **1.5 / 3** | **1 Pointed Dripstone per segment**; hand collection works, and an unbroken pickaxe is faster |
| Dripstone Block | **1.5 / 1** | **1 Dripstone Block**, requiring a correct, unbroken pickaxe; Wood is sufficient |

Both are pickaxe-mineable, but **only the full block requires a correct tool for its ordinary item drop**. Neither belongs to the bundled Stone-, Iron-, or Diamond-tier requirement tags. Silk Touch is unnecessary and Fortune does not increase either loot result. Explosion recovery is conditional, and ordinary block-item spawning requires `doTileDrops`. See [Mining](../mechanics/Mining.md) for the shared rules, including broken tools. [Properties][blocks] · [Pickaxe tag][pickaxe-tag] · [Wood restrictions][wood-tag] · [Stone requirement tag][stone-tier] · [Iron requirement tag][iron-tier] · [Diamond requirement tag][diamond-tier] · [Tool rules][material] · [Player gate][harvest] · [Broken-tool gate][broken] · [Active harvest][harvest-call] · [Pointed loot][loot-pointed] · [Full-block loot][loot-full] · [Item spawning][drops]

Removing a stalagmite's support schedules its unsupported segment to break after **1 game tick**. Removing a stalactite's support schedules falling after **2 game ticks**; the falling callback takes the downward chain through its tip, including a merged tip. Breaking a middle segment can therefore bring down the hanging portion below it. These delays are about 0.05 and 0.1 seconds at 20 ticks per second, after the relevant support update. Stand clear before harvesting overhead material. [Support updates][update] · [Scheduled action][fall-tick] · [Falling chain][fall-spawn]

Falling pieces lose their waterlogged state and leave their original water behind. A falling downward piece usually cannot survive as a placed block on an open floor, so it breaks and returns an item through the falling-entity path, subject to `doEntityDrops`. The landing code can place a piece only when its normal support and replacement checks pass; do not assume it becomes an upward stalagmite. [Falling conversion][fall-water] · [Placement, break, and item recovery][fall-land]

A **thrown Trident** can break a struck segment when its speed exceeds **0.6 blocks per game tick**, its interaction permissions allow it, and `projectilesCanBreakBlocks` is enabled. Other projectile classes do not pass this block's Trident check. A piston push treats Pointed Dripstone as a block to destroy and drop rather than move intact. [Trident callback][projectile] · [Permissions and game rule][projectile-permission] · [Trident impact tag][impact-tag] · [Piston registration][blocks] · [Piston resolution][piston-resolve] · [Piston drops][piston-drops]

## Falling and landing damage

**Falling onto an upward, unmerged `tip`** calls fall damage using the recorded fall distance **plus 2.5**, with a **2× multiplier**. Other thicknesses, including merged tips, use the ordinary fall callback. The effect comes from landing after a fall, not a separate cactus-like contact-damage routine. [Pointed landing][landing-damage] · [Active landing call][fall-call] · [Ordinary fall][normal-fall]

For a living entity with the default **3-block safe-fall distance** and **1× fall-damage attribute**, a recorded distance of exactly **3 blocks** gives **5 health points (2½ hearts) before applicable protection and other handling**. The calculation rounds down after applying those attributes; use the recorded fall distance, not just the difference between two block coordinates. Fall-immune entities and player flight/impulse handling can change the result. Stalagmite damage is tagged as fall damage and bypasses ordinary armor, so the player's `fallDamage` rule also applies. [Calculation][living-fall] · [Default safe distance][safe-fall] · [Default attribute][fall-multiplier] · [Player handling][player-fall] · [Fall tag][damage-fall-tag] · [Armor-bypass tag][damage-armor-tag] · [Player game rule][fall-rule]

**A falling stalactite hitting an entity is different.** Only the falling tip is assigned the damaging behavior for that detached chain. Let **n** be the number of detached segments from the falling start through that tip and **d** its recorded fall distance. The raw damage is capped at **40 health points (20 hearts)** and otherwise uses `floor(ceil(d − 1) × max(n, 6))`, with no damage for a negative distance factor. **This snapshot uses a minimum multiplier of 6, not a six-segment maximum**: a one-segment tip falling a recorded 2 blocks supplies 6 raw health points; one falling 3 blocks supplies 12. These are source calculations before defenses, not tested trap yields. [Exact size calculation][fall-spawn] · [Fall-distance calculation and target filter][fall-damage]

The falling-hit target filter excludes Creative/Spectator players and targets living entities still alive. Equipment and normal damage handling can reduce the received amount: falling-stalactite damage participates in the helmet-damage path, which multiplies damage by **0.75** when the head slot is occupied. It is not the same damage type as landing on a stalagmite. [Hit targets][fall-damage] · [Helmet tag][damage-helmet-tag] · [Helmet handling][helmet]

## Growing more Pointed Dripstone

For a compact growth setup, stack these vertically:

| Position | Required block |
| --- | --- |
| Top | **Water source block** |
| Middle | **Dripstone Block** |
| Bottom | **Downward Pointed Dripstone**, ending in a dry, unmerged tip |

Keep dry air immediately below the tip. Leave a clear vertical gap and sturdy floor below if you also want an upward stalagmite. The water must be an actual **Water block with source fluid**; a waterlogged support, flowing Water, Lava, or Mud does not satisfy this natural-growth test. No light or biome check is added by this growth callback. [Source requirement][growth-source] · [Growth entry and floor search][growth] · [Tip space][drip-tip]

Only the **topmost downward pointed segment** starts this process. On each of its random ticks, an approximately **1.1377778%** growth roll calls the setup checks. A passing setup then chooses **50/50** between extending the stalactite downward and trying to grow a stalagmite below; the chosen branch can still fail. These are random-tick probabilities, not chances every game tick or a fixed number of minutes. [Random roll][random] · [Root definition][root] · [Branch choice][growth] · [Server sampling][random-sampling]

The hanging tip must be at the root or within **six blocks below it**, so an existing stalactite of up to **7 segments** is considered. A successful downward step can make it **8 segments long**; then the next root lookup cannot reach that new tip. This is the actual search-and-growth boundary, not a limit on manual construction. Harvest lower growth while preserving the supported starter. [Tip-search call][growth] · [Tip lookup][find-tip] · [Exclusive search bound][search]

The upward branch searches **1–10 blocks below the hanging tip**, stops at fluid or an obstructed drip path, and either extends an eligible upward tip or starts a new one on suitable support. Its growing tip also needs dry air or an opposing unmerged tip in front of it. Meeting opposite tips merge; merged tips do not qualify for further ordinary growth or cauldron dripping. The source Water is not consumed. [Stalagmite search, growth, and merging][growth] · [Tip conditions][drip-tip]

**Bone Meal does not grow either dripstone block** in this implementation. Pointed Dripstone implements falling and waterlogging, not the Bone Meal growth interface; the full block is an ordinary Block. Natural growth requires random ticks in the relevant chunk. `randomTickSpeed=0` stops new growth and drip-transfer rolls. [Pointed interfaces][states] · [Full-block type][ordinary-block] · [Bone Meal dispatch][bonemeal] · [Active tick selection][random-dispatch] · [Random-tick loop][random-sampling] · [Game-rule default][rules]

## Dripping into Cauldrons

Use the detailed **[Cauldrons filling guide](Cauldrons.md#filling-from-pointed-dripstone)** for receiving contents, timing, and collection. The compact setup is source Water or Lava **above a sturdy support**, downward Pointed Dripstone below it, and a compatible cauldron directly under a **dry, unmerged tip**. The support may be a suitable ordinary block; it need not be a Dripstone Block. Decorative particles may appear without a usable source and do not certify a functioning collector. [Source and particle rules][fluid] · [Particle callback][particles] · [Support and tip][support] · [Dry-tip condition][drip-tip]

The cauldron must be **1–10 blocks below the tip**, with a dry, unobstructed central path. The receiving callback also needs to find the support within **10 blocks above the tip**. Keep the stalactite short: unlike Mud drying, an 11-segment hanging chain cannot pass that support lookup on the scheduled cauldron recheck. Source Water and Lava are read as fluids above the support; flowing variants do not qualify. Water adds one level to Empty/Water, up to full; Lava fills Empty in one transfer. The ordinary source is not consumed. [Fluid lookup and ranges][fluid] · [Search and obstruction][search] · [Scheduled recheck][cauldron-check] · [Empty receiving][cauldron-empty] · [Water receiving][cauldron-water] · [Water increment][cauldron-layer]

The root's random-tick transfer chances are **17.578125% for Water** and **5.859375% for Lava**. A successful ordinary transfer schedules the cauldron after **50 + tip-to-cauldron distance game ticks**, or **51–60 ticks** for the accepted range. This is the delay after a random successful attempt, not the overall production interval. [Rolls and scheduling][random]

## Mud drying is a separate use

For the exact build and conversion limits, follow **[Turning Mud into Clay](ClayAndBricks.md#turning-mud-into-clay)**. Put **Mud above a separate sturdy support**, with downward Pointed Dripstone beneath that support. Attaching the spike directly to Mud does not target that supporting Mud. This path needs no cauldron or Water source and is disabled in ultra-warm dimensions. [Mud source lookup][fluid] · [Conversion branch][random]

Mud drying accepts an unmerged hanging tip at the root or within ten blocks below it, even if that tip is waterlogged. It does not run the ordinary cauldron path or require open space below. Do not reuse the natural-growth requirements for this setup; the [Clay guide](ClayAndBricks.md#turning-mud-into-clay) owns the full recipe and harvesting instructions. [Separate branch][random] · [Tip lookup][find-tip] · [Search bound][search]

Related: [Pointed Dripstone item](../items/PointedDripstone.md) · [Dripstone Block item](../items/DripstoneBlock.md) · [Cauldrons](Cauldrons.md) · [Clay and Bricks](ClayAndBricks.md) · [Mining](../mechanics/Mining.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd`. Checked the exact two block/item registrations and state map, placement/support/waterlogging, complete bundled recipe and loot references for these IDs, recursively expanded block/item mining and relevant generation tags, active random/scheduled tick callers, falling and landing damage, Bone Meal dispatch, trader selection, the described world-preset → biome → placement → feature chains, and the Trial Chamber start/corridor/hallway pool connections and two pointed-dripstone NBT templates. The bundled large, cluster, and pointed feature configurations were checked alongside their implementations. [Large configuration][config-large] · [Cluster configuration][config-cluster] · [Pointed configuration][config-pointed]

This is source and data review, **not an in-game test**. No placement, mining, fall-damage, timed-growth, cauldron, Mud conversion, trading, or natural-generation test was run. Generation entries do not establish a seed-specific find or yield, and data packs can change tags, recipes, loot, and world generation.

[blocks]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L6521-L6545
[placement]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L235-L259
[growth-source]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L559-L565
[fluid]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L523-L573
[point-item]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/Items.java#L2519-L2519
[states]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L46-L98
[property-names]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/state/properties/BlockStateProperties.java#L141-L142
[update]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L100-L135
[support]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L425-L499
[shape]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L261-L281
[waterlogging]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L50
[drip-tip]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L459-L480
[growth]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L318-L395
[ordinary-block]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L7274-L7293
[full-item]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/Items.java#L110-L110
[default-block]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[recipe]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/recipe/crafting/dripstone_block.json#L1-L15
[normal-overworld]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L1-L13
[overworld-preset]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L80
[overworld-builder]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L106-L110
[cave-select]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L815-L821
[cave-biome]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/biome/dripstone_caves.json#L32-L82
[worldgen-call]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L380
[placed-large]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/placed_feature/large_dripstone.json#L1-L31
[placed-cluster]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/placed_feature/dripstone_cluster.json#L1-L31
[placed-pointed]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/placed_feature/pointed_dripstone.json#L1-L56
[feature-register]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L147-L155
[feature-large]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/levelgen/feature/LargeDripstoneFeature.java#L157-L183
[feature-cluster]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/levelgen/feature/DripstoneClusterFeature.java#L120-L143
[feature-pointed]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/levelgen/feature/PointedDripstoneFeature.java#L16-L46
[worldgen-utils]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/levelgen/feature/DripstoneUtils.java#L76-L116
[worldgen-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/dripstone_replaceable_blocks.json#L1-L5
[base-stone-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/base_stone_overworld.json#L1-L10
[dry-preset]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L35-L80
[dry-biome]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/biome/dry_midlands.json#L32-L45
[geode-placed]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/placed_feature/geode/red_sand.json#L1-L21
[geode-config]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/configured_feature/geode/red_sand.json#L1-L42
[geode-call]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/levelgen/feature/GeodeFeature.java#L96-L139
[dry-placed]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/placed_feature/veg_patch/dripstone.json#L1-L12
[dry-config]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/configured_feature/veg_patch/dripstone.json#L1-L32
[dry-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/replaceable/dripstone.json#L1-L5
[patch-ground]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/levelgen/feature/VegetationPatchFeature.java#L125-L147
[dry-selector]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/configured_feature/vegetation/dripstone.json#L1-L18
[wandering]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L811-L828
[wandering-call]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L141
[mason]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L647-L668
[mason-call]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/npc/Villager.java#L818-L836
[offer-selection]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L232
[offer-values]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1430-L1478
[creative]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L785-L795
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L271-L280
[wood-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json#L1-L7
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json#L1-L82
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json#L1-L16
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json#L1-L9
[material]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[harvest]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[harvest-call]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[loot-pointed]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/loot_table/blocks/pointed_dripstone.json#L1-L21
[loot-full]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/loot_table/blocks/dripstone_block.json#L1-L21
[drops]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Block.java#L410-L420
[fall-tick]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L172-L179
[fall-spawn]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L288-L316
[fall-water]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L92-L103
[fall-land]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L172-L234
[projectile]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L137-L149
[projectile-permission]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L336-L346
[impact-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/entity_type/impact_projectiles.json#L1-L15
[piston-resolve]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/piston/PistonStructureResolver.java#L37-L47
[piston-drops]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L282-L299
[landing-damage]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L151-L158
[fall-call]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/Entity.java#L1398-L1416
[normal-fall]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Block.java#L456-L458
[living-fall]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1710-L1735
[safe-fall]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L72-L74
[fall-multiplier]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L36-L39
[player-fall]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/player/Player.java#L1342-L1369
[damage-fall-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/damage_type/is_fall.json#L1-L7
[damage-armor-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json#L1-L23
[fall-rule]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/player/Player.java#L706-L718
[fall-damage]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L247-L275
[damage-helmet-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/damage_type/damages_helmet.json#L1-L7
[helmet]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1172-L1175
[random]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L181-L233
[root]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L502-L512
[random-sampling]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/server/level/ServerLevel.java#L487-L507
[find-tip]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L412-L423
[search]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L575-L612
[bonemeal]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L78
[random-dispatch]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L370-L404
[rules]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/GameRules.java#L57-L83
[particles]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L160-L170
[cauldron-check]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/AbstractCauldronBlock.java#L85-L101
[cauldron-empty]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/CauldronBlock.java#L54-L72
[cauldron-water]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/LayeredCauldronBlock.java#L51-L65
[cauldron-layer]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/LayeredCauldronBlock.java#L131-L139
[config-large]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/configured_feature/large_dripstone.json#L1-L34
[config-cluster]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/configured_feature/dripstone_cluster.json#L1-L38
[config-pointed]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/configured_feature/pointed_dripstone.json#L1-L73
[thickness-values]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/state/properties/DripstoneThickness.java#L5-L10
[trial-structure]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/structure/trial_chambers.json#L1-L147
[trial-set]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/structure_set/trial_chambers.json#L1-L14
[trial-pool]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/hallway.json#L223-L267
[trial-jigsaw]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java#L134-L153
[trial-encounter]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/structure/trial_chambers/hallway/encounter_4.nbt
[trial-staircase]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/structure/trial_chambers/hallway/trapped_staircase.nbt
[geode-protected]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/cannot_replace/red_sand_geode.json#L1-L22
[primordial-protected]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/base_stone_primordial.json#L1-L16
[replace-gate]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L175-L182
