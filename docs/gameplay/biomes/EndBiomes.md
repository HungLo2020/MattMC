# End biomes

For **Chorus**, search **End Highlands**. For **End Cities**, search **End Highlands or End Midlands**. The central **The End** biome, **End Barrens**, and **Small End Islands** have different generation lists and are not interchangeable loot destinations. The bundled **Normal** preset uses the End biome source to select these five biomes. [Normal preset][normal] · [Active five-biome source][end-selection] · [City eligibility][city-tag]

Use the [End dimension guide](../dimensions/End.md) for entry, the dragon-fight travel sequence, exit portals, and respawn hazards. The biome guide helps choose an exploration route once you are there.

## Compare the five biomes

| Biome and exact ID | Checked biome-feature entries | End City start eligible? |
| --- | --- | --- |
| [The End](#the-end) — `minecraft:the_end` | End spikes and the fixed End platform | No |
| [End Highlands](#end-highlands) — `minecraft:end_highlands` | Chorus Plant and generated return-gateway attempts | Yes |
| [End Midlands](#end-midlands) — `minecraft:end_midlands` | None in its own feature list | Yes |
| [End Barrens](#end-barrens) — `minecraft:end_barrens` | None in its own feature list | No |
| [Small End Islands](#small-end-islands) — `minecraft:small_end_islands` | Additional End Stone island feature | No |

The exact lists come from [The End][the_end], [Highlands][end_highlands], [Midlands][end_midlands], [Barrens][end_barrens], and [Small Islands][small_end_islands]. An empty feature list does not mean empty terrain: the Normal preset's End noise settings use **End Stone** as the base block. Structures have their own eligibility and generation path. [End terrain settings][end-terrain] · [City definition][city-definition]

Near the origin, the selector uses **The End**. Outside its central section-coordinate test, an island-noise value selects Highlands, Midlands, Barrens, or Small Islands. These names are selection categories, not a measured height, a promise of a continuous walking route, or a city-distance guarantee. Check the actual ground before crossing. [Selection rules][end-selection]

## The End

This is the central biome around the dragon arena. Its feature list includes the End-spike generator and a fixed platform placement origin at **(100, 49, 0)**. That platform entry is tied to a fixed location; it is not an island or a new arrival platform generated in every patch of this biome. [Biome entries][the_end] · [Spike placement][placed-end_spike] · [Spike configuration][config-end_spike] · [Fixed platform placement][placed-end_platform] · [Platform configuration][config-end_platform] · [Spike dispatch][spike-checks] · [Platform dispatch][platform-checks]

If your goal is Chorus, city chests, or an Elytra-bearing ship, repeating a circuit of the central biome will not satisfy the checked generation route. Consult [Ender Dragon](../mobs/EnderDragon.md) for the fight and [End Gateways](../blocks/EndPortals.md#end-gateway) for travel to other End locations. Entry-platform replacement and return travel remain documented in the [dimension guide](../dimensions/End.md).

## End Highlands

**Highlands is the checked natural Chorus route among these five biomes.** Its placed feature chooses 0–4 attempted surface positions, then applies a biome filter. The live plant generator requires empty space directly above End Stone. Consequently, reaching Highlands does not guarantee a plant at every stop. [Highlands list][end_highlands] · [Chorus placement][placed-chorus_plant] · [Chorus configuration][config-chorus_plant] · [Ground and space checks][chorus-checks]

Bring a way to recover the parts you need before breaking a stand. [Chorus](../blocks/Chorus.md) distinguishes stems, flowers, fruit, and replanting; [End Stone and Purpur](../blocks/EndStoneAndPurpur.md) owns the building-material route. A plant introduced by a player or extending across a boundary does not add a Chorus feature to another biome's list.

Highlands also includes a **generated return-gateway** placement with a rarity-filter setting of **700**. This is a placement setting, not one guaranteed gateway per 700 chunks or a gateway beside every city. Its configured exact exit is **(100, 50, 0)**; the [gateway guide](../blocks/EndPortals.md#end-gateway) explains the travel behavior. Keep a return route that does not depend on finding one. [Placement][placed-end_gateway_return] · [Return configuration][config-end_gateway_return] · [Gateway and exit creation][gateway-checks]

## End Midlands

**Midlands is a valid End City search biome even though its own feature list is empty.** City generation is connected through the structure definition and its Highlands/Midlands tag. That distinction matters when you see bare End Stone: the absence of a Chorus feature does not by itself disqualify the area from a city start. [Midlands data][end_midlands] · [City definition][city-definition] · [City tag][city-tag]

Continue looking for large, accessible outer-island terrain and inspect crossings before committing supplies. A candidate city still faces placement and terrain checks, including rejection of a sampled base below Y=60. This does not establish a fixed elevation for all Midlands or certify a safe approach. [City placement][city-set] · [Generation checks][city-checks] The [End City guide](../structures/EndCity.md) owns towers, ships, loot, and Shulker hazards.

## End Barrens

Barrens has **no entries in its own feature list** and is absent from the city start tag. It can still contain End Stone terrain and Enderman candidates. Treat it as terrain to cross or work around rather than a source-defined destination for new Chorus or a city start. [Barrens data][end_barrens] · [End terrain][end-terrain] · [City tag][city-tag]

The selector chooses Barrens for one outer island-noise band; the name does not guarantee a flat shore, a safe ledge, or a particular distance from another biome. Stop at gaps, check the landing area, and reserve blocks for the return crossing. Structure pieces, feature shapes from neighboring origins, and player changes are not confined by this page's start/feature tables. [Biome selection][end-selection]

## Small End Islands

This biome lists an **additional End Stone island feature**. Its placement has a rarity-filter setting of **14**, selects one or two attempts through a weighted count, and chooses starting Y between **55 and 70** before the biome check. The feature then writes a tapering End Stone shape downward from its starting point. Those values describe attempts and origin heights, not a guaranteed platform beneath your next step or the complete height range of all End terrain. [Biome entry][small_end_islands] · [Placement][placed-end_island_decorated] · [Configuration][config-end_island] · [Island generator][island-checks]

It has no own Chorus or return-gateway entry and is excluded from the city start tag. For a Chorus or city expedition, continue toward the eligible Highlands/Midlands route while preserving enough supplies to cross back. Do not assume that a sequence of small islands forms an uninterrupted bridge. [Feature list][small_end_islands] · [City tag][city-tag]

## Mobs and expedition limits

All five checked biome lists have the same sole ordinary monster entry: **Enderman**, weight **10**, with a listed group size of **4**. Their other spawn categories are empty. Weight is a selection input, and a group entry is not a guarantee of four successful spawns; placement, light, difficulty, population, and collision checks still apply. Read [Enderman](../mobs/Enderman.md) before choosing where to look or stop. [The End][the_end] · [Highlands][end_highlands] · [Midlands][end_midlands] · [Barrens][end_barrens] · [Small Islands][small_end_islands] · [Spawn checks][spawn-checks]

**Shulkers are not recurring entries in these biome spawn lists.** Their checked city route is structure placement; see [Shulker](../mobs/Shulker.md). Likewise, the [Ender Dragon](../mobs/EnderDragon.md) fight has its own controller, rather than a dragon entry in the central biome's ordinary monster list.

End City eligibility never guarantees a city or a ship. Keep food, spare blocks, and a return plan that works before finding [Elytra](../items/Elytra.md). See [End hazards](../dimensions/End.md#hazards) before crossing the void. The [inventory item browser](../mechanics/InventoryBrowser.md) is a separate acquisition route; its entries do not prove natural End generation.

For imported airborne creatures, see [Spectre](../mobs/Spectre.md), [Cosmaw](../mobs/Cosmaw.md), and [Cosmic Cod](../mobs/CosmicCod.md). Their guides distinguish listed eggs from the absent bundled natural-spawn route; the names do not add those mobs to the End biome lists above.

## Sources and verification

Source-reviewed on **2026-10-02** at `79f20bccc697135bd56a472c64f59981dce47fe0`. Checked Normal-preset selection, all five biome feature/spawn resources and loading paths, connected selected feature placements and generators, End terrain settings, and city eligibility. No in-game island survey, Chorus harvest, gateway search, city search, or spawn-rate test was run. Seeds, data packs, presets, and previously generated terrain can change results.

Biome resources are loaded into the world-generation registries, and the active decoration loop executes placed/configured features with biome and placement checks. A listed feature does not guarantee a successful placement. [World loading][world-loader] · [Registry codecs][registry-loader] · [JSON loading][resource-load] · [Biome fields][biome-codec] · [Decoration caller][decoration] · [Placed dispatch][placed] · [Configured dispatch][configured] · [Registered generators][feature-registry] · [Biome filter][biome-filter]

Related: [Biomes](Biomes.md) · [End](../dimensions/End.md) · [End City](../structures/EndCity.md) · [Nether biomes](NetherBiomes.md)

[normal]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[end-selection]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/biome/TheEndBiomeSource.java#L14-L78
[city-tag]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/end_city.json
[the_end]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/biome/the_end.json
[end_highlands]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/biome/end_highlands.json
[end_midlands]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/biome/end_midlands.json
[end_barrens]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/biome/end_barrens.json
[small_end_islands]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/biome/small_end_islands.json
[end-terrain]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/noise_settings/end.json
[city-definition]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/structure/end_city.json
[placed-end_spike]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/end_spike.json
[config-end_spike]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/configured_feature/end_spike.json
[placed-end_platform]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/end_platform.json
[config-end_platform]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/configured_feature/end_platform.json
[spike-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/SpikeFeature.java#L42-L65
[platform-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/EndPlatformFeature.java#L15-L39
[placed-chorus_plant]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/chorus_plant.json
[config-chorus_plant]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/configured_feature/chorus_plant.json
[chorus-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/ChorusPlantFeature.java#L18-L28
[placed-end_gateway_return]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/end_gateway_return.json
[config-end_gateway_return]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/configured_feature/end_gateway_return.json
[gateway-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/EndGatewayFeature.java#L16-L44
[city-set]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/structure_set/end_cities.json
[city-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/structure/structures/EndCityStructure.java
[placed-end_island_decorated]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/placed_feature/end_island_decorated.json
[config-end_island]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/configured_feature/end_island.json
[island-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/EndIslandFeature.java#L19-L37
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L245-L265
[world-loader]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/WorldLoader.java#L36-L44
[registry-loader]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[resource-load]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L277-L326
[biome-codec]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/biome/Biome.java#L38-L46
[decoration]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L308-L380
[placed]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L39-L60
[configured]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L17-L26
[feature-registry]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L98-L136
[biome-filter]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/placement/BiomeFilter.java#L23-L28
