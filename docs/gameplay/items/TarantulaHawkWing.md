# Tarantula Hawk Wing

The **Tarantula Hawk Wing** is a registered material item with no verified crafting or active use in the checked MattMC snapshot. Its name does not make it a glider or establish that a [Tarantula Hawk](../mobs/TarantulaHawk.md) drops it. [Registration][hawk-items] · [Hawk implementation][hawk]

## Obtaining

It appears in an ordinary item category, so the [inventory item browser](../mechanics/InventoryBrowser.md) can insert it in **Creative**. [Category entry][categories]

No bundled recipe, death-loot entry or hawk growth/interaction reward supplies a wing in the checked sources. Do not build a hawk farm around this item until that resource route is supplied by your server's data or a later implementation. [Bundled recipes][recipe-data] · [Entity loot][loot-data] · [Hawk behavior][hawk]

## Usage

No bundled recipe consumes the wing, and its registration is an ordinary item without a special use handler. In particular, there is no checked wing-to-[Tarantula Hawk Elytra](TarantulaHawkElytra.md) crafting recipe. [Registration][hawk-items] · [Bundled recipes][recipe-data]

## Behavior

Default wings stack to **64**, have no durability and provide no equipment or gliding component. [Registration][hawk-items] · [Default item components][common-components]

## Notes

Registered as `minecraft:tarantula_hawk_wing`. It is a separate item from [Tarantula Hawk Wing Fragment](TarantulaHawkWingFragment.md); no bundled fragment-to-wing recipe joins the two. This is bundled Alex's Mobs content whose current data is incomplete. [Registration][hawk-items] · [Recipe data][recipe-data]

Related: [Tarantula Hawk](../mobs/TarantulaHawk.md) · [Tarantula Hawk Elytra](TarantulaHawkElytra.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. Checked active registrations, relevant callers and bundled data. No in-game spawn, combat, feeding, drop, crafting, equipment or repair test was run. Server data packs and custom components may change these defaults.

[hawk-items]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Items.java#L1971-L1981
[hawk]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/alexsmobs/entity/EntityTarantulaHawk.java
[categories]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[recipe-data]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/recipe
[loot-data]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities
[common-components]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L389
