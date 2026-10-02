# Trial Spawner

A **Trial Spawner** (`minecraft:trial_spawner`) runs a finite encounter: it registers nearby participants, creates a configured wave, ejects rewards when its tracked wave is cleared, then cools down. It has separate normal and ominous configurations. The [ordinary Monster Spawner](MonsterSpawner.md) uses a different activation and spawning system. [Active state machine][states] · [Normal/ominous configuration][full-config]

## Finding and preserving one

Trial Spawners are placed by the current **Trial Chambers** generation route. The bundled structure uses jigsaw pools and an underground starting height from **Y −40 to −20**; that is a starting-height range, not the full height of every chamber. Structure spacing and biome eligibility do not guarantee a chamber at any chosen coordinate. [Structure][structure] · [Structure set][structure-set] · [Biome eligibility][biomes]

The checked Breeze route reaches a real template containing a waiting Trial Spawner with both `trial_chamber/breeze/normal` and `trial_chamber/breeze/ominous` configurations. Other checked chamber routes use different configured mobs, including the [Bogged](../mobs/Bogged.md#obtaining) and [Stray](../mobs/Stray.md#obtaining) ranged selections. A display mob identifies that spawner's configured encounter, not every enemy elsewhere in the chamber. [Breeze contents pool][breeze-pool] · [Breeze template][breeze-template] · [Display entity][empty-data]

**Breaking a Trial Spawner does not drop its block item, including with Silk Touch.** Its block loot has no pools, and its implementation adds no mining-XP reward. Preserve a found spawner if you want to repeat its trials. Its current hardness is 50, with no correct-tool drop gate; the bundled axe, hoe, pickaxe and shovel mining tags do not list it, so do not assume the ordinary pickaxe speed bonus applies. [Registration][registration] · [Empty loot][loot] · [Inherited break behavior][break-default] · [Pickaxe][pickaxe] · [Axe][axe] · [Hoe][hoe] · [Shovel][shovel]

No bundled crafting recipe produces the block. The item is available in Creative's Spawn Eggs tab; see the [Trial Spawner item](../items/TrialSpawner.md) for supplied-item and spawn-egg setup. [Creative entry][creative]

## Activation and participants

The default player range is **strictly less than 14 blocks**, measured between the player's block position and the spawner's block position. The normal detector excludes Creative and Spectator players. The first participants need a clear visual ray between their eye position and the spawner center; fluid alone is ignored by that ray test. [Default range][full-config] · [Player filter and visibility][players]

Detection runs every **20 game ticks**, staggered by block position. Once participants are already registered, later eligible players within range can be added without the initial visibility requirement. Registering new participants postpones the next spawn to at least **40 ticks** ahead. Walls therefore are not a reliable way to remain unregistered after an encounter has started. [Registration and scan timing][detection]

Participant UUIDs remain registered through the encounter. Simply leaving the 14-block range does not lower its quota or pause the active state in the same way as an ordinary spawner. This still requires a loaded, ticking world. The normal spawning gate requires non-Peaceful difficulty, `doMobSpawning` enabled and `spawnerBlocksEnabled` enabled. [Active processing][active] · [Spawn gate][spawn-gates]

## Wave size and spawning

The configured **total** is how many mobs must be spawned during the encounter; **simultaneous** limits how many tracked mobs may be present at once. Each extra registered player adds the configuration's increments, and the final values are rounded **down**. These representative bundled configurations have 20-tick spacing between successful spawns:

| Encounter configuration | Normal: total / simultaneous for one player | Ominous: total / simultaneous for one player | Added per extra player: total / simultaneous |
| --- | --- | --- | --- |
| Zombie | 6 / 3 | 6 / 3, with configured equipment | 2 / 0.5 |
| Breeze | 2 / 1 | 4 / 2 | 1 / 0.5 |
| Slime | 6 / 3 | 12 / 4 | 2 / 0.5 |

The rows resolve omitted JSON fields through the active configuration defaults. They are not a universal count for every spawner. For example, two registered players in the normal Zombie configuration require **8 total mobs while the simultaneous cap remains 3**; with three players, those values become 10 and 4. [Default values and rounding][config] · [Zombie normal][normal-zombie] / [ominous][ominous-zombie] · [Breeze normal][normal-breeze] / [ominous][ominous-breeze] · [Slime normal][normal-slime] / [ominous][ominous-slime]

The ordinary default spawn range is four blocks in each horizontal direction, with candidate feet positions one block below, level with or one above the spawner. Candidate positions must pass collision, visual-line, registered entity spawn rules, any custom light limits, and the mob's obstruction checks. An attempted spawn can fail, so the spacing is not a guaranteed output rate. [Default range][config] · [Spawn attempt][spawn] · [Custom light checks][custom-light]

**Torches are not a general off switch.** The trial spawn reason bypasses ordinary darkness checks in the standard monster predicate. Space, visibility and other applicable checks still matter. Keep enough room for the actual configured creature rather than assuming all mobs use the same clearance. [Trial light exception][light-exception] · [Monster predicate][monster-rules] · [Active spawn checks][spawn]

## Clearing, rewards and cooldown

The spawner advances when it has spawned its target total and its **tracked mob set is empty**. It tracks the UUIDs it created, rather than every hostile creature in the room. Dead, missing, different-dimension or sufficiently distant tracked entities can be removed from that set; the distance cutoff is more than 47 blocks between block positions. An opening reward shutter is therefore not proof that the whole chamber is safe. [Clear condition][active] · [Tracking removal][mob-tracking]

After a completed wave, the shutter-opening check occurs after **40 game ticks**. Reward ejections align to **30-tick intervals measured from the clear**, so the first ordinary ejection is at 60 ticks, followed by one every 30 ticks. These are source timings for continuous ticking, not measured wall-clock delays. [Reward states][reward-states] · [Timing helpers][reward-timing]

There is **one ejection per registered participant**. Rewards appear as loose items above the spawner; they are not privately assigned into each player's inventory and do not require each participant to make a final hit. The spawner chooses a reward **table once**, reuses it for that encounter's ejections, and rolls that table's contents for each ejection. [Selection and participant removal][reward-states] · [Upward item ejection][rewards]

| Reward mode | Table selection in the checked bundled configurations | Selected table's contents |
| --- | --- | --- |
| Normal | Equal key/consumables weights | One Trial Key, or a consumables roll |
| Ominous | Key weight 3, consumables weight 7 | One Ominous Trial Key, or an ominous consumables roll |

Those correspond to 50% and 30% **encounter table-selection chances**, not independent key chances for every player. No key is guaranteed. Normal consumables include Cooked Chicken, Bread, Baked Potatoes, Regeneration or Swiftness potions; ominous consumables include Cooked Beef, Baked Potatoes, Golden Carrots, Regeneration or Strength potions, with counts defined by the tables. [Normal table defaults][config] · [Normal key][normal-key] / [consumables][normal-consumables] · [Checked ominous weights][ominous-zombie] · [Ominous key][ominous-key] / [consumables][ominous-consumables]

The default cooldown is **36,000 game ticks**, nominally 30 minutes at 20 TPS, counted from the clear and including the reward sequence. When it expires, the spawner resets and can wait for a fresh trial. A normal cooldown has an ominous-conversion exception described below. [Cooldown default][full-config] · [Clear timestamp and reset][states]

For keys and their separate reward destination, see [Trial Key](../items/TrialKey.md), [Ominous Trial Key](../items/OminousTrialKey.md) and [Vault](Vault.md).

## Becoming ominous

A visible detected player with **Trial Omen**, or with **Bad Omen that can be converted**, can turn a non-ominous spawner ominous. Drinking an [Ominous Bottle](../items/OminousBottle.md) supplies Bad Omen; merely carrying the bottle is not the checked condition. Conversion removes Bad Omen and grants Trial Omen for **18,000 ticks per Bad Omen level**, nominally 15 minutes per level at 20 TPS. [Detection and effect choice][omen-detection] · [Conversion][omen-reset] · [Bottle effect][bottle]

Conversion can occur while waiting, during an active normal trial, or during a **normal cooldown**. The cooldown case can begin an ominous encounter before that normal timer would have expired. An already ominous spawner in cooldown skips player detection until its cooldown ends. [Detection gates][omen-detection] · [Cooldown transition][reward-states]

When an active encounter becomes ominous, its tracked mobs are discarded and its spawned count resets. The code has a special preserved-equipment drop path, but it does not perform an ordinary defeat-and-reward sequence for the discarded wave. It then uses the ominous configuration, which can change counts, equipment and reward tables. The Zombie example adds equipment without increasing its base total; Breeze and Slime examples increase their base totals. [Ominous reset][omen-reset] · [Configuration examples][ominous-zombie] [ominous-breeze][] [ominous-slime][]

Ominous encounters can also create floating item spawners above nearby registered players or tracked mobs. A successful creation starts a **160-tick interval** before the next attempt is due; available targets and space still affect success. The floating entity itself waits **60–120 ticks**, then launches a projectile downward or creates a loose item, depending on the chosen item's type. Watch overhead as well as the ordinary wave. [Target/position selection][omen-items] · [Item-spawner delay and release][floating-items]

The bundled hazard pool includes lingering potions, arrows and charge items. Its available choices are generated and cached for the spawner, then weighted by the rolled stack counts; the complete loot pool is not freshly rerolled independently for every overhead attack. These hazards are separate from the final key/consumable reward ejections. [Hazard pool][omen-pool] · [Cached choices][omen-cache]

## Interactions and custom setups

There is no ordinary player menu, redstone-input switch, storage inventory or dedicated comparator reading. The state controls emitted light and effects: waiting gives light level 4, active/reward states 8, inactive/cooldown 0. Those light levels are not redstone strengths. [Block and entity][block] [entity] · [State light values][states] · [Inherited interaction/signal defaults][no-redstone]

A plain placed Creative item starts inactive with no default mob in its empty configuration. A supplied spawn egg can set its entity through the active Spawner interface when spawners are enabled. This resets recorded trial state and replaces the normal and ominous spawn choices with that entity while retaining their quota/reward settings. It does not guarantee the chosen mob can pass its spawn checks. [Default setup][entity] · [Empty spawn data][empty-data] · [Egg interaction][egg] · [State reset][egg-reset] · [Configuration replacement][full-config]

The block entity serializes registered players, tracked mobs, counts, spawn/cooldown timestamps, selected spawn data and reward-table state. Those are world progress records, not item contents recovered by breaking the block. [Persistence][save] · [Saved fields][packed-state] · [Block loot][loot]

## Sources and verification

Source-reviewed on 2026-10-02 at `053cd852a8609f4002234ce0d445d3a345b551ae`. Checked active server tick wiring, participant detection, spawn and tracking gates, state transitions, normal/ominous configurations, reward selection, block acquisition and representative real Trial Chamber template routes. No gameplay, generated-world, timing, multiplayer, combat, loot-frequency, farm or save/reload test was run. Custom block-entity data, configuration resources, game rules and debug overrides can alter behavior; this is not an exhaustive chamber-layout or every-mob combat guide.

Related: [Trial Spawner item](../items/TrialSpawner.md) · [Monster Spawner](MonsterSpawner.md) · [Breeze](../mobs/Breeze.md) · [Bogged](../mobs/Bogged.md) · [Structures](../structures/Structures.md) · [Blocks](Blocks.md)

[states]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java#L31-L146
[full-config]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L382-L403
[structure]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/structure/trial_chambers.json
[structure-set]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/structure_set/trial_chambers.json
[biomes]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/trial_chambers.json
[breeze-pool]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/spawner/contents/breeze.json
[breeze-template]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/structure/trial_chambers/spawner/breeze/breeze.nbt
[empty-data]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerStateData.java#L227-L252
[registration]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/Blocks.java#L6775-L6786
[loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/blocks/trial_spawner.json
[break-default]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L357-L358
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[axe]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[hoe]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[shovel]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[creative]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1960-L1969
[players]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/PlayerDetector.java#L23-L51
[detection]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerStateData.java#L101-L159
[active]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java#L55-L105
[spawn-gates]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L147-L155
[config]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerConfig.java#L18-L95
[normal-zombie]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/trial_spawner/trial_chamber/melee/zombie/normal.json
[ominous-zombie]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/trial_spawner/trial_chamber/melee/zombie/ominous.json
[normal-breeze]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/trial_spawner/trial_chamber/breeze/normal.json
[ominous-breeze]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/trial_spawner/trial_chamber/breeze/ominous.json
[normal-slime]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/trial_spawner/trial_chamber/small_melee/slime/normal.json
[ominous-slime]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/trial_spawner/trial_chamber/small_melee/slime/ominous.json
[spawn]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L157-L233
[custom-light]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/SpawnData.java#L57-L80
[light-exception]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/entity/EntitySpawnReason.java#L24-L30
[monster-rules]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/entity/monster/Monster.java#L103-L118
[mob-tracking]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L267-L290
[reward-states]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java#L107-L145
[reward-timing]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerStateData.java#L213-L225
[rewards]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L236-L247
[normal-key]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/spawners/trial_chamber/key.json
[normal-consumables]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/spawners/trial_chamber/consumables.json
[ominous-key]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/spawners/ominous/trial_chamber/key.json
[ominous-consumables]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/spawners/ominous/trial_chamber/consumables.json
[omen-detection]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerStateData.java#L121-L178
[omen-reset]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerStateData.java#L181-L211
[bottle]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/component/OminousBottleAmplifier.java#L21-L33
[omen-items]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java#L149-L215
[floating-items]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/entity/OminousItemSpawner.java#L23-L105
[omen-pool]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/spawners/trial_chamber/items_to_drop_when_ominous.json
[omen-cache]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerStateData.java#L273-L298
[block]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/TrialSpawnerBlock.java#L20-L62
[no-redstone]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L202-L234
[entity]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/TrialSpawnerBlockEntity.java#L22-L92
[egg]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L75
[egg-reset]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L340-L344
[save]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L89-L97
[packed-state]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerStateData.java#L301-L321
