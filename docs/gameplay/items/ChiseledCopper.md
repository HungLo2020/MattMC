# Chiseled Copper

Chiseled Copper is an **unaffected, unwaxed decorative full block in the chiseled copper family**. See [Copper construction](../blocks/CopperConstruction.md#full-blocks) for its placed-block properties.

## Obtaining

Place **2 [Cut Copper Slab](CutCopperSlab.md) vertically → 1 Chiseled Copper**. This fits the inventory crafting grid. [Slab recipe][recipe-craft]

At a [Stonecutter](../blocks/Stonecutter.md), **1 [Block of Copper](BlockOfCopper.md) → 4 Chiseled Copper**, or **1 [Cut Copper](CutCopper.md) → 1 Chiseled Copper**. Choose the full copper input when making these blocks in bulk. [Full-block input][recipe-stone-full] · [Cut input][recipe-stone-cut]

Remove the wax from a placed [Waxed Chiseled Copper](WaxedChiseledCopper.md) with an unbroken axe, or scrape a placed [Exposed Chiseled Copper](ExposedChiseledCopper.md) back one stage. Collect the resulting block after the conversion. [Axe conversions][axe]

Mine the matching placed block with an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** to recover **1 Chiseled Copper**. Wooden and Golden Pickaxes do not satisfy its drop requirement. See [mining and collection](../blocks/CopperConstruction.md#mining-and-collection) for the shared tool and explosion rules. [This variant’s loot][loot]

## Usage

Place it for a chiseled finish in [full-block construction](../blocks/CopperConstruction.md#full-blocks). The bundled recipes do not turn Chiseled Copper back into Cut Copper, slabs, plain full copper blocks, or ingots.

## Behavior

While placed and unwaxed, it can age into [Exposed Chiseled Copper](ExposedChiseledCopper.md). Use Honeycomb to preserve this stage as [Waxed Chiseled Copper](WaxedChiseledCopper.md). [Aging callback][aging] · [Matching wax recipe][matching-wax-recipe]

Use the shared [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing), [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping), and [lightning-cleaning rules](../blocks/CopperConstruction.md#lightning-cleaning) when managing the finish.

## Notes

- This item is the item form of the `minecraft:chiseled_copper` block. [Item registration][item]
- Available through the [combined inventory item browser](../mechanics/InventoryBrowser.md) in Creative. [Creative entry][creative] · [Current catalog entry][catalog-entry] · [Combined browser][catalog-browser]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Recipes and drops describe bundled data; data packs can change them. No in-game test was run.

[recipe-craft]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/chiseled_copper.json
[recipe-stone-full]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_copper_from_copper_block_stonecutting.json
[recipe-stone-cut]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_copper_from_cut_copper_stonecutting.json
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L123
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/chiseled_copper.json
[aging]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopperFullBlock.java#L23-L36
[matching-wax-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_chiseled_copper_from_honeycomb.json
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L184
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L454

[catalog-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L454
[catalog-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
