# Moss Carpet

**Moss Carpet** (`minecraft:moss_carpet`) is a thin green floor covering. Craft it from [Moss Blocks](MossBlock.md), or collect existing carpet by hand. It does not have the wall-covering growth of [Pale Moss Carpet](PaleMossCarpet.md). [Item registration][items] · [Carpet class][carpet] · [Carpet recipe][recipe]

## Obtaining

Place **two Moss Blocks side by side → three Moss Carpets**. This two-slot recipe fits the inventory crafting grid. Both ingredients must be the green Moss Block; Pale Moss Blocks make their own pale carpet. [Green recipe][recipe] · [Pale recipe][pale-recipe]

Breaking a placed Moss Carpet in ordinary Survival returns **one Moss Carpet**, including when you use your hand. Silk Touch and Shears are unnecessary, and Fortune does not increase the yield. A hoe speeds mining. See [registered forms and harvesting](../blocks/MossAndPaleMoss.md#registered-forms-and-harvesting) for the family comparison. [Carpet loot][loot] · [Registration][block] · [Hoe tag][hoe] · [Harvest gate][gate]

Green moss patches can also place Moss Carpet among their vegetation. After finding a starter Moss Block, Bone Meal on that full block can produce more moss and vegetation; the carpet result is a random possibility, not a guaranteed return from each application. The shared guide covers [starting supplies](../blocks/MossAndPaleMoss.md#finding-a-starting-supply) and [moss growth](../blocks/MossAndPaleMoss.md#moss-blocks-and-spreading). [Green patch][patch] · [Vegetation choices][vegetation] · [Growth callback][growth]

## Usage

Place it over a **non-air block** for a floor layer **one-sixteenth of a block high**. It does not need a full sturdy top face. This makes it useful for adding a green surface while keeping the underlying block; removing that support makes the carpet break when its neighbors update. [Carpet support and shape][carpet]

## Behavior

Applying Bone Meal directly to Moss Carpet does not grow or duplicate it, and it does not spread on its own. Use a **Moss Block** for the patch-growth route. The carpet also has no waterlogged form; see [water and recovery](../blocks/MossAndPaleMoss.md#water-and-recovery) before using it beside flowing water. [Carpet class][carpet] · [Bone Meal dispatch][meal]

## Notes

This item places `minecraft:moss_carpet`. The canonical [Moss Carpet guide](../blocks/MossAndPaleMoss.md#moss-carpet) owns its placed behavior; [Moss and Pale Moss](../blocks/MossAndPaleMoss.md#crafting-and-composting) covers composting and the other moss materials.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`: item/block registration, complete loot and both carpet recipes, mining tag, support, and the green patch/vegetation and Bone Meal paths. No gameplay test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L374-L378
[carpet]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/CarpetBlock.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/moss_carpet.json
[pale-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/pale_moss_carpet.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/moss_carpet.json
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6590-L6614
[hoe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[patch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/moss_patch_bonemeal.json
[vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/moss_vegetation.json
[growth]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BonemealableFeaturePlacerBlock.java
[meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L81
