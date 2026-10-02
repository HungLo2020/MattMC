# Moose Antler

**Moose Antler** (`minecraft:moose_antler`) is a stackable resource shed by a living [Moose](../mobs/Moose.md). The current item is not food, and possession of an Antler does not by itself establish a working equipment recipe. [Registration][item] · [Living drop][cycle]

## Obtaining

The Moose's saved antler counter supplies the verified produced route. When the counter reaches zero while it is antlered, the server drops **one Moose Antler**, removes the animal's antlered appearance and starts the regrowth counter. **No shearing, killing or jostling partner is required by this handler.** [Drop and regrowth][cycle] · [Saved counter and state][save]

The initial antlered period uses **168,000, 192,000 or 216,000 ticking game ticks**. The following antlerless period uses **48,000, 72,000 or 96,000 ticks**; the regrowth transition restores antlers but drops no item, then starts another longer counter. See [Moose antler shedding](../mobs/Moose.md#antler-shedding) for the complete cycle and its lack of an adult-only guard. These counter values are not a wall-clock or production-rate promise. [Initialization][initial] · [Tick transitions][cycle]

Moose Antler is also an ordinary Ingredients-category entry in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), available in **Survival and Creative**. Browser insertion is separate from waiting for a Moose to shed. [Category entry][tab] · [List assembly][browser-list] · [Client request][browser-client] · [Server check][browser-server]

## Usage

Collect or store Antlers. **No active bundled recipe using or producing Moose Antlers was found.** [Moose Headgear](MooseHeadgear.md) is separately registered, but that does not establish an Antler-to-Headgear crafting route. Use the actual loaded recipes rather than an upstream mod recipe. [Antler and Headgear registrations][items] · [Bundled recipes][recipes]

## Behavior

The plain item inherits the ordinary **64-item stack limit** and registers no food or special-use component. Its active drop comes directly from Moose ticking; it is not a reward for applying the held Antler to an animal. [Item registration][item] · [Default stack][default-stack] · [Drop callback][cycle]

## Notes

The checked bundled data has no default Moose death-loot table. This guide therefore treats Antler shedding and browser supply as the verified routes, rather than promising Antlers on death. Custom loot overrides or later data packs can differ. [Default loot key][loot-key] · [Missing-table fallback][loot-missing] · [Bundled loot][loot-tables]

Source-reviewed on **2026-10-02** at `25319cecd6bee492767c5b15ee53b9213d1ec1ee`. Checked item/category registration, the live timer and save/load path, and the active recipe/loot scopes. No waiting, shedding, pickup, crafting or runtime drop test was run.

Related: [Moose](../mobs/Moose.md) · [Raw Moose Ribs](RawMooseRibs.md) · [Items](Items.md)

[browser-client]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-list]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-server]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[cycle]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L206-L218
[default-stack]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[initial]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L50-L64
[item]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Items.java#L1782
[items]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Items.java#L1782-L1785
[loot-key]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-missing]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[loot-tables]: https://github.com/HungLo2020/MattMC/tree/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/loot_table
[recipes]: https://github.com/HungLo2020/MattMC/tree/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/recipe
[save]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L167-L183
[tab]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1821
