# Creeper

The **Creeper** is a hostile mob that attacks by exploding. Keep it away from buildings, and kill it before detonation to collect [Gunpowder](../items/Gunpowder.md). It has **20 health points (10 hearts)** and a registered body size of **0.6 blocks wide × 1.7 blocks tall**. [Behavior][behavior] · [Health default][attributes] · [Attribute inheritance][living] · [Registration][registration]

## Where to find it

Creepers have normal ground-spawn registrations and appear in the bundled Overworld monster tables. For example, **plains**, **forest**, and **desert** each list groups of four. **Deep Dark** and **Mushroom Fields** have no creeper entry in their normal biome spawn tables; this does not prevent an existing creeper from entering them. [Spawn placement][placement] · [Plains][plains] · [Forest][forest] · [Desert][desert] · [Deep Dark][deep-dark] · [Mushroom Fields][mushroom-fields]

Natural spawning requires a difficulty other than Peaceful, suitable ground, and the monster darkness checks. In the bundled Overworld, **block light must be 0**; a separate sky/local-light test also applies. Lighting a spawnable floor therefore helps prevent new creepers, but does not remove ones already present. Spawn-table group sizes are attempted groups, not a guarantee of four visible mobs. [Spawn checks][monster] · [Ground check][mob] · [Overworld light settings][overworld]

For Creative testing, use the [Creeper Spawn Egg](../items/CreeperSpawnEgg.md). With command permission, use `/summon minecraft:creeper`.

## Avoiding an explosion

- **Stay out of close range.** A creeper pursuing a target can begin swelling at less than **3 blocks** away and stops navigating while its swelling goal runs.
- **Retreat decisively or break line of sight.** Once swelling has started, its AI reverses the fuse when the target is **more than 7 blocks away**, is out of sight, or is absent. Merely stepping back outside the initial 3-block trigger is insufficient. Reversing the fuse takes time; it does not instantly erase accumulated swelling.
- **Treat the hiss as urgent.** The default fuse is **30 ticks**, about **1.5 seconds at 20 TPS**. A fall adds fuse progress, so a creeper dropping onto you can give much less warning.
- **Use cats or ocelots as a deterrent.** Creepers have avoidance goals for both within **6 blocks**. Swelling has a higher AI priority, so do not rely on a nearby cat to rescue you from an already primed creeper.

These tactics follow the targeting and fuse code, rather than a timed in-game combat test. The pursuit goal does not deal ordinary melee damage; the explosion is the attack. [Fuse and interactions][behavior] · [Distance and visibility rules][swell]

Unlike [Skeletons](Skeleton.md), creepers have no daylight-burning routine. Do not assume a surviving creeper outside your shelter will disappear at sunrise. [Creeper behavior][behavior] · [Shared monster behavior][monster]

## Charged and manually ignited creepers

A lightning strike makes a creeper **charged**. Its explosion power doubles from **3 to 6**; these are explosion-strength values, not a guaranteed block-destruction radius or a fixed damage amount. Block destruction is governed by `mobGriefing`, while disabling that rule does not disable the explosion itself. [Charging and explosion][behavior] · [Explosion game-rule handling][explosion]

Using [Flint and Steel](../items/FlintAndSteel.md) or a [Fire Charge](../items/FireCharge.md) on a creeper ignites it directly. Flint and steel loses one durability; a fire charge is consumed. **Manual ignition keeps the fuse advancing even if the target escapes or hides.** [Ignition][behavior] · [Igniter tag][igniters]

If a creeper explodes while carrying status effects, it creates a shrinking lingering-effect cloud. Avoid the blast location as well as the initial explosion. [Effect cloud][behavior]

## Drops and special rewards

With mob loot enabled, killing a creeper normally gives **0–2 gunpowder**. Looting increases the possible maximum by one per level, up to **5 with Looting III**. The table has no fire-based replacement. A qualifying player-attributed kill has a base reward of **5 experience**. A creeper that detonates is discarded instead of going through its normal death-loot routine, so kill it first when collecting gunpowder. [Loot table][loot] · [Explosion removal][behavior] · [Base experience][monster] · [Experience conditions][experience]

A kill attributed to an entity in the bundled **skeletons** tag also rolls **one music disc** from the creeper-disc tag. That tag contains **13, cat, blocks, chirp, far, mall, mellohi, stal, strad, ward, 11, and wait**. Positioning a creeper between yourself and a bow-wielding skeleton is a way to attempt this, but the skeleton must get the killing hit. [Disc condition][loot] · [Eligible killers][skeletons] · [Disc choices][discs]

A charged creeper can produce **at most one special head drop** across its kills. The bundled head table supports creepers, ordinary skeletons, wither skeletons, zombies, and piglins. To obtain a [Creeper Head](../items/CreeperHead.md), the victim must be another creeper killed by the charged one. [One-head limit][behavior] · [Eligible victims][heads] · [Creeper head drop][creeper-head]

## Verification scope

Source-reviewed on **2026-10-01** against active MattMC code and bundled data at commit `9bd57e1d0057903f6a9196e592d5e2a087c9248a`. No in-game combat, disc-farming, or explosion test was run for this page. Data packs can change biome tables, tags, and loot; entity data can override the default fuse and explosion power.

Related: [Skeleton](Skeleton.md) · [Cat](Cat.md) · [Ocelot](Ocelot.md) · [Mobs](Mobs.md)

[behavior]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/monster/Creeper.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L57-L59
[living]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L326-L343
[registration]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/EntityType.java#L461-L463
[placement]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L115
[plains]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/plains.json
[forest]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/forest.json
[desert]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/desert.json
[deep-dark]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/deep_dark.json
[mushroom-fields]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/mushroom_fields.json
[monster]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/monster/Monster.java
[mob]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/Mob.java#L728-L740
[overworld]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/overworld.json
[swell]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/goal/SwellGoal.java
[explosion]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerLevel.java#L1148-L1167
[igniters]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/creeper_igniters.json
[loot]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/entities/creeper.json
[experience]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1488
[skeletons]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/entity_type/skeletons.json
[discs]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/creeper_drop_music_discs.json
[heads]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/charged_creeper/root.json
[creeper-head]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/charged_creeper/creeper.json
