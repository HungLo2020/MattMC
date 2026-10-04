# Oxidized Copper

Oxidized Copper is a **fully oxidized, unwaxed full copper building block and a source material for matching copper construction pieces**. See [Copper construction](../blocks/CopperConstruction.md#full-blocks) for its placed-block properties.

## Obtaining

Let a placed [Weathered Copper](WeatheredCopper.md) oxidize to this stage, then collect it with the correct pickaxe. There is **no direct bundled crafting or Stonecutter recipe** for Oxidized Copper. [Exact stage progression][weather] See [aging and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing).

Remove the wax from a placed [Waxed Oxidized Copper](WaxedOxidizedCopper.md) with an unbroken axe. Collect the resulting block after the conversion. [Axe conversions][axe]

Mine the matching placed block with an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** to recover **1 Oxidized Copper**. Wooden and Golden Pickaxes do not satisfy its drop requirement. See [mining and collection](../blocks/CopperConstruction.md#mining-and-collection) for the shared tool and explosion rules. [This variant’s loot][loot]

## Usage

Each block can be stonecut into **4 [Oxidized Cut Copper](OxidizedCutCopper.md)**, **4 [Oxidized Chiseled Copper](OxidizedChiseledCopper.md)**, **4 [Oxidized Copper Grate](OxidizedCopperGrate.md)**, **8 [Oxidized Cut Copper Slab](OxidizedCutCopperSlab.md)**, or **4 [Oxidized Cut Copper Stairs](OxidizedCutCopperStairs.md)**, keeping its stage and wax state. [Cut recipe][use-cut] · [Chiseled recipe][use-chiseled] · [Grates recipe][use-grates] · [Slabs recipe][use-slabs] · [Stairs recipe][use-stairs]

This aged block has **no direct recipe into Copper Ingots**. To recover the metal, place it, scrape it back 3 stages to [Block of Copper](BlockOfCopper.md), then mine and unpack that block. See [wax removal and scraping](../blocks/CopperConstruction.md#waxing-and-scraping).

It also works as the full copper body for [creating a Copper Golem and Copper Chest](../blocks/CopperChests.md#creating-a-chest-with-a-copper-golem); place the carved pumpkin head last. [Construction pattern][golem]

## Behavior

This is the last oxidation stage, so it does not age further. Use Honeycomb to preserve this stage as [Waxed Oxidized Copper](WaxedOxidizedCopper.md). [Aging callback][aging] · [Matching wax recipe][matching-wax-recipe]

Use the shared [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing), [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping), and [lightning-cleaning rules](../blocks/CopperConstruction.md#lightning-cleaning) when managing the finish.

## Notes

- This item is the item form of the `minecraft:oxidized_copper` block. [Item registration][item]
- Available in the Creative Menu. [Creative entry][creative]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Recipes and drops describe bundled data; data packs can change them. No in-game test was run.

[weather]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L17-L103
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L123
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/oxidized_copper.json
[use-cut]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/oxidized_cut_copper_from_oxidized_copper_stonecutting.json
[use-chiseled]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/oxidized_chiseled_copper_from_oxidized_copper_stonecutting.json
[use-grates]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/oxidized_copper_grate_from_oxidized_copper_stonecutting.json
[use-slabs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/oxidized_cut_copper_slab_from_oxidized_copper_stonecutting.json
[use-stairs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/oxidized_cut_copper_stairs_from_oxidized_copper_stonecutting.json
[golem]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L212-L230
[aging]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopperFullBlock.java#L23-L36
[matching-wax-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_copper_from_honeycomb.json
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L183
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L486
