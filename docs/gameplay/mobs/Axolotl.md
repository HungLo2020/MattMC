# Axolotl

**Axolotls** are aquatic companions that hunt several water mobs and can reward a player who finishes their opponent. Carry one safely with a [Bucket of Axolotl](../items/BucketOfAxolotl.md), and use [Buckets of Tropical Fish](../items/BucketOfTropicalFish.md) to lead and breed them. Their entity ID is `minecraft:axolotl`.

## Where to find them

Look in **Lush Caves in the Overworld**, in water immediately above **clay**. Lush Caves has a natural axolotl spawn entry, and the registered placement rules require water at the spawn position and clay directly below it. The block above must not be a redstone-conducting solid block, and ordinary collision, mob-cap, and player-distance checks still apply.

The bundled axolotl-specific rules add **no darkness requirement**. Do not substitute a generic underground pool for the Lush Caves biome and clay requirements. For Creative placement, use the [Axolotl Spawn Egg](../items/AxolotlSpawnEgg.md).

## Moving and keeping axolotls

- Use a **Water Bucket** on a living axolotl to capture it, including one stranded on land. An empty bucket does not capture it. The [bucket guide](../items/BucketOfAxolotl.md) covers release and preserved data.
- Hold a **Bucket of Tropical Fish** to attract axolotls, or use a [Lead](../items/Lead.md). Loose tropical-fish items are not their food.
- Provide water in their enclosure. Water or rain restores their moisture reserve; away from both, their 6,000-tick reserve lasts about **five minutes** at 20 ticks per second before drying damage begins shortly afterward. Drying then deals 2 health points per damage event.
- A thrown plain-water potion restores up to 1,800 ticks, or **90 seconds**, of that reserve without exceeding its maximum. This helps a stranded axolotl but does not replace a lasting water supply.
- An axolotl released from a bucket is protected from ordinary distance despawning. Breeding also marks the offspring persistent. Keep that distinction in mind when moving wild axolotls.

## Breeding and colors

Feed a Bucket of Tropical Fish to each of two adults that are ready to breed. In Survival, each feeding returns a **Water Bucket**. The bundled food tag contains only `minecraft:tropical_fish_bucket`; raw fish, cooked fish, and other fish buckets are not alternatives.

The parents have a **6,000-tick breeding cooldown** (five minutes). A newborn starts with 24,000 ticks of growth time (20 minutes), and feeding it the same food shortens its remaining growth time. Successful breeding can produce 1–7 experience when mob loot is enabled.

The common spawn variants are **Lucy, wild, gold, and cyan**. Blue is excluded from the common natural-spawn selection. Each bred offspring has a **1 in 1,200** chance to use the rare blue variant; otherwise it inherits one parent's variant, chosen equally. A blue parent can therefore pass its color on through ordinary inheritance as well as the rare roll.

## Hunting and player support

Axolotls do not normally target players. Their hunting sensor chooses visible, attackable targets in water within 8 blocks:

- **Hostile targets:** Drowned, Guardians, and Elder Guardians
- **Other prey:** Cod, Salmon, Pufferfish, Tropical Fish, Squid, Glow Squid, and Tadpoles

After leaving a fight, an axolotl receives a **2,400-tick hunting cooldown** (two minutes). That blocks the ordinary prey list while it lasts; the hostile-target list remains eligible. Keep pet fish and tadpoles in separate enclosures. Breeding activity suppresses hunting.

To receive help from an axolotl, **kill the target it was attacking and stay nearby**. When it stops attacking that dead target, the code checks that the target's last damage source credits the player and that the player is within the axolotl's bounding box expanded by 20 blocks. The support effect adds five seconds of **Regeneration I**, up to two minutes total, and removes **Mining Fatigue**. Simply standing near an axolotl, or letting it make the final kill, does not meet that player-credit check.

An injured axolotl can **play dead underwater** for up to 200 ticks (10 seconds), stop moving, and gain Regeneration I. This is a chance-based response to a nonfatal entity-caused hit, with additional damage/health conditions; it is not guaranteed on every hit. Playing dead makes it ineligible as an enemy target, but does not make it invulnerable.

## Health and drops

- **Health:** 14 points, or 7 hearts
- **Base attack damage:** 2 points, or 1 heart, before applicable combat modifiers
- **Death items:** the bundled axolotl loot table has no item pools; Looting does not add a special axolotl drop
- **Death experience:** eligible adult player-credit kills yield 1–3 experience with mob loot enabled; babies do not yield death experience

## Related pages

- [Bucket of Axolotl](../items/BucketOfAxolotl.md)
- [Bucket of Tropical Fish](../items/BucketOfTropicalFish.md)
- [Tropical Fish](TropicalFish.md)
- [Drowned](Drowned.md), [Guardian](Guardian.md), and [Elder Guardian](ElderGuardian.md)
- [Regeneration](../effects/Regeneration.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-01** at commit `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. This is a review of active source and bundled data; no in-game spawning, breeding, bucket round trip, or combat test was run. Data packs can change the referenced tags and loot tables. Tick-to-time conversions assume 20 ticks per second.

- [Registration and attributes](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/EntityType.java#L286-L288); [attribute wiring](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L118)
- [Lush Caves spawn data](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/lush_caves.json); [Overworld cave-biome selection](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L817-L823)
- [Spawn registration](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L87); [water placement](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java); [spawnable floor tag](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/block/axolotls_spawnable_on.json)
- [Axolotl interactions, survival, colors, support, and stats](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/axolotl/Axolotl.java); [water-potion rehydration caller](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/projectile/AbstractThrownPotion.java#L87-L106)
- [Breeding and hunting AI](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/axolotl/AxolotlAi.java); [target sensor](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/sensing/AxolotlAttackablesSensor.java); [hostile targets](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/entity_type/axolotl_always_hostiles.json); [prey targets](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/entity_type/axolotl_hunt_targets.json)
- [Food tag](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/axolotl_food.json); [animal feeding and breeding](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/Animal.java); [growth timing](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/AgeableMob.java)
- [Attack-end callback execution](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/behavior/StopAttackingIfTargetInvalid.java); [playing-dead behavior](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/axolotl/PlayDead.java)
- [Death loot](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/entities/axolotl.json); [death-loot and experience eligibility](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java)
