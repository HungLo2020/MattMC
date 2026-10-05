# World data packs

World data packs provide gameplay data for a particular world. The controls here select that data during creation or reload supported data in a running world. For client resource packs and shaders, use [Graphics settings and packs](GraphicsAndPacks.md). World packs belong to the saved world's `datapacks` directory. [World pack location][world-packs]

Before changing an existing local world's packs, follow [Local worlds: back up a closed world](LocalWorlds.md#back-up-a-closed-world). Pack functions can run commands when loaded and during play; disabling a pack does not undo commands it has already run. [Function execution][functions] · [Loading functions][function-load]

## Select packs for a new world

Open **Create New World → More → Data Packs**. The **Select Data Packs** screen uses a temporary staging folder. **Open Pack Folder** opens that folder, not a saved world's final folder. See [Local worlds](LocalWorlds.md#create-a-local-world) for the surrounding creation choices. [Creation controls][creation-controls] · [Selection route][creation-route] · [Temporary folder][temporary-folder]

1. Check **Available** for the pack. **Search...** matches its ID, title, or description. Discovery checks folders containing `pack.mcmeta` and regular `.zip` files, then reads pack metadata; a ZIP extension alone does not establish a usable pack. [Search][search] · [Discovery][discovery] · [Metadata][metadata]
2. Hover over its icon and use the transfer control to move it to **Selected**. Copying a file into the folder, or highlighting a list entry, does not select it. A metadata-incompatible pack prompts a warning before selection. [Transfer controls][transfer] · [Discovery refresh][discovery-refresh]
3. Use the allowed up/down controls to set order. Higher Selected entries take priority for ordinary same-path lookups; merged resources and filtering have their own rules. Required packs cannot be removed, and fixed-position neighbors can limit movement. [Required packs][required] · [Movement][movement] · [Order][order] · [Lookup][lookup]
4. Choose **Done** to submit the selection to the creation screen. **Escape also commits the selection**; this screen has no Cancel/discard action. Neither action creates the world. [Done][done] · [Close behavior][close] · [Escape dispatch][escape] · [Creation callback][creation-route]

Dragging files onto the selection screen asks whether to add them, then copies recognized entries into its current folder and refreshes discovery. Newly discovered packs still need selecting. Rejected entries and copy errors have separate messages; a mixed drop can copy some entries while rejecting others. [Drop confirmation][drop-confirm] · [Copy handling][drop]

When you eventually create the world, the temporary folder's contents are copied into its `datapacks` folder, including staged entries left unselected. A copy/access failure can stop creation and report **Failed to copy packs**. [Created-world copy][copy] · [Creation failure handling][copy-failure] · [Copy failure notice][copy-notice]

## Understand creation warnings and validation

A changed selection loads server data and world-generation data, checks the available world presets and biomes, and validates the chosen generators before replacing the creation settings. An unchanged enabled-pack list and feature set can skip that rebuild, even if files under the same pack IDs were edited. **Done is not a force-revalidate button.** [Validation][validation] · [Unchanged-selection shortcut][shortcut]

**Experimental Features Warning** offers **Details**, **Proceed**, and **Back** when selected packs request experimental features. Read the warning before proceeding; it does not certify compatibility. [Experimental warning][experimental]

At **Data pack validation failed!**, **Go Back** reopens selection using the stored creation configuration. **Reset to Default** reopens it using the default data configuration. Reset does not delete staged files or restore a saved world. [Validation failure choices][validation-failure]

## Inspect or change packs in a loaded world

The `/datapack` controls below and `/reload` require **permission level 2**. Start with [Commands: help and permissions](../commands/Commands.md#start-with-help-and-permissions); [Local worlds](LocalWorlds.md#choose-modes-difficulty-and-commands) explains **Allow Commands**. [Data-pack permission][command-permission] · [Reload permission][reload]

| Command form | What it does |
| --- | --- |
| `/datapack list` | Refreshes discovery and lists enabled and available packs |
| `/datapack list enabled` | Lists the selected packs after refreshing discovery |
| `/datapack list available` | Lists unselected packs whose required features are enabled in this world |
| `/datapack enable <name>` | Requests selection at the pack's default position and reloads |
| `/datapack enable <name> first` or `last` | Requests selection at the corresponding end of the loading order and reloads |
| `/datapack enable <name> before <existing>` or `after <existing>` | Positions it relative to an enabled pack and reloads |
| `/datapack disable <name>` | Requests removal from the selection and reloads |
| `/reload` | Refreshes discovery and reloads server data; it can include newly discovered packs not recorded as disabled |

`<name>` and `<existing>` are placeholders: use the exact pack IDs returned or suggested by the game, with quotes when required. World-file pack IDs begin with `file/`. In command order, `last` has higher ordinary same-path lookup priority than `first`; the menu displays the reverse order. [Command syntax][command-syntax] · [Suggestions][suggestions] · [File IDs][file-ids] · [Order][order] · [Lookup][lookup]

Listing packs is an inventory check, not a gameplay reload or proof that their content works. Discovery can change the repository's selected list when files disappear, so its output alone does not establish what the running game has loaded. [List behavior][lists] · [Discovery refresh][repository] · [Selection rebuild][repository-rebuild]

Unknown IDs, already-enabled/already-disabled choices, and missing world features can reject a command. Feature packs supporting enabled flags can also refuse disabling; `/datapack enable` does not add missing world features. The separate `/datapack create` authoring command requires level 4 and creates an empty pack, rather than enabling gameplay content. [Command restrictions][command-errors] · [Authoring permission][create-permission] · [Empty-pack creation][create]

## What a reload does, and what its messages mean

The normal reload route covers recipes, functions, advancements, existing-registry tags, and loot data; it also refreshes structure templates and fuel values. After tag updates, the current code refreshes its village-centre membership data. [Village membership refresh][poi-refresh] It does not rebuild world-generation registries or regenerate explored terrain. This route is not an installer for Java mods or arbitrary new registered block, item, or mob types. Imported systems can use separate loaders, so do not assume all their content reloads here. [Reloadable data][reload-data] · [Loot types][loot-types] · [Reload completion][replacement] · [Startup registries][startup] · [Built-in types][registrations] · [Registry freezing][registry-freeze] · [Separate loader example][separate-loader]

**Enabling data pack**, **Disabling data pack**, and **Reloading!** are initial feedback, not completion confirmations. Enable and disable already request a reload. `/reload` is also an active change, not a read-only test. [Selection changes][changes] · [Reload and feedback][reload]

If you see **Reload failed; keeping old data**, check the accompanying log error before making further changes. The old loaded resources remain assigned if loading fails before replacement starts. Once replacement begins, the remaining updates have no general rollback. Do not treat that message as a guarantee that all selection or world state was restored. [Failure feedback][reload] · [Replacement boundary][replacement]

Even a completed reload or successful creation validation can leave malformed individual files or functions omitted, with errors or warnings in the log. Check the intended behavior in a separate test world; a pack appearing in the list is not a compatibility certificate. [Skipped data files][json-errors] · [Failed functions][function-errors] · [Loot warnings][loot-errors]

## If a saved world will not open

The data-pack load failure screen offers **Safe Mode** or **Back**; Escape does not dismiss it. Safe Mode retries loading with an alternate selection starting from the vanilla pack. Saved world features can still force required feature packs, so it is not guaranteed to be vanilla-only, a restoration, or a successful recovery. [Failure screen][failure-screen] · [Retry route][load-failure] · [Safe Mode selection][safe-selection] · [Required feature packs][forced-features]

A failed retry displays **Failed to load world in Safe Mode.** These screens can follow level-data loading or generator-validation errors as well as pack-loading errors. Their wording alone does not identify the broken file or prove that an external pack caused the failure. [Load and retry failures][load-failure]

## Sources and verification

Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`, then checked against `5218ac875eda9f2c4151ff1a97795f20f5f356cf`. The latter adds the village-membership refresh to the reload completion path; the described controls and failure limits remain the same. English labels were checked across all four bundled language namespaces and the deprecation transform. External language/resource packs can change labels. [Language loading][language] · [Label migration][label-migration]

No live UI, game/server command, pack copy, reload, Safe Mode, compatibility, or recovery test was run. Source review establishes the control paths and limits described here; it does not establish successful disk writes or pack behavior in play.

Related: [Local worlds](LocalWorlds.md) · [Graphics settings and packs](GraphicsAndPacks.md) · [Commands](../commands/Commands.md) · [Mechanics](Mechanics.md)

[world-packs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/packs/repository/ServerPacksSource.java#L74-L85
[functions]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/ServerFunctionManager.java#L41-L58
[function-load]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/ServerFunctionManager.java#L81-L92
[creation-route]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L407-L420
[creation-controls]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L748-L773
[temporary-folder]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L384-L397
[search]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/PackSelectionScreen.java#L137-L150
[discovery]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/packs/repository/PackDetector.java#L22-L51
[metadata]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/packs/repository/Pack.java#L43-L72
[transfer]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/TransferableSelectionList.java#L254-L318
[discovery-refresh]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/PackSelectionModel.java#L61-L66
[required]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/PackSelectionModel.java#L101-L106
[movement]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/PackSelectionModel.java#L181-L205
[order]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/PackSelectionModel.java#L37-L58
[lookup]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/packs/resources/FallbackResourceManager.java#L63-L82
[close]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/PackSelectionScreen.java#L96-L100
[done]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/PackSelectionScreen.java#L124-L128
[escape]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/Screen.java#L111-L115
[drop-confirm]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/PackSelectionScreen.java#L260-L324
[drop]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/packs/PackSelectionScreen.java#L220-L258
[copy]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L555-L605
[copy-failure]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L305-L325
[copy-notice]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/components/toasts/SystemToast.java#L147-L149
[validation]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L449-L483
[shortcut]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldCreationUiState.java#L200-L215
[experimental]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/ConfirmExperimentalFeaturesScreen.java#L29-L79
[validation-failure]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/CreateWorldScreen.java#L479-L506
[command-permission]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/DataPackCommand.java#L98-L105
[reload]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/ReloadCommand.java#L18-L50
[command-syntax]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/DataPackCommand.java#L98-L147
[suggestions]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/DataPackCommand.java#L80-L96
[file-ids]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/packs/repository/FolderRepositorySource.java#L48-L66
[lists]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/DataPackCommand.java#L273-L311
[repository]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/packs/repository/PackRepository.java#L35-L39
[repository-rebuild]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/packs/repository/PackRepository.java#L84-L98
[command-errors]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/DataPackCommand.java#L314-L337
[create-permission]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/DataPackCommand.java#L163-L179
[create]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/DataPackCommand.java#L183-L249
[reload-data]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/ReloadableServerResources.java#L66-L96
[loot-types]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/storage/loot/LootDataType.java#L13-L27
[replacement]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/MinecraftServer.java#L1680-L1734
[startup]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/WorldLoader.java#L35-L52
[registrations]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/core/registries/BuiltInRegistries.java#L160-L170
[registry-freeze]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/core/registries/BuiltInRegistries.java#L361-L381
[separate-loader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L153
[changes]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/commands/DataPackCommand.java#L255-L270
[json-errors]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/packs/resources/SimpleJsonResourceReloadListener.java#L59-L93
[function-errors]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/ServerFunctionLibrary.java#L97-L117
[loot-errors]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L86-L90
[failure-screen]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/DatapackLoadFailureScreen.java#L17-L45
[load-failure]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/screens/worldselection/WorldOpenFlows.java#L339-L374
[safe-selection]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L1732-L1741
[forced-features]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/MinecraftServer.java#L1788-L1824
[language]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/resources/language/ClientLanguage.java#L35-L56
[label-migration]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/locale/DeprecatedTranslationsInfo.java#L70-L91

[poi-refresh]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiManager.java#L418-L426
