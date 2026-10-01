# Oak Planks

**Oak Planks** (`minecraft:oak_planks`) are an early building material, crafting ingredient, and furnace fuel. Their placed-block harvesting and fire behavior are covered by the [Oak block guide](../blocks/Oak.md). [Registration][item]

## Crafting oak planks

Put **one item in the oak-log tag** into any crafting-grid slot to make **4 oak planks**. This recipe is shapeless and fits in the inventory grid. Its bundled accepted inputs are:

- [Oak Log](OakLog.md)
- [Oak Wood](OakWood.md)
- [Stripped Oak Log](StrippedOakLog.md)
- [Stripped Oak Wood](StrippedOakWood.md)

The tag is specific to oak; a different wood family is not an oak-plank input merely because it is also called a log. [Plank recipe][recipe] · [Exact input tag][oak-tag]

## Beginner crafting uses

Oak planks belong to the broad **planks** ingredient tag and, through it, the **wooden-tool-materials** tag. Verified uses include:

- [Crafting Table](../blocks/CraftingTable.md): unlock a larger crafting grid
- [Sticks](Stick.md#crafting): handles, torches, and other small components
- [Wooden Pickaxe](WoodenPickaxe.md): the recipe accepts oak planks for its head and sticks for its handle

Use the linked pages and recipe book for layouts. These are selected recipes, not a complete list of furniture, tools, or oak building forms. [Planks tag][planks-tag] · [Tool-material tag][tool-tag] · [Crafting Table recipe][table] · [Stick recipe][sticks] · [Wooden Pickaxe recipe][pickaxe]

## Fuel

One oak plank supplies **300 default furnace burn ticks**. Four planks made from one log therefore supply **1,200 burn ticks**, versus **300** for burning that log directly. That is enough energy for **six uninterrupted 200-tick recipes** when all four planks are used efficiently. [Recipe yield][recipe] · [Fuel table][fuel]

Burning fuel continues while a furnace is lit, so keep input available and output space clear. These numbers describe the ordinary furnace's default fuel lookup, not every machine or recipe duration. See [Furnace fuel planning](../blocks/Furnace.md#fuel-planning). [Furnace processing][furnace] · [Server fuel initialization][server-fuel]

Related: [Oak](../blocks/Oak.md) · [Oak Log](OakLog.md) · [Stick](Stick.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game crafting or fuel-efficiency test was run. Recipes, ingredient tags, and server configuration can change the available conversions.

[item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L120
[recipe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/oak_planks.json
[oak-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/oak_logs.json
[planks-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/planks.json
[tool-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/wooden_tool_materials.json
[table]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/crafting_table.json
[sticks]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/stick.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/wooden_pickaxe.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java
[furnace]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java
[server-fuel]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L340
