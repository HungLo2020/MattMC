# Smithing Template (Ward Armor Trim)

Use this template to choose the **Ward pattern** when decorating armor at a [Smithing Table](../blocks/SmithingTable.md). The addition material chooses the trim material; it does not change which pattern this template supplies.

## Obtaining

Search ordinary Ancient City chests. Their trim pool has a **5% chance to supply one Ward template**. Ice-box chests use a different loot table.

Once you have one, duplicate it at a Crafting Table with **one Ward template, seven Diamonds, and one Cobbled Deepslate**. The recipe consumes those inputs and returns **two Ward templates**, a net gain of one template. Use Cobbled Deepslate, not ordinary Deepslate. Follow the [shared duplication layout](../mechanics/ArmorTrims.md#copy-a-template-before-using-it); duplication cannot make your first template from raw materials alone.

## Usage

Put **one Ward template**, **one eligible armor piece**, and **one trim-material item** into the Smithing Table's template, base, and addition slots, respectively. Take the output to receive the same armor piece with the Ward trim. This consumes the template and addition. See [Armor trims](../mechanics/ArmorTrims.md#apply-or-replace-a-trim) for accepted armor and all eleven addition materials.

## Behavior

Trimming preserves the base armor's other properties, including damage, enchantments, and name. It supplies decoration without increasing protection or durability. A different pattern or material replaces the existing trim; repeating exactly the same pattern and material gives no output and consumes nothing. A trim template cannot perform a [Netherite upgrade](SmithingTemplateNetheriteUpgrade.md).

## Notes

- Item ID: `minecraft:ward_armor_trim_smithing_template`
- The stated acquisition chance belongs to the named loot-table roll, not to a whole structure or expedition; data packs and previously looted locations can change what you find
- [Armor trims: complete pattern and copying reference](../mechanics/ArmorTrims.md) · [Smithing](../smithing/Smithing.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked active item/recipe data, acquisition table and its consumer or placement wiring. No in-game loot or appearance test was run; see the [appearance limits](../mechanics/ArmorTrims.md#appearance-and-verification-limits).

- [Item registration](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java)
- [Duplication recipe](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/ward_armor_trim_smithing_template.json)
- [Trim recipe](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/smithing/ward_armor_trim_smithing_template_smithing_trim.json)
- [Acquisition: chests/ancient_city](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/ancient_city.json)
- [Placement data: worldgen/template_pool/ancient_city/structures.json](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/structures.json)
- [Placement data: structure/ancient_city/structures/barracks.nbt](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt)
- [Trim preservation and unchanged-result check](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/SmithingTrimRecipe.java)
- [Smithing input consumption](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/SmithingMenu.java)
