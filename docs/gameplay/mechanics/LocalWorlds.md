# Local worlds, saving, and backups

Use **Singleplayer** to create or reopen a world stored on your computer. Normal saving preserves your latest progress; a separate backup archive preserves a copy made at that point. For inviting other players into a local world, see [Multiplayer and LAN](MultiplayerAndLAN.md). [Title-screen route][title]

## Create a local world

From the title screen, choose **Singleplayer → Create New World**. Review the three tabs, then use **Create New World** to start. [World-list actions][selection] · [Creation tabs and button][tabs]

| Tab | What to choose |
| --- | --- |
| **Game** | World name, **Game Mode**, **Difficulty**, and **Allow Commands** |
| **World** | **World Type**, customization where the selected type supports it, the generator seed, **Generate Structures**, and **Bonus Chest** |
| **More** | **Game Rules**, **Experiments**, and **Data Packs** |

For an ordinary fresh world, the starting choices are **Survival**, **Normal** difficulty, the **Default** world type, a random seed, structures on, and bonus chest off. Leave the seed field empty for a random seed, or enter a seed before creating the world. Special world types and data choices can change which controls are available. [Game controls][game-tab] · [World controls][world-tab] · [More controls][more-tab] · [Fresh preset][fresh] · [Initial mode][initial] · [Initial settings][initial-settings] · [World defaults and seed][world-options]

## Choose modes, difficulty, and commands

The ordinary creation screen offers **Survival**, **Hardcore**, and **Creative**. Read [Game modes](../gamemodes/Gamemodes.md) for the four player modes and their abilities. **Hardcore is a Survival world with the Hardcore setting enabled**, rather than an additional player mode. At creation it forces **Hard** difficulty, turns off **Allow Commands**, and disables the bonus chest. See [Death and respawn](DeathAndRespawn.md#special-cases) before choosing it. [Mode choices][game-tab] · [Hardcore and command gates][gates] · [Bonus-chest gate][bonus-gate] · [Hardcore mode mapping][mode-map] · [Created settings][created-settings]

For an ordinary non-Hardcore world, **Allow Commands** starts off in Survival and on in Creative until you explicitly choose its value. An explicit choice is retained when you switch between those modes. The saved command setting gives the local owner command access; individual commands still have permission requirements. Creative abilities and command permission are separate choices. See [Commands](../commands/Commands.md#start-with-help-and-permissions) for help and permission limits, and [Multiplayer and LAN](MultiplayerAndLAN.md) for the separate sharing-session controls. [Command choice][gates] · [Owner command access][owner-commands] · [Saved world settings][saved]

While playing a local world, open **Options...** from the pause menu to change difficulty or use its lock control. Confirming the lock disables further changes through those menu controls. Difficulty and the lock are saved with the world; Hardcore keeps its difficulty control disabled. The server accepts these setting requests from the local owner or a player with permission level 2. An authorized `/difficulty` command can still change a locked ordinary world's difficulty; Hardcore remains Hard. [Options route][pause-options] · [Difficulty controls][difficulty-ui] · [Server permissions][difficulty-packets] · [Saved settings][saved] · [Difficulty command][difficulty-command] · [Lock and Hardcore enforcement][difficulty-setter]

## Save and close a world

Use the pause menu's **Save and Quit to Title**, then let the saving screen finish. The normal close path saves players and world chunks, closes the world, and releases its storage access before returning to the title screen. This preserves the current state; it does not undo changes or make a separate backup archive. [Quit button][quit-button] · [Local quit label][quit-label] · [Saving screen and shutdown wait][quit] · [Owner disconnect][owner-quit] · [Shutdown saves][shutdown]

The ordinary pause menu pauses a local world that has not been opened to LAN. Once hosting starts, that menu no longer pauses the world. Pausing can trigger a save, but it is not a backup operation. [Pause condition][pause] · [Save on pausing][pause-save]

## Back up a closed world

Before changes you may want to revisit, close the world normally and make a separate archive:

1. Open **Singleplayer** and select the saved world without entering it
2. Choose **Edit → Make Backup**
3. Wait for the backup-created notification; use **Edit → Open Backups Folder** to locate the archive

The Edit route must first obtain access to the selected save. A world-access error can prevent it from opening. The backup action writes a uniquely named, timestamped ZIP to the configured backups folder. Its contents sit under the world's save-folder name; the active-session lock file is excluded. [Selected-world access][edit-access] · [Backup and folder buttons][edit-buttons] · [Archive creation][backup]

The current client uses **saves** and **backups** inside its configured game directory. **Open World Folder** opens the selected save, while **Open Backups Folder** opens the archive directory. Use those controls to find the actual locations for your installation. Renaming the world in the list does not change its save-folder name. [Game directory][game-dir] · [Storage paths][storage-paths] · [Folder buttons][edit-buttons] · [Rename storage][rename-storage]

A completed backup operation reports the archive's size. A backup or world-access error means you should not rely on that attempt. A ZIP file appearing by itself does not prove completion, and not every directory or write failure reaches the backup notification. [Backup result handling][backup-result] · [Archive creation and failure paths][backup]

## Rename or re-create a world

To change the name shown in the world list, select the closed world, choose **Edit**, enter the name, and choose **Save**. This changes its display name only; it neither renames the save directory nor creates a second copy. [Edit name and Save][edit-buttons] · [Rename action][rename] · [Name metadata][rename-storage]

**Re-Create** opens a new creation screen using the selected world's saved settings, generation information, and data packs. Review its choices before creating: special modes and older customized worlds can take different paths. Completing creation makes a new world in a new save directory. It does not copy the old world's builds, explored chunks, or player inventory, and it is not a backup-restoration action. [Re-Create route][recreate] · [Read saved settings][recreate-data] · [Populate creation screen][recreate-ui] · [New world data and directory][new-world] · [Data-pack copy][copy-packs] · [Available directory name][folder-name]

For pack selection during creation and changes to loaded data, see [World data packs](WorldDataPacks.md).

For the creation rule editor and later queries or changes, see [Game rules](GameRules.md).

## Sources and verification

Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`. The review followed active menu actions through creation, saved settings, permissions, normal shutdown, renaming, archive writing, and re-creation. English labels were checked across the four bundled language namespaces and their deprecation transform; the effective label is **Allow Commands**. External language packs can change labels. [Language loading][language] · [Label migration][label-migration] · [Bundled rename mapping][deprecated-labels]

No game was launched and no world was created, saved, backed up, restored, or re-created during verification. Source review does not establish successful disk writes, crash recovery, or archive recoverability. Restore procedures, corruption repair, optimization, and external pack installation are outside this guide.

Related: [Mechanics](Mechanics.md) · [Game modes](../gamemodes/Gamemodes.md) · [Death and respawn](DeathAndRespawn.md) · [Commands](../commands/Commands.md) · [Gameplay](../Gameplay.md)

[title]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/TitleScreen.java#L211-L216
[selection]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/SelectWorldScreen.java#L93-L124
[tabs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L253-L266
[game-tab]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L672-L734
[world-tab]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L789-L850
[more-tab]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L748-L785
[fresh]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L135-L142
[initial]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldCreationContext.java#L30-L43
[initial-settings]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldCreationUiState.java#L34-L63
[world-options]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/levelgen/WorldOptions.java#L31-L88
[gates]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldCreationUiState.java#L120-L160
[bonus-gate]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldCreationUiState.java#L176-L183
[mode-map]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldCreationUiState.java#L291-L304
[created-settings]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L329-L345
[owner-commands]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/players/PlayerList.java#L626-L630
[saved]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/storage/PrimaryLevelData.java#L232-L250
[pause-options]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/PauseScreen.java#L88-L93
[difficulty-ui]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/options/OptionsScreen.java#L147-L203
[difficulty-packets]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2093-L2126
[difficulty-command]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/DifficultyCommand.java#L18-L40
[difficulty-setter]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L1445-L1465
[quit-button]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/PauseScreen.java#L95-L106
[quit-label]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/network/chat/CommonComponents.java#L48-L57
[quit]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L2224-L2300
[owner-quit]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerCommonPacketListenerImpl.java#L66-L71
[shutdown]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L733-L779
[pause]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L1431-L1435
[pause-save]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/server/IntegratedServer.java#L92-L105
[edit-access]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldSelectionList.java#L669-L701
[edit-buttons]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/EditWorldScreen.java#L67-L110
[backup]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/storage/LevelStorageSource.java#L619-L660
[game-dir]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L414-L419
[storage-paths]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L533
[rename-storage]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/storage/LevelStorageSource.java#L601-L616
[backup-result]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/EditWorldScreen.java#L159-L178
[rename]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/EditWorldScreen.java#L148-L156
[recreate]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldSelectionList.java#L704-L734
[recreate-data]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldOpenFlows.java#L165-L205
[recreate-ui]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L208-L230
[new-world]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L291-L325
[copy-packs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L564-L578
[folder-name]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldCreationUiState.java#L92-L109
[language]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/resources/language/ClientLanguage.java#L35-L56
[label-migration]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/locale/DeprecatedTranslationsInfo.java#L73-L90
[deprecated-labels]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/lang/deprecated.json
