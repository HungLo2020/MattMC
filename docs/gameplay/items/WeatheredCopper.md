# Weathered Copper

Weathered Copper is a **weathered, unwaxed full copper building block and a source material for matching copper construction pieces**. See [Copper construction](../blocks/CopperConstruction.md#full-blocks) for its placed-block properties.

## Obtaining

Let a placed [Exposed Copper](ExposedCopper.md) oxidize to this stage, then collect it with the correct pickaxe. There is **no direct bundled crafting or Stonecutter recipe** for Weathered Copper. [Exact stage progression][weather] See [aging and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing).

Remove the wax from a placed [Waxed Weathered Copper](WaxedWeatheredCopper.md) with an unbroken axe, or scrape a placed [Oxidized Copper](OxidizedCopper.md) back one stage. Collect the resulting block after the conversion. [Axe conversions][axe]

Mine the matching placed block with an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** to recover **1 Weathered Copper**. Wooden and Golden Pickaxes do not satisfy its drop requirement. See [mining and collection](../blocks/CopperConstruction.md#mining-and-collection) for the shared tool and explosion rules. [This variant’s loot][loot]

## Usage

Each block can be stonecut into **4 [Weathered Cut Copper](WeatheredCutCopper.md)**, **4 [Weathered Chiseled Copper](WeatheredChiseledCopper.md)**, **4 [Weathered Copper Grate](WeatheredCopperGrate.md)**, **8 [Weathered Cut Copper Slab](WeatheredCutCopperSlab.md)**, or **4 [Weathered Cut Copper Stairs](WeatheredCutCopperStairs.md)**, keeping its stage and wax state. [Cut recipe][use-cut] · [Chiseled recipe][use-chiseled] · [Grates recipe][use-grates] · [Slabs recipe][use-slabs] · [Stairs recipe][use-stairs]

This aged block has **no direct recipe into Copper Ingots**. To recover the metal, place it, scrape it back 2 stages to [Block of Copper](BlockOfCopper.md), then mine and unpack that block. See [wax removal and scraping](../blocks/CopperConstruction.md#waxing-and-scraping).

It also works as the full copper body for [creating a Copper Golem and Copper Chest](../blocks/CopperChests.md#creating-a-chest-with-a-copper-golem); place the carved pumpkin head last. [Construction pattern][golem]

## Behavior

While placed and unwaxed, it can age into [Oxidized Copper](OxidizedCopper.md). Use Honeycomb to preserve this stage as [Waxed Weathered Copper](WaxedWeatheredCopper.md). [Aging callback][aging] · [Matching wax recipe][matching-wax-recipe]

Use the shared [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing), [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping), and [lightning-cleaning rules](../blocks/CopperConstruction.md#lightning-cleaning) when managing the finish.

## Notes

- This item is the item form of the `minecraft:weathered_copper` block. [Item registration][item]
- Available through the [combined inventory item browser](../mechanics/InventoryBrowser.md) in Creative. [Creative entry][creative] · [Current catalog entry][catalog-entry] · [Combined browser][catalog-browser]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Recipes and drops describe bundled data; data packs can change them. No in-game test was run.

[weather]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L17-L103
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L123
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/weathered_copper.json
[use-cut]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/weathered_cut_copper_from_weathered_copper_stonecutting.json
[use-chiseled]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/weathered_chiseled_copper_from_weathered_copper_stonecutting.json
[use-grates]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/weathered_copper_grate_from_weathered_copper_stonecutting.json
[use-slabs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/weathered_cut_copper_slab_from_weathered_copper_stonecutting.json
[use-stairs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/weathered_cut_copper_stairs_from_weathered_copper_stonecutting.json
[golem]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L212-L230
[aging]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopperFullBlock.java#L23-L36
[matching-wax-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_from_honeycomb.json
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L182
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L475

[catalog-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L475
[catalog-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
