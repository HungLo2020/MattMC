# Dimensions

MattMC's bundled Normal world preset defines the Overworld, Nether, End, and Primordial Caves. Choose your destination and check its respawn rules before travelling; a familiar-looking bed or anchor can explode in the wrong dimension.

## Travel and respawn at a glance

| Destination | Entry route covered here | Beds | Charged respawn anchors |
| --- | --- | --- | --- |
| Overworld (`minecraft:overworld`) | The ordinary starting world; return routes from other dimensions | Supported | Explode when used to set spawn |
| [Nether](Nether.md) (`minecraft:the_nether`) | Lit obsidian portal | Explode when used | Supported |
| [End](End.md) (`minecraft:the_end`) | Activated End portal | Explode when used | Explode when used to set spawn |
| [Primordial Caves](PrimordialCaves.md) (`minecraft:primordial_caves`) | A Nether portal converted with a dropped Pitcher Pod | Supported | Explode when used to set spawn |

“Supported” means the bundled dimension type permits the interaction. [Beds](../blocks/Bed.md) still have sleeping and safe-exit checks; Nether anchors need fuel and usable space around them. Charging an anchor is separate from trying to set spawn with it.

## Primordial Caves in MattMC

The fourth destination has active preset, server-registration, and portal code. Drop a **single Pitcher Pod** into a complete Nether portal to convert it; the code discards the entire dropped stack. Its return portal targets the Overworld.

See [Primordial Caves](PrimordialCaves.md) for the full route, source-defined environment, preset/fallback differences, and unresolved natural-content availability. The [Pitcher Pod page](../items/PitcherPod.md) documents verified digging/crop sources without promising a complete first-Sniffer progression route.

## Reading these guides

- [Nether](Nether.md): build and link portals, set an anchor respawn, and avoid dimension-specific hazards
- [End](End.md): activate a stronghold portal, prepare for arrival, and understand the exit route
- [Primordial Caves](PrimordialCaves.md): converted portals, return route, and current content limits
- [Biomes](../biomes/Biomes.md): biome category
- [Gameplay](../Gameplay.md): return to the gameplay index

## Sources and verification

Reviewed at source `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. This is an active-source review, not an in-game dimension, terrain-generation, or portal test. World settings and data packs can alter the bundled definitions.

- [Bundled Normal preset and its four dimension entries](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/world_preset/normal.json)
- [Java preset helper's Overworld, Nether, and End wiring](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/levelgen/presets/WorldPresets.java#L89-L117) and [server world creation and Primordial Caves fallback](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/MinecraftServer.java#L406-L563)
- [Overworld rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/overworld.json), [Nether rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/the_nether.json), [End rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/the_end.json), and [Primordial Caves rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/primordial_caves.json)
- [Pod conversion and discarded item stack](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L110-L172) and [Primordial Caves portal destination and exit selection](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/PrimordialCavesPortalBlock.java#L126-L186)
- [Fallback biome preset](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L81-L93)
- [Bed safety checks](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/BedBlock.java#L80-L122) and [anchor interaction and explosion](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/RespawnAnchorBlock.java#L75-L170)
