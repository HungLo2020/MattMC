# Fletching Table

The **Fletching Table item** (`minecraft:fletching_table`) places a **Fletcher job-site block**. The current table has no player crafting menu or arrow-processing inventory. See the [Fletching Table block guide](../blocks/FletchingTable.md#fletching-table) for the verified placed behavior. [Item registration][items] · [Block registration][registration] · [Default interaction][base-interaction]

## Obtaining and placing

Craft **one Fletching Table from 2 Flint above 4 planks** in a two-wide, three-high pattern. Each plank slot accepts the planks tag independently, allowing mixed accepted woods. Use a Crafting Table for the three-row recipe. [Recipe][fletching-recipe] · [Planks tag][planks]

An axe speeds up breaking the placed block, but no minimum tool tier is required for its ordinary drop; hand harvesting returns one matching item. Silk Touch is unnecessary and Fortune does not multiply the table. Explosion survival applies. [Axe tag][axe] · [Harvest gate][harvest] · [Loot][fletching-loot]

## Use and limits

Place it for an eligible Villager to claim as a Fletcher job site, following the [profession conditions](../blocks/FletchingTable.md#fletcher-job-site). No Flint, Sticks, Feathers or ammunition need to be loaded into it. Interacting with the block provides no recipe selector, repair interface or storage slots. [Job-site registration][poi] · [Plain-block registration path][plain-block] · [Default menu provider][base-menu]

For actual ammunition and weapon recipes, use [Arrow](Arrow.md), [Bow](Bow.md) and [Crossbow](Crossbow.md). Fletcher offers are Villager trades, covered by [Trading](../trading/Trading.md).

## Sources and verification

Source-reviewed on 2026-10-02 at `96e5604a6abaec697de2004b1ba9775e303bfba7`; no crafting, placement, harvesting or profession gameplay test was run.

Related: [Fletching Table block](../blocks/FletchingTable.md) · [Flint](Flint.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/Items.java#L2440-L2441
[registration]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5336-L5344
[base-interaction]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L202-L234
[fletching-recipe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/crafting/fletching_table.json
[planks]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/planks.json
[axe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[fletching-loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/fletching_table.json
[poi]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L120-L133
[plain-block]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L7274-L7293
[base-menu]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L323
