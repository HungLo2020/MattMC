# Flytrap

**Flytrap** (`minecraft:flytrap`) places a decorative plant that opens and closes. Its explicit Creative listing is in Natural Blocks; a natural starter supply or crafting recipe was not established in the checked data. [Registration][blocks] [items][] [creative][]

## Obtaining

An existing plant returns one Flytrap when ordinarily harvested. Bone Meal can produce one extra Flytrap item while preserving the original plant; this needs block item drops enabled. See [Flytrap propagation and timing](../blocks/AncientPlants.md#flytrap). [Loot][loot-flytrap] · [Bone Meal callback][flytrap] [block]

## Usage

Place it on dirt-tag ground or Farmland. The plant is non-colliding; its open state and fly particles do not provide an active prey-catching or damage mechanic. [Placement support][plant-support] · [Active plant class][flytrap]

## Related pages

- [Ancient trees, Flytraps and Tree Stars](../blocks/AncientPlants.md), [Tree Star](TreeStar.md), and [Items](Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. No gameplay test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[loot-flytrap]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/flytrap.json
[flytrap]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/custom/FlytrapBlock.java
[block]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Block.java
[plant-support]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/VegetationBlock.java
