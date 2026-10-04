# Waxed Weathered Lightning Rod

Waxed Weathered Lightning Rod holds the Weathered copper finish in place. This is a separately registered inventory item, so start with the matching Weathered rod rather than another waxed or unwaxed stage. [Block][block] · [Item][item]

## Obtaining

Combine **1 [Weathered Lightning Rod](WeatheredLightningRod.md) + 1 Honeycomb → 1 Waxed Weathered Lightning Rod** in any arrangement. The two-ingredient recipe works in the inventory crafting grid. [Recipe][recipe]

For a rod already built into a structure, use one Honeycomb on its unwaxed Weathered block, then mine it if you need the item. That world interaction consumes one Honeycomb and preserves the stage. The [Weathered rod entry](WeatheredLightningRod.md#obtaining) explains how to prepare the input through aging, scraping or wax removal; this waxed rod does not form by aging a Waxed Exposed rod. [Placed waxing][wax] · [Waxed registration][block]

Mine the placed block with an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe** to recover **1 Waxed Weathered Lightning Rod**. Wooden/Golden Pickaxes, broken pickaxes and bare hands do not satisfy the normal drop gate. Its loot adds no Fortune bonus or Silk Touch requirement; explosions can destroy the drop. [Exact loot][loot] · [Tool and collection rules](../blocks/LightningRods.md#crafting-and-collecting)

This item is category-listed for Creative access. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) can show it in Survival, but ordinary Survival insertion requests are skipped; catalog visibility does not supply crafting materials or replace these acquisition routes. [Category entry][creative]

## Usage

One successful Survival placement consumes **1 item** and creates the matching rod block. It points along the clicked face, supporting floor, wall and underside placement; placement into source Water makes it waterlogged. See [rod placement and water](../blocks/LightningRods.md#placement-water-and-redstone) for the shared details. [Item placement][place-item] · [Facing and water state][placement]

Place it where you want a Weathered rod that will keep its oxidation stage. Waxed rods use the shared rod placement, waterlogging and redstone behavior without the unwaxed weathering callback. [Waxed registration][block]

## Behavior

One use of an **unbroken axe** removes the wax and leaves a [Weathered Lightning Rod](WeatheredLightningRod.md) block. It does not remove an oxidation stage in that same operation. Another successful use scrapes the unwaxed block to [Exposed](ExposedLightningRod.md). Wax removal returns no Honeycomb; follow [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping) for durability and controls. [Wax inverse][wax] · [Axe conversion][axe] · [Broken-tool guard][use-guard] · [Stage map][weather]

Wax does not disable the lightning target or its redstone pulse. It also keeps this rod out of the copper-cleaning entry path. See the canonical [Lightning Rods guide](../blocks/LightningRods.md#oxidation-and-lightning-cleaning) for shared lightning, redstone and cleaning rules. [Waxed registration][block] · [Strike cleaning][cleaning]

## Notes

- This item is the item form of the `minecraft:waxed_weathered_lightning_rod` block. [Registration][item]
- The aging, waxing and axe conversions above act on placed blocks unless a crafting recipe is explicitly named. Normal block loot carries the recovered variant, not the rod's active redstone pulse. [Exact loot][loot] · [Placed state](../blocks/LightningRods.md#placement-water-and-redstone)
- Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on **2026-10-04**. No in-game test was run. Recipes, loot, tags and later code changes can alter these results

Related: [All Lightning Rod variants](../blocks/LightningRods.md#exact-variants-recipes-and-loot) · [Copper finish controls](../blocks/CopperConstruction.md#waxing-and-scraping) · [Items](Items.md)

[axe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L62-L117
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6515-L6517
[cleaning]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LightningBolt.java#L173-L210
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1111
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1026
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_lightning_rod.json
[place-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L48-L83
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LightningRodBlock.java#L39-L49
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_lightning_rod_from_honeycomb.json
[use-guard]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L372
[wax]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/HoneycombItem.java#L72-L113
[weather]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L55-L96
