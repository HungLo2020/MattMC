# Netherite Ingot

Netherite Ingot is a smithing and crafting material registered as `minecraft:netherite_ingot`.

## Crafting

Combine **four Netherite Scrap and four Gold Ingots** in a shapeless Crafting Table recipe to produce **one Netherite Ingot**. Smelting Ancient Debris produces scrap, not a finished ingot.

Nine ingots can be packed into one Block of Netherite with its crafting recipe, and one Block of Netherite unpacks into nine ingots. These packing recipes do not create extra material.

## Smithing

The bundled Netherite-tool-materials tag contains Netherite Ingot. A verified example uses a **Netherite Upgrade Smithing Template + Diamond Pickaxe + Netherite Ingot** to produce a Netherite Pickaxe in a [Smithing Table](../blocks/SmithingTable.md).

The template is a separate input. See [smithing](../smithing/Smithing.md) for consumption and preserved data, and [the template page](SmithingTemplateNetheriteUpgrade.md) for acquiring and duplicating templates.

The ingot is also registered as a Netherite armor-trim material. A decorative trim and an equipment-material upgrade are different operations; do not infer upgraded stats from trim color alone.

## Item property

The ingot registration is fire-resistant. This does not promise that dropped ingots survive every explosion, world boundary, or despawn condition.

## Related pages

- [Netherite Scrap](NetheriteScrap.md)
- [Netherite Upgrade template](SmithingTemplateNetheriteUpgrade.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game loot, smithing, or smelting test was run.

- [Ingot crafting](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/netherite_ingot.json)
- [Block packing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/netherite_block.json)
- [Block unpacking](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/netherite_ingot_from_netherite_block.json)
- [Tool-material tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/netherite_tool_materials.json)
- [Pickaxe upgrade](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/smithing/netherite_pickaxe_smithing.json)
- [Fire resistance and trim material](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1304)
