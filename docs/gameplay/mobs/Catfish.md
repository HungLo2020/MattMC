# Catfish

**Catfish** (`minecraft:catfish`) come in three sizes. Small and medium fish collect dropped items; large fish can swallow other small mobs. They can spit their contents back out, but **their current bucket-release path does not put the fish back into the world**. Do not use a Catfish bucket as a dependable way to move a valued fish or its cargo. [Registration][c-id] · [Collection][c-pickup] · [Swallowing][c-swallow] · [Custom bucket release][c-bucket-release]

## Obtaining

The [Catfish Spawn Egg](../items/CatfishSpawnEgg.md) and all three named Catfish buckets are ordinary category-listed items, available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. The egg provides working entity placement; the bucket items have the separate release limitation below. [Egg registration][c-egg] · [Egg category][c-category] · [Bucket registrations][c-buckets] · [Bucket category][c-bucket-list] · [Egg placement][egg-placement]

No Catfish entry was found in the checked 68 biome definitions, 34 structure definitions or 1,202 structure templates. Its standalone water predicate has no active SpawnPlacements registration. The separate spawn-roll method also does not create a population without a spawn route. Do not assume a river, swamp or cave habitat from the imported class alone. [Bundled data][bundled-data] · [Active placement registrations][spawn-rules] · [Species predicate and roll][c-spawn-predicate]

## Sizes and initialization

| Size | Base body width × height | Maximum health |
| --- | --- | --- |
| Small | 0.9 × 0.6 blocks | 10 points / 5 hearts |
| Medium | 1.25 × 0.9 blocks | 20 points / 10 hearts |
| Large | 1.9 × 0.9 blocks | 30 points / 15 hearts |

The size state selects the body and renderer, and changing it updates maximum health. These are **size variants**, not baby-to-adult growth stages. [Dimensions and base attributes][c-stats] · [Health update][c-size] · [Dimension selection][c-finalize] · [Renderer][c-render] · [Attribute registration][c-attr]

Ordinary initialization first chooses medium with a **35%** test, otherwise small. A separate **10%** roll can then replace that result with large, but only in the huge-Catfish biome tag or with a Bucket/Command spawn reason. The huge-biome tag has no bundled definition, so ordinary egg placement in the checked biomes selects small or medium. [Initialization][c-finalize] · [Tag declaration][c-tags] · [Bundled tags][tag-data]

With command permission, the normal `/summon minecraft:catfish` route runs Command initialization and can therefore select large. Its command requires permission level 2. That admin route is separate from natural spawning and from the currently incomplete large-bucket release. [Active command and initialization][summon] · [Size conditions][c-finalize]

## Behavior

### Collecting dropped items

A small or medium Catfish in water periodically searches nearby entities and approaches **dropped items older than 35 ticks**. This is not limited to edible items. Within reach and line of sight it draws the item toward its mouth and tries to store the stack. There is no player-facing chest inventory screen; retrieve contents through spitting. [Goal installation][c-stats] · [Item filter][c-target-filter] · [Approach and pickup caller][c-eat-goal] · [Storage][c-pickup]

Storage capacity is not consistently initialized for medium fish in this snapshot. The constructor creates three slots before size selection; only the save-data load path rebuilds a medium fish's inventory as nine slots. Do not assume every newly placed medium fish has nine slots or use it as a reliable bulk container. [Constructor and inventory creation][c-stats] [Inventory construction][c-inventory] · [Size update][c-size] · [Reload rebuild][c-save]

### Large Catfish and swallowed mobs

A large Catfish instead selects **other mobs at most one block tall**, excluding Catfish and any entity type in the ignore-eating tag. That tag has no bundled entries here. The selector requires a `Mob`, so it does not select players through this feeding goal. Babies and small pets can be candidates: keep them out of its pool. [Target filter][c-target-filter] · [Tag definition and data][c-tags] [Bundled tags][tag-data]

When close enough with line of sight, the Catfish saves the mob's type/data and removes it from the world until it is spat out. It holds one swallowed mob at a time. A released mob is loaded from the stored data and has its health set to **25% of maximum, with a minimum of 2 points**; this is not safe transport at full health. Stored state is cleared only after adding the released creature succeeds. [Target movement and swallowing caller][c-eat-goal] · [Saved creature][c-swallow] · [Release and health][c-spit]

### Getting the contents back

Use a **Sea Pickle item on the Catfish** to make it spit. The interaction does not consume the pickle. A small or medium fish releases the first occupied inventory slot as a loose item stack per call; repeat until empty. A large fish attempts to release its swallowed creature. Taking accepted damage also triggers spitting, and a nearby placed Sea Pickle can trigger repeated attempts. Prefer the item interaction over injuring the fish. [Sea Pickle interaction and damage][c-use] · [Spit behavior][c-spit] · [Placed-pickle detection][c-pickle]

After spitting, the feeding cooldown is **60–119 ticks**, nominally three to just under six seconds at 20 TPS. The separate search scheduling and travel can add time before another pickup. Sea Pickles are a release control, not breeding food. [Cooldown][c-spit] · [Search gate][c-eat-goal]

### Lures, care and persistence

No working held-item lure or block fascination is established by the bundled tags. Both installed goals read Catfish-specific tags that have no definitions; the method named for Sea Lanterns still checks the missing block tag. Do not assume a Sea Lantern or a familiar upstream food attracts these fish. [Installed lure][c-stats] · [Block fascination test][c-fascination] · [Tag declarations][c-tags] · [Bundled tags][tag-data]

Keep Catfish **in water**. The inherited WaterAnimal tick resets air in water and damages a dry fish when its air runs out; ordinary full air gives about **16 seconds** before the first 2-point hit, then continued damage. Catfish cannot be led on a Lead. They are not ageable animals and have no food-breeding or taming interaction. A matching spawn egg does not create a baby through the shared helper because they do not implement baby state. [WaterAnimal air and leash rules][water-base] [Leash rule][water-lead] · [Air maximum and threshold][air-max] [Drowning threshold][air-damage] · [Class][c-class] · [Goals][c-stats] · [Species interaction][c-use] · [Baby helper][egg-baby] · [Default baby setter][baby-setter] · [Default baby state][baby-state]

A name, bucket-origin flag, swallowed creature or nonempty item inventory makes the Catfish persistent against ordinary distance despawning. An unnamed empty fish without those flags can disappear at distance. Its world save stores size and carried contents. [Persistence and stored-item drops][c-inventory] · [World save and load][c-save]

## Bucket capture and release limitation

A **Water Bucket** can capture a living Catfish. The resulting item is [Small](../items/BucketOfSmallCatfish.md), [Medium](../items/BucketOfMediumCatfish.md) or [Large Catfish Bucket](../items/BucketOfLargeCatfish.md) according to its current size; the active registry aliases resolve to those actual items. Capture removes the fish and writes its size/cargo data to the bucket, with common health/name data stored separately. [Capture dispatch][c-use] · [Common capture][bucket-capture] · [Species data and bucket selection][c-bucket-save] · [Registry aliases][c-bucket-alias]

**The release is incomplete.** The custom Catfish bucket creates and configures a fish, loads custom data and selects size from the bucket item, but never adds that entity to the server level. The ordinary bucket-use caller can still place water and return an empty Bucket in Survival. The checked code therefore does not support a successful capture-and-release round trip. This is a source-confirmed missing call, not a reproduced gameplay test. The [Small Catfish Bucket guide](../items/BucketOfSmallCatfish.md#usage) owns the shared item details. [Custom release method][c-bucket-release] · [Active bucket-use caller][bucket-use] · [Normal creature creation versus addition][entity-create]

## Drops

The default Catfish loot table and its medium/large table names are all absent from the bundled entity loot directory. There is no configured Raw Catfish death drop in this snapshot. Catfish death separately drops stored item inventory and, for a large fish, calls the swallowed-creature release method. Those stored contents are distinct from newly generated loot. [Size-specific saved loot names][c-save] · [Bundled entity loot][loot-data] · [Missing-table fallback][loot-fallback] · [Stored-content death handler][c-inventory] · [Death dispatch][death-call]

The separate [Raw Catfish](../items/RawCatfish.md) and [Cooked Catfish](../items/CookedCatfish.md) food items are browser-listed. A qualifying player-credited kill can give **1–3 base XP** under the ordinary `doMobLoot` rule. [Food item listings][c-food-list] · [WaterAnimal XP][water-xp] · [Experience gate][death-call]

## Notes

The shared bucket-release limitation is tracked in [#801](https://github.com/HungLo2020/MattMC/issues/801).

The mob is registered as `MobCategory.WATER_AMBIENT` with `EntityCatfish` from bundled Alex's Mobs content. Its three size renderers are active. Buckets, fascination tags and absent loot tables are current integration limits, not assurances that another mod version's behavior works here. [Registration][c-id] · [Renderer registration][render-register]

Related: [Catfish Spawn Egg](../items/CatfishSpawnEgg.md) · [Sea Pickle](../items/SeaPickle.md) · [Mobs](Mobs.md)

Source-reviewed at `a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1` on 2026-10-02. Active registration, source/data spawning, size initialization, item/mob collection, save/load, release controls, bucket caller and loot fallback were traced. No gameplay or storage-loss test was run.

[c-id]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/EntityType.java#L360-L366
[c-pickup]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L471-L494
[c-swallow]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L501-L525
[c-bucket-release]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/item/ItemModFishBucket.java#L39-L69
[c-egg]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Items.java#L1815
[c-category]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1992
[c-buckets]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Items.java#L1549-L1563
[c-bucket-list]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1437-L1439
[egg-placement]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L76-L103
[bundled-data]: https://github.com/HungLo2020/MattMC/tree/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L87-L178
[c-spawn-predicate]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L161-L167
[c-stats]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L79-L116
[c-size]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L231-L238
[c-finalize]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L430-L447
[c-render]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/client/render/RenderCatfish.java#L28-L59
[c-attr]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L136
[c-tags]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L11-L16
[tag-data]: https://github.com/HungLo2020/MattMC/tree/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/tags
[summon]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/server/commands/SummonCommand.java#L35-L105
[c-target-filter]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L585-L590
[c-eat-goal]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L625-L671
[c-inventory]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L119-L159
[c-save]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L377-L428
[c-spit]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L527-L578
[c-use]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L357-L375
[c-pickle]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L195-L228
[c-fascination]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L691-L759
[water-base]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L39-L59
[water-lead]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L67-L70
[air-max]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/Entity.java#L2660-L2662
[air-damage]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[c-class]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L70-L91
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L181
[baby-setter]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/Mob.java#L1264-L1265
[baby-state]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/LivingEntity.java#L532-L534
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[c-bucket-save]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L248-L300
[c-bucket-alias]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/item/AMItemRegistry.java#L27-L31
[bucket-use]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/BucketItem.java#L72-L92
[entity-create]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/EntityType.java#L1736-L1776
[loot-data]: https://github.com/HungLo2020/MattMC/tree/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/loot_table/entities
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[death-call]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1486
[c-food-list]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1720-L1721
[water-xp]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L34-L37
[render-register]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L78-L113
