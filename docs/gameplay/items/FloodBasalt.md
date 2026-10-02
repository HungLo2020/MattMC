# Flood Basalt

**Flood Basalt** (`minecraft:flood_basalt`) is an orientable building block, explicitly listed in Building Blocks Creative. It is separate from ordinary Basalt and has no verified crafting or natural deposit route in this snapshot. [Registrations][blocks] [items][] [creative][]

## Obtaining

Its loot table names one Flood Basalt, but the placed block requires a correct tool and is absent from the standard mining tags. **An ordinary pickaxe does not pass that drop gate**, including with Silk Touch. Read the [mining limitation](../blocks/FloodBasaltAndFernThatch.md#flood-basalt-access-and-the-mining-limitation) before placing a supplied block in a Survival build. [Loot][loot-flood-basalt] · [Gate and tags][blocks] [pickaxe][] [tool-material][] [tool-component][] [player-tool][]

## Usage

Place it on the desired face to select its vertical or horizontal [pillar axis](../blocks/FloodBasaltAndFernThatch.md#orientation-and-placement). It has full collision, hardness 3 and blast resistance 100. [Properties][blocks] · [Axis selection][pillar]

## Related pages

- [Flood Basalt and Fern Thatch](../blocks/FloodBasaltAndFernThatch.md), [ordinary Basalt](../blocks/BlackstoneAndBasalt.md#basalt-variants-and-orientation), and [Items](Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. No gameplay test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[loot-flood-basalt]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/flood_basalt.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/ToolMaterial.java
[tool-component]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/component/Tool.java
[player-tool]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[pillar]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java
