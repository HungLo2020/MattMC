# Weathered Cut Copper

Weathered Cut Copper is a **weathered, unwaxed decorative full block that can be made into matching slabs, stairs, or Chiseled Copper**. See [Copper construction](../blocks/CopperConstruction.md#full-blocks) for its placed-block properties.

## Obtaining

Arrange **4 [Weathered Copper](WeatheredCopper.md) in a 2 × 2 square → 4 Weathered Cut Copper**. A [Stonecutter](../blocks/Stonecutter.md) gives **4 Weathered Cut Copper from just 1 Weathered Copper**, so it stretches the same input four times as far. [Crafting recipe][recipe-craft] · [Stonecutter recipe][recipe-stone-full]

Remove the wax from a placed [Waxed Weathered Cut Copper](WaxedWeatheredCutCopper.md) with an unbroken axe, or scrape a placed [Oxidized Cut Copper](OxidizedCutCopper.md) back one stage. Aging a placed [Exposed Cut Copper](ExposedCutCopper.md) also reaches this variant. Collect the resulting block after the conversion. [Axe conversions][axe] · [Stage map][weather]

Mine the matching placed block with an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** to recover **1 Weathered Cut Copper**. Wooden and Golden Pickaxes do not satisfy its drop requirement. See [mining and collection](../blocks/CopperConstruction.md#mining-and-collection) for the shared tool and explosion rules. [This variant’s loot][loot]

## Usage

Stonecut **1 Weathered Cut Copper** into **2 [Weathered Cut Copper Slab](WeatheredCutCopperSlab.md)**, **1 [Weathered Cut Copper Stairs](WeatheredCutCopperStairs.md)**, or **1 [Weathered Chiseled Copper](WeatheredChiseledCopper.md)**. [Slabs recipe][use-slabs] · [Stairs recipe][use-stairs] · [Chiseled recipe][use-chiseled]

For crafting-table slab and stair layouts, use the [matching-shape recipes](../blocks/CopperConstruction.md#crafting-layouts). The bundled recipes do not turn Cut Copper back into plain full copper blocks or ingots.

## Behavior

While placed and unwaxed, it can age into [Oxidized Cut Copper](OxidizedCutCopper.md). Use Honeycomb to preserve this stage as [Waxed Weathered Cut Copper](WaxedWeatheredCutCopper.md). [Aging callback][aging] · [Matching wax recipe][matching-wax-recipe]

Use the shared [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing), [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping), and [lightning-cleaning rules](../blocks/CopperConstruction.md#lightning-cleaning) when managing the finish.

## Notes

- This item is the item form of the `minecraft:weathered_cut_copper` block. [Item registration][item]
- Available through the [combined inventory item browser](../mechanics/InventoryBrowser.md) in Creative. [Creative entry][creative] · [Current catalog entry][catalog-entry] · [Combined browser][catalog-browser]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Recipes and drops describe bundled data; data packs can change them. No in-game test was run.

[recipe-craft]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/weathered_cut_copper.json
[recipe-stone-full]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/weathered_cut_copper_from_weathered_copper_stonecutting.json
[weather]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L17-L103
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L123
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/weathered_cut_copper.json
[use-slabs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/weathered_cut_copper_slab_from_weathered_cut_copper_stonecutting.json
[use-stairs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/weathered_cut_copper_stairs_from_weathered_cut_copper_stonecutting.json
[use-chiseled]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/weathered_chiseled_copper_from_weathered_cut_copper_stonecutting.json
[aging]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopperFullBlock.java#L23-L36
[matching-wax-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_cut_copper_from_honeycomb.json
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L190
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L478

[catalog-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L478
[catalog-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
