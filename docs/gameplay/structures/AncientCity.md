# Ancient City

An **Ancient City** (`minecraft:ancient_city`) is an underground expedition for **Swift Sneak books, Echo Shards, Disc Fragments, and Ward or Silence armor-trim templates**. Prepare to avoid [Sculk Shriekers](../blocks/SculkShrieker.md) and the [Warden](../mobs/Warden.md), and leave yourself a clear route out before opening chests. These rewards are possible loot, not a checklist guaranteed by one city. [City loot][city-loot]

## Finding a city

Search the **Deep Dark in the bundled normal Overworld**. Deep Dark is the sole member of the city's allowed-biome tag, and the normal Overworld biome selector includes it. Finding sculk or a Deep Dark patch does not guarantee a city; [Cave biomes](../biomes/CaveBiomes.md#deep-dark) explains the biome and its separate sculk generation. [Allowed biome][city-biome] · [Normal preset][normal] · [Preset resource][overworld-preset] · [Preset mapping][overworld-map] · [Deep Dark selection][deep-selector]

The loaded city placement set uses **24-chunk spacing and 8-chunk separation**. These control candidate placement, not a fixed distance between cities you can visit. New starts also require structure generation enabled, a compatible biome source and a valid start in an allowed biome. Data packs, custom presets and terrain generated under older settings can differ. [Placement set][city-set] · [Registry loading][registry] · [Generation option][generation-option] · [Biome-source filter][structure-filter] · [Start generation][structure-create] · [Start-biome check][valid-biome]

**Look around Y=-51 for the center's ground-level reference, then explore vertically.** This is derived from the bundled center templates and placement code, not an in-game terrain survey. The configured start height is -27, but the named anchor is 24 blocks above the template origin and the ground offset is another block: the center template's base is Y=-52. The city has several levels; neither number is a safe teleport height or a promise that a tunnel at that height will intersect a city. [Structure settings][city] · [Center template][center-one] · [Second center][center-two] · [Third center][center-three] · [Anchor alignment][jigsaw-placement] · [Ground offset][ground-offset]

With permission level 2, use these commands **in the Overworld**:

```text
/locate structure minecraft:ancient_city
/locate biome minecraft:deep_dark
```

The first searches for the structure; the second finds a biome, which may have no city. Both search the command source's current dimension. The structure result omits Y and its suggested teleport keeps your current height; follow [the structure-search cautions](Structures.md#finding-a-structure) before using it. [Locate permission and searches][locate] · [Coordinate output][locate-output]

The checked bundled Cartographer offers and exploration-map destination tags do **not** provide an Ancient City map. An ordinary [Map](../items/Map.md) records terrain. Use exploration or the permitted search commands above rather than buying an unrelated explorer map expecting a city marker. [Cartographer offers][cartographer] · [Map destination tags][map-tags] · [Map creation][map-create]

## Prepare and move carefully

- **Bring food, tools, lighting, spare blocks and full Wool blocks.** Record the entrance and build a retraceable approach. Wool can dampen or block particular vibration paths; [Wool and Carpet](../blocks/WoolAndCarpet.md#vibrations) explains why a carpet floor is not the same as a full-block barrier
- **Use sneaking as one precaution.** [Sensors](../blocks/SculkSensors.md#wool-sneaking-and-water) detect game events, not everything audible to you. Sneaking does not suppress every event: opening containers, eating and block work can still matter
- **Keep off Sensors and Shriekers themselves.** Their direct contact routes bypass the ordinary sneaking precautions; see [Sensor contact](../blocks/SculkSensors.md#detection-travel-and-cooldown) and [Shrieker triggers](../blocks/SculkShrieker.md#what-triggers-a-shriek). Warnings belong to players and can carry between Shriekers, so moving to a different chest or Shrieker is not a fresh warning count. Read [warnings and summoning](../blocks/SculkShrieker.md#warning-levels-and-summoning) before disturbing them
- **Retreat if a Warden appears.** Wool and quiet movement do not stop its smell or contact detection, and a wall does not stop its sonic attack. Use the [Warden encounter guide](../mobs/Warden.md#practical-avoidance) rather than treating the chest expedition as a required boss fight
- **Bring Silk Touch if collecting sculk blocks is a goal.** The [Sculk harvesting guide](../blocks/Sculk.md#mining-and-experience) distinguishes item drops from XP and explains why collecting and replacing a Shrieker does not preserve its natural summoning state

These are source-based precautions, not a tested safe route. Lighting can help navigation, but the [Shrieker's summoning checks](../blocks/SculkShrieker.md#why-a-fourth-warning-may-not-spawn-a-warden) do not require ordinary monster-spawning darkness.

The city defines empty ordinary spawn overrides across its full recorded structure box. That affects normal spawn selection; it does **not** disable triggered Warden summoning or keep mobs from entering from elsewhere. A quiet-looking city is still a hazardous place to loot. [Spawn overrides][city] · [Override lookup][spawn-override] · [Shrieker response][shriek-response]

## Layout and sculk

The start pool chooses among **three center templates**. Connectors lead to surrounding walls, entrance pieces and optional structures such as barracks, chambers, ruins, a sauna and an ice box. The structures pool also contains an empty choice, and assembly is constrained by connector fit and space. Do not plan around a guaranteed room, a fixed chest count or a straight path from the center to every reward. [Center pool][center-pool] · [Center connectors][center-one] · [Wall pool][walls-pool] · [Optional pieces][structures-pool] · [Assembly checks][jigsaw-fit]

One configured wall-stair variant, `intact_horizontal_wall_stairs_5`, has **no bundled template** at this snapshot. Unless another template source supplies it, the loader provides an empty template with no connecting jigsaws, so that candidate cannot attach. The assembler can still try other shuffled candidates. [Pool entry][missing-stair-entry] · [Bundled wall inventory][wall-inventory] · [Template lookup][template-lookup] · [Empty-template fallback][template-fallback] · [Empty connectors][empty-jigsaws]

Some template connectors select the sculk pool, which can place an Ancient City Sculk Patch or choose nothing. The patch's extra-growth attempts explicitly place summoning-capable Shriekers when their support and space checks succeed. That is an active generation route, not proof that every sculk patch or city contains the same hazards. Follow [the Sculk family guide](../blocks/Sculk.md#finding-and-collecting-the-family) for the full generation and collection rules. [Barracks connectors][barracks] · [Sculk pool][sculk-pool] · [Placed feature][sculk-placed] · [Patch settings][sculk-config] · [Feature dispatch][feature-place] · [Patch placement][sculk-patch]

### Lower-center redstone rooms

Look below the large Reinforced Deepslate frame for the center's enclosed redstone assemblies. All three center templates contain a **waterlogged Sculk Sensor**, comparators, repeaters, Redstone Lamps and Sticky Pistons in their lower rooms. Their saved wiring and piston states differ, so this review does not establish one opening action that works for every center or the state you will find on arrival. Inspect the approach before disturbing the circuitry. [First center][center-one] · [Second center][center-two] · [Third center][center-three]

The waterlogged Sensor is still a detector: Water silences its ordinary clicks but does not suppress detection or its Shrieker-relevant game event. Follow [Sensors and water](../blocks/SculkSensors.md#wool-sneaking-and-water) while inspecting these rooms. No in-game room-entry or circuit test was run.

## Chest rewards

Decoded bundled templates assign **`minecraft:chests/ancient_city`** to chests in the barracks, three chamber variants, sauna and four tall-ruin variants. The ice-box piece instead assigns **`minecraft:chests/ancient_city_ice_box`**. For example, the barracks template has two ordinary-table chests and the ice box has one ice-box-table chest; those are template contents, not guaranteed city totals. The active template placer loads their block-entity data, and the container then resolves its saved loot table when first unpacked. Reopening an already generated chest does not reroll it. [Barracks][barracks] · [Example chamber][chamber-one] · [Sauna][sauna] · [Tall-ruin example][tall-ruin] · [Ice box][ice-box] · [Template resource loading][template-load] · [Pool placement][pool-place] · [Block-entity placement][template-place] · [Container loot][container-loot]

**One center variant has a separate prefilled chest.** The saved `city_center_2` template puts **one ordinary Golden Apple** in that chest's item list, with no loot-table assignment. That is distinct from the Enchanted Golden Apple entry in ordinary city loot, and the other two center templates do not contain that chest. Which center is selected and whether its contents remain available are separate questions. [Second-center chest][center-two] · [Center choices][center-pool] · [Saved chest item loading][chest-items]

### Ordinary city chests

The main pool makes **5–10 weighted selections**. Useful possibilities include:

| Selected entry | Bundled result |
| --- | --- |
| Echo Shards | 1–3; save eight for a [Recovery Compass](../items/RecoveryCompass.md#obtaining) |
| Disc Fragments | 1–3 fragments for Music Disc 5 |
| Swift Sneak book | One book with Swift Sneak I–III; see [equipment and movement effects](../enchanting/MobilityEnchantments.md#swift-sneak-crouching-and-crawling) |
| Enchanted Golden Apples | 1–2 |
| Equipment | Possible enchanted Diamond Hoe, Diamond Leggings or Iron Leggings |
| Other music discs | Possible Otherside, 13 or Cat |
| Other supplies | Sculk, Sensors, Catalysts, books, Regeneration II potions and assorted materials |

[Main pool, quantities and weights][city-loot] · [Random book level][random-enchant] · [Swift Sneak levels][swift-sneak] · [Potion effect][regeneration]

Counts in this table are **per selected entry**, not guaranteed contents or per-chest maxima; selections can repeat. Nine Disc Fragments in the nine crafting-grid slots make one Music Disc 5, so a single selected fragment entry is not a complete disc. Use a [Jukebox](../blocks/Jukebox.md) to play it. [Disc recipe][disc-recipe]

A **separate single roll** chooses among nothing at weight 75, **one Ward template at weight 4**, and **one Silence template at weight 1**. For each ordinary-table chest, that roll has a **5% chance to select Ward** or a **1.25% chance to select Silence**. The two trim outcomes share the same roll, so this pool does not award both together; repeated misses do not make a later chest guaranteed. Ice-box chests do not use this trim pool. [Trim pool][city-loot] · [Default entry weight][loot-weight] · [Weighted selection][loot-roll] · [Ice-box table][ice-loot]

If you find a trim template, consider [copying it before use](../mechanics/ArmorTrims.md#copy-a-template-before-using-it). The [Ward](../items/SmithingTemplateWardArmorTrim.md#obtaining) and [Silence](../items/SmithingTemplateSilenceArmorTrim.md#obtaining) pages own their exact ingredients; the first template is needed for duplication. [Ward duplication][ward-copy] · [Silence duplication][silence-copy]

When the optional **[Trade Rebalance pack](../trading/Trading.md#optional-trade-rebalance)** is selected and its resource takes precedence, it supplies a replacement city table. It replaces the ordinary leather entry with a Saddle and changes the second pool to weights 71 empty, 4 Mending book, 4 Ward and 1 Silence. That adds a **dedicated 5% Mending chance from that roll**, while the trim chances stay the same. Ordinary city chests can already yield Mending through their separate random-enchanted-book entry, whose allowed enchantments include Mending; 5% is not the total Mending chance per chest. [Ordinary random-enchantment pool][random-loot-enchants] · [Book enchantment selection][random-enchant] [Optional table][rebalance-loot] · [Pack metadata][rebalance-pack] · [Default feature set][default-flags] · [Selected-pack loading][selected-packs] · [Loot resource loading][loot-load]

### Ice-box chest

The ice-box table makes **4–10 weighted selections** among Suspicious Stew, Golden Carrots, Baked Potatoes, Packed Ice and Snowballs. It contains no Echo Shard, Swift Sneak or armor-trim entry. Its stew randomly receives either Night Vision or Blindness, so do not treat found stew as reliable vision support during an escape. [Ice-box table][ice-loot] · [Stew effect choice][stew-effect]

## The central frame and Primordial Caves

The center templates contain the large Reinforced Deepslate structure. In this source snapshot, Reinforced Deepslate is registered as an ordinary block, and the checked center templates contain no active portal blocks. The verified [Primordial Caves route](../dimensions/PrimordialCaves.md#entering-convert-a-nether-portal) uses an existing Nether portal's Pitcher Pod interaction. That conversion requires the Nether portal's ordinary Obsidian frame; the city's Reinforced Deepslate structure does not meet that validator. Follow the [placed portal guide](../blocks/NetherPortals.md#primordial-caves-portal) for conversion, item-consumption and return-route cautions. No Ancient City visit is required by this checked conversion path. [Center template][center-one] · [Reinforced Deepslate registration][reinforced] · [Ordinary block constructor][plain-block] · [Obsidian frame rule][portal-frame] · [Pod interaction and conversion][portal-conversion]

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Traced loaded worldgen registries and normal-Overworld biome selection, structure eligibility and placement, reachable template pools, all **58 bundled Ancient City NBT templates**, processors and sculk-feature wiring, chest assignments and runtime loot loading, selected recipes, map routes and the separate Primordial portal interaction. Height and loot percentages above are source-derived.

No world generation, `/locate`, exploration, Warden encounter, chest-opening, crafting or portal test was run. Template selection, terrain, prior exploration, server settings and resource/data-pack overrides can change what a particular expedition finds. This review does not establish a seed, a travel distance, a safe digging route, a guaranteed room or a successful portal landing.

Related: [Structures](Structures.md) · [Deep Dark](../biomes/CaveBiomes.md#deep-dark) · [Sculk](../blocks/Sculk.md) · [Sensors](../blocks/SculkSensors.md) · [Shriekers](../blocks/SculkShrieker.md) · [Warden](../mobs/Warden.md)

[city-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/ancient_city.json
[city-biome]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/ancient_city.json
[normal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[overworld-preset]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json
[overworld-map]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[deep-selector]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L815-L831
[city-set]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure_set/ancient_cities.json
[registry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L127
[generation-option]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L57
[structure-filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L45-L64
[structure-create]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L580
[valid-biome]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/Structure.java#L131-L140
[city]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/structure/ancient_city.json
[center-one]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/city_center/city_center_1.nbt
[center-two]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/city_center/city_center_2.nbt
[center-three]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/city_center/city_center_3.nbt
[center-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/city_center.json
[jigsaw-placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java#L50-L125
[ground-offset]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pools/StructurePoolElement.java#L93-L95
[locate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L55-L118
[locate-output]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/LocateCommand.java#L162-L183
[cartographer]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L337-L465
[map-tags]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/worldgen/structure
[map-create]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/EmptyMapItem.java#L16-L35
[spawn-override]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[shriek-response]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/SculkShriekerBlockEntity.java#L100-L167
[walls-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/walls.json
[structures-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/structures.json
[jigsaw-fit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java#L347-L447
[barracks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt
[sculk-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/sculk.json
[sculk-placed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/sculk_patch_ancient_city.json
[sculk-config]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/sculk_patch_ancient_city.json
[feature-place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pools/FeaturePoolElement.java#L81-L96
[sculk-patch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/SculkPatchFeature.java#L21-L77
[chamber-one]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/structures/chamber_1.nbt
[sauna]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/structures/sauna_1.nbt
[tall-ruin]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/structures/tall_ruin_1.nbt
[ice-box]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/structures/ice_box_1.nbt
[template-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java#L104-L129
[pool-place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L138-L181
[template-place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L254-L310
[container-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/RandomizableContainer.java#L50-L89
[random-enchant]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantRandomlyFunction.java#L56-L80
[swift-sneak]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/enchantment/swift_sneak.json
[disc-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/music_disc_5.json
[loot-weight]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L45-L54
[loot-roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L101
[ice-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/ancient_city_ice_box.json
[ward-copy]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/ward_armor_trim_smithing_template.json
[silence-copy]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/silence_armor_trim_smithing_template.json
[rebalance-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/loot_table/chests/ancient_city.json
[stew-effect]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/functions/SetStewEffectFunction.java#L62-L77
[reinforced]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6765-L6768
[portal-frame]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/portal/PortalShape.java#L26-L31
[portal-conversion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L110-L172
[plain-block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7274-L7292
[regeneration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L64-L66
[rebalance-pack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks/trade_rebalance/pack.mcmeta
[default-flags]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/flag/FeatureFlags.java#L32-L41
[selected-packs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/WorldLoader.java#L87-L95
[loot-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L70
[missing-stair-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/walls/no_corners.json#L57-L64
[template-lookup]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L90-L123
[template-fallback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java#L75-L97
[empty-jigsaws]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L196-L218
[wall-inventory]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/walls
[random-loot-enchants]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/on_random_loot.json
[chest-items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java#L82-L88
