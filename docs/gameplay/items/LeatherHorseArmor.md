# Leather Horse Armor

**Leather Horse Armor** is dyeable body armor for a [Horse](../mobs/Horse.md), providing **3 armor points and 0 armor toughness** when equipped. Bald Eagle code also checks for this item in a player's hand, but that does not provide a working glove-carry or launch feature in the reviewed snapshot. [Item registration][item] · [Material values][material] · [Attribute construction][attributes] · [Eagle limits](../mobs/BaldEagle.md#falconry-and-the-player-carry-limit)

## Obtaining

Craft one piece from **7 Leather** in a 3×3 grid:

| Leather | Empty | Leather |
| --- | --- | --- |
| Leather | Leather | Leather |
| Leather | Empty | Leather |

[Bundled crafting recipe][recipe] · [Recipe loading][recipe-loading]

The level-4 Leatherworker trade pool also includes a dyed piece for a **base price of 6 Emeralds**; the final offered price can change through normal trading modifiers. In Creative, request it through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), where it is listed in the Combat category. [Trade pool][trade] · [Dyed trade construction][dyed-trade] · [Creative category][category]

## Usage

On a **tame, unoccupied adult Horse**, interact while holding the armor to fill an empty body-armor slot. To manage or replace equipped armor, use Sneak/Crouch + interact to open the Horse inventory. The armor slot is separate from the Saddle slot; armor does not enable steering or jumping. See the [Horse guide](../mobs/Horse.md#riding-and-equipment) for riding and equipment controls. [Horse interaction][horse] · [Shared equip interaction][horse-equip] · [Inventory slots][horse-menu]

The bundled allowed-entity tag contains the ordinary Horse only. Leather Horse Armor is not player armor and does not equip on Donkeys, Mules, or Bald Eagles as body protection. [Allowed entities][allowed] · [Body-slot component][components]

## Behavior

Its 3 armor points are an equipped attribute, not extra hearts or a fixed percentage of damage reduction. The default item stacks to **1**, has no durability component, and disables equipment damage on hurt. [Horse-armor components][components] · [Armor material][material] · [Armor and damage reduction](../mechanics/Armor.md)

Combine one Leather Horse Armor with one or more dyes in a crafting grid to color it. A Water Cauldron removes an existing dye color and lowers the water level by one. These are cosmetic changes; they do not change the leather material's armor attributes. [Dyeable tag][dyeable] · [Active dye recipe][dye-recipe] · [Dye recipe logic][dye-logic] · [Water Cauldron registration][wash-registration] · [Washing behavior][wash]

### Bald Eagle hand-item check

The [Bald Eagle](../mobs/BaldEagle.md) interaction treats Leather Horse Armor held in either hand as its glove check. On a tamed adult, an eligible non-food interaction attempts to make the eagle a passenger of the player. **The server rejects that attachment because the player entity type is non-serializable**, even though the eagle reports interaction success; a client-local result does not establish a working carry. [Eagle interaction][eagle-interaction] · [Mount guard][mount-guard] · [Player type][player-type] · [`noSave` implementation][no-save]

No separate Falconry Glove item, active launch caller, or direct-control caller was found in the reviewed gameplay tree. The eagle's hand-positioning, launch, remote-control, and return methods do not by themselves make those features available. Keep this item's normal Horse-armor role separate from those incomplete eagle paths; see the [full falconry explanation](../mobs/BaldEagle.md#falconry-and-the-player-carry-limit) and [issue #805](https://github.com/HungLo2020/MattMC/issues/805). [Actual registration][item] · [Eagle implementation][eagle] · [Falconry interface][falconry] · [Reviewed source][snapshot]

## Notes

* This item is registered as `minecraft:leather_horse_armor`. It uses ordinary `Item` registration with horse-armor components, not a special Falconry Glove item class. [Registration][item]
* Source reviewed at [`2fff1ef`][snapshot] on 2026-10-03. Recipe/trade/category data, Horse equipment, dyeing, and eagle interaction reachability were inspected; no in-game crafting, trade, armor, or falconry test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2134
[material]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java#L9-L11
[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/equipment/ArmorMaterial.java#L25-L40
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/leather_horse_armor.json#L1-L16
[recipe-loading]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L76
[trade]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L615-L645
[dyed-trade]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1174-L1214
[category]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1640
[horse]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/horse/Horse.java#L158-L176
[horse-equip]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L635-L659
[horse-menu]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/HorseInventoryMenu.java#L20-L52
[allowed]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/can_wear_horse_armor.json#L1-L5
[components]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Item.java#L487-L502
[dyeable]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/dyeable.json#L1-L10
[dye-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/armor_dye.json#L1-L4
[dye-logic]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/ArmorDyeRecipe.java#L17-L70
[wash-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java#L74-L128
[wash]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java#L313-L329
[eagle-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L328-L378
[mount-guard]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2291-L2329
[player-type]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1596-L1606
[no-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2142-L2145
[eagle]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java
[falconry]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/IFalconry.java#L7-L21
[snapshot]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716
