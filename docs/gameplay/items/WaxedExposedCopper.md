# Waxed Exposed Copper

Waxed Exposed Copper is an **exposed, waxed full copper building block and a source material for matching copper construction pieces**. See [Copper construction](../blocks/CopperConstruction.md#full-blocks) for its placed-block properties.

## Obtaining

Combine **1 [Exposed Copper](ExposedCopper.md) + 1 [Honeycomb](Honeycomb.md) → 1 Waxed Exposed Copper** in any crafting-grid positions. You can also apply one Honeycomb to the matching placed unwaxed block and then collect it. [Waxing recipe][recipe-wax] · [Placed waxing][wax]

Mine the matching placed block with an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** to recover **1 Waxed Exposed Copper**. Wooden and Golden Pickaxes do not satisfy its drop requirement. See [mining and collection](../blocks/CopperConstruction.md#mining-and-collection) for the shared tool and explosion rules. [This variant’s loot][loot]

## Usage

Each block can be stonecut into **4 [Waxed Exposed Cut Copper](WaxedExposedCutCopper.md)**, **4 [Waxed Exposed Chiseled Copper](WaxedExposedChiseledCopper.md)**, **4 [Waxed Exposed Copper Grate](WaxedExposedCopperGrate.md)**, **8 [Waxed Exposed Cut Copper Slab](WaxedExposedCutCopperSlab.md)**, or **4 [Waxed Exposed Cut Copper Stairs](WaxedExposedCutCopperStairs.md)**, keeping its stage and wax state. [Cut recipe][use-cut] · [Chiseled recipe][use-chiseled] · [Grates recipe][use-grates] · [Slabs recipe][use-slabs] · [Stairs recipe][use-stairs]

This aged block has **no direct recipe into Copper Ingots**. To recover the metal, place it, remove its wax and then scrape it back 1 stage to [Block of Copper](BlockOfCopper.md), then mine and unpack that block. See [wax removal and scraping](../blocks/CopperConstruction.md#waxing-and-scraping).

It also works as the full copper body for [creating a Copper Golem and Copper Chest](../blocks/CopperChests.md#creating-a-chest-with-a-copper-golem); place the carved pumpkin head last. [Construction pattern][golem]

## Behavior

Wax keeps this finish from aging. One use of an unbroken axe removes the wax and leaves [Exposed Copper](ExposedCopper.md), **without changing its oxidation stage**. [Wax/axe conversion][axe]

Use the shared [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing), [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping), and [lightning-cleaning rules](../blocks/CopperConstruction.md#lightning-cleaning) when managing the finish.

## Notes

- This item is the item form of the `minecraft:waxed_exposed_copper` block. [Item registration][item]
- Available in the Creative Menu. [Creative entry][creative]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Recipes and drops describe bundled data; data packs can change them. No in-game test was run.

[recipe-wax]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_from_honeycomb.json
[wax]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/HoneycombItem.java#L24-L113
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_copper.json
[use-cut]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_cut_copper_from_waxed_exposed_copper_stonecutting.json
[use-chiseled]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_chiseled_copper_from_waxed_exposed_copper_stonecutting.json
[use-grates]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_copper_grate_from_waxed_exposed_copper_stonecutting.json
[use-slabs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_cut_copper_slab_from_waxed_exposed_copper_stonecutting.json
[use-stairs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_cut_copper_stairs_from_waxed_exposed_copper_stonecutting.json
[golem]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L212-L230
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L123
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L201
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L508
