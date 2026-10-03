# Bedrock

**`minecraft:bedrock`** is a solid world-boundary block. Ordinary Survival mining cannot remove it or obtain an item from it, including with Silk Touch. Creative players can request its item through the current [inventory browser](../mechanics/InventoryBrowser.md). Plan before placing it in a Survival build: access to the item does not grant the ability to mine it back. [Registration][bedrock-reg] · [Mining rule][defaults] · [Browser requests][client-add] · [Server acceptance][server-add]

## Finding natural Bedrock

The normal world preset uses the bundled Overworld and Nether noise settings. Their active surface-rule path places Bedrock along the Overworld bottom and the Nether bottom/roof using vertical gradients. The layers can be uneven; this is not a uniform five-block-thick floor or a resource vein. Custom presets and data packs can change the configuration. [Normal preset][normal] · [Overworld floor][overworld-settings] · [Nether floor and roof][nether-settings] · [Active surface application][surface]

## Obtaining an inventory item

The registered Bedrock item belongs to **Natural Blocks**, without the operator-category gate. MattMC's current [inventory browser](../mechanics/InventoryBrowser.md) lists it in both Survival and Creative, but insertion is a Creative acquisition route. Search for Bedrock in the ordinary inventory's right-hand panel and follow that guide for cursor, capacity, and screen-layout limits. [Item registration][items] · [Category][natural-title] [natural-tab] · [Active item list][jei]

This **Creative inventory-spawning route** is separate from natural harvesting. The route does not give a Survival player the ability to mine placed Bedrock back. [Client request][client-add] · [Server acceptance][server-add]

With permission level 2, `/give @s minecraft:bedrock 1` is another player-inventory route. No Bedrock crafting recipe was found in the checked bundled recipe tree. [Give command][give]

## Placement, breaking, and light

Bedrock uses ordinary full-block placement and collision. It has no facing, waterlogged, powered, or age property, no support requirement, and emits no light. Its opaque default shape blocks light. [Light blocking][light-blocking] Hardness **−1** makes ordinary mining progress zero; its registered explosion resistance is **3,600,000**. That resistance is not a claim that all commands, world edits, or unusual removal mechanisms are impossible. [Registration][bedrock-reg] · [Shape and mining defaults][defaults] · [Property defaults][properties]

It is not a Game Master block, so Creative players can break it under the usual world-action restrictions without the additional Game Master permission check. It has **no loot table**: mining/removal does not turn natural Bedrock into a harvestable material, and Fortune or Silk Touch does not create a drop. It also disables the ordinary valid-spawn predicate on its top. [Registration][bedrock-reg] · [Server breaking][break]

## Building example

For a visible boundary in a disposable Creative arena, place a short Bedrock wall and check its route before extending it. Keep normal walkways and exits made from removable materials. For an invisible wall use [Barrier](TechnicalBlocks.md#barrier); for empty space use [Air](TechnicalBlocks.md#air).

## Related pages

- [Soil, sand, ice, and fluid catalog](catalog/terrain.md)
- [Bedrock inventory entry](../items/Bedrock.md)
- [Game modes](../gamemodes/Gamemodes.md)
- [Placed blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on **2026-10-02**. These are source-based instructions, not in-game verification. No game commands, server changes, saves, loads, generation, or tests were executed. Availability and results can change with data packs and server configuration.

[bedrock-reg]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L276-L284
[defaults]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L321-L353
[client-add]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[server-add]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1995
[normal]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[overworld-settings]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L443-L465
[nether-settings]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/noise_settings/nether.json#L104-L149
[surface]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L248-L281
[items]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java
[natural-title]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L753-L759
[jei]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L109
[give]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/commands/GiveCommand.java
[light-blocking]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L296-L303
[properties]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1024
[break]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L300
[natural-tab]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1021-L1028
