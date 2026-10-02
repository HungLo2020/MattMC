# Nether Quartz

**Nether Quartz** (`minecraft:quartz`) is a crafting resource and an armor-trim material. It is a small item, separate from the placeable Block of Quartz. [Item registration][items]

## Obtaining and use

The [Ore Resources guide](../blocks/OreResources.md#bring-a-suitable-pickaxe) owns Nether Quartz Ore mining, tool requirements, Silk Touch/Fortune and ore processing. The ore's ordinary loot selects Nether Quartz; its Silk Touch branch selects the ore block instead. [Ore loot][quartz-ore-loot]

Use Nether Quartz for the [Block of Quartz recipe](../blocks/Quartz.md#crafting-and-smoothing). The [decorative-stone recipes](../blocks/DecorativeStone.md#crafting-the-base-and-polished-blocks) also use it for Diorite and Granite. These are crafting ingredients, not a claim that quartz building blocks can be unpacked back into crystals. [Quartz-block recipe][crafting-quartz-block]

It is also accepted as a material in the [armor-trim workflow](../smithing/Smithing.md#example-apply-an-armor-trim). That use still needs an appropriate trim template and trimmable armor. [Trim-material tag][trim-materials]

Related: [Block of Quartz](BlockOfQuartz.md) · [Nether Quartz Ore](NetherQuartzOre.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `688be47ebeaa38beb06130efddf19484a8b81580`; registration and relevant recipes, loot and active acquisition paths checked. No in-game acquisition, crafting or placement test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/Items.java
[quartz-ore-loot]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/nether_quartz_ore.json
[crafting-quartz-block]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/recipe/crafting/quartz_block.json
[trim-materials]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/tags/item/trim_materials.json
