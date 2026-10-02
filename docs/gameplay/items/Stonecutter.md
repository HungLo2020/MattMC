# Stonecutter

**Stonecutter** (`minecraft:stonecutter`) is a building workstation for single-input stonecutting recipes and the Mason job site. Its [block guide](../blocks/Stonecutter.md) owns the recipe, menu, output examples, and placement details. [Item registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L2444)

## Obtaining

Craft one with **three ordinary Stone and one Iron Ingot**, using the [canonical arrangement](../blocks/Stonecutter.md#crafting-and-collecting). A correct, unbroken pickaxe recovers one placed Stonecutter; Silk Touch is not required, and explosion recovery remains conditional. [Recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/stonecutter.json) · [Loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/stonecutter.json)

## Using it

Place it, open its menu, insert an accepted material, choose a recipe, and take the result. It needs no fuel. The menu consumes one input block per completed operation, with an output count defined by the chosen recipe. It is not persistent container storage. See [menu handling](../blocks/Stonecutter.md#using-the-menu) and [checked examples](../blocks/Stonecutter.md#checked-recipe-examples).

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Registration, recipe, loot, and the shared block/menu guide were checked. No gameplay test was run; data packs can alter recipe choices.

Related: [Stonecutter block](../blocks/Stonecutter.md) · [Items](Items.md)
