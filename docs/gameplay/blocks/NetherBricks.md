# Nether Bricks

**Nether Bricks** (`minecraft:nether_bricks`) is a placeable masonry block. **Nether Brick** (`minecraft:nether_brick`, singular) is the small crafting item used to make it; that item is not a placeable brick block. **Red Nether Bricks** (`minecraft:red_nether_bricks`) is a separate building material made with Nether Wart. [Block and item registrations][blocks] [items]

## Obtaining

### Make the small brick item

Smelt **1 Netherrack → 1 Nether Brick** in a [Furnace](Furnace.md). It takes **200 game ticks**, nominally **10 seconds at 20 ticks per second**, and specifies **0.1 recipe experience**. Follow the Furnace guide for fuel and experience collection. [Smelting recipe][smelting-nether-brick] · [Active recipe type and processing][furnace] [furnace-tick]

Completed adult Piglin barters can also select **2–8 Nether Brick items**. This is one possible Gold Ingot barter result, not a guaranteed return and not the full Nether Bricks block. [Barter loot][barter-loot] · [Active interaction/completion][piglin] [piglin-ai][] [barter-completion][]

### Collect placed masonry

**Nether Fortresses** provide a checked natural structure route for ordinary Nether Bricks and their built-in fence/stair pieces. The active Nether structure set includes Fortresses; their registered generator builds the pieces using these block states. This does not establish the red, cracked or chiseled variants as ordinary Fortress materials. [Structure set and configuration][nether-structures] [fortress] · [Biome eligibility][fortress-biomes] · [Registered generator][structure-types] [fortress-structure] · [Built pieces][fortress-pieces]

Use an **unbroken pickaxe**, including Wood, for all 11 placed-block forms in this guide. They require the correct tool, are pickaxe-tagged and have no higher material-tier restriction. Hand breaking does not collect them. [Registrations][blocks] · [Mining tags][pickaxe] [wood-denials][] [stone-tier][] [iron-tier][] [diamond-tier][] · [Tool/break checks][tool-material] [player-tool][] [broken-tool][] [break-dispatch]

Full blocks, stairs, walls and fences drop **one matching block item**. A single slab gives **one slab** and a double slab gives **two**. Silk Touch is unnecessary, and Fortune does not increase these drops. A Nether Bricks block does **not** break into the four small Nether Brick crafting items. Explosion drops have separate survival/decay rules. [Full-block loot][loot-nether-bricks] [loot-red-nether-bricks][] [loot-cracked-nether-bricks][] [loot-chiseled-nether-bricks] · [Shape examples][loot-nether-brick-stairs] [loot-red-nether-brick-wall][] [loot-nether-brick-fence][] · [Slab tables][loot-nether-brick-slab] [loot-red-nether-brick-slab]

## Variants

Both regular and red masonry have stairs, slabs and walls. [Block/item registry][blocks] [items]

| Full block | Stairs | Slab | Wall |
| --- | --- | --- | --- |
| [Nether Bricks](../items/NetherBricks.md) | [Stairs](../items/NetherBrickStairs.md) | [Slab](../items/NetherBrickSlab.md) | [Wall](../items/NetherBrickWall.md) |
| [Red Nether Bricks](../items/RedNetherBricks.md) | [Stairs](../items/RedNetherBrickStairs.md) | [Slab](../items/RedNetherBrickSlab.md) | [Wall](../items/RedNetherBrickWall.md) |

The regular family also has [Nether Brick Fence](../items/NetherBrickFence.md), [Cracked Nether Bricks](../items/CrackedNetherBricks.md) and [Chiseled Nether Bricks](../items/ChiseledNetherBricks.md). There is no registered Red Nether Brick Fence, cracked/chiseled red variant, or cracked/chiseled shaped family. The fence and wall are different blocks. [Registry][blocks]

## Crafting and cracking

| Ingredients and arrangement | Result |
| --- | --- |
| 4 small Nether Brick items in a 2 × 2 square | 1 Nether Bricks block |
| 2 small Nether Brick items + 2 Nether Wart in a 2 × 2 checkerboard | 1 Red Nether Bricks block |
| 2 Nether Brick Slabs stacked vertically | 1 Chiseled Nether Bricks |
| Smelt 1 Nether Bricks block | 1 Cracked Nether Bricks |
| Two rows of: Nether Bricks block, small Nether Brick item, Nether Bricks block | 6 Nether Brick Fences |

[Regular block][crafting-nether-bricks] · [Red block][crafting-red-nether-bricks] · [Chiseled][crafting-chiseled-nether-bricks] · [Cracked][smelting-cracked-nether-bricks] · [Fence][crafting-nether-brick-fence]

The fence recipe therefore uses **4 full Nether Bricks blocks and 2 small Nether Brick items**. The red-block recipe uses **Nether Wart itself**, not a Nether Wart Block. Cracking takes **200 game ticks** and specifies **0.1 recipe experience**. There is no bundled reverse recipe that turns a full Nether Bricks block back into its small brick ingredients. [Exact ingredients][crafting-nether-brick-fence] [crafting-red-nether-bricks] · [Cracking recipe][smelting-cracked-nether-bricks]

For stairs, slabs and walls, use the matching full-block finish with the [shared crafting patterns](Stone.md#crafting-yields).

| Matching full-block material | 6 → 4 stairs | 3 → 6 slabs | 6 → 6 walls |
| --- | --- | --- | --- |
| Nether Bricks | [Recipe][crafting-nether-brick-stairs] | [Recipe][crafting-nether-brick-slab] | [Recipe][crafting-nether-brick-wall] |
| Red Nether Bricks | [Recipe][crafting-red-nether-brick-stairs] | [Recipe][crafting-red-nether-brick-slab] | [Recipe][crafting-red-nether-brick-wall] |

## Stonecutting

The [Stonecutter](Stonecutter.md) consumes one full input block per operation. Stairs and walls give **1** result, slabs give **2**, and Chiseled Nether Bricks gives **1**. The fence is made by its mixed-item crafting recipe, not by stonecutting. [Menu consumption][stonecutter] · [Fence recipe][crafting-nether-brick-fence]

| One input block | Available outputs and count per operation |
| --- | --- |
| Nether Bricks | [1 × Chiseled Nether Bricks][stonecutting-chiseled-nether-bricks-from-nether-bricks-stonecutting]; [2 × Nether Brick Slab][stonecutting-nether-brick-slab-from-nether-bricks-stonecutting]; [1 × Nether Brick Stairs][stonecutting-nether-brick-stairs-from-nether-bricks-stonecutting]; [1 × Nether Brick Wall][stonecutting-nether-brick-wall-from-nether-bricks-stonecutting] |
| Red Nether Bricks | [2 × Red Nether Brick Slab][stonecutting-red-nether-brick-slab-from-red-nether-bricks-stonecutting]; [1 × Red Nether Brick Stairs][stonecutting-red-nether-brick-stairs-from-red-nether-bricks-stonecutting]; [1 × Red Nether Brick Wall][stonecutting-red-nether-brick-wall-from-red-nether-bricks-stonecutting] |

These cuts preserve the regular/red material family. They do not convert regular Nether Bricks into Red Nether Bricks or crack the blocks. Cracked and Chiseled Nether Bricks have no stonecutting-input recipe in the checked bundle. [Recipe loading and menu selection][recipes] [stonecutter]

## Placement and the fence

Full blocks have no player-selected facing or axis and remain placed without support beneath. The stair, slab and wall forms follow the [shared masonry placement rules](Stone.md#placing-shaped-blocks). [Classes][blocks] · [Base behavior][properties] · [Shapes][stairs] [slabs][] [walls][]

**Nether Brick Fence** uses fence behavior rather than wall behavior. It connects to another Nether Brick Fence, suitable sturdy block faces and properly aligned Fence Gates. It does **not** connect directly to the registered wooden fences: the connection rule separates the wooden and non-wooden fence groups. It supports waterlogging where water can exist, and its collision rises **1.5 blocks** above its base. Using the fence can also attach eligible mobs already held on [Leads](../items/Lead.md). [Fence connections and interaction][fence] · [Collision class][fence-shape] · [Fence tags][fences-tag] [wood-fences-tag]

All 11 masonry forms have **hardness 2** and **blast resistance 6** and use the bass-drum note-block instrument. Hardness is a block property, not a break time in seconds. [Registration and copied properties][blocks] [properties]

## Sources and verification

Source-reviewed on **2026-10-02** at `1879e5f54378351fd773b9a2c10839eb9259c504`. Checked the small brick item's distinct registration, 11 block/item forms and loot tables, correct-tool gates, exact crafting/smelting/stonecutting recipes, Fortress and barter dispatch, and fence connection/placement rules. No in-game crafting, mining, bartering, structure-generation or placement test was run.

Related: [Nether Brick item](../items/NetherBrick.md) · [Nether Bricks item](../items/NetherBricks.md) · [Red Nether Bricks item](../items/RedNetherBricks.md) · [Blackstone and Basalt](BlackstoneAndBasalt.md) · [Stone category](catalog/stone.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/Items.java
[smelting-nether-brick]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/smelting/nether_brick.json
[furnace]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java
[furnace-tick]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L143-L204
[barter-loot]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/gameplay/piglin_bartering.json
[piglin]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java
[piglin-ai]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java
[barter-completion]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/monster/piglin/StopHoldingItemIfNoLongerAdmiring.java
[nether-structures]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/worldgen/structure_set/nether_complexes.json
[fortress]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/worldgen/structure/fortress.json
[fortress-biomes]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/nether_fortress.json
[structure-types]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java
[fortress-structure]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressStructure.java
[fortress-pieces]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ToolMaterial.java#L21-L48
[player-tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[loot-nether-bricks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/nether_bricks.json
[loot-red-nether-bricks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/red_nether_bricks.json
[loot-cracked-nether-bricks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/cracked_nether_bricks.json
[loot-chiseled-nether-bricks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/chiseled_nether_bricks.json
[loot-nether-brick-stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/nether_brick_stairs.json
[loot-red-nether-brick-wall]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/red_nether_brick_wall.json
[loot-nether-brick-fence]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/nether_brick_fence.json
[loot-nether-brick-slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/nether_brick_slab.json
[loot-red-nether-brick-slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/red_nether_brick_slab.json
[crafting-nether-bricks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/nether_bricks.json
[crafting-red-nether-bricks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/red_nether_bricks.json
[crafting-chiseled-nether-bricks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/chiseled_nether_bricks.json
[smelting-cracked-nether-bricks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/smelting/cracked_nether_bricks.json
[crafting-nether-brick-fence]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/nether_brick_fence.json
[crafting-nether-brick-stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/nether_brick_stairs.json
[crafting-nether-brick-slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/nether_brick_slab.json
[crafting-nether-brick-wall]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/nether_brick_wall.json
[crafting-red-nether-brick-stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/red_nether_brick_stairs.json
[crafting-red-nether-brick-slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/red_nether_brick_slab.json
[crafting-red-nether-brick-wall]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/red_nether_brick_wall.json
[stonecutter]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java
[stonecutting-chiseled-nether-bricks-from-nether-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_nether_bricks_from_nether_bricks_stonecutting.json
[stonecutting-nether-brick-slab-from-nether-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/nether_brick_slab_from_nether_bricks_stonecutting.json
[stonecutting-nether-brick-stairs-from-nether-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/nether_brick_stairs_from_nether_bricks_stonecutting.json
[stonecutting-nether-brick-wall-from-nether-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/nether_brick_wall_from_nether_bricks_stonecutting.json
[stonecutting-red-nether-brick-slab-from-red-nether-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/red_nether_brick_slab_from_red_nether_bricks_stonecutting.json
[stonecutting-red-nether-brick-stairs-from-red-nether-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/red_nether_brick_stairs_from_red_nether_bricks_stonecutting.json
[stonecutting-red-nether-brick-wall-from-red-nether-bricks-stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/red_nether_brick_wall_from_red_nether_bricks_stonecutting.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[properties]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/StairBlock.java
[slabs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/SlabBlock.java
[walls]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/WallBlock.java
[fence]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/FenceBlock.java
[fence-shape]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/CrossCollisionBlock.java
[fences-tag]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/fences.json
[wood-fences-tag]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/wooden_fences.json
