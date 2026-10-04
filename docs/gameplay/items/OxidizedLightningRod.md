# Oxidized Lightning Rod

Oxidized Lightning Rod is the final copper oxidation stage in the unwaxed rod family. It will not age to a fifth stage, but its finish remains removable by an axe or eligible lightning cleaning. [Block][block] · [Item][item] · [Stage map][weather]

## Obtaining

There is **no direct production recipe** for this unwaxed rod in the bundled recipe set. Let a placed, unwaxed [Weathered Lightning Rod](WeatheredLightningRod.md) reach Oxidized, or remove the wax from a placed [Waxed Oxidized Lightning Rod](WaxedOxidizedLightningRod.md) with one use of an unbroken axe. Mine the transformed block to obtain the item. [Recipe set][recipes] · [Stage map][weather] · [Weathering callback][aging] · [Wax map][wax]

Starting with a fresh [Lightning Rod](LightningRod.md) requires the Exposed and Weathered stages first. Random-tick aging is not a crafting process and has no guaranteed completion time; follow the [oxidation and spacing guide](../blocks/CopperConstruction.md#oxidation-and-spacing) when preparing a batch.

Mine the placed block with an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe** to recover **1 Oxidized Lightning Rod**. Wooden/Golden Pickaxes, broken pickaxes and bare hands do not satisfy the normal drop gate. Its loot adds no Fortune bonus or Silk Touch requirement; explosions can destroy the drop. [Exact loot][loot] · [Tool and collection rules](../blocks/LightningRods.md#crafting-and-collecting)

This item is category-listed for Creative access. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) can show it in Survival, but ordinary Survival insertion requests are skipped; catalog visibility does not supply crafting materials or replace these acquisition routes. [Category entry][creative]

## Usage

One successful Survival placement consumes **1 item** and creates the matching rod block. It points along the clicked face, supporting floor, wall and underside placement; placement into source Water makes it waterlogged. See [rod placement and water](../blocks/LightningRods.md#placement-water-and-redstone) for the shared details. [Item placement][place-item] · [Facing and water state][placement]

Craft **1 Oxidized Lightning Rod + 1 Honeycomb → 1 [Waxed Oxidized Lightning Rod](WaxedOxidizedLightningRod.md)** in any arrangement, or use one Honeycomb on the placed Oxidized rod. Wax still has a purpose at the last stage: it protects the finish from the lightning-cleaning path described in [Lightning Rods](../blocks/LightningRods.md#oxidation-and-lightning-cleaning). [Waxing recipe][wax-recipe] · [Placed waxing][wax]

## Behavior

This stage has no next oxidation state. One successful use of an unbroken axe on the placed rod restores [Weathered Lightning Rod](WeatheredLightningRod.md); further uses step back through Exposed to Unaffected. The shared [scraping guide](../blocks/CopperConstruction.md#waxing-and-scraping) explains durability and controls. [Stage map][weather]

Fully oxidized rods still attract eligible natural lightning and emit their redstone pulse when struck. Their unwaxed finish can be cleaned by lightning. The canonical [Lightning Rods guide](../blocks/LightningRods.md) owns the targeting conditions, pulse timing and cleaning rules. [Unwaxed registration][block] · [Strike cleaning][cleaning]

## Notes

- This item is the item form of the `minecraft:oxidized_lightning_rod` block. [Registration][item]
- The aging, waxing and axe conversions above act on placed blocks unless a crafting recipe is explicitly named. Normal block loot carries the recovered variant, not the rod's active redstone pulse. [Exact loot][loot] · [Placed state](../blocks/LightningRods.md#placement-water-and-redstone)
- Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on **2026-10-04**. No in-game test was run. Recipes, loot, tags and later code changes can alter these results

Related: [All Lightning Rod variants](../blocks/LightningRods.md#exact-variants-recipes-and-loot) · [Copper finish controls](../blocks/CopperConstruction.md#waxing-and-scraping) · [Items](Items.md)

[aging]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringLightningRodBlock.java#L28-L39
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6506-L6510
[cleaning]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LightningBolt.java#L173-L210
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1108
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1023
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/oxidized_lightning_rod.json
[place-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L48-L83
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LightningRodBlock.java#L39-L49
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[wax]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/HoneycombItem.java#L72-L113
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_lightning_rod_from_honeycomb.json
[weather]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L55-L96
