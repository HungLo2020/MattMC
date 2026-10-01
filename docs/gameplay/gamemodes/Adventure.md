# Adventure

Adventure is intended for maps that restrict how players alter blocks. It retains the ordinary non-Creative ability setup, but disables the general building permission.

## Block interactions

The player block-action check allows breaking only when the held item has an Adventure-mode predicate permitting that target, unless a separate building ability grants an exception. Placement similarly checks whether the item can be placed on the relevant supporting block.

A tool's usual mining suitability and an Adventure permission predicate are different checks. Having a pickaxe does not automatically authorize breaking every stone block in an Adventure map.

## Playing a map

Follow the map's supplied tools and interactions. If a block cannot be broken, that may be an intentional map rule rather than a broken tool. Ordinary UI interactions and item use are not all replaced by a blanket “no interaction” rule.

Adventure is also not Spectator: it does not grant flight or the invulnerable ability. The game-type helper treats Survival and Adventure as survival-like modes, so do not expect Creative resource conveniences.

## Related pages

- [Game modes and mode command](Gamemodes.md)
- [Survival](Survival.md)
- [Spectator](Spectator.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Rules below describe checked code, not a running-world test or every server override.

- [Mode abilities](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/GameType.java)
- [Server interactions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java)
- [Player restrictions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/player/Player.java)
- [Mode command](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/commands/GameModeCommand.java)
