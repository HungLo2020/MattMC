# Jukebox

The **Jukebox** item places `minecraft:jukebox`, which holds one playable disc. See [Jukebox](../blocks/Jukebox.md) for the song table, redstone outputs, Hopper transfers, and playback persistence. [Item registration][jukebox-item] · [Block behavior][jukebox]

## Obtaining

Craft one with **eight tagged planks surrounding one Diamond**. The [canonical recipe](../blocks/Jukebox.md#crafting-and-collecting) accepts mixed plank types from the bundled tag. It is also available in Creative. [Recipe][jukebox-recipe] · [Planks][planks] · [Creative entry][creative]

Ordinary mining returns **one Jukebox**. An axe is efficient, but a correct tool and Silk Touch are not required for the block drop. An inserted disc is ejected separately when the block is broken. [Registration][jukebox-reg] · [Axe tag][axe] · [Loot][jukebox-loot] · [Disc removal][jukebox-entity]

## Usage

Use the empty block with a playable disc to insert it and start playback. Use the occupied block to stop and eject that disc. The song ending on its own leaves the disc inside; it does not automatically repeat. [Insertion][disc-use] · [Use/ejection][jukebox] · [Song finish][song-player]

## Behavior

The placed Jukebox emits ordinary signal strength **15 while logically playing**. A Comparator instead reads the inserted song's configured value, including after playback has ended. [All 21 songs and values](../blocks/Jukebox.md#song-lengths-and-comparator-values) · [Signal callbacks][jukebox] · [Comparator lookup][jukebox-entity]

## Notes

Source-reviewed at `8d065c943710a8d4e3d609b0406dda95ed62cc63` on 2026-10-02. No gameplay or listening test was run. The canonical [Jukebox guide](../blocks/Jukebox.md) owns the playback, automation, and save/recovery details.

[jukebox-item]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L492
[jukebox]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/JukeboxBlock.java
[jukebox-recipe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/jukebox.json
[planks]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/item/planks.json
[creative]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1087-L1092
[jukebox-reg]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Blocks.java#L1970-L1974
[axe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[jukebox-loot]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/jukebox.json
[jukebox-entity]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java
[disc-use]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/JukeboxPlayable.java#L47-L69
[song-player]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/JukeboxSongPlayer.java
