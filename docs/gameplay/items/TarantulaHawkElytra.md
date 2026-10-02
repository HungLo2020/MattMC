# Tarantula Hawk Elytra

**Tarantula Hawk Elytra is not configured as a working glider in the checked MattMC snapshot.** Do not jump from a height expecting it to work like [ordinary Elytra](Elytra.md). Its registration gives it durability, but omits the equipment-slot and glider components required by the current flight checks. [Registration][hawk-items] · [Current glide eligibility][glider]

## Obtaining

It is an ordinary category entry that the [inventory item browser](../mechanics/InventoryBrowser.md) can insert in **Survival and Creative**. [Category entry][categories]

No bundled crafting recipe or loot entry supplies it. [Tarantula Hawk Wings](TarantulaHawkWing.md) and [Wing Fragments](TarantulaHawkWingFragment.md) do not form a checked recipe for these wings. [Bundled recipes][recipe-data] · [Entity loot][loot-data]

## Usage

The current item-use handler returns without equipping it, and its default stack lacks both **equippable** and **glider** components. The class's older flight-named methods are not the component-based flight route used by living entities. Use the [Elytra guide](Elytra.md) for a configured glider, flight controls and boosts. [Item handler][hawk-elytra] · [Default properties][hawk-items] · [Live glide check][glider]

**Wing Fragment repair is also not connected.** The class contains an older fragment-repair method, but the live Anvil asks the item stack's repairable component, which this registration omits. [Legacy method][hawk-elytra] · [Registration][hawk-items] · [Current repair check][repair-check] · [Anvil caller][anvil]

Its damage components do qualify two matching Tarantula Hawk Elytra stacks for the shared **crafting-grid repair** recipe. That combines remaining durability with the normal 5% bonus, but does not add missing flight components. See [crafting repair and data loss](../mechanics/Durability.md#crafting-grid-repair-details) before consuming customized items. [Active repair recipe][repair-data] · [Matching and result rules][repair-recipe]

## Behavior

The default item has **432 maximum durability** and a stack limit of **one**. These numbers do not establish 432 seconds of flight: the ordinary flight path cannot use this default stack. [Registration][hawk-items] · [Durability properties][item-durability] · [Glide eligibility][glider]

MattMC's normal wear path retains fully damaged stacks rather than replacing them with another item. A worn-out stack of this item is therefore distinct from the separately registered [Tarantula Hawk Elytra (Broken)](TarantulaHawkElytraBroken.md). No checked conversion produces that separate item. [Retained damage handling][stack] · [Separate registrations][hawk-items] · [Item implementation][hawk-elytra]

## Notes

Registered as `minecraft:tarantula_hawk_elytra`. Custom components can change a stack's behavior; these limits concern its bundled default registration. General flight and retained-broken rules remain owned by the [Elytra](Elytra.md) and [Durability](../mechanics/Durability.md) guides. [Registration][hawk-items]

Related: [Tarantula Hawk](../mobs/TarantulaHawk.md) · [Wing Fragment](TarantulaHawkWingFragment.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. Checked active registrations, relevant callers and bundled data. No in-game spawn, combat, feeding, drop, crafting, equipment or repair test was run. Server data packs and custom components may change these defaults.

[hawk-items]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Items.java#L1971-L1981
[glider]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3703-L3710
[categories]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[recipe-data]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/recipe
[loot-data]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities
[hawk-elytra]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/alexsmobs/item/ItemTarantulaHawkElytra.java
[repair-check]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1109
[anvil]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L120-L154
[repair-data]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/recipe/crafting/repair_item.json
[repair-recipe]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/crafting/RepairItemRecipe.java#L21-L80
[item-durability]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Item.java#L382-L391
[stack]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
