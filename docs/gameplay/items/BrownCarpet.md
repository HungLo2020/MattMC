# Brown Carpet

**Brown Carpet** (`minecraft:brown_carpet`) is a thin brown floor covering made from wool. Its placement, support, mining, fire, fuel, and vibration rules are in the [Wool and Carpet guide](../blocks/WoolAndCarpet.md). [Block registration][blocks] · [Item registration][items]

## Crafting

Place **two [Brown Wool](BrownWool.md) side by side in one row** to make **three brown carpets**. Both wool inputs must be brown; mixing wool colors does not match this recipe. [Recipe][craft]

To recolor an existing carpet, combine **one [Brown Dye](BrownDye.md)** with **one wool carpet of any other bundled color**, shapeless, to make **one brown carpet**. All 15 other wool-carpet colors are accepted; brown carpet, [Moss Carpet](MossCarpet.md), and [Pale Moss Carpet](PaleMossCarpet.md) are not inputs. [Recoloring recipe][dye]

## Collection and use

Breaking placed brown carpet normally returns **one brown carpet**, not wool. There is no Silk Touch requirement; its loot has an explosion-survival condition. [Block loot][loot]

Place it above a supporting block and keep it away from fire. See [placement and support](../blocks/WoolAndCarpet.md#placement-and-support) before removing the floor beneath it, and [vibrations](../blocks/WoolAndCarpet.md#vibrations) before relying on carpet around sculk.

Related: [Brown Wool](BrownWool.md) · [All wool and carpet colors](../blocks/WoolAndCarpet.md#colors-and-item-ids) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. These are selected crafting and collection routes. No gameplay test was run. Data packs can change recipes, tags, and loot.

[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L3194-L3273
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L658-L708
[craft]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/brown_carpet.json
[dye]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/dye_brown_carpet.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/brown_carpet.json
