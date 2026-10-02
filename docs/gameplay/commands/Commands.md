# Commands

Commands can inspect or change a world, give items, create entities, and manage player modes. This page covers selected commands verified in MattMC's active registration code; it is not a complete reference to every command or integrated editing tool.

For commands stored in placed blocks, see [Command Blocks](../blocks/CommandBlocks.md) for Game Master permissions, trigger modes and conditional chains.

## Start with help and permissions

Use `/help` to request available command usage, or `/help give` for a specific command's syntax. The examples below are written for an in-game player source. A server console has no self-player for commands that require one.

The checked `give`, `summon`, `teleport`/`tp`, `gamemode`, `time`, `weather`, `locate`, and `gamerule` registrations require **permission level 2**. A missing command or permission error is not fixed by changing the spelling repeatedly; check the server's granted permissions and current help output.

## Selected examples

These examples describe operations; the wiki does not execute them.

| Example | Purpose and scope |
| --- | --- |
| `/give @s minecraft:torch 16` | Gives the executing player 16 Torch items |
| `/summon minecraft:grizzly_bear ~ ~ ~` | Creates one Grizzly Bear at the command position; prepare a safe test area |
| `/gamemode creative` | Changes your own mode; see the [mode guide](../gamemodes/Gamemodes.md) |
| `/tp @s ~ ~1 ~` | Moves the executing player one block upward relative to the command position; check clearance |
| `/time query daytime` | Reads daytime in the command source's level |
| `/time set day` | Sets day time to 1,000 across the server's loaded levels in this implementation |
| `/weather clear` | Changes the server Overworld weather, even when invoked from another dimension |
| `/locate structure minecraft:stronghold` | Searches for a matching structure; world settings and availability can make the search fail |
| `/locate biome minecraft:plains` | Searches for the registered biome rather than creating it |
| `/gamerule keepInventory` | Queries the current rule without a new value |

Time `set` and `add` iterate over all loaded server levels. A dimension with a fixed-time property, such as [Primordial Caves](../dimensions/PrimordialCaves.md), still has its own environmental rules; changing stored time does not promise ordinary daylight there.

## Targets and identifiers

`@s` means the executing entity. `@p` and `@a` select the nearest player and all players respectively. Before replacing a self-target with a broader selector, check that affecting more players is actually intended.

Use the exact registered ID. A display name and an upstream mod namespace can differ from MattMC's registry: the documented Grizzly Bear uses `minecraft:grizzly_bear`. A successful summon proves the entity can be created by command, not that it naturally spawns or that every imported interaction is complete.

## Reading before changing

A gamerule command without a value queries it; providing a value changes server game-rule state. For example, inspect `keepInventory` before deciding whether to set it. Weather and time commands also change shared world state, not just the executing player's view.

For experiments involving entities, portals, or block editing, use a separate test world or a recoverable world copy. This page deliberately does not present an unverified large-scale edit workflow.

## Related pages

- [Game modes](../gamemodes/Gamemodes.md)
- [Dimensions](../dimensions/Dimensions.md)
- [Content guide](../ContentGuide.md)
- [Gameplay](../Gameplay.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No commands were executed in a running world. Examples are limited to the checked syntax; permissions, features, data packs, and world configuration can affect availability.

- [Help usage](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/commands/HelpCommand.java)
- [Item giving](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/commands/GiveCommand.java)
- [Entity summoning](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/commands/SummonCommand.java)
- [Teleport syntax and alias](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/commands/TeleportCommand.java)
- [Mode permissions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/commands/GameModeCommand.java)
- [Time scope](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/commands/TimeCommand.java)
- [Overworld weather scope](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/commands/WeatherCommand.java)
- [Locating](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/commands/LocateCommand.java)
- [Game-rule queries and updates](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/commands/GameRuleCommand.java)
- [Selector parser](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/commands/arguments/selector/EntitySelectorParser.java)
- [Game-rule names](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/GameRules.java)
