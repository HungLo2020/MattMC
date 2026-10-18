# Dedicated servers and world files

Use a dedicated server when the world should run in its own process. For sharing a world from the client or joining a host, follow [Multiplayer and LAN](MultiplayerAndLAN.md). This guide covers the current launch routes, finding server files, and saving and stopping safely. [Dedicated launch][dev-launch]

## Choose an existing launch route

- **Prepared development checkout:** from the repository root, use `./gradlew runServer`, or `gradlew.bat runServer` on Windows. It opens the headless dedicated server with console input. Project preparation belongs in the [README Quick Start][quick-start]; `DevUtils/RunDev.py` launches the client. [Development task][dev-launch] · [RunDev target][run-dev]
- **Installed or exported game:** its server launchers are `MattMC/run/server/run-server.sh` and `MattMC/run/server/run-server.bat`. They start the headless server and depend on the bundled JDK in `MattMC/run/jdk/` and the libraries and natives in the same MattMC folder. Keep that folder together. The scripts do not forward extra arguments supplied after their names. [Distribution layout][dist-layout] · [Launcher placement][dist-scripts] · [Shell launcher][shell-launch] · [Windows launcher][windows-launch]

These routes are verified from source definitions and, for the installed layout, one built distribution. The Java-based packaging is transitional. The [architecture target](../../development/PROJECT-ARCHITECTURE.md) is one Rust executable supporting client and dedicated-server modes, at most one separately loaded Rust library, and no Java. [Target definition][architecture]

## Find the actual world and configuration

The working directory determines where `server.properties` and the default world are opened. With unchanged launchers and the default `level-name=world`, the source resolves these locations:

| Launch route | Server properties | World folder |
| --- | --- | --- |
| Development `runServer` | `<repository>/run/server.properties` | `<repository>/run/world` |
| Installed server script | `<MattMC>/run/server/server.properties` | `<MattMC>/run/server/world` |

[Development directory][dev-launch] · [Shell directory][shell-directory] · [Windows directory][windows-directory] · [Settings and world selection][main-paths] · [Default name][properties] · [World path resolution][world-path]

Because the server lives inside `MattMC/run/`, game updates keep its world and settings (they only replace the launchers and server README there), and Sync Up/Down carries it along with the rest of `run/`. Older installs kept the server in `MattMC/server/`; move that folder's contents into `MattMC/run/server/` yourself to keep using that world. [Packaging README][packaging-readme] · [Shell directory][shell-directory] · [Windows directory][windows-directory]

A custom launch can change the world base with `--universe` and select a world with `--world`; otherwise `level-name` selects it. The default base is the working directory (`.`). Those world options do not move `server.properties`. The exported scripts do not expose argument forwarding, so appending those options to a script is not a supported recipe here. [World options][world-options] · [Settings and selection][main-paths] · [Shell launch arguments][shell-launch] · [Windows launch arguments][windows-launch]

`ops.json`, `whitelist.json`, `banned-players.json` and `banned-ips.json` also belong to the working directory. A world-folder copy is not automatically a backup of these files and `server.properties`; identify your actual paths before planning a separate backup. [Access-list paths][list-paths]

## Understand settings and console permissions

Useful `server.properties` defaults include `level-name=world`, `server-port=25565`, `gamemode=survival`, `difficulty=easy` and `max-players=20`. The port is a server setting, not evidence that a guest can reach it. Use [Multiplayer and LAN](MultiplayerAndLAN.md#join-a-server) for the player's joining controls. [Property defaults][properties] · [Player-limit default][player-limit]

The dedicated console accepts commands such as `help` and has permission level **4**. It is not a player, so commands that need a player target may need one supplied explicitly. See [Commands](../commands/Commands.md#start-with-help-and-permissions) for general syntax and help. `stop`, `save-all`, `save-off` and `save-on` each require level 4. A player's operator membership and stored permission level govern access; Creative mode and allowlist membership do not grant these commands. [Console input][console-input] · [Console dispatch][console-dispatch] · [Console source][console-source] · [Player permission][player-permission] · [Stop][stop] · [Save all][save-all] · [Save off][save-off] · [Save on][save-on]

## Know the identity limitation

**The reviewed dedicated login path uses offline profiles derived from the supplied player name. It does not verify ownership of an online account.** `online-mode` defaults to `true`, but the key-exchange path still constructs an offline profile. Encryption protects that connection's traffic; it does not establish who owns the name. [Offline services][offline-services] · [Login branches][login-branches] · [Encryption and profile creation][login-key] · [Name-derived profile][offline-uuid] · [Property default][properties]

Allowlist, ban, IP-ban and player-limit checks still apply. Operator authorization is a separate check, and operators can pass the allowlist gate. These checks apply to the profile this implementation supplies; an allowed connection is not proof of online-account identity. [Admission checks][admission] · [Allowlist gate][allowlist] · [Operator permission][player-permission]

## Save and stop normally

Use the dedicated console's `stop` command for normal shutdown. Give players time to finish first, then wait for the server process to finish and inspect its output for saving or closing errors. The initial stopping response is only the start of shutdown. The normal path saves players and worlds, closes them and releases storage access. [Stop command][stop] · [Shutdown cleanup][shutdown] · [Shutdown completion][shutdown-finish]

For any separate external backup copy, first complete that normal shutdown. A `session.lock` file can remain after its operating-system lock is released, so its presence or absence is not a shutdown-completion test. This guide does not provide a live-copy or restoration procedure. For the client's closed-world **Edit → Make Backup** workflow, use [Local worlds](LocalWorlds.md#back-up-a-closed-world). [Lock release][lock-release]

`save-all` requests a save; `save-all flush` also requests flushing. Neither creates a backup archive. `save-off` disables per-level automatic saving, and `save-on` restores it, but `save-off` does not stop gameplay or every writer: player data and world metadata can still be saved. Normal shutdown also turns world saving back on. Do not treat `save-off` as a safe-copy barrier or a way to discard a session's changes. [Save command][save-all] · [Save path][saving] · [Save toggles][save-toggle] · [Player disconnect saving][player-save] · [Shutdown saves][shutdown]

For view/simulation distance and the empty-server pause setting, see [Ticks and chunk activity](TicksAndChunkActivity.md#forced-chunks-and-paused-servers).

## Sources and verification

Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`. The server location moved to `run/server/` on **2026-10-17**; that layout was checked by building the distribution and starting its server launcher, which created `server.properties` and `world/` in `run/server/`. The architecture target is cited at documentation commit `d723254d406f300bdcf2579d39dfd5a433e43869`. Command names, property keys and launcher filenames retain their source spelling.

Verification did not launch a game/server, build or export a game distribution, or execute an administrative server command. No built archive, connection, successful disk write, backup copy, restoration or crash recovery was tested. This page establishes source-derived behavior, not a public-hosting or security configuration guide.

Related: [Local worlds](LocalWorlds.md) · [Multiplayer and LAN](MultiplayerAndLAN.md) · [Commands](../commands/Commands.md) · [Mechanics](Mechanics.md) · [Gameplay](../Gameplay.md)

[quick-start]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/README.md#L21-L24
[dev-launch]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/build.gradle#L863-L889
[run-dev]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/DevUtils/RunDev.py#L126-L142
[dist-layout]: https://github.com/HungLo2020/MattMC/blob/master/build.gradle
[dist-scripts]: https://github.com/HungLo2020/MattMC/blob/master/build.gradle
[shell-directory]: https://github.com/HungLo2020/MattMC/blob/master/packaging/run-server.sh
[windows-directory]: https://github.com/HungLo2020/MattMC/blob/master/packaging/run-server.bat
[shell-launch]: https://github.com/HungLo2020/MattMC/blob/master/packaging/run-server.sh
[windows-launch]: https://github.com/HungLo2020/MattMC/blob/master/packaging/run-server.bat
[architecture]: https://github.com/HungLo2020/MattMC/blob/d723254d406f300bdcf2579d39dfd5a433e43869/docs/development/PROJECT-ARCHITECTURE.md#L1-L5
[world-options]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/Main.java#L130-L132
[main-paths]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/Main.java#L158-L171
[properties]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/dedicated/DedicatedServerProperties.java#L54-L70
[player-limit]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/dedicated/DedicatedServerProperties.java#L85-L95
[world-path]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/storage/LevelStorageSource.java#L363-L383
[packaging-readme]: https://github.com/HungLo2020/MattMC/blob/master/packaging/SERVER-README.md
[list-paths]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/players/PlayerList.java#L101-L105
[console-input]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/dedicated/DedicatedServer.java#L202-L218
[console-dispatch]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/dedicated/DedicatedServer.java#L464-L479
[console-source]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L1855-L1868
[player-permission]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L1960-L1974
[stop]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/StopCommand.java#L8-L16
[save-all]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/SaveAllCommand.java#L14-L31
[save-off]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/SaveOffCommand.java#L12-L22
[save-on]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/SaveOnCommand.java#L12-L22
[offline-services]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/Services.java#L10-L38
[login-branches]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerLoginPacketListenerImpl.java#L112-L128
[login-key]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerLoginPacketListenerImpl.java#L164-L196
[offline-uuid]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/core/UUIDUtil.java#L86-L93
[admission]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/players/PlayerList.java#L382-L407
[allowlist]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/dedicated/DedicatedPlayerList.java#L100-L103
[shutdown]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L733-L783
[shutdown-finish]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L893-L907
[lock-release]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/util/DirectoryLock.java#L20-L60
[saving]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L675-L710
[save-toggle]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L2319-L2339
[player-save]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/players/PlayerList.java#L327-L343
