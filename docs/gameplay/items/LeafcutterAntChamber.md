# Leafcutter Ant Chamber

## Obtaining

Leafcutter Ant Chamber has a registered block item and an ordinary **Functional Blocks** category entry. Request it through the [inventory item browser](../mechanics/InventoryBrowser.md) in Survival or Creative. No bundled crafting, natural-generation, or mining-drop route was found; see [nest acquisition and recovery](../blocks/LeafcutterNests.md#obtaining-and-recovering-the-blocks). [Item registration][item] · [Category entry][category]

## Usage

Place it as a decorative block associated with [Leafcutter Ant](../mobs/LeafcutterAnt.md) content. Its outline and collision are 14/16 of a block high; it does not need an anthill or special ground to stay placed. [Chamber class][chamber] · [Default support and collision][defaults]

## Behavior

The current block has no fungus growth, harvesting action, ant storage, or colony-capacity upgrade. Placing it beneath an anthill does not activate those systems. See the [Leafcutter Ant Chamber guide](../blocks/LeafcutterNests.md#leafcutter-ant-chamber) for the checked limits. [Complete chamber class][chamber] · [Nest storage][nest]

## Notes

* This is the item form of `minecraft:leafcutter_ant_chamber`.
* Its placed-block hardness is 0.3; no correct-tool requirement is registered, but the bundled block loot table is missing. [Block registration][block]
* Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on 2026-10-02; no in-game placement or harvesting test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2475-L2476
[category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1098-L1102
[chamber]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/block/BlockLeafcutterAntChamber.java#L12-L29
[defaults]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L327
[nest]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/alexsmobs/tileentity/TileEntityLeafcutterAnthill.java#L12-L60
[block]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L5360-L5369
