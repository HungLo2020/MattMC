# Pressure plates

Pressure plates provide redstone input when eligible entities overlap the small detection region just above them. Use **Stone** to filter out dropped items, **Oak** for broader detection, or a **weighted plate** for a signal that changes with the number of entities.

This guide covers Stone (`minecraft:stone_pressure_plate`), Oak (`minecraft:oak_pressure_plate`), Light Weighted (`minecraft:light_weighted_pressure_plate`), and Heavy Weighted (`minecraft:heavy_weighted_pressure_plate`). Other materials have their own registrations.

## Crafting and collecting

Place two matching ingredients side by side to make **one plate**. All four recipes fit the inventory's 2 × 2 crafting grid.

| Plate | Two ingredients | Tagged mining tool |
| --- | --- | --- |
| Stone | Ordinary Stone | Pickaxe |
| Oak | Oak Planks | Axe |
| Light Weighted, gold | Gold Ingots | Pickaxe |
| Heavy Weighted, iron | Iron Ingots | Pickaxe |

The Stone/Oak recipes name those exact blocks, not Cobblestone or any kind of planks. All four checked registrations lack a correct-tool requirement for their own drops: ordinary Survival mining returns the matching plate even without its tagged tool.

## Placement and output

Place a plate on an upper face that supplies rigid support or center support; an ordinary full solid block is a simple choice. It cannot attach to a wall or ceiling. Removing valid support breaks the plate.

A pressed plate supplies its signal to neighbors and direct power into its supporting block. Take the output with a short [dust](RedstoneDust.md) connection or a receiver beside it. Weighted outputs may be very weak, so a long dust run can lose the signal before the receiver.

## What each plate detects

| Plate | Eligible entity types | Output |
| --- | --- | --- |
| Stone | Living entities, including players and mobs | 15 when at least one qualifies; otherwise 0 |
| Oak | Any eligible entity type, including dropped items | 15 when at least one qualifies; otherwise 0 |
| Light Weighted | Any eligible entity type | One signal level per entity, capped at 15 |
| Heavy Weighted | Any eligible entity type | One signal level per ten entities, rounded up, capped at 15 |

Every plate excludes spectators and entities whose implementation ignores block triggers. “Living entities” is the game's class category: ordinary Armor Stands also belong to it, while marker Armor Stands opt out. Being a mob is therefore not, by itself, a promise that an entity activates a plate.

Weighted plates count **entities, not individual items or mass**. One dropped stack containing 64 Cobblestone counts as one item entity, just as a one-item stack does. If nearby dropped stacks merge, the count can decrease even though the total items stay the same.

A Heavy plate gives 1 for 1–10 entities, 2 for 11–20, and reaches 15 at **141 or more**. Its internal count cap is 150; 150 is not the minimum required for output 15. Both weighted plates give 0 when empty.

## Activation and release timing

An unpowered plate checks for a qualifying occupant when an entity enters its block region. Once powered, it schedules repeated checks:

| Plate | Occupancy recheck | At 20 game ticks/second |
| --- | --- | --- |
| Stone and Oak | Every 20 game ticks, or 10 redstone ticks | 1 second |
| Light and Heavy Weighted | Every 10 game ticks, or 5 redstone ticks | 0.5 seconds |

These are **recheck intervals**, not fixed button pulses. The plate stays powered while qualifying occupants remain. It notices departure, and changes in a weighted count while already powered, at a later scheduled check. Leaving does not start a fresh full-length release timer. Tick-rate changes or lag alter real elapsed time.

## Small example: a floor indicator

This layout is source-derived and has **not been tested in game**.

1. Put an Oak Pressure Plate on a solid floor.
2. Put a Redstone Lamp immediately beside the plate, at the same block level. Keep the plate accessible from another side.
3. Walk onto it, then step away. The Lamp should light, then turn off after the plate's empty recheck and the Lamp's separate off delay.
4. Drop an item onto the plate and move clear. Oak should respond while the item remains; Stone should ignore that dropped item.

A weighted plate also powers an adjacent Lamp with a single eligible entity, but the Lamp shows only on/off, not the exact strength. Use a strength-sensitive circuit when you need to distinguish counts.

## Related pages

- [Stone item](../items/StonePressurePlate.md) and [Oak item](../items/OakPressurePlate.md)
- [Light Weighted item](../items/LightWeightedPressurePlate.md) and [Heavy Weighted item](../items/HeavyWeightedPressurePlate.md)
- [Tripwire](Tripwire.md), [Buttons](Buttons.md), and [Redstone basics](../redstone/Redstone.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Registrations, recipes, loot, current entity-contact callbacks, and scheduled-tick dispatch were checked. No gameplay test of entity detection, count changes, output, or release timing was run. This describes the checked MattMC implementation, not every Minecraft edition.

- [Registrations, count caps, and mining properties](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Stone recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/stone_pressure_plate.json)
- [Oak recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/oak_pressure_plate.json)
- [Light Weighted recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/light_weighted_pressure_plate.json)
- [Heavy Weighted recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/heavy_weighted_pressure_plate.json)
- [Stone loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/stone_pressure_plate.json)
- [Oak loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/oak_pressure_plate.json)
- [Light Weighted loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/light_weighted_pressure_plate.json)
- [Heavy Weighted loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/heavy_weighted_pressure_plate.json)
- [Support, entity filtering, signal output, and rechecks](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java)
- [Stone/Oak detection and binary output](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/PressurePlateBlock.java)
- [Block-set sensitivity](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java)
- [Weighted output calculation and recheck period](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/WeightedPressurePlateBlock.java)
- [Pickaxe tag](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Axe tag](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/axe.json)
- [Wooden pressure-plate membership](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/wooden_pressure_plates.json)
- [Armor Stand entity class and trigger exceptions](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java)
- [Dropped-item merging](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/item/ItemEntity.java)
- [Entity block-intersection dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java)
- [Game-time scheduling](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/LevelAccessor.java)
- [Scheduled tick dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java)
- [Lamp response](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/RedstoneLampBlock.java)
