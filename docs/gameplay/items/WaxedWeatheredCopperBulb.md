# Waxed Weathered Copper Bulb

Waxed Weathered Copper Bulb is a waxed weathered copper lamp that remembers whether it is on. Its registered light level is **8 when lit and 0 when off**. [Properties][block]

## Obtaining

In a **Crafting Table**, put **1 Blaze Rod in the center**, **1 Redstone Dust below it**, and **3 Waxed Weathered Copper blocks above, left, and right** to make **4 Waxed Weathered Copper Bulbs**. The copper ingredient is exactly `minecraft:waxed_weathered_copper`; other stages, wax states, and Cut Copper do not substitute. [Recipe][recipe]

Alternatively, combine **1 [Weathered Copper Bulb](WeatheredCopperBulb.md) + 1 Honeycomb → 1 Waxed Weathered Copper Bulb**, in any arrangement; this fits the inventory crafting grid. Using one Honeycomb on a placed Weathered Copper Bulb also waxes it, ready to collect. [Waxing recipe][wax-recipe] · [Placed conversion][wax]

For **1 Waxed Weathered Copper Bulb** from ordinary Survival mining, use an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe**. Hand mining, Wooden/Golden Pickaxes, and broken pickaxes fail the drop requirement. Fortune and Silk Touch do not add quantity or save the bulb's lit/powered state; explosions have a survival condition. See [collecting Bulbs](../blocks/CopperLighting.md#collecting-bulbs) for the shared tool and resistance rules. [Harvest dispatch][harvest] · [Exact loot][loot]

It is also listed in the [inventory browser for Creative acquisition](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item); visibility in Survival is not an item-grant route. [Category listing][listing]

## Usage

Place the item to make its matching Waxed Weathered Copper Bulb block. A fresh bulb starts off, but placement into an already-powered position turns it on. Later **new power pulses toggle** its light; removing power leaves the light unchanged. It has full-block collision, no facing or waterlogged state, and no continuing support requirement. [Item registration][item] · [Bulb state and controls][bulb]

A Comparator reads **15 when lit and 0 when off**, regardless of oxidation brightness; the bulb itself is not a redstone-conducting full block. Use the canonical [toggle and comparator guide](../blocks/CopperLighting.md#toggle-control-and-comparator-output) for circuit behavior, placement, and state reset after mining. [Analog output][bulb] · [Properties][block]

## Behavior

Wax keeps this bulb at the weathered stage, retaining its **8** lit brightness. Use an **unbroken axe** on the placed block to remove its wax and return it to [Weathered Copper Bulb](WeatheredCopperBulb.md); that use does not remove an oxidation stage or return Honeycomb. See [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping) for tool controls and state preservation. [Wax map][wax] · [Axe conversion][axe] After removing the wax, a separate axe use can scrape it back to [Exposed Copper Bulb](ExposedCopperBulb.md).

## Notes

* This item is the item form of the `minecraft:waxed_weathered_copper_bulb` block.
* Compare all stages in [Copper lighting](../blocks/CopperLighting.md#crafting-and-brightness).

## Sources and verification

Source-reviewed on **2026-10-04** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Recipes, loot, registrations, tool gates, and active placement/power dispatch were checked. No in-game lighting, circuit, or timing test was run. Data packs can change recipes, tags, and loot.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2618-L2625
[block]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L6355-L6390
[bulb]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CopperBulbBlock.java#L28-L73
[wax]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/HoneycombItem.java#L61-L114
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L59-L119
[listing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1051-L1058
[harvest]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L281-L292
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_copper_bulb.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_bulb.json
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_bulb_from_honeycomb.json
