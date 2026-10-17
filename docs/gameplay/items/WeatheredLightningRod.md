# Weathered Lightning Rod

Weathered Lightning Rod carries the third, unwaxed copper stage into a build. It is a separate block item from both Exposed and fully Oxidized rods, and its placed finish can still change. [Block][block] · [Item][item]

## Obtaining

The bundled recipes contain **no direct production recipe** for Weathered Lightning Rod. To obtain it through placed blocks:

- Let an unwaxed [Exposed Lightning Rod](ExposedLightningRod.md) reach Weathered; a fresh [Lightning Rod](LightningRod.md) passes through Exposed first
- Use an unbroken axe once on a placed [Oxidized Lightning Rod](OxidizedLightningRod.md)
- Remove the wax from a placed [Waxed Weathered Lightning Rod](WaxedWeatheredLightningRod.md) with one use of an unbroken axe

Then mine the resulting Weathered rod. These are world transformations, not grid recipes. Aging requires eligible random ticks and depends on nearby copper; [the shared oxidation guide](../blocks/CopperConstruction.md#oxidation-and-spacing) does not promise a completion time. [Recipe set][recipes] · [Stage map][weather] · [Weathering callback][aging] · [Wax map][wax] · [Axe controls](../mechanics/AxesAndHoes.md#scraping-copper-and-removing-wax)

Mine the placed block with an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe** to recover **1 Weathered Lightning Rod**. Wooden/Golden Pickaxes, broken pickaxes and bare hands do not satisfy the normal drop gate. Its loot adds no Fortune bonus or Silk Touch requirement; explosions can destroy the drop. [Exact loot][loot] · [Tool and collection rules](../blocks/LightningRods.md#crafting-and-collecting)

This item is category-listed for Creative access. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) can show it in Survival, but ordinary Survival insertion requests are skipped; catalog visibility does not supply crafting materials or replace these acquisition routes. [Category entry][creative]

## Usage

One successful Survival placement consumes **1 item** and creates the matching rod block. It points along the clicked face, supporting floor, wall and underside placement; placement into source Water makes it waterlogged. See [rod placement and water](../blocks/LightningRods.md#placement-water-and-redstone) for the shared details. [Item placement][place-item] · [Facing and water state][placement]

For a finish that stays Weathered, craft **1 Weathered Lightning Rod + 1 Honeycomb → 1 [Waxed Weathered Lightning Rod](WaxedWeatheredLightningRod.md)**, shapelessly. Applying one Honeycomb to an already-placed Weathered rod is a separate route to the same waxed block. [Waxing recipe][wax-recipe] · [Placed waxing][wax]

## Behavior

An unwaxed placed Weathered rod can advance to [Oxidized](OxidizedLightningRod.md). An unbroken axe moves it back to [Exposed](ExposedLightningRod.md) by one stage per successful use. See [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping) for the shared controls and durability cost. [Stage map][weather]

Weathering does not disable the rod's lightning or redstone function. Lightning cleaning can remove this unwaxed finish; use the canonical [Lightning Rods guide](../blocks/LightningRods.md#oxidation-and-lightning-cleaning) for cleaning, targeting and pulse behavior. [Unwaxed registration][block] · [Strike cleaning][cleaning]

## Notes

- This item is the item form of the `minecraft:weathered_lightning_rod` block. [Registration][item]
- The aging, waxing and axe conversions above act on placed blocks unless a crafting recipe is explicitly named. Normal block loot carries the recovered variant, not the rod's active redstone pulse. [Exact loot][loot] · [Placed state](../blocks/LightningRods.md#placement-water-and-redstone)
- Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on **2026-10-04**. No in-game test was run. Recipes, loot, tags and later code changes can alter these results

Related: [All Lightning Rod variants](../blocks/LightningRods.md#exact-variants-recipes-and-loot) · [Copper finish controls](../blocks/CopperConstruction.md#waxing-and-scraping) · [Items](Items.md)

[aging]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringLightningRodBlock.java#L28-L39
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6501-L6505
[cleaning]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LightningBolt.java#L173-L210
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1107
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1022
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/weathered_lightning_rod.json
[place-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L48-L83
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LightningRodBlock.java#L39-L49
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[wax]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/HoneycombItem.java#L72-L113
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_lightning_rod_from_honeycomb.json
[weather]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L55-L96
