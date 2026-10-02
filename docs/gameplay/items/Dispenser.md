# Dispenser

The Dispenser item places `minecraft:dispenser`, a nine-slot device that performs a selected item's registered action when triggered by redstone.

## Obtaining and use

Craft it from seven Cobblestone, one Bow, and one Redstone Dust using the [shared device recipe](../blocks/DispenserAndDropper.md#crafting-and-collecting). Collect a placed Dispenser with a pickaxe. Its ordinary mined item does not preserve the loaded inventory; contents drop separately.

Load the placed device and trigger it with a Button or another redstone source. The [Dispenser and Dropper guide](../blocks/DispenserAndDropper.md) explains output facing, the four-game-tick delay, random slot selection, and verified projectile, Bone Meal, and bucket actions. Choose a [Dropper](Dropper.md) for direct insertion into another container.

## Related pages

- [Placed device mechanics](../blocks/DispenserAndDropper.md)
- [Hopper](../blocks/Hopper.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. No gameplay test was run. The canonical block guide supplies recipe, mining, drop, and behavior evidence. The item has a Redstone Creative entry.

- [Block-item registrations](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Redstone Creative entries](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
