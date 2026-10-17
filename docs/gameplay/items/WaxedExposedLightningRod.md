# Waxed Exposed Lightning Rod

Waxed Exposed Lightning Rod keeps the first aged copper finish after placement. Obtain the Exposed stage before waxing; waxing an unaffected rod does not create this variant. [Block][block] · [Item][item] · [Wax map][wax]

## Obtaining

Craft **1 [Exposed Lightning Rod](ExposedLightningRod.md) + 1 Honeycomb → 1 Waxed Exposed Lightning Rod**. The recipe is shapeless and fits the inventory grid; an unaffected or differently aged rod is not its listed input. [Recipe][recipe]

You can instead apply one Honeycomb to a placed, unwaxed Exposed rod and mine the result. Waxing is a placed-block conversion that consumes the Honeycomb, separate from the inventory recipe. To prepare the unwaxed input, use the [Exposed rod's aging, scraping or unwaxing routes](ExposedLightningRod.md#obtaining). [Placed waxing][wax]

Mine the placed block with an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe** to recover **1 Waxed Exposed Lightning Rod**. Wooden/Golden Pickaxes, broken pickaxes and bare hands do not satisfy the normal drop gate. Its loot adds no Fortune bonus or Silk Touch requirement; explosions can destroy the drop. [Exact loot][loot] · [Tool and collection rules](../blocks/LightningRods.md#crafting-and-collecting)

This item is category-listed for Creative access. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) can show it in Survival, but ordinary Survival insertion requests are skipped; catalog visibility does not supply crafting materials or replace these acquisition routes. [Category entry][creative]

## Usage

One successful Survival placement consumes **1 item** and creates the matching rod block. It points along the clicked face, supporting floor, wall and underside placement; placement into source Water makes it waterlogged. See [rod placement and water](../blocks/LightningRods.md#placement-water-and-redstone) for the shared details. [Item placement][place-item] · [Facing and water state][placement]

Choose this rod to preserve an Exposed finish without giving up the shared lightning and redstone role. It stays Exposed while waxed; there is no natural aging route from this placed waxed variant to Waxed Weathered. [Waxed registration][block]

## Behavior

Use an **unbroken axe once** on the placed rod to remove its wax and obtain the unwaxed [Exposed Lightning Rod](ExposedLightningRod.md) block. A second successful use then scrapes that block to the unaffected [Lightning Rod](LightningRod.md). Removing wax does not refund Honeycomb. See [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping) for durability and controls. [Wax inverse][wax] · [Axe conversion][axe] · [Broken-tool guard][use-guard] · [Stage map][weather]

The waxed rod still responds to lightning with a redstone pulse but does not start the copper-cleaning routine. The canonical [Lightning Rods guide](../blocks/LightningRods.md#oxidation-and-lightning-cleaning) owns these shared behaviors and targeting conditions. [Waxed registration][block] · [Strike cleaning][cleaning]

## Notes

- This item is the item form of the `minecraft:waxed_exposed_lightning_rod` block. [Registration][item]
- The aging, waxing and axe conversions above act on placed blocks unless a crafting recipe is explicitly named. Normal block loot carries the recovered variant, not the rod's active redstone pulse. [Exact loot][loot] · [Placed state](../blocks/LightningRods.md#placement-water-and-redstone)
- Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on **2026-10-04**. No in-game test was run. Recipes, loot, tags and later code changes can alter these results

Related: [All Lightning Rod variants](../blocks/LightningRods.md#exact-variants-recipes-and-loot) · [Copper finish controls](../blocks/CopperConstruction.md#waxing-and-scraping) · [Items](Items.md)

[axe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L62-L117
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6512-L6514
[cleaning]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LightningBolt.java#L173-L210
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1110
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1025
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_lightning_rod.json
[place-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L48-L83
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LightningRodBlock.java#L39-L49
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_lightning_rod_from_honeycomb.json
[use-guard]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L372
[wax]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/HoneycombItem.java#L72-L113
[weather]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L55-L96
