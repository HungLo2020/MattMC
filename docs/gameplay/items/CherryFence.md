# Cherry Fence

**Cherry Fence** (`minecraft:cherry_fence`) forms railings and barriers. The [Wood construction fence guide](../blocks/WoodConstruction.md#fences) covers its shared connection and collision rules. [Item registration][item]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), fill **2 rows with Cherry Planks–Stick–Cherry Planks**: **4 [Cherry Planks](CherryPlanks.md) + 2 [Sticks](Stick.md) → 3 Cherry Fences**. Every plank slot requires Cherry Planks; other plank materials cannot substitute in this recipe. [Recipe][recipe]

Ordinary Survival mining returns **1 Cherry Fence**, including by hand. No tool or material tier is required for that drop; an **unbroken axe** is faster. Silk Touch and Fortune do not change the bundled loot result. See [mining and drops](../blocks/WoodConstruction.md#mining-and-drops) for shared tool and explosion rules. [Loot][loot] · [Block registration][block]

## Usage

Use Cherry Fences for railings, pens, or decorative trim. Use a matching [Cherry Fence Gate](CherryFenceGate.md) where you need a passage.

To anchor nearby eligible entities **already leashed to you**, use the fence with an empty hand. This transfers their connections to a fence knot without consuming another Lead. See [Lead: fence knots](Lead.md#fence-knots) for attaching, releasing, and cutting connections. [Fence interaction][fence] · [Binding action][lead-bind]

## Behavior

Cherry Fence connects horizontally to the other tagged wooden fences, including Crimson and Warped, suitable sturdy side faces, and correctly oriented fence gates. See [fence connections](../blocks/WoodConstruction.md#fences) for exceptions and the separate Nether Brick Fence group. [Connection checks][fence] · [Wooden-fence tag][fence-tag]

Its collision reaches **1.5 blocks high**, above the visible one-block post. It can be waterlogged, and losing a rail connection changes the shape without removing the fence. See [waterlogging](../blocks/WoodConstruction.md#waterlogging-and-power) for placement and bucket rules. [Fence dimensions and updates][fence] · [Collision and water state][cross]

## Notes

A placed Cherry Fence can burn when it is not waterlogged. Its item supplies **300 default furnace burn ticks**. See [fire and furnace fuel](../blocks/WoodConstruction.md#fire-and-furnace-fuel) for the waterlogging and fuel details. [Fire rules][fire] · [Fuel rules][fuel] · [Fence fire entries][fire-entry]

Related: [Cherry Planks](CherryPlanks.md) · [Cherry Fence Gate](CherryFenceGate.md) · [Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game crafting, mining, placement, leashing, fire, or furnace test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L498-L498
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L4063-L4072
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/cherry_fence.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/cherry_fence.json
[fence]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FenceBlock.java#L37-L124
[fence-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/wooden_fences.json
[cross]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/CrossCollisionBlock.java#L23-L80
[lead-bind]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/LeadItem.java#L35-L58
[fire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FireBlock.java#L223-L243
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[fire-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FireBlock.java#L342-L351
