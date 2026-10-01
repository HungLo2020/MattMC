# Dolphin

**Dolphins** can help swimmers move faster and guide a player toward certain ocean structures after feeding. They need both water and access to air, and adults can retaliate when attacked. Their entity ID is `minecraft:dolphin`.

## Where to find them

The bundled Overworld spawn tables include dolphins in **Ocean, Deep Ocean, Lukewarm Ocean, Deep Lukewarm Ocean, and Warm Ocean**. Cold and frozen ocean variants do not have those dolphin entries.

Their registered natural-spawn check uses water from **13 blocks below sea level through sea level**, inclusive. That is **Y=50–63** with the bundled Overworld sea level of 63. It requires water at and below the spawn position, a water block above, and an unobstructed spawn position; ordinary mob-cap and player-distance checks also apply. There is no separate light check in this dolphin spawn predicate.

Primordial Ocean's standalone biome data also contains a dolphin entry, but the reviewed default [Primordial Caves](../dimensions/PrimordialCaves.md) preset and server fallback do not select that biome. That entry alone is not a confirmed extra acquisition route. For Creative placement, use the [Dolphin Spawn Egg](../items/DolphinSpawnEgg.md).

## Feeding and finding structures

Use any item in the bundled fish tag on a dolphin:

- Raw Cod or Cooked Cod
- Raw Salmon or Cooked Salmon
- Tropical Fish or Pufferfish

These are loose fish items, not fish buckets. In Survival, feeding consumes one item. **An adult becomes ready to guide you toward a structure; feeding a baby instead accelerates its growth.** There is no food-driven breeding or taming behavior in the registered dolphin goals. The presence of an offspring-creation method does not provide a player breeding interaction.

After feeding an adult, follow it through the water. Its target tag contains **shipwrecks and ocean ruins**, including beached shipwrecks. The bundled tag does **not** include buried treasure.

Treat the dolphin as a direction guide, with these limits:

- It looks for a structure location, not a chest. It does not inspect chest contents or promise unopened loot.
- Previously located structures are not excluded. Feeding again can lead toward the same place.
- Structure generation must be enabled, and a matching structure must be found. A blocked swimming route can end the attempt.
- The dolphin stops the trip when it reaches within roughly four blocks of the target's horizontal location. It does not need to reach a chest or match the target's height.
- Low air interrupts guidance so the dolphin can breathe. Keep the route and surface accessible.

## Swimming with dolphins

A dolphin can choose a swimming player within 10 blocks and give **Dolphin's Grace** for 100 ticks, or five seconds at 20 ticks per second. It periodically refreshes the effect while swimming alongside that player; the continuing follow check allows a distance below 16 blocks. The effect reduces horizontal water slowdown, helping the player swim faster. Feeding is not required for this swimming assistance.

Dolphins also follow boats and play with dropped items in water. They can pick up and throw items, so do not treat an item floating beside a dolphin as safely stored.

## Keeping dolphins alive

Give a dolphin a water enclosure with a usable route to air. Its full air supply is **4,800 ticks** (four minutes); it actively seeks air when its supply gets low. Keeping it submerged without breathing access can drown it.

Outside water and rain, its **2,400-tick moisture reserve** runs down in about two minutes, after which drying damage starts. Rain restores moisture, but a dry enclosure still leaves the dolphin flopping on land. Dolphins can be attached to a [Lead](../items/Lead.md).

Adults can retaliate and alert other dolphins when attacked. Their avoidance behavior targets Guardians, and the retaliation goal excludes Guardian attackers. Babies cannot attack. Feeding a dolphin does not give it an owner or make it a permanent pet; a [Name Tag](../items/NameTag.md) can provide ordinary named-mob persistence.

## Health and drops

- **Health:** 10 points, or 5 hearts
- **Base adult attack damage:** 3 points, or 1½ hearts, before applicable combat modifiers
- **Adult death items:** 0–1 Raw Cod. Looting can add up to its level in extra cod, so the maximum with Looting III is 4
- **Cooked drops:** the cod is furnace-smelted when the dolphin is burning or the direct attacker's main-hand item has an enchantment in `minecraft:smelts_loot`; the bundled tag contains Fire Aspect
- **Death experience:** eligible adult player-credit kills yield 1–3 experience with mob loot enabled

Babies do not produce the normal death-loot-table drops or death experience. The listed item range concerns the dolphin's loot table; an item it picked up is separate equipment.

## Related pages

- [Cod](Cod.md), [Salmon](Salmon.md), and [Tropical Fish](TropicalFish.md)
- [Guardian](Guardian.md)
- [Fishing](../mechanics/Fishing.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-01** at commit `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game spawn, feeding, treasure-guidance, or enclosure test was run. The findings describe the registered source paths and bundled data; world presets and data packs can change availability, tags, and loot. Tick-to-time conversions assume 20 ticks per second.

- [Dolphin registration](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/EntityType.java#L480-L482); [attribute wiring](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L152)
- [Ocean biome spawn builders](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/data/worldgen/biome/OverworldBiomes.java#L383-L444); [warm-ocean spawn helper](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/data/worldgen/BiomeDefaultFeatures.java#L457-L462); [biome registration](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/data/worldgen/biome/BiomeData.java#L57-L65)
- [Spawn predicate registration](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L90-L95); [surface-water spawn predicate](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/AgeableWaterCreature.java); [Overworld sea level](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L392)
- [Dolphin interactions, goals, health, breathing, and moisture](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/Dolphin.java); [accepted fish tag](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/fishes.json); [Dolphin's Grace movement and death eligibility](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java)
- [Treasure target tag](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/structure/dolphin_located.json); [shipwreck members](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/structure/shipwreck.json); [ocean-ruin members](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/structure/ocean_ruin.json); [structure lookup entry point](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/level/ServerLevel.java#L1329-L1342); [structure-reference handling](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L272-L304)
- [Retaliation and group alerts](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/goal/target/HurtByTargetGoal.java); [air-seeking goal](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/goal/BreathAirGoal.java); [name-tag persistence](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/NameTagItem.java)
- [Dolphin death loot](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/entities/dolphin.json); [Looting count calculation](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java); [smelting enchantment tag](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json)
- [Primordial Ocean spawn data](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/primordial_ocean.json); [Normal preset's actual biome selection](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/world_preset/normal.json); [fallback biome preset](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L81-L93)
