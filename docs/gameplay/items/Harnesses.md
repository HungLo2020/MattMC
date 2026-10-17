# Harnesses

**Harnesses** equip adult [Happy Ghasts](../mobs/HappyGhast.md) for riding. All **sixteen registered colors** share the same equipment rules and passenger capacity; color selects the appearance. Craft a Harness using matching Wool, or recolor an existing Harness with a Dye. A [Saddle](Saddle.md) is a different item for other mounts. [All registrations][harness-items] · [Shared component and color asset][harness-component] · [Allowed entity tag][harness-target] · [Passenger rules][happy-seats]

## Crafting a Harness

Each color's shaped recipe makes **1 Harness** from **3 [Leather](Leather.md), 2 [Glass](Glass.md) and 1 Wool of the desired color**. At a [Crafting Table](../blocks/CraftingTable.md), place Leather–Leather–Leather in one row, with Glass–colored Wool–Glass directly beneath it. Use ordinary Glass, not Glass Panes or Stained Glass. The exact recipe files are linked in the color table below.

To recolor, combine **1 Harness of any of the other fifteen colors** with **1 Dye matching the desired output** in a shapeless recipe. The same-color input is explicitly excluded. Both recipes make one new Harness item; the standard result assembly does not copy custom components from the input Harness. These files use recipe IDs `minecraft:crafting/<color>_harness` and `minecraft:crafting/dye_<color>_harness`. [Recipe loader][recipe-load] · [Shaped result assembly][shaped-result] · [Shapeless result assembly][shapeless-result]

## Colors and exact recipes

Wool and Dye IDs in this table use the `minecraft:` namespace. Each Craft link provides the full shaped recipe; each Recolor link lists its fifteen accepted Harness inputs.

| Existing item page | Output item ID | Wool for crafting | Dye for recoloring | Exact recipes |
| --- | --- | --- | --- | --- |
| [White Harness](WhiteHarness.md) | `minecraft:white_harness` | `white_wool` | `white_dye` | [Craft][craft-white] · [Recolor][dye-white] |
| [Orange Harness](OrangeHarness.md) | `minecraft:orange_harness` | `orange_wool` | `orange_dye` | [Craft][craft-orange] · [Recolor][dye-orange] |
| [Magenta Harness](MagentaHarness.md) | `minecraft:magenta_harness` | `magenta_wool` | `magenta_dye` | [Craft][craft-magenta] · [Recolor][dye-magenta] |
| [Light Blue Harness](LightBlueHarness.md) | `minecraft:light_blue_harness` | `light_blue_wool` | `light_blue_dye` | [Craft][craft-light_blue] · [Recolor][dye-light_blue] |
| [Yellow Harness](YellowHarness.md) | `minecraft:yellow_harness` | `yellow_wool` | `yellow_dye` | [Craft][craft-yellow] · [Recolor][dye-yellow] |
| [Lime Harness](LimeHarness.md) | `minecraft:lime_harness` | `lime_wool` | `lime_dye` | [Craft][craft-lime] · [Recolor][dye-lime] |
| [Pink Harness](PinkHarness.md) | `minecraft:pink_harness` | `pink_wool` | `pink_dye` | [Craft][craft-pink] · [Recolor][dye-pink] |
| [Gray Harness](GrayHarness.md) | `minecraft:gray_harness` | `gray_wool` | `gray_dye` | [Craft][craft-gray] · [Recolor][dye-gray] |
| [Light Gray Harness](LightGrayHarness.md) | `minecraft:light_gray_harness` | `light_gray_wool` | `light_gray_dye` | [Craft][craft-light_gray] · [Recolor][dye-light_gray] |
| [Cyan Harness](CyanHarness.md) | `minecraft:cyan_harness` | `cyan_wool` | `cyan_dye` | [Craft][craft-cyan] · [Recolor][dye-cyan] |
| [Purple Harness](PurpleHarness.md) | `minecraft:purple_harness` | `purple_wool` | `purple_dye` | [Craft][craft-purple] · [Recolor][dye-purple] |
| [Blue Harness](BlueHarness.md) | `minecraft:blue_harness` | `blue_wool` | `blue_dye` | [Craft][craft-blue] · [Recolor][dye-blue] |
| [Brown Harness](BrownHarness.md) | `minecraft:brown_harness` | `brown_wool` | `brown_dye` | [Craft][craft-brown] · [Recolor][dye-brown] |
| [Green Harness](GreenHarness.md) | `minecraft:green_harness` | `green_wool` | `green_dye` | [Craft][craft-green] · [Recolor][dye-green] |
| [Red Harness](RedHarness.md) | `minecraft:red_harness` | `red_wool` | `red_dye` | [Craft][craft-red] · [Recolor][dye-red] |
| [Black Harness](BlackHarness.md) | `minecraft:black_harness` | `black_wool` | `black_dye` | [Craft][craft-black] · [Recolor][dye-black] |

All sixteen are ordinary listed items in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), which permits insertion in Creative. This browser route is separate from the material recipes. [Category entries][equipment-category] · [Registered Harness family][harness-tag]

## Equipping and riding

Use a Harness on a **living adult Happy Ghast with an empty body slot**. It takes one Harness and marks that equipment for recovery on death under the usual loot conditions. A ghastling cannot use that slot, and an occupied slot is not replaced by simply applying another color. No taming condition is present in the Happy Ghast's equipment gate. [Adult slot and interaction][happy-equip] · [Item component dispatch][item-interaction] · [Equip action][equip-action]

A dispenser can also equip a Harness. Put the adult so that its body intersects the block directly in front of the dispenser; the equipment action selects the first eligible living target in that block. The target must be alive, not a spectator, allowed by the Harness component, and have an empty eligible body slot. If no target qualifies, the dispenser falls back to ordinary item dispensing. [Component dispatch][dispenser-dispatch] · [Target selection and fallback][dispenser-action] · [Eligibility][dispenser-gates] · [Happy Ghast slot][happy-equip]

After equipping, interact without Sneak/Crouch to board. **Four passengers** is the Happy Ghast's capacity; the player in the first seat controls it when the stay-still timeout is clear. A Harness does not increase capacity or add armor protection simply because it occupies the body slot. The default Harness registration sets a stack limit of **1** and an equippable component, without a durability or armor-attribute component. [Riding and capacity][happy-equip] · [Controller gate][happy-seats] · [Registered properties][harness-items]

For movement, looking up/down, Jump, dismounting, and the temporary platform behavior, use the [Happy Ghast riding guide](../mobs/HappyGhast.md#riding-and-steering). For hatching its baby, use [Dried Ghast](../blocks/DriedGhast.md).

## Removing and recoloring a Harness

1. Have **all passengers dismount**
2. Interact with usable **[Shears](Shears.md)** without Sneak/Crouch
3. If the ghast has leash connections, that interaction may remove the connections first; repeat the equipment interaction once they are clear
4. Pick up the dropped Harness, use its target-color recoloring recipe if wanted, then equip the adult's now-empty body slot again

The shared shearing path costs **1 Shears durability** before normal modifiers. An equipment-change prevention effect can block removal except for a Creative player. Do not assume a mounted ghast or sneaking interaction will remove its Harness. [Unoccupied gate][shear-passengers] · [Leash priority and non-sneaking gate][equipment-shear] · [Removal, damage and drop][shear-action]

Player-equipped and dispenser-equipped Harnesses are marked for guaranteed equipment drops, but death recovery still passes the adult/mob-loot gate and equipment-drop prevention checks. Shearing is the direct recovery action; killing the animal is unnecessary. [Equip marking][equip-action] · [Dispenser marking][dispenser-action] · [Loot gate][loot-gate] · [Death dispatch][death-drops] · [Equipment checks][equipment-drops]

## Related pages

- [Happy Ghast](../mobs/HappyGhast.md) and [Happy Ghast Spawn Egg](HappyGhastSpawnEgg.md)
- [Dried Ghast](../blocks/DriedGhast.md), [Saddle](Saddle.md) and [Transport](../mechanics/Transport.md)
- [Items](Items.md)

## Verification scope

Source-reviewed at MattMC commit `bb9a8a060da02b64f23508b77794fb0f79307de4` on 2026-10-02. All sixteen registrations and thirty-two recipes were compared with their active equippable, crafting, dispenser, interaction and removal paths. This family guide owns shared behavior; the existing color filenames remain precise recipe routes. No in-game crafting, recoloring, flight, dispenser, shearing or loot test was run.

[harness-items]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L1127-L1174
[harness-component]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L111-L120
[harness-target]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/entity_type/can_equip_harness.json
[happy-seats]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L292-L332
[recipe-load]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L85
[shaped-result]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/crafting/ShapedRecipe.java#L61-L82
[shapeless-result]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L55-L89
[craft-white]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/white_harness.json
[dye-white]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_white_harness.json
[craft-orange]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/orange_harness.json
[dye-orange]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_orange_harness.json
[craft-magenta]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/magenta_harness.json
[dye-magenta]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_magenta_harness.json
[craft-light_blue]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/light_blue_harness.json
[dye-light_blue]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_light_blue_harness.json
[craft-yellow]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/yellow_harness.json
[dye-yellow]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_yellow_harness.json
[craft-lime]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/lime_harness.json
[dye-lime]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_lime_harness.json
[craft-pink]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/pink_harness.json
[dye-pink]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_pink_harness.json
[craft-gray]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/gray_harness.json
[dye-gray]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_gray_harness.json
[craft-light_gray]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/light_gray_harness.json
[dye-light_gray]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_light_gray_harness.json
[craft-cyan]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/cyan_harness.json
[dye-cyan]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_cyan_harness.json
[craft-purple]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/purple_harness.json
[dye-purple]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_purple_harness.json
[craft-blue]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/blue_harness.json
[dye-blue]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_blue_harness.json
[craft-brown]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/brown_harness.json
[dye-brown]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_brown_harness.json
[craft-green]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/green_harness.json
[dye-green]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_green_harness.json
[craft-red]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/red_harness.json
[dye-red]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_red_harness.json
[craft-black]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/black_harness.json
[dye-black]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_black_harness.json
[equipment-category]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1494-L1512
[harness-tag]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/harnesses.json
[happy-equip]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L254-L290
[item-interaction]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ItemStack.java#L591-L605
[equip-action]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L159-L175
[dispenser-dispatch]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L107-L113
[dispenser-action]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/core/dispenser/EquipmentDispenseItemBehavior.java#L15-L37
[dispenser-gates]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3555-L3568
[shear-passengers]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/Mob.java#L512-L514
[equipment-shear]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/Entity.java#L2134-L2144
[shear-action]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/Entity.java#L2211-L2228
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[death-drops]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[equipment-drops]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/Mob.java#L813-L838
