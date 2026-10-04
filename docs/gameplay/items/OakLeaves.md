# Oak Leaves

Collect Oak Leaves for persistent foliage, or harvest without Shears or Silk Touch for the [Oak leaf drops](../blocks/TreeLeaves.md#oak-leaves).

## Obtaining

In ordinary Survival harvesting with block drops enabled, **[Shears](Shears.md) or a tool with Silk Touch** drops **one Oak Leaves item**. This replaces the material drops: it does not also roll for saplings, sticks, or apples. [Variant loot][loot] · [Harvest dispatch][harvest] · [Block-drop rule][block-drops]

Without either, ordinary non-explosion harvesting has a separate chance to drop one [Oak Sapling](OakSapling.md) (**5% without Fortune; 10% with Fortune III**) and **1–2 [Sticks](Stick.md)**, plus an independent [Apple](Apple.md) roll. Any or all of these rolls can fail. See the full [Fortune chances](../blocks/TreeLeaves.md#fortune-chances) for the other rolls and intermediate enchantment levels. [Variant loot][loot]

## Usage

Use Oak Leaves for tree canopies, hedges, and natural decoration. The shared [building guide](../blocks/TreeLeaves.md#building-with-leaves) covers waterlogging, fire, and composting.

## Behavior

Ordinary placement from the item makes the leaves **persistent**, so a built hedge does not need nearby logs to avoid distance-based decay. Breaking placed leaves still uses the same tool and drop rules. [Placement][placement] · [Variant loot][loot]

Natural, non-persistent leaves decay on a selected random tick at **distance 7**. Decay uses the no-Fortune drops, not the held tool's enchantments. See [keeping leaves or letting them decay](../blocks/TreeLeaves.md#keeping-leaves-or-letting-them-decay) for the shared log-support rules. [Decay][decay] · [Tool-free drops][empty-tool]

## Notes

* This item is the item form of the `minecraft:oak_leaves` block. [Registration][registration]
* It appears in the Natural Blocks creative tab. [Creative entry][creative]
* Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. No in-game harvest or decay test was run; data packs and server rules can alter drops.

[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/oak_leaves.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L291
[block-drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LeavesBlock.java#L179-L184
[decay]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LeavesBlock.java#L51-L66
[empty-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L345-L368
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L268
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L855-L865
