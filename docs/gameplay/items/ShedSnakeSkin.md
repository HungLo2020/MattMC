# Shed Snake Skin

**Shed Snake Skin** (`minecraft:shed_snake_skin`) is a registered plain item associated with the [Anaconda](../mobs/Anaconda.md). **Its ordinary Survival supply is not established in the current source:** the Anaconda's imported kill-to-digestion trigger does not match the active death callback. It also has no checked category entry in the inventory browser. [Registration][registration] · [Anaconda production limits](../mobs/Anaconda.md#swallowing-and-shed-snake-skin) · [Browser list][browser]

## Obtaining

For commands or mapmaking, a player with the required command permission can use `/give @s minecraft:shed_snake_skin 1`. Registration makes that command possible; it does not automatically add the item to the Creative catalog or provide a Survival recipe. [Item registration][registration] · [Registration helper][register-helper] · [Give command][give]

The checked category lists and their variant generators do not include Shed Snake Skin. MattMC's [inventory browser](../mechanics/InventoryBrowser.md#which-items-appear) assembles category display items rather than enumerating the whole item registry, so switching to Creative does not by itself create a missing catalog entry. [Category entries][categories] · [Variant helpers][category-helpers] · [Category build][category-build] · [Browser list][browser]

An Anaconda with an already-established shedding timer can drop **one skin** when the timer expires. However, ordinary prey kills do not call its narrower kill overload, and using raw food does not call its digestion routine. The [Anaconda guide](../mobs/Anaconda.md#swallowing-and-shed-snake-skin) explains the exact limitation and conditional timers. Do not assume that killing snakes, feeding three raw items, or letting one kill three animals supplies skin. [Shedding reward][shed] · [Food interaction][food-interaction] · [Kill overload][kill-overload] · [Current callback][kill-callback] · [Death dispatcher][death] · [Digestion integration issue #806](https://github.com/HungLo2020/MattMC/issues/806)

No dedicated recipe or loot-table source was found after checking resource filenames and contents across bundled namespaces, active singular `recipe/` and `loot_table/` directories, other recipe directories, and optional packs. Custom server data can change these routes. [Bundled data][data]

## Usage

No skin-specific crafting, smelting, trading, repair, feeding, or special interaction use was found in the reviewed code and bundled data. Its registration supplies no food, equipment, tool, or consumable component. Treat it as an ordinary item for storage or display rather than as functioning armor, snake food, or an established crafting ingredient. [Registration][registration] · [Default properties][properties] · [Base use handling][use] · [Bundled data][data]

## Behavior

An unmodified stack holds **64** and has no durability. It uses the base Item handlers, with no dedicated use-on-block or use-on-mob action. Its item definition resolves to a bundled generated item model and skin texture; those assets do not establish a gameplay use or an acquisition route. [Default components][components] · [Base use handling][use] · [Mob interaction][mob-use] · [Item definition][item-definition] · [Item model][item-model]

## Notes

This item comes from bundled Alex's Mobs content integrated into MattMC. The Anaconda page owns the behavior and production explanation; this page covers the skin's availability and item properties.

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. No command, catalog, shedding, recipe, or item-use test was run in game.

Related: [Anaconda](../mobs/Anaconda.md) · [Inventory browser](../mechanics/InventoryBrowser.md) · [Items](Items.md)

[registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1745
[register-helper]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2792-L2797
[give]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/commands/GiveCommand.java#L25-L49
[categories]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L71-L2187
[category-helpers]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2207-L2283
[category-build]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2290-L2318
[browser]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[shed]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L395-L404
[food-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L146-L153
[kill-overload]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L537-L547
[kill-callback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L1873-L1877
[death]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1413-L1435
[properties]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Item.java#L354-L371
[use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Item.java#L164-L192
[components]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[mob-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Item.java#L254-L256
[item-definition]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/assets/minecraft/items/shed_snake_skin.json#L1-L6
[item-model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/assets/minecraft/models/item/shed_snake_skin.json#L1-L6
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
