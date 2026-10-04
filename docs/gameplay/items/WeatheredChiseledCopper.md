# Weathered Chiseled Copper

Weathered Chiseled Copper is a **weathered, unwaxed decorative full block in the chiseled copper family**. See [Copper construction](../blocks/CopperConstruction.md#full-blocks) for its placed-block properties.

## Obtaining

Place **2 [Weathered Cut Copper Slab](WeatheredCutCopperSlab.md) vertically → 1 Weathered Chiseled Copper**. This fits the inventory crafting grid. [Slab recipe][recipe-craft]

At a [Stonecutter](../blocks/Stonecutter.md), **1 [Weathered Copper](WeatheredCopper.md) → 4 Weathered Chiseled Copper**, or **1 [Weathered Cut Copper](WeatheredCutCopper.md) → 1 Weathered Chiseled Copper**. Choose the full copper input when making these blocks in bulk. [Full-block input][recipe-stone-full] · [Cut input][recipe-stone-cut]

Remove the wax from a placed [Waxed Weathered Chiseled Copper](WaxedWeatheredChiseledCopper.md) with an unbroken axe, or scrape a placed [Oxidized Chiseled Copper](OxidizedChiseledCopper.md) back one stage. Aging a placed [Exposed Chiseled Copper](ExposedChiseledCopper.md) also reaches this variant. Collect the resulting block after the conversion. [Axe conversions][axe] · [Stage map][weather]

Mine the matching placed block with an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** to recover **1 Weathered Chiseled Copper**. Wooden and Golden Pickaxes do not satisfy its drop requirement. See [mining and collection](../blocks/CopperConstruction.md#mining-and-collection) for the shared tool and explosion rules. [This variant’s loot][loot]

## Usage

Place it for a chiseled finish in [full-block construction](../blocks/CopperConstruction.md#full-blocks). The bundled recipes do not turn Chiseled Copper back into Cut Copper, slabs, plain full copper blocks, or ingots.

## Behavior

While placed and unwaxed, it can age into [Oxidized Chiseled Copper](OxidizedChiseledCopper.md). Use Honeycomb to preserve this stage as [Waxed Weathered Chiseled Copper](WaxedWeatheredChiseledCopper.md). [Aging callback][aging] · [Matching wax recipe][matching-wax-recipe]

Use the shared [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing), [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping), and [lightning-cleaning rules](../blocks/CopperConstruction.md#lightning-cleaning) when managing the finish.

## Notes

- This item is the item form of the `minecraft:weathered_chiseled_copper` block. [Item registration][item]
- Available in the Creative Menu. [Creative entry][creative]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Recipes and drops describe bundled data; data packs can change them. No in-game test was run.

[recipe-craft]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/weathered_chiseled_copper.json
[recipe-stone-full]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/weathered_chiseled_copper_from_weathered_copper_stonecutting.json
[recipe-stone-cut]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/weathered_chiseled_copper_from_weathered_cut_copper_stonecutting.json
[weather]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L17-L103
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L123
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/weathered_chiseled_copper.json
[aging]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopperFullBlock.java#L23-L36
[matching-wax-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_chiseled_copper_from_honeycomb.json
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L186
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L476
