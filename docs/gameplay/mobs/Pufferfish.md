# Pufferfish

**Pufferfish** inflate when a nearby creature frightens them, then hurt and poison creatures that touch them. Keep some space while collecting one with a Water Bucket. The living mob is distinct from the loose [Pufferfish item](../items/Pufferfish.md) used in brewing and Nautilus taming. Its ID is `minecraft:pufferfish`. [Inflation][puff-goal] · [Contact][puff-contact] · [Bucket][Pufferfish] · [Entity registration][entity-registration]

## Obtaining

Look in **Warm Ocean, Lukewarm Ocean and Deep Lukewarm Ocean**. Their loaded water-ambient tables request groups of **1–3**. A group entry does not mean that Pufferfish follow a school leader. [Warm][biome-warm_ocean] · [Lukewarm][biome-lukewarm_ocean] · [Deep Lukewarm][biome-deep_lukewarm_ocean]

The registered natural-spawn checks require water at the spawn position and below it, a Water block above, and a height from **sea level minus 13 through sea level**, inclusive. With the bundled Overworld sea level of 63, that is **Y 50–63**. The usual population, distance and collision checks still apply. [Placement registration][placements] · [Water placement][in-water] · [Surface rule][water] · [Sea level][sea-level] · [Natural checks][natural-check]

The [Pufferfish Spawn Egg](../items/PufferfishSpawnEgg.md) is an ordinary listed item. The [inventory item browser](../mechanics/InventoryBrowser.md) can supply it in Creative; placing an egg is a separate route from natural spawning. [Listing][egg-list-puffer] · [Egg use][egg-use] · [Browser list][browser-list] · [Client request][browser-client] · [Server handling][browser-server]

## Behavior

### Inflation and safe distance

A frightening living entity inside the Pufferfish's bounding box expanded by **2 blocks** starts inflation. The scan ignores invisibility and line of sight; a thin wall does not reliably stop that proximity check. Creative players and entities in the not-scary tag are excluded from that trigger. The bundled exemptions include the other ordinary fish, both squids, Dolphins, Turtles, Guardians and Tadpoles. [Target filter][puff-trigger] · [Trigger area][puff-goal] · [Exemptions][puff-exempt]

It first becomes partly inflated, then fully inflated after the inflation counter passes **40 ticks**, roughly two seconds. Once the threat is gone, full inflation steps down after the deflation timer passes **60 ticks**, and it returns to its small state after the timer passes **100 ticks**, roughly five seconds overall. These are timer thresholds, not instant reactions at an exact player distance. [State changes][puff-timing]

| State | Base contact damage before applicable damage modifiers | Poison I duration |
| --- | ---: | ---: |
| Small | No sting from these contact paths | None |
| Partly inflated | 2 health points, or 1 heart | 60 ticks / 3 seconds |
| Fully inflated | 3 health points, or 1½ hearts | 120 ticks / 6 seconds |

Player collision uses the player-touch path. Other eligible mobs are checked within the inflated bounding box expanded by **0.3 blocks**; they must pass the same not-scary targeting filter. Poison is applied only when the contact damage is accepted. The fish does not need to chase or be struck first to sting. [Contact handlers][puff-contact]

### Keeping one

Use a Water Bucket on a living fish to obtain a [Bucket of Pufferfish](../items/BucketOfPufferfish.md). The capture path has no inflated-state prohibition. It saves health and custom name, but not the inflation timer or puff state; a newly released fish starts small and can inflate again if a threat is nearby. Release also protects it from ordinary distance despawning. [Capture data][bucket-capture] · [Default state and bucket][Pufferfish] · [Release][bucket-release] · [Persistence][fish]

Pufferfish inherit the fish avoidance, panic and swimming goals, but have no school-following or breeding interaction. Keep it submerged: it breathes underwater, flops when stranded on solid ground, and loses air while out of water. From a full 300-tick reserve, drying deals its first **2 health points after about 16 seconds**, then another 2 points each second; entering water refills that reserve. [Breathing tag][breathing] · [Flopping][fish] · [Active air tick][water] · [Damage threshold][air-threshold] [Goals][puff-timing]

## Drops

With mob loot enabled, death supplies **one Pufferfish item** and a separate **5% chance of one Bone Meal**. Looting does not increase these entries, and the table has no cooked-fish conversion. An eligible player-credit kill gives **1–3 experience**. [Loot][loot-pufferfish] · [Experience amount][water] · [Death gates][death-loot]

## Notes

- **Health:** 3 points, or 1½ hearts. [Attribute wiring][attributes-puffer] · [Value][fish]
- A living sting applies **Poison I**; eating the loose item has different, stronger effects. See [Pufferfish food and brewing](../items/Pufferfish.md)
- Use the separate guides for [Poison treatment](../effects/Poison.md) and [Nautilus taming](Nautilus.md#taming-and-feeding)
- Natural candidates are read from loaded biome lists. [World loading][world-load] · [JSON loading][registry-load] · [Active chooser][biome-choice]

Related: [Tropical Fish](TropicalFish.md) · [Ocean biomes](../biomes/Oceans.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `eafe6b61edf55cefa8036ec540a3029da1143637`. Checked the loaded biome candidates and active spawn placement, behavior inheritance, bucket capture/release, attributes and death loot. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed biome entries, tags, recipes and loot.

[puff-goal]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Pufferfish.java#L186-L214
[puff-contact]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Pufferfish.java#L123-L152
[Pufferfish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Pufferfish.java
[biome-warm_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/warm_ocean.json
[biome-lukewarm_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/lukewarm_ocean.json
[biome-deep_lukewarm_ocean]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/biome/deep_lukewarm_ocean.json
[placements]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L88-L105
[in-water]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java#L12-L20
[water]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L16-L79
[sea-level]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L392-L392
[natural-check]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L247-L287
[egg-list-puffer]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2065-L2065
[egg-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L127
[browser-list]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L87-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[puff-trigger]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Pufferfish.java#L30-L40
[puff-exempt]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/entity_type/not_scary_for_pufferfish.json
[puff-timing]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Pufferfish.java#L88-L120
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L93
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L50
[fish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/AbstractFish.java#L34-L142
[breathing]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[air-threshold]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[loot-pufferfish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/resources/data/minecraft/loot_table/entities/pufferfish.json
[death-loot]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[attributes-puffer]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L216-L216
[world-load]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/WorldLoader.java#L38-L51
[registry-load]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L277-L334
[biome-choice]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450

[entity-registration]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/EntityType.java#L1088-L1090
