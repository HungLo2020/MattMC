# Tinted Glass

**Tinted Glass** (`minecraft:tinted_glass`) is a full, see-through glass block that blocks light. It is a separate material from the 16 stained-glass colors, including Black Stained Glass. Use the [family comparison](../blocks/GlassAndPanes.md#tinted-glass) when choosing between them. [Registration][blocks] · [Item registration][items] · [Light behavior][tinted]

## Crafting

Place **one ordinary [Glass](Glass.md)** in the center of a [Crafting Table](../blocks/CraftingTable.md), with **four [Amethyst Shards](AmethystShard.md)** directly above, below, left, and right. Leave the corners empty. This produces **two Tinted Glass**. Stained glass is not accepted by this recipe. [Recipe][recipe]

## Collection and use

Ordinary breaking returns **one Tinted Glass without Silk Touch**. Its loot has an explosion-survival condition, so an explosion does not guarantee recovery. [Block loot][loot]

Use it where a transparent wall should block light. Its source reports maximum light blocking and no downward skylight propagation. It also **blocks a beacon beam** passing through its position, rather than coloring that beam. [Light behavior][tinted] · [Beacon obstruction check][beacon]

For collision and placement, see the [glass-family guide](../blocks/GlassAndPanes.md#placement-and-pane-connections).

Related: [Glass](Glass.md) · [Black Stained Glass](BlackStainedGlass.md) · [Glass and Glass Panes](../blocks/GlassAndPanes.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. These are selected crafting and collection routes; no gameplay test was run. Data packs can change recipes and loot.

[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L6040-L6050
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L281-L282
[tinted]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/TintedGlassBlock.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/tinted_glass.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/tinted_glass.json
[beacon]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L143-L166
