# Brown Mushroom Block

**Brown Mushroom Block** (`minecraft:brown_mushroom_block`) is the full cap block from a huge brown mushroom. Bring **Silk Touch** when collecting it for a build: ordinary mining can leave you with no item at all. The small [Brown Mushroom](BrownMushroom.md) is the separate planting and recipe ingredient. [Item registration][items] · [Cap loot][loot]

## Obtaining

Mine a brown cap with **Silk Touch level I or above** to receive **one Brown Mushroom Block**. Without it, the cap drops **zero, one, or two small Brown Mushrooms**, rather than the building block. Fortune does not improve this loot, and Shears have no special recovery rule. An axe speeds mining, but a tool tier is not required for drops; the Silk Touch condition is what preserves the cap. See [mining and recovery](../blocks/Mushrooms.md#mining-and-building-faces) for the exact ordinary-drop probabilities. [Cap loot][loot] · [Block registration][block] · [Axe tag][axe] · [Harvest gate][gate]

Look for huge mushrooms in **Mushroom Fields**, whose vegetation can select a brown or red huge mushroom. For a renewable supply, use Bone Meal on a planted small Brown Mushroom, then collect the resulting cap with Silk Touch. The growth attempt can fail and needs suitable ground and clearance; keep small mushrooms for replanting instead of relying on cap drops. The family guide owns [checked natural sources](../blocks/Mushrooms.md#checked-acquisition-routes) and the [huge-mushroom growing layout](../blocks/Mushrooms.md#growing-a-huge-mushroom). [Biome feature list][biome] · [Placed selector][placed] · [Huge selector][selector] · [Brown feature][feature] · [Growth callback][growth]

## Usage

Place it as a solid decorative block. It does not need a mushroom stem or growing soil beneath it. To grow another huge mushroom, plant the **small Brown Mushroom**; placing a collected cap makes another building block. For Mushroom Stew, the recipe likewise takes the small brown and red mushrooms. [Placement and block shape][shape] · [Cap class][faces] · [Stew ingredients][stew]

## Behavior

The cap's **six faces** respond separately to neighboring Brown Mushroom Blocks. A face touching another brown cap becomes an interior face; Red Mushroom Blocks and Mushroom Stems do not count as the same type. Removing the neighboring cap **does not restore** the brown exterior. To reset an exposed interior face, recover the block with Silk Touch and place it again without a matching neighbor on that side. Its ordinary Silk Touch drop does not preserve the old face settings. See [building faces](../blocks/Mushrooms.md#mining-and-building-faces) for the shared rule. [Face placement and updates][faces] · [Default update][update] · [Face models][models] · [Cap loot][loot]

## Notes

This item places `minecraft:brown_mushroom_block`. [Mushrooms](../blocks/Mushrooms.md#brown-mushroom-block) compares it with the red cap and shared [Mushroom Stem](MushroomStem.md), and covers composting, planting stock, and the full growth rules.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`: registrations, complete cap loot, mining conditions, natural/grown feature wiring, and placement/face behavior. No gameplay test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L550-L552
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/brown_mushroom_block.json
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2292-L2306
[axe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[biome]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/mushroom_fields.json
[placed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/mushroom_island_vegetation.json
[selector]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/mushroom_island_vegetation.json
[feature]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/huge_brown_mushroom.json
[growth]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/MushroomBlock.java#L91-L119
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[faces]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/HugeMushroomBlock.java
[stew]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/mushroom_stew.json
[update]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L150-L161
[models]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/blockstates/brown_mushroom_block.json
