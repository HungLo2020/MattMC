# Bald Eagle

The **Bald Eagle** is a neutral, tamable predator with **16 health points (8 hearts)**. Raw fish can tame and breed it; a tamed eagle has wandering, following, and sitting commands. It can hunt small animals and fish, but the reviewed MattMC code does **not** establish a working player-carried or player-steered falconry route. [Attributes and goals][goals] · [Active attributes][attribute-registration] · [Falconry limits](#falconry-and-the-player-carry-limit)

## Obtaining

Use the [Bald Eagle Spawn Egg](../items/BaldEagleSpawnEgg.md) for Creative access through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md). It is registered and included in the Spawn Eggs category. [Egg registration][egg] · [Category entry][egg-category]

**No built-in natural-spawn route was found in the reviewed snapshot.** There is no Bald Eagle entry in the active spawn-placement registrations or bundled biome/structure spawn lists. The class's unused spawn helper asks for raw brightness above 8, while its instance spawn-rule override returns true; neither creates a biome population. Do not treat an upstream mountain or river habitat as a confirmed MattMC acquisition route. [Spawn registrations][spawn-placements] · [Spawn-list selection][natural-spawns] · [Entity spawn methods][spawn-methods]

## Behavior

### Food, taming, and healing

Hold **raw Cod, raw Salmon, Tropical Fish, or Rotten Flesh** to attract an eagle. Only the three fish are breeding foods and taming items; Rotten Flesh is a healing item. These checks name the items directly, so cooked fish and Pufferfish are not substitutes. [Temptation and food][goals] · [Food predicate][food] · [Feeding callback][feeding]

- **Taming:** when the fish-taming branch is reached, it consumes one fish outside Creative and has a **50% chance** to assign the feeding player as owner and select Following
- **Healing:** Cod, Salmon, or Rotten Flesh restores **10 health points (5 hearts)**, capped by maximum health, and consumes one item outside Creative. This healing check runs before the taming roll and accepts injured wild eagles as well as tame ones. Tropical Fish does not heal
- **Ownership warning:** the taming branch does not require the eagle to be wild or the feeder to be its owner. A successful fish roll on an already tame eagle can **replace its owner**

[Feeding order and random roll][feeding] · [Owner assignment][taming]

**Carry more than one fish and account for ordinary animal feeding first.** Before those species checks, an adult ready to breed can consume a fish to enter love mode, or a baby can consume one to grow faster. If another fish remains, the same click can consume it for healing or a taming attempt. If the first fish empties the stack, the later fish check is skipped. Hearts can mean breeding, healing, or successful taming; they do not uniquely confirm ownership. [Inherited feeding][animal-feeding] · [Species callback][feeding] · [Empty-stack handling][empty-stack] · [Heart events][tame-events]

On an already tame, breed-ready adult, feeding the last fish can also fall through to the command branch: the inherited result is `SUCCESS_SERVER`, while the eagle excludes only `SUCCESS`. A successful fish tame selects command 1 but does not clear an existing eagle sitting flag. These are source-level interaction quirks, not a reliable one-fish-per-click interface. [Inherited result][animal-feeding] · [Command handling][commands] · [Sitting state][eagle-sit]

### Breeding and babies

Feed two nearby adults Cod, Salmon, or Tropical Fish when they are ready to breed. The inherited mating rule requires two Bald Eagles in love; it does not require them to be tame or have the same owner. Keep them able to approach one another. Successful breeding gives both parents a **6,000-tick cooldown** (5 minutes at 20 ticks per second), and the baby starts with **24,000 ticks** to adulthood (20 minutes at that rate). Fish feeding shortens a baby's remaining growth time. [Breeding goal][breed-goal] · [Mating and cooldown][animal-breeding] · [Baby age][baby-age]

The offspring factory creates a fresh Bald Eagle without copying taming or ownership, so **tame the baby separately**. Babies are prevented from entering the eagle's flying state and cannot receive a hood or use its glove-pickup branch. [Offspring][offspring] · [Initial tame state][tame-initial] · [Baby flight restriction][flight-state] · [Adult interactions][commands]

### Commands and following

With neither hand holding Leather Horse Armor, use an **empty hand** on a tamed eagle to cycle **Wandering → Following → Sitting → Wandering**. A successful initial fish tame selects Following, so the next ordinary command click normally selects Sitting. These command interactions are **not restricted to the owner**. Naming, a matching spawn egg, or lead handling can take priority over the species callback. [Command callback][commands] · [Command labels][command-text] · [Interaction dispatch][interaction]

- **Wandering:** the eagle can wander on land or fly and circle overhead; its natural prey-search goal is enabled in this command
- **Following:** the dedicated owner-follow goal starts at **25 blocks** and continues toward a distance of **2 blocks**. Once active, it attempts collision-checked teleportation near the owner at 12 blocks or more, then runs the eagle's follow movement. Leashes, passenger state, and nearby combat can interrupt this behavior
- **Sitting:** sets the eagle's custom sitting flag, suppressing its normal travel and wandering. It does not clear an existing attack target

[Wandering and prey gate][wander] · [Follow conditions and teleport][follow-goal] · [Eagle follow movement][follow-movement] · [Travel gate][travel]

Sitting is not a guaranteed combat shutoff. The eagle's setter changes its own sitting flag without updating the separate inherited sit-order field checked by the shared sitting and owner-defense goals. The tackle goal also has no custom-sitting check. Keep vulnerable animals apart instead of relying on a sit command to make an eagle harmless. [Custom setter][eagle-sit] · [Inherited sit field][inherited-sit] · [Shared sitting goal][sit-goal] · [Owner-defense gate][owner-defense] · [Tackle goal][tackle]

### Hunting and carrying prey

In Wandering, an unlaunched eagle can search for **Rabbits, Salmon, Raccoons, Jerboas, Catfish, and Flying Fish**, the entries in the bundled `minecraft:bald_eagle_targets` entity tag. This prey search is not limited to untamed eagles. It also registers retaliation, defense of its owner, and attacks against its owner's targets; player aggression is conditional rather than an always-hostile player-hunting rule. [Target goals][goals] · [Prey tag][prey-tag] · [Tag namespace][tag-namespace] · [Tag predicate][tag-predicate] · [Owner target goal][owner-target]

The attack goal can approach on foot, fly toward prey, and tackle. A tackle attempts to lift an `AbstractFish`, or another target under **1 block tall and 0.7 blocks wide** that is not a Bald Eagle, as the eagle's passenger. Carried prey receives **1 damage every 40 passenger-position updates**. Larger prey takes a **5-damage tackle**; the separate close attack animation deals **2 damage** at its hit frame. The 5-point attack attribute is therefore not a promise that every attack does identical damage. [Tackle and size checks][tackle] · [Prey positioning and damage][prey-carry] · [Close hit][close-hit]

Prey carrying uses the eagle as the vehicle. It is a separate path from the rejected eagle-on-player attachment described below. [Prey mount call][tackle] · [Player mount guard][mount-guard]

### Hood equipment

Use a **Leather Helmet** on an unhooded, tamed adult to add its hood, consuming the helmet outside Creative. The hood is a species flag, not ordinary armor equipment: the interaction does not equip the helmet in an armor slot or grant its armor attributes. It suppresses normal travel while the eagle is tame, off a vehicle, and not in its hood-return condition. [Hood interaction][commands] · [Travel condition][travel] · [Hood-return condition][hood-return]

Use **Shears** on a hooded eagle to remove the hood and spawn a Leather Helmet. **The reviewed callback consumes one entire Shears item on the server, including for a Creative ServerPlayer, instead of applying a durability point.** Neither hood interaction checks ownership. Do not assume the shears or the exact original helmet's dye/components will be preserved: removal constructs a new Leather Helmet. [Hood removal][commands]

### Falconry and the player-carry limit

MattMC's eagle code uses **[Leather Horse Armor](../items/LeatherHorseArmor.md) held in either hand** as its glove check; no separate Falconry Glove item is registered in this snapshot. Interacting with a tamed adult through the eligible non-food branch attempts to attach the eagle to the player when no `IFalconry` passenger is present. This branch clears its launched flag and ejects any prey it is carrying before attempting the attachment. [Glove check][commands] · [Passenger counting][falconry-interface] · [Actual item registration][horse-armor-item]

**That pickup fails on the server.** Players are registered with `noSave`, and the shared `startRiding` implementation rejects such non-serializable vehicles before its force option is considered. The eagle ignores the failed return value and still reports interaction success. Its callback also runs locally, where this server-only rejection is absent; a client-side attachment or success result is not evidence of a server-supported carry. The rejected player-carry route is tracked in [issue #805](https://github.com/HungLo2020/MattMC/issues/805). [Pickup attempt][commands] · [Mob delegation][mob-mount] · [Server rejection][mount-guard] · [Player registration][player-type] · [`noSave` implementation][no-save] · [Client interaction prediction][client-interaction]

The remaining falconry routines are **conditional code, not usable control instructions** in this snapshot:

- The existing-passenger routine positions an eagle at its owner's hand and removes it if neither hand holds Leather Horse Armor; it does not create the missing server attachment
- `onLaunch` contains hooded-flight and unhooded-target behavior, and `directFromPlayer` contains steering and remote-attack logic, but no call sites for either method were found in the reviewed gameplay tree. No active glove-use or networking path was found that invokes them
- The registered return goal requires a launched, tame eagle without prey or a live target. It flies toward its owner and can reposition there when stalled, but its final attempt to mount a player is rejected by the same server guard; it clears the launched flag before trying

[Passenger routine][passenger] · [Launch routine][launch] · [Direct-control routine][direct-control] · [Return goal][return-goal] · [Falconry interface][falconry-interface] · [Source snapshot][snapshot]

Do not build a hunt-and-return, remote-camera, or eagle transport plan around those routines. Automatic flying, ordinary following, and AI prey carrying have their own active paths. [Registered goals][goals] · [Wandering][wander] · [Tackling][tackle]

## Notes

* This mob is registered as `minecraft:bald_eagle`, with entity class `EntityBaldEagle` and category `MobCategory.CREATURE`. Its registered size is **0.6 blocks wide × 0.8 blocks tall**, with an eye height of 0.6 blocks. [Entity registration][entity]
* Its spawn egg is registered as `minecraft:bald_eagle_spawn_egg`. This is bundled Alex's Mobs content integrated into MattMC; the active items and control paths differ from upstream assumptions. [Egg registration][egg] · [Species implementation][snapshot-eagle]
* Registered attributes include 16 maximum health, 0.3 movement speed, 32 follow range, 5 attack damage, and 10 temptation range. Taming adds no species-specific attribute upgrade. [Attributes][goals] · [Inherited taming][taming]
* No bundled `minecraft:entities/bald_eagle` death-loot table or species-specific death-drop override was found. The normal lookup falls back to an empty table, so this snapshot does not establish feathers, fish, or a special eagle item as its base death drops. Custom loot and ordinary equipment drops are separate. [Default loot key][loot-key] · [Death-loot lookup][loot-call] · [Missing-table fallback][loot-fallback] · [Bundled loot directory][loot-directory]
* Ordinary animal distance-despawning is disabled. The inherited save data retains ownership; the eagle additionally saves `BirdSitting`, `Launched`, `HasCap`, `EagleCommand`, and `LaunchTime`. Flight/tackle animation state and the direct-control timers are not among those species save fields. [Animal persistence][animal-persistence] · [Owner persistence][owner-save] · [Eagle save fields][save]
* Source reviewed at [`2fff1ef`][snapshot] on 2026-10-03. Spawn, feeding, item/tag data, goal registration, mount rejection, missing control callers, loot, and save paths were inspected; no in-game feeding, flight, combat, or equipment test was run.

[goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L98-L139
[attribute-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L120
[egg]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1800
[egg-category]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1976
[spawn-placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L52-L189
[natural-spawns]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L284-L325
[spawn-methods]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L102-L162
[food]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L161-L163
[feeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L302-L328
[taming]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L115-L172
[animal-feeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L173
[empty-stack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L297-L335
[tame-events]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L86-L109
[commands]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L328-L378
[eagle-sit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L227-L241
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L32-L81
[animal-breeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L233
[baby-age]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AgeableMob.java#L156-L168
[offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L576-L580
[tame-initial]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L46-L51
[flight-state]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L251-L260
[command-text]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/assets/minecraft/lang/en_us.json#L3985-L3987
[interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1054-L1107
[wander]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L902-L1034
[follow-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/FlyingAIFollowOwner.java#L31-L161
[follow-movement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L278-L295
[travel]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L197-L203
[inherited-sit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L224-L230
[sit-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/SitWhenOrderedToGoal.java#L15-L48
[owner-defense]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/OwnerHurtByTargetGoal.java#L22-L47
[owner-target]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/OwnerHurtTargetGoal.java#L22-L47
[prey-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/bald_eagle_targets.json#L1-L12
[tag-namespace]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L258-L260
[tag-predicate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L74-L81
[tackle]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L1038-L1129
[prey-carry]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L668-L688
[close-hit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L545-L550
[hood-return]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L699-L706
[falconry-interface]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/IFalconry.java#L7-L21
[horse-armor-item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2134
[mob-mount]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1223-L1230
[mount-guard]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2291-L2329
[player-type]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1596-L1606
[no-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2142-L2145
[client-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L428-L438
[passenger]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L387-L425
[launch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L824-L846
[direct-control]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L714-L781
[return-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L1132-L1176
[entity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1155-L1157
[loot-key]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-call]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[loot-directory]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/entities
[animal-persistence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[owner-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L53-L79
[save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java#L179-L195
[snapshot]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716
[snapshot-eagle]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBaldEagle.java
