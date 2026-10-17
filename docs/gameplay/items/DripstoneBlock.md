# Dripstone Block

Dripstone Block (`minecraft:dripstone_block`) is the inventory form of the solid **[Dripstone Block](../blocks/Dripstone.md#dripstone-block)**. It is used for building and as the specific support for renewable Pointed Dripstone growth. [Item registration][full-item] · [Block registration][blocks] · [Growth requirement][growth-source]

## Obtaining

Craft **four [Pointed Dripstone](PointedDripstone.md) in a 2 × 2 square** into **one Dripstone Block**, including in the personal crafting grid. The checked bundled recipes provide no reverse conversion. [Exact recipe][recipe]

The **[family acquisition guide](../blocks/Dripstone.md#finding-a-starting-supply)** traces natural full blocks in Overworld Dripstone Caves and the Normal preset's Dry Midlands. A level-3 Mason Villager can also select an offer with a **base price of 1 Emerald for 4 Dripstone Blocks**, allowing **16 uses**. It is one option in the Mason's level-3 pool, not a guaranteed offer. Both dripstone items are listed in Creative. [Mason listing][mason] · [Active villager selection][mason-call] · [Random offer choice][offer-selection] · [Offer values][offer-values] · [Creative entries][creative]

Use a **correct, unbroken pickaxe** for ordinary Survival collection; Wood is sufficient with the bundled tags. Hand mining loses the item. A successful harvest returns **one Dripstone Block**; Silk Touch is unnecessary and Fortune adds nothing. [Correct-tool requirement][blocks] · [Pickaxe tag][pickaxe-tag] · [Wood restrictions][wood-tag] · [Tool gate][harvest] · [Broken-tool gate][broken] · [Loot][loot-full]

## Usage

For **[natural Pointed Dripstone growth](../blocks/Dripstone.md#growing-more-pointed-dripstone)**, place a Water source block above the full block and downward Pointed Dripstone below it. Keep the tip dry, unmerged, and able to grow. The full block does not make new pointed pieces by itself. Bone Meal does not accelerate this process. [Source requirement][growth-source] · [Active growth][growth] · [Bone Meal dispatch][bonemeal]

An ordinary sturdy support can instead be used for **[Cauldron dripping](../blocks/Cauldrons.md#filling-from-pointed-dripstone)** or **[Mud drying](../blocks/ClayAndBricks.md#turning-mud-into-clay)**. Those uses do not require a Dripstone Block. [Support test][support] · [Fluid and Mud source lookup][fluid]

## Behavior

The full block has **1.5 hardness**, **1 blast resistance**, no orientation or waterlogged state, and no continuing support requirement. It stays in place when the block below is removed. Its behavior is separate from the falling Pointed Dripstone attached to it. [Properties][blocks] · [Ordinary block type][ordinary-block] · [Default shape and survival][default-block]

## Notes

The full block and Pointed Dripstone are separate registered block/item IDs. Explosion loot has a survives-explosion condition, and ordinary item drops require `doTileDrops`. See **[Mining](../mechanics/Mining.md)** for shared tool rules. [Registrations][blocks] · [Loot][loot-full] · [Drop spawning][drops]

## Sources and verification

Source-reviewed on **2026-10-02** at `a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd`. Checked registration, complete bundled recipe/loot references, expanded mining and tier tags, active Mason selection, and growth callers. The family guide records the reviewed generation chains. No in-game crafting, mining, placement, growth, trading, or generation test was run. Data packs can change the documented data-driven rules.

[full-item]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/Items.java#L110-L110
[blocks]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L6521-L6545
[growth-source]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L559-L565
[recipe]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/recipe/crafting/dripstone_block.json#L1-L15
[mason]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L647-L668
[mason-call]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/npc/Villager.java#L818-L836
[offer-selection]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L232
[offer-values]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1430-L1478
[creative]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L785-L795
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L271-L280
[wood-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json#L1-L7
[harvest]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[loot-full]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/loot_table/blocks/dripstone_block.json#L1-L21
[growth]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L318-L395
[bonemeal]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L78
[support]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L425-L499
[fluid]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L523-L573
[ordinary-block]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L7274-L7293
[default-block]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[drops]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Block.java#L410-L420
