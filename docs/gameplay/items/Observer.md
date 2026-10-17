# Observer

The Observer item places `minecraft:observer`, which watches one adjacent position and pulses from the opposite face after a qualifying update.

## Obtaining and use

Craft it from six Cobblestone, two Redstone Dust, and one Nether Quartz using the [block guide's recipe](../blocks/Observer.md#crafting-and-collecting). Collect the placed block with a pickaxe. The Observer is also listed in the Redstone Blocks source category feeding the [combined inventory item browser](../mechanics/InventoryBrowser.md) for Creative requests. [Current catalog entry][catalog-entry] · [Combined browser][catalog-browser]

Its watching direction follows the nearest direction you are looking when placing it, including up and down. See the [Observer block guide](../blocks/Observer.md) for face orientation, what updates it detects, pulse timing, and a simple indicator. For changes to stored items, use a [Comparator](../blocks/RedstoneComparator.md).

## Related pages

- [Observer block behavior](../blocks/Observer.md)
- [Redstone basics](../redstone/Redstone.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. No gameplay test was run; the block guide carries recipe, mining, loot, and circuit evidence.

- [Item registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Creative tab](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)

[catalog-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1335
[catalog-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
