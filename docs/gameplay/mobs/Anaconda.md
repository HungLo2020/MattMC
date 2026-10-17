# Anaconda

The **Anaconda** (`minecraft:anaconda`) is an aggressive, semi-aquatic snake with **40 health points (20 hearts)** and a head followed by seven separate body segments. You can temporarily pacify it with food and breed it, but it has no taming or owner-command system. **Do not plan a Shed Snake Skin farm around ordinary kills:** the current swallowing trigger is disconnected from the normal death callback. [Health][health] · [Multipart body][body] · [Food interaction][interaction] · [Swallowing limitation](#swallowing-and-shed-snake-skin)

## Obtaining

The [Anaconda Spawn Egg](../items/AnacondaSpawnEgg.md) is registered as `minecraft:anaconda_spawn_egg` and appears in Creative. With command permission, use `/summon minecraft:anaconda`. These are confirmed acquisition routes; no natural Anaconda spawn entry was found in the reviewed biome resources, biome-building code, or SpawnPlacements registrations. No structure or event route was established by the source/resource search. [Entity registration][entity] · [Egg registration][egg] · [Creative entry][creative] · [Spawn placements][placements] · [Bundled data][data]

An imported spawn helper checks Grass Block, Dirt, Sand, Mud, or Clay below the spawn and a height below sea level + 4. The separate spawn-roll override uses the configured roll count. Neither supplies an active biome entry or wires that terrain helper into SpawnPlacements, so those conditions are **not a confirmed habitat guide**. [Spawn helpers][spawn-helpers] · [Ground tag][ground] · [Spawn roll][roll]

Spawn initialization randomly selects the ordinary green or yellow variant. Breeding follows the separate inheritance rule below. [Variant initialization][variant]

## Behavior

### Targets and combat

Anacondas hunt **Chickens, Rabbits, Pigs, Sheep, and Cows** through the bundled prey tag. Their automatic player-hunting goal starts only for adults outside Peaceful while neither temporarily pacified nor in love. They also have a retaliation goal. Babies are excluded from automatic player hunting, but the prey and retaliation goals have no equivalent baby exclusion; keep them separate from livestock too. [Goals][goals] · [Prey tag][prey] · [Tag predicate][target-predicate]

The active custom melee goal bites a nearby target for **4 damage points before defenses**. Targets no wider than 2 blocks, other than another Anaconda, can then be constricted. The snake coils toward the target and suppresses its movement; this is not a passenger/riding interaction. While constricting, its timer requests another hit every 20 ticks once it reaches 40, for **4–12.5 damage points**, calculated from one quarter of the victim's maximum health with a minimum of 4 and a maximum of 12.5. Armor and other damage rules still apply. The bite and squeeze use a compatibility damage method that actively forwards to server damage. [Melee goal][melee] · [Constriction][constriction] · [Damage dispatch][damage]

Its long body is not seven extra health pools. Server damage to a positioned segment forwards to the head, and segment interactions pass up the parent chain. The head and segments reject in-wall suffocation damage, but that does not make them generally invulnerable. [Part interaction][part-interaction] · [Part damage][part-damage] · [Head immunity][immunity]

### Food and temporary pacification

Use **Raw Chicken, Raw Rabbit, Raw Cod, or Raw Salmon**. These four direct entries make up `minecraft:anaconda_foodstuffs`; cooked versions and fish buckets are not included. Holding one can lure the snake, but simply holding food does not start pacification. [Food tag][food] · [Goals][goals] · [Temptation][tempt]

When the food interaction reaches its feeding handler, it clears the current target and sets a **3,600–7,199 tick** passive period, roughly **3 to just under 6 minutes** at 20 ticks per second. This happens before ordinary breeding/growth checks, including while an adult is on breeding cooldown. Item consumption still follows those normal checks. If you are its leash holder, the earlier shared interaction can detach the lead instead; use food again afterward. [Food interaction][interaction] · [Interaction order][mob-interaction] · [Lead interaction][lead-interaction] · [Animal feeding][animal-food]

Pacification does not protect livestock and is not permanent taming. The player most recently recorded as hurting the snake is exempt from its passive-player protection, so hitting it can provoke retaliation. Food use does **not** call its healing/skin-production routine. [Attack protection][passive] · [Food interaction][interaction] · [Digestion routine][feeding]

### Breeding

Feed two eligible adults one of the four foods and let them approach each other. The registered BreedGoal calls the current offspring method, which creates a baby Anaconda. The baby copies the green/yellow state of the parent whose breeding callback creates it; the code does not blend colors. Both parents receive a **6,000-tick cooldown** (5 minutes), and mating awards **1–7 experience** with mob loot enabled. [Goals][goals] · [Breeding dispatch][breed-goal] · [Offspring][offspring] · [Shared breeding][breeding]

Babies start with the ordinary **24,000-tick** growth timer (20 minutes while ticking). More of the same food advances their growth by approximately 10% of the remaining time per feeding. Breeding does not grant ownership. [Age and growth][age] · [Animal feeding][animal-food]

### Swallowing and Shed Snake Skin

The imported kill method would remove prey and start a swelling effect down the body. However, its `awardKillScore(LivingEntity, DamageSource)` signature does not override the current `awardKillScore(Entity, DamageSource)` callback used by the death dispatcher. No active caller of the narrower Anaconda overload was found. **Ordinary kills therefore do not establish prey swallowing, loot suppression, digestion healing, or automatic shedding through this chain.** Do not count raw-food interactions as digestion meals either. [Anaconda kill overload][kill-overload] · [Current callback][kill-callback] · [Death dispatcher][death] · [Digestion integration issue #806](https://github.com/HungLo2020/MattMC/issues/806)

If swelling is established separately, the segment code can pass it toward the tail and invoke digestion. That routine heals **10 health points** and increments a meal counter. Every third counted meal can start **500–999 ticks** of shedding when the cooldown has expired. When an established shedding timer finishes, it produces **one [Shed Snake Skin](../items/ShedSnakeSkin.md) (`minecraft:shed_snake_skin`)**, then sets a **1,000–2,999 tick** cooldown. These are conditional implementation details, not a verified ordinary Survival production method. [Swell propagation][swell] · [Digestion routine][feeding] · [Shedding reward][shed] · [Skin registration][skin]

## Care and persistence

- Keep a separate enclosure with room for the head and trailing body. Pacification leaves its animal-prey targeting active
- Provide water with an accessible dry bank and air above it. The active water goals alternate between land and swimming and try to leave water for a land target or shedding. Neither the head nor the parts are included in the bundled underwater-breathing tag or its nested tags, so do not seal the snake underwater. [Water behavior][water] · [Water goals][goals] · [Breathing and drowning][breathing] · [Underwater tag][underwater] · [Nested undead tag][undead]
- A [Lead](../items/Lead.md) can attach through the shared leash rules; it does not tame the snake. There is no normal saddle, riding, sit, or owner-follow interaction in this class. [Leash eligibility][leash] · [Lead interaction][lead-interaction] · [Food interaction][interaction]
- Ordinary distance despawning is disabled by the Animal superclass. Its saved data retains variant, passive time, meal count, shedding time/cooldown, and the first body-part UUID; body parts save their parent/child links and position in the chain. Do not assume that an in-progress swelling effect survives reloading: its swelling value is not among the part's saved fields. [Animal persistence][persistence] · [Head saving][save] · [Part saving][part-save]

## Notes

The entity uses `MobCategory.CREATURE`, the `EntityAnaconda` class, and a registered adult head size of **1.2 blocks wide × 0.6 blocks tall**. That size is not the length of the complete multipart snake. It comes from bundled Alex's Mobs content integrated into MattMC. Both head and part attribute builders are registered. [Entity registration][entity] · [Attribute registration][attributes]

### Drops

No `minecraft:entities/anaconda` death-loot resource was found under the active `data/minecraft/loot_table/` path, including bundled optional-pack overrides. The body-part registration explicitly has no loot table. Missing loot tables fall back to empty loot, so no dedicated skin, meat, or Looting-scaled item drop is established here. Adults have a base death reward of **1–3 experience**, subject to the shared player-credit, mob-loot, and experience-modifier rules. [Loot-table naming][loot-name] · [Bundled data][data] · [Part registration][entity] · [Missing-table fallback][loot-fallback] · [Animal experience][persistence] · [Experience conditions][death-xp]

### Rendering limitation

The registered head and segment renderers use `ModelAnaconda`, which inherits the Citadel proxy implicated in [issue #803](https://github.com/HungLo2020/MattMC/issues/803). For an admitted visible ordinary-body submission with a readable texture and finite transform, the native path traverses the proxy's empty ModelPart tree and can reach its empty-mesh exception. **This is a conditional source finding, not a reproduced Anaconda crash or invisibility report.** [Renderer registration][renderers] · [Head renderer][renderer] · [Part renderer][part-renderer] · [Model][model] · [Inheritance][advanced-model] · [Proxy][proxy] · [Body submission][body-submit] · [Collector admission][collector] · [Native extraction][extraction] · [Tree traversal][traversal]

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Resource checks included exact filenames, tag dependencies, all bundled data namespaces, and optional data packs. No in-game spawning, feeding, breeding, constriction, shedding, save/reload, or rendering test was run.

Related: [Anaconda Spawn Egg](../items/AnacondaSpawnEgg.md) · [Shed Snake Skin](../items/ShedSnakeSkin.md) · [Lead](../items/Lead.md) · [Mobs](Mobs.md)

[health]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L96-L98
[body]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L334-L389
[interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L146-L153
[entity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L235-L249
[egg]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1798
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1972
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L52-L178
[spawn-helpers]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L100-L107
[ground]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/anaconda_spawns.json#L1-L10
[roll]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L62-L72
[variant]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L575-L579
[goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L121-L140
[prey]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/anaconda_targets.json#L1-L10
[target-predicate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L79-L81
[melee]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L581-L620
[constriction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L292-L322
[damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1777
[part-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnacondaPart.java#L74-L77
[part-damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnacondaPart.java#L246-L259
[immunity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L549-L552
[food]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/anaconda_foodstuffs.json#L1-L9
[tempt]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/TemptGoal.java#L34-L64
[mob-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1103
[lead-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2146-L2178
[animal-food]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[passive]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L554-L561
[feeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L563-L569
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L32-L80
[offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L527-L535
[breeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L228
[age]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L168
[kill-overload]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L537-L547
[kill-callback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L1873-L1877
[death]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1413-L1435
[swell]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnacondaPart.java#L136-L164
[shed]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L395-L404
[skin]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1745
[water]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L502-L525
[breathing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L442
[underwater]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json#L1-L19
[undead]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/undead.json#L1-L9
[leash]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[persistence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L155-L184
[part-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAnacondaPart.java#L372-L408
[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L113-L114
[loot-name]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2050-L2068
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[death-xp]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1488
[renderers]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L79-L80
[renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/RenderAnaconda.java#L11-L46
[part-renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/RenderAnacondaPart.java#L15-L38
[model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/model/ModelAnaconda.java#L14-L56
[advanced-model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/AdvancedEntityModel.java#L17-L25
[proxy]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/basic/BasicEntityModel.java#L13-L55
[body-submit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L1044-L1054
[collector]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L283-L302
[extraction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9705-L9728
[traversal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/model/geom/ModelPart.java#L151-L169
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
