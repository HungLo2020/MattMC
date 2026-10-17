# Smithing Template (Coast Armor Trim)

Use this template to choose the **Coast pattern** when decorating armor at a [Smithing Table](../blocks/SmithingTable.md). The addition material chooses the trim material; it does not change which pattern this template supplies.

## Obtaining

Search [Shipwreck](../structures/Shipwreck.md) map, supply, and treasure chests. Each of those chest tables has a **1/6 chance (about 16.7%) to supply two Coast templates**.

Once you have one, duplicate it at a Crafting Table with **one Coast template, seven Diamonds, and one Cobblestone**. The recipe consumes those inputs and returns **two Coast templates**, a net gain of one template. Follow the [shared duplication layout](../mechanics/ArmorTrims.md#copy-a-template-before-using-it); duplication cannot make your first template from raw materials alone.

## Usage

Put **one Coast template**, **one eligible armor piece**, and **one trim-material item** into the Smithing Table's template, base, and addition slots, respectively. Take the output to receive the same armor piece with the Coast trim. This consumes the template and addition. See [Armor trims](../mechanics/ArmorTrims.md#apply-or-replace-a-trim) for accepted armor and all eleven addition materials.

## Behavior

Trimming preserves the base armor's other properties, including damage, enchantments, and name. It supplies decoration without increasing protection or durability. A different pattern or material replaces the existing trim; repeating exactly the same pattern and material gives no output and consumes nothing. A trim template cannot perform a [Netherite upgrade](SmithingTemplateNetheriteUpgrade.md).

## Notes

- Item ID: `minecraft:coast_armor_trim_smithing_template`
- The stated acquisition chance belongs to the named loot-table roll, not to a whole structure or expedition; data packs and previously looted locations can change what you find
- [Armor trims: complete pattern and copying reference](../mechanics/ArmorTrims.md) · [Smithing](../smithing/Smithing.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked active item/recipe data, acquisition table and its consumer or placement wiring. No in-game loot or appearance test was run; see the [appearance limits](../mechanics/ArmorTrims.md#appearance-and-verification-limits).

- [Item registration](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java)
- [Duplication recipe](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/coast_armor_trim_smithing_template.json)
- [Trim recipe](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/smithing/coast_armor_trim_smithing_template_smithing_trim.json)
- [Acquisition: chests/shipwreck_map](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/shipwreck_map.json)
- [Acquisition: chests/shipwreck_supply](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/shipwreck_supply.json)
- [Acquisition: chests/shipwreck_treasure](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/shipwreck_treasure.json)
- [Active loot or interaction consumer](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/structures/ShipwreckPieces.java)
- [Trim preservation and unchanged-result check](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/SmithingTrimRecipe.java)
- [Smithing input consumption](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/SmithingMenu.java)
