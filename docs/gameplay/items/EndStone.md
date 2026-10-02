# End Stone

**End Stone** (`minecraft:end_stone`) is the placeable base material of End island terrain. [Registration][blocks] [items] · [Normal End settings][end-settings]

## Obtaining and use

Mine it with an **unbroken pickaxe**, including Wood, to collect one End Stone. Silk Touch is unnecessary and Fortune does not multiply the drop. The [family guide](../blocks/EndStoneAndPurpur.md#obtaining-in-the-end) traces the active normal-world route. [Loot][loot-end-stone] · [Mining tag][pickaxe]

Use the [End Stone recipes](../blocks/EndStoneAndPurpur.md#end-stone-variants-and-recipes) for bricks and their shapes. Raw End Stone also supports Chorus Flowers and has a Dragon block-clearing protection that End Stone Bricks lacks; see the [placed-behavior section](../blocks/EndStoneAndPurpur.md#placement-pillars-and-dragon-interactions) before choosing a material near the dragon. [Brick recipe][crafting-end-stone-bricks] · [Flower support][chorus-flower] · [Dragon-immune tag][dragon-immune]

Related: [End Stone Bricks](EndStoneBricks.md) · [End](../dimensions/End.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `688be47ebeaa38beb06130efddf19484a8b81580`; registration and relevant recipes, loot and active acquisition paths checked. No in-game acquisition, crafting or placement test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/Items.java
[end-settings]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/worldgen/noise_settings/end.json
[loot-end-stone]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/end_stone.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[crafting-end-stone-bricks]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/end_stone_bricks.json
[chorus-flower]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java
[dragon-immune]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/block/dragon_immune.json
