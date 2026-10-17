# Nether Wart Block

**Nether Wart Block** (`minecraft:nether_wart_block`) is a solid red building and fungus-cap block. It is a separate item from the plantable [Nether Wart](NetherWart.md) brewing ingredient. [Item nether_wart_block] · [Registry nether_wart_block]

## Obtaining

Harvest a Nether Wart Block by hand or with a tool for **one matching full block**. A hoe mines it efficiently; Silk Touch is unnecessary and Fortune does not increase the drop. Huge Crimson fungi use it as cap material, including the [renewable planted-fungus route](../blocks/NetherGroundAndVegetation.md#renewable-cap-blocks). [Loot nether_wart_block] · [Hoe mining tag] · [Configuration crimson_fungus_planted] · [Huge-fungus cap placement]

You can also craft it from **nine loose Nether Wart** at a Crafting Table. The [family guide](../blocks/NetherGroundAndVegetation.md#crafting-is-not-reversible-storage) owns that recipe and its restriction: **there is no reverse recipe** returning the loose crop. [Nether Wart Block recipe]

## Usage

Place it as a full block for building, or compost it with its configured **85% chance** to advance a partially filled composter. Neither mining nor composting converts it into the brewing ingredient. [Loot nether_wart_block] · [Wart-block compost values] · [Compost chance and first layer]

## Behavior

It has hardness **1.0**, needs no supporting stem, and has no leaf-decay or growth-stage behavior. Removing a fungus stem does not turn the remaining cap blocks into loose Nether Wart. [Registry nether_wart_block] · [Full-block support and shape defaults] · [Default random-tick eligibility]

## Notes

[Warped Wart Block](WarpedWartBlock.md) is a separately registered cap material with its own acquisition route. Use [Nether Wart crop](../blocks/NetherWart.md) for farming and [Nether Fungi](../blocks/NetherFungi.md) for huge-fungus growth.

## Sources and verification

Source-reviewed on **2026-10-02** at `ae92d4575f1af7752d0f461bbf7bf0843c3cddf7`. Checked this block/item, full self-drop loot, hoe tag, exact nine-ingredient recipe and lack of a reverse recipe, planted Crimson cap material, generic full-block behavior, and compost value. No in-game mining, support, water, Bone Meal, crafting, or world-generation test was run. Data packs can change tags, loot, recipes, and features.

[Registry nether_wart_block]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L4332-L4334
[Loot nether_wart_block]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/nether_wart_block.json
[Item nether_wart_block]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java#L766
[Hoe mining tag]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[Full-block support and shape defaults]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[Default random-tick eligibility]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L391-L392
[Configuration crimson_fungus_planted]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/worldgen/configured_feature/crimson_fungus_planted.json
[Huge-fungus cap placement]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/levelgen/feature/HugeFungusFeature.java#L149-L176
[Nether Wart Block recipe]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/recipe/crafting/nether_wart_block.json
[Wart-block compost values]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L175-L176
[Compost chance and first layer]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L319
