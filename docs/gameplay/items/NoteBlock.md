# Note Block

## Obtaining

Craft **one Note Block** with **eight planks surrounding one Redstone Dust** in a Crafting Table. The planks tag allows its listed plank types to be mixed. Note Blocks are also listed in Creative, and ordinary Survival mining returns one Note Block even by hand; an axe is efficient. Silk Touch is unnecessary, Fortune does not increase the drop, and explosions have a survival condition. [Recipe][recipe] · [Planks][planks] · [Creative entry][creative] · [Registration][registration] · [Axe tag][axe] · [Harvest check][harvest] · [Loot][loot]

## Usage

Place the item to build a tunable redstone sound source. The [Note Block guide](../blocks/NoteBlock.md) covers [tuning and player controls](../blocks/NoteBlock.md#tuning-and-player-controls), [every supported instrument](../blocks/NoteBlock.md#choosing-an-instrument), and [small circuits](../blocks/NoteBlock.md#small-circuit-examples).

## Behavior

Use cycles the stored note through **0–24**, then back to 0. Redstone plays on the **off-to-on transition**, so a steady input does not repeat. Base instruments come from the block below and need **air directly above**; standing heads above select fixed-pitch mob or custom sounds. A plain Player Head without a custom sound is silent, and wall heads above do not select the head instruments in this source snapshot. See the placed-block guide for [head details](../blocks/NoteBlock.md#standing-heads-and-custom-player-heads) and [timing](../blocks/NoteBlock.md#redstone-timing-and-feedback). [Controls and playback][note] · [Note range][note-range]

## Notes

- The item and placed block share the exact ID **`minecraft:note_block`**. [Item registration][item] · [Block registration][registration]
- An ordinary mined drop does not preserve tuning; placing it starts at note 0 and selects an instrument from its new neighbors. [Default and placement][selection] · [Loot][loot]
- Source-reviewed at `cf1c134b3f9ff634490e448fe26c335b90f82227` on 2026-10-02. No gameplay or listening test was run; the [placed-block guide](../blocks/NoteBlock.md#sources-and-verification) gives the verification scope.

[recipe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/note_block.json
[planks]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/item/planks.json
[creative]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1084-L1088
[registration]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L661-L665
[axe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/mineable/axe.json#L1-L5
[harvest]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/note_block.json
[note]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/NoteBlock.java#L49-L172
[note-range]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/properties/BlockStateProperties.java#L119
[item]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L1037
[selection]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/NoteBlock.java#L49-L85
