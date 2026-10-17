# Redstone Comparator

The Redstone Comparator item places `minecraft:comparator`, a directional signal component with compare/subtract modes and support for container-fullness readings.

## Obtaining and use

Craft it from three Redstone Torches, one Nether Quartz, and three ordinary Stone using the [block guide's recipe](../blocks/RedstoneComparator.md#crafting-and-collecting). Ordinary Survival mining returns the placed item without a specific tool requirement.

Place it on a supported surface with its rear toward the signal or container being read. The [Comparator block guide](../blocks/RedstoneComparator.md) explains the two modes, side inputs, delay, fullness calculation, blocked-Chest caveat, and a storage-indicator example. It also has an entry in the Redstone Blocks source category feeding the [combined inventory item browser](../mechanics/InventoryBrowser.md) for Creative requests. [Current catalog entry][catalog-entry] · [Combined browser][catalog-browser]

## Related pages

- [Comparator block behavior](../blocks/RedstoneComparator.md)
- [Nether Quartz](NetherQuartz.md)
- [Redstone basics](../redstone/Redstone.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. No gameplay test was run; the block guide carries recipe, loot, and circuit evidence.

- [Item registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Creative tab](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)

[catalog-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1294
[catalog-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
