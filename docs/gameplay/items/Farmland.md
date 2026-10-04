# Farmland

Farmland (`minecraft:farmland`) supports crops, but **making a field does not give you Farmland inventory items**. Tilling creates the placed soil; harvesting it returns Dirt. [Hoe use][hoe] · [Loot][farmland-loot]

## Obtaining

For ordinary Survival farming, use a **hoe on Dirt, Grass Block, or Dirt Path**, with air directly above and without clicking the underside. This turns the existing ground into Farmland in place. Coarse Dirt first becomes Dirt; Rooted Dirt becomes Dirt and drops Hanging Roots. See [making farmland](../blocks/Farmland.md#making-farmland). [Hoe conversions and conditions][hoe]

**Mining Farmland returns one Dirt, even with Silk Touch.** Fortune does not increase that drop. The complete bundled loot table has no Farmland-item branch, and no bundled crafting recipe produces the inventory item. [Loot][farmland-loot] · [Registration][farmland-reg] · [Tool gate][gate] · [Mining dispatch][mining]

Farmland is listed in Natural Blocks and can be requested through the [inventory browser in Creative](../mechanics/InventoryBrowser.md). That listing is separate from Survival acquisition: ordinary Survival insertion requests are skipped, and Creative requests still have cursor, space and feature restrictions. [Category entry][farm-category]

## Usage

If you already have a normal Farmland item, place it to make **dry Farmland, moisture 0**. If the cover above fails Farmland's survival rule, the item instead selects **Dirt** for placement. Solid cover normally fails that rule, with exceptions for fence gates and moving pistons. [Default state and placement][farmland] · [Block-item placement][placement]

Plant compatible crops such as [Wheat](../blocks/Wheat.md) above it and provide their required light. Wheat's ordinary random growth requires brightness **9 or higher at the crop**, while its survival threshold is **8**; hydration alone does not supply light. [Crop growth][crop] · [Crop survival][crop-survival]

## Behavior

Water within **four blocks horizontally**, at Farmland height or one block above, or rain at the block above can raise moisture to **7**. Water below the Farmland is outside that search. Dry soil can revert to Dirt unless a plant in the maintains-farmland tag keeps it, and falling entities can trample it under the game's conditions. Use the [Farmland guide](../blocks/Farmland.md) for hydration and plot protection. [Moisture updates][farmland] · [Water and maintenance checks][farm-water] · [Trampling][trampling]

## Notes

The inventory item and placed block share `minecraft:farmland`. The dry-placement description covers a normal supplied item; custom block-state components can override placement properties. Recipe and loot statements describe bundled data, not every possible command or data pack. [Item registration][items] · [Item state application][placement]

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`, using the bundled recipes, loot and tags and the active behavior paths linked here. No gameplay test was run; custom data-pack changes are outside this review.

[hoe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/HoeItem.java#L24-L88
[farmland-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/farmland.json
[farmland-reg]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L1281-L1291
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mining]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[farm-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L752-L767
[farmland]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FarmBlock.java#L42-L107
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L141
[crop]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/CropBlock.java#L83-L92
[crop-survival]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/CropBlock.java#L149-L156
[farm-water]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FarmBlock.java#L128-L139
[trampling]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/FarmBlock.java#L109-L125
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
