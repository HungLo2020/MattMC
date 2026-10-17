# Lightning Rod

Lightning Rod is the unaffected, unwaxed starting form of the eight-variant rod family. Craft it for a lightning target or a short redstone pulse source, then choose whether to let its placed copper finish age or preserve it with wax. [Block][block] · [Item][item]

## Obtaining

At a [Crafting Table](../blocks/CraftingTable.md), put **3 [Copper Ingots](CopperIngot.md) in a vertical column → 1 Lightning Rod**. The three-high layout does not fit the inventory's 2 × 2 grid. [Recipe][recipe]

You can also restore a placed [Exposed Lightning Rod](ExposedLightningRod.md) with one use of an unbroken axe, or remove the wax from a placed [Waxed Lightning Rod](WaxedLightningRod.md). Mine the resulting unaffected rod to carry it. These axe operations change the block in the world; they are not crafting recipes. [Stage map][weather] · [Wax map][wax] · [Axe controls](../mechanics/AxesAndHoes.md#scraping-copper-and-removing-wax)

Mine the placed block with an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe** to recover **1 Lightning Rod**. Wooden/Golden Pickaxes, broken pickaxes and bare hands do not satisfy the normal drop gate. Its loot adds no Fortune bonus or Silk Touch requirement; explosions can destroy the drop. [Exact loot][loot] · [Tool and collection rules](../blocks/LightningRods.md#crafting-and-collecting)

This item is category-listed for Creative access. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) can show it in Survival, but ordinary Survival insertion requests are skipped; catalog visibility does not supply crafting materials or replace these acquisition routes. [Category entry][creative]

## Usage

One successful Survival placement consumes **1 item** and creates the matching rod block. It points along the clicked face, supporting floor, wall and underside placement; placement into source Water makes it waterlogged. See [rod placement and water](../blocks/LightningRods.md#placement-water-and-redstone) for the shared details. [Item placement][place-item] · [Facing and water state][placement]

To preserve this initial finish, combine **1 Lightning Rod + 1 Honeycomb → 1 [Waxed Lightning Rod](WaxedLightningRod.md)**, shapelessly. You can instead use one Honeycomb on the placed rod and then collect that waxed block. [Waxing recipe][wax-recipe] · [Placed waxing][wax]

## Behavior

An unwaxed placed Lightning Rod can advance to [Exposed](ExposedLightningRod.md), then Weathered and Oxidized. Aging depends on eligible random block ticks and nearby copper; there is no fixed wait or immediate result after placement. Follow the shared [oxidation and spacing guide](../blocks/CopperConstruction.md#oxidation-and-spacing). This unaffected stage has no earlier oxidation stage for an axe to scrape. [Weathering callback][aging] · [Stage map][weather]

The [Lightning Rods guide](../blocks/LightningRods.md) owns natural-lightning targeting, redstone timing and copper cleaning. Use its [exposed-column placement guidance](../blocks/LightningRods.md#attracting-natural-lightning) when choosing a position; holding the item is not that placed-block setup.

## Notes

- This item is the item form of the `minecraft:lightning_rod` block. [Registration][item]
- The aging, waxing and axe conversions above act on placed blocks unless a crafting recipe is explicitly named. Normal block loot carries the recovered variant, not the rod's active redstone pulse. [Exact loot][loot] · [Placed state](../blocks/LightningRods.md#placement-water-and-redstone)
- Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on **2026-10-04**. No in-game test was run. Recipes, loot, tags and later code changes can alter these results

Related: [All Lightning Rod variants](../blocks/LightningRods.md#exact-variants-recipes-and-loot) · [Copper finish controls](../blocks/CopperConstruction.md#waxing-and-scraping) · [Items](Items.md)

[aging]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringLightningRodBlock.java#L28-L39
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6485-L6495
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1105
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1020
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/lightning_rod.json
[place-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L48-L83
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LightningRodBlock.java#L39-L49
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/lightning_rod.json
[wax]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/HoneycombItem.java#L72-L113
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/waxed_lightning_rod_from_honeycomb.json
[weather]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L55-L96
