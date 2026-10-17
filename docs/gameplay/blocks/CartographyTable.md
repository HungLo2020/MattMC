# Cartography Table

A **Cartography Table** (`minecraft:cartography_table`) enlarges, copies, and locks existing maps. It opens a workstation with two input slots and a result slot. Use a filled [Map](../items/Map.md) for the survey you want to change; an [Empty Map](../items/EmptyMap.md) belongs in the second slot when copying. [Block interaction][cartography] · [Input slots][slots]

## Crafting and placement

Craft **one table from 2 Paper above 4 planks**, arranged in a two-wide, three-high pattern. Each plank slot accepts the planks tag independently, so accepted woods can be mixed. The current tag includes Bamboo Planks and Crimson/Warped Planks, but not Bamboo Mosaic. The three-row recipe needs a Crafting Table. [Recipe][cartography-recipe] · [Accepted planks][planks]

An **axe speeds up breaking**, but no particular tool or tier is required for the block drop: ordinary Survival harvesting by hand works. Normal block loot is one Cartography Table, without a Silk Touch requirement or Fortune bonus; the loot is subject to explosion survival. [Axe tag][axe] · [Registration][registration] · [Harvest gate][harvest] · [Loot][cartography-loot]

The placed table has no facing property: the direction you stand in does not rotate it. It uses ordinary default-state placement. Interact normally to open its menu; holding an item while using the secondary-use control can bypass the block's usual interaction. [Block implementation][cartography] · [Default placement][placement] · [Use dispatch][use-dispatch]

## The three operations

Put a filled map in the upper input and one of these materials in the lower input:

| Second input | Output per operation | Required saved map state |
| --- | --- | --- |
| 1 Paper | 1 enlarged map | Unlocked, scale below 4 |
| 1 Empty Map | 2 copies | Any valid record, including locked maps and scale 4 |
| 1 ordinary Glass Pane | 1 locked map | Not already locked; scale 4 is allowed |

These operations use the map's saved record. The upper slot technically accepts an item carrying a map-ID component; an ordinary filled Map supplies that component. A name alone does not make an item a working survey. The lower slot accepts the exact Paper, Empty Map and ordinary Glass Pane items, so a glass block or stained pane is not a substitute. [Slot filters][slots] · [Saved-data lookup][map-lookup] · [Operation checks][operations]

Taking a normal result consumes **one item from each input slot**. Copying therefore turns one filled map plus one empty map into two filled copies, a net gain of one. There is no fuel, experience-level charge or processing timer. The result slot rejects manual insertion. Shift-click routes map-ID items to the upper slot and the three accepted supplies to the lower slot; shift-clicking a result tries to move it into player inventory. [Consumption and result slot][slots] · [Shift-click routing][shift]

### Enlarging

Each enlargement raises the map by **one scale level, up to scale 4**. The result receives a **new map ID with fresh terrain data** for the larger, grid-aligned region in the same dimension. It does not stretch the previously surveyed colors. Explore with the enlarged map again; the [Map scale table](../items/Map.md#region-and-scale) explains coverage and detail. [Result processing][map-process] · [Fresh scaled data][map-data]

The table does **not** reject exploration maps separately: an unlocked exploration map below scale 4 passes its Paper check. The separate crafting-grid extension recipe does reject exploration maps. This difference follows the current server checks and has not been tested in-game. Keep a copy of a valued survey before enlarging it. [Table conditions][operations] · [Crafting extension conditions][extend]

### Copying

The two outputs retain the input map's **same map ID** and item components. They refer to one shared saved map record, so surveying with an unlocked copy updates that shared record. Copying a locked map preserves its lock. To begin an independent survey, use an Empty Map as described in its own guide. [Copy construction][operations] · [Map-ID lookup][map-lookup] · [Survey updates][map-terrain]

### Locking

A Glass Pane creates a **new locked map ID** with the current terrain colors, center, scale, dimension and copied decorations. Other copies still using the old ID continue to refer to the old record. The locked result stops ordinary held-map terrain updates, while player and other marker handling remain separate. Locking does not prevent copying. See [Map markers](../items/Map.md#markers-and-treasure-maps) for their normal behavior. [Lock processing][map-process] · [Locked data copy][map-data] · [Terrain gate][map-terrain] · [Marker updates][map-markers]

The table refuses Paper enlargement of a locked map. However, the current **separate crafting-grid extension recipe omits the locked-state check**: a qualifying non-exploration map below scale 4 can be marked for scaling into fresh, unlocked data. This source-level difference has not been tested in-game. A lock should not be treated as protection against every crafting route. [Crafting checks][extend] · [Fresh scaled data][map-data]

## Closing and recovery

Both inputs belong to the open menu, not to storage inside the block. Closing clears the result preview and returns remaining inputs to player inventory; ordinary cleanup can drop items when needed, including inventory overflow. The menu also requires the original Cartography Table and valid interaction range. Server handling rejects spectator item transfers. [Menu cleanup][cleanup] · [Return path][return] · [Overflow handling][inventory-return] · [Validity][menu-validity] · [Server clicks][server-clicks]

The table has no storage block entity or world-accessible inventory for a [Hopper](Hopper.md). A [Dropper](DispenserAndDropper.md) aimed at the table does not insert materials into someone's open menu. Its temporary input and output slots produce no comparator reading, and redstone cannot trigger a map operation. Store finished maps in a normal container. [Block implementation][cartography] · [Hopper lookup][hopper] · [Dropper fallback][dropper] · [Inherited signal behavior][base-interaction]

## Cartographer job site

The placed table is a **Cartographer job site**, with one job-site claim per block. An eligible unemployed adult Villager must be able to acquire it through the normal job-site path. No map, Paper or Glass Pane needs to be loaded for that role. [POI registration][poi] · [Profession binding][profession] · [Acquisition][acquire] · [Assignment][assign]

Cartographers use the ordinary work-at-job-site behavior, which can restock when their conditions allow. Their work does not operate your map menu or consume its supplies. Use the [Villager](../mobs/Villager.md) and [Trading](../trading/Trading.md) guides for employment, offers and restocking conditions. [Work selection][work-selection] · [Work behavior][work]

## Sources and verification

Source-reviewed on 2026-10-02 at `96e5604a6abaec697de2004b1ba9775e303bfba7`. No gameplay test of crafting, harvesting, map transactions, menu cleanup, automation or job-site behavior was run. The operations above describe ordinary filled maps with valid saved data; custom components, malformed map records and changed server data can need separate checks.

Related: [Map](../items/Map.md) · [Empty Map](../items/EmptyMap.md) · [Cartography Table item](../items/CartographyTable.md) · [Workstations](catalog/workstations.md) · [Blocks](Blocks.md)

[cartography]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/CartographyTableBlock.java#L19-L47
[slots]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/inventory/CartographyTableMenu.java#L46-L88
[cartography-recipe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/crafting/cartography_table.json
[planks]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/planks.json
[axe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[registration]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5336-L5344
[harvest]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[cartography-loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/cartography_table.json
[placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Block.java#L434-L437
[use-dispatch]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L343-L378
[map-lookup]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/MapItem.java#L50-L59
[operations]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/inventory/CartographyTableMenu.java#L90-L134
[shift]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/inventory/CartographyTableMenu.java#L141-L189
[map-process]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/MapItem.java#L281-L314
[map-data]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/saveddata/maps/MapItemSavedData.java#L118-L144
[extend]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/crafting/MapExtendingRecipe.java#L13-L47
[map-terrain]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/MapItem.java#L267-L279
[map-markers]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/saveddata/maps/MapItemSavedData.java#L151-L200
[cleanup]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/inventory/CartographyTableMenu.java#L191-L196
[return]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L589-L613
[inventory-return]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/player/Inventory.java#L302-L323
[menu-validity]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L96-L98
[server-clicks]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1801-L1833
[hopper]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L359-L388
[dropper]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DropperBlock.java#L58-L75
[base-interaction]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L202-L234
[poi]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L120-L133
[profession]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java#L101-L120
[acquire]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/ai/behavior/AcquirePoi.java#L59-L101
[assign]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/ai/behavior/AssignProfessionFromJobSite.java#L15-L41
[work-selection]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L74-L101
[work]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/ai/behavior/WorkAtPoi.java#L21-L45
