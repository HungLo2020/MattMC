# Leafcutter Anthill

## Obtaining

Leafcutter Anthill has a registered block item and an ordinary **Functional Blocks** category entry. Request it through the [inventory item browser](../mechanics/InventoryBrowser.md) in Survival or Creative. No bundled crafting, natural-generation, or mining-drop route was found; see [nest acquisition and recovery](../blocks/LeafcutterNests.md#obtaining-and-recovering-the-blocks). [Item registration][item] · [Category entry][category]

## Usage

Place it, then use a [Leafcutter Ant Pupa](LeafcutterAntPupa.md) directly on the block to spawn a baby ant one block above. The Pupa is consumed outside Creative. A Chamber and an existing queen are not required. [Pupa interaction][pupa]

## Behavior

A fresh anthill does not become a working colony merely by being placed. Home discovery and ant storage are unfinished, and Pupa use does not install a queen. The separate [Leafcutter Anthill guide](../blocks/LeafcutterNests.md#leafcutter-anthill) explains the conditional queen-release action and current nesting limits. [Nest implementation][nest]

## Notes

* This is the item form of `minecraft:leafcutter_anthill`.
* The placed block has hardness 0.5; no correct-tool requirement is registered, but the bundled block loot table is missing. [Block registration][block]
* Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on 2026-10-02; no in-game placement or colony test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2475-L2476
[category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1098-L1102
[pupa]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/item/ItemLeafcutterPupa.java#L18-L36
[nest]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/tileentity/TileEntityLeafcutterAnthill.java#L12-L60
[block]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L5360-L5369
