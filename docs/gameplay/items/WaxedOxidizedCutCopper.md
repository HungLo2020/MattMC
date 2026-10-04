# Waxed Oxidized Cut Copper

Waxed Oxidized Cut Copper is a **fully oxidized, waxed decorative full block that can be made into matching slabs, stairs, or Chiseled Copper**. See [Copper construction](../blocks/CopperConstruction.md#full-blocks) for its placed-block properties.

## Obtaining

Arrange **4 [Waxed Oxidized Copper](WaxedOxidizedCopper.md) in a 2 × 2 square → 4 Waxed Oxidized Cut Copper**. A [Stonecutter](../blocks/Stonecutter.md) gives **4 Waxed Oxidized Cut Copper from just 1 Waxed Oxidized Copper**, so it stretches the same input four times as far. [Crafting recipe][recipe-craft] · [Stonecutter recipe][recipe-stone-full]

Combine **1 [Oxidized Cut Copper](OxidizedCutCopper.md) + 1 [Honeycomb](Honeycomb.md) → 1 Waxed Oxidized Cut Copper** in any crafting-grid positions. You can also apply one Honeycomb to the matching placed unwaxed block and then collect it. [Waxing recipe][recipe-wax] · [Placed waxing][wax]

Mine the matching placed block with an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** to recover **1 Waxed Oxidized Cut Copper**. Wooden and Golden Pickaxes do not satisfy its drop requirement. See [mining and collection](../blocks/CopperConstruction.md#mining-and-collection) for the shared tool and explosion rules. [This variant’s loot][loot]

## Usage

Stonecut **1 Waxed Oxidized Cut Copper** into **2 [Waxed Oxidized Cut Copper Slab](WaxedOxidizedCutCopperSlab.md)**, **1 [Waxed Oxidized Cut Copper Stairs](WaxedOxidizedCutCopperStairs.md)**, or **1 [Waxed Oxidized Chiseled Copper](WaxedOxidizedChiseledCopper.md)**. [Slabs recipe][use-slabs] · [Stairs recipe][use-stairs] · [Chiseled recipe][use-chiseled]

For crafting-table slab and stair layouts, use the [matching-shape recipes](../blocks/CopperConstruction.md#crafting-layouts). The bundled recipes do not turn Cut Copper back into plain full copper blocks or ingots.

## Behavior

Wax keeps this finish from aging. One use of an unbroken axe removes the wax and leaves [Oxidized Cut Copper](OxidizedCutCopper.md), **without changing its oxidation stage**. [Wax/axe conversion][axe]

Use the shared [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing), [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping), and [lightning-cleaning rules](../blocks/CopperConstruction.md#lightning-cleaning) when managing the finish.

## Notes

- This item is the item form of the `minecraft:waxed_oxidized_cut_copper` block. [Item registration][item]
- Available in the Creative Menu. [Creative entry][creative]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Recipes and drops describe bundled data; data packs can change them. No in-game test was run.

[recipe-craft]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_cut_copper.json
[recipe-stone-full]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_oxidized_cut_copper_from_waxed_oxidized_copper_stonecutting.json
[recipe-wax]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_cut_copper_from_honeycomb.json
[wax]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/HoneycombItem.java#L24-L113
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_cut_copper.json
[use-slabs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_oxidized_cut_copper_slab_from_waxed_oxidized_cut_copper_stonecutting.json
[use-stairs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_oxidized_cut_copper_stairs_from_waxed_oxidized_cut_copper_stonecutting.json
[use-chiseled]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_oxidized_chiseled_copper_from_waxed_oxidized_cut_copper_stonecutting.json
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L123
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L211
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L533
