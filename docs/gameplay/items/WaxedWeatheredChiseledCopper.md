# Waxed Weathered Chiseled Copper

Waxed Weathered Chiseled Copper is a **weathered, waxed decorative full block in the chiseled copper family**. See [Copper construction](../blocks/CopperConstruction.md#full-blocks) for its placed-block properties.

## Obtaining

Place **2 [Waxed Weathered Cut Copper Slab](WaxedWeatheredCutCopperSlab.md) vertically → 1 Waxed Weathered Chiseled Copper**. This fits the inventory crafting grid. [Slab recipe][recipe-craft]

At a [Stonecutter](../blocks/Stonecutter.md), **1 [Waxed Weathered Copper](WaxedWeatheredCopper.md) → 4 Waxed Weathered Chiseled Copper**, or **1 [Waxed Weathered Cut Copper](WaxedWeatheredCutCopper.md) → 1 Waxed Weathered Chiseled Copper**. Choose the full copper input when making these blocks in bulk. [Full-block input][recipe-stone-full] · [Cut input][recipe-stone-cut]

Combine **1 [Weathered Chiseled Copper](WeatheredChiseledCopper.md) + 1 [Honeycomb](Honeycomb.md) → 1 Waxed Weathered Chiseled Copper** in any crafting-grid positions. You can also apply one Honeycomb to the matching placed unwaxed block and then collect it. [Waxing recipe][recipe-wax] · [Placed waxing][wax]

Mine the matching placed block with an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** to recover **1 Waxed Weathered Chiseled Copper**. Wooden and Golden Pickaxes do not satisfy its drop requirement. See [mining and collection](../blocks/CopperConstruction.md#mining-and-collection) for the shared tool and explosion rules. [This variant’s loot][loot]

## Usage

Place it for a chiseled finish in [full-block construction](../blocks/CopperConstruction.md#full-blocks). The bundled recipes do not turn Chiseled Copper back into Cut Copper, slabs, plain full copper blocks, or ingots.

## Behavior

Wax keeps this finish from aging. One use of an unbroken axe removes the wax and leaves [Weathered Chiseled Copper](WeatheredChiseledCopper.md), **without changing its oxidation stage**. [Wax/axe conversion][axe]

Use the shared [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing), [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping), and [lightning-cleaning rules](../blocks/CopperConstruction.md#lightning-cleaning) when managing the finish.

## Notes

- This item is the item form of the `minecraft:waxed_weathered_chiseled_copper` block. [Item registration][item]
- Available in the Creative Menu. [Creative entry][creative]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Recipes and drops describe bundled data; data packs can change them. No in-game test was run.

[recipe-craft]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_chiseled_copper.json
[recipe-stone-full]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_weathered_chiseled_copper_from_waxed_weathered_copper_stonecutting.json
[recipe-stone-cut]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/stonecutting/waxed_weathered_chiseled_copper_from_waxed_weathered_cut_copper_stonecutting.json
[recipe-wax]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_chiseled_copper_from_honeycomb.json
[wax]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/HoneycombItem.java#L24-L113
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_chiseled_copper.json
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L123
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L206
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L520
