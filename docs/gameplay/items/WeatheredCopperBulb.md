# Weathered Copper Bulb

Weathered Copper Bulb is a weathered copper lamp that remembers whether it is on. Its registered light level is **8 when lit and 0 when off**. [Properties][block]

## Obtaining

In a **Crafting Table**, put **1 Blaze Rod in the center**, **1 Redstone Dust below it**, and **3 Weathered Copper blocks above, left, and right** to make **4 Weathered Copper Bulbs**. The copper ingredient is exactly `minecraft:weathered_copper`; other stages, wax states, and Cut Copper do not substitute. [Recipe][recipe]

You can also let a placed [Exposed Copper Bulb](ExposedCopperBulb.md) oxidize to this stage, or remove the wax from a placed [Waxed Weathered Copper Bulb](WaxedWeatheredCopperBulb.md) with an unbroken axe, then collect the result. [Oxidation map][weather] · [Wax removal][axe]

For **1 Weathered Copper Bulb** from ordinary Survival mining, use an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe**. Hand mining, Wooden/Golden Pickaxes, and broken pickaxes fail the drop requirement. Fortune and Silk Touch do not add quantity or save the bulb's lit/powered state; explosions have a survival condition. See [collecting Bulbs](../blocks/CopperLighting.md#collecting-bulbs) for the shared tool and resistance rules. [Harvest dispatch][harvest] · [Exact loot][loot]

It is also listed in the [inventory browser for Creative acquisition](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item); visibility in Survival is not an item-grant route. [Category listing][listing]

## Usage

Place the item to make its matching Weathered Copper Bulb block. A fresh bulb starts off, but placement into an already-powered position turns it on. Later **new power pulses toggle** its light; removing power leaves the light unchanged. It has full-block collision, no facing or waterlogged state, and no continuing support requirement. [Item registration][item] · [Bulb state and controls][bulb]

A Comparator reads **15 when lit and 0 when off**, regardless of oxidation brightness; the bulb itself is not a redstone-conducting full block. Use the canonical [toggle and comparator guide](../blocks/CopperLighting.md#toggle-control-and-comparator-output) for circuit behavior, placement, and state reset after mining. [Analog output][bulb] · [Properties][block]

## Behavior

While placed and receiving random ticks, this unwaxed bulb can oxidize into [Oxidized Copper Bulb](OxidizedCopperBulb.md), reducing its lit brightness from **8 to 4**. Aging has no fixed completion time; follow the shared [oxidation and spacing rules](../blocks/CopperConstruction.md#oxidation-and-spacing). [Oxidation map][weather] An **unbroken axe** scrapes the placed block back one stage to [Exposed Copper Bulb](ExposedCopperBulb.md).

Use **1 Honeycomb** on the placed bulb, or craft it shapelessly with one Honeycomb, to make **1 [Waxed Weathered Copper Bulb](WaxedWeatheredCopperBulb.md)** and keep this stage. Waxing does not brighten it. See [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping) for shared controls and conversion details. [Placed waxing][wax] · [Axe conversion][axe]

## Notes

* This item is the item form of the `minecraft:weathered_copper_bulb` block.
* Compare all stages in [Copper lighting](../blocks/CopperLighting.md#crafting-and-brightness).

## Sources and verification

Source-reviewed on **2026-10-04** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Recipes, loot, registrations, tool gates, and active placement/power dispatch were checked. No in-game lighting, circuit, or timing test was run. Data packs can change recipes, tags, and loot.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2618-L2625
[block]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L6355-L6390
[bulb]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CopperBulbBlock.java#L28-L73
[weather]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L44-L103
[wax]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/HoneycombItem.java#L61-L114
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L59-L119
[listing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1051-L1058
[harvest]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L281-L292
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/weathered_copper_bulb.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/weathered_copper_bulb.json
