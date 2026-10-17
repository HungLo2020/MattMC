# Crimson Planks

**Crimson Planks** (`minecraft:crimson_planks`) turn one matching stem-family item into four building blocks. Use them for Crimson construction shapes and selected shared wood recipes. The [Wood construction guide](../blocks/WoodConstruction.md) covers the placed blocks and their shared construction rules. [Item registration][item] · [Block registration][block]

## Obtaining

Put **one** of these items in any crafting-grid slot to make **4 Crimson Planks**:

- [Crimson Stem](CrimsonStem.md)
- [Crimson Hyphae](CrimsonHyphae.md)
- [Stripped Crimson Stem](StrippedCrimsonStem.md)
- [Stripped Crimson Hyphae](StrippedCrimsonHyphae.md)

The shapeless recipe fits the inventory grid. These are the four bundled entries in `minecraft:crimson_stems`; Warped stems and hyphae make their own planks instead. Stripping a stem or using its hyphae form does not change this recipe's four-plank yield. See [Tree Logs and Roots](../blocks/TreeLogsAndRoots.md) for harvesting and stripping the input blocks. [Plank recipe][recipe] · [Exact input tag][stem-tag]

A placed Crimson Planks block gives **one matching plank item** when mined normally in Survival, including by hand. An unbroken axe mines it faster; an axe is not required for the drop. Silk Touch and Fortune do not change the bundled loot, while explosions have a separate survival check. See [Wood construction: mining and drops](../blocks/WoodConstruction.md#mining-and-drops) for the shared rules and retained broken-tool caveat. [Loot][loot] · [Axe tag][axe-tag] · [Included plank blocks][block-planks-tag] · [Axe assignment][axe] · [Tool speed][axe-speed] · [Broken-tool speed][broken-tool] · [Harvest gate][harvest] · [Server harvest call][harvest-call]

## Usage

These six construction recipes require **Crimson Planks in every plank slot**. Use the [shared construction layouts](../blocks/WoodConstruction.md#crafting-construction-shapes) at a Crafting Table; the costs and outputs below are for this material:

| Ingredients | Output | Source |
| --- | --- | --- |
| 3 planks | [6 Crimson Slabs](CrimsonSlab.md) | [Recipe][slab] |
| 6 planks | [4 Crimson Stairs](CrimsonStairs.md) | [Recipe][stairs] |
| 4 planks + 2 sticks | [3 Crimson Fences](CrimsonFence.md) | [Recipe][fence] |
| 2 planks + 4 sticks | [1 Crimson Fence Gate](CrimsonFenceGate.md) | [Recipe][fence_gate] |
| 6 planks | [3 Crimson Doors](CrimsonDoor.md) | [Recipe][door] |
| 6 planks | [2 Crimson Trapdoors](CrimsonTrapdoor.md) | [Recipe][trapdoor] |

Crimson Planks also belong to the generic **planks** ingredient tag. Selected useful recipes accept them in their plank slots, including a mixture with other tagged planks:

- **4 planks → 1 [Crafting Table](../blocks/CraftingTable.md)** [recipe][crafting-table]
- **2 planks → 4 [Sticks](Stick.md)** [recipe][stick]
- **8 planks → 1 [Chest](../blocks/Chest.md)** [recipe][chest]
- **3 planks + 2 sticks → 1 [Wooden Pickaxe](WoodenPickaxe.md)**, through the wooden-tool-materials tag [recipe][wooden-pickaxe]

Use the linked item/block pages for layouts and use. This is a selected recipe list; each recipe's exact ingredient decides whether a substitution works. [Planks tag][planks-tag] · [Wooden-tool-materials tag][wooden-tool-tag] · [Ingredient matching][shaped-match]

## Behavior

Using this item places `minecraft:crimson_planks`. For placement and the resulting construction shapes, follow [Wood construction](../blocks/WoodConstruction.md#planks-and-materials). The item uses the ordinary block-item placement path. [Placement][placement] · [Item factory][item-factory] · [Block factory][block-factory]

The **placed planks are not consumed by ordinary fire spread**, and they do not count as a flammable neighbor for lava's fire-starting check. Those are separate block rules; see [Wood construction: fire and furnace fuel](../blocks/WoodConstruction.md#fire-and-furnace-fuel). [Active fire bootstrap][fire-active] · [Complete fire table and consumption][fire-table] · [Block properties][block] · [Property defaults][block-defaults] · [Lava ignition check][lava]

The **plank item is not accepted as default furnace fuel**. The fuel table removes the non-flammable-wood tag after adding the generic plank tag, and Crimson Planks belong to that removed tag. [Fuel entries][fuel] · [Removal][fuel-remove] · [Excluded items][fuel-tag] · [Server fuel setup][server-fuel] · [Furnace lookup][furnace]

**Dropped planks can still be destroyed by fire or lava.** Their ordinary item registration does not grant the damage-resistance component used for fire-resistant items, and dropped-item damage checks use that component rather than the fuel-exclusion tag. Keep loose planks out of fire and lava even when the placed blocks are suitable nearby. [Item defaults][item-defaults] · [Default properties][item-properties] · [Fire-resistance component][fire-component] · [Stack damage check][damage-check] · [Dropped-item damage][dropped-damage] · [Item entity registration][item-entity-type] · [Fire damage dispatch][entity-fire] · [Lava damage dispatch][entity-lava]

## Notes

* This item is the item form of the `minecraft:crimson_planks` block.
* Related: [Wood construction](../blocks/WoodConstruction.md) · [Tree Logs and Roots](../blocks/TreeLogsAndRoots.md) · [Warped Planks](WarpedPlanks.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. The registrations, bundled recipes and tags, block loot, mining dispatch, fire/lava checks, fuel lookup, and dropped-item damage path were inspected. No in-game crafting, mining, placement, fire, or fuel test was run. Data packs can change recipes, tags, and loot. Natural generation and a complete recipe inventory are outside this review.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L130-L131
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5582-L5589
[item-factory]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2744-L2782
[block-factory]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7283-L7293
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[block-planks-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/planks.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest-call]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L280-L293
[axe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L439-L441
[axe-speed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L374-L380
[planks-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/planks.json
[wooden-tool-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/wooden_tool_materials.json
[crafting-table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/crafting_table.json
[stick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/stick.json
[chest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/chest.json
[wooden-pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/wooden_pickaxe.json
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L48-L95
[fire-table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FireBlock.java
[fire-active]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/Bootstrap.java#L42-L51
[block-defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1000-L1014
[shaped-match]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L193
[lava]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/material/LavaFluid.java#L78-L136
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[fuel-remove]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L125-L148
[fuel-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/non_flammable_wood.json
[server-fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L340
[furnace]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L264-L266
[item-defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[item-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L355-L364
[fire-component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L402-L404
[damage-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1101-L1104
[dropped-damage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L253-L288
[item-entity-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L792-L800
[entity-fire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L510-L519
[entity-lava]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L574-L592
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/crimson_planks.json
[stem-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/crimson_stems.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/crimson_planks.json
[slab]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/crimson_slab.json
[stairs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/crimson_stairs.json
[fence]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/crimson_fence.json
[fence_gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/crimson_fence_gate.json
[door]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/crimson_door.json
[trapdoor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/crimson_trapdoor.json
