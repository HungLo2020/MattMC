# Ladder

Ladder (`minecraft:ladder`) is a wall-supported climbing block. Its [placed-block guide](../blocks/Ladder.md) covers climbing controls, support, water and trapdoor exits. [Registration][reg-ladder] · [English name][name-ladder]

## Obtaining

Craft **7 Sticks in an H pattern → 3 Ladders** at a Crafting Table: Stick–empty–Stick across the top and bottom, and three Sticks in the middle. Placed ladders drop one Ladder, including when mined by hand; an unbroken axe is efficient. Tall Stronghold libraries are one [verified generated source](../blocks/Ladder.md#crafting-and-collection). [Recipe][recipe-ladder] · [Loot][loot-ladder] · [Collection rules](../blocks/Ladder.md#crafting-and-collection)

## Usage

Place each Ladder against a sturdy side face to build a vertical route. Every rung needs its own backing support. Move into its block space to climb; during ordinary non-flying play, sneak stops downward sliding. [Support](../blocks/Ladder.md#placement-and-support) · [Climbing controls](../blocks/Ladder.md#climbing-and-descent)

## Behavior

A Ladder is waterloggable and is removed if its backing face stops supporting it. An open trapdoor immediately above it continues climbing only when their horizontal facings match. The item is also **300 ticks of default furnace fuel**. [Support and water][ladder] · [Trapdoor continuation](../blocks/Ladder.md#exiting-through-a-trapdoor) · [Fuel](../blocks/Ladder.md#water-and-fuel)

## Notes

This is the item form of `minecraft:ladder`. Source-reviewed at `3cc0d7d93500be7a135c5577511b5565406d42e6` on 2026-10-02; no in-game test was run. The canonical [Ladder guide](../blocks/Ladder.md) owns detailed placed behavior and verification.

[reg-ladder]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/Blocks.java#L1411-L1415
[name-ladder]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/assets/minecraft/lang/en_us.json#L1768
[recipe-ladder]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/recipe/crafting/ladder.json#L1-L16
[loot-ladder]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/loot_table/blocks/ladder.json#L1-L21
[ladder]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/LadderBlock.java#L25-L125
