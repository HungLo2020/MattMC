# Hopper

A Hopper (`minecraft:hopper`) is a five-slot container that pushes items toward its output and pulls items from above. Redstone power disables its own automatic transfer attempt. It is useful for moving resources between storage and workstations, but its output direction and the destination's slot rules matter.

## Crafting and collecting

Craft one from **five Iron Ingots and one Chest**:

| Left | Center | Right |
| --- | --- | --- |
| Iron Ingot | Empty | Iron Ingot |
| Iron Ingot | Chest | Iron Ingot |
| Empty | Iron Ingot | Empty |

The recipe names the ordinary Chest item; other chest-like items are not accepted merely by appearance. Use a **pickaxe** to collect a placed Hopper. The block requires the correct tool for drops, belongs to the pickaxe tag, and its loot returns the Hopper while preserving its custom name.

Its inventory is saved as separate block-entity contents. On ordinary removal, the container-removal path drops contents into the world; the dropped Hopper item is not a packed portable inventory. Clear or secure valuable items before dismantling a line.

## Point the output correctly

The outlet can point **down or horizontally**, never upward. Placement uses the face clicked: a horizontal face points the outlet back toward the clicked block, while either vertical face produces a downward outlet. Check the outlet before filling the system.

The Hopper pulls from the container directly **above it**, regardless of the output direction. Its outlet pushes into a container at the adjacent output position. Pointing at open air does not make it spit items like a Dropper.

## Transfer behavior and rate

An enabled Hopper that is not on cooldown:

1. Tries to push one item from its inventory into the attached container
2. If its own inventory has room, tries to pull one item from the source container above, or collect a nearby dropped-item entity when no source container exists
3. Starts an **8-game-tick cooldown** after a successful transfer attempt

At normal tick speed, eight ticks are 0.4 seconds. A sustained one-item container-transfer direction therefore has a nominal rate of **2.5 items per second** under suitable conditions. This is not a universal item-entity pickup limit: pickup can merge several items from a dropped stack, and receiving Hopper timing is adjusted within chains.

Push and pull may both succeed in one cycle. A full destination, incompatible stack components, blocked slot permissions, or lack of input can stop progress. Items of the same name do not necessarily merge if their components differ.

## Picking up dropped items

Without a source container above, the Hopper searches its intake region for dropped items. A full-collision block above normally blocks that search, except for blocks in the `does_not_block_hoppers` tag. The bundled exception tag contains the beehives tag.

Pickup is not a promise that every nearby item, or an item beyond the intake region, will enter. If a container exists above, the normal source-container path takes precedence over searching for loose items. A complete dropped stack may be collected when there is room; a partly accepted stack can remain in the world.

## Redstone locking

Neighbor redstone power sets `enabled` to false and prevents this Hopper's own push/pull attempt. Removing power enables it again. A [Lever](Lever.md) provides a simple control source.

**Locking one Hopper is not an access lock on its inventory.** Other enabled Hoppers can still insert into or extract from that container through their own transfer paths. Players can also open it. Design the surrounding line, rather than assuming a powered Hopper seals every direction.

The Hopper exposes an analog fullness signal for a [Comparator](RedstoneComparator.md), ranging from 0 for empty to 15 for full. Stack limits affect fullness; a non-stackable item fills its slot differently from one item in a 64-stack.

## Furnace connections

The [Furnace](Furnace.md#hopper-automation) exposes its input from above, fuel from the sides, and output from below. Point the feeding Hoppers at the appropriate faces. Container insertion and extraction still use that device's allowed slots and items; a Hopper does not bypass recipe or fuel validation.

## Related pages

- [Hopper item](../items/Hopper.md)
- [Chest](Chest.md)
- [Furnace](Furnace.md)
- [Redstone](../redstone/Redstone.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game transfer, pickup, redstone timing, comparator, or furnace-automation test was run. Container rules, tags, and data packs can change results.

- [Recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/hopper.json)
- [Placement, redstone, and menu](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/HopperBlock.java)
- [Transfer order, cooldown, item pickup, and saved inventory](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java)
- [Correct-tool requirement](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Pickaxe tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Hopper block loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/hopper.json)
- [Container removal drops](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java)
- [Above-block pickup exceptions](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/does_not_block_hoppers.json)
- [Fullness signal calculation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java)
- [Furnace sided slots](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java)
