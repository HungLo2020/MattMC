# Farmland

Farmland (`minecraft:farmland`) is the inventory form of the [crop-supporting soil](../blocks/Farmland.md). **Tilling makes a placed field; it does not give you Farmland items.** [Item registration][item] · [Hoe conversion][hoe]

## Obtaining

For ordinary Survival farming, follow [making Farmland](../blocks/Farmland.md#making-farmland). Breaking the prepared soil returns **one Dirt**, even with Silk Touch; Fortune adds nothing. No bundled crafting recipe produces the Farmland item. The [block guide's mining section](../blocks/Farmland.md#mining-and-physical-behavior) owns its tools, drops and physical properties. [Loot][loot]

Farmland is listed in Natural Blocks and can be requested through the [inventory browser in Creative](../mechanics/InventoryBrowser.md). The listing is separate from Survival acquisition: ordinary Survival insertion requests are skipped, and Creative requests still have cursor, space and feature restrictions. [Category entry][category]

## Usage

A normal Farmland item places **dry Farmland, moisture 0**. If solid cover above fails the block's survival rule, placement instead selects **Dirt**; fence gates and moving pistons are the checked exceptions. Plan the field before using a supplied item. [Native default][catalog] · [Default state][templates] · [Placement fallback][farmland]

Plant a compatible crop above the soil and follow that crop's rules: [Wheat](../blocks/Wheat.md#planting-and-growing), [root crops](../blocks/RootCrops.md), or [Pumpkin and Melon](../blocks/PumpkinAndMelon.md). Watering soil does not supply the crop's required light.

## Behavior

Use the block guide for [hydration and a watered layout](../blocks/Farmland.md#hydration), [plants that preserve dry soil](../blocks/Farmland.md#hydration), and [cover and trampling protection](../blocks/Farmland.md#protecting-the-plot). Those rules apply to the placed soil regardless of whether it began as a supplied item or tilled Dirt.

## Notes

The item and placed block share `minecraft:farmland`. The dry-placement description covers a normal supplied item; custom block-state components can override placement properties. Recipe and loot statements describe bundled data, not every possible command or data pack. [Item registration][item] · [Item state application][placement]

## Sources and verification

Source-reviewed on **2026-10-09** at `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`, including item registration, native defaults, placement, the bundled loot table and a recipe search. No in-game acquisition, placement, mining or farming test was run. The linked block and inventory-browser guides own their detailed behavior.

[item]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/Items.java#L480
[hoe]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/HoeItem.java#L24-L38
[loot]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/loot_table/blocks/farmland.json
[category]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L760-L768
[catalog]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/catalog.rs#L234
[templates]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/templates.rs#L50
[farmland]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/FarmBlock.java#L65-L77
[placement]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L141
