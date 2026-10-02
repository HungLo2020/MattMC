# Black Stained Glass Pane

**Black Stained Glass Pane** (`minecraft:black_stained_glass_pane`) is a thin black window block. It shares pane connections, waterlogging, light, and beacon rules with the [Glass and Glass Panes family](../blocks/GlassAndPanes.md). [Block registration][blocks] · [Item registration][items]

## Crafting

Use a [Crafting Table](../blocks/CraftingTable.md) for either checked recipe:

- **From colored blocks:** arrange **six [Black Stained Glass](BlackStainedGlass.md)** in two full rows to make **16 black stained-glass panes**. All six inputs must be this color. [Recipe][from-blocks]
- **From ordinary panes:** surround **one [Black Dye](BlackDye.md)** with **eight ordinary [Glass Panes](GlassPane.md)** to make **eight black stained-glass panes**. Place the dye in the center of the 3 × 3 grid. [Recipe][from-panes]

Neither recipe accepts panes already stained another color. See [color choices](../blocks/GlassAndPanes.md#obtaining-and-color-choices) before converting a large batch.

## Collecting and placing

A **Silk Touch** tool recovers **one black stained-glass pane** from the placed block. Without Silk Touch, its block loot produces no item. It does not turn back into a full glass block. [Block loot][loot]

Use the family guide for [connections and support](../blocks/GlassAndPanes.md#placement-and-pane-connections), [waterlogging](../blocks/GlassAndPanes.md#waterlogging), and [beacon colors](../blocks/GlassAndPanes.md#beacon-beam-colors).

Related: [Black Stained Glass](BlackStainedGlass.md) · [All glass colors](../blocks/GlassAndPanes.md#forms-and-colors) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. These are selected crafting and collection routes; no gameplay test was run. Data packs can change recipes and loot.

[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L3031-L3110
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L734-L749
[from-blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/black_stained_glass_pane.json
[from-panes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/black_stained_glass_pane_from_glass_pane.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/black_stained_glass_pane.json
