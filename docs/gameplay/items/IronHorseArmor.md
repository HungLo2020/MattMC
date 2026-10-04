# Iron Horse Armor

**Iron Horse Armor** (`minecraft:iron_horse_armor`) protects a [Horse](../mobs/Horse.md) with **5 armor points** and **0 armor toughness** while worn in its body slot. It offers one more armor point than [Copper Horse Armor](CopperHorseArmor.md). [Golden Horse Armor](GoldenHorseArmor.md) gives a Horse more armor than Iron does. See the [full Horse Armor comparison](../mobs/Horse.md#riding-and-equipment). [Registration][items] · [Material values][materials] · [Equipped attributes][attributes]

## Obtaining

The following chests can roll this armor. Each selected armor entry gives **one item**; finding the structure or opening a chest does not guarantee an armor roll.

- [Monster Room](../structures/MonsterRoom.md) chests. [Loot entry][dungeon]
- [Desert Pyramid](../structures/DesertPyramid.md) chests. [Loot entry][desert]
- [Jungle Temple](../structures/JungleTemple.md) chests. [Loot entry][jungle]
- [Nether Fortress](../structures/NetherFortress.md) chests. [Loot entry][fortress]
- [End City](../structures/EndCity.md) treasure chests. [Loot entry][end-city]
- [Stronghold](../structures/Stronghold.md) corridor chests. [Loot entry][stronghold]
- Village weaponsmith chests. [Loot entry][village]

The optional **Trade Rebalance** pack retains these armor entries in its replacement Desert Pyramid and Jungle Temple tables. The chest contents remain random. [Pyramid override][rebalance-desert] · [Temple override][rebalance-jungle]

No recipe producing this item or villager trade for it was found in the reviewed base data or bundled optional packs. The Leatherworker's horse-armor sale is [Leather Horse Armor](LeatherHorseArmor.md), a different item. [Recipe resources][recipes] · [Bundled packs][packs] · [Leatherworker trades][villager-trades]

It is listed in the Combat category of MattMC's [inventory item browser](../mechanics/InventoryBrowser.md). Catalog browsing works in Survival, but ordinary Survival insertion requests are skipped before the server handler; use the [Creative insertion route and its limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits). [Category entry][category]

## Usage

On a **tame, unoccupied adult Horse**, interact while holding the armor to fill its empty body-armor slot. Use **Sneak/Crouch + interact** to open the Horse inventory and replace or remove armor. The [Saddle](Saddle.md) has a separate slot; follow the [Horse equipment guide](../mobs/Horse.md#riding-and-equipment) for riding controls. [Horse interaction][horse-interact] · [Body equip path][body-equip] · [Inventory slots][menu]

The default armor fits the **ordinary Horse only**. Donkeys, Mules, Skeleton Horses, Zombie Horses, and other mounts are outside its allowed-entity tag. [Allowed entities][allowed]

For recovery, use the inventory or the [eligible Shears interaction](Saddle.md#removing-a-saddle). [Horse death-recovery rules](../mobs/Horse.md#keeping-and-recovering-your-mount) explain the adult, mob-loot, and equipment-drop conditions; death is not required to take armor back.

A spare armor can be recycled into **1 Iron Nugget** in a Furnace (**200 ticks**) or Blast Furnace (**100 ticks**). The whole armor is consumed; these are processing recipes, not ways to craft armor. See [Smelting](../smelting/Smelting.md#fuel-and-output) for fuel and output rules. [Furnace recipe][smelting] · [Blasting recipe][blasting] · [Active processing][furnace-process]

## Behavior

The default item stacks to **1**, has **no durability**, and does not take equipment damage when its Horse is hurt. It needs no routine repair. Armor points are equipped defensive attributes, not health or a fixed damage-reduction percentage; see [Armor and damage reduction](../mechanics/Armor.md#why-armor-points-are-not-a-fixed-reduction-percentage). [Item components][components] · [Attribute construction][attributes]

It has no ordinary enchanting-table option. The bundled enchantments also exclude it from their supported items, so applying enchanted books at an Anvil in ordinary Survival has no eligible enchantment. Creative anvil rules or changed item components and data packs can behave differently. It is outside the bundled [armor-trim recipes](../mechanics/ArmorTrims.md#eligible-armor) and cannot be dyed; use [Leather Horse Armor](LeatherHorseArmor.md#behavior) for dye colors. [Enchantability check][enchanting] · [Enchantment definitions][enchantment-data] · [Expanded item tags][item-tags] · [Supported-item test][enchantment-check] · [Anvil gates][anvil] · [Trim tag][armor-tags] · [Dyeable tag][dyeable]

## Notes

* Registry ID: `minecraft:iron_horse_armor`
* Source-reviewed on **2026-10-04** at [`78e8e0423084f010bb47e36132550619b37644c2`][snapshot]. This covers active `src/main` registration, recipe/loot filenames across namespaces and optional packs, referenced tags, chest consumers, trades, and the selected interaction, equipment, damage, and menu paths. No in-game loot, smithing, equipping, enchanting, or rendering test was run. Later builds and data packs can change the rules.
* Related: [Horse](../mobs/Horse.md) · [Leather Horse Armor](LeatherHorseArmor.md) · [Items](Items.md)

[allowed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/entity_type/can_wear_horse_armor.json#L1-L5
[anvil]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L179-L222
[armor-tags]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/trimmable_armor.json#L1-L8
[attributes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterial.java#L25-L40
[blasting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/blasting/iron_nugget_from_blasting.json
[body-equip]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L635-L659
[category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1640-L1645
[components]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L487-L502
[desert]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/desert_pyramid.json#L135-L154
[dungeon]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/simple_dungeon.json#L53-L72
[dyeable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/dyeable.json#L1-L10
[enchanting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L965-L971
[enchantment-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L179-L180
[enchantment-data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/enchantment/
[end-city]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/end_city_treasure.json#L92-L107
[fortress]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/nether_bridge.json#L91-L110
[furnace-process]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L143-L188
[horse-interact]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/horse/Horse.java#L158-L176
[item-tags]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2129-L2134
[jungle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/jungle_temple.json#L135-L150
[materials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java#L9-L38
[menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/HorseInventoryMenu.java#L23-L43
[packs]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks/
[rebalance-desert]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/loot_table/chests/desert_pyramid.json#L124-L143
[rebalance-jungle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/loot_table/chests/jungle_temple.json#L124-L139
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/
[smelting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/iron_nugget_from_smelting.json
[snapshot]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2
[stronghold]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/stronghold_corridor.json#L157-L172
[village]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_weaponsmith.json#L154-L169
[villager-trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L615-L645
