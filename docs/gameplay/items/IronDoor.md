# Iron Door

Iron Door (`minecraft:iron_door`) places a two-block-high, redstone-controlled opening. See the [Iron Door guide](../blocks/IronFixtures.md#iron-door) for floor support, facing and hinge behavior. [Registration][reg-door] · [English name][names]

## Obtaining

Craft **6 Iron Ingots in two columns of three → 3 Iron Doors** at a Crafting Table. A placed door can be collected **by hand** in this source: its registration has no correct-tool drop requirement, though a pickaxe is efficient. One door item drops for the assembly, through the lower-half loot entry. Stronghold prison halls are one [verified generated source](../blocks/IronFixtures.md#finding-generated-examples). [Recipe][recipe-iron_door] · [Properties][reg-door] · [Harvest gate][harvest] · [Loot][loot-iron_door]

## Usage

Leave two vertical spaces and a sturdy upper face below the lower half. One item places both halves. Removing the floor or either half removes the unsupported remainder. [Placement and support](../blocks/IronFixtures.md#iron-door)

## Behavior

The door requires power to open and closes when detected power leaves; either half can detect it. Ordinary hand use and direct Wind Charge bursts do not toggle it. Opening rotates the panel to the side, where it retains collision. Iron Doors have no waterlogged state. [Power controls](../blocks/IronFixtures.md#power-and-water) · [Iron flags][iron-controls] · [Door shape][door-shape] · [State list][door-states]

## Notes

This is the item form of `minecraft:iron_door`. Source-reviewed at `3cc0d7d93500be7a135c5577511b5565406d42e6` on 2026-10-02; no in-game test was run. The canonical [Iron fixtures guide](../blocks/IronFixtures.md) owns detailed placed behavior and verification.

[reg-door]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/Blocks.java#L1764-L1768
[names]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/assets/minecraft/lang/en_us.json#L1739-L1744
[recipe-iron_door]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/recipe/crafting/iron_door.json#L1-L16
[harvest]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L656
[loot-iron_door]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/loot_table/blocks/iron_door.json#L1-L30
[iron-controls]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L11-L46
[door-shape]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L47-L108
[door-states]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L266-L268
