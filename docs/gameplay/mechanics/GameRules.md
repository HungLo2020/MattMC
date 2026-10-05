# Game rules

Choose game rules during world creation, or inspect and change them with `/gamerule` in a running world. Before changing a rule, query it and record the value if you may want to restore it later. Rules affect the shared world, so agree on changes with the people playing there. [Query and change][command] · [Shared rules][shared-rules]

## Choose rules before creation

In the creation screen, open **More → Game Rules**. The **Edit Game Rules** screen works on a copy of the current creation settings. See [Local worlds](LocalWorlds.md#create-a-local-world) for the wider creation workflow. [Open editor][open-editor]

- Toggle boolean rules on or off, or enter an integer for numeric rules
- Invalid integer input turns red and disables **Done** until all entries are valid
- **Done** accepts the edited copy into the creation settings; **Cancel** or **Escape** discards that editor's changes

[Boolean controls][boolean-editor] · [Integer validation][integer-editor] · [Done and Cancel][editor-exit] · [Escape handling][escape] · [Close-on-Escape setting][close-on-escape]

The tooltip labelled **Default: …** shows the value captured from the editor's current copy when that entry was built. It is not a factory-reset control or a reliable reference for the rule's registered default. [Tooltip value][tooltip]

## Inspect or change a loaded world

Both querying and changing a rule require **permission level 2 or higher**. See [Commands](../commands/Commands.md#start-with-help-and-permissions) for help and permission limits, and [Allow Commands](LocalWorlds.md#choose-modes-difficulty-and-commands) for local-world access. [Command permission and syntax][command]

- `/gamerule <rule>` reports the current value
- `/gamerule <rule> <value>` changes it; query again to check the running value
- Boolean values use `true` or `false`; integer rules take integers, with bounds that depend on the rule

Do not assume all integers must be nonnegative or all percentage rules are restricted to 0–100. For example, `minecartMaxSpeed` accepts 1–1000 and is available only with the `minecart_improvements` feature enabled; [Transport](Transport.md#why-another-railway-design-may-behave-differently) owns the experimental movement details. [Boolean arguments][boolean-type] · [Integer arguments][integer-type] · [Speed rule and feature requirement][minecart-rule] · [Available-rule filtering][available-rules]

## Two query-and-change examples

These are commands to enter yourself if you want the described change. The defaults below are reference values, not a record of your world's current settings.

### Keep inventory on death

1. Run `/gamerule keepInventory` and note the reported value
2. To keep ordinary inventory and experience through death and respawn, run `/gamerule keepInventory true`
3. Run `/gamerule keepInventory` again to confirm `true`

The registered default is `false`. See [Death and respawn](DeathAndRespawn.md#what-you-keep-and-lose) for item drops, experience, curses, and exceptions. [Default][inventory-default] · [Death dispatch][death-dispatch] · [Death drop gate][inventory-drops] · [Respawn restoration][inventory-restore]

### Change the sleep threshold

1. Run `/gamerule playersSleepingPercentage` and note the reported value
2. To set the sleep threshold to 50%, run `/gamerule playersSleepingPercentage 50`
3. Run `/gamerule playersSleepingPercentage` again to confirm `50`

The registered default is `100`. The sleep check rounds the required count up and still requires at least one sleeper. See [Time, weather, and sleep](TimeWeatherAndSleep.md#coordinate-multiplayer-sleep) for which players count, deep sleep, and the separate time/weather consequences. [Default][sleep-default] · [Active sleep check][sleep-check] · [Required count][sleep-count]

## Current values, scope, and saving

A saved world's current rules can differ from registered defaults. Loading reads its saved values, creation options can alter starting values, and **Re-Create** copies saved rules into the new creation settings. Query the loaded world instead of treating a listed default as the value to restore. See [Local worlds](LocalWorlds.md#rename-or-re-create-a-world) for what re-creation copies. [Initial rule values][load-rules] · [Saved-value loading][read-rules] · [Creation options][creation-options] · [Debug-world setup][debug-options] · [Re-created rules][recreate-rules]

The Overworld and the other dimensions normally created with that world share the same rule values. A rule is not a per-player preference; its effects can still depend on the mechanic and dimension. [Command rule source][server-rules] · [Other-dimension setup][dimension-setup] · [Shared rules][shared-rules]

A successful change updates the running rule state. When its effects become visible depends on the mechanic's checks and callbacks. Updated values are persisted through successful normal world saves, rather than being written to disk by the `/gamerule` command itself. Follow [normal saving and closing](LocalWorlds.md#save-and-close-a-world), and use a [separate backup](LocalWorlds.md#back-up-a-closed-world) before changes you may want to revisit. [Change path][command] · [Value and callback update][change-callback] · [Rule serialization][serialize] · [Saved world data][saved-data] · [Normal save][save-world] · [Metadata write][save-data]

## Sources and verification

Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`. The review followed creation controls, command registration and permissions, rule storage, and the death and sleep consumers cited above. Direction labels were checked against the bundled English language files and their deprecation transform; other languages or external packs can change labels.

No game was launched, no commands were executed in a world, and no world settings were changed. No save/reload, disk-write, or cross-dimension runtime test was run. This is a controls guide, not a complete rule catalog or a verification of every rule's gameplay effect.

Related: [Commands](../commands/Commands.md) · [Local worlds](LocalWorlds.md) · [Natural spawning](NaturalSpawning.md) · [Health](Health.md) · [Hunger](Hunger.md) · [Fire](../blocks/Fire.md) · [Mechanics](Mechanics.md)

[open-editor]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L748-L785
[boolean-editor]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/EditGameRulesScreen.java#L106-L117
[integer-editor]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/EditGameRulesScreen.java#L199-L216
[editor-exit]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/EditGameRulesScreen.java#L64-L103
[escape]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/Screen.java#L111-L115
[close-on-escape]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/Screen.java#L181-L183
[tooltip]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/EditGameRulesScreen.java#L260-L277
[command]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/GameRuleCommand.java#L13-L43
[boolean-type]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/GameRules.java#L365-L404
[integer-type]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/GameRules.java#L475-L543
[minecart-rule]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/GameRules.java#L209-L213
[available-rules]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/GameRules.java#L332-L339
[inventory-default]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/GameRules.java#L50-L52
[death-dispatch]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/level/ServerPlayer.java#L905-L907
[inventory-drops]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/entity/player/Player.java#L587-L603
[inventory-restore]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1574-L1583
[sleep-default]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/GameRules.java#L179-L181
[sleep-check]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/level/ServerLevel.java#L337-L347
[sleep-count]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/players/SleepStatus.java#L12-L23
[load-rules]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/GameRules.java#L276-L294
[read-rules]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/GameRules.java#L310-L329
[creation-options]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldCreationUiState.java#L57-L64
[debug-options]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L329-L344
[recreate-rules]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L216-L220
[server-rules]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L1920-L1922
[dimension-setup]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L458-L466
[shared-rules]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/storage/DerivedLevelData.java#L129-L132
[change-callback]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/GameRules.java#L658-L669
[serialize]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/GameRules.java#L310-L318
[saved-data]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/storage/PrimaryLevelData.java#L245-L250
[save-world]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L675-L688
[save-data]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L700-L710
