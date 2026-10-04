# Waxed Block of Copper

Waxed Block of Copper is an **unaffected, waxed full copper building block and a source material for matching copper construction pieces**. See [Copper construction](../blocks/CopperConstruction.md#full-blocks) for its placed-block properties.

## Obtaining

Combine **1 [Block of Copper](BlockOfCopper.md) + 1 [Honeycomb](Honeycomb.md) → 1 Waxed Block of Copper** in any crafting-grid positions. You can also apply one Honeycomb to the matching placed unwaxed block and then collect it. [Waxing recipe][recipe-wax] · [Placed waxing][wax]

Mine the matching placed block with an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** to recover **1 Waxed Block of Copper**. Wooden and Golden Pickaxes do not satisfy its drop requirement. See [mining and collection](../blocks/CopperConstruction.md#mining-and-collection) for the shared tool and explosion rules. [This variant’s loot][loot]

## Usage

Each block can be stonecut into **4 [Waxed Cut Copper](WaxedCutCopper.md)**, **4 [Waxed Chiseled Copper](WaxedChiseledCopper.md)**, **4 [Waxed Copper Grate](WaxedCopperGrate.md)**, **8 [Waxed Cut Copper Slab](WaxedCutCopperSlab.md)**, or **4 [Waxed Cut Copper Stairs](WaxedCutCopperStairs.md)**, keeping its stage and wax state. [Cut recipe][use-cut] · [Chiseled recipe][use-chiseled] · [Grates recipe][use-grates] · [Slabs recipe][use-slabs] · [Stairs recipe][use-stairs]

Place **1 Waxed Block of Copper alone in a crafting grid → 9 [Copper Ingots](CopperIngot.md)**. [Unpacking recipe][recipe-unpack]

It also works as the full copper body for [creating a Copper Golem and Copper Chest](../blocks/CopperChests.md#creating-a-chest-with-a-copper-golem); place the carved pumpkin head last. [Construction pattern][golem]

## Behavior

Wax keeps this finish from aging. One use of an unbroken axe removes the wax and leaves [Block of Copper](BlockOfCopper.md), **without changing its oxidation stage**. [Wax/axe conversion][axe]

Use the shared [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing), [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping), and [lightning-cleaning rules](../blocks/CopperConstruction.md#lightning-cleaning) when managing the finish.

## Notes

- This item is the item form of the `minecraft:waxed_copper_block` block. [Item registration][item]
- Available in the Creative Menu. [Creative entry][creative]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Recipes and drops describe bundled data; data packs can change them. No in-game test was run.

[recipe-wax]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_block_from_honeycomb.json
[wax]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/HoneycombItem.java#L24-L113
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/waxed_copper_block.json
[use-cut]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_cut_copper_from_waxed_copper_block_stonecutting.json
[use-chiseled]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_chiseled_copper_from_waxed_copper_block_stonecutting.json
[use-grates]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_copper_grate_from_waxed_copper_block_stonecutting.json
[use-slabs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_cut_copper_slab_from_waxed_copper_block_stonecutting.json
[use-stairs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_cut_copper_stairs_from_waxed_copper_block_stonecutting.json
[recipe-unpack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/copper_ingot_from_waxed_copper_block.json
[golem]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L212-L230
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L123
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L200
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L497
