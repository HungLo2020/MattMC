# Cartography Table

The **Cartography Table item** (`minecraft:cartography_table`) places the workstation for enlarging, copying and locking existing maps. It also places a Cartographer job site. The [Cartography Table block guide](../blocks/CartographyTable.md#cartography-table) owns its placed behavior and transactions. [Item registration][items] · [Block interaction][cartography]

## Obtaining and placing

Craft **one table with 2 Paper above 4 planks** in a two-wide, three-high pattern. Different accepted planks may be mixed; the three rows require a Crafting Table. An axe speeds up collecting the placed block, but ordinary hand harvesting also returns one table. Its loot has no Silk Touch requirement or Fortune bonus and is subject to explosion survival. [Recipe][cartography-recipe] · [Planks][planks] · [Axe tag][axe] · [Harvest gate][harvest] · [Loot][cartography-loot]

Place the table and interact normally. It has no directional placement state. Its menu takes a filled Map plus **Paper to enlarge**, an **Empty Map to copy**, or an **ordinary Glass Pane to lock**. Review the [operation conditions](../blocks/CartographyTable.md#the-three-operations), especially the scale cap, locked-map restrictions and fresh terrain after enlargement. [Placement][placement] · [Server menu checks][operations]

## Storage and village use

The item does not carry a stored map inventory, and the placed menu is temporary: remaining inputs return or drop when it closes. Keep finished surveys in a normal container. The Cartographer job-site role needs no supplies loaded into the table; see [Cartographer job site](../blocks/CartographyTable.md#cartographer-job-site). [Menu cleanup][cleanup] · [Job-site registration][poi]

## Sources and verification

Source-reviewed on 2026-10-02 at `96e5604a6abaec697de2004b1ba9775e303bfba7`; no crafting, placement, map-processing or profession gameplay test was run.

Related: [Cartography Table block](../blocks/CartographyTable.md) · [Map](Map.md) · [Empty Map](EmptyMap.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/Items.java#L2440-L2441
[cartography]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/CartographyTableBlock.java#L19-L47
[cartography-recipe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/crafting/cartography_table.json
[planks]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/planks.json
[axe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[cartography-loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/cartography_table.json
[placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Block.java#L434-L437
[operations]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/inventory/CartographyTableMenu.java#L90-L134
[cleanup]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/inventory/CartographyTableMenu.java#L191-L196
[poi]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L120-L133
