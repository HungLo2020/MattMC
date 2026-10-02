# Red Concrete Powder

**Red Concrete Powder** (`minecraft:red_concrete_powder`) is the loose red building material that falls without suitable support and hardens into [Red Concrete](RedConcrete.md) when it meets qualifying water contact. [Registration and matching target][block] · [Item registration][item] · [Shared Concrete guide](../blocks/Concrete.md)

## Crafting

Use the [shared concrete-powder recipe](../blocks/Concrete.md#crafting-and-color-choices) with **[Red Dye](RedDye.md)** to make **eight red concrete powder**. This color's recipe uses ordinary [Sand](Sand.md) and [Gravel](Gravel.md); **Red Sand is not an accepted substitute**. [Exact recipe][recipe]

The dye sets the color when powder is crafted. The bundled recipes provide no route for recoloring existing concrete or powder; see [color choices](../blocks/Concrete.md#crafting-and-color-choices).

## Collection and hardening

Breaking dry, placed red concrete powder normally returns **one red concrete powder**. No special tool or Silk Touch is required; a shovel is the efficient mining tool. Its loot has an explosion-survival condition. [Block loot][loot] · [Powder tag][powder-tag] · [Shovel tag][shovel]

Once it hardens, collect the resulting **red concrete with an unbroken pickaxe**. Use [water hardening](../blocks/Concrete.md#hardening-powder-with-water) for conversion and [falling and support](../blocks/Concrete.md#falling-and-support) before building over an empty space.

Related: [Red Concrete](RedConcrete.md) · [All concrete colors](../blocks/Concrete.md#colors-and-item-ids) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. These are the checked crafting, hardening, and collection routes. No gameplay test was run. Data packs can change recipes, tags, and loot.

[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L4648-L4727
[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L855-L870
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/red_concrete_powder.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/red_concrete_powder.json
[powder-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/concrete_powder.json
[shovel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
