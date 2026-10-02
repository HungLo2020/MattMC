# Iron Trapdoor

Iron Trapdoor (`minecraft:iron_trapdoor`) is a redstone-controlled panel that can hold water. See the [Iron Trapdoor guide](../blocks/IronFixtures.md#iron-trapdoor) for placement and ladder exits. [Registration][reg-trapdoor] · [English name][names]

## Obtaining

Craft **4 Iron Ingots in a 2 × 2 square → 1 Iron Trapdoor**; this fits the inventory crafting grid. Mine a placed trapdoor with an **unbroken pickaxe of any material**, including Wooden or Golden, to recover one item. Hand mining does not satisfy its normal drop requirement. [Recipe][recipe-iron_trapdoor] · [Loot][loot-iron_trapdoor] · [Collection rules](../blocks/IronFixtures.md#mining-and-collection)

## Usage

The panel lies horizontally at the top or bottom of the block when closed and stands vertically when open. The clicked face and height choose its placement. It requires no continuing attachment support. [Shape and placement](../blocks/IronFixtures.md#iron-trapdoor)

## Behavior

Power opens the trapdoor; loss of detected power closes it. Hand use and direct Wind Charge bursts do not toggle it. It can be waterlogged, and redstone toggling retains that water. When open directly above a Ladder with the same facing, it continues the [climbing route](../blocks/Ladder.md#exiting-through-a-trapdoor). [Power and water](../blocks/IronFixtures.md#power-and-water) · [Iron flags][iron-controls] · [Climbing check][climb]

## Notes

This is the item form of `minecraft:iron_trapdoor`. Source-reviewed at `3cc0d7d93500be7a135c5577511b5565406d42e6` on 2026-10-02; no in-game test was run. The canonical [Iron fixtures guide](../blocks/IronFixtures.md) owns detailed placed behavior and verification.

[reg-trapdoor]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/Blocks.java#L3144-L3148
[names]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/assets/minecraft/lang/en_us.json#L1739-L1744
[recipe-iron_trapdoor]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/recipe/crafting/iron_trapdoor.json#L1-L15
[loot-iron_trapdoor]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/loot_table/blocks/iron_trapdoor.json#L1-L21
[iron-controls]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L11-L46
[climb]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1652-L1676
