# Exposed Chiseled Copper

Exposed Chiseled Copper is an **exposed, unwaxed decorative full block in the chiseled copper family**. See [Copper construction](../blocks/CopperConstruction.md#full-blocks) for its placed-block properties.

## Obtaining

Place **2 [Exposed Cut Copper Slab](ExposedCutCopperSlab.md) vertically → 1 Exposed Chiseled Copper**. This fits the inventory crafting grid. [Slab recipe][recipe-craft]

At a [Stonecutter](../blocks/Stonecutter.md), **1 [Exposed Copper](ExposedCopper.md) → 4 Exposed Chiseled Copper**, or **1 [Exposed Cut Copper](ExposedCutCopper.md) → 1 Exposed Chiseled Copper**. Choose the full copper input when making these blocks in bulk. [Full-block input][recipe-stone-full] · [Cut input][recipe-stone-cut]

Remove the wax from a placed [Waxed Exposed Chiseled Copper](WaxedExposedChiseledCopper.md) with an unbroken axe, or scrape a placed [Weathered Chiseled Copper](WeatheredChiseledCopper.md) back one stage. Aging a placed [Chiseled Copper](ChiseledCopper.md) also reaches this variant. Collect the resulting block after the conversion. [Axe conversions][axe] · [Stage map][weather]

Mine the matching placed block with an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** to recover **1 Exposed Chiseled Copper**. Wooden and Golden Pickaxes do not satisfy its drop requirement. See [mining and collection](../blocks/CopperConstruction.md#mining-and-collection) for the shared tool and explosion rules. [This variant’s loot][loot]

## Usage

Place it for a chiseled finish in [full-block construction](../blocks/CopperConstruction.md#full-blocks). The bundled recipes do not turn Chiseled Copper back into Cut Copper, slabs, plain full copper blocks, or ingots.

## Behavior

While placed and unwaxed, it can age into [Weathered Chiseled Copper](WeatheredChiseledCopper.md). Use Honeycomb to preserve this stage as [Waxed Exposed Chiseled Copper](WaxedExposedChiseledCopper.md). [Aging callback][aging] · [Matching wax recipe][matching-wax-recipe]

Use the shared [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing), [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping), and [lightning-cleaning rules](../blocks/CopperConstruction.md#lightning-cleaning) when managing the finish.

## Notes

- This item is the item form of the `minecraft:exposed_chiseled_copper` block. [Item registration][item]
- Available in the Creative Menu. [Creative entry][creative]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Recipes and drops describe bundled data; data packs can change them. No in-game test was run.

[recipe-craft]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/exposed_chiseled_copper.json
[recipe-stone-full]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/exposed_chiseled_copper_from_exposed_copper_stonecutting.json
[recipe-stone-cut]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/exposed_chiseled_copper_from_exposed_cut_copper_stonecutting.json
[weather]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L17-L103
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L123
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/exposed_chiseled_copper.json
[aging]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopperFullBlock.java#L23-L36
[matching-wax-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_chiseled_copper_from_honeycomb.json
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L185
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L465
