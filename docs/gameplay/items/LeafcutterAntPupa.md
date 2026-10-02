# Leafcutter Ant Pupa

## Obtaining

Leafcutter Ant Pupa is a real item listed in the **Functional Blocks** category alongside the nest blocks. It can be requested through the [inventory item browser](../mechanics/InventoryBrowser.md) in Survival or Creative. No bundled recipe or loot source was found for it; being listed does not establish natural colony production. [Registration][item] · [Category entry][category] · [Bundled data][data]

## Usage

Use the Pupa on a placed [Leafcutter Anthill](../blocks/LeafcutterNests.md#leafcutter-anthill). The server spawns one baby Leafcutter Ant at the center of the block, one block above it. Keep space above the anthill clear. The action consumes one Pupa outside Creative. [Use action][use]

## Behavior

Using the Pupa on a Chamber or an unrelated block does not activate this action. It does not create a queen, set an anthill's queen flag, store an ant, or assign the newborn a hive. The [nest guide](../blocks/LeafcutterNests.md#queen-release-and-nesting-limits) explains the unfinished colony behavior. [Use action][use] · [Default home][home] · [Nest implementation][nest]

## Notes

* This item is registered as `minecraft:leafcutter_ant_pupa`.
* It is also included in the bundled insect-item tag; that tag does not supply a recipe or drop. [Insect-item tag][insects]
* Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on 2026-10-02; no in-game spawning or inventory-consumption test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L1900-L1901
[category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1098-L1102
[use]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/item/ItemLeafcutterPupa.java#L18-L36
[home]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/entity/EntityLeafcutterAnt.java#L88-L96
[nest]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/tileentity/TileEntityLeafcutterAnthill.java#L12-L60
[insects]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/item/insect_items.json#L1-L8
[data]: https://github.com/HungLo2020/MattMC/tree/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft
