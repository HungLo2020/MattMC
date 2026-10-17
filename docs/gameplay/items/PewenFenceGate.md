# Pewen Fence Gate

`minecraft:pewen_fence_gate` places an openable gate for a passage through a barrier. It uses the oak gate implementation within the [Pewen family](../blocks/Pewen.md#block-behavior). [Item binding][item] · [Block settings][block] · [Placement][placement]

## Obtaining

At a Crafting Table, use **two rows of Stick–Pewen Plank–Stick**: **four Sticks and two [Pewen Planks](PewenPlanks.md)** make **one Pewen Fence Gate**. [Recipe][recipe]

This recipe uses Pewen Planks specifically. Obtaining those planks is a separate step: the bundled log-to-plank recipe has an incompatible ingredient format, as explained in the [Pewen recipe cautions](../blocks/Pewen.md#recipes-and-tools-that-need-caution).

It is listed with Building Blocks. The [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) can supply it in Creative, subject to its insertion checks; a visible catalog entry in Survival does not supply the item. [Listing][listing]

## Usage

The gate faces your horizontal direction when placed. Open or close it by hand, or control it with a redstone signal. A closed gate blocks movement; an open gate has no collision barrier. Beside suitable wall-tag blocks, it takes a lower wall-adjacent shape. [Placement and controls][controls] · [Shapes and collision][shape]

Read the [Pewen fence-connection limits](../blocks/Pewen.md#fence-connections) before using it in a pen. A correctly oriented gate can connect to Pewen Fence, but that does not establish ordinary connections between the surrounding Pewen Fence blocks. [Gate orientation][connections]

## Behavior

Normal Survival mining, including by hand, returns **one Pewen Fence Gate**. It does not require a special tool or Silk Touch, and Fortune does not increase the bundled loot count. Explosion recovery is conditional. [Block settings][block] · [Player drop gate][drop-gate] · [Harvest path][harvest] · [Loot][loot]

The gate has no waterlogged state. The bundled axe-mineable tag omits this block, so an ordinary axe does not receive its tag-based mining-speed bonus here. [Axe tag][axe-tag] · [Axe tool][axe-tool] · [Speed rule][tool-speed] [Gate states][states]

## Notes

The bundled fence-gates item tag omits Pewen Fence Gate, so the default gate furnace-fuel entry does not cover it. [Item tag][fuel-tag] · [Fuel list][fuels]

Related: [Pewen Fence](PewenFence.md) · [Pewen Door](PewenDoor.md) · [Items](Items.md)

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. These are checked bundled recipes, tags, loot, and placement rules; data packs can change them. No in-game crafting, placement, or harvesting test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L301-L310
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7010-L7033
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L40-L93
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/pewen_fence_gate.json
[listing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L237-L246
[controls]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FenceGateBlock.java#L131-L199
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FenceGateBlock.java#L45-L108
[connections]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FenceGateBlock.java#L207-L209
[drop-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L277-L293
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/pewen_fence_gate.json
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[axe-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L439-L441
[tool-speed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[states]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FenceGateBlock.java#L202-L205
[fuel-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/fence_gates.json
[fuels]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L42-L108
