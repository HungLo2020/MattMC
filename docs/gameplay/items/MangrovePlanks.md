# Mangrove Planks

**Mangrove Planks** (`minecraft:mangrove_planks`) are a full-block building material and a crafting ingredient. The [Wood Construction guide](../blocks/WoodConstruction.md#planks-and-materials) covers their placed form and matching construction shapes. [Item][item] · [Block][block]

## Obtaining

Put **one** [Mangrove Log](MangroveLog.md), [Mangrove Wood](MangroveWood.md), [Stripped Mangrove Log](StrippedMangroveLog.md), or [Stripped Mangrove Wood](StrippedMangroveWood.md) into any crafting-grid slot to make **4 Mangrove Planks**. This shapeless recipe fits the inventory grid and uses the specific `mangrove_logs` ingredient family. See [the timber guide](../blocks/TreeLogsAndRoots.md#mangrove-timber) for the input blocks and stripping. [Recipe][recipe] · [Accepted inputs][inputs]

Mangrove Roots and Muddy Mangrove Roots are not accepted plank inputs; use the four timber forms above. See [Mangrove roots](../blocks/TreeLogsAndRoots.md#mangrove-roots) for those separate blocks. [Exact input tag][inputs]

Ordinary mining of placed Mangrove Planks returns **one matching plank item**, even by hand; an unbroken axe is faster. Silk Touch and Fortune do not change the bundled drop. See [mining and drops](../blocks/WoodConstruction.md#mining-and-drops) for the shared rules and explosion caveat. [Loot][loot] · [Harvest eligibility][harvest] · [Player mining][harvest-call]

## Usage

- **3 Mangrove Planks in a horizontal row → 6 [Mangrove Slabs](MangroveSlab.md)**. [Recipe][slab]
- **6 Mangrove Planks in the stair layout → 4 [Mangrove Stairs](MangroveStairs.md)**. The [construction recipes](../blocks/WoodConstruction.md#crafting-construction-shapes) also cover matching fences, fence gates, doors, and trapdoors. Use this exact plank material for those variants. [Stair recipe][stairs]
- Generic plank recipes accept Mangrove Planks: **4 in a 2 × 2 square → 1 [Crafting Table](../blocks/CraftingTable.md)**, or **2 vertically → 4 [Sticks](Stick.md#crafting)**. These recipes also accept other members of the plank tag. [Tag][planks] · [Table recipe][table] · [Stick recipe][sticks]

They also qualify as wooden tool material; for example, the [Wooden Pickaxe](WoodenPickaxe.md) recipe uses three planks and two sticks. [Tool-material tag][tool-materials] · [Pickaxe recipe][pickaxe]

## Behavior

Placed Mangrove Planks have **hardness 2 and blast resistance 3**, and can catch fire. Each plank supplies **300 default furnace burn ticks**. See [fire and fuel](../blocks/WoodConstruction.md#fire-and-furnace-fuel) and [Furnace fuel planning](../blocks/Furnace.md#fuel-planning) for the shared limits and avoiding wasted fuel. [Block properties][block] · [Fire registration][fire] · [Fuel values][fuel] · [Furnace lookup][fuel-active]

## Notes

- The item places the `minecraft:mangrove_planks` block and appears in the **Building Blocks** Creative tab. [Item][item] · [Creative listing][creative]
- Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game crafting, mining, or fuel test was run. Data packs and server settings can change recipes, tags, loot, and fire behavior.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L128
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L186-L189
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/mangrove_planks.json
[inputs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/mangrove_logs.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/mangrove_planks.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest-call]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L282-L292
[slab]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/mangrove_slab.json
[stairs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/mangrove_stairs.json
[planks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/planks.json
[table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/crafting_table.json
[sticks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/stick.json
[tool-materials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/wooden_tool_materials.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/wooden_pickaxe.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[fuel-active]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L264-L266
[fire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FireBlock.java#L308-L320
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L73-L200
