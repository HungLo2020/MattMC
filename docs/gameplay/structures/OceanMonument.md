# Ocean Monument

**Ocean Monuments** are underwater expeditions for [Prismarine](../blocks/Prismarine.md), [Sea Lanterns](../blocks/LuminousBlocks.md#sea-lantern), gold, and [Wet Sponges](../blocks/Sponge.md). Prepare for renewable [Guardian](../mobs/Guardian.md) encounters and three structure-placed [Elder Guardians](../mobs/ElderGuardian.md), whose Mining Fatigue can make an improvised mining escape painfully slow. The structure ID is **`minecraft:monument`**. [Structure definition][definition] · [Building and residents][building]

## Finding a Monument

In the bundled **Normal Overworld**, search **Deep Ocean, Deep Cold Ocean, Deep Lukewarm Ocean, or Deep Frozen Ocean**. These four biomes are selected by the active Overworld preset and belong to the Monument's allowed-biome tag. Warm Ocean is not an eligible start biome. The [ocean-biome guide](../biomes/Oceans.md) owns the broader ocean families and resources. [Normal preset][normal] · [Preset selector][selector] · [Ocean selection][oceans] · [Allowed tag][allowed] · [Deep variants][deep]

The structure set uses triangular random-spread placement with **32-chunk spacing and 5-chunk separation**. These are candidate-placement parameters, not a promise of a Monument every 512 blocks. Generation must also pass the allowed-biome check and a surrounding-biome check: the sampled biomes within 29 blocks of the candidate's check point at sea level must belong to the ocean/river tag. Structures must be enabled for new starts. Seeds, terrain, data packs, and already-generated chunks affect what you find. [Placement data][placement] · [Placement calculation][spread] · [Surrounding check][generation] · [Surrounding tag][surrounding] · [Generation setting][setting]

A **level-3 Cartographer** can offer an Ocean Explorer Map. Its base offer is **13 Emeralds and one Compass**, with the displayed price subject to trading adjustments. It is one candidate in that level's trade selection, and its destination search must succeed before an offer is created. The bundled destination tag contains `minecraft:monument`. This map route does not certify that the building is untouched or safe; follow its Monument marker, then inspect your approach from the surface. Use [Trading](../trading/Trading.md) for stock and prices and [Maps](../items/Map.md) for navigation. [Trade candidates][trades] · [Active selection][trade-selection] · [Offer creation][map-offer] · [Map destinations][map-tag]

With permission level 2, use `/locate structure minecraft:monument` in the Overworld. `minecraft:ocean_monument` is the generator's type, **not** the structure ID to put in that command. Read the [coordinate cautions](Structures.md#finding-a-structure): a located X/Z is not a safe underwater teleport or an entrance. [Locate command][locate] · [Structure definition][definition]

## Prepare before diving

- Establish a surface return point, carry food, and mark the entrance and junctions as you explore. The interior's room connections vary; do not plan on retracing a memorized layout from another Monument. [Room graph][rooms]
- Arrange **Water Breathing** for a long dive, or use a properly activated [Conduit](../blocks/Conduit.md) within its coverage. These effects are recognized by the active air-supply check. Plan [brewing supplies](../brewing/Brewing.md) before entering and watch the remaining effect time. [Breathing helper][breathing] · [Air-supply consumer][air]
- Bring a weapon and blocks for cover. A Guardian's charging beam loses its target when line of sight breaks; damaging it at close range can trigger its spikes. [Beam and spikes](../mobs/Guardian.md#behavior)
- Bring an **unbroken Iron, Diamond, or Netherite Pickaxe** if you want the gold core. Prismarine has a lower harvest requirement; [its own guide](../blocks/Prismarine.md#recovering-placed-blocks) covers suitable tools and shapes. Choose Sea Lantern recovery through the [lighting guide](../blocks/LuminousBlocks.md#sea-lantern). [Gold properties][gold] · [Pickaxe tag][pickaxe] · [Iron-tier tag][iron-tier] · [Mining](../mechanics/Mining.md)
- Treat [Milk](../items/MilkBucket.md) as a deliberate reset: it clears beneficial effects, including Water Breathing, alongside Mining Fatigue. A living Elder can apply fatigue again on a later pulse. Drink where you can breathe, then restore the protection you need before resuming a long dive. [Milk action][milk] · [Elder pulse][fatigue]

Conduit Power does **not** cancel Mining Fatigue. The player's mining calculation applies both effects, plus separate submerged and off-ground modifiers. Prepare to use openings and cover while clearing the residents rather than depending on rapid wall breaking. [Mining calculation][mining]

## Layout and clearing the residents

The generator builds a Prismarine complex with an entry room, a core room, two wings, a top penthouse, and a randomized network of other rooms. It chooses a horizontal orientation, so “left wing” and “right wing” describe the building, not fixed compass directions. Look for the front arches and mark your chosen access route. [Orientation and building][generation] · [Included rooms][building] · [Entrance construction][entrance] · [Room connections][rooms]

The **two wings and top penthouse each place one Elder Guardian**, for three resident placements in a newly generated, intact Monument. They are placed during structure generation, not chosen from the ordinary monster spawn list. A visited structure may have missing or killed residents. Follow the [Elder Guardian guide](../mobs/ElderGuardian.md#mining-fatigue) for the nearby fatigue pulse, including its ability to reach through walls. [Wing residents][wings] · [Penthouse resident][penthouse] · [Placement helper][elder-placement]

Ordinary Guardians can continue to spawn inside the Monument's **full structure bounding box** after the Elders are defeated. Removing the Elders does not change that spawn override. Cover, room control, and a route to air still matter while collecting blocks. Killing an Elder also does not by itself clear a Mining Fatigue effect already running on you; allow its duration to end or use Milk with the breathing precautions above. [Spawn data][definition] · [Active spawn lookup][spawn-lookup] · [Elder effect](../mobs/ElderGuardian.md#mining-fatigue)

## What to collect

The checked Monument generator places **blocks and resident mobs, not loot chests**. Choose your collection goal before dismantling the rooms. [Piece generator][pieces]

| Goal | Checked source inside or around the Monument | Collection notes |
| --- | --- | --- |
| Prismarine, Prismarine Bricks, Dark Prismarine | Building and room materials | Use the [Prismarine guide](../blocks/Prismarine.md) for recovery and recipes |
| Sea Lanterns | Lights in the generated building | [Silk Touch and Crystal drops](../blocks/LuminousBlocks.md#sea-lantern) have different results |
| Gold | A **2 × 2 × 2 core of eight Gold Blocks**, enclosed in Dark Prismarine | Recover with a suitable pickaxe; these are placed blocks, not chest rolls |
| Wet Sponges | Optional sponge rooms and player-credited Elder deaths | Room availability and its individual sponge placements vary; [drying and draining](../blocks/Sponge.md) are separate steps |
| Prismarine Shards and Crystals | Guardian and Elder death tables | Quantities and selected rewards are conditional; see [Guardian drops](../mobs/Guardian.md#drops) and [Elder drops](../mobs/ElderGuardian.md#drops) |
| Tide Armor Trim Smithing Template | Separate Elder death-table roll | **20% per Elder** in the unchanged table, with no Looting bonus; three residents do not guarantee a template |

[Building materials][materials] · [Gold core][core] · [Gold block loot][gold-loot] · [Sponge-room fitting][sponge-fit] · [Sponge placement][sponge-room] · [Elder loot][elder-loot]

The generator includes a core room, but does not promise a sponge room in every layout. Within a selected sponge room, individual sponge columns are also randomized. If the goal is a first Sponge, each Elder's Wet Sponge pool requires player kill credit; an environmental death without that credit does not satisfy it. The [Elder page](../mobs/ElderGuardian.md#drops) separates that condition from the independent Tide template roll. [Room construction][building] · [Sponge placement][sponge-room] · [Elder loot][elder-loot]

For repeat ingredient collection, ordinary Guardians have a continuing natural-spawn route. The three structure residents are a different source: the ordinary Monument spawn list contains no Elder Guardian. This guide does not establish a tested farm rate or a mechanism that replenishes defeated Elders. [Spawn override][definition] · [Guardian spawning](../mobs/Guardian.md#obtaining)

## MattMC inventory access

These expedition rewards also include ordinary category-listed items, such as Prismarine, Sponges, Shards, Crystals, and the Tide template. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can supply ordinary listed items in Creative. Both Guardian spawn eggs are listed too. That is a separate way to obtain an item; it does not create a naturally generated Monument or establish a natural Elder respawn route. [Material and reward listings][inventory-items] · [Egg listings][eggs]

## Sources and verification

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. Checked loaded Normal-preset biome/structure data, placement and generation callers, room assembly, Elder placement, ordinary Guardian spawn lookup, Cartographer map selection, preparation consumers, and reward sources. The registry loader reads the active biome and structure JSON; the chunk generation path dispatches the Monument pieces. No in-game generation, map purchase, dive, combat, loot, or farming test was run. [World-data loading][world-load] · [Registry data][registry-load] · [Structure selection][structure-selection] · [Piece dispatch][piece-dispatch]

Related: [Structures](Structures.md) · [Ocean biomes](../biomes/Oceans.md) · [Guardian](../mobs/Guardian.md) · [Elder Guardian](../mobs/ElderGuardian.md) · [Sponge](../blocks/Sponge.md) · [Prismarine](../blocks/Prismarine.md) · [Conduit](../blocks/Conduit.md)

[definition]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/structure/monument.json
[building]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L167-L206
[normal]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[selector]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[oceans]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L190-L200
[allowed]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ocean_monument.json
[deep]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/worldgen/biome/is_deep_ocean.json
[placement]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/worldgen/structure_set/ocean_monuments.json
[spread]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement.java#L71-L87
[generation]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentStructure.java#L29-L55
[surrounding]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/worldgen/biome/required_ocean_monument_surrounding.json
[setting]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L57
[trades]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L400-L409
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L842
[map-offer]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1559-L1577
[map-tag]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/worldgen/structure/on_ocean_explorer_maps.json
[locate]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/server/commands/LocateCommand.java
[rooms]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L213-L323
[breathing]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/effect/MobEffectUtil.java#L43-L45
[air]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L442
[gold]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/block/Blocks.java#L1085-L1093
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[milk]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L22-L24
[fatigue]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/monster/ElderGuardian.java#L63-L73
[mining]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L652
[entrance]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L431-L474
[wings]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1784-L1894
[penthouse]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1393-L1399
[elder-placement]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1534-L1545
[spawn-lookup]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[pieces]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java
[materials]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1402-L1409
[core]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L754-L757
[gold-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/blocks/gold_block.json
[sponge-fit]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L140-L155
[sponge-room]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1725-L1760
[elder-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities/elder_guardian.json
[inventory-items]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[eggs]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1970-L2031
[world-load]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/server/WorldLoader.java#L34-L51
[registry-load]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[structure-selection]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L580
[piece-dispatch]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L103
