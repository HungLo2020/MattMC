# Tarantula Hawk Wing Fragment

The **Tarantula Hawk Wing Fragment** is a registered material. Its checked default item has no active crafting use, and it is **not a working Anvil repair material for Tarantula Hawk Elytra** in this snapshot. [Registration][hawk-items] · [Current repair check][repair-check]

## Obtaining

The fragment is an ordinary category entry, available through the [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. [Category entry][categories]

The bundled data has no fragment recipe or loot entry, and the [Tarantula Hawk](../mobs/TarantulaHawk.md) implementation has no growth reward producing it. Do not assume that breeding, raising or killing hawks supplies fragments. [Bundled recipes][recipe-data] · [Entity loot][loot-data] · [Hawk implementation][hawk]

## Usage

A legacy repair method in the [Tarantula Hawk Elytra](TarantulaHawkElytra.md) class names this fragment, but the current Anvil checks the stack's **repairable component**. That Elytra registration does not supply one, so the method does not establish fragment repair. No bundled fragment-to-[Wing](TarantulaHawkWing.md) recipe is present either. [Legacy method][hawk-elytra] · [Item components][hawk-items] · [Stack repair check][repair-check] · [Anvil caller][anvil] · [Recipe data][recipe-data]

## Behavior

Default fragments stack to **64** and have no durability, equipment slot or special item-use handler. [Registration][hawk-items] · [Default components][common-components]

## Notes

Registered as `minecraft:tarantula_hawk_wing_fragment`. Server data packs or custom item components can change its uses; this page describes the checked bundled defaults. [Registration][hawk-items]

Related: [Tarantula Hawk](../mobs/TarantulaHawk.md) · [Tarantula Hawk Wing](TarantulaHawkWing.md) · [Tarantula Hawk Elytra](TarantulaHawkElytra.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. Checked active registrations, relevant callers and bundled data. No in-game spawn, combat, feeding, drop, crafting, equipment or repair test was run. Server data packs and custom components may change these defaults.

[hawk-items]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Items.java#L1971-L1981
[repair-check]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1109
[categories]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[recipe-data]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/recipe
[loot-data]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities
[hawk]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/alexsmobs/entity/EntityTarantulaHawk.java
[hawk-elytra]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/alexsmobs/item/ItemTarantulaHawkElytra.java
[anvil]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L120-L154
[common-components]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L389
