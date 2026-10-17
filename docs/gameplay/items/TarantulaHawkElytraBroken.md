# Tarantula Hawk Elytra (Broken)

**Tarantula Hawk Elytra (Broken)** is a separate plain item. It is **not** the ordinary fully damaged state of [Tarantula Hawk Elytra](TarantulaHawkElytra.md), and there is no checked wear-out conversion that produces it. [Separate registrations][hawk-items] · [Retained damage handling][stack] · [Elytra implementation][hawk-elytra]

## Obtaining

The broken-named item is listed in an ordinary category, so the [inventory item browser](../mechanics/InventoryBrowser.md) can insert it in **Creative**. [Category entry][categories]

No bundled crafting recipe, loot entry or active break conversion supplies it through ordinary resource play. Damaging a Tarantula Hawk Elytra stack to its limit retains that original item ID. [Bundled recipes][recipe-data] · [Entity loot][loot-data] · [Stack damage path][stack] · [Item handler][hawk-elytra]

## Usage

It has no configured equipment slot or gliding ability. It also lacks damage components, so the ordinary two-item crafting repair recipe cannot turn it into usable wings. [Registration][hawk-items] · [Flight eligibility][glider] · [Repair requirements][repair-recipe]

For flight, use [ordinary Elytra](Elytra.md). For an actual retained fully damaged equipment stack, consult [Durability and repair](../mechanics/Durability.md); this item's name does not give it those repair properties.

## Behavior

It stacks to **64**, has **no durability bar**, and is registered as a plain item without a special repair or use handler. [Registration][hawk-items] · [Default components][common-components]

## Notes

Registered as `minecraft:tarantula_hawk_elytra_broken`, distinct from `minecraft:tarantula_hawk_elytra`. It is bundled Alex's Mobs content whose named recovery route is not connected in the checked implementation. [Registration][hawk-items]

Related: [Tarantula Hawk Elytra](TarantulaHawkElytra.md) · [Tarantula Hawk Wing Fragment](TarantulaHawkWingFragment.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. Checked active registrations, relevant callers and bundled data. No in-game spawn, combat, feeding, drop, crafting, equipment or repair test was run. Server data packs and custom components may change these defaults.

[hawk-items]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Items.java#L1971-L1981
[stack]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[hawk-elytra]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/alexsmobs/item/ItemTarantulaHawkElytra.java
[categories]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[recipe-data]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/recipe
[loot-data]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities
[glider]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3703-L3710
[repair-recipe]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/crafting/RepairItemRecipe.java#L21-L80
[common-components]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L389
