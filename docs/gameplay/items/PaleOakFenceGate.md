# Pale Oak Fence Gate

**Pale Oak Fence Gate** (`minecraft:pale_oak_fence_gate`) makes an openable passage through a fence or wall. See [Wood construction: fence gates](../blocks/WoodConstruction.md#fence-gates) guide for shared placement and connection rules. [Item registration][item]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), arrange **2 rows of Stick–Pale Oak Planks–Stick**: **4 [Sticks](Stick.md) + 2 [Pale Oak Planks](PaleOakPlanks.md) → 1 Pale Oak Fence Gate**. Both plank slots require Pale Oak Planks; other wood materials cannot substitute. [Recipe][recipe]

Mining normally returns **1 Pale Oak Fence Gate**, including by hand. An unbroken axe is faster; Silk Touch and Fortune do not change the drop. See [mining and drops](../blocks/WoodConstruction.md#mining-and-drops). [Block properties][block] · [Loot][loot]

## Usage

Place the gate facing along the intended passage; fence rails join across the perpendicular axis. A wall on either side along that rail axis lowers the gate's appearance, but keeps its closed collision height. [Placement and walls][gate]

## Behavior

Use the gate to toggle it. Opening while facing opposite its stored direction flips it to open away from that approach. **Closed collision is 1.5 blocks high; open collision is empty.** Gates have no waterlogged state. [Interaction, collision, and states][gate]

A gate starts open when placed with power. Later detected power changes open or close it; steady power still allows manual toggling. See [waterlogging and power](../blocks/WoodConstruction.md#waterlogging-and-power). [Redstone][gate]

## Notes

Placed Pale Oak gates can burn. Each gate item supplies **300 ticks of default Furnace fuel**; see [fire and fuel differences](../blocks/WoodConstruction.md#fire-and-furnace-fuel). [Fire table][fire] · [Fuel][fuel]

Related: [Pale Oak Fence](PaleOakFence.md) · [Pale Oak Planks](PaleOakPlanks.md) · [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game crafting, mining, placement, redstone, or fire test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1117-L1117
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L3993-L4002
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/pale_oak_fence_gate.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_fence_gate.json
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FenceGateBlock.java
[fire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FireBlock.java
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
