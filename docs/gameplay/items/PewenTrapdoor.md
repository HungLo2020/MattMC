# Pewen Trapdoor

`minecraft:pewen_trapdoor` places a **single-block hinged panel** using Cherry block-set controls. Its top/bottom setting locates the closed panel within one block space. [Item binding][item] · [Block registration][block] · [Shape][shape]

## Obtaining

Two full rows of **three Pewen Planks** craft **two Pewen Trapdoors**. This recipe names Pewen Planks directly. See the [Pewen construction recipes](../blocks/Pewen.md#construction-and-recipes). [Recipe][recipe]

Getting the planks is a separate step: the [Pewen recipe caveats](../blocks/Pewen.md#recipes-and-tools-that-need-caution) explain the incompatible log-to-plank definition. The trapdoor recipe alone does not establish an end-to-end natural acquisition route.

The ordinary [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) can also provide this item in Creative, subject to its cursor, space, and feature checks. [Listing][listing]

## Usage

Use it as a hatch, shutter, or decorative panel. It opens by hand or redstone and can be waterlogged. Its controls come from [Pewen's block-set configuration](../blocks/Pewen.md#block-behavior); the shared trapdoor callback handles opening, signal changes, and placement. [Cherry settings][type] · [Control and placement][control]

## Behavior

Normal hand mining returns **one Pewen Trapdoor** without Silk Touch. Its registration has no correct-tool drop requirement; explosion recovery is conditional. [Registration][block] · [Player drop gate][gate] · [Loot][loot]

## Notes

The bundled axe-mineable tag does **not** include Pewen Trapdoor, so do not infer the ordinary axe speed bonus from its wood appearance. Keep the [Pewen integration limits](../blocks/Pewen.md#recipes-and-tools-that-need-caution) separate from standard wood rules. [Axe tag][axe]

Source-reviewed at `9363264a1615b1c23931f39e1189cda0cc088490` on 2026-10-02. Selected bundled routes are described here; data packs can change recipes, tags, and loot. No gameplay test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/Items.java#L311-L311
[block]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/level/block/Blocks.java#L7039-L7047
[listing]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L247
[recipe]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/resources/data/minecraft/recipe/pewen_trapdoor.json
[control]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L92-L165
[shape]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L45-L75
[type]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L123-L140
[loot]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/resources/data/minecraft/loot_table/blocks/pewen_trapdoor.json
[axe]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[gate]: https://github.com/HungLo2020/MattMC/blob/9363264a1615b1c23931f39e1189cda0cc088490/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
