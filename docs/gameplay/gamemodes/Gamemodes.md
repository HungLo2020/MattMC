# Game modes

MattMC defines four player game modes. They change building, flight, and interaction abilities; they are separate from world difficulty and individual game rules.

| Mode | Typical use | Key distinction |
| --- | --- | --- |
| [Survival](Survival.md) | Gathering, building, and managing danger | Normal resource and vulnerability rules |
| [Creative](Creative.md) | Building and content testing | Flight and instant-build abilities |
| [Adventure](Adventure.md) | Maps with controlled block interaction | Block actions depend on allowed item predicates |
| [Spectator](Spectator.md) | Observation | Forced flight, no ordinary block editing |

## Changing mode

With permission level 2, `/gamemode survival`, `/gamemode creative`, `/gamemode adventure`, or `/gamemode spectator` changes your own mode. The command also accepts an optional player target, for example `/gamemode survival PlayerName`.

These are command examples, not actions performed by this wiki. Multiplayer permissions still apply. Switching mode does not repair an incomplete imported mob, add a missing recipe, or establish natural spawning for Creative-only content.

## Starting points

- [Survival](Survival.md)
- [Creative](Creative.md)
- [Adventure](Adventure.md)
- [Spectator](Spectator.md)
- [Hunger and food](../mechanics/Hunger.md)
- [Gameplay](../Gameplay.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Rules below describe checked code, not a running-world test or every server override.

- [Mode abilities](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/GameType.java)
- [Server interactions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java)
- [Player restrictions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/player/Player.java)
- [Mode command](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/commands/GameModeCommand.java)
