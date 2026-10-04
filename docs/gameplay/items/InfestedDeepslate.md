# Infested Deepslate

## Obtaining

**Infested Deepslate is listed in the Functional Blocks catalog category.** Use the [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) in Creative mode to request it; ordinary Survival insertion requests are skipped. Mining does not return the infested item. No bundled crafting, smelting or stonecutting recipe produces it. [Creative entry]

**Without Silk Touch**, breaking it drops no block item. **With Silk Touch**, it drops **1 ordinary [Deepslate](Deepslate.md)**, never Infested Deepslate. This block has no correct-tool requirement. [Drop table] · [Block registration] See the [collection and infestation guide](../blocks/Deepslate.md#infested-deepslate).

## Usage

Place this item when you specifically want a Silverfish-bearing Deepslate block. It is separate from the [construction recipe chain](../blocks/Deepslate.md#crafting-and-smelting).

## Behavior

Breaking it without Silk Touch can release a Silverfish when `doTileDrops` is enabled. Silk Touch suppresses that spawn in the bundled enchantment tag. [Silverfish release] · [Spawn suppression]

Placement uses the clicked face to choose its axis, like ordinary Deepslate. [Infested placement] See [Infested Deepslate](../blocks/Deepslate.md#infested-deepslate) for host and Silverfish behavior, and [raw Deepslate orientation](../blocks/Deepslate.md#orienting-ordinary-deepslate) for placement.

## Notes

* This item is the item form of the `minecraft:infested_deepslate` block. [Item registration] · [Block registration]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Bundled recipes and loot were checked; no in-game crafting, mining or placement test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L537-L537
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6712-L6716
[Drop table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/infested_deepslate.json
[Creative entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1272-L1278
[Silverfish release]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/InfestedBlock.java#L61-L67
[Spawn suppression]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/prevents_infested_spawns.json
[Infested placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/InfestedRotatedPillarBlock.java#L23-L41
