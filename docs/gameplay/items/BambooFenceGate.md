# Bamboo Fence Gate

**Bamboo Fence Gate** (`minecraft:bamboo_fence_gate`) provides a passage through fences or walls that opens by hand or redstone. See [Wood construction: fence gates](../blocks/WoodConstruction.md#fence-gates). [Item registration][item]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), fill **2 rows with Stick–Bamboo Planks–Stick**. The total is **2 [Bamboo Planks](BambooPlanks.md) + 4 [Sticks](Stick.md) → 1 Bamboo Fence Gate**. Bamboo Mosaic and other plank materials cannot replace the Bamboo Planks. [Recipe][recipe]

Mining a gate normally returns **1 Bamboo Fence Gate**, including by hand. An unbroken axe is efficient; see [mining and drops](../blocks/WoodConstruction.md#mining-and-drops). [Loot][loot]

## Usage

Face along the intended passage when placing the gate. Fence rails connect across the perpendicular axis, so rotate the gate if its neighboring fences do not join. The gate can stand without adjacent fences. [Placement and connection direction][gate]

## Behavior

Use the gate to open or close it. Closed collision is **1.5 blocks high**; an open gate has no movement-blocking collision. It has no waterlogged state. [Gate shapes and states][gate]

A gate starts open if powered during placement. Later detected power changes set it open or closed, but steady power does not prevent a manual toggle. See [Wood construction: waterlogging and power](../blocks/WoodConstruction.md#waterlogging-and-power) for the shared redstone behavior. [Interaction and redstone checks][gate]

## Notes

Related: [Bamboo Fence](BambooFence.md) · [Bamboo Pressure Plate](BambooPressurePlate.md) · [Items](Items.md)

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. No in-game crafting, mining, placement, or circuit test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1119
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/bamboo_fence_gate.json
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/bamboo_fence_gate.json
[gate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/FenceGateBlock.java
