# Skeleton Horse

A **Skeleton Horse** can become a saddled mount after a lightning trap activates. The horse is passive, but approaching an untriggered trap summons armed **Skeleton riders**. Surviving trap horses are already tame. They have **15 health points (7.5 hearts)** and can keep a rider underwater, although the rider still needs air. [Trap activation][trap-trigger] · [Additional mounts][trap-horses] · [Rider equipment][trap-riders] · [Health][skeleton-stats] · [Attribute registration][skeleton-attributes] · [Breathing and rider checks][drowning] · [Underwater dismount list][dismounts]

## Obtaining

### Lightning traps

The natural route starts with a **rain-valid thunderstorm strike** in an entity-ticking area. The weather caller can create a new Skeleton Horse with its trap flag set when `doMobSpawning` is enabled, a local-difficulty-dependent roll succeeds, and the strike target is **not above a Lightning Rod**. Peaceful's effective difficulty is zero, so this weather roll cannot create a trap there. Rain must actually reach the chosen location; snow or a roof does not satisfy the rain check. [Active chunk caller][weather-caller] · [Trap creation][weather-trap] · [Rain and exposure][rain] · [Difficulty calculation][difficulty]

This creates a **new animal**; it is not a method of converting an ordinary Horse by striking it. A Lightning Rod can attract weather lightning but its targeted strike is excluded from this trap branch. Use the [Lightning Rod guide](../blocks/LightningRods.md) for placement and attraction details. Ordinary damaging lightning still has its usual damage/fire path. [Creation and rod exclusion][weather-trap] · [Target selection][weather-target] · [Ordinary lightning hit][ordinary-lightning]

Neither Skeleton Horse nor Zombie Horse appears in the **68 bundled biome spawn definitions** reviewed here. Their spawn-placement predicates do not, by themselves, add them to a biome list. Do not search a particular grassland for ordinary Skeleton Horse herds. [Registered placement][placement-skeleton] · [Species predicate][skeleton-stats] · [Candidate selection][natural-candidates]

### Approaching the trap

An alive, non-Spectator player **closer than 10 blocks** triggers the trap; Creative players also satisfy that player test. Prepare before entering that range. The event clears the trap flag, makes the original horse a tame adult, puts a Skeleton on it, and attempts to add **three more tame adult horses with Skeleton riders**. The intended result is four mounted Skeletons, subject to the creation calls succeeding. [Trigger and event][trap-trigger] · [Player eligibility and distance][trap-player] · [New horses][trap-horses]

The riders receive Bows through normal Skeleton initialization, get an Iron Helmet if their head slot is empty, and have their main-hand/head equipment passed through the mob enchantment provider. The event's lightning flash is visual-only, but the riders are real combatants. Defeat the riders while avoiding damage to the horses you want to keep. [Rider setup][trap-riders] · [Skeleton equipment and combat setup][rider-bow] · [Visual-only bolt][trap-trigger] · [Bolt damage gate][visual-lightning]

An **untriggered** trap horse discards itself when its trap timer passes the **18,000-tick** limit, about **15 minutes while ticking**. Naming it does not remove the trap flag or bypass this timer. Triggering the trap clears the flag, so the recovered mounts no longer use that timeout. [Timer, save/load and trap flag][skeleton-timeout] · [Activation clears the flag][trap-trigger]

### Spawn eggs and operator-created mounts

The [Skeleton Horse Spawn Egg](../items/SkeletonHorseSpawnEgg.md) is available through the ordinary [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. A normal egg creates an **untamed, non-trap horse**. Its interaction refuses ordinary mounting until it is already tame, so repeated empty-hand clicks do not start the standard [Horse taming routine](Horse.md#taming-and-feeding). [Egg registration][skeleton-egg] · [Egg initialization][egg-create] · [Creation callback][type-create] · [Default tame state][horse-flags] · [Default trap state][skeleton-timeout] · [Species interaction][skeleton-interact]

With **permission level 2**, an operator can create an already-tame adult:

```mcfunction
/summon minecraft:skeleton_horse ~ ~ ~ {Tame:1b,Age:0}
```

This example supplies tame/age data and leaves the trap flag off; bring a Saddle separately. It is a source-checked command example, not an executed gameplay test. Ordinary browser egg acquisition and permission-controlled entity commands are separate routes. [Command registration][summon-register] · [Syntax and permission][summon-syntax] · [Entity-data loading][summon-create] · [Tame key][horse-save] · [Age data][age-save]

## Behavior

### Riding and equipment

Once the rider is gone, use a **tame adult**:

1. Interact with a [Saddle](../items/Saddle.md) to equip its empty saddle slot, or Sneak/Crouch-interact to open its inventory
2. Mount with an empty hand
3. Use the ordinary Horse steering and charged-jump controls; the Saddle is required for player control and jumping

See [Horse riding controls](Horse.md#riding-and-equipment) for the key bindings and charge behavior, and the Saddle owner for crafting and removal. There is **one rider seat**. The bundled Horse Armor tag accepts only the ordinary Horse, so Skeleton Horses cannot use that armor. They also have **no Chest cargo inventory**. [Tame/age saddle gate][horse-saddle] · [Interaction and inventory][horse-use] · [Inventory access][horse-inventory] · [Saddle component][equippable] · [Held-item dispatch][equip-dispatch] · [Equip action][equip-action] · [Allowed saddle targets][saddles] · [Player control][horse-controller] · [Jump gate][horse-jump] · [One-seat default][capacity] · [Armor tag][armor] · [Zero cargo columns][horse-cargo]

A **Lead** can move or secure one, but food does not lure it: this species omits the ordinary horse food-temptation goal. Tame state also does not make it stay where you leave it. Use an enclosure or Lead for parking. [Lead eligibility][lead] · [Shared Lead interaction][lead-action] · [Shared goals][horse-goals] · [Species goal override][skeleton-stats]

### Underwater travel

Skeleton Horses are included in the **undead** tag, which grants underwater breathing. They are absent from the automatic underwater-dismount tag, so a player can remain mounted when submerged. The horse also uses its own water movement slowdown value. This supports underwater riding; it does **not** give the player Water Breathing or immunity to suffocation. Plan an air supply and an exit before descending. [Skeleton membership][skeletons] · [Undead expansion][undead] · [Underwater-breathing tag][underwater] · [Breathing lookup][breathing] · [Rider drowning/dismount checks][drowning] · [Dismount tag lookup][dismount-tag-call] · [Dismount list][dismounts] · [Species water motion][skeleton-water] · [Ridden movement][ridden-water] · [Fluid travel][fluid-travel] · [Applied water slowdown][water-apply]

Unlike this mount, a [Zombie Horse](ZombieHorse.md#water-and-daylight) is in the underwater-dismount list. Neither species installs the ordinary horse floating goal. Do not treat these animals as boats that reliably keep their rider at the surface. [Species override][skeleton-stats] · [Shared floating goal][horse-goals] · [Dismount list][dismounts]

### Health, food and foals

A Skeleton Horse can heal **1 health point** on a successful **1-in-900 roll per eligible server AI tick**. This is random passive recovery, not one guaranteed heal every 45 seconds. For targeted recovery, **Instant Damage heals it and Instant Health harms it**; the undead effect rules also reject Poison and Regeneration. Use the [brewing guide](../brewing/Brewing.md#splash-and-lingering-forms) for throwable forms, and avoid splashing nearby living pets or players with Harming. [Healing tick][horse-regen] · [Health cap][heal-cap] · [Undead membership][undead] · [Inversion tag][inverted] · [Inversion lookup][potion-inversion] · [Registered effects][potion-registration] · [Instant-effect behavior][instant-effect] · [Splash caller][splash-caller] · [Poison/Regeneration tag][effect-tag] · [Effect gate][effect-immunity]

**Do not apply the ordinary Horse food-healing table to this species.** Its unoccupied tame-adult interaction mounts the player instead of calling the Horse-specific feeding handler. It cannot breed: the inherited mating predicate always returns false, even if a special interaction produces love particles. [Species interaction][skeleton-interact] · [Shared interaction][horse-use] · [Ordinary Horse's separate food caller][horse-real-food-caller] · [Mating rejection][horse-save] · [Partner selection][breeding-goal]

The [matching-egg baby interaction](../items/SpawnEggs.md#using-an-egg-on-an-existing-mob) can still create a foal. It starts untamed and normally takes **24,000 ticks**, about **20 minutes while ticking**, to grow. A foal explicitly made tame through entity data can accept tagged Horse food through the generic animal growth path, advancing roughly **10% of remaining time** per feeding rather than the adult Horse table's fixed food amounts. Ordinary untamed foals still meet the species interaction block. [Mob egg dispatch][egg-baby-caller] · [Egg helper][egg-baby] · [Species offspring factory][skeleton-interact] · [Default state][horse-flags] · [Age progression][age] · [Baby interaction branch][horse-use] · [Generic feeding][animal-feed] · [Food predicate][horse-food-test] · [Food tag][food]

## Notes

- Entity ID: `minecraft:skeleton_horse`; registered adult size **1.3964844×1.6 blocks** [Registration][skeleton-reg]
- It does not normally despawn merely because you walk away; the untriggered-trap timer is a separate exception. Tame state and trap data are saved [Animal retention][animal-persistence] · [Tame save/load][horse-save] · [Trap timer and persistence][skeleton-timeout]
- The horse itself does not burn merely from daylight; its hostile rider is a separate Skeleton with its own sunlight behavior [Horse hierarchy][skeleton-class] · [Inherited tick][horse-regen] · [Sunburn predicate][sun-helper] · [Rider sunburn][sunburn-skeleton]
- Jump strength can differ between normally initialized mounts; this is not a measured jump-height guarantee [Species initialization][skeleton-stats] · [Shared finalization][horse-finalize] · [Generated jump value][horse-jump-random]
- Its adult base death table gives **0–2 Bones**, with a Looting count increase; ordinary mob loot must be enabled. Foals skip the ordinary base loot [Table][skeleton-loot] · [Loot gate][loot-gate] · [Death dispatch][death] · [Loaded table][loot-load]
- Retrieve a valuable Saddle through the inventory or eligible Shears interaction before danger. Normally player-equipped gear is marked for guaranteed equipment drops, but death recovery is still subject to the ordinary loot and equipment-prevention gates [Equipment marking][equip-mark] · [Saddle action][equip-action] · [Inventory insertion marking][menu-mark] · [Equipment drop rules][equipment-drop] · [Loot gate][loot-gate]

## Related pages

- [Horse](Horse.md): shared steering and jumping controls
- [Zombie Horse](ZombieHorse.md): egg/command acquisition and underwater dismount difference
- [Lightning Rods](../blocks/LightningRods.md)
- [Saddle](../items/Saddle.md)
- [Transport](../mechanics/Transport.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`. Traced active creation and interaction callers, loaded tags, horse/animal inheritance, entity data, equipment and loot. The biome inventory covers the exact 68 active `data/<namespace>/worldgen/biome/*.json` definitions, excluding tags and legacy source trees. No game, command, riding, feeding, healing, trap, underwater or drop test was run. Timings assume a ticking server at 20 ticks per second; data packs, custom entity data and server settings can alter behavior.

[trap-trigger]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/SkeletonTrapGoal.java#L26-L60
[trap-horses]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/SkeletonTrapGoal.java#L63-L75
[trap-riders]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/SkeletonTrapGoal.java#L78-L103
[skeleton-stats]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/SkeletonHorse.java#L45-L64
[skeleton-attributes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L239-L239
[drowning]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L440
[dismounts]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/dismounts_underwater.json#L1-L17
[weather-caller]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L413-L423
[weather-trap]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/level/ServerLevel.java#L519-L549
[rain]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/Level.java#L855-L873
[difficulty]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/DifficultyInstance.java#L43-L60
[weather-target]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/level/ServerLevel.java#L588-L620
[ordinary-lightning]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Entity.java#L2697-L2704
[placement-skeleton]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L144-L144
[natural-candidates]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L329
[trap-player]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/EntityGetter.java#L103-L113
[rider-bow]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java#L132-L175
[visual-lightning]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LightningBolt.java#L127-L158
[skeleton-timeout]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/SkeletonHorse.java#L124-L164
[skeleton-egg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/Items.java#L1957-L1959
[egg-create]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L89-L103
[type-create]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L1731-L1775
[horse-flags]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L150-L185
[skeleton-interact]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/SkeletonHorse.java#L166-L175
[summon-register]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/commands/Commands.java#L237-L237
[summon-syntax]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/commands/SummonCommand.java#L35-L76
[summon-create]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/commands/SummonCommand.java#L79-L107
[horse-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L779-L802
[age-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/AgeableMob.java#L106-L118
[horse-saddle]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L221-L235
[horse-use]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L635-L659
[horse-inventory]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L403-L407
[equippable]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L99-L108
[equip-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/ItemStack.java#L591-L604
[equip-action]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L159-L175
[saddles]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/can_equip_saddle.json#L1-L14
[horse-controller]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L947-L951
[horse-jump]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L860-L880
[capacity]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Entity.java#L2386-L2388
[armor]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/can_wear_horse_armor.json#L1-L5
[horse-cargo]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L1033-L1035
[lead]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[lead-action]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Entity.java#L2162-L2174
[horse-goals]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L129-L148
[skeletons]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/skeletons.json#L1-L9
[undead]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/undead.json#L1-L9
[underwater]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json#L1-L19
[breathing]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L386
[dismount-tag-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Entity.java#L2533-L2535
[skeleton-water]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/SkeletonHorse.java#L146-L149
[ridden-water]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2422-L2430
[fluid-travel]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2257-L2265
[water-apply]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2313-L2342
[horse-regen]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L524-L535
[heal-cap]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1127-L1140
[inverted]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/inverted_healing_and_harm.json#L1-L5
[potion-inversion]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1027-L1029
[potion-registration]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/effect/MobEffects.java#L50-L51
[instant-effect]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/effect/HealOrHarmMobEffect.java#L16-L40
[splash-caller]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/projectile/ThrownSplashPotion.java#L38-L68
[effect-tag]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/ignores_poison_and_regen.json#L1-L5
[effect-immunity]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1003-L1012
[horse-real-food-caller]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/Horse.java#L157-L177
[breeding-goal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L32-L81
[egg-baby-caller]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1103
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L158-L181
[age]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L167
[animal-feed]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L174
[horse-food-test]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L502-L505
[food]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/item/horse_food.json#L1-L12
[skeleton-reg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L1215-L1222
[animal-persistence]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[skeleton-class]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/SkeletonHorse.java#L29-L64
[sun-helper]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1326-L1336
[sunburn-skeleton]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java#L98-L125
[horse-finalize]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L1003-L1016
[horse-jump-random]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L928-L930
[skeleton-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/skeleton_horse.json#L1-L36
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L568
[death]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[loot-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[equip-mark]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L506-L518
[menu-mark]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L786-L799
[equipment-drop]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L813-L839
