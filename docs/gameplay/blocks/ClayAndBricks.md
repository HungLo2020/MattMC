# Clay and Bricks

Turn **Clay** into a supply of **Clay Balls**, smelt those balls into **Bricks**, and use the bricks for masonry or [Flower Pots](FlowerPot.md). Keep the block and ingredient forms distinct: a Clay block follows the separate [Terracotta smelting route](Terracotta.md#crafting-and-smelting). [Registrations][clay-block] · [Ingredients][ingredients] · [Brick smelting][brick-recipe]

| Form | ID | Main checked role |
| --- | --- | --- |
| [Clay block](../items/Clay.md) | `minecraft:clay` | Collectable terrain/building block; Terracotta input |
| [Clay Ball](../items/ClayBall.md) | `minecraft:clay_ball` | Craft Clay blocks or smelt Bricks |
| [Brick](../items/Brick.md) | `minecraft:brick` | Ingredient for Bricks and Flower Pots |
| [Bricks block](../items/Bricks.md) | `minecraft:bricks` | Full building block and input for shaped masonry |

Clay Ball and Brick are ordinary ingredient items, not placeable blocks. Clay and Bricks have registered block items. [Ingredient registrations][ingredients] · [Clay item][clay-item] · [Bricks item][bricks-item]

## Finding and collecting Clay

**River** is one confirmed natural source: its biome features include the Clay disk placement, which checks water at its selected ocean-floor position and replaces eligible Dirt/Clay with Clay. This is a checked example, not a complete biome or generation inventory. [River feature][river] · [Placement][disk-placement] · [Configured disk][disk-config] · [Registered feature][disk-register] · [Placement implementation][disk]

Clay has **0.6 hardness**, does not require a tool for ordinary drops, and belongs to the shovel-mining tag. Use an unbroken shovel for faster collection, or mine it by hand. Its loot alternatives are:

- **Silk Touch I or higher:** one Clay block
- **Without Silk Touch:** exactly **four Clay Balls** in ordinary mining

Fortune does not increase either result. The ball branch has explosion decay, so explosions can recover fewer balls. Four collected balls can be crafted back into a block; Silk Touch is not required to obtain the block item. [Clay properties][clay-block] · [Shovel tag][shovel] · [Tool rules][tool] · [Drop gate][tool-gate] · [Clay loot][clay-loot]

## Crafting and smelting

| Conversion | Exact input and layout | Output |
| --- | --- | --- |
| Re-form Clay | **4 Clay Balls** in a **2 × 2 square** | **1 Clay block** |
| Fire a Brick | Smelt **1 Clay Ball** in a fueled [Furnace](Furnace.md) | **1 Brick**; **200 ticks**, **0.3 recipe XP** |
| Build Bricks | **4 Bricks** in a **2 × 2 square** | **1 Bricks block** |

Both square recipes fit the personal crafting grid. Brick smelting takes approximately **10 seconds at 20 ticks per second** while processing. It uses the normal smelting recipe type; a Blast Furnace does not accept this recipe. [Clay recipe][clay-recipe] · [Brick recipe][brick-recipe] · [Bricks recipe][bricks-recipe] · [Furnace type][furnace-type]

One Coal or Charcoal supplies **1,600 default burn ticks**, enough for **eight uninterrupted Brick smelts**. Keep input and output space available because lit fuel continues burning between jobs. The stated 0.3 XP belongs to the recipe; see [Furnace experience](Furnace.md#experience-and-troubleshooting) for payout and [fuel planning](Furnace.md#fuel-planning) for other fuels. Clay, Clay Balls, Brick, Bricks, and Flower Pots are not default fuels. [Fuel values][fuel] · [Server fuel setup][fuel-load] · [Cooking consumption][furnace-burn]

For the exact **Clay-block-to-Terracotta** recipe, use the [Terracotta guide](Terracotta.md#crafting-and-smelting). For the exact **Brick-to-Flower-Pot** layout, use [Flower Pot crafting](FlowerPot.md#crafting). These pages own those shared recipes.

## Brick slabs, stairs, and walls

The checked shape recipes all use **Bricks blocks**, not loose Brick items. Craft them at a [Crafting Table](CraftingTable.md), or select the matching output in a [Stonecutter](Stonecutter.md). [Stonecutter selection][cut-menu]

| Result | Crafting Table recipe | Stonecutter recipe |
| --- | --- | --- |
| [Brick Slab](../items/BrickSlab.md) | 3 Bricks across one row → **6 slabs** | 1 Bricks → **2 slabs** |
| [Brick Stairs](../items/BrickStairs.md) | 6 Bricks in a three-row stair pattern, **1 / 2 / 3 blocks** → **4 stairs** | 1 Bricks → **1 stair** |
| [Brick Wall](../items/BrickWall.md) | 6 Bricks in **two full rows** → **6 walls** | 1 Bricks → **1 wall** |

Stonecutting gives more stairs per Bricks block; slab and wall yields are equivalent. These are selected building recipes, not an inventory of every Brick-related ingredient use. [Slab crafting][slab-recipe] · [Stair crafting][stairs-recipe] · [Wall crafting][wall-recipe] · [Slab cutting][slab-cut] · [Stair cutting][stairs-cut] · [Wall cutting][wall-cut]

## Turning Mud into Clay

A compact source-derived setup uses this vertical stack:

| Position | Place here |
| --- | --- |
| Top | **[Mud](../items/Mud.md)** |
| Middle | A support block with a **sturdy underside** |
| Bottom | **One downward [Pointed Dripstone](../items/PointedDripstone.md)** hanging from that underside |

The support does not need to be a Dripstone Block. The conversion checks the block **above the support**, so attaching the pointed dripstone directly to Mud does not target that supporting Mud. No water source or cauldron is required. Leave the area ticking until the Mud becomes Clay, then collect the Clay and replace the Mud. [Support and root checks][drip-layout] · [Mud lookup][drip-mud] · [Conversion][drip-convert]

Only the **topmost downward pointed-dripstone block** makes conversion attempts. Each of its random ticks has a **17.578125%** conversion chance when the other conditions pass; this is not a fixed timer or a chance every game tick. Lengthening the stalactite adds no extra rolls. Random ticks must be running in that chunk. [Conversion roll][drip-convert] · [Root definition][drip-layout] · [Random-tick registration][drip-register] · [Server ticking][random-tick]

For a longer setup, the branch must end in an **unmerged tip** at the root or within ten blocks below it, a maximum ordinary length of **11 pointed-dripstone blocks**. A merged tip does not qualify. This Mud conversion branch does not require an open drip path below or a non-waterlogged tip. It is disabled in **ultra-warm dimensions**, including the Nether. [Tip search][drip-tip] · [Search bounds][drip-search] · [Conversion branch][drip-convert] · [Dimension check][drip-mud] · [Nether settings][nether]

## Building and collecting Bricks

Clay and full Bricks use ordinary full-block shapes with no continuing support requirement; they do not fall when the block underneath is removed. Bricks have **2 hardness** and **6 blast resistance**. [Default shape and survival][shape] · [Clay registration][clay-block] · [Bricks properties][bricks-block]

Use an **unbroken pickaxe** to collect Bricks, Brick Slabs, Brick Stairs, or Brick Walls. A Wooden Pickaxe is sufficient under the bundled mining and tool-tier tags. Hand breaking or an unsuitable tool fails the correct-tool drop requirement; broken pickaxes fail it too. [Pickaxe tag][pickaxe] · [Wooden restrictions][wood] · [Tool rules][tool] · [Player drop gate][tool-gate] · [Broken-tool handling][broken] · [Bricks properties][bricks-block] · [Slab properties][slab-block] · [Stair registration][stairs-block] · [Inherited stair properties][stairs-copy] · [Wall properties][wall-block]

Ordinary correct-tool mining returns **one matching block item**. A double Brick Slab returns **two Brick Slabs**, rather than a Bricks block. These loot tables have no Silk Touch requirement or Fortune multiplier. Their explosion conditions/decay mean blast recovery is not guaranteed. [Bricks loot][bricks-loot] · [Slab loot][slab-loot] · [Stair loot][stairs-loot] · [Wall loot][wall-loot] · [Mining dispatch][break]

Related: [Flower Pot](FlowerPot.md) · [Terracotta](Terracotta.md) · [Furnace](Furnace.md) · [Mining tools](../mechanics/Mining.md) · [Blocks](Blocks.md) · [Items](../items/Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked item/block registrations, the River Clay-disk chain, all bundled recipe outputs for the selected materials and Brick shapes, their loot, expanded mining/fuel tags, and active Mud conversion and processing callbacks. Natural structures, trades, and every possible clay-generation route are outside this page's checked scope. No in-game mining, smelting, stonecutting, or dripstone setup test was run. Data packs can change recipes, tags, and loot.

[clay-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L1962-L1964
[ingredients]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1594-L1595
[brick-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/smelting/brick.json
[clay-item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L491
[bricks-item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L417
[river]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/river.json#L62-L65
[disk-placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/placed_feature/disk_clay.json
[disk-config]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/configured_feature/disk_clay.json
[disk-register]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L107
[disk]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/feature/DiskFeature.java
[shovel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[tool]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[clay-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/clay.json
[clay-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/clay.json
[bricks-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/bricks.json
[furnace-type]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java#L11-L16
[fuel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L26-L108
[fuel-load]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/MinecraftServer.java#L340
[furnace-burn]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L236-L273
[cut-menu]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java#L136-L164
[slab-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/brick_slab.json
[stairs-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/brick_stairs.json
[wall-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/brick_wall.json
[slab-cut]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/stonecutting/brick_slab_from_bricks_stonecutting.json
[stairs-cut]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/stonecutting/brick_stairs_from_bricks_stonecutting.json
[wall-cut]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/stonecutting/brick_wall_from_bricks_stonecutting.json
[drip-layout]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L474-L512
[drip-mud]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L544-L556
[drip-convert]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L181-L233
[drip-register]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L6521-L6536
[random-tick]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java#L487-L507
[drip-tip]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L412-L423
[drip-search]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L575-L599
[nether]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/dimension_type/the_nether.json
[shape]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[bricks-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L1103-L1106
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[broken]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[slab-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L3867-L3871
[stairs-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L2437
[stairs-copy]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L7255-L7257
[wall-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L5271
[bricks-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/bricks.json
[slab-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/brick_slab.json
[stairs-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/brick_stairs.json
[wall-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/brick_wall.json
[break]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
