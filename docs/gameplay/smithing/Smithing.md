# Smithing

Use a [Smithing Table](../blocks/SmithingTable.md) to upgrade equipment or add an armor trim. These are different operations: a Netherite upgrade changes the equipment item, while a trim changes its decoration.

## The three inputs

Open a placed table and fill the input slots from left to right:

1. **Template:** the Smithing Template for the upgrade or trim pattern
2. **Base:** the equipment you want to change
3. **Addition:** the material required by that recipe

Take the result from the output slot to finish. For the recipes below, each result consumes **one template, one base item, and one addition item**. The equipment is returned as the result; the template is not returned. The menu does not charge experience levels or require fuel.

Putting an item into an accepted slot does not prove that all three inputs form a recipe. Slot acceptance is collected from loaded recipes, while the output requires a matching combination.

## Example: upgrade a Diamond Pickaxe

Place these three items in order:

- [Netherite Upgrade Smithing Template](../items/SmithingTemplateNetheriteUpgrade.md)
- Diamond Pickaxe
- [Netherite Ingot](../items/NetheriteIngot.md)

The bundled recipe returns **one Netherite Pickaxe**. Its addition uses the `minecraft:netherite_tool_materials` tag, which contains only Netherite Ingot in the checked data. Netherite Scrap is not a substitute for the ingot.

The upgrade carries over the base item's saved changes, including enchantments, a custom name, and its damage value. It applies those changes to the new item's defaults rather than copying every default from the Diamond Pickaxe. **Upgrading does not reset damage to zero.** A data-pack recipe can also supply its own component changes, so the same guarantees should not be assumed for every custom transformation.

Because using the template consumes it, consider making copies first. The [Netherite Upgrade template page](../items/SmithingTemplateNetheriteUpgrade.md) explains its crafting-table duplication recipe and acquisition. Duplication requires an existing template; it does not create the first one from raw materials alone.

## Example: apply an armor trim

See [Armor trims](../mechanics/ArmorTrims.md) for all patterns, first-template sources, exact copying ingredients, and appearance-verification limits.

A verified combination is **Sentry Armor Trim Smithing Template + Diamond Chestplate + Redstone Dust**. It returns the same chestplate with the Sentry pattern and redstone trim material.

The template selects the pattern; the addition selects the trim material. The bundled trim-material tag contains Amethyst Shard, Copper Ingot, Diamond, Emerald, Gold Ingot, Iron Ingot, Lapis Lazuli, Netherite Ingot, Nether Quartz, Redstone Dust, and Resin Brick. The armor must belong to the `minecraft:trimmable_armor` tag; an integrated item being called “armor” is not enough.

Trims are decorative. The trim operation copies one base item and changes only its trim component, leaving its other saved properties intact. It does not upgrade the armor's material or increase its defense or durability. Using Netherite Ingot as a trim material is therefore different from performing a Netherite upgrade.

Applying a different pattern or material replaces an existing trim. Applying the **same pattern and material already on the armor produces no output**; this prevents spending inputs on an unchanged trim.

## If no result appears

- Check the template, base item, and addition as a complete combination
- Use the correct kind of template: an armor-trim template does not perform a Netherite upgrade
- Check exact ingredients and current tag membership, especially for integrated items
- For an already-trimmed item, choose a different pattern or material
- Check the server's loaded recipes and data packs if the documented combination is unavailable

## Related pages

- [Smithing Table: crafting, collection, and menu](../blocks/SmithingTable.md)
- [Smithing Table item](../items/SmithingTable.md)
- [Netherite Ingot](../items/NetheriteIngot.md)
- [Netherite Upgrade Smithing Template](../items/SmithingTemplateNetheriteUpgrade.md)
- [Crafting](../crafting/Crafting.md)
- [Gameplay](../Gameplay.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. This is not an in-game test. These examples use active `src/main` code and bundled data; data packs and later builds can change recipes, tags, or results. Other templates' acquisition routes are not covered here.

- [Menu slots, recipe lookup, and input consumption](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/SmithingMenu.java)
- [Base menu and output pickup](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/ItemCombinerMenu.java)
- [Recipe loading and accepted-input sets](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java)
- [Netherite Pickaxe recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/smithing/netherite_pickaxe_smithing.json)
- [Netherite tool materials](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/netherite_tool_materials.json)
- [Smithing transformation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/crafting/SmithingTransformRecipe.java)
- [Transformation result and recipe component changes](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java)
- [Stack copying and preserved component changes](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/ItemStack.java#L607-L637)
- [Netherite template duplication](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/netherite_upgrade_smithing_template.json)
- [Sentry trim recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/smithing/sentry_armor_trim_smithing_template_smithing_trim.json)
- [Trim application, preservation, and unchanged-result handling](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/crafting/SmithingTrimRecipe.java)
- [Trimmable armor tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/trimmable_armor.json) and [chest armor tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/chest_armor.json)
- [Trim-material ingredients](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/trim_materials.json) and [material resolution](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/equipment/trim/TrimMaterials.java)
- [Item registration and trim-material properties](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
- [Trim rendering](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer.java#L106-L115)
