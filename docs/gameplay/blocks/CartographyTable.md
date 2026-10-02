# Cartography Table

A **Cartography Table** (`minecraft:cartography_table`) enlarges, copies, and locks existing maps. It opens a two-input workstation rather than a general crafting grid. Bring a filled [Map](../items/Map.md) and the material for the operation you want. [Block interaction][block] · [Menu][menu]

## Crafting and placement

Craft one table with **2 Paper above 4 planks** in a two-wide, three-high pattern. Each plank slot accepts the planks tag; the four planks can be different accepted species. This needs a Crafting Table because the inventory grid is only two rows high. [Recipe][recipe]

Place and interact with the table to open its menu. Its ordinary block loot returns one Cartography Table, subject to explosion survival; its registration does not require a correct tool for drops. The table is also the registered Cartographer job site. See [Trading](../trading/Trading.md) for profession and restocking conditions rather than assuming placing a block guarantees a particular offer. [Registration][registration] · [Loot][loot] · [Job site][poi]

## The three operations

Put a filled map in the first slot and the second material below it:

| Second input | Output | Conditions |
| --- | --- | --- |
| 1 Paper | 1 enlarged map | Existing map data, unlocked, scale below 4 |
| 1 Empty Map | 2 copies of the input map | Existing map data; locked maps can also be copied |
| 1 Glass Pane | 1 locked map | Existing map data, not already locked |

Taking the result consumes one item from each input slot. It does not charge experience levels. The menu checks the actual saved map data, so a custom item with a missing or invalid map record is not a working input merely because its name says “Map.” [Input filters, output construction, and consumption][menu]

### Enlarging

Each enlargement raises the scale by one, up to scale 4. It creates a **new map ID with fresh terrain data** at the larger scale rather than stretching the existing surveyed image. Take the enlarged map exploring again. The table uses only one Paper, unlike the separate eight-Paper crafting recipe. [Result processing][map] · [Scaled data][data] · [Crafting alternative][extend]

### Copying

The two resulting maps carry the **same map ID** and refer to the same saved map record. They are copies of the same survey, not two independent blank regions. Exploring with an unlocked copy updates that shared terrain record. [Copied components][menu] · [Map-ID data lookup][map]

### Locking

A Glass Pane creates a **new locked map ID** containing the current terrain colors and copied map decorations. Locked terrain no longer changes through ordinary held-map surveying. Player/marker behavior is handled separately, so do not interpret locking as freezing every future display element or securing a map from copying. [Lock processing][map] · [Locked copy][data]

The table itself refuses to enlarge a locked map. However, the current separate crafting-grid extension recipe does **not** check the locked flag; it can mark a qualifying non-exploration map for scaling into fresh, unlocked map data. This source-level difference has not been tested in-game. Keep a copy of an important survey before changing it, and do not treat the lock as universal protection across every crafting route. [Crafting extension checks][extend] · [Fresh scaled data][data]

## Closing and recovery

The input slots are temporary menu storage. Closing the menu clears its result preview and returns or drops the remaining inputs through the normal container-cleanup path. The workstation block has no map-storage block entity; use a Chest to keep maps between sessions. [Menu removal][menu] · [Block implementation][block]

## Sources and verification

Source-reviewed on 2026-10-02 at `3e85592c4c78ebb420302360667a6c230dc0318d`. No gameplay test of map enlargement, copying, locking, job-site selection, or menu cleanup was run.

Related: [Map](../items/Map.md) · [Empty Map](../items/EmptyMap.md) · [Cartography Table item](../items/CartographyTable.md) · [Blocks](Blocks.md)

[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CartographyTableBlock.java
[menu]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/inventory/CartographyTableMenu.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/cartography_table.json
[registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L5336-L5340
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/cartography_table.json
[poi]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L123
[map]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/MapItem.java
[data]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/saveddata/maps/MapItemSavedData.java
[extend]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/crafting/MapExtendingRecipe.java
