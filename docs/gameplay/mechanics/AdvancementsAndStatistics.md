# Advancements and statistics

Use **Advancements** to browse goals and check your progress. Use **Statistics** to inspect recorded activity, such as crafted items, travel and mob kills. These records belong to your player in the current world or server; they are not a single account-wide record shared across unrelated worlds. [Player records][player-files] · [Saved progress][stats-save]

## Open and browse the screens

Press **Escape** while playing, then choose **Advancements** or **Statistics** from the pause menu. Advancements also opens with **L** by default; use your configured Advancements key if you have rebound it. [Pause controls][pause-key] · [Pause route][pause-route] · [Menu buttons][pause-buttons] · [Default binding][advance-key] · [Key code][key-L] · [Key action][advance-open]

In Advancements, select a tab to change the tree. Drag with the left mouse button or scroll to move around a larger tree, and hover an entry to read its title, description and available progress text. **Done** or **Escape** returns to the previous screen, including the pause menu when opened from there. Pressing the configured Advancements key again closes that screen directly to play. Statistics also has **Done** and the normal Escape close action. [Advancement controls][advance-screen] · [Tree movement][advance-tabs] · [Hover details][advance-hover] · [Escape action][screen-escape] · [Statistics close][stats-close]

Advancement progress comes from the server, including the local server used by Singleplayer. The screen receives its visible tree and progress updates, and tab selection is synchronized with the server. Viewing or moving around the tree does not itself complete a goal. [Server updates][advance-listeners-sync] · [Client updates][advance-update-client] · [Tab selection][advance-selection-server]

## Read advancement progress

Start with the entry's description. Completion depends on its configured requirements: every requirement group must be satisfied, while conditions within one group are alternatives. A displayed fraction such as **2/3** counts satisfied groups out of the total, rather than every action performed or every possible alternative. Numeric progress text is used when there is more than one group. [Completion check][advance-done] · [Requirement groups][advance-requirements] · [Progress text][advance-progress-display] · [Displayed progress][advance-progress-widget]

**MattMC deliberately makes unfinished goals with display information visible.** You do not have to finish them before browsing their descriptions. This is still a display of eligible trees, not a guarantee that every loaded advancement has a screen entry: roots need display information and the tab layout has a finite capacity. A visible goal also does not establish that every associated gameplay trigger is implemented. [Visibility choice][advance-hidden] · [Visibility evaluation][advance-visibility] · [Initial tree evaluation][advance-initial-visibility] · [Tab and entry creation][advance-tabs]

### Rewards and notifications

When an advancement changes from incomplete to complete, the server grants its configured rewards. These can include experience, loot or other configured effects; some advancements have no reward. Repeating an already completed condition does not repeatedly grant its reward. [Completion and rewards][advance-award] · [Reward types][advance-rewards] · [Already completed conditions][advance-progress]

**Recipe-unlocking rewards are intentionally inactive in current MattMC.** Their recipe-award route does not record known recipes. This does not disable the separate advancement completion, experience or loot paths. See [Knowledge Book](../items/KnowledgeBook.md#behavior) for the same inactive recipe-award behavior and its item-use cases, and [Recipe Viewer](RecipeViewer.md) for browsing recipes. [Recipe reward call][advance-rewards] · [Inactive recipe award][recipe-award-no-op]

A quiet chat does not mean an advancement failed. Chat announcements require both the advancement's announcement setting and the **announceAdvancements** game rule, which defaults to true. That rule controls the chat broadcast, not completion or rewards. The on-screen toast is separate: it depends on the advancement's display/toast settings and a qualifying completion update; the initial reset/load update does not replay completion toasts. Check the Advancements screen when a notification is absent. [Chat rule][announcement-rule] · [Completion and announcement order][advance-award] · [Toast conditions][advance-update-client]

### Extra rewards in Skyblock

An advancement completion can also grant extra loot when **the dimension you are currently in uses the Skyblock generator** and that advancement has a matching Skyblock reward table. Generated items are offered to your inventory; an uninserted result is dropped for you. This is conditional, so do not expect a bonus for every advancement. The [Flint and Steel guide](../items/FlintAndSteel.md#loot-and-creative-access) owns the worked Obsidian-acquisition example and its possible rewards. [Completion hook][advance-award] · [Generator, table and delivery conditions][skyblock-reward]

For a new local world, choose **Create New World → World → World Type: Skyblock**; follow [Local worlds](LocalWorlds.md#create-a-local-world) for the creation screen. Skyblock is a world type, separate from the player mode. Its bundled preset uses the Skyblock generator in the Overworld, while its Nether and End use ordinary generators. Merely entering those dimensions from a Skyblock world therefore does not satisfy the bonus's current-dimension condition. [Selectable preset][skyblock-selectable] · [Preset list][preset-selector] · [World Type control][world-type-control] · [Control choices][world-type-choices] · [Dimension generators][skyblock-resource]

## Read statistics

Opening Statistics requests your counters from the server and first shows **Retrieving statistics...**. Once the response arrives, choose a tab below. Reopen the screen to request updated values; do not rely on it as a continuously refreshed activity feed. [Screen request and loading][stats-open] · [Server request handler][stats-request-server] · [Sent counters][stats-send-server] · [Client response][stats-packet-client]

| Tab | What it shows |
| --- | --- |
| **General** | Registered general statistics, sorted by their translated names, including entries with zero values |
| **Items** | Items with a positive recorded value in at least one relevant column: **Times Mined**, **Times Broken**, **Times Crafted**, **Times Used**, **Picked Up**, or **Dropped** |
| **Mobs** | Mob/entity types with a positive kill or killed-by count, showing how many you killed and how many times that type killed you separately |

The Items list is not your current inventory, and Mobs is not a list of every creature you have encountered. Empty Items or Mobs tabs are disabled with **No statistics found.** Non-block items show **-** in the mining column. Hover an item-column header to identify it; selecting a header sorts highest first, selecting it again sorts lowest first, and a third selection clears the selected sort. [General list][stats-general] · [Item rows][stats-items] · [Mining display][stats-null] · [Mob rows][stats-mobs] · [Empty tabs][stats-open] · [Header controls][stats-sort-buttons] · [Sort cycle][stats-sort] · [Sort comparison][stats-sort-order]

### Interpret the numbers

Statistics depend on the actions that record each counter. They are not an exhaustive log of every click or world change. Two useful distinctions are:

- **Times Crafted** counts output items through the ordinary crafting-result route. Taking a result containing several items can add several to that item's counter; it is not simply a count of clicks or distinct recipes. [Result amounts][stats-craft-slot] · [Crafted counter][stats-crafted]
- **Time Played** and **Time with World Open** both advance during ordinary player ticking, but the local server's paused path also advances **Time with World Open**. These are tick counters displayed using 20 ticks per second, not guaranteed real-world stopwatch measurements. See [Local worlds](LocalWorlds.md#save-and-close-a-world) and [Multiplayer and LAN](MultiplayerAndLAN.md#keep-the-host-world-running) for when menus pause a world. [Player time counters][stats-time] · [Pause branch][stats-paused] · [Paused time counter][stats-paused-increment] · [Time formatting][stats-format]

For more specific behavior, use [Target](../blocks/Target.md) for hit counts versus its advancement condition, [Knowledge Book](../items/KnowledgeBook.md#behavior) for successful and failed item-use cases, and [Time, weather, and sleep](TimeWeatherAndSleep.md) for world clocks and rest. Those systems have their own conditions; a statistic and an advancement need not change together.

## Sources and verification

Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`. The review followed the active menu and key routes, advancement progress/reward synchronization, statistic requests and displays, player record storage, the Skyblock world-type selector and its conditional reward hook. English labels were checked across all four bundled language namespaces and the shipped translation-renaming pass. Other languages and resource packs can change displayed text. [Language loading][english-loader] · [Translation updates][english-deprecated]

No running-client UI, gameplay, reward, save/reload, multiplayer synchronization or world-generation test was performed. This guide does not certify every advancement condition, imported gameplay hook, statistic producer or reward table, and is not a complete Skyblock progression guide.

Related: [Mechanics](Mechanics.md) · [Local worlds](LocalWorlds.md) · [Multiplayer and LAN](MultiplayerAndLAN.md) · [Experience](Experience.md) · [Gameplay](../Gameplay.md)

[player-files]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/players/PlayerList.java#L804-L837
[stats-save]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/players/PlayerList.java#L327-L338
[pause-key]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/KeyboardHandler.java#L528-L540
[pause-route]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L1691-L1701
[pause-buttons]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/PauseScreen.java#L38-L84
[advance-key]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Options.java#L574-L582
[key-L]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/blaze3d/platform/InputConstants.java#L53-L61
[advance-open]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L2064-L2074
[advance-screen]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/advancements/AdvancementsScreen.java#L66-L175
[advance-tabs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/advancements/AdvancementTab.java#L130-L202
[advance-hover]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/advancements/AdvancementWidget.java#L246-L285
[screen-escape]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/Screen.java#L111-L118
[stats-close]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/achievement/StatsScreen.java#L156-L176
[advance-listeners-sync]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/PlayerAdvancements.java#L269-L367
[advance-update-client]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/multiplayer/ClientAdvancements.java#L38-L103
[advance-selection-server]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L554-L563
[advance-done]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/advancements/AdvancementProgress.java#L55-L68
[advance-requirements]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/advancements/AdvancementRequirements.java#L27-L74
[advance-progress-display]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/advancements/AdvancementProgress.java#L131-L160
[advance-progress-widget]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/advancements/AdvancementWidget.java#L186-L206
[advance-hidden]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/advancements/DisplayInfo.java#L99-L125
[advance-visibility]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/advancements/AdvancementVisibilityEvaluator.java#L14-L66
[advance-initial-visibility]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/PlayerAdvancements.java#L78-L116
[advance-award]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/PlayerAdvancements.java#L219-L244
[advance-rewards]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/advancements/AdvancementRewards.java#L25-L92
[advance-progress]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/advancements/AdvancementProgress.java#L68-L94
[recipe-award-no-op]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1470-L1487
[announcement-rule]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/GameRules.java#L128-L133
[skyblock-reward]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/PlayerAdvancements.java#L393-L455
[skyblock-selectable]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json#L1-L10
[preset-selector]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldCreationUiState.java#L248-L268
[world-type-control]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L800-L817
[world-type-choices]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L860-L872
[skyblock-resource]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/worldgen/world_preset/skyblock.json#L1-L35
[stats-open]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/achievement/StatsScreen.java#L55-L138
[stats-request-server]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1788-L1793
[stats-send-server]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/stats/ServerStatsCounter.java#L121-L134
[stats-packet-client]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java#L1692-L1704
[stats-general]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/achievement/StatsScreen.java#L177-L229
[stats-items]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/achievement/StatsScreen.java#L241-L298
[stats-null]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/achievement/StatsScreen.java#L470-L497
[stats-mobs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/achievement/StatsScreen.java#L556-L607
[stats-sort-buttons]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/achievement/StatsScreen.java#L426-L440
[stats-sort]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/achievement/StatsScreen.java#L327-L339
[stats-craft-slot]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L27-L56
[stats-crafted]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/ItemStack.java#L713-L716
[stats-time]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/level/ServerPlayer.java#L642-L666
[stats-paused]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/server/IntegratedServer.java#L92-L111
[stats-paused-increment]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/server/IntegratedServer.java#L134-L140
[stats-format]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/stats/StatFormatter.java#L9-L42
[english-loader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/resources/language/ClientLanguage.java#L35-L72
[english-deprecated]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/locale/DeprecatedTranslationsInfo.java#L73-L92
[stats-sort-order]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/achievement/StatsScreen.java#L528-L551
