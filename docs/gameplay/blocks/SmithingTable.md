# Smithing Table

A Smithing Table opens the workstation menu for equipment upgrades and armor trims. Its block and item ID is `minecraft:smithing_table`.

## Crafting and collecting

At a [Crafting Table](CraftingTable.md), arrange **two Iron Ingots above four planks-tag items** in a two-column, three-row pattern:

| Left column | Right column |
| --- | --- |
| Iron Ingot | Iron Ingot |
| Planks | Planks |
| Planks | Planks |

The recipe produces **one Smithing Table**. Leave the unused column empty. Ingredients in the planks positions must belong to `minecraft:planks`; similarly named integrated wood blocks are not automatically valid substitutes.

The table's loot returns one table under ordinary harvesting conditions, subject to explosion survival. It belongs to the axe mining tag, but its registration does **not** require a correct tool for drops. [Current native flags][acq-native-flags] · [Player drop-tool check][acq-tool-check] · [Current block loot][acq-loot]

## Finding a generated table

Look for a Smithing Table in **[village Toolsmith houses](../structures/Village.md#where-to-search)** in Plains, Desert, Savanna, Snowy and Taiga styles. The checked house pieces each contain a placed table, but those buildings are optional: finding a village does not guarantee a Toolsmith house or an untouched workstation. Before taking one from a settlement you want to keep using, see [Toolsmith job site](#toolsmith-job-site). [Plains house][acq-plains-toolsmith] · [Desert house][acq-desert-toolsmith] · [Savanna house][acq-savanna-toolsmith] · [Snowy house][acq-snowy-toolsmith] · [Taiga house][acq-taiga-toolsmith]

**[Trail Ruins](../structures/TrailRuins.md)** offer another possible route: the optional `large_room_1` building contains a Smithing Table, and its archaeology processor preserves that block. A ruin need not include that room. Follow the structure guide's excavation advice to protect nearby Suspicious Gravel while exposing and collecting the table. [Room template][acq-trail-room] · [Building selections][acq-trail-pool] · [Room processing][acq-trail-processor] · [Archaeology replacement tag][acq-trail-tag]

## Opening and using the menu

Place the table and interact with it. Its three inputs are **template, base equipment, and addition material**, from left to right, with an output slot to their right. See the [smithing guide](../smithing/Smithing.md) for verified upgrade and trim examples, input costs, and item-preservation details.

The working slots are not permanent storage: closing the menu clears the input container. Store spare equipment and templates in a [Chest](Chest.md).

## Toolsmith job site

A Smithing Table is the job-site block for a **Toolsmith Villager**. Its registered
point of interest has one claim slot; placing a table near a villager does not
by itself prove that the villager has claimed it. Keep the claimed table
accessible for work. The player's upgrade/trim menu is a separate use of the
same block. [Registered job-site states][toolsmith-site] · [Profession binding][toolsmith-profession]

Use [Villager employment and changing jobs](../mobs/Villager.md#employment-and-changing-jobs)
for eligibility and profession retention, and [Trading restocks](../trading/Trading.md#stock-and-restocking)
for the work/stock conditions. Those shared guides own the detailed rules;
removing a workstation is not a general way to reset an experienced villager's
profession or offers.

## Related pages

- [Smithing guide](../smithing/Smithing.md)
- [Armor trims: patterns, acquisition, and copying](../mechanics/ArmorTrims.md)
- [Smithing Table item](../items/SmithingTable.md)
- [Netherite Upgrade Smithing Template](../items/SmithingTemplateNetheriteUpgrade.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. This is not an in-game test. Data packs can change recipes, tags, and loot; later builds can change behavior. Natural generation was outside that initial review; the bounded acquisition review below supersedes that scope limitation. The Toolsmith job-site mapping was separately source-reviewed on **2026-10-09** at `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`; no villager or restocking gameplay test was run.

The named natural-acquisition routes were source-reviewed separately at `1b9b103398fd70d5b5152b93a1d0abc581fffc19` on **2026-10-10**: actual table blocks were decoded from the selected NBT templates, then traced through active pools and placement processors. Village coverage here uses ordinary house selections in the five named styles; the Trail Ruins check covers the named optional room. No in-game generation, structure search, excavation or collection test was run. Data packs, saved template overrides, placement conditions and the state of an existing site can change what is available. House selections: [Plains][acq-plains-houses] · [Desert][acq-desert-houses] · [Savanna][acq-savanna-houses] · [Snowy][acq-snowy-houses] · [Taiga][acq-taiga-houses]. [Configured placement][acq-pool-place] · [Legacy block filtering][acq-legacy-place] · [Mossy-block substitutions][acq-mossify] · [Unchanged nonmatching blocks][acq-rules]

The collection rule was also rechecked at the **2026-10-10** source pin, including the native block properties applied before Java state initialization. The mapped `Chest` profile does not set the correct-tool-required flag. [Block/profile mapping][acq-native-profile] · [Native flag application][acq-native-apply] · [Initialization order][acq-native-init]

- [Table recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/smithing_table.json)
- [Planks tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/planks.json)
- [Block registration and tool requirement](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L5370-L5374)
- [Player tool-for-drops check](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657)
- [Axe mining tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/mineable/axe.json)
- [Block loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/smithing_table.json)
- [Opening the menu](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/SmithingTableBlock.java)
- [Menu layout and recipe lookup](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/SmithingMenu.java)
- [Menu closing and input cleanup](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/ItemCombinerMenu.java)

[toolsmith-site]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L132
[toolsmith-profession]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java#L122

[acq-plains-houses]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/worldgen/template_pool/village/plains/houses.json
[acq-desert-houses]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/worldgen/template_pool/village/desert/houses.json
[acq-savanna-houses]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/worldgen/template_pool/village/savanna/houses.json
[acq-snowy-houses]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/worldgen/template_pool/village/snowy/houses.json
[acq-taiga-houses]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/worldgen/template_pool/village/taiga/houses.json
[acq-pool-place]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L166-L181
[acq-legacy-place]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/level/levelgen/structure/pools/LegacySinglePoolElement.java#L32-L37
[acq-mossify]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/worldgen/processor_list/mossify_10_percent.json
[acq-rules]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/RuleProcessor.java#L35-L45
[acq-plains-toolsmith]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/structure/village/plains/houses/plains_tool_smith_1.nbt
[acq-desert-toolsmith]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/structure/village/desert/houses/desert_tool_smith_1.nbt
[acq-savanna-toolsmith]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/structure/village/savanna/houses/savanna_tool_smith_1.nbt
[acq-snowy-toolsmith]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/structure/village/snowy/houses/snowy_tool_smith_1.nbt
[acq-taiga-toolsmith]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/structure/village/taiga/houses/taiga_tool_smith_1.nbt
[acq-trail-room]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/structure/trail_ruins/buildings/large_room_1.nbt
[acq-trail-pool]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/worldgen/template_pool/trail_ruins/buildings.json#L48-L55
[acq-trail-processor]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/worldgen/processor_list/trail_ruins_houses_archaeology.json
[acq-trail-tag]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/tags/block/trail_ruins_replaceable.json
[acq-native-flags]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/rust/content/block/definitions/physics.rs#L305-L310
[acq-tool-check]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[acq-loot]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/loot_table/blocks/smithing_table.json
[acq-native-profile]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/rust/content/block/definitions/catalog.rs#L890
[acq-native-apply]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/level/block/state/NativeBlockDefinitions.java#L99-L122
[acq-native-init]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L105-L108
