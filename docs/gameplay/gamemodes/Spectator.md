# Spectator

Spectator is an observation mode. The game-type setup grants flight, enables flying immediately, sets the invulnerable ability flag, and disables ordinary building. The player tick also enables no-physics movement for spectators.

## Interactions

Server-side ordinary item use returns without using the item. Block interaction can still open a menu provider for viewing, while ordinary block actions are restricted. Do not interpret a visible container menu as permission to edit the world or as Creative mode.

The precise behavior of every custom menu or integrated subsystem has not been tested here. This guide describes the core player and server interaction paths rather than promising every mod-derived feature handles spectators identically.

## When to use it

Use Spectator to inspect spaces without the ordinary editing workflow. Use [Creative](Creative.md) when you need to place blocks or spawn entities. Mode changes require permission; see the [mode command](Gamemodes.md#changing-mode).

The invulnerable ability flag is not a claim that every exceptional command or damage source is ignored.

## Related pages

- [Game modes](Gamemodes.md)
- [Creative](Creative.md)
- [Adventure](Adventure.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Rules below describe checked code, not a running-world test or every server override.

- [Mode abilities](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/GameType.java)
- [Server interactions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java)
- [Player restrictions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/player/Player.java)
- [Mode command](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/commands/GameModeCommand.java)
