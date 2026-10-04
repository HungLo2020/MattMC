# Dark Oak Shelf

Dark Oak Shelf is the item form of **`minecraft:dark_oak_shelf`**, a block that displays and stores three item stacks. [Shelves](../blocks/Shelves.md) covers its shared placed-block behavior. [Item registration][items] · [Shelf behavior][shelf]

## Obtaining

In a [Crafting Table](../blocks/CraftingTable.md), arrange **six [Stripped Dark Oak Logs](StrippedDarkOakLog.md)**: three across the top row and three across the bottom, leaving the middle row empty. The recipe makes **six Dark Oak Shelves**. Unstripped Dark Oak Logs, Dark Oak Planks and Stripped Dark Oak Wood do not substitute. [Exact recipe][recipe]

Use [Tree Logs and Roots](../blocks/TreeLogsAndRoots.md#stripping) to prepare the stripped material, or compare [all Shelf recipes](../blocks/Shelves.md#variants-and-crafting). Dark Oak Shelves are listed in the **Functional Blocks** category and can be acquired through the [inventory browser](../mechanics/InventoryBrowser.md) in Creative. [Category entry][creative]

## Usage

In Survival, use the **front face** with your main hand. While unpowered, using a display slot exchanges its whole stack with your held stack; an empty main hand retrieves the stored stack. The selected front slot determines which of the three stacks changes. [Front-slot selection][slots] · [Stack exchange][shelf]

Power alone moves no items. Using a single powered Shelf exchanges its three stacks with **hotbar positions 7–9**. See [Shelf controls and connected groups](../blocks/Shelves.md#redstone-and-hotbar-exchanges) for larger exchanges, and the [shared guide](../blocks/Shelves.md) for Comparator output and Hopper access. [Powered exchange][shelf]

## Behavior

With ordinary Survival block drops enabled, mining returns **one empty Dark Oak Shelf**. An axe is efficient, but hand mining also works. **Stored items spill separately, including with Silk Touch**; ordinary block loot does not copy them into the Shelf item. Collect the stored items before moving a loaded Shelf. [Loot][loot] · [Mining and drop rules][mining] [drop-rule][] · [Tool rules][registration] [harvest][] [axe][] [shelf-block-tag][] · [Removal and spill][chunk] [spill][] [containers][]

Shelf items can carry existing container data: placing such an item restores its stored contents, and Creative **Pick Block with block data** can copy a loaded Shelf this way. That capability does not change ordinary mining recovery. Explosion-caused Shelf drops also depend on the loot table's survival check. See [mining, saving, and moving contents](../blocks/Shelves.md#mining-saving-and-moving-contents). [Empty item default][items] · [Container components][entity] · [Placement][placement] · [Creative data copy][pick] · [Explosion condition][loot]

## Notes

- Each Dark Oak Shelf item is default furnace fuel for **300 burn ticks**. See [Shelf fuel and fire distinctions](../blocks/Shelves.md#fuel-and-fire-distinctions) for the other wood variants. [Fuel rules][fuel] · [Item tags][shelf-item-tag] [nonflammable][]
- Source-reviewed against MattMC commit [`78e8e0423084f010bb47e36132550619b37644c2`](https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2) on **2026-10-04**. These acquisition and behavior notes are source-derived, not gameplay-tested.

[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/dark_oak_shelf.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_shelf.json
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L418-L453
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1132-L1143
[shelf]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ShelfBlock.java#L124-L234
[slots]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SelectableSlotContainer.java#L17-L42
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L1125-L1184
[axe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[shelf-block-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/wooden_shelves.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[drop-rule]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L411-L416
[mining]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[chunk]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L322
[spill]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[containers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/Containers.java#L13-L45
[entity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/ShelfBlockEntity.java#L103-L113
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L64-L104
[pick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L667-L698
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[shelf-item-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/wooden_shelves.json
[nonflammable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/non_flammable_wood.json
