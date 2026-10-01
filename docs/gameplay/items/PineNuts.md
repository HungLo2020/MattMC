# Pine Nuts

Pine Nuts is a food item registered as `minecraft:pine_nuts`.

## Obtaining

[Pewen Branches](PewenBranch.md) and [Pewen Pines](PewenPines.md) include Pine Nuts in a chance-based loot pool when harvested without Shears or Silk Touch. Fortune improves the table's eligibility test, but the same single-roll pool also contains Sticks. A nut drop is therefore not guaranteed, and the eligibility numbers are not standalone final drop probabilities.

Shears and Silk Touch select the decorative block instead and disable this resource pool. A Pewen Sapling can grow the tree feature that places these blocks; a natural forest source remains unverified. Pine Nuts is also listed in Creative food items.

## Eating

Eating one restores **2 hunger points** (one drumstick icon) and adds **0.7 saturation** before the normal cap at current hunger. It is ordinary food, not an always-edible item: use it when hungry. No special effect is attached in its checked food registration.

For the difference between hunger and saturation, see [Hunger and healing](../mechanics/Hunger.md).

## Related pages

- [Pewen family](../blocks/Pewen.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game placement, harvesting, eating, or tree-generation test was run. Natural Pewen forest placement remains unverified.

- [Food values](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/Foods.java)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Creative food listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Branch loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/pewen_branch.json)
- [Pines loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/pewen_pines.json)
- [Resource-pool selection](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java)
