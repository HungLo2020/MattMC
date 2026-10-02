# Creative

Creative grants flight, instant-build, and invulnerable player abilities. It is useful for construction, inspection, and trying registered MattMC content without first establishing a Survival acquisition route.

MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) supports requesting ordinary listed items in Survival as well as Creative. Browser insertion is separate from crafting/loot acquisition and from the additional permissions needed for operator entries.

## Using Creative for integrated content

The Creative inventory includes registered building materials, foods, and spawn eggs. For example, [Ambersol](../blocks/Ambersol.md) can be placed to inspect its lighting, while a [Grizzly Bear Spawn Egg](../items/GrizzlyBearSpawnEgg.md) places that mob directly.

Test in a separate world or a safe area. Creative makes content accessible, but does not establish that its breeding, recipes, flight controls, or natural spawning are complete. The [Subterranodon guide](../mobs/Subterranodon.md) illustrates this distinction.

## Limits

The mode sets an invulnerable ability flag; this page does not claim immunity to every command, world boundary, or exceptional damage path. Individual item/block handlers may also have custom consumption or interaction behavior, so “Creative” should not be used as evidence that every integrated item is perfectly free or lossless.

Changing your mode requires the appropriate permission; see [Game modes](Gamemodes.md#changing-mode). Use [Spectator](Spectator.md) when observation rather than editing is the goal.

## Related pages

- [Game modes](Gamemodes.md)
- [Content guide](../ContentGuide.md)
- [Blocks](../blocks/Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Rules below describe checked code, not a running-world test or every server override.

- [Mode abilities](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/GameType.java)
- [Server interactions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java)
- [Player restrictions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/player/Player.java)
- [Mode command](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/commands/GameModeCommand.java)
- [Creative categories](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
