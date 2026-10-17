# Pale Moss Block

**Pale Moss Block** (`minecraft:pale_moss_block`) is a full pale ground-cover block. A starter block and Bone Meal let you turn eligible nearby terrain into more pale moss, then collect blocks or make [Pale Moss Carpet](PaleMossCarpet.md). [Item registration][items] · [Block registration][block] · [Growth callback][growth]

## Obtaining

Look for pale ground patches in **Pale Garden**. Its biome features include a patch that places Pale Moss Blocks and pale vegetation. A Wandering Trader can also select an offer of **one Emerald → two Pale Moss Blocks**, with **five uses**. That offer is not guaranteed on an individual trader. See [finding a starting supply](../blocks/MossAndPaleMoss.md#finding-a-starting-supply) for the checked generation routes and the distinction from player-grown Pale Oak. [Biome][biome] · [Patch placement][placed] · [Natural patch][natural] · [Offer][trade] · [Offer quantities][quantities] · [Trader selection][selection]

Break a placed block to collect **one Pale Moss Block**, including by hand. A hoe is efficient, but Silk Touch and Shears are not required and Fortune gives no extra blocks. Keep a starter before converting the rest into carpet. [Block loot][loot] · [Block registration][block] · [Hoe tag][hoe] · [Harvest gate][gate]

## Usage

Place it as a full building block, or use **Bone Meal with air directly above it** to attempt a pale moss patch. An accepted Survival use consumes **one Bone Meal**, even when the surrounding terrain prevents useful growth. The callback does not require Pale Garden, a particular light level, or adjacent water, so a collected starter can be used elsewhere. The [moss-spreading guide](../blocks/MossAndPaleMoss.md#moss-blocks-and-spreading) owns the eligible terrain, patch size, and obstruction rules. [Growth callback][growth] · [Bone Meal consumption][meal] · [Pale growth patch][patch]

**Two Pale Moss Blocks side by side craft three Pale Moss Carpets**, fitting the inventory crafting grid. Use the [Pale Moss Carpet guide](../blocks/MossAndPaleMoss.md#pale-moss-carpet) for that covering's floor and wall behavior; ordinary green Moss Blocks do not substitute in its recipe. [Carpet recipe][recipe]

## Behavior

The full block does not spread merely by waiting. Bone Meal attempts can add **Pale Moss Carpet, Short Grass, or Tall Grass** after ground conversion; they do not grow Pale Hanging Moss. These are random, placement-dependent vegetation results. The block has no waterlogged state and no special support requirement. See [water and recovery](../blocks/MossAndPaleMoss.md#water-and-recovery) for the different limits of full blocks and thin moss forms. [Registration][block] · [Growth callback][growth] · [Vegetation choices][vegetation]

## Notes

This item places `minecraft:pale_moss_block`. [Moss and Pale Moss](../blocks/MossAndPaleMoss.md#registered-forms-and-harvesting) compares all five forms, and its [crafting and composting section](../blocks/MossAndPaleMoss.md#crafting-and-composting) explains the green-only mossy-stone recipes.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`: registrations, complete loot and carpet recipe, trader selection, natural patch wiring, and active Bone Meal/vegetation paths. No gameplay test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L374-L378
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6825-L6841
[growth]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BonemealableFeaturePlacerBlock.java
[biome]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/pale_garden.json
[placed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/pale_moss_patch.json
[natural]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/pale_moss_patch.json
[trade]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L821-L822
[quantities]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1422-L1480
[selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/pale_moss_block.json
[hoe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[meal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L81
[patch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/pale_moss_patch_bonemeal.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/pale_moss_carpet.json
[vegetation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/pale_moss_vegetation.json
