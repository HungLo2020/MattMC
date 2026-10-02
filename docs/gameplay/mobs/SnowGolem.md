# Snow Golem

A **Snow Golem** (`minecraft:snow_golem`) throws Snowballs at eligible hostile mobs and can leave a trail of Snow layers. It has only **4 health points, or 2 hearts**, so choose its location carefully: water, rain, and certain biomes can harm it.

## Build a Snow Golem

Stack **two full [Snow Blocks](../items/SnowBlock.md)** vertically, then place one [Carved Pumpkin](../items/CarvedPumpkin.md) or Jack o'Lantern on top **last**. From top to bottom, the pattern is:

1. Carved Pumpkin or Jack o'Lantern
2. Snow Block
3. Snow Block

Snow layers and Powder Snow do not substitute for the two full Snow Blocks. The head's facing is not part of the match. A successful pattern consumes both blocks and the head and creates a Snow Golem. This is a source-derived construction pattern, not an in-game-tested build.

The [Snow Block item page](../items/SnowBlock.md) owns its crafting and collection recipe; the existing [Pumpkin guide](../blocks/PumpkinAndMelon.md#carving-a-pumpkin) owns carving. [Snow Golem Spawn Eggs](../items/SnowGolemSpawnEgg.md) provide a Creative route. A registered spawn predicate alone is not evidence of a wild biome population; construction is the confirmed Survival route described here.

## Targets and Snowballs

Its active ranged-attack goal selects eligible mobs implementing the hostile `Enemy` category, with visibility and other target checks. The behavior includes wandering and looking at nearby players; it is not a tamed follower.

The Snowball hit handler requests **3 damage points against a Blaze** and **0 against other entities**. A Snow Golem can distract or provoke enemies, but should not be treated as a general-purpose lethal defender. Its targeting predicate does not contain the Iron Golem's explicit Creeper exclusion.

## Water, rain, and melting biomes

Water sensitivity is active through the shared living-entity tick: **being in water or exposed to rain can damage it**. Keep its space dry and provide a roof where rain occurs.

Melting uses the bundled **`snow_golem_melts` biome tag**, rather than a single temperature cutoff in the golem's code. The checked tag includes Desert, the three Savanna variants, Badlands/Eroded Badlands/Wooded Badlands, and the five Nether biomes. The server attempts heat damage while it occupies a tagged biome.

A roof can protect against rain, but does not remove a biome from that melting tag. Choose an untagged biome and a dry enclosure before adding the final head. Shearing off the pumpkin does not disable either environmental hazard.

## Snow trail

With **`mobGriefing` enabled**, the golem checks several positions beneath its footprint and places the default single Snow layer where the position is air and Snow can survive on the block below. It does not fill arbitrary gaps or stack more layers into a position that already contains Snow.

Snow support uses its own block checks and support tags. The trail code has no separate temperature test; the golem's biome damage is a different path. Disabling `mobGriefing` prevents this trail but does not switch off melting damage.

Placed Snow layers can later melt when their random-tick check sees **block light above 11**. That layer behavior is separate from the golem's melting-biome rule. Do not confuse a Snow trail with full Snow Blocks or assume every deposited layer will remain indefinitely.

## Shearing and preserving it

Use **unbroken Shears** on a living Snow Golem that still has its pumpkin. The action removes the pumpkin flag, drops **one Carved Pumpkin**, and attempts one point of tool wear through the normal durability system. It does not kill the golem or remove its ranged-attack goal.

The same shearing loot is used even if the construction head was a Jack o'Lantern. The pumpkin flag is saved with the golem. The shared golem class also disables ordinary distance-based despawning, though damage and other removal paths still apply.

## Drops

The bundled death table gives **0–15 Snowballs** with normal mob loot enabled. It has no Looting bonus or player-kill requirement. The Carved Pumpkin is a separate **shearing** result, not an additional death-table drop.

## Related pages

- [Iron Golem](IronGolem.md)
- [Snow Block](../items/SnowBlock.md), [Snow](../items/Snow.md), and [Snowball](../items/Snowball.md)
- [Carved Pumpkin](../items/CarvedPumpkin.md) and [Shears](../items/Shears.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Entity/attribute registration, pattern placement, current AI and interaction callbacks, shared water-damage tick, biome tag, Snow support/melting, shearing data, and death loot were checked. No in-game construction, combat, weather, biome, shearing, or Snow-farm test was run. Active data packs, game rules, custom entity data, and later source changes can affect results.

- [Entity registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java)
- [Active attributes](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java)
- [Snow Golem construction pattern](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java)
- [Head block registrations](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Placement callback dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java)
- [AI, Snow trail, shearing, and saved pumpkin state](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/SnowGolem.java)
- [Active mob goal execution](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java)
- [Water/rain damage and mob-loot gate](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java)
- [Current melting-biome list](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/worldgen/biome/snow_golem_melts.json)
- [Snowball damage distinction](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/projectile/Snowball.java)
- [Snow-layer support and block-light melting](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/SnowLayerBlock.java)
- [Shearing loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/shearing/snow_golem.json)
- [Death loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/snow_golem.json)
- [Distance despawning rule](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/AbstractGolem.java)
