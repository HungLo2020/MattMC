# Twisting Vines

Twisting Vines form **upward-growing columns**. The inventory item is `minecraft:twisting_vines`; it supplies both the growing tip and the connected body form. [Item registration][items] · [Direction][twisting] · [Column placement][column]

## Obtaining

Harvest an existing Twisting Vine column. **Mine each wanted segment with Shears or Silk Touch** for a guaranteed **one matching vine item** from that segment. Both the tip and body use the same full loot branches:

- **Shears or Silk Touch:** 100% chance of one item
- **Neither, and no Fortune:** 33% chance of one item
- **Fortune I:** 55% chance of one item
- **Fortune II:** 77% chance of one item
- **Fortune III or higher:** 100% chance of one item

Fortune increases the chance, not the successful item count. No special tool is required merely to break the block, but the fallback can lose the item. [Tip loot][loot-twisting_vines] · [Body loot][loot-twisting_vines_plant] · [Fortune selection][fortune] · [Harvest gate][harvest] · [Block properties][blocks]

**Do not rely on breaking the support to recover the whole column.** Detached segments are destroyed with an empty-tool loot context, so they use the **33% fallback** rather than inheriting the original tool's Shears, Silk Touch or Fortune. See [Nether-vine harvesting](../blocks/Vines.md#recovering-nether-vines) and [checked starter locations](../blocks/Vines.md#source-backed-places-to-collect-starters). [Support loss][column] · [Body updates][body] · [Detached loot caller][collapse]

You can also obtain starter columns from the Warped Nylium Bone Meal route, which has a 1-in-8 chance to attempt Twisting Vines after other vegetation. Placement may still fail. See [Nylium cultivation](../blocks/NetherGroundAndVegetation.md#bone-meal-on-nylium). [Ground callback][nylium]

## Usage

Place it against a **sturdy top face**, or extend a connected column of the same species. It grows upward into **air**. Its player-placement support rule does not require a particular Nylium or the narrower substrates used by natural feature generation. Use [Vines](../blocks/Vines.md#weeping-and-twisting-vines) for column support, trimming and climbing. [Placement and support][column] · [Direction and growth][twisting] · [Air requirement][nether-vines]

## Behavior

Apply Bone Meal to the **tip or a connected body** when there is air beyond the tip. A valid use consumes **one Bone Meal** in ordinary Survival and extends the end by a variable attempted length; obstructions stop further placement. It also works on an age-25 tip whose natural growth has stopped. Newly placed tips start at age 0–24, and eligible random ticks have a **10% chance** to extend into air until age 25. These are growth-event chances, not a fixed time or total column length. [Tip growth][head] · [Body forwarding][body] · [Variable length][nether-vines] · [Consumption][bonemeal]

## Notes

The connected body block is `minecraft:twisting_vines_plant`. Its loot and pick-block behavior return the same **Twisting Vines item**; it is not a separate inventory item to collect. [Body clone behavior][body] · [Body loot][loot-twisting_vines_plant] · [Item registration][items]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game harvesting, placement or growth test was run. Data packs can change loot and tags.

Related: [Twisting Vines block guide](../blocks/Vines.md#twisting-vines) · [Bone Meal](BoneMeal.md) · [Shears](Shears.md) · [Nether Fungi](../blocks/NetherFungi.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L362-L368
[twisting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/TwistingVinesBlock.java#L19-L36
[column]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/GrowingPlantBlock.java#L32-L62
[loot-twisting_vines]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/twisting_vines.json
[loot-twisting_vines_plant]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/twisting_vines_plant.json
[fortune]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java#L37-L41
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5464-L5581
[body]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/GrowingPlantBodyBlock.java#L29-L87
[collapse]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/Level.java#L263-L278
[nylium]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/NyliumBlock.java#L31-L88
[nether-vines]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/NetherVines.java#L6-L24
[head]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/GrowingPlantHeadBlock.java#L32-L128
[bonemeal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
