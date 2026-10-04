# Smooth Stone

**Smooth Stone** (`minecraft:smooth_stone`) is a placeable finished stone block, distinct from ordinary Stone. [Item registration] · [Block registration]

## Obtaining and use

In a [Furnace](../blocks/Furnace.md), smelt **1 [Stone](Stone.md) → 1 Smooth Stone** with fuel. Each item takes **200 game ticks**, nominally **10 seconds at 20 ticks per second**, and the recipe specifies **0.1 experience**. Starting with [Cobblestone](Cobblestone.md) takes two separate smelting steps: Cobblestone → Stone → Smooth Stone. See the [Stone family guide](../blocks/Stone.md#smooth-stone) and Furnace guide for the shared fuel and experience rules. [Smelting][smelting-smooth-stone] · [First smelting step][smelting-stone]

Mine with an **unbroken pickaxe**, including a Wooden Pickaxe: each block returns **1 Smooth Stone**. Breaking it by hand does not collect it. Silk Touch does not change the output or bypass the pickaxe requirement, and Fortune does not multiply it. [Drop table] · [Pickaxe tag] · [Tool gate] · [Broken tools] See the [family mining rules](../blocks/Stone.md#obtaining) for tool tiers and drop conditions. [Wood tier]

Use it as a full building block or make [Smooth Stone Slabs](SmoothStoneSlab.md): **3 Smooth Stone in one row → 6 slabs** at a Crafting Table, or **1 Smooth Stone → 2 slabs** at a Stonecutter. The [shape guide](../blocks/Stone.md#stairs-slabs-and-walls) compares the family. There is no registered Smooth Stone stair or wall. [Slab recipe][crafting-smooth-stone-slab] · [Stonecutting][cutting-smooth-stone-slab] · [Registry][blocks]

For **1 [Blast Furnace](BlastFurnace.md)**, put **3 Iron Ingots** across the top row, **Iron Ingot / Furnace / Iron Ingot** across the middle, and **3 Smooth Stone** across the bottom: **5 Iron Ingots + 1 Furnace + 3 Smooth Stone** in total. Ordinary Stone is not a substitute for Smooth Stone here. [Blast Furnace recipe][crafting-blast-furnace]

Find Smooth Stone by name in the combined [inventory item browser](../mechanics/InventoryBrowser.md). Its catalog entry is available to browse, but requesting an item requires the server's infinite-materials ability, normally Creative mode; ordinary Survival browsing does not supply it. [Catalog entry] · [Combined catalog] · [Creative admission]

Related: [Stone](Stone.md) · [Cobblestone](Cobblestone.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Bundled recipes, loot, registrations, tool rules and Creative-browser admission were checked; no in-game crafting, smelting, mining, placement or inventory-request test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L416-L416
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L3917-L3920
[Drop table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/smooth_stone.json
[Pickaxe tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[Wood tier]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Broken tools]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[Catalog entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L263-L263
[Combined catalog]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L88-L108
[Creative admission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[smelting-smooth-stone]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/smooth_stone.json
[smelting-stone]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/stone.json
[crafting-smooth-stone-slab]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/smooth_stone_slab.json
[cutting-smooth-stone-slab]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/smooth_stone_slab_from_smooth_stone_stonecutting.json
[crafting-blast-furnace]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/blast_furnace.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java
