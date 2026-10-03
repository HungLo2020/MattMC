# Skunk

The **Skunk** is a neutral animal with **8 health points (4 hearts)**. It usually avoids feared creatures, but a harassed adult can spray nearby living things with Nausea and any status effects it is carrying. Its spray also leaves a collectible surface coating. [Attributes][attributes] · [Attribute binding][attribute-binding] · [Behavior][skunk]

## Obtaining

Use the [Skunk Spawn Egg](../items/SkunkSpawnEgg.md) from Creative for deliberate placement. The egg is registered, has a Creative-tab entry, and uses the shared spawn-egg placement path. [Egg registration][egg] · [Creative entry][creative] · [Placement][egg-use]

**No natural Skunk encounter is established in the checked defaults.** No entry was found in biome or structure spawn lists, bundled world-generation data, or spawn-placement registrations. The mob's spawn-roll check does not add it to any spawn list; natural spawning first selects a listed entity. Do not rely on an upstream biome description to find one here. [Natural selection][natural] · [Biome definitions][biomes] · [Spawn placements][placements] · [Bundled data][data] · [Skunk check][spawn-check] · [Roll helper][spawn-roll]

## Behavior

Skunks have no melee attack or prey-hunting goal. They wander, look around, follow parents while young, and try to avoid creatures in `minecraft:skunk_fears`: **Players, Grizzly Bears and Polar Bears** in the bundled tag. Avoidance searches up to **10 blocks** away, needs a valid escape path and obeys visibility and targeting checks. Creative and Spectator Players are excluded; the shared targeting check also excludes Players in Peaceful. Mere proximity is therefore not a guaranteed spray trigger. [Goals][goals] · [Fear tag][fear-tag] · [Escape-path check][avoid] · [Target checks][targeting] · [Peaceful check][peaceful]

Successful avoidance and damage-induced panic build a harassment counter, which also drains over time. Panic uses the shared damage-tag check. The last creature that hurt the skunk can determine where it aims its rear-facing spray. This is defensive behavior, not owner protection: the class has no taming or sitting interaction. [Goals][goals] · [Panic][panic] · [Harassment and aim][trigger] · [Aim selection][spray] · [Complete species class][skunk]

## Food, breeding and babies

Both hand-feeding and held-food attraction check **`minecraft:skunk_breedables`**. That item-tag file is absent from the bundled data, including optional packs. No default lure, breeding food or baby-growth food is therefore established; familiar foods from other versions are not a substitute for loaded tag membership. There is no separate dropped-food eating or food-healing routine. [Food check][spawn-check] · [Attraction][goals] · [Tag key][tag-keys] · [Namespace][tag-namespace] · [Tag loading][tag-loader] · [Bundled data][data] · [Inherited feeding][feeding]

If a data pack supplies that tag, the shared animal interaction lets eligible adults enter love mode and babies grow faster. Two compatible adults produce a baby Skunk, then each parent has a **6,000-tick breeding cooldown**. A newborn starts at **−24,000 age ticks** and ordinarily reaches adulthood after **20 minutes while ticking at 20 TPS**. Babies follow parents and cannot start the harassment-triggered spray. [Feeding][feeding] · [Offspring type][offspring] · [Breeding][breeding] · [Growth][age] · [Goals][goals] · [Adult-only trigger][trigger]

Using a matching Skunk Spawn Egg on an existing Skunk is a separate way to create a baby; it needs no breeding food or second adult. The active mob interaction dispatch calls the offspring routine and sets the new mob to baby age. [Interaction dispatch][interaction] · [Egg offspring][egg-baby]

## Spray and cleanup

Once an adult's harassment counter exceeds 200 and its cooldown is zero, it starts a **60–119-tick spray** and sets a **200–399-tick cooldown**. The cooldown counts down during spraying too. These counters do not promise a fixed delay after approaching or hitting a skunk. It stops navigating during the spray and turns its rear toward the stored target position. [Trigger and cooldown][trigger] · [Spray goal][spray]

After the animation builds up and the spray goal has run long enough, eligible attempts have a 50% chance to trace toward a surface up to **5 blocks** away. Living entities in an expanded box along that path, **except other Skunks**, are offered **300 ticks of Nausea (15 seconds at 20 TPS)** and copies of the skunk's current effects. Bystanders can be hit too; the fear tag is not a filter for spray victims. Normal effect immunity and replacement rules still apply. Nausea itself adds no attack or movement attribute modifier, and the spray has no separate direct-damage call. [Spray placement and victims][spray] · [Effect acceptance][effect-acceptance] · [Nausea registration][nausea]

The surface coating, active spray and end-of-spray cloud are separate. Coating can appear in an air or replaceable cell against a valid support face; this branch does not check `mobGriefing`. **Walking over the remaining coating does not apply status effects.** Use a **Glass Bottle on its exposed coated face** to remove one face and receive a [Stink Bottle](../items/StinkBottle.md), or break it, remove its support, or let it decay. See [Skunk Spray](../blocks/SkunkSpray.md) for face selection, bottle consumption, attachment and decay details. [Placement][spray] · [Coating behavior][coating] · [Bottling][bottling]

A skunk carrying effects also creates an area-effect cloud when its spray ends, but **that cloud does not reach its effect-application phase in the checked code**. Its duration calculation turns the shared default of −1 into 0, so the server removes it at the 20-tick wait boundary. The active spray above still copies effects independently. This is a source finding, not a measured gameplay result. [Cloud setup][cloud] · [Default duration][cloud-default] · [Duration assignment][cloud-duration] · [Removal before application][cloud-tick] · [Copied-cloud issue #807](https://github.com/HungLo2020/MattMC/issues/807)

For effects already on the player, leave the active spray and let them expire, or drink a [Milk Bucket](../items/MilkBucket.md). Milk clears all status effects, including beneficial ones; it does not clean nearby coated blocks, and another spray can apply effects again. [Milk item][milk-item] · [Milk consumption][milk-consumable] · [Effect removal][milk-effect]

## Care and persistence

Provide a dry enclosure with accessible air and safe footing. The floating goal helps a skunk jump in water, but does not grant underwater breathing. Skunks are absent from the bundled breathing and fall-immunity tags, and their entity registration has no fire immunity: drowning, falls and lava remain hazards. [Floating][float] · [Breathing check][breathing] · [Breathing tag][breathing-tag] · [Drowning][drowning] · [Fall damage][fall] · [Fall-immunity tag][fall-tag] · [Registration][entity] · [Lava damage][lava]

Skunks inherit the animal rule that prevents ordinary distance despawning; a Name Tag is not required for that protection. Their age is saved, but the species has no save/load fields for spray time, harassment or cooldown. Do not plan a spray setup around those counters surviving a reload. [Animal persistence][persistence] · [Despawn dispatch][despawn] · [Saved age][save-age] · [Species state][skunk]

## Drops and experience

The default death-loot key is `minecraft:entities/skunk`, but its table is absent from the bundled data, including optional packs. The missing-table fallback is empty, and Skunk has no custom death-drop routine: **no species item drop or Looting bonus is defined** in these defaults. Bottling the surface coating is the implemented Stink Bottle source. Custom data packs and equipment drops are separate possibilities. [Loot key][loot-key] · [Bundled data][data] · [Missing-table fallback][loot-fallback] · [Species class][skunk] · [Bottling][bottling]

Adults can give **1–3 base death XP** when normal player-credit and `doMobLoot` conditions are met and the experience has not already been consumed. Babies give no death XP. [Animal XP][persistence] · [XP conditions][death-xp] · [Baby exclusion][baby-xp]

## Rendering limitation

Skunk's actual model uses the Citadel proxy involved in [issue #803](https://github.com/HungLo2020/MattMC/issues/803). When its visible ordinary body is selected by the Rust whole-frame semantic renderer with a readable texture and valid transform, the current direct-texture path traverses an empty model-part tree and reaches the empty-mesh exception. **This is conditional source evidence; no Skunk crash or invisibility was reproduced in game.** [Renderer registration][renderer-registration] · [Renderer][renderer] · [Skunk model][model] · [Model inheritance][model-base] · [Proxy root][proxy] · [Selected submission][selected] · [Body submission][living-submit] · [Collector][collector] · [Admission][render-admission] · [Extraction guard][render-extract] · [Traversal][render-traverse] · [Empty-tree behavior][model-visit]

## Notes

* Entity ID: `minecraft:skunk`; spawn egg ID: `minecraft:skunk_spawn_egg` [Entity registration][entity] · [Egg registration][egg]
* The registered adult body is **0.5 blocks wide × 0.5 blocks high**, with a 0.4-block eye height; these are entity dimensions, not a tested enclosure minimum [Entity registration][entity]
* Its active class is `net.alexsmobs.entity.EntitySkunk`, registered in `MobCategory.CREATURE` from bundled Alex's Mobs content integrated into MattMC [Entity registration][entity]

Related: [Skunk Spray](../blocks/SkunkSpray.md) · [Skunk Spawn Egg](../items/SkunkSpawnEgg.md) · [Stink Bottle](../items/StinkBottle.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked active registrations and shared dispatch, spawn lists, resource filenames and nested tag membership, optional packs, breeding, damage and effects, cloud lifetime, cleanup, persistence, loot and the selected renderer path. No in-game spawning, feeding, breeding, spraying, cleanup, environmental damage, save/reload or rendering test was run. Data packs and custom entity data can change these defaults.

[skunk]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySkunk.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySkunk.java#L58-L64
[attribute-binding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L231
[entity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1182-L1184
[egg]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1949
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2080
[egg-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L76-L103
[natural]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[spawn-check]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySkunk.java#L107-L113
[spawn-roll]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L62-L71
[goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySkunk.java#L73-L105
[avoid]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/AvoidEntityGoal.java#L34-L76
[targeting]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/targeting/TargetingConditions.java#L60-L93
[peaceful]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L917-L927
[panic]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/PanicGoal.java#L42-L63
[tag-keys]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L230-L232
[tag-namespace]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L250-L259
[tag-loader]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/tags/TagLoader.java#L142-L168
[feeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySkunk.java#L236-L240
[breeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L227
[age]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L168
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L185
[interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1104
[trigger]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySkunk.java#L143-L180
[spray]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySkunk.java#L260-L320
[effect-acceptance]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L978-L1012
[nausea]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/effect/MobEffects.java#L57
[cloud]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntitySkunk.java#L184-L202
[cloud-default]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L50-L69
[cloud-duration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L137-L153
[cloud-tick]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L191-L238
[coating]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/block/BlockSkunkSpray.java#L34-L158
[bottling]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/block/BlockSkunkSpray.java#L93-L125
[milk-item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1531-L1533
[milk-consumable]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/component/Consumables.java#L64
[milk-effect]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L20-L23
[float]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/FloatGoal.java#L10-L29
[breathing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L386
[drowning]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L443
[lava]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L574-L590
[fall]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1710-L1729
[persistence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[despawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L606-L630
[save-age]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AgeableMob.java#L106-L118
[loot-key]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[death-xp]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487
[baby-xp]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L566
[renderer-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L259
[renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/RenderSkunk.java#L9-L32
[model]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/model/ModelSkunk.java#L10-L73
[model-base]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/AdvancedEntityModel.java#L17-L26
[proxy]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/citadel/client/model/basic/BasicEntityModel.java#L13-L56
[selected]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/LevelRenderer.java#L745-L792
[living-submit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L1044-L1059
[collector]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L283-L302
[render-admission]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L6910-L6943
[render-extract]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9705-L9728
[render-traverse]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L11943-L11957
[model-visit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/model/geom/ModelPart.java#L151-L169
[fear-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/skunk_fears.json
[breathing-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[fall-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/fall_damage_immune.json
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[biomes]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/data/worldgen/biome
