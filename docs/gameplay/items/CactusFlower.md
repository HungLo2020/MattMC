# Cactus Flower

Cactus Flower (`minecraft:cactus_flower`) is a decorative plant, a **Pink Dye** ingredient, and a Bee food. You can collect it by hand and display it away from a cactus. [Item registration][items] · [Loot][flower-loot] · [Dye recipe][dye] · [Bee food][bee-food]

## Obtaining

Break a Cactus Flower **by hand or with an ordinary tool** to recover **one flower**. Shears and Silk Touch are unnecessary; Fortune adds no extra flower. [Registration][flower-reg] · [Loot][flower-loot] · [Tool gate][gate]

Some generated cactus patches, including **Desert** patches, carry flowers; not every cactus has one. Flowers can also grow through the cactus's **age-8 flower attempt**. With air above and the prospective cactus position meeting its survival rules, that attempt has a **10% chance on a one- or two-block column**, or **25% on a column at least three blocks tall**. These are chances at that age, not every random tick. A flower blocks further upward growth until removed; a three-block-or-taller top that reaches age 15 does not repeat the age-8 attempt. See [Cactus growth and flowers](../blocks/Cactus.md#growth-and-flowers) and [generation routes](../blocks/Cactus.md#finding-and-collecting-cactus) for the full limits. [Growth routine][cactus-growth] · [Cactus survival][cactus-support] · [Desert features][desert] · [Generated cactus patch][cactus-patch]

No bundled recipe makes the flower itself. Its Natural Blocks listing also supplies a [Creative inventory-browser route](../mechanics/InventoryBrowser.md); seeing it in Survival's catalog does not permit ordinary Survival insertion. [Category entry][flower-category]

## Usage

Place it on **Cactus, Farmland, or a block whose upper center is sturdy**. A full solid block is a simple display support; planting it does not require a cactus, sand, nearby water, or a particular light level. [Special support rule][flower] · [Placement checks][placement]

- Craft **1 Cactus Flower → 1 Pink Dye**, shapelessly, in either crafting grid. [Recipe][dye]
- Compost it with a **30%** ordinary chance to gain one level; the first accepted item in an empty Composter succeeds automatically. [Value][compost] · [Compost use][compost-use]
- Hold or feed it for [Bee luring and breeding](../mobs/Bee.md#luring-and-breeding). A placed flower is also an eligible pollination target, subject to the Bee's normal reachability and behavior rules. [Food tag][bee-food] · [Tempt goal][bee-goal] · [Feeding check][bee-feeding] · [Attractive block tag][bee-attractive] · [Block check][bee-flower]

## Behavior

The flower has no collision or waterlogged form. It has **no direct Bone Meal action and does not spread automatically**; new growing flowers come from the cactus routine. Removing valid support breaks it and normally returns the flower. Keep collected drops clear of the cactus's [loose-item hazard](../blocks/Cactus.md#contact-and-loose-item-hazards). [Registration][flower-reg] · [Flower class][flower] · [Bone Meal dispatch][bone-meal] · [Support updates][vegetation] · [Support removal][support-loss] · [Loot][flower-loot]

## Notes

This is the item form of `minecraft:cactus_flower`. The [Cactus guide](../blocks/Cactus.md) owns its growth source, while [Bees](../mobs/Bee.md#flowers-and-pollination) covers pollination conditions. Normal collection assumes block drops are enabled; explosions need not return the item. [Drop dispatch][drops] · [Loot][flower-loot]

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`, using the bundled recipes, loot and tags and the active behavior paths linked here. No gameplay test was run; custom data-pack changes are outside this review.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[flower-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/cactus_flower.json
[dye]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/pink_dye_from_cactus_flower.json
[bee-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/bee_food.json
[flower-reg]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L1951-L1961
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[cactus-growth]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/CactusBlock.java#L41-L82
[cactus-support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/CactusBlock.java#L112-L123
[desert]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/desert.json
[cactus-patch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/patch_cactus.json
[flower-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L908-L915
[flower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/CactusFlowerBlock.java#L12-L35
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L141
[compost]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L110-L114
[compost-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L273-L319
[bee-goal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L175-L197
[bee-feeding]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L569-L590
[bee-attractive]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/bee_attractive.json
[bee-flower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L668-L678
[bone-meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L81
[vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[support-loss]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L213-L233
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L364-L420
