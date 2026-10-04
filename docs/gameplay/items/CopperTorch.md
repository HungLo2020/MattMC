# Copper Torch

Copper Torch is a steady light with floor and wall forms. Both have registered **light level 14** and use copper-flame particles. [Properties][block]

## Obtaining

In a **Crafting Table**, arrange **1 [Copper Nugget](CopperNugget.md) above 1 Coal or Charcoal above 1 Stick** in one vertical column to make **4 Copper Torches**. This three-tall recipe does not fit the inventory crafting grid. [Recipe][recipe]

Breaking either a standing or wall-mounted Copper Torch ordinarily returns **1 Copper Torch**, including when its support is removed. No tool or tool tier is required, and Fortune and Silk Touch add no extra yield. The wall form reuses the standing form's loot table, which has an explosion-survival condition. Follow [Copper Torch collection and support](../blocks/CopperLighting.md#copper-torches) for the shared rules. [Exact loot][loot] · [Wall loot inheritance][wall-loot]

It is also listed in the [inventory browser for Creative acquisition](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item); visibility in Survival is not an item-grant route. [Category listing][listing]

## Usage

Use the same item to place `minecraft:copper_torch` on a floor or `minecraft:copper_wall_torch` on a wall; **there is no separate Wall Torch item**. The standing form needs center support on the upper face below, and the wall form needs a sturdy side face behind it. Losing that support breaks the torch. Neither form can hang from a ceiling or hold water. [Item registration][item] · [Placement routing][standing-wall] · [Floor support][floor] · [Wall support][wall]

For a **[Copper Lantern](CopperFixtures.md#copper-lantern)**, surround **1 Copper Torch with 8 Copper Nuggets** in a Crafting Table to make **1 Lantern**. The Lantern has its own [floor/ceiling placement and support rules](../blocks/CopperBarsChainsAndLanterns.md#copper-lantern-floor-or-ceiling-light). [Lantern recipe][lantern]

## Behavior

Copper Torches break instantly and have no collision. Their steady light needs no redstone input or ongoing fuel. They have no oxidation chain, waxed form, or bulb-style lit/powered toggle. For their placed-block details, see [Copper Torches](../blocks/CopperLighting.md#copper-torches); use the separate [Redstone Torch](../blocks/RedstoneTorch.md) for redstone signaling. [Properties][block]

## Notes

* This item is the item form of the `minecraft:copper_torch` block and also places its wall form.
* [Copper Bulbs](CopperBulb.md) use power pulses to toggle their light; a Copper Torch does not share that behavior.

## Sources and verification

Source-reviewed on **2026-10-04** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Recipe, loot inheritance, item routing, support-removal dispatch, and light properties were checked. No in-game placement or lighting test was run. Data packs can change recipes and loot.

[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/copper_torch.json
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/copper_torch.json
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L526-L529
[standing-wall]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java#L13-L52
[block]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L2043-L2051
[floor]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BaseTorchBlock.java#L30-L48
[wall]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WallTorchBlock.java#L54-L106
[wall-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[lantern]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/copper_lantern.json
[listing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1041
