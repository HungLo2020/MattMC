# Jack o'Lantern

A **Jack o'Lantern** (`minecraft:jack_o_lantern`) places a full carved-face block with **light level 15**. It can also complete the checked Snow, Iron, and Copper Golem head patterns. [Registration][block] · [Head patterns][pumpkin]

## Obtaining

Use the [Jack o'Lantern crafting and harvesting guide](../blocks/JackOLantern.md#crafting-and-harvesting) for its exact two-ingredient recipe. Mining it normally returns one Jack o'Lantern, without Silk Touch or a correct-tool requirement. [Loot][loot]

## Usage

Place it as a directional light, including in an underwater wall, or use it as the final golem head. Follow the [golem and dispenser distinctions](../blocks/JackOLantern.md#golem-heads-and-dispensers) before automating construction.

## Behavior

The [placed-block guide](../blocks/JackOLantern.md) owns facing, light, water behavior, construction consumption, and wild-Crow avoidance. The normal item has no wearable-head component, and a dispenser ejects it instead of placing a golem head. [Item registration][item] · [Dispenser fallback][fallback] · [Carved Pumpkin-only handler][dispenser]

## Notes

- This item is the item form of `minecraft:jack_o_lantern`
- Source-reviewed on **2026-10-02** at `cf1c134b3f9ff634490e448fe26c335b90f82227`; no gameplay construction, dispenser, or placement test was run

[block]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L2095-L2106
[pumpkin]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java
[loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/jack_o_lantern.json
[item]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L504-L514
[fallback]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L102-L113
[dispenser]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L298-L317
