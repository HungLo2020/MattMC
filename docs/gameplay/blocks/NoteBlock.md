# Note Block

A **Note Block** (`minecraft:note_block`) plays one sound when you tune it, tap it in Survival, or change its redstone input from off to on. Choose a base instrument with the block directly underneath, or place a **standing head directly above** for a mob or custom sound. Ordinary instruments need **air immediately above** the Note Block. [Controls and playback][note-controls] · [Instrument selection][selection]

## Crafting and collecting

Craft **one Note Block** with **eight planks surrounding one Redstone Dust** in a Crafting Table. Each plank slot accepts the bundled `minecraft:planks` item tag, so the accepted plank types can be mixed. The [Note Block item](../items/NoteBlock.md) is also listed in Creative. [Recipe][recipe] · [Plank choices][planks] · [Item registration][item] · [Creative listing][creative]

An **axe is efficient**, but ordinary Survival mining can recover the block by hand: its registration does not require a correct tool for drops. The loot table returns **one Note Block**, with no Silk Touch requirement or Fortune bonus; explosions have a survival condition. Hardness and blast resistance are both **0.8**. [Registration][registration] · [Strength meaning][strength] · [Axe tag][axe] · [Harvest check][harvest] · [Mining dispatch][mining] · [Loot][loot]

Ordinary loot does not retain the selected note or instrument. Placing that ordinary drop starts at **note 0** and selects an instrument from its new neighbors. The block has no inventory or disc slot; use a [Jukebox](Jukebox.md) for music discs. [Default and placement][selection] · [Loot][loot] · [Note Block class][note-class]

## Tuning and player controls

- **Use** the block, normally right-click, to raise its stored note by one and attempt playback at the new setting. After **24**, the next use wraps to **0**. A newly placed block starts at 0, so its first tuning use plays setting 1. [Tuning][note-controls] · [Note range][note-range] · [Ascending values][integer-values] · [Wraparound][cycle]
- **Tap the attack/mining control in Survival**, normally left-click, to attempt playback without changing the note. Holding the control can break the block. Creative attack takes the immediate-break path, so use redstone to preview an unchanged setting there. [Attack callback][note-controls] · [Attack dispatch][attack-dispatch]
- Ordinary main-hand use can tune even with an item held. **Crouch/Sneak with a held item** to bypass tuning when placing a control or another block against it. Head items in the top-instrument tag have a special exception: using one on the Note Block's upper face passes the interaction to head placement. [Input routing][use-dispatch] · [Default item fallback][item-fallback] · [Head exception][note-controls] · [Accepted head items][head-tag]

Tuning changes the stored setting even if an obstruction prevents the sound. You can also tune while the Note Block is already powered; player use is separate from the redstone transition check. [Tuning and power callbacks][note-controls]

### Pitch range

The **16 base instruments** use **25 note settings, 0–24**. Playback calculates the pitch multiplier as `2^((note - 12) / 12)`: each step multiplies pitch by `2^(1/12)`, and 12 steps double it. The full range runs from **0.5× to 2×** the selected sound's base pitch. These are source-defined multipliers, not measured frequencies or a claim about a sound asset's named musical note. [Range][note-range] · [Pitch calculation][playback] · [Tunable instrument types][instruments]

| Stored note | Tuning uses from a new block | Pitch multiplier |
| ---: | ---: | ---: |
| 0 | 0, or 25 to return to it | 0.5× |
| 6 | 6 | About 0.7071× |
| 12 | 12 | 1× |
| 18 | 18 | About 1.4142× |
| 24 | 24 | 2× |

Head instruments always use **1× pitch** and do not make a note particle. Tuning still cycles the stored note while a head is installed, but it does not transpose the head sound. Removing the head exposes that stored setting again when a base instrument is selected. [Instrument types][instruments] · [Tuning][note-controls] · [Playback branches][playback]

## Choosing an instrument

The Note Block checks the **block directly above first**. If that block supplies a head instrument, it wins. Otherwise, the Note Block reads the instrument assigned to the **block directly below**. A head instrument below is rejected and falls back to **Harp**. Placement and changes immediately above or below update the selection; a material beside the Note Block does not choose its instrument. [Selection and vertical updates][selection]

For a base instrument, the upper block must be **air**, not merely transparent or non-solid. Glass above therefore stops playback even though Glass below selects Hat. A standing head is the explicit exception to this air requirement. [Playback gate][power] · [Glass registration][material-hat]

### Base instruments

These are **verified examples for every supported base instrument**, not an exhaustive list of every block that shares one. All example IDs use the `minecraft:` namespace. Use the specific block listed; similar names, textures, or breaking sounds do not guarantee the same instrument. The serialized instrument names below come from the instrument enum. [Instrument definitions][instruments] · [Selection][selection]

| Instrument | Example block directly below | Evidence |
| --- | --- | --- |
| Harp · `harp` | Dirt · `dirt` | [Dirt registration][material-harp] · [Default instrument][defaults] |
| Bass drum · `basedrum` | Stone · `stone` | [Registration][material-basedrum] |
| Snare · `snare` | Sand · `sand` | [Registration][material-snare] |
| Hat · `hat` | Glass · `glass` | [Registration][material-hat] |
| Bass · `bass` | Oak Planks · `oak_planks` | [Registration][material-bass] |
| Flute · `flute` | Clay · `clay` | [Registration][material-flute] |
| Bell · `bell` | Block of Gold · `gold_block` | [Registration][material-bell] |
| Guitar · `guitar` | White Wool · `white_wool` | [Registration][material-guitar] |
| Chime · `chime` | Packed Ice · `packed_ice` | [Registration][material-chime] |
| Xylophone · `xylophone` | Bone Block · `bone_block` | [Registration][material-xylophone] |
| Iron xylophone · `iron_xylophone` | Block of Iron · `iron_block` | [Registration][material-iron_xylophone] |
| Cow bell · `cow_bell` | Soul Sand · `soul_sand` | [Registration][material-cow_bell] |
| Didgeridoo · `didgeridoo` | Pumpkin · `pumpkin` | [Registration][material-didgeridoo] |
| Bit · `bit` | Block of Emerald · `emerald_block` | [Registration][material-bit] |
| Banjo · `banjo` | Hay Bale · `hay_block` | [Registration][material-banjo] |
| Pling · `pling` | Glowstone · `glowstone` | [Registration][material-pling] |

### Standing heads and custom Player Heads

Place one of these **standing forms directly above** the Note Block. The six mob instruments use their registered imitation sound events. Player Head selects a custom sound supplied on the head. Head acquisition, placement, retained data, and powered animations are covered in [Heads and Skulls](HeadsAndSkulls.md#note-block-sounds). [Standing registrations][heads] · [Instrument mapping][instruments] · [Sound-event registration][sounds]

| Standing block above | Selected instrument |
| --- | --- |
| Skeleton Skull · `minecraft:skeleton_skull` | `skeleton` |
| Wither Skeleton Skull · `minecraft:wither_skeleton_skull` | `wither_skeleton` |
| Zombie Head · `minecraft:zombie_head` | `zombie` |
| Creeper Head · `minecraft:creeper_head` | `creeper` |
| Dragon Head · `minecraft:dragon_head` | `dragon` |
| Piglin Head · `minecraft:piglin_head` | `piglin` |
| Player Head · `minecraft:player_head` | `custom_head` |

A **plain Player Head is silent** on the custom-head path if it has no Note Block sound ID. A profile or custom name does not supply that separate value. Placement can transfer the head item's `minecraft:note_block_sound` component into its block entity; playback reads the value from that head above. The enum's placeholder sound is not used as a missing-value fallback. [Head component storage][head-data] · [Component registration][sound-component] · [Placement transfer][head-placement] · [Missing-sound check][playback]

**Wall heads do not work as top instruments in this source snapshot.** Their registrations use fresh default properties that copy loot and display name, but not the standing head's instrument. A wall head directly above therefore makes selection fall back to the block below; because the wall head is not air, it then blocks ordinary playback. Use the standing form. This is traced source behavior, not an in-game sound test. [Wall registrations][heads] · [Wall-property helper][wall-properties] · [Default Harp][defaults] · [Selection][selection] · [Air gate][power]

## Redstone, timing, and feedback

A Note Block plays on an **off-to-on transition** of its combined neighboring input. Any positive accepted signal is enough; signal strength does not set pitch or volume. Continuous power produces one activation, and turning power off is silent. Every contributing input must be off before another rising transition can occur. [Power callback][power] · [Six-neighbor signal check][signals]

Removing an obstruction while power remains on does not create a new power transition. Clear the top, then turn the input fully off and back on, or use the player controls. The powered state is still updated even when the playback gate blocks a sound. [Power state and playback gate][power]

The block has **no built-in repeat clock, sustained-note control, or scheduled musical delay**. Playback requests go through the server's block-event queue. Identical requests for the same Note Block can merge while still queued, and playback reads the block's current note and instrument when the event is handled. Do not assume rapid same-tick toggles preserve every requested sound or tuning step. Use deliberate spacing with [Repeaters](RedstoneRepeater.md). [Note Block callbacks][note-class] · [Set-based queue][event-set] · [Event identity][event-data] · [Active dispatch][event-dispatch] · [Playback state lookup][playback]

Tunable base instruments request a **note particle above the block**, using the stored note to choose its color. The sound uses the **Records** category and volume parameter **3**, independently of redstone strength. Check that category in client sound settings if playback is unexpectedly quiet. These are playback parameters, not a measured audible range or loudness. [Particle and sound request][playback] · [Particle color][particle] · [Client event dispatch][client-event] · [Local event callback][local-event]

The Note Block itself provides **no dedicated redstone output or Comparator value for its selected note**. Its `powered` state records the input; it is not a song-playing output like a Jukebox's. Ordinary signal transmission through a powered solid block is a separate circuit behavior. [Inherited signal/analog defaults][output-defaults] · [Ordinary conduction][signals] · [Note Block class][note-class]

Passing the air/head gate also emits the `note_block_play` game event, assigned vibration frequency **10**. It is independent of the eventual sound: a Player Head with no custom sound can pass that gate and emit the event before its sound lookup fails. Do not use a game-event response as proof that audio played. The [Allay](../mobs/Allay.md) has a listener for this event. [Event emission][power] · [Missing custom sound][playback] · [Vibration frequency][vibration] · [Allay listener][allay]

## Small circuit examples

These layouts are **source-derived and have not been built or timed in game**. Start with a short circuit, leave ordinary instruments open above, and wait for the input to turn off before retriggering.

### One-note doorbell

1. Place the Note Block on the desired base material, with air directly above it.
2. Tune it with the use control.
3. Crouch-place a [Stone Button](Buttons.md#stone-button) against a side of the Note Block, then release crouch and press the button.
4. The button's powered transition should play the note once. Its 20-game-tick press does not cause continuous playback, and release does not play another note. Wait for release before pressing again.

The Note Block's ordinary full-cube shape supports the side-mounted button. Button presses supply signal 15 and notify neighboring blocks; the Note Block responds only to the rising transition. [Block shape][shape] · [Button support][button-support] · [Press and output][button] · [Button notifications][button-neighbors] · [Stone timing][stone-button] · [Note Block input][power]

### Two-note sequence

Split a button's output into **two short, separate supported dust branches**. Put a Repeater at the end of each branch, with each Repeater's output pointing directly into its own Note Block. Keep the branches from powering the other Note Block directly, and leave the Repeaters unlocked. Tune the Note Blocks separately. [Wire connections](RedstoneDust.md) · [Repeater direction and locking](RedstoneRepeater.md) · [Directional output and scheduling][diode]

Set the first Repeater to **1** and the second to **4**. When both receive the same rising input, their configured delays are **2 and 8 game ticks**, so their output activations are nominally **6 game ticks apart**. At the normal 20-game-tick rate, that is 0.3 seconds; lag, tick-rate changes, and actual wiring can affect the result. This is a timing design from the scheduling code, not measured audio synchronization. [Delay calculation][repeater] · [Scheduled transitions][diode] · [Tick conventions](../redstone/Redstone.md#strength-is-not-duration)

## Sources and verification

Source-reviewed at `cf1c134b3f9ff634490e448fe26c335b90f82227` on 2026-10-02. The one block/item registration, recipe and loot, all 23 instrument types, representative material registrations, head and wall-head selection, player dispatch, power transitions, queued event dispatch, pitch/particle/sound parameters, and example control timing were inspected. No gameplay, circuit simulation, listening, audio-frequency, audible-range, or timing test was run. No audio assets were copied into this guide.

[note-controls]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/NoteBlock.java#L87-L137
[selection]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/NoteBlock.java#L49-L85
[recipe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/note_block.json
[planks]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/item/planks.json
[item]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L1037
[creative]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1084-L1088
[registration]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L661-L665
[strength]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[axe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/mineable/axe.json#L1-L5
[harvest]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mining]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/note_block.json
[note-class]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/NoteBlock.java
[note-range]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/properties/BlockStateProperties.java#L119
[integer-values]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/properties/IntegerProperty.java#L12-L28
[cycle]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/StateHolder.java#L46-L53
[attack-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L159-L197
[use-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L399
[item-fallback]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L202-L210
[head-tag]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/item/noteblock_top_instruments.json
[playback]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/NoteBlock.java#L135-L172
[instruments]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/properties/NoteBlockInstrument.java#L8-L67
[power]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/NoteBlock.java#L87-L104
[material-hat]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L620-L630
[material-harp]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L127-L130
[defaults]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1022
[material-basedrum]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L73-L75
[material-snare]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L313-L317
[material-bass]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L139-L142
[material-flute]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L1962-L1964
[material-bell]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L1085-L1093
[material-guitar]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L811-L814
[material-chime]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L3282-L3285
[material-xylophone]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L4344-L4353
[material-iron_xylophone]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L1094-L1102
[material-cow_bell]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L1996-L2007
[material-didgeridoo]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L2332-L2341
[material-bit]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L2623-L2631
[material-banjo]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L3189-L3193
[material-pling]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L2053-L2062
[heads]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L2740-L2803
[sounds]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/sounds/SoundEvents.java#L1219-L1240
[head-data]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/SkullBlockEntity.java#L38-L107
[sound-component]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/core/component/DataComponents.java#L275-L280
[head-placement]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/BlockItem.java#L64-L103
[wall-properties]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[signals]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/SignalGetter.java#L61-L83
[event-set]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerLevel.java#L207-L208
[event-data]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/BlockEventData.java#L1-L7
[event-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerLevel.java#L1182-L1217
[particle]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/client/particle/NoteParticle.java#L12-L49
[client-event]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java#L1516-L1523
[local-event]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/Level.java#L816-L818
[output-defaults]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L224-L234
[vibration]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L79-L84
[allay]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L576-L603
[shape]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L321-L327
[button-support]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock.java#L27-L55
[button]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L87-L144
[button-neighbors]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L183-L190
[stone-button]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L1915-L1915
[diode]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L49-L113
[repeater]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/RepeaterBlock.java#L32-L50
