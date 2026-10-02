# Glass Pane

**Glass Pane** (`minecraft:glass_pane`) is the ordinary, uncolored thin glass window block. Its connections, support, waterlogging, and light rules are in [Glass and Glass Panes](../blocks/GlassAndPanes.md). [Block registration][blocks] · [Item registration][items]

## Crafting

Arrange **six [Glass](Glass.md)** in **two full rows** on a [Crafting Table](../blocks/CraftingTable.md) to make **16 Glass Panes**. The recipe uses ordinary glass; stained blocks have their own matching-color pane recipes. [Recipe][recipe]

Ordinary panes can be dyed in groups of eight. Choose a [stained-pane color](../blocks/GlassAndPanes.md#forms-and-colors) for the exact recipe. Once stained, the bundled recipes do not turn them back into ordinary panes or recolor them into another stained color. [Color recipe rules](../blocks/GlassAndPanes.md#obtaining-and-color-choices)

## Collecting and placing

A **Silk Touch** tool recovers **one Glass Pane**. Without Silk Touch, its block loot produces no item. Breaking a pane never restores the full glass block used to craft it. [Block loot][loot]

See the family guide for [pane connections](../blocks/GlassAndPanes.md#placement-and-pane-connections) and [waterlogging](../blocks/GlassAndPanes.md#waterlogging). Ordinary panes do not add a stained color to a [beacon beam](../blocks/GlassAndPanes.md#beacon-beam-colors).

Related: [Glass](Glass.md) · [All glass colors](../blocks/GlassAndPanes.md#forms-and-colors) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. These are selected crafting and collection routes; no gameplay test was run. Data packs can change recipes and loot.

[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L2329-L2331
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L557
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/glass_pane.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/glass_pane.json
