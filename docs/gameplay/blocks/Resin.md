# Resin

**Resin Clumps** are both placeable surface decorations and the raw material for Resin building blocks. Pack nine clumps into a **Block of Resin**, or smelt individual clumps into **Resin Bricks**, the ingredient items used to make brick masonry. A renewable supply comes from an operating [Creaking Heart](CreakingHeart.md#producing-resin); breaking the Heart spends that source. [Block registrations][blocks] · [Block items][items] · [Brick ingredient][brick-item]

## Registered forms

All IDs below use the `minecraft:` namespace. The similarly named loose **Resin Brick** is an ingredient, not a placeable block. [Registrations][blocks] · [Items][items] · [Ingredient registration][brick-item]

| Form | ID | Role and ordinary mining result |
| --- | --- | --- |
| <span id="resin-clump"></span>[Resin Clump](../items/ResinClump.md) | `resin_clump` | Thin decoration; one item per occupied face |
| <span id="block-of-resin"></span>[Block of Resin](../items/BlockOfResin.md) | `resin_block` | Full storage/building block; one matching block |
| <span id="resin-brick"></span>[Resin Brick](../items/ResinBrick.md) | `resin_brick` | Smelted ingredient; no placed-block form |
| <span id="resin-bricks"></span>[Resin Bricks](../items/ResinBricks.md) | `resin_bricks` | Full masonry block; one matching block |
| <span id="resin-brick-stairs"></span>[Resin Brick Stairs](../items/ResinBrickStairs.md) | `resin_brick_stairs` | Shaped masonry; one matching stair |
| <span id="resin-brick-slab"></span>[Resin Brick Slab](../items/ResinBrickSlab.md) | `resin_brick_slab` | One slab, or two slabs from a double slab |
| <span id="resin-brick-wall"></span>[Resin Brick Wall](../items/ResinBrickWall.md) | `resin_brick_wall` | Connecting masonry; one matching wall |
| <span id="chiseled-resin-bricks"></span>[Chiseled Resin Bricks](../items/ChiseledResinBricks.md) | `chiseled_resin_bricks` | Full decorative masonry block; one matching block |

The mining results assume the [tool and drop conditions below](#mining-and-drops). Resin has **seven registered placed blocks** in this family; the [Creaking Heart](CreakingHeart.md) is a separate functional block with its own guide.

## Getting a starting supply

The checked natural route begins with a **Creaking Heart inside some generated Pale Oak trees** in Pale Garden. Keep it intact, wait for its operating conditions, then cause a qualifying player-attributed hit on its bound Creaking. The Heart attempts to add Resin Clump faces to nearby Pale Oak timber. A clump is a placed block to harvest, not an item automatically delivered by the hit. The [Heart guide](CreakingHeart.md#producing-resin) owns the exact night, log, spawn, cooldown and placement rules. [Generation configuration][heart-tree] · [Hurt caller][creaking-hurt] · [Placement][heart-resin]

Breaking a Heart without Silk Touch is another finite source of clumps; see [Heart collection](CreakingHeart.md#collecting-a-heart-and-its-drops) for Fortune and the natural-only experience reward. Ordinary sapling-grown Pale Oaks use a tree configuration with no Heart decorator, so planting more saplings does not itself produce new Hearts. [Heart loot][loot-creaking_heart] · [Planted-tree selection][grower] · [Planted configuration][grown-tree]

**Woodland Mansion chests** are also a checked loot route: their table has a weighted entry yielding **2–4 Resin Clumps when selected**. This is not a guaranteed amount in every chest. The mansion structure set reaches the mansion definition, its biome tag includes Dark Forest and Pale Garden, and the mansion chest-marker handler assigns this loot table. No mansion was located or looted in-game for this guide. [Structure set][mansion-set] · [Structure][mansion] · [Biomes][mansion-biomes] · [Piece generation][mansion-pieces] · [Chest assignment][mansion-chest] · [Loot entry][mansion-loot]

## Crafting and smelting

| Conversion | Exact input/layout | Output |
| --- | --- | --- |
| Pack clumps | 9 Resin Clumps filling a 3 × 3 crafting grid | **1 Block of Resin** |
| Unpack a block | 1 Block of Resin, shapeless | **9 Resin Clumps** |
| Fire a brick | Smelt 1 Resin Clump in a fueled [Furnace](Furnace.md) | **1 Resin Brick**, **200 ticks**, **0.1 recipe XP** |
| Make masonry | 4 Resin Brick items in a 2 × 2 square | **1 Resin Bricks block** |

Packing needs a [Crafting Table](CraftingTable.md); unpacking and the 2 × 2 brick recipe fit the personal crafting grid. **Block of Resin is not the smelting input.** Unpack it first to fire nine separate clumps into nine brick items. At 20 ticks per second, each uninterrupted smelt takes about ten seconds. Neither a Blast Furnace nor a Smoker accepts the bundled Resin Brick recipe, because its type is smelting. [Packing][craft-resin_block] · [Unpacking][craft-resin_clump] · [Smelting][smelt] · [Masonry recipe][craft-resin_bricks] · [Furnace type][furnace-type] · [Blast Furnace type][blasting-type] · [Smoker type][smoking-type]

One Coal or Charcoal supplies **1,600 default burn ticks**, enough for eight uninterrupted Resin Brick smelts; a ninth needs more fuel. Already-lit fuel burns even if processing stops. The 0.1 XP is a recipe value; use [Furnace experience](Furnace.md#experience-and-troubleshooting) for the actual payout path. None of the Resin forms listed here is a default Furnace fuel. [Fuel definitions][fuel] · [Server fuel initialization][fuel-init] · [Burn loop][furnace-engine]

A Block of Resin also makes a Heart with two specific Pale Oak Logs. See the [Heart crafting layout](CreakingHeart.md#crafting-and-placing-a-heart) rather than substituting Resin Bricks for that ingredient.

## Slabs, stairs, walls and chiseled bricks

These conversions consume **Resin Bricks blocks**, except the chiseled crafting recipe, which consumes **Resin Brick Slabs**. Select the intended result at a [Stonecutter](Stonecutter.md). [Output selection][cut-menu]

| Result | Crafting recipe | Stonecutter recipe |
| --- | --- | --- |
| Resin Brick Slab | 3 Resin Bricks across one row → **6 slabs** | 1 Resin Bricks → **2 slabs** |
| Resin Brick Stairs | 6 Resin Bricks in a three-row stair pattern, **1 / 2 / 3 blocks** → **4 stairs** | 1 Resin Bricks → **1 stair** |
| Resin Brick Wall | 6 Resin Bricks in two full rows → **6 walls** | 1 Resin Bricks → **1 wall** |
| Chiseled Resin Bricks | 2 Resin Brick Slabs in a vertical column → **1 block** | 1 Resin Bricks → **1 block** |

Stonecutting saves material for stairs. Slabs, walls and chiseled blocks have equivalent material yields through the listed routes, although crafting slabs first makes the chiseled route a batch process. The two-slab recipe fits the personal grid; the other shaped masonry recipes need a Crafting Table. There is no bundled reverse recipe from finished Resin Brick masonry to loose Resin Bricks or Resin Clumps. [Slab crafting][craft-resin_brick_slab] · [Stair crafting][craft-resin_brick_stairs] · [Wall crafting][craft-resin_brick_wall] · [Chiseled crafting][craft-chiseled_resin_bricks] · [Slab cutting][cut-resin_brick_slab] · [Stair cutting][cut-resin_brick_stairs] · [Wall cutting][cut-resin_brick_wall] · [Chiseled cutting][cut-chiseled_resin_bricks]

## Placing clumps and building with Resin

Resin Clumps occupy a block space as **up to six independently attached faces**: floor, ceiling and the four sides. Place a clump against a neighboring block with a full supporting or collision face; this manual placement is not restricted to Pale Oak. Adding another clump to a vacant supported face in the same space adds that face, rather than creating a second block. A face that is already occupied cannot accept another clump. The decoration has no collision, so it does not make a walkable platform. [Registration][blocks] · [Face support and vacancy][clump-placement] · [Attachment shape test][clump-face]

Each occupied face depends on its own neighbor. Removing support removes that face; when none remain, the clump block becomes air. Harvest the clump itself if you want the ordinary face-count loot instead of relying on support changes. Clumps can be **waterlogged**: placement into source water preserves it, and the inherited water-container behavior also supports filling or draining a placed clump. The Heart's automatic spread specifically accepts air, a source Water block, or an existing clump with a vacant face; it does not create a fresh clump in flowing water. [Support updates][clump-support] · [Manual placement in water][clump-placement] · [Water-container behavior][water] · [Heart spread][heart-resin]

The Block of Resin, Resin Bricks and Chiseled Resin Bricks are ordinary full blocks with no waterlogged state or continuing support requirement. Slabs, stairs and walls use their shared shape behavior: slabs can be top, bottom or double; stairs change corners beside compatible stairs; walls connect to compatible neighboring blocks. **Single slabs, stairs and walls can waterlog; double slabs cannot.** Combining two matching slabs produces a double slab and clears its waterlogged state. [Default shape/survival][full-shape] · [Registrations][blocks] · [Slabs][slab] · [Stairs][stair] · [Walls][wall]

For state-aware building or commands, the clump has six face booleans plus `waterlogged`; slabs have `type` and `waterlogged`; stairs have `facing`, `half`, `shape` and `waterlogged`; walls have `up`, four side-height properties and `waterlogged`. These are states of their existing IDs, not extra items. [Clump states][clump-state] · [Slab states][slab] · [Stair states][stair-states] · [Wall states][wall-states]

## Mining and drops

**Resin Clumps and Blocks of Resin have zero default hardness and zero blast resistance**, and neither requires a tool for drops. Break a clump directly to recover **one Resin Clump item for each occupied face**, up to six from one block space. A Block of Resin drops itself and can then be unpacked. Neither loot table uses Silk Touch or Fortune. [Registrations][blocks] · [Property defaults][default-properties] · [Clump loot][loot-resin_clump] · [Block loot][loot-resin_block] · [Tool gate][player-gate]

**Resin Bricks, their stairs/slabs/walls, and Chiseled Resin Bricks have 1.5 hardness and 6 blast resistance.** Use an **unbroken pickaxe** to collect them; a Wooden Pickaxe is sufficient under the bundled tier tags. Hand mining, other tool types, or a broken pickaxe fail the correct-tool drop gate. Their normal loot is one matching item, except a double slab returns **two slab items**. Silk Touch is unnecessary and Fortune adds nothing. Explosion survival or decay conditions mean blast recovery is not guaranteed. [Properties][blocks] · [Stair inheritance][stair-copy] · [Copied tool/strength properties][legacy-copy] · [Pickaxe membership][pickaxe] · [Wood restrictions][wood-tier] · [Stone requirements][needs-stone] · [Iron requirements][needs-iron] · [Diamond requirements][needs-diamond] · [Tool rules][materials] · [Broken tools][broken] · [Mining dispatch][mining] · [Bricks loot][loot-resin_bricks] · [Stair loot][loot-resin_brick_stairs] · [Slab loot][loot-resin_brick_slab] · [Wall loot][loot-resin_brick_wall] · [Chiseled loot][loot-chiseled_resin_bricks]

## Resin Brick armor trim

The loose **Resin Brick** item also supplies the Resin trim material at a [Smithing Table](SmithingTable.md). Use it with a supported armor piece and an armor-trim template; Resin Clumps and Blocks of Resin are not the listed trim ingredient. The recipe's addition tag includes Resin Brick, and the item supplies its Resin trim material. [Item registration][brick-item] · [Trim ingredients][trim-tag] · [Example trim recipe][trim-recipe] · [Applied trim][trim-apply]

Related: [Creaking Heart](CreakingHeart.md) · [Creaking mob](../mobs/Creaking.md) · [Pale Oak timber](TreeLogsAndRoots.md#pale_oak-timber) · [Pale Oak saplings](SaplingsAndAzaleas.md#pale-oak-sapling) · [Furnace](Furnace.md) · [Stonecutter](Stonecutter.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf1c134b3f9ff634490e448fe26c335b90f82227`. Checked all seven block registrations and item forms, loose Resin Brick, bundled recipes and expanded ingredient tags, all eight relevant block-loot tables including the Heart, mining/tier/fuel tags, the mansion chest route, and active clump placement, shape, water and Resin-production callbacks. The Heart guide owns the complete natural-tree and activation audit. No gameplay mining, smelting, stonecutting, generation, placement, water or farming test was run. Data packs and later builds can change recipes, tags and loot.

[blocks]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L2416-L2489
[items]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L561-L567
[brick-item]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L2119
[heart-tree]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/configured_feature/pale_oak_creaking.json
[creaking-hurt]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L153-L185
[heart-resin]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L247-L280
[loot-creaking_heart]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/creaking_heart.json
[grower]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java#L65
[grown-tree]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/configured_feature/pale_oak_bonemeal.json
[mansion-set]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/structure_set/woodland_mansions.json
[mansion]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/structure/mansion.json
[mansion-biomes]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/woodland_mansion.json
[mansion-pieces]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionStructure.java#L27-L42
[mansion-chest]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L1205-L1231
[mansion-loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/chests/woodland_mansion.json#L211-L232
[craft-resin_block]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/resin_block.json
[craft-resin_clump]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/resin_clump.json
[smelt]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/smelting/resin_brick.json
[craft-resin_bricks]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/resin_bricks.json
[furnace-type]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java#L11-L16
[blasting-type]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/BlastFurnaceBlockEntity.java#L11-L16
[smoking-type]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/SmokerBlockEntity.java#L11-L16
[fuel]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L26-L108
[fuel-init]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/MinecraftServer.java#L340
[furnace-engine]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L236-L273
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L136-L164
[craft-resin_brick_slab]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/resin_brick_slab.json
[craft-resin_brick_stairs]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/resin_brick_stairs.json
[craft-resin_brick_wall]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/resin_brick_wall.json
[craft-chiseled_resin_bricks]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/chiseled_resin_bricks.json
[cut-resin_brick_slab]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/stonecutting/resin_brick_slab_from_resin_bricks_stonecutting.json
[cut-resin_brick_stairs]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/stonecutting/resin_brick_stairs_from_resin_bricks_stonecutting.json
[cut-resin_brick_wall]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/stonecutting/resin_brick_wall_from_resin_bricks_stonecutting.json
[cut-chiseled_resin_bricks]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_resin_bricks_from_resin_bricks_stonecutting.json
[clump-placement]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L175-L218
[clump-face]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L246-L278
[clump-support]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L124-L173
[water]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[full-shape]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[slab]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L31-L115
[stair]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L151
[wall]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/WallBlock.java#L105-L153
[clump-state]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L109-L122
[stair-states]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/StairBlock.java#L213-L219
[wall-states]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/WallBlock.java#L242-L254
[default-properties]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1022
[loot-resin_clump]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/resin_clump.json
[loot-resin_block]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/resin_block.json
[player-gate]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[stair-copy]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L7255-L7257
[legacy-copy]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1061-L1089
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-tier]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[needs-stone]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[needs-iron]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[needs-diamond]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[materials]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[broken]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[mining]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[loot-resin_bricks]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/resin_bricks.json
[loot-resin_brick_stairs]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/resin_brick_stairs.json
[loot-resin_brick_slab]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/resin_brick_slab.json
[loot-resin_brick_wall]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/resin_brick_wall.json
[loot-chiseled_resin_bricks]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/chiseled_resin_bricks.json
[trim-tag]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/item/trim_materials.json
[trim-recipe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/smithing/sentry_armor_trim_smithing_template_smithing_trim.json
[trim-apply]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/crafting/SmithingTrimRecipe.java#L39-L58
