# Parrot

Tame a **Parrot** with seeds, then let it follow you or ride on a shoulder. Parrots have **6 health points (3 hearts)** and cannot breed. **Never feed one a Cookie**: its poisonous-food interaction applies Poison and normally inflicts lethal damage immediately. [Registered attributes][panda-parrot-attrs] · [Health][parrot-stats] · [Taming and poisonous food][parrot-use] · [Cookie tag][parrot-poison-tag] · [No breeding][parrot-no-breed]

## Obtaining

### Finding wild Parrots

The bundled [Jungle and Bamboo Jungle](../biomes/JunglesAndSwamps.md) creature lists both include Parrots at **weight 40**, in requested groups of **1–2**. **Sparse Jungle has no Parrot entry.** Candidate weights do not promise a spawn percentage or a number of birds in every area. [Jungle][jungle] · [Bamboo Jungle][bamboo-jungle] · [Sparse Jungle][sparse-jungle]

Their registered natural placement calls a Parrot-specific predicate requiring **raw brightness at least 9** and a block from the Parrot support tag below. The tag includes Grass Block, Leaves, Logs and Air; the caller still applies its ground-placement and obstruction checks, so the Air tag entry is not a promise of free-floating spawns. [Placement registration][parrot-placement] · [Parrot check][parrot-spawn] · [Support tag][parrot-ground] · [Light threshold][animal-spawn] · [Natural checks][spawn-rules]

A newly finalized spawn chooses among **five color variants**: red-blue, blue, green, yellow-blue and gray. The variant is saved. A [Parrot Spawn Egg](../items/ParrotSpawnEgg.md) provides another creation route; the [inventory item browser](../mechanics/InventoryBrowser.md) supplies ordinary listed items in Survival or Creative. [Spawn selection][parrot-init] · [Variant list][parrot-color] · [Save/load][parrot-save]

## Behavior

### Taming food

Use any of these on an **untamed** Parrot:

- [Wheat Seeds](../items/WheatSeeds.md)
- [Melon Seeds](../items/MelonSeeds.md)
- [Pumpkin Seeds](../items/PumpkinSeeds.md)
- [Beetroot Seeds](../items/BeetrootSeeds.md)
- [Torchflower Seeds](../items/TorchflowerSeeds.md)
- [Pitcher Pod](../items/PitcherPod.md)

Each accepted feeding consumes one item in ordinary Survival and has a **1-in-10 taming chance**. Success assigns the feeding player as owner. This is a chance per attempt, not a guarantee after ten seeds. [Food tag][parrot-food-tag] · [Interaction and roll][parrot-use] · [Owner assignment][tame-owner] · [Consumption][consume]

These foods do **not** heal a tamed Parrot or put it into love mode. The species returns false for breeding food and mating, returns no breeding offspring, and always reports itself as adult. Protect a bird you want to keep; extra seeds are not a recovery treatment. [Food and taming branches][parrot-use] · [No offspring][parrot-no-breed] · [Adult-only state][parrot-init]

### Following and sitting

Interact with your own **grounded** Parrot, preferably with an empty hand, to toggle its sit order. The handler checks whether it is on the ground, so wait for it to land. Damage cancels that order. While free to move, its follow goal starts when it is at least **5 blocks** away and tries to approach within **1 block**. It can attempt to teleport near its owner at **12 blocks or farther**, but a valid destination still has to pass path and collision checks. [Owner interaction][parrot-use] · [Damage response][parrot-hurt] · [Registered distances][parrot-init] · [Follow goal][follow] · [Teleport checks and movement restrictions][follow-teleport]

A sitting bird, a passenger or a leashed bird cannot use the normal owner-follow route. A Lead is available for deliberate movement, but also prevents the shoulder-landing goal from finishing. Parrots slow their descent and skip ordinary fall-damage handling; this does not protect their three hearts from other hazards. [Follow restrictions][follow-teleport] · [Lead eligibility][mob-lead] · [Shoulder gate][shoulder-goal] · [Descent][parrot-flight] · [Fall handler][parrot-fall]

### Shoulder rides

Let a tamed, unsitting, unleashed Parrot touch you while you stand on land. Its shoulder goal requires its owner not to be spectating, ability-flying, in water or in Powder Snow. The player's acceptance check also rejects riding another entity or being off the ground. A newly ticking bird waits **more than 100 ticks** before it can land. There are **two shoulder slots**. [Landing goal][shoulder-goal] · [Initial wait and transfer][shoulder-mob] · [Player acceptance][shoulder-player]

To release it, step down far enough to accumulate **more than 0.5 block of fall distance**, enter water or Powder Snow, sleep, or use ability-flight. A damage event that reaches the player's damage handler also asks to release shoulder entities. Release attempts work only after **more than 20 ticks have passed since the last shoulder landing**; an earlier one-shot trigger may need repeating. On release, the saved bird is recreated beside you with its owner restored. Keep the landing area safe before dismounting it. [Release triggers and recreation][shoulder-player] · [Damage caller][shoulder-hurt]

### Cookies, sounds and music

**Cookies are dangerous to wild and tamed Parrots.** The interaction consumes one Cookie in ordinary Survival, applies **900 ticks of Poison** and normally calls an effectively lethal player-attack damage amount. Taming does not protect it. See [Cocoa](../blocks/Cocoa.md#uses) for the Cookie ingredient route, and keep that food out of your hand when interacting with birds. [Poisonous-food tag][parrot-poison-tag] · [Complete interaction][parrot-use]

Parrots imitate selected nearby mobs, including Creepers, Creakings, Breezes and many other threats. The nearby check searches a box expanded **20 blocks** around the sound source and makes random selections. Outside Peaceful, an ordinary ambient call can also choose an imitation **without a matching nearby mob**. Treat the sound as a reason to look around, not a reliable detector. Shoulder birds also use this sound system. [Imitation list][parrot-sound-map] · [Nearby caller][parrot-dance] · [Nearby imitation][parrot-sounds] · [Unprompted ambient imitation][parrot-ambient] · [Shoulder sound caller][shoulder-player]

A nearby playing [Jukebox](../blocks/Jukebox.md) can make a Parrot dance. The client playback event notifies nearby living entities; the Parrot keeps its dance state only while the recorded block remains a Jukebox and it stays within **3.46 blocks of its center**. Stopping playback sends a stop notification. This is a display behavior, not a breeding or healing method. [Playback notifications][jukebox-notify] · [Parrot dance state][parrot-dance]

## Notes

- Entity ID: `minecraft:parrot`; registered size **0.5×0.9 blocks** [Registration][parrot-reg]
- Its installed goals focus on fleeing, following, sitting, wandering and shoulder landing; it has no installed combat target goal [Goals][parrot-init]
- It inherits the animal rule against ordinary distance despawning [Persistence][animal-persist]
- Its normal death table gives **1–2 Feathers**, with a Looting count increase, when mob loot is enabled. Killing one does not yield a special taming or music item [Loot][parrot-loot] · [Loot gate][loot-gate] · [Death dispatch][death] · [Loaded table][loot-load]

## Related pages

- [Jungle and Bamboo Jungle](../biomes/JunglesAndSwamps.md)
- [Jukebox](../blocks/Jukebox.md)
- [Cocoa and Cookies](../blocks/Cocoa.md)
- [Parrot Spawn Egg](../items/ParrotSpawnEgg.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`. Checked the active registrations, loaded biome and item data, relevant AI and interaction callers, shared breeding/retention rules, and loot resolution. No in-game spawning, feeding, breeding, transport, planting, combat, sound or drop test was run. Tick timings assume a ticking server at 20 ticks per second; data packs, settings and custom entity data can change the result. The biome inventory covered all 68 bundled biome definitions.

[panda-parrot-attrs]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L205-L206
[parrot-stats]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L170-L176
[parrot-use]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L247-L300
[parrot-poison-tag]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/item/parrot_poisonous_food.json#L1-L5
[parrot-no-breed]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L312-L321
[jungle]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/jungle.json#L1-L226
[bamboo-jungle]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/bamboo_jungle.json#L1-L226
[sparse-jungle]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/biome/sparse_jungle.json#L1-L213
[parrot-placement]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L134-L134
[parrot-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L302-L306
[parrot-ground]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/parrots_spawnable_on.json#L1-L8
[animal-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L105-L114
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L253-L287
[parrot-init]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L140-L168
[parrot-color]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L516-L527
[parrot-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L442-L452
[parrot-food-tag]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/item/parrot_food.json#L1-L10
[tame-owner]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L154-L173
[consume]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1113-L1120
[parrot-hurt]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L394-L402
[follow]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/goal/FollowOwnerGoal.java#L35-L89
[follow-teleport]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L232-L284
[mob-lead]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L81-L84
[shoulder-goal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/goal/LandOnOwnersShoulderGoal.java#L14-L41
[parrot-flight]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L211-L227
[parrot-fall]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L308-L310
[shoulder-mob]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/ShoulderRidingEntity.java#L21-L44
[shoulder-player]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/level/ServerPlayer.java#L760-L827
[shoulder-hurt]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/player/Player.java#L721-L733
[parrot-sound-map]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L81-L121
[parrot-dance]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L186-L209
[parrot-sounds]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L229-L245
[parrot-ambient]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L323-L339
[jukebox-notify]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/client/renderer/LevelEventHandler.java#L661-L687
[parrot-reg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L1014-L1016
[animal-persist]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[parrot-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/parrot.json#L1-L36
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L568
[death]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[loot-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
