# Pointed Dripstone

Pointed Dripstone (`minecraft:pointed_dripstone`) is the one inventory item used to place upward stalagmites and downward stalactites. Use the **[Dripstone guide](../blocks/Dripstone.md#pointed-dripstone)** for the complete placement, support, waterlogging, growth, and damage rules. [Item registration][point-item] · [Placed states][states]

## Obtaining

Collect each segment by hand or with a tool; an unbroken pickaxe is faster. Ordinary mining yields **one Pointed Dripstone per segment**, without requiring Silk Touch, and Fortune adds nothing. Explosions have a survival condition. [Properties][blocks] · [Pickaxe tag][pickaxe-tag] · [Tool gate][harvest] · [Loot][loot-pointed]

The family guide covers the checked **[Dripstone Caves and Dry Midlands generation routes](../blocks/Dripstone.md#finding-a-starting-supply)**. A selected Wandering Trader offer sells **2 Pointed Dripstone for a base cost of 1 Emerald**, with **five uses**. The active trader randomly chooses offers, so this listing is not guaranteed. Neither a crafting recipe nor a Dripstone-Block-to-Pointed-Dripstone conversion exists in the checked bundled recipe set. [Trader listing][wandering] · [Active selection][wandering-call] · [Random choice][offer-selection]

Once you have a starter, use **[natural growth](../blocks/Dripstone.md#growing-more-pointed-dripstone)** to renew it. Both dripstone items are listed in Creative. [Growth callback][growth] · [Creative entries][creative]

## Usage

Craft **four in a 2 × 2 square** into **one [Dripstone Block](DripstoneBlock.md)**. This fits the personal crafting grid. Keep a pointed starter if you plan to grow more. [Exact recipe][recipe]

For resource setups, follow **[Cauldron filling](../blocks/Cauldrons.md#filling-from-pointed-dripstone)** or **[Mud-to-Clay conversion](../blocks/ClayAndBricks.md#turning-mud-into-clay)**. Their requirements differ from natural growth, especially the source/support arrangement and whether a waterlogged tip can qualify. [Separate transfer branches][random] · [Source lookup][fluid]

## Behavior

Place it on suitable upper/lower support faces or extend a segment pointing the same way. Secondary-use placement can keep previously unmerged opposing tips separate. Ordinary support need not be a Dripstone Block; **natural growth does require one, with a Water source block above it**. Bone Meal does not grow it. [Placement][placement] · [Support and merging][support] · [Growth source][growth-source] · [Bone Meal dispatch][bonemeal]

Removing hanging support can release falling spikes. Landing on an upward unmerged tip can also amplify fall damage; see **[both damage paths](../blocks/Dripstone.md#falling-and-landing-damage)** before building a trap or harvesting overhead. [Support updates][update] · [Falling chain][fall-spawn] · [Landing damage][landing-damage]

## Notes

The item is shared by all **20** combinations of the placed block's direction, thickness, and waterlogged states; it is not a separate item for each shape. Normal block drops require `doTileDrops`; falling-piece recovery uses the separate `doEntityDrops` check. [State definition][states] · [Block drops][drops] · [Falling recovery][fall-land]

## Sources and verification

Source-reviewed on **2026-10-02** at `a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd`. Registration, relevant recipes and loot, tools, placement, growth, active trader selection, and the family guide's generation chains were checked. No in-game mining, placement, growth, damage, trading, or generation test was run. Data packs can change recipes, tags, loot, and world generation.

[point-item]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/Items.java#L2519-L2519
[states]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L46-L98
[blocks]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L6521-L6545
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L271-L280
[harvest]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[loot-pointed]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/loot_table/blocks/pointed_dripstone.json#L1-L21
[wandering]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L811-L828
[wandering-call]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L141
[offer-selection]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L232
[growth]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L318-L395
[creative]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L785-L795
[recipe]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/recipe/crafting/dripstone_block.json#L1-L15
[random]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L181-L233
[fluid]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L523-L573
[placement]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L235-L259
[support]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L425-L499
[growth-source]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L559-L565
[bonemeal]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L78
[update]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L100-L135
[fall-spawn]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L288-L316
[landing-damage]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L151-L158
[drops]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Block.java#L410-L420
[fall-land]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L172-L234
