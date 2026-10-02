# Gray Carpet

**Gray Carpet** (`minecraft:gray_carpet`) is a thin gray floor covering made from wool. Its placement, support, mining, fire, fuel, and vibration rules are in the [Wool and Carpet guide](../blocks/WoolAndCarpet.md). [Block registration][blocks] · [Item registration][items]

## Crafting

Place **two [Gray Wool](GrayWool.md) side by side in one row** to make **three gray carpets**. Both wool inputs must be gray; mixing wool colors does not match this recipe. [Recipe][craft]

To recolor an existing carpet, combine **one [Gray Dye](GrayDye.md)** with **one wool carpet of any other bundled color**, shapeless, to make **one gray carpet**. All 15 other wool-carpet colors are accepted; gray carpet, [Moss Carpet](MossCarpet.md), and [Pale Moss Carpet](PaleMossCarpet.md) are not inputs. [Recoloring recipe][dye]

## Collection and use

Breaking placed gray carpet normally returns **one gray carpet**, not wool. There is no Silk Touch requirement; its loot has an explosion-survival condition. [Block loot][loot]

Place it above a supporting block and keep it away from fire. See [placement and support](../blocks/WoolAndCarpet.md#placement-and-support) before removing the floor beneath it, and [vibrations](../blocks/WoolAndCarpet.md#vibrations) before relying on carpet around sculk.

Related: [Gray Wool](GrayWool.md) · [All wool and carpet colors](../blocks/WoolAndCarpet.md#colors-and-item-ids) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. These are selected crafting and collection routes. No gameplay test was run. Data packs can change recipes, tags, and loot.

[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L3194-L3273
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L658-L708
[craft]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/gray_carpet.json
[dye]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/dye_gray_carpet.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/gray_carpet.json
