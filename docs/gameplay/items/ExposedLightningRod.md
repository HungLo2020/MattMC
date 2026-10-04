# Exposed Lightning Rod

Exposed Lightning Rod is the first aged, unwaxed rod. It places an Exposed rod immediately, making it useful when you already have the finish you want and do not want to start a build with unaffected copper. [Block][block] · [Item][item]

## Obtaining

There is **no direct production recipe** for this unwaxed stage in the bundled recipe set. The checked placed-block routes are:

- Let an unwaxed [Lightning Rod](LightningRod.md) reach Exposed through eligible oxidation ticks
- Use an unbroken axe once on a placed [Weathered Lightning Rod](WeatheredLightningRod.md)
- Use an unbroken axe once on a placed [Waxed Exposed Lightning Rod](WaxedExposedLightningRod.md) to remove its wax

Collect the transformed block to obtain the item. Aging and axe use happen in the world, not in a crafting grid. Oxidation has no fixed completion time; see [copper aging and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing). [Recipe set][recipes] · [Stage map][weather] · [Weathering callback][aging] · [Wax map][wax] · [Axe controls](../mechanics/AxesAndHoes.md#scraping-copper-and-removing-wax)

Mine the placed block with an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe** to recover **1 Exposed Lightning Rod**. Wooden/Golden Pickaxes, broken pickaxes and bare hands do not satisfy the normal drop gate. Its loot adds no Fortune bonus or Silk Touch requirement; explosions can destroy the drop. [Exact loot][loot] · [Tool and collection rules](../blocks/LightningRods.md#crafting-and-collecting)

This item is category-listed for Creative access. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) can show it in Survival, but ordinary Survival insertion requests are skipped; catalog visibility does not supply crafting materials or replace these acquisition routes. [Category entry][creative]

## Usage

One successful Survival placement consumes **1 item** and creates the matching rod block. It points along the clicked face, supporting floor, wall and underside placement; placement into source Water makes it waterlogged. See [rod placement and water](../blocks/LightningRods.md#placement-water-and-redstone) for the shared details. [Item placement][place-item] · [Facing and water state][placement]

Keep the Exposed finish by crafting **1 Exposed Lightning Rod + 1 Honeycomb → 1 [Waxed Exposed Lightning Rod](WaxedExposedLightningRod.md)** in any arrangement. One Honeycomb can also wax a rod already placed at this stage. [Waxing recipe][wax-recipe] · [Placed waxing][wax]

## Behavior

While unwaxed and placed, this rod can advance to [Weathered](WeatheredLightningRod.md). One use of an unbroken axe instead restores [Lightning Rod](LightningRod.md), the unaffected stage. The shared [waxing and scraping guide](../blocks/CopperConstruction.md#waxing-and-scraping) covers durability, state preservation and wax removal. [Stage map][weather]

Exposed copper still functions as a lightning target and redstone pulse source. A rod-directed strike can clean the unwaxed rod back to Unaffected, so wax it if keeping this finish matters. The canonical [Lightning Rods guide](../blocks/LightningRods.md#oxidation-and-lightning-cleaning) explains that cleaning and the shared lightning/redstone behavior. [Unwaxed registration][block] · [Strike cleaning][cleaning]

## Notes

- This item is the item form of the `minecraft:exposed_lightning_rod` block. [Registration][item]
- The aging, waxing and axe conversions above act on placed blocks unless a crafting recipe is explicitly named. Normal block loot carries the recovered variant, not the rod's active redstone pulse. [Exact loot][loot] · [Placed state](../blocks/LightningRods.md#placement-water-and-redstone)
- Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on **2026-10-04**. No in-game test was run. Recipes, loot, tags and later code changes can alter these results

Related: [All Lightning Rod variants](../blocks/LightningRods.md#exact-variants-recipes-and-loot) · [Copper finish controls](../blocks/CopperConstruction.md#waxing-and-scraping) · [Items](Items.md)

[aging]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringLightningRodBlock.java#L28-L39
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6496-L6500
[cleaning]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LightningBolt.java#L173-L210
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1106
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1021
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/exposed_lightning_rod.json
[place-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L48-L83
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LightningRodBlock.java#L39-L49
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[wax]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/HoneycombItem.java#L72-L113
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_lightning_rod_from_honeycomb.json
[weather]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L55-L96
