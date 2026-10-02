# Brown Stained Glass

**Brown Stained Glass** (`minecraft:brown_stained_glass`) is a full glass block colored brown. Its shared placement, light, mining, and beacon behavior is covered in [Glass and Glass Panes](../blocks/GlassAndPanes.md). [Block registration][blocks] · [Item registration][items]

## Crafting

On a [Crafting Table](../blocks/CraftingTable.md), surround **one [Brown Dye](BrownDye.md)** with **eight ordinary [Glass](Glass.md)** to make **eight brown stained-glass blocks**. The dye goes in the center of the 3 × 3 grid. This recipe accepts ordinary glass, not glass already stained another color. [Recipe][recipe]

For a thin window, use the [Brown Stained Glass Pane recipes](BrownStainedGlassPane.md#crafting). The bundled recipes provide no route for dyeing this block into another stained-glass color; choose the color before crafting. [Color recipe rules](../blocks/GlassAndPanes.md#obtaining-and-color-choices)

## Collecting and building

Break it with a **Silk Touch** tool to recover **one brown stained glass**. Without Silk Touch, its block loot produces no item. [Block loot][loot]

See the family guide for [placement](../blocks/GlassAndPanes.md#placement-and-pane-connections), [light transmission](../blocks/GlassAndPanes.md#light-and-transparency), and [beacon color mixing](../blocks/GlassAndPanes.md#beacon-beam-colors).

Related: [Brown Stained Glass Pane](BrownStainedGlassPane.md) · [All glass colors](../blocks/GlassAndPanes.md#forms-and-colors) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. These are selected crafting and collection routes; no gameplay test was run. Data packs can change recipes and loot.

[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L2122-L2137
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L718-L733
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/brown_stained_glass.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/brown_stained_glass.json
