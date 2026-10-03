# Bunfungus

The **Bunfungus** is a large, aggressive mushroom rabbit with **80 health points (40 hearts)**. It targets nearby mobs and retaliates against attackers, but does not select players merely because they approach. Its base attack damage is **8 points** and its movement-speed attribute is **0.21**. Keep it separate from livestock and other Bunfunguses. [Attributes][attributes] · [Attribute registration][attribute-registration] · [Target selection][goals] · [Target checks][target-checks]

## Obtaining

Use the [Bunfungus Spawn Egg](../items/BunfungusSpawnEgg.md) from Creative for deliberate placement. The entity factory, matching egg and Creative Spawn Eggs entry are registered. [Entity registration][entity] · [Egg registration][egg] · [Creative entry][creative] · [Egg placement][egg-placement]

**No natural encounter location is established in this snapshot.** No Bunfungus entry was found in the checked biome or structure spawn lists, including bundled optional data packs. The natural-spawn system selects from those lists. Its solid-ground spawn helper has no registered caller, and its separate one-in-ten spawn-rule roll does not create a spawn location. Do not search Mushroom Fields expecting a verified wild source. [Natural selection][natural] · [Spawn-list consumers][spawn-lists] · [Placement registration][placements] · [Local spawn rules][spawn-rules] · [Bundled data][data]

There is also **no active mushroom-fed Rabbit conversion route** in the checked source. The Bunfungus has a temporary “rabbit form” timer, but it starts at zero and the only active setter call counts an already positive value down. Neither mushrooms nor any other ordinary interaction starts that transformation here. [Initial state][state] · [Timer update][transform-tick] · [Timer helpers][transform-state] · [Interaction][feeding] · [Rabbit implementation][rabbit]

## Behavior

### Targeting and combat

The Bunfungus searches for eligible nearby **mobs**, with a base follow range of **32 blocks** and a line-of-sight check. This includes animals, Munguses and other Bunfunguses; the target goal has no friendly-species filter. Shared targeting still excludes allies, invalid targets and Ghasts. Players are outside that automatic target class, but hurting a Bunfungus can make it retaliate, subject to the usual targeting and game-rule checks. [Goals][goals] · [Nearest-target search][nearest] · [Target checks][target-checks] · [Ghast exclusion][attack-types] · [Retaliation][retaliation]

Its active melee goal starts a **belly attack** when a target is less than **3.5 blocks** away. At animation tick five, if the target remains in range, the attack checks a box extending **two blocks beyond its body** and hits the target plus nearby mobs of the shared monster class. Each hit uses its base attack-damage value and can launch a grounded victim upward and outward; armor and other damage handling still apply. The bundled area-attack exclusion tag protects Creepers. [Melee goal][melee] · [Hit timing][combat] · [Launch][launch] · [Area exclusions][aoe-tag] · [Nested exclusions][ignore-tag] · [Damage dispatch][damage-dispatch]

Combat has two limits worth planning around: the melee goal does **not request a pursuit path**, and its closer-range slam branch cannot be selected because the belly-attack condition catches that same distance first. Do not rely on it as a dependable chasing guard. The separately named `minecraft:bunfungus_ignores` tag is not consulted by target selection; its effect here comes only from inclusion in the area-attack exclusion tag. [Melee conditions][melee] · [Tag declaration][target-tags] · [Nested area tag][aoe-tag]

A hurt [Mungus](Mungus.md) can alert a nearby Bunfungus that has no target. The alert ignores attacks made by Bunfunguses themselves. To defend a Mungus against a **player**, the Bunfungus must additionally have its special fed state. With the bundled food tag missing, that is not an ordinary player-triggered protection route. [Mungus alert registration][alert-registration] · [Alert conditions][alert] · [Fed-state requirement][food-test]

### Feeding, taming and breeding

**No usable Bunfungus food is supplied by the checked bundled data.** Feeding tests `minecraft:bunfungus_foodstuffs`, whose expected file is `data/minecraft/tags/item/bunfungus_foodstuffs.json`; it is absent from the base data and bundled optional packs. The method's “carrot” name does not make normal or Golden Carrots valid. [Food test][food-test] · [Tag declaration][target-tags] · [Tag loading][tag-loading] · [Tag membership][membership] · [Bundled data][data]

A server data pack can supply that tag. With eligible food, using an item on a Bunfungus whose main hand is empty transfers **one** item to it, consuming the player's item outside Creative. Its eating cycle then consumes that held item, heals **8 health points**, grants temporary Regeneration and sets the fed state. This is **not taming**: there is no owner, sit command, breeding interaction or baby Bunfungus implementation. [Feeding][feeding] · [Eating][eating] · [Mob class and state][state-class]

Once fed, an awake Bunfungus can beg toward a player holding tagged food in either hand: the search uses its body box expanded by **eight blocks**, and following stops when the player is over **10 blocks** away or stops holding food. Ordinary dropped food is not a feeding route: no food-pickup goal is registered and its inherited loot pickup starts disabled. [Begging goal][begging] · [Registered goals][goals] · [Default pickup][pickup]

Using the matching spawn egg on an existing Bunfungus does **not** produce offspring. That shared interaction requires the newly created mob to become a baby; Bunfungus inherits an empty baby setter and always reports itself as adult. Place another Bunfungus with the egg on a block instead. [Egg offspring check][egg-offspring] · [Baby setter][baby-setter] · [Adult state][adult]

### Sleep and care

A Bunfungus can randomly fall asleep when it has no target, is not begging, is out of water and is in a dimension without fixed time. There is **no day-versus-night requirement** in that check. Water, a target, begging or a fixed-time dimension clears sleep; ordinary movement is suppressed while asleep. Its target selector remains active, so sleep does not make a shared animal pen safe. [Sleep check][sleep] · [Movement][movement] · [Goals][goals]

It heals **one health point every 40 ticks**, about **two seconds while ticking at normal speed**, without food. Give its **1.85-block-wide, 2.1-block-tall** body a roomy enclosure, keep its head above water and keep fire or lava out. Its float goal and reduced water slowdown do not grant underwater breathing, and its registration grants no fire immunity. A [Lead](../items/Lead.md) is supported by the inherited leash rule. [Automatic healing][healing] · [Size and registration][entity] · [Water movement][water] · [Breathing and drowning][breathing] · [Water-breathing tag][breathing-tag] · [Leashing][leashing]

The active landing callback suppresses ordinary fall damage. This should not be generalized to immunity from every fall-related damage source: its older two-argument fall-damage method does not override the current three-argument method. [Bunfungus fall callbacks][fall] · [Landing dispatch][landing] · [Current damage method][fall-current]

## Notes

- Registered as `minecraft:bunfungus`, with spawn egg `minecraft:bunfungus_spawn_egg`, category `MobCategory.CREATURE` and entity class `EntityBunfungus`. This is bundled Alex's Mobs content integrated into MattMC. The creature category does not prevent its aggressive mob targeting. [Entity registration][entity] · [Egg registration][egg] · [Goals][goals]
- It does **not despawn merely for being far from a player**, and its registration allows it in Peaceful. Its special sleeping, begging, fed and transformation states are not saved by Bunfungus-specific hooks; after an unload/reload they start from their defaults. Ordinary health, effects and equipment use the inherited save system. [Distance behavior][spawn-rules] · [Despawn dispatch][despawn] · [Peaceful default][peaceful] · [Initial state][state] · [Complete entity class][bunfungus] · [Inherited saving][saving]
- There is **no bundled Bunfungus death-loot table** at `data/minecraft/loot_table/entities/bunfungus.json`, and no Bunfungus-specific death-item callback or experience reward is assigned. Missing loot tables resolve to empty loot; ordinary equipment drops remain a separate inherited possibility. Do not treat this mob as an established source of mushrooms or rabbit drops. [Default loot key][loot-key] · [Bundled data][data] · [Loot fallback][loot-fallback] · [Default experience][experience] · [Equipment drops][equipment-drops]
- Rendering is **source-reviewed only**. An ordinary visible, awake Bunfungus reaching the Rust whole-frame model route with a readable texture and finite transform has the empty-model risk tracked in [#803](https://github.com/HungLo2020/MattMC/issues/803): the native extractor visits an empty proxy root and throws when it obtains no geometry. The inspected path rolls back and rethrows rather than falling back. This is a conditional source prediction, not an observed crash or invisibility claim. Separately, the renderer requests `minecraft:textures/entity/bunfungus_sleeping.png` while asleep, but that file is absent from bundled assets; that texture problem can occur before the empty-geometry check. [Renderer registration][renderer-registration] · [Bunfungus renderer][renderer] · [Model inheritance][model] · [Advanced model base][advanced] · [Proxy model][proxy] · [Body submission][body-submit] · [Direct-texture route][collector] · [Native eligibility][eligibility] · [Native extraction][extraction] · [Texture and geometry][texture-extraction] · [Failure handling][render-failure] · [Bundled textures][textures]

Related: [Mungus](Mungus.md) · [Rabbit](Rabbit.md) · [Mushrooms](../blocks/Mushrooms.md) · [Spawn eggs](../items/SpawnEggs.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked entity and egg factories, attribute registration, active tick/AI/interaction/damage dispatch, spawn-list consumers, exact resource names, nested tags, all bundled optional data packs, save/load behavior and the renderer-to-native extraction chain. No in-game spawning, combat, feeding, sleep, reload or rendering test was run. Server data packs can change food, spawn and loot data; they do not by themselves supply the missing Java breeding, conversion, pursuit or state-saving behavior. [Server tick][server-tick] · [AI dispatch][ai-dispatch] · [Interaction dispatch][interaction-dispatch]

[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L80-L82
[attribute-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L133
[entity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1287-L1293
[egg]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1812
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1988
[egg-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L110
[natural]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L280-L325
[spawn-lists]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L49-L178
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L102-L112
[state]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L154-L162
[transform-tick]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L281-L308
[transform-state]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L358-L368
[feeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L370-L385
[rabbit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Rabbit.java#L1-L660
[goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L118-L148
[target-checks]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/targeting/TargetingConditions.java#L60-L93
[nearest]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal.java#L22-L84
[attack-types]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L239-L242
[retaliation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/HurtByTargetGoal.java#L27-L71
[melee]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/BunfungusAIMelee.java#L14-L49
[combat]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L220-L249
[aoe-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/bunfungus_ignore_aoe_attacks.json#L1-L8
[ignore-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/bunfungus_ignores.json#L1-L10
[damage-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1784
[target-tags]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L47-L52
[alert-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L156-L168
[alert]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/MungusAIAlertBunfungus.java#L31-L101
[food-test]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L421-L427
[tag-loading]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/tags/TagLoader.java#L142-L168
[membership]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/Holder.java#L167-L177
[eating]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L255-L280
[state-class]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L47-L78
[begging]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/BunfungusAIBeg.java#L22-L65
[pickup]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L115-L152
[egg-offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L185
[baby-setter]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1264-L1265
[adult]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L532-L534
[sleep]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L308-L317
[movement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L387-L393
[healing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L251-L254
[water]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L118-L152
[breathing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L442
[breathing-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json#L1-L19
[leashing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[fall]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L164-L169
[landing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L1398-L1416
[fall-current]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1710-L1729
[despawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L598-L631
[peaceful]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2068
[bunfungus]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L1-L433
[saving]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L730-L838
[loot-key]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[experience]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L287-L307
[equipment-drops]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L813-L839
[renderer-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L108
[renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/RenderBunfungus.java#L11-L49
[model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/model/ModelBunfungus.java#L12-L42
[advanced]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/AdvancedEntityModel.java#L17-L28
[proxy]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/basic/BasicEntityModel.java#L13-L55
[body-submit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L1044-L1061
[collector]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L283-L302
[extraction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9705-L9728
[render-failure]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9442-L9463
[server-tick]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerLevel.java#L773-L786
[ai-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L634-L672
[interaction-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1111
[launch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBunfungus.java#L320-L328
[texture-extraction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L11943-L11961
[eligibility]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L6910-L6944
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[textures]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/assets
