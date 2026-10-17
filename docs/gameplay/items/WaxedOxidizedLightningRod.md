# Waxed Oxidized Lightning Rod

Waxed Oxidized Lightning Rod preserves the final copper finish. The unwaxed Oxidized stage already stops aging, but waxing also protects it from the copper-cleaning path that can remove an unwaxed rod's finish. [Block][block] · [Item][item] · [Lightning cleaning](../blocks/LightningRods.md#oxidation-and-lightning-cleaning)

## Obtaining

Craft **1 [Oxidized Lightning Rod](OxidizedLightningRod.md) + 1 Honeycomb → 1 Waxed Oxidized Lightning Rod**, shapelessly in either crafting grid. The listed input must already be the unwaxed Oxidized rod. [Recipe][recipe]

You can also use one Honeycomb on a placed, unwaxed Oxidized rod and collect the resulting waxed block. This consumes one Honeycomb in a world interaction rather than creating a new inventory result directly. Use the [Oxidized rod's acquisition guide](OxidizedLightningRod.md#obtaining) to prepare the last-stage input; earlier waxed rods do not age into this item. [Placed waxing][wax] · [Waxed registration][block]

Mine the placed block with an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe** to recover **1 Waxed Oxidized Lightning Rod**. Wooden/Golden Pickaxes, broken pickaxes and bare hands do not satisfy the normal drop gate. Its loot adds no Fortune bonus or Silk Touch requirement; explosions can destroy the drop. [Exact loot][loot] · [Tool and collection rules](../blocks/LightningRods.md#crafting-and-collecting)

This item is category-listed for Creative access. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) can show it in Survival, but ordinary Survival insertion requests are skipped; catalog visibility does not supply crafting materials or replace these acquisition routes. [Category entry][creative]

## Usage

One successful Survival placement consumes **1 item** and creates the matching rod block. It points along the clicked face, supporting floor, wall and underside placement; placement into source Water makes it waterlogged. See [rod placement and water](../blocks/LightningRods.md#placement-water-and-redstone) for the shared details. [Item placement][place-item] · [Facing and water state][placement]

Use this rod when a finished build needs the fully oxidized appearance and the rod's normal lightning/redstone function. It remains at this finish while waxed. [Waxed registration][block]

## Behavior

An **unbroken axe** first removes the wax, leaving an [Oxidized Lightning Rod](OxidizedLightningRod.md) block. A further successful use scrapes it back to [Weathered](WeatheredLightningRod.md); removing wax alone does not lower the oxidation stage or refund Honeycomb. The shared [waxing and scraping guide](../blocks/CopperConstruction.md#waxing-and-scraping) covers durability and controls. [Wax inverse][wax] · [Axe conversion][axe] · [Broken-tool guard][use-guard] · [Stage map][weather]

This waxed variant still receives the lightning-triggered redstone pulse and does not start copper cleaning. The canonical [Lightning Rods guide](../blocks/LightningRods.md) owns targeting, pulse timing, placement details and the [waxed/unwaxed cleaning distinction](../blocks/LightningRods.md#oxidation-and-lightning-cleaning). [Waxed registration][block] · [Strike cleaning][cleaning]

## Notes

- This item is the item form of the `minecraft:waxed_oxidized_lightning_rod` block. [Registration][item]
- The aging, waxing and axe conversions above act on placed blocks unless a crafting recipe is explicitly named. Normal block loot carries the recovered variant, not the rod's active redstone pulse. [Exact loot][loot] · [Placed state](../blocks/LightningRods.md#placement-water-and-redstone)
- Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on **2026-10-04**. No in-game test was run. Recipes, loot, tags and later code changes can alter these results

Related: [All Lightning Rod variants](../blocks/LightningRods.md#exact-variants-recipes-and-loot) · [Copper finish controls](../blocks/CopperConstruction.md#waxing-and-scraping) · [Items](Items.md)

[axe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L62-L117
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6518-L6520
[cleaning]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LightningBolt.java#L173-L210
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1112
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1027
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_lightning_rod.json
[place-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L48-L83
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LightningRodBlock.java#L39-L49
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_lightning_rod_from_honeycomb.json
[use-guard]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L372
[wax]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/HoneycombItem.java#L72-L113
[weather]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L55-L96
