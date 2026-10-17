# Igloo

An **Igloo** is a small snowy shelter with a possible underground curing room. An intact basement offers a [Zombie Villager](../mobs/ZombieVillager.md), a separate [Villager](../mobs/Villager.md), and the supplies for one cure. **Only half of igloos receive a basement when their pieces are chosen**; finding the surface building does not guarantee that opportunity. [Basement selection][pieces] · [Basement template][bottom]

## Where to search

Search the bundled **normal Overworld** in **[Snowy Plains and Snowy Taiga](../biomes/TaigaAndSnowyBiomes.md)** or **[Snowy Slopes](../biomes/MountainsAndWindsweptBiomes.md#snowy-slopes)**. Those are the three entries in the igloo start-biome tag. Ice Spikes, ordinary Taiga and Frozen Peaks are not entries in that tag. This is start eligibility, not a promise that every snowy patch contains a building. [Definition][definition] · [Eligible biomes][biomes] · [Normal preset][normal] · [Overworld parameter set][parameters] · [Parameter mapping][parameter-map] · [Biome selection][biome-map]

New starts require **structure generation enabled**, a placement candidate and a valid start biome. The bundled set uses **32-chunk spacing and 8-chunk separation**; those settings do not mean an igloo appears every 32 chunks or establish a guaranteed walking distance. The surface piece is positioned against the terrain, so there is no single world Y level to search. Custom presets, data packs, saved template overrides and already-generated terrain can differ. [Generation option][generation-option] · [Set filtering][set-filter] · [Placement set][placement] · [Candidate calculation][random-spread] · [Start checks][starts] · [Biome validation][start-biome] · [Terrain placement][pieces] · [Template precedence][template-load]

With permission level 2, use `/locate structure minecraft:igloo` **in the Overworld**. The result is a structure destination, not confirmation of a basement, surviving residents or untouched supplies. Its suggested teleport preserves your current height; follow the [arrival-height cautions](Structures.md#finding-a-structure) before using it. [Locate permission and search][locate] · [Coordinate output][locate-output]

## Prepare and inspect the shelter

Bring food, lighting, a pickaxe, spare blocks and a way to mark your return route. If rescuing a resident is the goal, carrying a spare ordinary Golden Apple and Splash Potion of Weakness avoids relying entirely on an unvisited basement. Reserve those supplies until you have secured the room.

**Prepare for Powder Snow on Snowy Slopes.** Leather Boots provide surface walking when you approach from above without descending; Powder Snow can otherwise let you sink and freeze. Read the [Snow guide](../blocks/Snow.md#powder-snow-buckets-and-collision) for movement, freezing protection and escape details. Snow cover is not proof of solid footing. [Slopes surface rule][snow-surface] · [Powder Snow collision][powder] · [Freezing damage][freezing]

Look for a low **Snow Block shelter with Ice windows**. The bundled surface template has carpet, a red Bed, a Crafting Table and an empty Furnace. These are identification and staging clues, not a guarantee that a previously visited site still has its furnishings. [Surface template][top]

Lift the carpet to inspect the floor. The template places a trapdoor beneath a white carpet tile, but the generator can replace that floor opening with Snow Block when the block below is neither air nor a ladder. A surface-only igloo is a normal result: if there is no shaft, do not assume a curing room must exist farther down. When the ladder is present, use it and preserve your way back up; the selected shaft length varies. [Surface template][top] · [Shaft template][middle] · [Basement roll, shaft assembly and floor closure][pieces]

The basement is a small stone room with barred resident spaces, a Chest, a Brewing Stand and a water-filled Cauldron. **Some of its masonry is infested.** Breaking those blocks through the ordinary drop path can release [Silverfish](../mobs/Silverfish.md#infested-blocks), subject to block-drop and tool-enchantment checks. Leave the walls intact while collecting supplies and curing; widening the room can add a fight beside vulnerable residents. [Basement template][bottom] · [Registered infested masonry][infested-register] · [Mining dispatch][block-drops] · [Infestation conditions][infested]

## What the basement supplies

These are the bundled contents and loot rules for a successfully placed, intact basement. The supplied potion and generated loot do not automatically refill on a return visit.

| Source | What to expect | Limit that matters |
| --- | --- | --- |
| Brewing Stand | **One Splash Potion of Weakness**, already prepared | Saved fuel is **zero** and the other item slots are empty; taking this bottle needs no brewing fuel |
| Basement Chest | **One ordinary Golden Apple** from a separate, fixed one-roll pool | Guaranteed by this chest table, not by every surface igloo or a chest somebody has already looted |
| Same Chest | **2–8 random rolls** among Apples, Coal, Gold Nuggets, a Stone Axe, Rotten Flesh, an Emerald and Wheat | No particular entry in this pool is guaranteed; repeated entries can combine |
| Barred spaces | **One Villager and one Zombie Villager** stored in the template | Preserve them; they are placed residents, not an igloo-specific renewable spawn supply |

[Saved supplies and residents][bottom] · [Chest marker assignment][pieces] · [Chest table][loot] · [One-time chest generation][chest-fill] · [Stand inventory and fuel loading][stand-load]

The random Chest pool gives 1–3 Apples, 1–4 Coal, 1–3 Gold Nuggets, or 2–3 Wheat when those entries are selected; the Stone Axe, Rotten Flesh and Emerald entries give one each. These are **per-selection amounts**, not total-stack or per-chest guarantees. The pool's relative weights also differ, so the list is not an equal-chance menu. [Complete chest pool][loot]

The supplied bottle and Apple let you attempt the cure without brewing another potion first. If either is missing or wasted, use the canonical [Brewing guide](../brewing/Brewing.md): **Water Bottle + Fermented Spider Eye → Weakness**, then **Gunpowder → splash form**, with **Blaze Powder fuel** in the stand. Nether Wart is not part of that Weakness recipe. The checked basement and Chest table do not supply that complete replacement-brewing kit. For a replacement Apple, use the [Golden Apple recipe](../items/GoldenApple.md#obtaining); the Chest's Gold Nuggets are not the recipe's Gold Ingots. [Server brewing initialization][brew-init] · [Bottle conversion][brew-container] · [Weakness recipe][brew-weakness] · [Fuel gate][stand-fuel] · [Fuel tag][fuel] · [Golden Apple recipe][apple-recipe]

## Secure the room and cure its resident

The saved residents are placed by the active template path, which keeps entity placement enabled and loads their saved data. Both carry the persistence flag, so ordinary distance despawning is not the expected loss route for these basement residents. **Peaceful still removes the Zombie Villager, including during curing.** Its persistence does not protect it from damage, sunlight or that difficulty rule. [Placement settings][settings] · [Entity placement][entity-place] · [Entity loading][entity-load] · [Saved persistence][bottom] · [Persistence loading][persistence-load] · [Despawn order][despawn] · [Peaceful exclusion][zv-register]

1. **Keep the two residents separated.** Check the bars and any holes before changing the room; the Zombie Villager retains Zombie attacks against players and Villagers
2. **Take the prepared bottle and Golden Apple.** Aim the splash close to the Zombie Villager while preserving containment; splash duration falls with distance
3. **Apply Weakness, then interact with the Zombie Villager while holding the ordinary Golden Apple.** Eating the Apple yourself does not start the cure, and an Enchanted Golden Apple does not substitute
4. **Keep it covered, contained and ticking until the conversion finishes.** Do not release it when the cure starts: Weakness is removed and Strength is applied during the wait

[Zombie targets][zombie-targets] · [Sunlight behavior][zombie-sun] · [Splash application][splash] · [Interaction dispatch][interaction] · [Cure interaction, timer and effects][cure]

The initial countdown is **3,600–6,000 game ticks**, normally **3–5 minutes at 20 ticks per second** before the random nearby-Iron-Bars/Bed acceleration. Those blocks are optional helpers, not extra ingredients. Stay nearby enough for the entity to tick; leaving the area or stopping the world does not provide an offline cure timer. The remaining conversion time is saved. See [Zombie Villager: curing step by step](../mobs/ZombieVillager.md#curing-step-by-step) for the complete cure behavior. [Saved timer and curing tick][cure-save] · [Random acceleration][cure-progress] · [Server ticking range][entity-ticking]

A successful cure produces a Villager. **If the original resident survives too, you now have two Villagers to protect**, but that is not a completed breeder or trading setup. Prepare safe housing and consult [Villager breeding and population](../mobs/Villager.md#breeding-and-population) for food and reachable spare beds, or [employment and job sites](../mobs/Villager.md#employment-and-changing-jobs) and [Trading](../trading/Trading.md) for ongoing use. Do not infer a fixed future profession or guaranteed offer from the basement resident's initial appearance. [Cure completion][cure-finish]

## Leave a recoverable route

Record the shelter's coordinates before descending, keep the ladder usable, and close accidental holes before leaving. If a splash misses or Weakness expires before the Apple interaction, keep the Zombie Villager contained and return with replacement supplies instead of opening its enclosure. If a cure has started, preserve the room and resume the wait when you return; confirm the actual conversion before releasing either resident.

If the Zombie Villager is gone, the remaining Villager and loot can still make the trip useful. The igloo definition has **no special spawn override** that replenishes that captive. Search another basement or follow the other [Zombie Villager acquisition routes](../mobs/ZombieVillager.md#finding-one) when the cure itself is your goal. [Spawn settings][definition] · [Saved residents][bottom]

## Related pages

- [Structures](Structures.md), [Taiga and snowy biomes](../biomes/TaigaAndSnowyBiomes.md), [Mountain and windswept biomes](../biomes/MountainsAndWindsweptBiomes.md)
- [Zombie Villager](../mobs/ZombieVillager.md), [Villager](../mobs/Villager.md), [Golden Apple](../items/GoldenApple.md)
- [Brewing](../brewing/Brewing.md), [Brewing Stand](../blocks/BrewingStand.md), [Snow and Powder Snow](../blocks/Snow.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2` against active code and bundled data. All three igloo templates were decoded to inspect furnishings, items, markers and saved entities. No in-game structure search, terrain survey, basement encounter, cure or resident-transport test was run. Layout and travel advice is source-based; successful placement and surviving supplies still depend on the world.

The world loader loads the structure and structure-set registries; the registered igloo type selects the template pieces, and chunk decoration places their blocks, data markers and entities. Chest markers assign the reviewed table, while the Brewing Stand loads its saved inventory directly. This verifies the active route, not just the presence of unused resources. [World loading][world-loader] · [Registry list][registries] · [Igloo registration][igloo-register] · [Structure generator][igloo-generator] · [Decoration dispatch][decoration] · [Piece dispatch][piece-dispatch] · [Template and marker placement][template-dispatch] · [Block-entity loading][block-entity-load]

[pieces]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/IglooPieces.java#L34-L145
[bottom]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/igloo/bottom.nbt
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/igloo.json
[biomes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/igloo.json
[normal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[parameters]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json
[parameter-map]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[biome-map]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[generation-option]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L59
[set-filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L49-L63
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure_set/igloos.json
[random-spread]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement.java#L69-L83
[starts]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L578
[start-biome]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/Structure.java#L202-L206
[locate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L55-L107
[locate-output]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L162-L182
[snow-surface]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/noise_settings/overworld.json#L1123-L1168
[powder]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/PowderSnowBlock.java#L115-L144
[freezing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2833-L2852
[top]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/igloo/top.nbt
[middle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/igloo/middle.nbt
[infested-register]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2280-L2291
[block-drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L378-L384
[infested]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/InfestedBlock.java#L51-L65
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/igloo_chest.json
[chest-fill]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/RandomizableContainer.java#L72-L89
[stand-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L192-L203
[brew-init]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L340
[brew-container]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L127-L138
[brew-weakness]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L184-L190
[stand-fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L96-L121
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/brewing_fuel.json
[apple-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/golden_apple.json
[settings]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings.java#L14-L27
[entity-place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L480-L525
[entity-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L1861-L1876
[persistence-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L372-L385
[despawn]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L607-L630
[zv-register]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L1575-L1584
[zombie-targets]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L105-L121
[zombie-sun]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L228-L254
[splash]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/ThrownSplashPotion.java#L38-L73
[interaction]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1075
[cure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/ZombieVillager.java#L138-L176
[cure-save]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/ZombieVillager.java#L83-L135
[cure-progress]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/ZombieVillager.java#L244-L266
[entity-ticking]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L396-L417
[cure-finish]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/ZombieVillager.java#L199-L236
[world-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L36-L44
[registries]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[igloo-register]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java#L24-L32
[igloo-generator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/IglooStructure.java#L22-L40
[decoration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L333-L344
[piece-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L102
[template-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/TemplateStructurePiece.java#L81-L101
[block-entity-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L282-L309
[template-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java#L69-L82
