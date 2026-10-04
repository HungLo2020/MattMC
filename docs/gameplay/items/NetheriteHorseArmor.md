# Netherite Horse Armor

**Netherite Horse Armor** (`minecraft:netherite_horse_armor`) protects a [Horse](../mobs/Horse.md) with **11 armor points** and **3 armor toughness** while worn in its body slot. It matches [Diamond Horse Armor](DiamondHorseArmor.md) in armor points, raises toughness from 2 to 3, and adds **0.1 knockback resistance**. See the [full Horse Armor comparison](../mobs/Horse.md#riding-and-equipment). [Registration][items] · [Material values][materials] · [Equipped attributes][attributes]

## Obtaining

Upgrade [Diamond Horse Armor](DiamondHorseArmor.md) at a [Smithing Table](../blocks/SmithingTable.md):

1. Left input: **1 Netherite Upgrade Smithing Template**
2. Middle input: **1 Diamond Horse Armor**
3. Right input: **1 Netherite Ingot**
4. Take **1 Netherite Horse Armor** from the output

Taking the result consumes one of each input, including the template. The upgrade carries over the base stack's saved component changes, such as a custom name. See [Smithing](../smithing/Smithing.md#the-three-inputs) and the [Netherite Upgrade template](SmithingTemplateNetheriteUpgrade.md) for the shared workflow and template supply. [Exact recipe][smithing-recipe] · [Accepted addition][upgrade-addition] · [Consumption][smithing-menu] · [Component copy][component-copy]

The upgrade is part of the base game data and needs no optional pack. No direct chest-loot or villager-trade source for Netherite Horse Armor was found in the reviewed base data or bundled optional packs. [Recipe codec][smithing-codec] · [Result codec][result-codec] · [Server result selection][smithing-result] · [Loot resources][loot-data] · [Bundled packs][packs] · [Trade definitions][villager-trades]

It is listed in the Combat category of MattMC's [inventory item browser](../mechanics/InventoryBrowser.md). Catalog browsing works in Survival, but ordinary Survival insertion requests are skipped before the server handler; use the [Creative insertion route and its limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits). [Category entry][category]

## Usage

On a **tame, unoccupied adult Horse**, interact while holding the armor to fill its empty body-armor slot. Use **Sneak/Crouch + interact** to open the Horse inventory and replace or remove armor. The [Saddle](Saddle.md) has a separate slot; follow the [Horse equipment guide](../mobs/Horse.md#riding-and-equipment) for riding controls. [Horse interaction][horse-interact] · [Body equip path][body-equip] · [Inventory slots][menu]

The default armor fits the **ordinary Horse only**. Donkeys, Mules, Skeleton Horses, Zombie Horses, and other mounts are outside its allowed-entity tag. [Allowed entities][allowed]

For recovery, use the inventory or the [eligible Shears interaction](Saddle.md#removing-a-saddle). [Horse death-recovery rules](../mobs/Horse.md#keeping-and-recovering-your-mount) explain the adult, mob-loot, and equipment-drop conditions; death is not required to take armor back.

## Behavior

The default item stacks to **1**, has **no durability**, and does not take equipment damage when its Horse is hurt. It needs no routine repair. Armor points are equipped defensive attributes, not health or a fixed damage-reduction percentage; see [Armor and damage reduction](../mechanics/Armor.md#why-armor-points-are-not-a-fixed-reduction-percentage). [Item components][components] · [Attribute construction][attributes]

It has no ordinary enchanting-table option. The bundled enchantments also exclude it from their supported items, so applying enchanted books at an Anvil in ordinary Survival has no eligible enchantment. Creative anvil rules or changed item components and data packs can behave differently. It is outside the bundled [armor-trim recipes](../mechanics/ArmorTrims.md#eligible-armor) and cannot be dyed; use [Leather Horse Armor](LeatherHorseArmor.md#behavior) for dye colors. [Enchantability check][enchanting] · [Enchantment definitions][enchantment-data] · [Expanded item tags][item-tags] · [Supported-item test][enchantment-check] · [Anvil gates][anvil] · [Trim tag][armor-tags] · [Dyeable tag][dyeable]

The **dropped armor item resists fire-type damage, including Lava**. This item property does not give the Horse fire immunity; protect the mount separately. [Fire-resistant registration][items] · [Resistance component][fire-property] · [Fire damage tag][fire-tag] · [Dropped-item damage check][item-damage]

## Notes

* Registry ID: `minecraft:netherite_horse_armor`
* Source-reviewed on **2026-10-04** at [`78e8e0423084f010bb47e36132550619b37644c2`][snapshot]. This covers active `src/main` registration, recipe/loot filenames across namespaces and optional packs, referenced tags, chest consumers, trades, and the selected interaction, equipment, damage, and menu paths. No in-game loot, smithing, equipping, enchanting, or rendering test was run. Later builds and data packs can change the rules.
* Related: [Horse](../mobs/Horse.md) · [Leather Horse Armor](LeatherHorseArmor.md) · [Items](Items.md)

[allowed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/entity_type/can_wear_horse_armor.json#L1-L5
[anvil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L179-L222
[armor-tags]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/trimmable_armor.json#L1-L8
[attributes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterial.java#L25-L40
[body-equip]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L635-L659
[category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1640-L1645
[component-copy]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L631-L636
[components]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L487-L502
[dyeable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/dyeable.json#L1-L10
[enchanting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L965-L971
[enchantment-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L179-L180
[enchantment-data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/enchantment/
[fire-property]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L402-L404
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/damage_type/is_fire.json#L1-L11
[horse-interact]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/horse/Horse.java#L158-L176
[item-damage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L253-L285
[item-tags]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2129-L2134
[loot-data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/
[materials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java#L9-L38
[menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/HorseInventoryMenu.java#L23-L43
[packs]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks/
[result-codec]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L16-L27
[smithing-codec]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/SmithingTransformRecipe.java#L78-L87
[smithing-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/SmithingMenu.java#L70-L93
[smithing-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/netherite_horse_armor_smithing.json#L1-L9
[smithing-result]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/SmithingMenu.java#L105-L122
[snapshot]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2
[upgrade-addition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/netherite_tool_materials.json#L1-L5
[villager-trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L615-L645
