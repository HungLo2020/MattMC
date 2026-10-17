# Mimic Octopus

The **Mimic Octopus** is a neutral, tameable mob that camouflages itself and copies other creatures. It has **16 health points (8 hearts)**, no base armor and a **0.8 × 0.6-block body**. Its registered attack attribute is 2, but its upgraded form attacks use their own damage values. **Bucket transport currently loses ownership and upgrades**, and ordinary full submersion can cause drowning in this source snapshot. [Registration][entity] · [Attributes][attributes] · [Attribute wiring][attribute-wiring] · [Bucket data][bucket-data] · [Air handling][air]

## Obtaining

For Creative testing or mapmaking, use the [Mimic Octopus Spawn Egg][egg]. It is category-listed for MattMC's [inventory item browser][browser], and ordinary egg placement adds an octopus to the world. Using a matching egg directly on an octopus can create an untamed baby. A supplied [Bucket of Mimic Octopus][bucket] also releases an octopus, but the bucket itself is absent from the checked category catalog. [Items][items] · [Egg listing][egg-category] · [Egg placement][egg-use] · [Baby helper][egg-baby] · [Offspring creation][offspring] · [Category assembly][categories] · [Browser assembly][browser-list]

**No natural biome, structure or event acquisition route was found** in the active source and bundled data. The class contains a substrate test for Sand, Gravel, Clay or Dirt at or below sea level, plus a spawn-roll method, but the substrate test is not registered in SpawnPlacements and does not establish a natural population. Do not assume an ocean Survival source from an upstream mod guide. [Spawn helper][spawn-rule] · [Substrate tag][spawn-tag] · [Spawn roll][spawn-roll] · [Placement registry][placements] · [Biome code][worldgen] · [Bundled data][data]

## Behavior

### Wild behavior and camouflage

Octopuses switch between swimming and land navigation, seek water when appropriate and can leave it to pursue a target on land. Wild ones flee non-Creative players and the tagged Drowned, Dolphin, Guardian and Elder Guardian. Holding **Tropical Fish** tempts them; this higher-priority goal clears their block camouflage while running, which helps with taming. [Goals][goals] · [Flee behavior][flee] · [Fear tag][fears] · [Water decisions][water-goals]

Block camouflage copies the block below the octopus. While active, it reduces the visibility factor used by ordinary visibility-aware target checks to **one tenth**; it is not complete invisibility. Octopuses can also approach a Creeper, Guardian or Pufferfish and copy its form when within 5 blocks with line of sight. That copying sets a 1,200-tick form timer, about a minute at 20 TPS. These are form changes, not conversion into another entity. [Camouflage][environment] · [Visibility multiplier][camo] · [Target checks][visibility] · [Nearby copying][copy-mob]

The source defines ordinary/block-camouflage, Creeper, Guardian, Pufferfish and Mimicube states. The registered renderer has textures and overlays for them; this review does not certify their appearance in a running client. [States][forms] · [Renderer state][render] · [Overlay path][render-overlay] · [Renderer registration][render-registration]

### Taming, healing and breeding

Feed a wild octopus **raw Cod, raw Salmon, Tropical Fish or Pufferfish**. Food is consumed and the feeding count increases even when it is disguised, but taming succeeds only in its ordinary state **without block camouflage**. Eligible attempts have a 50% chance once more than five fish have been fed; once the accumulated count exceeds eight, the next eligible attempt succeeds. Cooked fish are not in the bundled taming tag. [Taming interaction][tame-heal] · [Food tag][tame-foods]

For a tame octopus, **raw Cod or Salmon heals 5 health points** when injured. Pufferfish instead selects its mimic form because that interaction runs first. Tropical Fish overlaps with breeding/growth and healing: the inherited breeding interaction runs first, and an injured octopus can consume a second fish for healing if fish remain in the stack. [Interaction order][form-use] · [Healing][tame-heal] · [Inherited feeding][breeding-interact]

Feed **two tamed adults Tropical Fish** to put them in love and breed them. Babies can be fed Tropical Fish to speed growth. The offspring factory creates a fresh octopus without copying either parent's owner or upgrade, so **the baby must be tamed separately**. Ordinary breeding applies a 6,000-tick parent cooldown, about 5 minutes at 20 TPS. [Food gate and goals][goals] · [Breeding food][breed-foods] · [Feeding][breeding-interact] · [Breeding and cooldown][breeding] · [Offspring factory][offspring]

After feeding the **last Tropical Fish in a stack**, the owner should recheck the current command: the server can process adult breeding, empty the stack and then fall through to command cycling. The species handler checks only the distinct `SUCCESS` result instead of also honoring the inherited `SUCCESS_SERVER` result. [Inherited feeding][breeding-interact] · [Species fallback][commands] · [Result constants][interaction-results] · [Empty-stack checks][empty-stack]

### Moisture and breathing

The octopus starts with **60,000 moisture ticks**, about 50 minutes at 20 TPS. Being in water resets that amount; otherwise it decreases each tick. Once depleted, it clears its custom sitting flag and attempts dry-out damage roughly once a second, randomly choosing 0 or 1 damage. It also heals **2 health points every 40 ticks**, so this moisture counter is not a guaranteed death countdown. [Initial moisture][moist-default] · [Moisture tick][moisture] · [Passive healing][healing]

**Moisture is separate from air.** An ordinary AI-enabled octopus has no active underwater-breathing override and is absent from the bundled underwater-breathing tag. With its eyes continuously submerged outside a bubble column, the inherited routine drains its usual 300 air and starts dealing 2-point drowning hits after about 320 ticks, or 16 seconds at 20 TPS; further hits follow as air depletes again. Provide access to air rather than assuming a deep tank is safe. Water Breathing and other normal protections can change this. The octopus's old `updateAir(int)` helper is not called by the current base tick; its only active direct air reset is in the NoAI branch. [Air routine][air] · [Breathing tag][breathers] · [Nested undead tag][undead] · [Air capacity][air-default] · [Air rate][air-rate] · [Damage threshold][air-threshold] · [Current damage path][damage] · [Unused helper][old-air] · [NoAI branch][moisture]

A **Slimeball** used on a tame octopus below **24,000 moisture** restores it to **48,000** and is consumed. This moisturizing interaction takes priority over the combat upgrade below, so that Slimeball does not count toward upgrading. Rain is not a refill condition in this tick method. [Moisturizing tag][slime-moist] · [Interaction priority][upgrade] · [Moisture conditions][moisture]

### Owner commands and held items

Use an ordinary empty-hand interaction as the owner to cycle **Wander → Follow → Sit → Wander**; a newly tamed octopus starts at Wander, so the first click selects Follow. Follow uses the water-aware owner-follow goal. Use an empty hand to avoid the item-specific interactions. [Command handler][commands] · [Default command][moist-default] · [Command names][command-names] · [Follow goal][follow]

**Sit is not a dependable combat-disable command in this version.** It stops ordinary movement through the octopus's custom sitting flag, but its setter does not update the separate inherited flag read by the shared sitting and owner-defense goals. Its attack goal also does not test the custom sitting flag. Keep an upgraded octopus away from targets and vulnerable builds even when sitting. [Custom sitting][sit] · [Inherited flag][base-sit] · [Shared sitting goal][sit-goal] · [Owner-defense check][owner-defense] · [Attack gate][attack-gate]

Sneak-interact as the owner with an item that has no earlier special interaction to give it **one held item**. Sneak-interact with an empty hand to drop its existing held item for collection. **A Water Bucket captures the octopus before this equipment handler runs**, including while sneaking; it is not an ordinary way to equip a permanent water supply, even though the moisture code checks for a held Water Bucket. Octopuses can be leashed. [Item transfer][commands] · [Bucket priority][bucket-gate] · [Held-water check][moisture] · [Leash rule][leash]

### Form controls and combat upgrade

On a tame octopus, use these items to select a form; the form-selection branch does **not consume them**:

* **Gunpowder or Creeper Head:** Creeper [Tag][creeper-items]
* **Prismarine Shard or Prismarine Crystals:** Guardian [Tag][guardian-items]
* **Pufferfish or Bucket of Pufferfish:** Pufferfish [Tag][puffer-items]

Manual selection has a 20-tick cooldown. It sets a 1,200-tick form timer for an ordinary octopus or 120 ticks for an upgraded one. Use an **Ink Sac**, also unconsumed, to toggle whether environment camouflage, timer reversion and automatic combat form changes are allowed. This is **not a complete form lock**: explicit item selection and the nearby-mob copying goal do not honor that flag. Locking the ordinary form blocks the attack goal while that form remains active. [Selection and toggle][form-use] · [Ink tag][ink] · [Timer reversion][form-time] · [Nearby-copy checks][copy-mob] · [Attack gate][attack-gate] · [Combat form choice][scare]

To enable damaging mimic abilities, feed a tame, not-yet-upgraded octopus **Slimeballs** while the moisturizing branch is not taking priority. The first two upgrade feedings cannot succeed; feedings 3–5 each have a 50% chance, and feeding 6 is guaranteed. Success briefly selects the **Mimicube** state for a 40-tick timer and clears the form lock. The actual bundled item is Slimeball; the tag's older numeric comment and upstream Mimicream instructions do not describe this implementation. [Upgrade food][slime-upgrade] · [Upgrade handler][upgrade]

Tame octopuses acquire targets when their owner attacks, their owner is attacked, or the octopus itself is hurt. Without the upgrade, the attack goal uses mimicry to frighten a mob target, attempting to steer it away and clear its target. Upgraded forms add these attacks after the form transition: [Target goals][goals] · [Owner retaliation][owner-defense] · [Owner attack][owner-attack] · [Scaring][scare] · [Attack branches][combat]

* **Pufferfish:** a close-range hit for 4 damage and Poison III for 400 ticks, normally 20 seconds
* **Guardian:** close contact for 1 damage and a water-dependent beam that deals 5 damage after a 30-tick charge; the ranged branch requires line of sight within 7 blocks
* **Creeper:** an explosion with power from 1 up to, but below, 2, without a self-discard step; it can damage blocks when `mobGriefing` allows it

These values are the attack requests before target defenses. On land, automatic combat mimicry selects Creeper; in water it selects Guardian or Pufferfish. The code does not add a separate Mimicube combat ability. [Combat choice][scare] · [Damage branches][combat] · [Beam timing][laser] · [Explosion behavior][explosion]

### Persistence and bucket transport

Tamed octopuses and bucket-released octopuses avoid ordinary distance despawning. World saves retain owner data, age, command, moisture, disguise, upgrade, form lock and feeding progress. **Capture is a different path:** only a living, already-tamed octopus accepts a Water Bucket, and the capture check is not restricted to its owner. [Persistence][persistence] · [Species save][world-save] · [Owner save][owner-save] · [Age save][age-save] · [Capture gate][bucket-gate] · [Pickup helper][capture]

**Do not bucket a trained pet expecting to keep its training.** Hand release really adds an octopus to the world and restores health, custom name and common flags, but it loses taming/owner, upgrade, command, disguise, age and held item. It receives full moisture and bucket-origin persistence, then must be tamed again before another capture. Retrieve any held item first. See [Bucket of Mimic Octopus][bucket] for the exact limitations and Dispenser behavior. [Capture data][bucket-data] · [Common data][common-data] · [Actual release][bucket-release] · [Initialization][create]

## Drops

The default `minecraft:entities/mimic_octopus` loot table is absent from the bundled entity loot directory, and the missing-table fallback is empty. No species-specific death drops are configured in this snapshot. Carried equipment is subject to the common equipment-drop rules, so retrieve it instead of relying on a death drop. A qualifying player-credited adult kill can award **1–3 base XP** with `doMobLoot` enabled. [Default loot name][loot-name] · [Bundled loot][loot-data] · [Fallback][loot-fallback] · [Equipment drops][equipment-drop] · [XP amount][xp] · [Adult restriction][xp-baby] · [XP conditions][xp-gate]

## Notes

* Entity ID: `minecraft:mimic_octopus`; spawn egg ID: `minecraft:mimic_octopus_spawn_egg`
* The active class is `net.alexsmobs.entity.EntityMimicOctopus`, registered in `MobCategory.WATER_CREATURE` from bundled Alex's Mobs content [Entity registration][entity] · [Item registration][items]

Related: [Mimic Octopus Spawn Egg][egg] · [Bucket of Mimic Octopus][bucket] · [Mobs][mobs]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716` on 2026-10-03. Active spawn wiring, food tags, interaction order, air and sitting dispatch, combat, world persistence, exact bucket components, category assembly and Dispenser lookup were traced. No in-game taming, breeding, combat, visual, breathing or bucket test was run.

[entity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L903-L910
[attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L115-L117
[attribute-wiring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L197
[bucket-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L214-L239
[air]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L443
[egg]: ../items/MimicOctopusSpawnEgg.md
[browser]: ../mechanics/InventoryBrowser.md
[bucket]: ../items/BucketOfMimicOctopus.md
[items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1912-L1913
[egg-category]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2046
[egg-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L76-L103
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[offspring]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L720-L725
[categories]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[browser-list]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[spawn-rule]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L119-L126
[spawn-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/mimic_octopus_spawns.json
[spawn-roll]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L153-L167
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L52-L178
[worldgen]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/data/worldgen/BiomeDefaultFeatures.java
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L245-L288
[flee]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L896-L990
[fears]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/mimic_octopus_fears.json
[water-goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L800-L817
[environment]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L610-L634
[camo]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L290-L297
[visibility]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/targeting/TargetingConditions.java#L75-L86
[copy-mob]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L1002-L1097
[forms]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L865-L870
[render]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/RenderMimicOctopus.java#L17-L81
[render-overlay]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/RenderMimicOctopus.java#L218-L290
[render-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L212
[tame-heal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L330-L353
[tame-foods]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/mimic_octopus_tameables.json
[form-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L302-L329
[breeding-interact]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L131-L174
[breed-foods]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/mimic_octopus_breedables.json
[breeding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L227
[commands]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L383-L417
[interaction-results]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/InteractionResult.java#L6-L15
[empty-stack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L322-L335
[moist-default]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L736-L750
[moisture]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L517-L529
[healing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L581-L583
[breathers]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[undead]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/undead.json
[air-default]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2660-L2662
[air-rate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L569-L582
[air-threshold]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[damage]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1146-L1195
[old-air]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L788-L798
[slime-moist]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/mimic_octopus_moisturizes.json
[upgrade]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L355-L380
[command-names]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/assets/minecraft/lang/en_us.json#L3985-L3987
[follow]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/ai/TameableAIFollowOwnerWater.java#L40-L102
[sit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L637-L661
[base-sit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L224-L230
[sit-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/SitWhenOrderedToGoal.java#L15-L47
[owner-defense]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/OwnerHurtByTargetGoal.java#L20-L35
[attack-gate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L1110-L1135
[bucket-gate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L355-L360
[leash]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L81-L84
[creeper-items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/mimic_octopus_creeper_items.json
[guardian-items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/mimic_octopus_guardian_items.json
[puffer-items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/mimic_octopus_pufferfish_items.json
[ink]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/mimic_octopus_toggles_mimic.json
[form-time]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L508-L539
[scare]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L1155-L1207
[slime-upgrade]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/mimic_octopus_attack_foods.json
[owner-attack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/target/OwnerHurtTargetGoal.java#L20-L35
[combat]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L1208-L1234
[laser]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L553-L579
[explosion]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L856-L863
[persistence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L727-L734
[world-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L170-L212
[owner-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L53-L79
[age-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AgeableMob.java#L106-L118
[capture]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[common-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L77
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L52
[create]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1750-L1775
[loot-name]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2063-L2066
[loot-data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/entities
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[equipment-drop]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L813-L838
[xp]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Animal.java#L126-L129
[xp-baby]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[xp-gate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1486
[mobs]: Mobs.md
