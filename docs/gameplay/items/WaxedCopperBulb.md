# Waxed Copper Bulb

Waxed Copper Bulb is a waxed unaffected copper lamp that remembers whether it is on. Its registered light level is **15 when lit and 0 when off**. [Properties][block]

## Obtaining

In a **Crafting Table**, put **1 Blaze Rod in the center**, **1 Redstone Dust below it**, and **3 Waxed Blocks of Copper above, left, and right** to make **4 Waxed Copper Bulbs**. The copper ingredient is exactly `minecraft:waxed_copper_block`; other stages, wax states, and Cut Copper do not substitute. [Recipe][recipe]

Alternatively, combine **1 [Copper Bulb](CopperBulb.md) + 1 Honeycomb → 1 Waxed Copper Bulb**, in any arrangement; this fits the inventory crafting grid. Using one Honeycomb on a placed Copper Bulb also waxes it, ready to collect. [Waxing recipe][wax-recipe] · [Placed conversion][wax]

For **1 Waxed Copper Bulb** from ordinary Survival mining, use an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe**. Hand mining, Wooden/Golden Pickaxes, and broken pickaxes fail the drop requirement. Fortune and Silk Touch do not add quantity or save the bulb's lit/powered state; explosions have a survival condition. See [collecting Bulbs](../blocks/CopperLighting.md#collecting-bulbs) for the shared tool and resistance rules. [Harvest dispatch][harvest] · [Exact loot][loot]

It is also listed in the [inventory browser for Creative acquisition](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item); visibility in Survival is not an item-grant route. [Category listing][listing]

## Usage

Place the item to make its matching Waxed Copper Bulb block. A fresh bulb starts off, but placement into an already-powered position turns it on. Later **new power pulses toggle** its light; removing power leaves the light unchanged. It has full-block collision, no facing or waterlogged state, and no continuing support requirement. [Item registration][item] · [Bulb state and controls][bulb]

A Comparator reads **15 when lit and 0 when off**, regardless of oxidation brightness; the bulb itself is not a redstone-conducting full block. Use the canonical [toggle and comparator guide](../blocks/CopperLighting.md#toggle-control-and-comparator-output) for circuit behavior, placement, and state reset after mining. [Analog output][bulb] · [Properties][block]

## Behavior

Wax keeps this bulb at the unaffected stage, retaining its **15** lit brightness. Use an **unbroken axe** on the placed block to remove its wax and return it to [Copper Bulb](CopperBulb.md); that use does not remove an oxidation stage or return Honeycomb. See [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping) for tool controls and state preservation. [Wax map][wax] · [Axe conversion][axe] The unwaxed form has no earlier oxidation stage to scrape.

## Notes

* This item is the item form of the `minecraft:waxed_copper_bulb` block.
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
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/waxed_copper_bulb.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_bulb.json
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_bulb_from_honeycomb.json
