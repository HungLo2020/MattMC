# Red Mushroom Block

**Red Mushroom Block** (`minecraft:red_mushroom_block`) is the red cap building block from a huge red mushroom. Collect it with **Silk Touch** to keep the block. It is a separate item from the small [Red Mushroom](RedMushroom.md) used for planting and mushroom recipes. [Item registration][items] · [Cap loot][loot]

## Obtaining

A cap mined with **Silk Touch level I or above** drops **one Red Mushroom Block**. Ordinary mining instead drops **zero, one, or two small Red Mushrooms**, so destroying a cap does not guarantee any planting material. Neither Fortune nor Shears adds a special yield or a way to recover the intact block. Axes mine these caps efficiently, but there is no tool-tier requirement for the loot table. [Cap loot][loot] · [Block registration][block] · [Axe tag][axe] · [Harvest gate][gate]

Huge red mushrooms can be selected by **Mushroom Fields** vegetation. You can also start with a planted small Red Mushroom and use Bone Meal to attempt huge growth, then Silk Touch the cap. Set aside some small mushrooms before harvesting: a cap farm can return no small mushrooms. Use the family guide's [checked acquisition routes](../blocks/Mushrooms.md#checked-acquisition-routes) to find a starter, and its [huge-growth guidance](../blocks/Mushrooms.md#growing-a-huge-mushroom) for ground, space, and failed attempts. [Biome feature list][biome] · [Placed selector][placed] · [Huge selector][selector] · [Red feature][feature] · [Growth callback][growth]

## Usage

Use the cap as a full decorative block; a placed cap does not require a stem or soil underneath. For another huge mushroom, use the small plant instead. Mushroom Stew also requires small Brown and Red Mushrooms, rather than their cap blocks. The shared [food and decoration guide](../blocks/Mushrooms.md#crafting-food-and-decoration) keeps the mushroom recipe comparison. [Full block shape][shape] · [Cap class][faces] · [Stew ingredients][stew]

## Behavior

A newly placed cap has an exterior on exposed sides and an interior on sides touching **another Red Mushroom Block**. Each of the six faces is independent; Brown Mushroom Blocks and Mushroom Stems do not trigger that matching-neighbor rule. Once a side becomes interior, removing the neighboring red cap leaves it interior. Recover and replace the remaining cap with Silk Touch, with that neighbor absent, to restore its exterior: the normal drop does not retain its old face settings. See [mining and building faces](../blocks/Mushrooms.md#mining-and-building-faces) for recovery odds and the full placement rules. [Face placement and updates][faces] · [Default update][update] · [Face models][models] · [Cap loot][loot]

## Notes

This item places `minecraft:red_mushroom_block`. The [Red Mushroom Block family entry](../blocks/Mushrooms.md#red-mushroom-block) compares it with [Brown Mushroom Block](BrownMushroomBlock.md) and [Mushroom Stem](MushroomStem.md), including their different ordinary drops.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`: registrations, complete cap loot, mining conditions, natural/grown feature wiring, and placement/face behavior. No gameplay test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L550-L552
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/red_mushroom_block.json
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2292-L2306
[axe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[biome]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/mushroom_fields.json
[placed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/mushroom_island_vegetation.json
[selector]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/mushroom_island_vegetation.json
[feature]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/huge_red_mushroom.json
[growth]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/MushroomBlock.java#L91-L119
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[faces]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/HugeMushroomBlock.java
[stew]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/mushroom_stew.json
[update]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L150-L161
[models]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/blockstates/red_mushroom_block.json
