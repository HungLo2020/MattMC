# Region Editor

**Tools → Region Editor** opens a map of saved Overworld terrain and offers chunk copying, pasting, and deletion. It operates on the save on disk. The in-game [WorldEdit commands](../commands/WorldEdit.md) use a separate selection, clipboard, and history. **Make a closed-world backup before opening a save in this editor.** Delete is immediate, and even one whole-region deletion is too large for its undo capture. [Menu route][tools] · [Disk actions][delete] · [Undo eligibility][delete-count]

## Prepare the world

Close the world normally and follow [Back up a closed world](LocalWorlds.md#back-up-a-closed-world): select it under **Singleplayer**, choose **Edit → Make Backup**, and wait for the backup-created notification. Keep the world closed in every client or server using that save while you use Region Editor.

A selectable world is not proof that it is available for editing. The world-list loader reads lock status, but Region Editor does not use it to disable its world buttons or acquire the world's session lock for its operations. Loading, viewing, and copying also use a writable region-file path; they are **not guaranteed read-only inspection**. [Lock observation][lock-summary] · [Picker behavior][picker] · [Editor load][load] · [Normal locked access][locked-access] · [Region-file opening][region-open] · [Native open][native-open] · [Native close][native-close]

## Load the intended save

1. From the title screen, open **Tools → Region Editor**
2. Choose **File → Load World**, wait for the list, then click the world's display-name button
3. Check **World:** on the map and wait for **Loading...** to finish before using actions or shortcuts

The picker uses the client's configured local saves and shows only the **first eight** returned worlds. It has no folder browser or dimension selector. If the intended save is missing, do not substitute a similarly named world. [Title menu][title] · [World list and buttons][world-list] · [Displayed world and loading state][map-labels] · [Eight-world notice][picker-limit]

The loaded directory is that save's **Overworld terrain** directory. Nether, End, and custom dimensions are outside this screen's scope. Separate entity records, points of interest, player data, and world metadata are not copied or deleted along with terrain here. This is not a whole-world transfer or a demonstrated regeneration/repair procedure. [Loaded path][load] · [Dimension directories][dimensions] · [Terrain and points of interest][terrain-poi] · [Entity storage][entities]

Loading any world, including the same one again, clears the selection, paste preview, and undo history. **It keeps the clipboard**, so the old copy can still be offered for pasting into the newly loaded world's Overworld. [Load resets][load-reset] · [Screen-owned clipboard and history][state]

## Select regions or chunks

Use the mouse wheel over the map to zoom and an ordinary left drag to pan. The selection unit changes with zoom: below **two GUI pixels per chunk**, a click selects a whole region; at or above that threshold, it selects an individual chunk. One region spans **32 × 32 chunks**, or 1,024 chunk positions. [Zoom and pan][navigation] · [Selection threshold][selection-mode] · [Region size][constants]

| Control over the map | Selection effect |
| --- | --- |
| Left click | Add the region or chunk under the pointer |
| Right click | Remove that region or chunk selection |
| Ctrl + left drag | Add a box of regions or chunks at the drag's starting zoom mode |
| Ctrl + right drag | Remove selections in that box |

Only map-present regions/chunks are selectable. Check the highlighted area and **Selected Regions** before acting; that label counts whole regions, not all selected chunks. Panning begins with a left press that can also select the starting cell. [Click handling][clicks] · [Chunk selection][chunk-clicks] · [Box selection][boxes] · [Selection display][map-labels]

**Zooming in does not clear a whole-region selection.** An ordinary right click on one chunk does not subtract it from a selected whole region. For a small chunk edit, first remove the whole-region selection at region zoom and confirm **Selected Regions: 0**, then select the intended chunks. Ctrl-drag at chunk zoom also removes whole-region selection for the regions it touches; check the result before Delete or Copy. [Separate selections][chunk-clicks] · [Chunk-box behavior][boxes]

## Copy, preview, and paste

1. Select the intended small area, then use **Action → Copy** or **Ctrl+C**
2. Use **Action → Paste** or **Ctrl+V once** to activate the preview
3. Move the pointer over the intended destination on the map and inspect the preview rectangle
4. Only when ready to write, press **Ctrl+V again** while the pointer remains over that destination

The first Paste only activates placement preview. The next Paste invocation commits at the pointer's current origin; **there is no further confirmation dialog or separate Save step**. Moving outside the map prevents an origin from being chosen, and an empty loaded map cannot provide one. The rectangle shows the clipboard's bounds, not a block-by-block result or a backup. [Copy][copy] · [Preview and commit][paste] · [Origin requirements][origin] · [Preview rectangle][preview] · [Shortcuts][shortcuts]

Copy gathers selected map-present chunks with readable saved terrain data. A successful copy replaces this editor's private clipboard; a copy with nothing readable leaves the older clipboard in place. It is not the operating-system clipboard. Whole-region-only selections snap the paste origin to the region grid; a selection containing individual chunk entries uses the chunk grid. Copied offsets start at the selection's smallest chunk X and Z, rather than a player position. [Collected chunks][copy-selection] · [Clipboard replacement, offsets, and snapping][copy]

Paste can replace existing destination terrain, and writes are attempted one chunk at a time, so partial results are possible. The editor updates chunk X/Z headers; this does not establish that every embedded coordinate is relocated. In particular, saved block/fluid ticks are filtered against the destination chunk when loaded rather than translated, so their preservation cannot be promised. The ordinary unpacked block-entity loading path adjusts X/Z into the destination chunk, but other embedded references have not been established as correctly relocated. [Paste loop][paste] · [Chunk writes][chunk-write] · [Coordinate changes][coordinates] · [Tick loading][tick-load] · [Tick filter][tick-filter] · [Block-entity loading][block-entity-load] · [Position adjustment][block-entity-position]

## Delete changes the save immediately

**Action → Delete** starts deletion as soon as its button action runs. There is **no confirmation or backup dialog**, and no later Save button. Selected whole regions remove their terrain region files; individually selected chunks clear their terrain records. The operation clears the selection, but that does not prove every requested deletion succeeded. [Delete button][action-buttons] · [Deletion and failure handling][delete]

Deleting a whole region does not perform the same external chunk-file cleanup as individual chunk deletion. Do not treat either operation as a complete world-data cleanup or repair. [Whole-region deletion][delete] · [Individual deletion][region-delete] · [External-payload handling][native-delete]

## Undo has a small, temporary scope

**Action → Undo** or **Ctrl+Z** immediately attempts to restore the newest captured action. There is **no redo**. It restores remembered terrain data or clears a destination that the snapshot recorded without readable data. [Undo button][action-buttons] · [Undo replay][undo] · [Shortcuts][shortcuts]

| Action | When undo capture is eligible |
| --- | --- |
| Delete | At most **256** selected chunk positions; each selected whole region counts as **1,024**, plus individually selected chunks outside those regions |
| Paste | At most **256 readable chunks in the clipboard**, regardless of how many writes eventually succeed |

**One selected whole region skips undo, even if it contains only a few saved chunks.** The 256 limit controls snapshot capture, not whether the action runs. Larger Delete/Paste actions still execute. They also leave older history intact, so pressing Undo afterward can undo an **older action**, not the large action you just performed. [Limit][constants] · [Delete count and capture][delete-count] · [Delete history][delete-history] · [Paste capture and history][paste]

History belongs to this open editor screen. Loading any world clears it; using **Back** and reopening Region Editor starts a new screen with empty history and clipboard. There is no saved undo history to rely on after reopening the tool or restarting. [Instance state][state] · [Load reset][load-reset] · [Back button][back] · [New screen][tools]

Undo is not a substitute for the separate backup. A failed terrain read is recorded like an absent chunk, so undo can choose to clear that destination instead of restoring unreadable prior data. An undo entry is removed before its restoration attempts, and failures can leave only some chunks restored. These are source-derived limits, not reproduced data-loss results. [Read failures][chunk-read] · [Snapshot capture][snapshot] · [Undo attempts][undo]

## Verification and feedback limits

The screen displays **Selected Regions** and a paste-preview prompt, but its general operation-status text is drawn only when the map is empty. Do not depend on a visible success message or advance warning that undo will be skipped. A refreshed map is not proof of a complete, recoverable world change. [Rendering][map-labels] · [Delete status][delete-history] · [Paste status][paste]

Source-reviewed on **2026-10-05** at `5218ac875eda9f2c4151ff1a97795f20f5f356cf`, including active menus, input, save scope, clipboard/history lifetime, and the terrain storage path. [Stream commit][stream-commit] · [Payload write][payload-write] · [Native bindings][native-bindings] · [Native operations][native-operations]

No game, editor action, save edit, copy, deletion, backup, restore, or runtime test was performed for this guide. Source review does not certify concurrent access, relocation completeness, crash recovery, or backup recoverability.

Related: [Local worlds and backups](LocalWorlds.md) · [WorldEdit basics](../commands/WorldEdit.md) · [Mechanics](Mechanics.md) · [Gameplay](../Gameplay.md)

[tools]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/ToolsScreen.java#L20-L29
[title]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/TitleScreen.java#L204-L208
[delete]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L417-L535
[delete-count]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L648-L660
[lock-summary]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/storage/LevelStorageSource.java#L210-L224
[picker]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L285-L301
[load]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L341-L400
[locked-access]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/storage/LevelStorageSource.java#L440-L449
[region-open]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/chunk/storage/RegionFile.java#L48-L62
[native-open]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/rust/storage/region/open.rs#L28-L90
[native-close]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/rust/storage/region/open.rs#L317-L326
[world-list]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L304-L338
[map-labels]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L1904-L1957
[picker-limit]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L1998-L2006
[dimensions]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/dimension/DimensionType.java#L132-L141
[terrain-poi]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ChunkMap.java#L164-L197
[entities]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerLevel.java#L241-L250
[load-reset]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L341-L370
[state]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L132-L157
[navigation]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L1686-L1757
[selection-mode]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L1826-L1835
[constants]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L81-L94
[clicks]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L1548-L1601
[chunk-clicks]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L1603-L1664
[boxes]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L1837-L1898
[copy]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L537-L588
[paste]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L591-L645
[origin]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L771-L800
[preview]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L2018-L2057
[shortcuts]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L1667-L1683
[copy-selection]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L717-L769
[chunk-write]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L822-L837
[coordinates]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L860-L867
[tick-load]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/chunk/storage/SerializableChunkData.java#L132-L137
[tick-filter]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/ticks/SavedTick.java#L46-L49
[block-entity-load]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/chunk/storage/SerializableChunkData.java#L498-L520
[block-entity-position]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L68-L80
[action-buttons]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L226-L237
[region-delete]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/chunk/storage/RegionFile.java#L240-L251
[native-delete]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/rust/storage/region/open.rs#L279-L314
[undo]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L663-L690
[delete-history]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L526-L534
[back]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L242-L245
[chunk-read]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L803-L819
[snapshot]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/screens/RegionEditorScreen.java#L870-L877
[stream-commit]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/chunk/storage/RegionFile.java#L416-L436
[payload-write]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/chunk/storage/RegionFile.java#L310-L360
[native-bindings]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/chunk/storage/NativeRegionFileBridge.java#L357-L411
[native-operations]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/rust/storage/region/ffi.rs#L1042-L1143
