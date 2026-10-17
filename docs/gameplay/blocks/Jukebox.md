# Jukebox

A **Jukebox** (`minecraft:jukebox`) holds one music disc and plays its configured song. It also provides two useful redstone outputs: **strength 15 while playing**, and a Comparator value determined by the inserted song even after playback ends. Hoppers can load and unload its one-item inventory. [Block registration][jukebox-reg] · [Signals][jukebox] · [Disc container][jukebox-entity]

## Crafting and collecting

Craft **one Jukebox** with **eight planks surrounding one Diamond** in a Crafting Table. The planks ingredient is the bundled planks tag, so accepted plank types can be mixed. The recipe creates an empty Jukebox; it does not include a disc. [Recipe][jukebox-recipe] · [Plank choices][planks]

The [Jukebox item](../items/Jukebox.md) is also available in Creative. The block has **hardness 2 and blast resistance 6**. An axe is efficient, but no correct-tool requirement gates ordinary loot, so hand mining can recover **one Jukebox**. Silk Touch is unnecessary and Fortune does not add drops. The block loot has an explosion-survival condition. [Item/Creative entries][jukebox-item] [creative] · [Registration][jukebox-reg] · [Axe tag][axe] · [Harvest gate][gate] [harvest] · [Loot][jukebox-loot]

Its full-cube collision uses the ordinary block shape. It has no attachment-support requirement or waterlogged state, and no facing to align for disc insertion. The only dedicated block state is `has_record`. [State definition][jukebox] · [Inherited shape/support][defaults]

## Inserting and ejecting discs

Use an empty Jukebox while holding a playable disc. One item moves into the Jukebox and playback starts. Insertion recognizes the **jukebox-playable item component**, and playback resolves its song; all 21 registered discs below have that component. A Jukebox holds **one item**, not a stack or a playlist. Creative insertion preserves the held item while placing a copy. [Item registrations][discs] · [Component setup][item-component] · [Manual insertion][disc-use] · [One-item inventory and start][jukebox-entity] [single] · [Creative consumption rule][consume]

Use an occupied Jukebox to **stop playback and eject the disc as a loose item** near the top. Ordinary main-hand use reaches this action even with an item held; Sneak/Crouch with a held item bypasses it. Using a second disc on an occupied Jukebox first ejects the existing one, rather than replacing both in a single interaction. Use the now-empty block again to insert the new disc. [Occupied use and fallback][jukebox] · [Input routing][use] · [Ejection][jukebox-entity]

When a song finishes on its own, **the disc remains inside**. There is no automatic repeat in the song player. Eject and reinsert it to restart, or arrange Hopper transfers to change discs. Redstone power supplied to the Jukebox is not a play, pause, or eject control. [Song finish][song-player] · [Block callbacks][jukebox]

## Song lengths and Comparator values

The table lists the **21 bundled song records**, each connected to its matching `minecraft:music_disc_…` item. Song IDs below use the `minecraft:` namespace. Durations are the configured `length_in_seconds` values, not audio measurements made for this guide. The linked item pages retain ownership of how to acquire each disc. [Disc mapping][discs] · [Song keys][song-keys] · [Active song-registry loading][song-load] · [Sound-event registration][sound-register]

| Disc and song ID | Configured length | Comparator strength | Source |
| --- | --- | ---: | --- |
| [13](../items/MusicDisc13.md) · `13` | 2:58 (178 s) | 1 | [Data][song-13] |
| [Cat](../items/MusicDiscCat.md) · `cat` | 3:05 (185 s) | 2 | [Data][song-cat] |
| [Blocks](../items/MusicDiscBlocks.md) · `blocks` | 5:45 (345 s) | 3 | [Data][song-blocks] |
| [Chirp](../items/MusicDiscChirp.md) · `chirp` | 3:05 (185 s) | 4 | [Data][song-chirp] |
| [Far](../items/MusicDiscFar.md) · `far` | 2:54 (174 s) | 5 | [Data][song-far] |
| [Mall](../items/MusicDiscMall.md) · `mall` | 3:17 (197 s) | 6 | [Data][song-mall] |
| [Mellohi](../items/MusicDiscMellohi.md) · `mellohi` | 1:36 (96 s) | 7 | [Data][song-mellohi] |
| [Stal](../items/MusicDiscStal.md) · `stal` | 2:30 (150 s) | 8 | [Data][song-stal] |
| [Strad](../items/MusicDiscStrad.md) · `strad` | 3:08 (188 s) | 9 | [Data][song-strad] |
| [Ward](../items/MusicDiscWard.md) · `ward` | 4:11 (251 s) | 10 | [Data][song-ward] |
| [11](../items/MusicDisc11.md) · `11` | 1:11 (71 s) | 11 | [Data][song-11] |
| [Wait](../items/MusicDiscWait.md) · `wait` | 3:58 (238 s) | 12 | [Data][song-wait] |
| [Pigstep](../items/MusicDiscPigstep.md) · `pigstep` | 2:29 (149 s) | 13 | [Data][song-pigstep] |
| [otherside](../items/MusicDiscOtherside.md) · `otherside` | 3:15 (195 s) | 14 | [Data][song-otherside] |
| [5](../items/MusicDisc5.md) · `5` | 2:58 (178 s) | 15 | [Data][song-5] |
| [Relic](../items/MusicDiscRelic.md) · `relic` | 3:38 (218 s) | 14 | [Data][song-relic] |
| [Precipice](../items/MusicDiscPrecipice.md) · `precipice` | 4:59 (299 s) | 13 | [Data][song-precipice] |
| [Creator](../items/MusicDiscCreator.md) · `creator` | 2:56 (176 s) | 12 | [Data][song-creator] |
| [Creator Music Box](../items/MusicDiscCreatorMusicBox.md) · `creator_music_box` | 1:13 (73 s) | 11 | [Data][song-creator_music_box] |
| [Tears](../items/MusicDiscTears.md) · `tears` | 2:55 (175 s) | 10 | [Data][song-tears] |
| [Lava Chicken](../items/MusicDiscLavaChicken.md) · `lava_chicken` | 2:14 (134 s) | 9 | [Data][song-lava_chicken] |

Playback uses a finish threshold of **ceil(length × 20) + 20 elapsed game ticks**: the code adds a one-second, 20-tick end padding to the declared song length. The playing signal therefore follows that padded playback state, not an exact measurement of when the sound file becomes silent. Playback progress requires the Jukebox to be actively ticked. [Length and end condition][song] · [Player tick][song-player] · [Block ticker][jukebox] · [Active dispatch][level-tick] [chunk-tick]

Several discs share a Comparator value, so the signal is not a unique identifier for all 21 songs. For example, `strad` and `lava_chicken` both produce 9, while `pigstep` and `precipice` both produce 13. [Strad][song-strad] · [Lava Chicken][song-lava_chicken] · [Pigstep][song-pigstep] · [Precipice][song-precipice]

## Redstone output

The ordinary signal is **15 in every direction while the song player is active**, otherwise zero. An inserted disc that has finished playing leaves the block's `has_record` state true but no longer supplies that playing signal. Starting or stopping a song explicitly notifies adjacent blocks. [Signal callback][jukebox] · [Play/stop state][song-player] · [Neighbor notification][jukebox-entity]

A [Comparator](RedstoneComparator.md) reads the inserted disc's configured value from the table. That value remains after playback stops, until the disc is removed; an empty Jukebox reads zero. There is no front-only output restriction. Disc changes and block-entity changes update the relevant state and output notifications. [Comparator lookup][jukebox-entity] · [Block analog output][jukebox] · [Block-entity change notification][block-entity-change]

## Hoppers and a disc queue

A [Hopper](Hopper.md) directed into an empty Jukebox can insert one playable item. A Hopper directly below can extract its disc when able to transfer. There are no sided slot restrictions, but insertion requires the playable component and an empty Jukebox, and the extraction callback requires the receiving container to have an empty slot. [Container interface][single] · [Insertion/extraction predicates][jukebox-entity] · [Hopper discovery and transfer][hopper]

**A playing Jukebox powers adjacent ordinary Hoppers and locks them.** This is what keeps the Hopper below from immediately pulling out the playing disc. The Jukebox's container extraction method itself does not test whether the song has finished. When the padded playback period ends, the signal falls to zero, allowing that Hopper to extract, provided no other power source keeps it locked. [Jukebox strength 15][jukebox] · [Song-change neighbor update][jukebox-entity] · [Neighbor signal direction][signals] · [Hopper power lock][hopper-lock] · [Extraction predicate][jukebox-entity]

For an **untested source-derived queue**, use a Chest feeding a downward Hopper above the Jukebox, plus a Hopper beneath the Jukebox pointing into an output Chest. Supply valid discs and leave room in the output path. The first insertion starts playback and locks the adjacent Hoppers; after playback ends the lower Hopper can remove the old disc and the input Hopper can insert the next. The transfer order and normal Hopper cooldown mean this is not a promise of seamless audio between tracks. [Hopper transfer and cooldown](Hopper.md#transfer-behavior-and-rate) · [Container methods][jukebox-entity] · [Power behavior][jukebox] [hopper-lock]

## Playback and nearby mobs

The song's sound event is started and stopped by the client event handler, using the **Records** sound category. Its volume can therefore differ from other game sounds through client sound settings. The block also emits a `jukebox_play` game event every 20 ticks while logically playing. [Sound events and client playback][audio-events] [audio] · [Sound category][audio-source] · [Periodic game event][song-player]

Two wired examples are nearby **Parrots** receiving the record-playing callback and **Allays** reacting through their Jukebox game-event listener. Those callbacks can put the mobs into their dancing states. They respond to playback rather than selecting a special effect for an individual song. This page does not expand the separate [Parrot](../mobs/Parrot.md) or [Allay](../mobs/Allay.md) mob guides. [Client nearby-entity callback][audio] · [Parrot state][parrot] · [Allay listener and registration][allay] [listener-dispatch] · [Game-event registration][game-events]

## Saving, breaking, and moving

The placed block entity saves its inserted disc as `RecordItem` and, while a song is active, its `ticks_since_song_started`. Loading restores the logical song/timer if that time has not passed the song's end condition. **That restoration does not send a new play-sound event or seek audio to the saved position.** It establishes saved playback state; uninterrupted audible playback across reloads has not been tested. Eject and reinsert the disc when you want a fresh start. [Save/load][jukebox-entity] · [Restore without playback event][song-player] · [Audio event handling][audio-events] [audio]

Breaking the Jukebox **ejects the disc separately** through the active block-entity removal callback, while ordinary block loot returns the Jukebox item. Silk Touch does not pack the disc or playback timer into that drop. Removing the block also sends the stop-playing event. [Removal dispatch][chunk] · [Disc ejection and removal effects][jukebox-entity] · [Ordinary loot][jukebox-loot]

## Verification scope

Source-reviewed at `8d065c943710a8d4e3d609b0406dda95ed62cc63` on 2026-10-02. Block/item registration, recipe and loot, all 21 playable-item/song records, registry and sound-event wiring, insertion/ejection, active tick dispatch, Hopper power interactions, Comparator output, nearby-mob callbacks, and save/remove paths were inspected. No gameplay, listening, sound-duration, Hopper-queue, or reload test was run. No audio was copied into this documentation.

[jukebox-reg]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Blocks.java#L1970-L1974
[jukebox]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/JukeboxBlock.java
[jukebox-entity]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java
[jukebox-recipe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/jukebox.json
[planks]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/item/planks.json
[jukebox-item]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L492
[creative]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1087-L1092
[axe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[gate]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[jukebox-loot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/jukebox.json
[defaults]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L338
[discs]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L2296-L2358
[item-component]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Item.java#L406-L408
[disc-use]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/JukeboxPlayable.java#L47-L69
[single]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/ticks/ContainerSingleItem.java
[consume]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1086
[use]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L400
[song-player]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/JukeboxSongPlayer.java
[song-keys]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/JukeboxSongs.java
[song-load]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L125-L132
[sound-register]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/sounds/SoundEvents.java#L1101-L1121
[song-13]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/13.json
[song-cat]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/cat.json
[song-blocks]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/blocks.json
[song-chirp]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/chirp.json
[song-far]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/far.json
[song-mall]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/mall.json
[song-mellohi]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/mellohi.json
[song-stal]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/stal.json
[song-strad]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/strad.json
[song-ward]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/ward.json
[song-11]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/11.json
[song-wait]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/wait.json
[song-pigstep]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/pigstep.json
[song-otherside]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/otherside.json
[song-5]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/5.json
[song-relic]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/relic.json
[song-precipice]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/precipice.json
[song-creator]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/creator.json
[song-creator_music_box]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/creator_music_box.json
[song-tears]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/tears.json
[song-lava_chicken]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/jukebox_song/lava_chicken.json
[song]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/JukeboxSong.java
[level-tick]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/Level.java#L440-L459
[chunk-tick]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L710-L783
[block-entity-change]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L208-L221
[hopper]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java
[signals]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/SignalGetter.java#L60-L83
[hopper-lock]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/HopperBlock.java#L116-L131
[audio-events]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/client/renderer/LevelEventHandler.java#L115-L120
[audio]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/client/renderer/LevelEventHandler.java#L661-L689
[audio-source]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/client/resources/sounds/SimpleSoundInstance.java#L39-L43
[parrot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L187-L208
[allay]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java
[listener-dispatch]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/level/ServerLevel.java#L1913
[game-events]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/gameevent/GameEvent.java#L46-L47
[chunk]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L357
