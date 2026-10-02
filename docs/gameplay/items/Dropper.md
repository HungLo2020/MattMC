# Dropper

The Dropper item places `minecraft:dropper`, a nine-slot device that transfers one selected item into a container in front or ejects it as a loose item when no container is found.

## Obtaining and use

Craft it from seven Cobblestone and one Redstone Dust using the [shared device recipe](../blocks/DispenserAndDropper.md#crafting-and-collecting). Collect a placed Dropper with a pickaxe. Its ordinary mined item does not retain its loaded contents.

Face the output toward the destination, load items, and give it a redstone activation. A full or incompatible container leaves the selected item in the Dropper. It needs another activation for another attempt. The [Dispenser and Dropper guide](../blocks/DispenserAndDropper.md) covers timing, random slot choice, upward transfer, persistence, and a button-controlled example.

## Related pages

- [Placed device mechanics](../blocks/DispenserAndDropper.md)
- [Dispenser](Dispenser.md) and [Hopper](../blocks/Hopper.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. No gameplay test was run. The canonical block guide supplies recipe, mining, drop, and behavior evidence. The item has a Redstone Creative entry.

- [Block-item registrations](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Redstone Creative entries](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
