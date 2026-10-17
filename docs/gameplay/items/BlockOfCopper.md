# Block of Copper

Block of Copper is an **unaffected, unwaxed full copper building block and a source material for matching copper construction pieces**. See [Copper construction](../blocks/CopperConstruction.md#full-blocks) for its placed-block properties.

## Obtaining

Fill a Crafting Table’s **3 × 3 grid with 9 [Copper Ingots](CopperIngot.md)** to make **1 Block of Copper**. [Packing recipe][recipe-pack]

Remove the wax from a placed [Waxed Block of Copper](WaxedBlockofCopper.md) with an unbroken axe, or scrape a placed [Exposed Copper](ExposedCopper.md) back one stage. Collect the resulting block after the conversion. [Axe conversions][axe]

Mine the matching placed block with an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** to recover **1 Block of Copper**. Wooden and Golden Pickaxes do not satisfy its drop requirement. See [mining and collection](../blocks/CopperConstruction.md#mining-and-collection) for the shared tool and explosion rules. [This variant’s loot][loot]

## Usage

Each block can be stonecut into **4 [Cut Copper](CutCopper.md)**, **4 [Chiseled Copper](ChiseledCopper.md)**, **4 [Copper Grate](CopperGrate.md)**, **8 [Cut Copper Slab](CutCopperSlab.md)**, or **4 [Cut Copper Stairs](CutCopperStairs.md)**, keeping its stage and wax state. [Cut recipe][use-cut] · [Chiseled recipe][use-chiseled] · [Grates recipe][use-grates] · [Slabs recipe][use-slabs] · [Stairs recipe][use-stairs]

Place **1 Block of Copper alone in a crafting grid → 9 [Copper Ingots](CopperIngot.md)**. [Unpacking recipe][recipe-unpack]

It also works as the full copper body for [creating a Copper Golem and Copper Chest](../blocks/CopperChests.md#creating-a-chest-with-a-copper-golem); place the carved pumpkin head last. [Construction pattern][golem]

## Behavior

While placed and unwaxed, it can age into [Exposed Copper](ExposedCopper.md). Use Honeycomb to preserve this stage as [Waxed Block of Copper](WaxedBlockofCopper.md). [Aging callback][aging] · [Matching wax recipe][matching-wax-recipe]

Use the shared [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing), [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping), and [lightning-cleaning rules](../blocks/CopperConstruction.md#lightning-cleaning) when managing the finish.

## Notes

- This item is the item form of the `minecraft:copper_block` block. [Item registration][item]
- Available through the [combined inventory item browser](../mechanics/InventoryBrowser.md) in Creative. [Creative entry][creative] · [Current catalog entry][catalog-entry] · [Combined browser][catalog-browser]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Recipes and drops describe bundled data; data packs can change them. No in-game test was run.

[recipe-pack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/copper_block.json
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L123
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/copper_block.json
[use-cut]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/cut_copper_from_copper_block_stonecutting.json
[use-chiseled]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_copper_from_copper_block_stonecutting.json
[use-grates]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/copper_grate_from_copper_block_stonecutting.json
[use-slabs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/cut_copper_slab_from_copper_block_stonecutting.json
[use-stairs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/cut_copper_stairs_from_copper_block_stonecutting.json
[recipe-unpack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/copper_ingot.json
[golem]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L212-L230
[aging]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopperFullBlock.java#L23-L36
[matching-wax-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_block_from_honeycomb.json
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L177
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L453

[catalog-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L453
[catalog-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
