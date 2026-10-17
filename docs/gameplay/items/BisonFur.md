# Bison Fur

**Bison Fur** (`minecraft:bison_fur`) is a plain, stackable resource item. Its verified produced supply is **dispenser shearing of an adult Bison**; the registered item is not food. [Item registration][item] · [Fur output][shear]

## Obtaining

Put Shears in a [Dispenser](../blocks/DispenserAndDropper.md) facing an adult, unsheared [Bison](../mobs/Bison.md#shearing-and-fur-regrowth), and activate it while the animal occupies the dispenser's front target space. The reachable shearing callback drops **2–3 Fur**, marks the coat sheared and resets its grazing counter. The dispenser handles a ripe hive or cuts an entity's leash connections before reaching coat shearing, so isolate the target and account for those higher-priority actions. [Shears registration][dispenser-register] · [Dispenser targeting and priority][dispenser-shear] · [Readiness and output][shear]

A successful dispenser action requests **one point of Shears wear**, before damageability, broken-item and enchantment handling. The call supplies no player, so a player's Creative exemption is not applied; enchantments can still change the actual wear. [Request][dispenser-shear] · [Wear processing][shear-wear]

The Bison guide explains the current **lack of an ordinary hand-shearing hookup** and the **five completed Grass Block grazings** needed to regrow a coat. Holding or feeding Wheat is not an instant Fur-restoration shortcut. [Bison interaction][interaction] · [Shears item implementation][shears-item] · [Grazing][grazing] · [Regrowth][regrowth]

Fur is also in the ordinary Ingredients category and can be requested through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**, independently of shearing. [Category entry][tab] · [Browser list][browser-list] · [Client request][browser-client] · [Server check][browser-server]

## Usage

Keep Fur as a collected resource. **No bundled Fur crafting recipe was found** in the reviewed active data. Do not assume upstream Fur-to-Wool, Fur-block, carpet or armor-upgrade recipes exist in MattMC merely because this item is registered. [Registration][item] · [Bundled recipes][recipes]

## Behavior

The plain registration supplies no food, equipment or special-use component. It inherits the ordinary **64-item stack limit**. Shearing and coat recovery belong to the Bison entity, not to a use action on a held Fur item. [Item properties][item] · [Default stack][default-stack] · [Bison callback][shear]

## Notes

No bundled Bison death-loot table was found, so this page does not describe killing Bison as a Fur supply. Missing default entity loot resolves to an empty table; custom loot/equipment paths are separate. [Default loot key][loot-key] · [Missing-table handling][loot-missing] · [Bundled loot][loot-tables]

Source-reviewed on **2026-10-02** at `25319cecd6bee492767c5b15ee53b9213d1ec1ee`. Verified registration, category/browser access, the active dispenser call, Fur output, counter reset/regrowth, and bounded recipe/loot absence. No shearing, growing, dispenser, loot or production-rate test was run.

Related: [Bison](../mobs/Bison.md) · [Shears](Shears.md) · [Items](Items.md)

[browser-client]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-list]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-server]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[default-stack]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[dispenser-register]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L378
[dispenser-shear]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/core/dispenser/ShearsDispenseItemBehavior.java#L19-L67
[grazing]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L198-L209
[interaction]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L318-L344
[item]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Items.java#L1781
[loot-key]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-missing]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[loot-tables]: https://github.com/HungLo2020/MattMC/tree/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/loot_table
[recipes]: https://github.com/HungLo2020/MattMC/tree/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/recipe
[regrowth]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L259-L266
[shear]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L403-L417
[shear-wear]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/ItemStack.java#L457-L473
[shears-item]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/ShearsItem.java#L27-L84
[tab]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1820
