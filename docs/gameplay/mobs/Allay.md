# Allay

An Allay (`minecraft:allay`) is a flying helper that collects **loose matching items** after you give it a sample. It carries those finds toward a chosen player or a remembered Note Block. It has **20 health points (10 hearts)**. [Health][health] · [Registered attributes][attributes] · [Collection and delivery activities][activities]

## Obtaining

Two checked structure routes can provide Allays:

- **[Pillager Outpost](../structures/PillagerOutpost.md#layout-and-possible-residents):** an optional cage template contains two Allays. The feature pool can select that template, and its placement includes the stored entities
- **[Woodland Mansion](../structures/WoodlandMansion.md#rooms-and-residents):** a possible first-floor cage room has four Allay-group markers. Each marker attempts 1–3 Allays when processed

[Outpost cage][outpost-template] · [Feature choice][outpost-pool] · [Pool placement][pool-place] · [Stored-entity placement][entity-place] · [Mansion room][mansion-template] · [Room selection][mansion-select] · [Marker creation][mansion-markers]

Neither structure promises that cage room in every generated example. Clear nearby hostiles before opening a cage. For deliberate placement, the ordinary listed [Allay Spawn Egg](../items/AllaySpawnEgg.md) is available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in Survival and Creative; this is separate from finding a structure resident. [Egg category entry][egg]

## Behavior

### Giving and taking the sample

Use a collection item, such as Cobblestone, on an empty-handed Allay. It takes **one** into its main hand and remembers the player who gave it. The usual item-consumption rule exempts players with Creative's infinite-materials ability. Special interactions such as naming and using spawn eggs can take priority over sample handoff. [Sample interaction][interact] · [Interaction order][interact-call] · [Consumption][sample-consume] [consume] · [Creative exemption][creative]

Use an **empty main hand** on an Allay holding a sample to take it back. This clears its remembered player and throws its collected cargo onto the ground. For an ordinarily assigned **one-item sample**, the required empty hand also leaves a free selected inventory slot for its return. Cargo is thrown as dropped items with a pickup delay; it is not inserted into that slot first. If the Allay is leashed to you, an interaction first detaches that leash. [Return interaction][interact] · [Selected-slot mapping][return-slot] · [Free-slot lookup][return-space] · [Inventory-add call][player-add] · [Inventory insertion][inventory-add] · [Cargo throw][throw] · [Leash interaction][lead]

This is not exclusive ownership: the sample-return branch does not require the player to be the one who assigned it. A player can take the sample and then give a new one. An existing Note Block preference is not erased by that return interaction. [Player-memory and item changes][interact]

### What it can collect

The sample is a filter for **dropped item entities**. The Allay does not harvest blocks or pull inventory out of a Chest. Its active behavior searches for wanted loose items, approaches them and carries them to its delivery target. [Item sensor][sensor] · [Installed activities][activities] · [Pickup dispatch][pickup-call]

The filter compares the **item type and potion contents**. Names, ordinary enchantments and damage values are not extra sample filters, so a named or enchanted example does not select only identically named or enchanted finds. Potion-content differences do matter. [Filter][filter]

There is **one cargo slot**, separate from the held sample. It can hold a compatible stack up to the item's stack limit; unstackable finds occupy it one at a time. Once cargo is present, stacking requires matching components, even though the sample filter is broader. An Allay carrying one named variant may therefore be unable to add a different variant until it unloads. [Cargo slot][cargo] · [Space/stack checks][cargo-stack] · [Merge behavior][stack-merge] · [Pickup transfer][inventory-carrier]

Pickup requires `mobGriefing` enabled, a held sample, room in cargo and no active pickup cooldown. The search checks a box extending 32 blocks horizontally and 16 vertically, then requires the item to be **less than 32 blocks away and visible**. It must still reach the item, and the item cannot have an active pickup delay. This is not a promise of collecting every item in a 32-block sphere. [Pickup eligibility][pickup] [filter] · [Search limits][sensor] · [Approach checks][pursue] · [Contact pickup][pickup-call]

### Returning finds to a player

With no valid Note Block preference, the remembered player must be in the **same server level**, **less than 64 blocks away**, and in **Survival or Creative**. The checked return-target function does not accept Adventure or Spectator. Stay close during transport instead of assuming an assigned Allay will find you across a dimension or a long trip. [Player-target conditions][player-target]

When close enough, the delivery behavior removes **one cargo item per throw** and creates a dropped item toward the target. It sets a **60-tick pickup cooldown**, nominally three seconds at 20 TPS, after throwing. The held sample stays in its hand. Delivery is a toss, so arrange your own collection area or [Hoppers](../blocks/Hopper.md); it does not insert the find straight into a player's inventory or Chest. [Delivery and cooldown][delivery] · [Actual dropped item][throw] · [Cooldown countdown][cooldown-tick]

## Note Block deliveries

Play a [Note Block](../blocks/NoteBlock.md) near the Allay to make that block the preferred delivery point. Its vibration listener has a **16-block radius**, listens for `note_block_play`, and receives the event after vibration travel. Wool can block the vibration path. A blocked Note Block that never emits the event cannot establish the preference; use the Note Block guide for its air/head rules. [Listener][listener] · [Allowed event][listenable] · [Event emission][note-emission] · [Range dispatch][range] · [Occlusion][vibration] · [Wool tag][wool] [wool-types] · [Travel time][travel]

The preference lasts **600 cooldown ticks**, nominally 30 seconds at 20 TPS, and a new heard event from that **same Note Block** refreshes it. A different Note Block does not simply overwrite an existing preference. Keep the selected block playing often enough if you want continued delivery there. [Remember/refresh][noteblock] · [Countdown activity][activities] · [Countdown removal][cooldown-tick]

The stored destination must still be a Note Block in the same dimension, no more than **1,024 blocks away along any coordinate axis**. Once invalid or expired, the delivery lookup clears it and tries the remembered player. Removing the block or letting the cooldown expire is therefore a way to release the assignment. The 16-block listening radius is separate from pickup range and from this stored-target validation. [Destination priority and clearing][noteblock] · [Stored-distance definition][global-range]

## Duplication

A [Jukebox](../blocks/Jukebox.md) playing a record can make a nearby Allay dance. This uses a separate Jukebox listener with a **10-block event radius**; a Note Block delivery preference alone does not enable duplication. Playback emits the play event repeatedly and sends a stop event when playback ends. Panic, an invalid Jukebox or moving out of dance range can also stop the dance. [Jukebox ticking][jukebox-ticker] [jukebox-blockentity] · [Play/stop events][jukebox-play] · [Event radius][events] · [Allay listener][jukebox-registration] [jukebox-listener] · [Dance controls][dance-start] [dance-stop] · [Panic][vibration-tick]

To duplicate one:

1. Get an Allay dancing near the playing Jukebox
2. Use **one [Amethyst Shard](../items/AmethystShard.md)** on it while its duplication cooldown is ready
3. On success, a second Allay is created at its position. Both receive a **6,000-tick cooldown**, nominally five minutes at 20 TPS

One Allay is sufficient; no second parent is required. The Shard is consumed subject to the normal Creative exemption. The new Allay is a fresh entity: the code does not copy the original's held sample, cargo or remembered player. Give it its own sample if you want it collecting for you. [Accepted item tag][shard-tag] · [Interaction priority][interact] · [New entity and both cooldowns][duplication] · [Consumption exemption][consume]

**A Shard is not always a duplication action.** If the Allay is not dancing or is still cooling down, an empty-handed Allay can take the Shard as its ordinary collection sample instead. Check the dance and wait for the cooldown before spending another Shard. Cooldowns advance while the entity ticks, not as a guaranteed five-minute wall-clock timer while unloaded. [Interaction branches][interact] · [Cooldown update/save][duplication] [death]

## Keeping it safe and recovering items

Allays heal **1 health point every 10 entity ticks** while alive on the server and do not take the ordinary fall-damage callback. Damage attributed to the currently remembered player is rejected, but that does not protect the Allay from other players or environmental threats. Secure the route and collection space rather than relying on regeneration. [Healing][regeneration] · [Player-damage and fall handling][damage]

Allays do not use ordinary distance-based despawning. Their cargo, duplication cooldown and relevant player/Note Block memories have save paths. This does not keep an unloaded Allay working or prevent death. They can be attached to a [Lead](../items/Lead.md); a sample assignment by itself is not a tether. [Retention and saved cargo][death] · [Memory codecs][save-memory] · [Brain save/load][brain-save] [brain-load] · [Leash eligibility][leashable] · [Lead attachment][lead]

The held item is useful as a collection sample, not an instruction to fight. The active behavior list contains no combat attack routine, and Allays reject dispenser equipment. Their entity loot table adds **no generated item reward**, but the death path separately drops carried cargo and the held sample unless an equipment-drop-prevention enchantment blocks the sample. Recover the sample with an empty main hand instead of killing a helper for it. [Active behaviors][activities] · [Dispenser refusal][pickup] · [Loot table][loot] · [Carried-item drops][death] · [Death dispatch][death-call]

## Notes

Related: [Pillager Outpost](../structures/PillagerOutpost.md) · [Woodland Mansion](../structures/WoodlandMansion.md) · [Amethyst](../blocks/Amethyst.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54` on 2026-10-02. Checked stored outpost residents, mansion marker selection, registered brain/listener callers, sample handoff/return, pickup filtering and capacity, item throws, player/Note Block target checks, vibration occlusion, Jukebox dancing, duplication consumption/cooldowns, saved state and damage/drop paths. The registered entity builds and ticks the reviewed behavior and registers both event listeners. [Registration][registration] · [Brain setup][brain] · [Brain tick][ai-call] · [Listener registration][dynamic-listeners] · [Server tracking caller][server-listeners]

No rescue, pickup, sorting, return, music, duplication, cooldown, damage or retention gameplay test was performed. Data packs, server rules and ticking can change the conditions; no farm design is certified.

[health]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L143-L157
[attributes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L111
[activities]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/AllayAi.java#L52-L97
[outpost-template]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/structure/pillager_outpost/feature_cage_with_allays.nbt
[outpost-pool]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/template_pool/pillager_outpost/features.json#L1-L88
[pool-place]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L138-L181
[entity-place]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L480-L513
[mansion-template]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/structure/woodland_mansion/2x2_a1.nbt
[mansion-select]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L47-L81
[mansion-markers]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L1232-L1260
[egg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1960-L1970
[interact]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L298-L330
[interact-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1103
[sample-consume]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L492-L494
[creative]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/player/Player.java#L1212-L1215
[player-add]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/player/Player.java#L1576-L1578
[inventory-add]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/player/Inventory.java#L249-L289
[lead]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Entity.java#L2146-L2175
[sensor]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/sensing/NearestItemSensor.java#L14-L34
[pickup-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L439-L458
[filter]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L349-L376
[cargo]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L102-L109
[cargo-stack]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/SimpleContainer.java#L89-L115
[stack-merge]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/SimpleContainer.java#L182-L211
[inventory-carrier]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/npc/InventoryCarrier.java#L16-L35
[pickup]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L280-L296
[pursue]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/GoToWantedItem.java#L17-L46
[player-target]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/AllayAi.java#L139-L159
[delivery]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/GoAndGiveItemsToTarget.java#L45-L79
[throw]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/BehaviorUtils.java#L81-L95
[cooldown-tick]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/CountDownCooldownTicks.java#L22-L42
[listener]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L562-L603
[listenable]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/game_event/allay_can_listen.json#L1-L5
[note-emission]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/NoteBlock.java#L87-L104
[range]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/gameevent/EuclideanGameEventListenerRegistry.java#L114-L123
[vibration]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L200-L255
[wool]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/occludes_vibration_signals.json#L1-L5
[travel]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L374-L402
[noteblock]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/AllayAi.java#L99-L137
[global-range]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/core/GlobalPos.java#L32-L34
[jukebox-ticker]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/JukeboxBlock.java#L111-L115
[jukebox-play]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/JukeboxSongPlayer.java#L55-L82
[events]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/gameevent/GameEvent.java#L44-L49
[jukebox-registration]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L116-L125
[dance-start]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L332-L342
[vibration-tick]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L270-L277
[shard-tag]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/item/duplicates_allays.json#L1-L5
[duplication]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L462-L494
[consume]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[regeneration]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L231-L244
[damage]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L172-L198
[death]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L425-L455
[save-memory]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/memory/MemoryModuleType.java#L130-L132
[brain-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L741-L744
[leashable]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/allay.json#L1-L4
[death-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1475
[registration]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L224-L227
[brain]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L128-L140
[ai-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L219-L244
[dynamic-listeners]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L383-L388
[server-listeners]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/level/ServerLevel.java#L1907-L1914
[wool-types]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/wool.json#L1-L20
[jukebox-blockentity]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L63-L65
[jukebox-listener]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L529-L559
[dance-stop]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/allay/Allay.java#L391-L405
[brain-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L808-L817

[return-slot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/PlayerEquipment.java#L14-L21
[return-space]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Inventory.java#L102-L109
