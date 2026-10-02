# Trapped Chest

Trapped Chest is the item form of `minecraft:trapped_chest`, a storage block that emits redstone power while counted users have it open. See the [Trapped Chest guide](../blocks/TrappedChest.md) for the full placement, storage, and circuit rules. [Block registration][registration] · [Item registration][item] · [Opening signal][opener-signal]

## Obtaining

Craft **one Chest + one Tripwire Hook → one Trapped Chest**, with no required arrangement. These are exact item ingredients. It is also in the Creative menu's Redstone Blocks tab; the guide covers [mansion acquisition and the TNT hazard](../blocks/TrappedChest.md#crafting-and-obtaining). [Complete recipe][recipe] · [Creative tab][creative-tab] · [Creative entry][creative-item]

## Usage

Place it for **27 storage slots**, or join two compatible Trapped Chests for **54 slots**. It does not join an ordinary Chest. Opening can power nearby circuitry and disable a neighboring Hopper's own transfer cycle. See [placement](../blocks/TrappedChest.md#placement-and-double-chests) and [Hoppers and Comparators](../blocks/TrappedChest.md#comparators-and-hoppers).

## Behavior

Its opening signal counts qualifying users, including Copper Golem inspections, up to strength 15. A Comparator reads inventory fullness separately. Blocked lids stop menu access, but a Hopper's container lookup bypasses that obstruction. See [opening signal](../blocks/TrappedChest.md#opening-signal-and-counted-users) and [obstruction](../blocks/TrappedChest.md#opening-and-obstruction).

## Notes

An axe is the tagged mining tool; a correct tool is not required for its ordinary Survival drop. **Breaking filled storage spills its contents separately.** The dropped chest item copies the custom name, not the stored items. See [saving and breaking](../blocks/TrappedChest.md#saving-contents-and-breaking-the-block). [Registration][registration] · [Axe tag][axe-tag] · [Complete loot][loot]

Source-reviewed at `cf1c134b3f9ff634490e448fe26c335b90f82227` on 2026-10-02; no gameplay test was run.

[registration]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L2834-L2838
[item]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L1032-L1034
[recipe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/trapped_chest.json#L1-L12
[creative-tab]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1283-L1288
[creative-item]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1325-L1334
[opener-signal]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/TrappedChestBlock.java#L41-L54
[loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/trapped_chest.json#L1-L30
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/mineable/axe.json#L25-L42
