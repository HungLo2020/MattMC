# Warped Toad

The **Warped Toad** is a tameable animal with **30 health points (15 hearts)** and a tongue attack. Feed fungi to tame it, use roots for healing without breeding, and keep it in a dry enclosure or shallow water with an easy exit. **Its swimming AI does not make it safe in lava or permanently underwater.** [Attributes][attributes] · [Attribute binding][attribute-binding] · [Food interaction][interaction] · [Environmental limits](#water-lava-and-falls)

## Obtaining

Request a [Warped Toad Spawn Egg](../items/WarpedToadSpawnEgg.md) from MattMC's [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) in **Creative**, then use it on a block with space for the animal. The egg is registered and listed in the Spawn Eggs category. See [Spawn eggs](../items/SpawnEggs.md) for placement, Dispensers, spawners and the browser's mode limits. [Egg registration][egg-registration] · [Category entry][egg-category] · [Placement][egg-placement]

**No natural Warped Toad encounter or ordinary Survival acquisition route was verified.** The checked biome definitions, structure definitions and templates contain no Warped Toad population, and no species entry was found in the active spawn-placement registry or world-generation callers. Its unused helper accepts lava or an opaque block below; that helper and its permissive instance spawn rule do not make it spawn in a Warped Forest. Breeding can expand an existing population, but does not supply the first pair. No bundled egg recipe or loot source was found. [Spawn helpers][spawn-helpers] · [Placement registry][placements] · [Population selection][population] · [Biome-spawn code][biome-code] · [Bundled data][data]

## Behavior

### Taming and food

Use **Crimson Fungus or Warped Fungus directly on an untamed toad**. Each attempt has a **1-in-3** taming roll and consumes one fungus unless you have infinite materials. Taming assigns the player as owner; it does not automatically change the initial wandering command. Roots do not tame it. [Taming interaction][interaction] · [Taming food][tameables] · [Consumption][consume] · [Owner assignment][tame] · [Initial command][initial-command]

Hold **either fungus, Crimson Roots or Warped Roots** to lure it. For a tamed, injured toad, feeding one of those items restores **5 health points (2½ hearts)**, capped by its maximum health. **Roots are the simplest healing-only food** because fungi can also start breeding or accelerate a tamed baby's growth. These food tags resolve to registered items in the bundled `minecraft` data. [Lure goal][goals] · [Food tag][foodstuffs] · [Food registration][food-registration] · [Healing][interaction] · [Health cap][health-cap]

Toads also seek loose items from that same food list and consume **one item per pickup**, healing 5 points. This pickup check does not require taming or missing health: even an untamed or healthy toad can eat dropped food. Dropping fungi does **not** run the hand-taming or breeding interaction. Keep supplies off the ground near them. [Item selection and healing][loose-food] · [Pickup and consumption][item-pickup]

### Commands, following and keeping

As the owner, **use an empty hand** to cycle **Wander → Follow → Sit → Wander**. A newly tamed toad starts at Wander, so the first command switches it to Follow. Food has its own interaction and is unsuitable for reliably changing commands. If the toad is leashed to you, an ordinary interaction first releases the leash; interact again to feed it or change its command. [Leash interaction][lead-interaction] [Command interaction][interaction] · [Command labels][command-labels] · [Initial command][initial-command]

Follow starts when the owner is at least **4 blocks away** and continues until within **2 blocks**, subject to path completion. At **12 blocks or more**, an unleashed toad that is not itself a passenger can try to teleport near its owner. The destination must pass walkability and collision checks; following is not a guarantee that it can reach every location. [Follow conditions][follow] · [Teleport attempts and destination checks][teleport]

Use a Lead or an enclosure when moving or housing it. Both wild and tamed toads inherit the rule against ordinary distance despawning; taming is not required for that protection. World saving retains age, owner, command and the toad's sitting flag. Damage clears the sitting flag without resetting its command number, so check and reissue commands after an attack. [Leashing][leash] · [Distance persistence][persistence] · [Owner save][owner-save] · [Age save][age-save] · [Toad save][toad-save] · [Damage response][incoming-damage]

There is **no ordinary player-mount or steering interaction** in this species' controls. Following and leashing are its supported movement options here. [Interaction][interaction] · [Shared mob interaction][mob-interaction]

### Breeding and feeding quirks

Tame both adults, leave them free to move together, then feed each **Crimson Fungus or Warped Fungus** when ready to breed. Food breeding requires a tamed toad; roots only lure or heal. The offspring factory creates an **untamed baby**, with no inherited owner assignment. Tame it separately before using fungi to speed up its growth. [Breeding food][breedables] · [Food check and interaction][interaction] · [Breeding goal][breed-goal] · [Offspring factory][offspring] · [New tameable state][new-tameable] · [Shared breeding][breeding]

A new baby takes **24,000 loaded entity ticks**, about **20 minutes at 20 TPS**, to grow without feeding. Breeding gives each parent a **6,000-tick cooldown**, about **5 minutes at 20 TPS**, and awards **1–7 XP** with `doMobLoot` enabled. A matching spawn egg used on a living toad can also create a baby, without the ordinary breeding reward. [Age progression][age] · [Breeding and reward][breeding] · [Egg interaction][egg-dispatch] · [Egg baby helper][egg-baby]

**Check food counts and commands after feeding fungi.** The ordinary animal interaction runs before the toad's healing branch. For an injured tame adult ready to breed, or an injured tame baby, a stack with at least two fungi can lose **two items in one interaction**: one for love/growth, then one for the 5-point heal. If the first use empties the stack, that healing branch is skipped. [Animal feeding][animal-food] · [Toad interaction order][interaction] · [Consumption][consume] · [Empty-stack checks][empty-stack]

A further source-identified edge affects an owner feeding their **last fungus to a ready adult**: it can enter love **and advance its command**. The superclass returns `SUCCESS_SERVER`, while the command check excludes only `SUCCESS`; the now-empty stack no longer counts as food. This can switch a following toad to Sit, interfering with breeding. These are code-path findings, not in-game reproductions. [Result types][interaction-results] · [Animal feeding][animal-food] · [Command branch][interaction] · [Empty-item interaction][empty-interaction]

### Combat

Warped Toads retaliate when attacked. Their target tag also includes **Hoglins, Piglins, Zombified Piglins, Striders and Magma Cubes**, so do not mix them with those mobs merely because this wiki classifies the toad as neutral. Tamed toads can defend their owner or attack the owner's target while not ordered to sit; ordinary targeting and ally checks still apply. [Installed targets][goals] · [Target tag][targets] · [Tag predicate][target-predicate] · [Owner defense][owner-defense] · [Owner attack][owner-attack] · [Owner protection][owner-protection]

The tongue attack starts against a visible target **less than 8 blocks away**, pulls the target during retraction and uses **2 base attack damage**. The damage call reaches server damage handling; 2 points is not a guaranteed final health loss after defenses. A toad carrying passengers does not start this attack goal. [Attack goal][tongue-goal] · [Pull and damage][tongue-damage] · [Damage dispatch][damage-dispatch] · [Attributes][attributes]

A tamed toad modifies incoming damage from an attributed attacker other than a Player or AbstractArrow to **(incoming amount + 1) ÷ 3** before base damage handling. This is not blanket protection: player attacks and environmental damage do not meet that condition, and the check uses the damage source's attributed entity. [Damage response][incoming-damage]

### Water, lava and falls

The toad walks, hops and swims, and is not pushed by fluid currents. Its wander routines can seek water and move through lava. **Keep lava out of its enclosure:** its registered entity type lacks fire immunity, and normal lava/fire damage still applies. Movement permission is not damage protection. [Movement][travel] · [Fluid behavior][fluid] · [Random swimming][swim-goal] · [Registration][registration] · [Lava damage][lava]

**Provide air and an easy way out of water.** The active breathing check uses the `can_breathe_under_water` entity tag, which does not include Warped Toad, including through its nested tags. With ordinary full air and no protective effect, continuous eye submersion reaches the first **2-point drowning hit after about 320 ticks (16 seconds at 20 TPS)**. Its timed desire to leave water begins only after more than 600 swimming ticks, and is disabled while following or ordered to sit; it is not a dependable air-supply safeguard. [Breathing tag][breathing-tag] · [Breathing check][breathing-check] · [Drowning loop][drowning] · [Air capacity][air] · [Air decrease][air-decrease] · [Threshold][drowning-threshold] · [Water timers][water-timers] · [Water preferences][fluid] · [Exit goal][water-exit]

Ordinary movement-based fall damage is suppressed by the toad's empty fall-check override. Its separate two-argument `causeFallDamage` method is an older overload; the active protection is the fall-check override called by movement, not a claim of immunity to every possible fall-damage source. [Species fall methods][fall] · [Movement caller][fall-caller] · [Current damage signature][fall-signature]

## Drops and experience

The default `minecraft:entities/warped_toad` death-loot table is absent from the bundled loot directory, and the class has no species death-drop override. The loader's missing-table fallback is empty: **no species item drop, cooked variant or Looting bonus is defined** in these defaults. Custom data packs and equipment drops are separate possibilities. [Default loot key][loot-key] · [Bundled loot][loot-data] · [Loot loading and fallback][loot-fallback] · [Death handling][death]

Adults can award **1–3 base death XP** when the normal player-credit and `doMobLoot` conditions are met and experience has not already been consumed. Babies do not drop death XP. [Animal XP][animal-xp] · [Kill credit][kill-credit] · [XP gates][xp] · [Baby exclusion][baby-xp]

## Appearance and integration

A name containing **`pepe`**, ignoring case and formatting, selects the alternate texture in the active renderer. Normal and alternate appearances both have blink textures; this is a visual choice, with no additional combat or taming benefit in the checked code. [Name check][name] · [Active renderer][renderer]

The Warped Toad's actual model inherits the shared Citadel proxy involved in [issue #803](https://github.com/HungLo2020/MattMC/issues/803). On an admitted visible ordinary-body submission, with a readable texture and valid transform, the native extractor traverses an empty model-part tree and can reach its empty-model exception. **This is a source-identified rendering limitation, not a reproduced crash or invisibility report.** [Renderer registration][renderer-registration] · [Toad model][model] · [Model inheritance][advanced-model] · [Citadel proxy][citadel] · [Native submission][render-submit] · [Extraction][extraction] · [Tree traversal][traversal]

## Notes

* Entity ID: `minecraft:warped_toad`; spawn egg ID: `minecraft:warped_toad_spawn_egg` [Registration][registration] · [Egg registration][egg-registration]
* The registered body is **1.0 block wide × 0.8 blocks high**, with a 0.6-block eye height; these are entity dimensions, not a tested enclosure minimum [Registration][registration]
* Its active class is `net.alexsmobs.entity.EntityWarpedToad`, registered in `MobCategory.CREATURE` from bundled Alex's Mobs content integrated into MattMC [Registration][registration]

Related: [Warped Toad Spawn Egg](../items/WarpedToadSpawnEgg.md) · [Mobs](Mobs.md)

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716` on 2026-10-03. Registration, resource filenames and tag dependencies, spawn routes, shared interaction/damage/air dispatch, ownership, breeding, loot and the species renderer chain were checked. No in-game spawning, feeding, taming, breeding, combat, following, environmental damage, save/reload or rendering test was run. Data packs and custom entity data can change these defaults.

[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L95-L97
[attribute-binding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L250
[interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L211-L259
[egg-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1975
[egg-category]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2116
[egg-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L76-L103
[spawn-helpers]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L89-L124
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L78-L178
[population]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L326
[biome-code]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/data/worldgen/BiomeDefaultFeatures.java
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[tameables]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/warped_toad_tameables.json#L1-L7
[consume]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1113-L1121
[tame]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L158-L164
[initial-command]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L301-L309
[goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L152-L174
[foodstuffs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/warped_toad_foodstuffs.json#L1-L9
[food-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L362-L365
[health-cap]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1127-L1140
[loose-food]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L439-L451
[item-pickup]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L130-L157
[command-labels]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/assets/minecraft/lang/en_us.json#L3985-L3987
[follow]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L577-L601
[teleport]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L615-L673
[leash]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L81-L84
[lead-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2146-L2178
[persistence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[owner-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L53-L79
[age-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AgeableMob.java#L106-L118
[toad-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L140-L150
[incoming-damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L127-L138
[mob-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1111
[breedables]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/warped_toad_breedables.json#L1-L7
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L32-L80
[offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L457-L461
[new-tameable]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L46-L51
[breeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L228
[age]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L168
[egg-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1088-L1107
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[animal-food]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[empty-stack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L322-L335
[interaction-results]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/InteractionResult.java#L11-L15
[empty-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Item.java#L254-L256
[targets]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/warped_toad_targets.json#L1-L10
[target-predicate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L79-L81
[owner-defense]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/OwnerHurtByTargetGoal.java#L20-L46
[owner-attack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/OwnerHurtTargetGoal.java#L20-L46
[owner-protection]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L166-L210
[tongue-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L508-L550
[tongue-damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L376-L414
[damage-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1777
[travel]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L176-L198
[fluid]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L471-L505
[swim-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/WarpedToadAIRandomSwimming.java#L21-L77
[registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1384-L1390
[lava]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L574-L590
[breathing-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json#L1-L19
[breathing-check]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L386
[drowning]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L443
[air]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2660-L2662
[air-decrease]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L569-L582
[drowning-threshold]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[water-timers]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L269-L287
[water-exit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/AnimalAILeaveWater.java#L25-L57
[fall]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L200-L205
[fall-caller]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L741-L745
[fall-signature]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1710-L1728
[loot-key]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/entities
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[death]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1491
[animal-xp]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L126-L129
[kill-credit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1324-L1336
[xp]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487
[baby-xp]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L566
[name]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityWarpedToad.java#L84-L87
[renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/RenderWarpedToad.java#L11-L51
[renderer-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L283
[model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/model/ModelWarpedToad.java#L11-L40
[advanced-model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/AdvancedEntityModel.java#L17-L26
[citadel]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/basic/BasicEntityModel.java#L13-L56
[render-submit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L283-L302
[extraction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9705-L9728
[traversal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/model/geom/ModelPart.java#L151-L169
