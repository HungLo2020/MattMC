# End Stone and Purpur

**End Stone** (`minecraft:end_stone`) is the pale terrain material of the End; it can become End Stone Bricks and their shapes. **Purpur Block** (`minecraft:purpur_block`) is a separate purple construction material made from **Popped Chorus Fruit**, with pillar, stair and slab forms. [Block and item registrations][blocks] [items]

## Obtaining in the End

The active normal world preset uses the End noise generator settings, whose base terrain block is **End Stone**. Mine the island terrain with a suitable pickaxe. The [End dimension guide](../dimensions/End.md) owns travel and dimension guidance. [Normal preset][normal-preset] · [End settings][end-settings] · [Active terrain generator][terrain]

**End Cities** are another checked material source. The generator's initial base-floor and second-floor templates contain End Stone Bricks, Purpur Blocks, pillars and stairs; the second-floor template also contains Purpur Slabs. Follow the existing [End City guide](../structures/EndCity.md) for search conditions, dangers and rewards. The template contents establish materials in generated pieces, not guaranteed totals in an intact or previously visited city. [Structure definition and set][city-config] [city-set] · [Generation and initial pieces][city-structure] [city-pieces] · [Decoded templates][city-base] [city-second] · [Template placement][template-piece] [template]

### Mining and recovery

Use an **unbroken pickaxe**, including Wood, for all nine End Stone/Purpur forms on this page. They require a correct tool, are in the pickaxe tag and have no higher material-tier restriction. Hand breaking does not collect them. [Registry][blocks] · [Tool tags][pickaxe] [wood-denials] [stone-tier] [iron-tier] [diamond-tier] · [Tool/break dispatch][tool-material] [player-tool] [broken-tool] [break-dispatch]

Full blocks, pillars, stairs and walls drop **one matching item**. Single slabs give **one slab**, and double slabs give **two matching slabs**. Silk Touch is unnecessary and Fortune does not multiply these drops. Mining End Stone Bricks preserves the bricks, and mining Purpur does not return Popped Chorus Fruit. Explosions have separate survival/decay conditions. [End Stone family loot][loot-end-stone] [loot-end-stone-bricks] [loot-end-stone-brick-stairs] [loot-end-stone-brick-wall] [loot-end-stone-brick-slab] · [Purpur loot][loot-purpur-block] [loot-purpur-pillar] [loot-purpur-stairs] [loot-purpur-slab]

## End stone variants and recipes

| Form | Registry ID |
| --- | --- |
| [End Stone](../items/EndStone.md) | `minecraft:end_stone` |
| [End Stone Bricks](../items/EndStoneBricks.md) | `minecraft:end_stone_bricks` |
| [End Stone Brick Stairs](../items/EndStoneBrickStairs.md) | `minecraft:end_stone_brick_stairs` |
| [End Stone Brick Slab](../items/EndStoneBrickSlab.md) | `minecraft:end_stone_brick_slab` |
| [End Stone Brick Wall](../items/EndStoneBrickWall.md) | `minecraft:end_stone_brick_wall` |

Craft **4 End Stone in a 2 × 2 square → 4 End Stone Bricks**. For the brick shapes, use the [shared patterns](Stone.md#crafting-yields): **6 End Stone Bricks → 4 stairs**, **3 → 6 slabs**, or **6 → 6 walls**. Crafting these shapes uses the bricks, while stonecutting can accept raw End Stone directly. [Brick recipe][crafting-end-stone-bricks] · [Shape recipes][crafting-end-stone-brick-stairs] [crafting-end-stone-brick-slab] [crafting-end-stone-brick-wall]

There are no registered raw End Stone stairs/slabs/walls, cracked or chiseled End Stone Bricks, or End Stone pillars. The raw block is also not made by a bundled crafting or smelting recipe. [Registry][blocks] · [Recipe loading][recipes]

## Chorus fruit to purpur

**Chorus Plant stems** provide the raw fruit for this route. The active End biome source includes End Highlands, whose feature list points to the placed Chorus Plant feature. That feature chooses surface positions and generates a plant only with empty space above **End Stone**. Breaking a stem has a bundled drop of **0–1 Chorus Fruit**; it does not directly produce Popped Chorus Fruit. [Biome source and feature wiring][end-biomes] [end-highlands] [chorus-placement] [chorus-config] · [Registered feature and generation][features] [chorus-feature] [chorus-flower] · [Stem loot][chorus-loot]

1. Smelt **1 Chorus Fruit → 1 Popped Chorus Fruit** in a [Furnace](Furnace.md)
2. Arrange **4 Popped Chorus Fruit in a 2 × 2 square → 4 Purpur Blocks**
3. Choose the pillar, stair or slab conversion below

Cooking takes **200 game ticks**, nominally **10 seconds at 20 ticks per second**, and specifies **0.1 recipe experience**. Popped Chorus Fruit is registered as an ordinary crafting item with no food/consumable component: **it cannot be eaten or used for the raw fruit's eating interaction**. [Cooking][smelting-popped-chorus-fruit] · [Purpur recipe][crafting-purpur-block] · [Active Furnace path][furnace] [furnace-tick] · [Item registrations and use dispatch][items] [item-use]

## Purpur variants and recipes

| Form | Registry ID | Crafting |
| --- | --- | --- |
| [Purpur Block](../items/PurpurBlock.md) | `minecraft:purpur_block` | 4 Popped Chorus Fruit in a square → 4 blocks |
| [Purpur Pillar](../items/PurpurPillar.md) | `minecraft:purpur_pillar` | 2 Purpur Slabs stacked vertically → 1 pillar |
| [Purpur Stairs](../items/PurpurStairs.md) | `minecraft:purpur_stairs` | 6 accepted blocks in the stair pattern → 4 stairs |
| [Purpur Slab](../items/PurpurSlab.md) | `minecraft:purpur_slab` | 3 accepted blocks in one row → 6 slabs |

For Purpur Stairs and Slabs, each occupied recipe slot accepts **Purpur Block or Purpur Pillar**, so the two may be mixed. There is no registered Purpur Wall, separate Purpur Bricks block, or smooth/chiseled/cracked Purpur family. [Recipes][crafting-purpur-block] [crafting-purpur-pillar] [crafting-purpur-stairs] [crafting-purpur-slab] · [Ingredient matching][ingredient] [pattern] · [Registry][blocks]

## Stonecutting

The [Stonecutter](Stonecutter.md) uses one input per operation. It produces one brick block, pillar, stair or wall, or two slabs, for these exact input/output choices. [Menu consumption][stonecutter]

| One input block | Available outputs and count per operation |
| --- | --- |
| End Stone | [2 × End Stone Brick Slab][stonecutting-end-stone-brick-slab-from-end-stone-stonecutting]; [1 × End Stone Brick Stairs][stonecutting-end-stone-brick-stairs-from-end-stone-stonecutting]; [1 × End Stone Brick Wall][stonecutting-end-stone-brick-wall-from-end-stone-stonecutting]; [1 × End Stone Bricks][stonecutting-end-stone-bricks-from-end-stone-stonecutting] |
| End Stone Bricks | [2 × End Stone Brick Slab][stonecutting-end-stone-brick-slab-from-end-stone-brick-stonecutting]; [1 × End Stone Brick Stairs][stonecutting-end-stone-brick-stairs-from-end-stone-brick-stonecutting]; [1 × End Stone Brick Wall][stonecutting-end-stone-brick-wall-from-end-stone-brick-stonecutting] |
| Purpur Block | [1 × Purpur Pillar][stonecutting-purpur-pillar-from-purpur-block-stonecutting]; [2 × Purpur Slab][stonecutting-purpur-slab-from-purpur-block-stonecutting]; [1 × Purpur Stairs][stonecutting-purpur-stairs-from-purpur-block-stonecutting] |

**Purpur Pillar is not a stonecutting input**, even though it works in the crafting recipes for stairs and slabs. There is no reverse recipe turning Purpur back into Popped Chorus Fruit or End Stone Bricks back into raw End Stone. Cutting stairs gives one per block rather than four per six crafted inputs. [Recipe loading and selection][recipes] [stonecutter] · [Crafted stairs][crafting-purpur-stairs] [crafting-end-stone-brick-stairs]

## Placement, pillars and dragon interactions

**Purpur Pillar** uses the clicked face's axis: top/bottom faces make a vertical pillar; east/west sides give an east–west axis; north/south sides give a north–south axis. Ordinary Purpur, End Stone and End Stone Bricks have no such orientation property. Their stairs, slabs and walls use the [shared shape and waterlogging rules](Stone.md#placing-shaped-blocks). All these masonry blocks remain placed when support beneath is removed. [Classes][blocks] · [Pillar placement][pillar] · [Base and shape behavior][properties] [stairs] [slabs] [walls]

Raw End Stone is specifically accepted beneath a **Chorus Flower**; End Stone Bricks is not a substitute for that directly-underneath support check. This is a substrate distinction, not a complete Chorus-growing guide. [Flower survival check][chorus-flower]

**Raw End Stone is in the Dragon-immune block tag. End Stone Bricks and Purpur are not.** With `mobGriefing` enabled, the Ender Dragon's active body/head/neck block-clearing checks skip raw End Stone, while those decorative materials can be removed. Converting your End Stone to bricks changes this protection. [Immune tag][dragon-immune] · [Active checks][dragon-tick] [dragon]

## Block properties

| Forms | Hardness | Blast resistance |
| --- | ---: | ---: |
| End Stone and all four End Stone Brick forms | 3 | 9 |
| Purpur Block, Pillar and Stairs | 1.5 | 6 |
| Purpur Slab | 2 | 6 |

Purpur Slab has its own hardness setting. These materials use the bass-drum note-block instrument. Hardness is not a measured breaking time. [Registrations and copied properties][blocks] [properties]

## Sources and verification

Source-reviewed on **2026-10-02** at `688be47ebeaa38beb06130efddf19484a8b81580`. Checked all nine block/item forms and loot tables, tools, exact recipes and ingredient alternatives, pillar/shape placement, active normal End terrain and Chorus generation, decoded End City construction templates, and Dragon block-clearing dispatch. No in-game generation, harvesting, cooking, crafting, placement or Dragon test was run.

Related: [End Stone item](../items/EndStone.md) · [Purpur Block item](../items/PurpurBlock.md) · [Popped Chorus Fruit](../items/PoppedChorusFruit.md) · [End City](../structures/EndCity.md) · [Quartz construction](Quartz.md) · [Stone category](catalog/stone.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/Items.java
[normal-preset]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[end-settings]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/worldgen/noise_settings/end.json
[terrain]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java
[city-config]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/worldgen/structure/end_city.json
[city-set]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/worldgen/structure_set/end_cities.json
[city-structure]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/levelgen/structure/structures/EndCityStructure.java
[city-pieces]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/levelgen/structure/structures/EndCityPieces.java
[city-base]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/structure/end_city/base_floor.nbt
[city-second]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/structure/end_city/second_floor_1.nbt
[template-piece]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/levelgen/structure/TemplateStructurePiece.java
[template]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/ToolMaterial.java#L21-L48
[player-tool]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[loot-end-stone]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/end_stone.json
[loot-end-stone-bricks]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/end_stone_bricks.json
[loot-end-stone-brick-stairs]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/end_stone_brick_stairs.json
[loot-end-stone-brick-wall]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/end_stone_brick_wall.json
[loot-end-stone-brick-slab]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/end_stone_brick_slab.json
[loot-purpur-block]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/purpur_block.json
[loot-purpur-pillar]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/purpur_pillar.json
[loot-purpur-stairs]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/purpur_stairs.json
[loot-purpur-slab]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/purpur_slab.json
[crafting-end-stone-bricks]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/end_stone_bricks.json
[crafting-end-stone-brick-stairs]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/end_stone_brick_stairs.json
[crafting-end-stone-brick-slab]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/end_stone_brick_slab.json
[crafting-end-stone-brick-wall]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/end_stone_brick_wall.json
[recipes]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[end-biomes]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/biome/TheEndBiomeSource.java
[end-highlands]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/worldgen/biome/end_highlands.json
[chorus-placement]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/worldgen/placed_feature/chorus_plant.json
[chorus-config]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/worldgen/configured_feature/chorus_plant.json
[features]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java
[chorus-feature]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/levelgen/feature/ChorusPlantFeature.java
[chorus-flower]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java
[chorus-loot]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/chorus_plant.json
[smelting-popped-chorus-fruit]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/smelting/popped_chorus_fruit.json
[crafting-purpur-block]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/purpur_block.json
[furnace]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java
[furnace-tick]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L143-L204
[item-use]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/Item.java#L172-L191
[crafting-purpur-pillar]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/purpur_pillar.json
[crafting-purpur-stairs]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/purpur_stairs.json
[crafting-purpur-slab]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/purpur_slab.json
[ingredient]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/crafting/Ingredient.java
[pattern]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L194
[stonecutter]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java
[stonecutting-end-stone-brick-slab-from-end-stone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/end_stone_brick_slab_from_end_stone_stonecutting.json
[stonecutting-end-stone-brick-stairs-from-end-stone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/end_stone_brick_stairs_from_end_stone_stonecutting.json
[stonecutting-end-stone-brick-wall-from-end-stone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/end_stone_brick_wall_from_end_stone_stonecutting.json
[stonecutting-end-stone-bricks-from-end-stone-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/end_stone_bricks_from_end_stone_stonecutting.json
[stonecutting-end-stone-brick-slab-from-end-stone-brick-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/end_stone_brick_slab_from_end_stone_brick_stonecutting.json
[stonecutting-end-stone-brick-stairs-from-end-stone-brick-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/end_stone_brick_stairs_from_end_stone_brick_stonecutting.json
[stonecutting-end-stone-brick-wall-from-end-stone-brick-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/end_stone_brick_wall_from_end_stone_brick_stonecutting.json
[stonecutting-purpur-pillar-from-purpur-block-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/purpur_pillar_from_purpur_block_stonecutting.json
[stonecutting-purpur-slab-from-purpur-block-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/purpur_slab_from_purpur_block_stonecutting.json
[stonecutting-purpur-stairs-from-purpur-block-stonecutting]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/stonecutting/purpur_stairs_from_purpur_block_stonecutting.json
[pillar]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java
[properties]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[stairs]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/StairBlock.java
[slabs]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/SlabBlock.java
[walls]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/WallBlock.java
[dragon-immune]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/dragon_immune.json
[dragon-tick]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java#L305-L314
[dragon]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java#L402-L435
