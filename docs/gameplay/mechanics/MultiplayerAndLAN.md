# Multiplayer and LAN

Use **Multiplayer** to join a server, or **Open to LAN** to share a local world while you play in it. For creating that world and making a backup before a shared session, see [Local worlds](LocalWorlds.md). Hosting keeps the world on the host's computer, and quitting that world ends the shared session. [Joining route][title-menu] · [Hosting route][lan-menu] · [Host departure][host-disconnect]

## Join a server

From the title screen, choose **Multiplayer**. A multiplayer warning may appear before the server list. If the button is unavailable, read its displayed reason before trying to connect. [Title-screen route and gate][title-menu]

| What you have | What to do |
| --- | --- |
| A saved server entry | Select it in the list, then choose **Join Server** |
| An address to use now | Choose **Direct Connection**, enter the address, then choose **Join Server** |
| An address you want in the visible server list | Choose **Add Server**, enter a name and address, then **Done**; select the saved entry and choose **Join Server** |
| A discovered LAN world | Select its entry in the multiplayer list and choose **Join Server**; the entry supplies the discovered host address and announced port |

The server-list buttons lead to the address forms and connection callback. Adding an entry saves it to the list; it does not start a server. Direct Connection can also remember an address internally, so it should not be treated as a way to leave no connection history. [List actions][server-list] · [Direct address entry][direct-entry] · [Add Server form][add-form] · [Save and connect callbacks][join-callbacks] · [Discovered entry][lan-entry]

For a manually entered hostname or IP address, include `:port` when the host gives you a port, as in `host:port`. When no port is supplied, the address parser uses **25565**. A LAN world's automatically chosen port can differ, so use the port reported for that session instead of assuming 25565. The address form's validation only checks the address format; it does not establish that the server can be reached. [Address parsing][address-parser] · [LAN port choice][port-choice] · [Published port message][publish-message]

LAN entries depend on receiving the host's announcements. If an expected world is missing, confirm that the host has started the LAN session and is still in that world. With a known host address and the reported port, **Direct Connection** provides another connection route. Neither discovery nor typing the address guarantees a successful connection on a particular network. [LAN listener][lan-listener] · [Announcement address][lan-address] · [Connection route][join-callbacks]

## Open your local world to LAN

1. Open the local world you want to share. Consider making a backup while it is closed first; follow [Local worlds](LocalWorlds.md)
2. Open the in-game menu and choose **Open to LAN**. This option is offered when a local world is running and has not already been opened to LAN
3. Review **Game Mode** and **Allow Commands**. The screen starts with the world's default mode and saved command setting; read the effects below before changing them
4. Leave **Port Number** empty for automatic port selection, or enter an available port from **1024 through 65535**. An invalid or unavailable explicit port disables **Start LAN World** and supplies an error tooltip
5. Choose **Start LAN World**. Check chat for the success message and selected port, or a startup failure, before asking other players to connect

Automatic selection looks for a free port, but startup can still fail; the success message is the relevant result to check. Players can then try the discovered LAN entry or the address and port supplied by the host. [Menu condition][lan-menu] · [Settings and start button][lan-screen] · [Port validation][port-choice] · [Automatic selection][available-port] · [Publication and failure path][publish-server] · [Success message][publish-message]

## Understand the LAN settings

### Allow Commands

Turning **Allow Commands** on grants a shared command allowance to players in that LAN session. It is broader than enabling commands only for the host. With the option off, players may still have permission through stored operator membership, and the local-world owner may still have permission from the world's saved command setting. Choosing off does not revoke those independent permissions. See [Commands](../commands/Commands.md#start-with-help-and-permissions) for checking command access and syntax. [Publication permission update][publish-server] · [Permission checks][permission-check] · [Permission levels][permission-levels]

The LAN allowance is session state in the player list; it does not replace the world's saved **Allow Commands** setting. Ending the session removes that session allowance, but does **not** undo commands already used or other gameplay changes. Changes to the world, rules, items and players can be saved normally. Back up before experimenting, and check command targets before changing shared state. [Session allowance][permission-state] · [Saved world settings][saved-settings] · [Save path][server-save] · [Player mode saving][player-mode-save]

### Game Mode

The LAN selector offers **Survival**, **Spectator**, **Creative** and **Adventure**. Choosing a mode and starting the session does **not** immediately switch every connected player or the host into that mode. Publication records the selection; in a non-Hardcore world, the server uses it when selecting a new player's mode and when loading saved player-mode data. A joining player's saved mode can therefore be overridden. [LAN selector][lan-screen] · [Publication state][publish-server] · [Forced mode and Hardcore exception][forced-mode] · [New player][new-player-mode] · [Saved player load][player-mode-load] · [Mode selection][mode-selection]

Actual player modes are saved separately. A mode applied during a LAN session can be saved with that player, so ending LAN hosting is not a general reset to everyone's previous mode. Hardcore bypasses this LAN forced-mode override; choosing a LAN mode does not remove the world's Hardcore rules. Use [Game modes](../gamemodes/Gamemodes.md) for the four modes and permitted mode changes, and [Death and respawn](DeathAndRespawn.md#special-cases) for the Hardcore death case. [Hardcore exception][forced-mode] · [Player mode saving][player-mode-save]

## Keep the host world running

**Opening to LAN stops the ordinary menu from pausing the world**, even if no guest has joined yet. Get to a safe place before leaving the game in a menu. Guests also cannot pause a shared world with their own menu. See [Movement and controls](Movement.md) and [Inventory controls](InventoryControls.md) for play controls. [Client pause condition][pause-condition] · [Integrated-server pause state][server-pause]

When the host chooses **Save and Quit to Title**, the local server shuts down, saves players and world data, and disconnects the shared session. Give other players time to finish what they are doing before leaving. This is the normal save-and-close path, not a rollback of the session. [Quit button][lan-menu] · [Button label][quit-label] · [Client waits for shutdown][client-quit] · [Host departure][host-disconnect] · [Server save and shutdown][server-shutdown]

The checked menu has no in-session control to close LAN sharing while keeping that world running: after publication, **Open to LAN** is replaced by another menu action. Save and quit, then reopen the world to start a fresh local session. Its LAN publication state begins closed; saved gameplay changes and independent command permissions still matter. [Published menu branch][lan-menu] · [Fresh local server][fresh-local-server] · [Initial publication state][initial-publication] · [Saved settings][saved-settings]

For operating a separate host process, see [Dedicated servers](DedicatedServers.md).

## Sources and verification

Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`. The review followed the active menu buttons, address forms and callbacks, LAN publication, permission checks, player-mode load/save paths, pause conditions and shutdown. Button names use the bundled English language resources after namespace merging and the shipped translation-renaming pass; this produces **Allow Commands**. Resource packs and other languages can change displayed text. [Language merge][language-merge] · [Translation renames][language-renames] · [Rename implementation][rename-implementation]

No running-client UI, LAN discovery, local or remote connection, authentication, protocol/version compatibility, resource-pack compatibility or firewall behavior was tested. A successful publication message does not establish internet reachability. This guide covers the checked player menus; it does not establish a server download, server packaging or router-configuration procedure.

Related: [Local worlds](LocalWorlds.md) · [Commands](../commands/Commands.md) · [Game modes](../gamemodes/Gamemodes.md) · [Mechanics](Mechanics.md) · [Gameplay](../Gameplay.md)

[title-menu]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/TitleScreen.java#L211-L246
[server-list]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/multiplayer/JoinMultiplayerScreen.java#L52-L86
[direct-entry]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/DirectJoinServerScreen.java#L43-L89
[add-form]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/ManageServerScreen.java#L35-L97
[join-callbacks]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/multiplayer/JoinMultiplayerScreen.java#L183-L229
[lan-entry]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/multiplayer/ServerSelectionList.java#L225-L228
[address-parser]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/multiplayer/resolver/ServerAddress.java#L36-L62
[lan-listener]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/server/LanServerDetection.java#L30-L58
[lan-address]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/server/LanServerDetection.java#L86-L104
[lan-menu]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/PauseScreen.java#L88-L106
[lan-screen]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/ShareToLanScreen.java#L41-L84
[port-choice]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/ShareToLanScreen.java#L92-L109
[available-port]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/util/HttpUtil.java#L253-L275
[publish-server]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/server/IntegratedServer.java#L181-L202
[publish-message]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/PublishCommand.java#L72-L75
[permission-check]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/players/PlayerList.java#L626-L630
[permission-levels]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L1960-L1974
[permission-state]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/players/PlayerList.java#L748-L750
[saved-settings]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/storage/PrimaryLevelData.java#L234-L250
[server-save]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L700-L708
[forced-mode]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/server/IntegratedServer.java#L265-L267
[new-player-mode]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/level/ServerPlayer.java#L376-L384
[player-mode-load]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/level/ServerPlayer.java#L395-L420
[mode-selection]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1980-L1991
[player-mode-save]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1994-L1997
[pause-condition]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L1431-L1435
[server-pause]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/server/IntegratedServer.java#L88-L108
[quit-label]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/network/chat/CommonComponents.java#L52-L54
[client-quit]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L2224-L2300
[host-disconnect]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerCommonPacketListenerImpl.java#L66-L71
[server-shutdown]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L733-L779
[initial-publication]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/server/IntegratedServer.java#L47-L57
[language-merge]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/resources/language/ClientLanguage.java#L35-L56
[language-renames]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/lang/deprecated.json
[rename-implementation]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/locale/DeprecatedTranslationsInfo.java#L73-L90
[fresh-local-server]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L2165-L2181
