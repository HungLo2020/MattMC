# Minimap and waypoints

Use **M** to browse the persistent world map, **N** to name your current location, and **U** to choose a waypoint to highlight. These are MattMC's integrated VoxelMap controls; the [current limits](#current-limits) below describe the source-reviewed scope. [Input actions][input]

For the built-in tracked-entity direction bar and its display rules, see [Locator Bar](LocatorBar.md).

## Default controls

Saved bindings can replace these defaults. The action names below are the current English key rows. [Defaults][defaults] · [Saved bindings][saved-keys] · [Key rows][key-rows] · [English names][key-labels]

| Default | Action name | Use |
| --- | --- | --- |
| **M** | VoxelMap Menu | Open the persistent world-map screen |
| **U** | Waypoint Menu | Open the waypoint list, when waypoints are allowed |
| **N** | Waypoint Hotkey | Open a new waypoint at your current position, when waypoints are allowed |
| **Z** | Zoom | Cycle the live minimap's zoom |
| **X** | Toggle Fullscreen Map | Toggle a centered live-map overlay |
| **O** | Toggle Minimap | Toggle VoxelMap's own **Hide Minimap** setting |

Use these shortcuts **in a loaded world with the HUD visible and no screen open**. Close chat, inventory, menus, or the welcome screen first. **F1** hides the whole HUD and prevents this input route from running; show the HUD again before using the map shortcuts. **O** only hides VoxelMap's overlay, so O can show it again while the rest of the HUD remains visible. [HUD gate][hud-gate] · [Input gate and actions][input] · [F1][f1] · [Input before map drawing][overlay-order] · [Own hide][overlay-hide]

On the **M** map, scroll to zoom and hold the left mouse button to drag the map; use **Done** to return. **X** is a separate, centered 256×256 live overlay. Its current drawing branch returns before the waypoint-marker loop, so use the ordinary minimap for that marker route. [Mouse navigation][map-mouse] · [Dragging][map-drag] · [Done][map-buttons] · [X overlay][fullscreen] · [Minimap markers][markers]

### Change a binding

Open **M → Options... → Controls...** to reach **Minimap Controls**. Select the key button beside the action, then press the replacement key or mouse button. Duplicate bindings are flagged against the game's other key mappings. [Menu route][map-buttons] · [Controls button][controls-button] · [Screen title][controls-title] · [Binding editor][binding-editor]

**Z is also the default [TaCZ Refit key](TaCZFirearms.md#controls).** Both mappings receive the press. With a supported gun in the main hand and a non-spectator player, a client tick can open Refit before the map's HUD input pass; opening that screen clears pending key clicks. Rendering can also occur without a client tick first, so there is no guaranteed winner. Rebind one action instead of relying on that timing. This interaction was traced in source, not reproduced in-game. [Shared keys][key-broadcast] · [Refit default][refit-key] · [Refit conditions][refit-input] · [Screen transition][screen-transition] · [Tick scheduling][tick-scheduling]

## Save a place and find it again

1. Stand at the place you want to remember and press **N**. The editor starts with your current coordinates, dimension, and subworld. [New point][new-point]
2. Enter a name such as **Home**, keep the prefilled coordinates, and choose **Done**. This adds the point and requests a local save. If you change coordinates, use whole numbers. [Editor][point-editor] · [Acceptance][point-accept] · [Add and save][point-add]
3. Later, press **U**, select the point, and choose **Highlight**, then **Done**. **Remove Highlight** clears that selection. Highlight chooses a temporary navigation target; it does not save a new point or permanently mark that target as selected. Entering a dimension clears the highlight. [List actions][list-actions] · [Highlight action][highlight-action] · [Temporary selection][highlight-state] · [Dimension entry][dimension-entry]

## Local data and world scope

Waypoint files are local to this client, under its game-directory **voxelmap** folder. Adding or editing a point invokes the save routine, which can report a write error; this source review does not verify successful persistence. Names beginning with `^` are skipped when writing points. This is not account or cloud synchronization. [Save routine][point-save]

Single-player points are keyed by the world's save-folder name; multiplayer normally uses the server address. The list is filtered by the current dimension and subworld. New points start in the current dimension, but dimension memberships can be changed; blank subworld names act as a wildcard. If a point is missing, check that context before assuming it was deleted. [World/server identity][world-identity] · [List filtering][list-filter] · [Dimension membership][dimension-entry] · [Membership editing][dimension-edit] · [Subworld matching][subworld-match]

The persistent map's local cache is also separated by world/server, subworld, and dimension. Single-player mapping can read existing saved region data, so it is not limited to terrain you personally visited during this session. [Cache paths][cache-paths] · [Saved-region input][saved-regions]

The **Make Backup** action in [Local worlds, saving, and backups](LocalWorlds.md#back-up-a-closed-world) archives the selected world-save folder. The client **voxelmap** folder is separate, so that world backup does not include these waypoint files or map caches. No backup or restore was tested here. [World-save location][world-save-path] · [Backup contents][world-backup] · [Waypoint location][point-save] · [Map-cache location][cache-save]

## Current limits

The current source includes a native image/mesh route for the minimap, copied-image drawing for ready persistent-map regions, and minimap waypoint markers. Missing map images or marker resources can be skipped. This does not establish complete displayed maps or visual parity. **3D beacon beams have no active producer in this route**; a beacon setting is not evidence that a beam is drawn. Rendering acceptance remains in the [Goal 5 rendering checkpoint](../../development/rendering/GOAL-5-STATUS.md). [Minimap submission][minimap-submit] · [Native consumer][native-consumer] · [Region images][region-images] · [World-map drawing][worldmap-draw] · [Marker resources][marker-resources] · [World waypoint producer][world-waypoints] · [Legacy beam method][legacy-beam]

**Working server-policy enforcement is not established.** Settings-packet decoding and world-ID dispatch are incomplete, and the minimap permission flag gates map calculation without guarding the outer drawing route. The shared settings/identity integration gap is tracked in [#817](https://github.com/HungLo2020/MattMC/issues/817); restoring delivery also needs checks at the supported display consumers, including already-generated map images. Separate legacy cave-permission chat handling, local mapping, and manual subworld selection remain active paths. These source findings do not establish that all multiplayer mapping fails or that a visible server restriction was bypassed. [Packet codec][packet-codec] · [Registration stub][packet-registry] · [Play dispatch][play-dispatch] · [Configuration dispatch][configuration-dispatch] · [Calculation gate][calculation-gate] · [Drawing route][drawing-route] · [Cave-chat caller][cave-chat-caller] · [Cave-chat action][cave-chat-action] · [Manual selection][manual-subworld]

## Sources and verification

Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`. This review followed current input, menu, save, map-image, marker, and server-integration paths. English labels were checked against the bundled language maps and deprecation transform; external language packs can change them. No game was launched, and no UI interaction, waypoint file, map cache, network session, or server restriction was tested.

Related: [Mechanics](Mechanics.md) · [TaCZ controls](TaCZFirearms.md#controls) · [Local worlds and backups](LocalWorlds.md#back-up-a-closed-world)

[input]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/Map.java#L396-L459
[defaults]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/MapSettingsManager.java#L91-L118
[saved-keys]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/MapSettingsManager.java#L124-L180
[key-rows]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/gui/GuiButtonRowListKeys.java#L134-L150
[key-labels]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/lang/en_us.json#L8036-L8045
[hud-gate]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/Gui.java#L263-L275
[f1]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/KeyboardHandler.java#L525-L548
[overlay-order]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/VoxelConstants.java#L123-L138
[overlay-hide]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/Map.java#L725-L730
[map-mouse]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/persistent/GuiPersistentMap.java#L300-L362
[map-drag]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/persistent/GuiPersistentMap.java#L516-L607
[map-buttons]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/persistent/GuiPersistentMap.java#L202-L211
[fullscreen]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/Map.java#L743-L769
[markers]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/Map.java#L817-L835
[controls-button]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/gui/GuiMinimapOptions.java#L49-L51
[controls-title]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/gui/GuiMinimapControls.java#L23-L29
[binding-editor]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/gui/GuiButtonRowListKeys.java#L22-L111
[key-broadcast]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/KeyMapping.java#L32-L48
[refit-key]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L16-L22
[refit-input]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L52
[screen-transition]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L1172-L1183
[tick-scheduling]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L1297-L1318
[new-point]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/Map.java#L410-L431
[point-editor]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/gui/GuiAddWaypoint.java#L74-L98
[point-accept]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/gui/GuiAddWaypoint.java#L122-L164
[point-add]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/WaypointManager.java#L718-L725
[list-actions]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/gui/GuiWaypoints.java#L66-L94
[highlight-action]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/gui/GuiWaypoints.java#L238-L248
[highlight-state]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/WaypointManager.java#L728-L745
[dimension-entry]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/WaypointManager.java#L285-L300
[point-save]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/WaypointManager.java#L484-L533
[world-identity]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/WaypointManager.java#L170-L236
[list-filter]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/gui/GuiSlotWaypoints.java#L41-L54
[dimension-edit]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/gui/GuiAddWaypoint.java#L355-L366
[subworld-match]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/WaypointManager.java#L357-L373
[cache-paths]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/persistent/CachedRegion.java#L103-L121
[saved-regions]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/persistent/CachedRegion.java#L295-L312
[world-save-path]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L530-L534
[world-backup]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/storage/LevelStorageSource.java#L619-L660
[cache-save]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/persistent/CachedRegion.java#L560-L574
[minimap-submit]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/Map.java#L786-L813
[native-consumer]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/rust/render/guirender/frontend/mesh_items/recording.rs#L285-L392
[region-images]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/persistent/CachedRegion.java#L684-L698
[worldmap-draw]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/persistent/GuiPersistentMap.java#L635-L644
[marker-resources]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/Map.java#L845-L895
[world-waypoints]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/VoxelConstants.java#L156-L209
[legacy-beam]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/util/WaypointContainer.java#L60-L111
[packet-codec]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/network/protocol/common/ClientboundCustomPayloadPacket.java#L21-L50
[packet-registry]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/fabricmc/fabric/api/networking/v1/PayloadTypeRegistry.java#L40-L46
[play-dispatch]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java#L2166-L2178
[configuration-dispatch]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/multiplayer/ClientConfigurationPacketListenerImpl.java#L71-L79
[calculation-gate]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/Map.java#L462-L465
[drawing-route]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/Map.java#L725-L843
[cave-chat-caller]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/gui/components/ChatComponent.java#L184-L196
[cave-chat-action]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/VoxelMap.java#L125-L140
[manual-subworld]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/voxelmap/gui/GuiSubworldsSelect.java#L209-L217
