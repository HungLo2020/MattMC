# Shipwreck

**Shipwrecks** are broken wooden vessels with supplies, valuables, and possible maps leading to [Buried Treasure](BuriedTreasure.md). Search the hull that actually generated: a wreck can be complete, split into a half, tilted, upside down, or partly buried, so three chests are not guaranteed.

## Where to search

The bundled Overworld definitions have two variants:

- **Ocean shipwrecks** (`minecraft:shipwreck`) are eligible in all nine ordinary ocean biomes, including their deep, cold, and frozen variants
- **Beached shipwrecks** (`minecraft:shipwreck_beached`) are eligible in Beach and Snowy Beach. Stony Shore is not in the bundled beach tag

Ocean wrecks use ocean-floor height placement. Beached wrecks use the terrain surface and deliberately lower the hull into it, so search around exposed wood rather than expecting every room above ground. The two variants share a random-spread placement set; eligible biomes and successful generation determine which can appear. Neither is promised in every eligible biome patch.

A fed adult [Dolphin](../mobs/Dolphin.md) can guide you toward a shipwreck or an ocean ruin. It cannot be instructed to choose a wreck, and its search does not check whether anyone has already looted the destination.

With permission level 2, search from the Overworld using `/locate structure minecraft:shipwreck` or `/locate structure minecraft:shipwreck_beached`. Read the [locating cautions](Structures.md#finding-a-structure): the result is a location, not a safe arrival height.

## Searching the hull

The active generator selects bundled templates and gives their chest markers three different loot tables. The inspected templates contain these layouts before terrain interactions or later player changes:

| Hull template | Chest types present |
| --- | --- |
| Full hull or mast variant | Supply, map, and treasure |
| Back half | Map and treasure |
| Upright or sideways front half | Supply |
| Upside-down front half | Supply and map |

The same chest-type pattern holds for the inspected degraded versions. Chest placement rotates with the hull. If a front half has only a supply chest, repeatedly digging for a missing stern does not add the other compartments.

### Chest rewards

| Chest | Bundled main loot |
| --- | --- |
| Supply | 3–10 weighted rolls: possibilities include food/crops, Paper, Moss Blocks, Coal, Gunpowder, TNT, and enchanted leather armor |
| Map | One treasure-map attempt, plus three weighted rolls for Paper, Feathers, Books, a Compass, a Clock, or an Empty Map |
| Treasure | 3–6 rolls for Iron/Gold Ingots, Emeralds, Diamonds, or Bottles o' Enchanting, plus 2–5 rolls for Iron/Gold Nuggets or Lapis Lazuli |

Roll counts are selections, not guaranteed distinct items or final stack totals. The supply table can produce Poisonous Potatoes, Rotten Flesh, and Suspicious Stew with harmful effects; bring reliable food rather than depending on the first chest.

Each of the three chest tables also makes a separate **1-in-6 roll for two Coast Armor Trim Smithing Templates**. Otherwise that extra pool gives nothing. This chance is per chest using the unchanged table, not a guaranteed reward per wreck.

## From a map chest to buried treasure

The map chest always selects its exploration-map entry, but creating a working map requires a successful structure lookup. Its destination tag contains Buried Treasure, and its resulting filled map has a red X. The lookup does not require an untouched chest and can choose a destination another map already marks.

See [Buried Treasure](BuriedTreasure.md#using-a-treasure-map) for the map's failure case, navigation, digging coordinates, and loot limits. An ordinary Empty Map from the map chest's separate supply pool is not automatically a treasure map.

## Preparing a dive

- Keep a clear route back to the surface before entering an enclosed hull
- Bring food, a tool for opening obstructed compartments, and room to collect items without lingering underwater
- Consider [Water Breathing and Night Vision](../brewing/Brewing.md) for deeper or awkward wrecks
- Watch the surrounding water for [Drowned](../mobs/Drowned.md); finding a wreck does not secure the area
- Keep your return boat or shore route easy to find before following a new map

## Related pages

- [Ocean Ruins](OceanRuins.md)
- [Buried Treasure](BuriedTreasure.md)
- [Dolphin](../mobs/Dolphin.md)
- [Transport](../mechanics/Transport.md)
- [Structures](Structures.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. The Java generation and loot paths, JSON data, and compressed NBT template markers were inspected. No in-game world generation, dive, chest opening, or map test was run. Structure generation must be enabled; custom data packs, world presets, template overrides, and existing terrain can differ.

- [Ocean definition](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/structure/shipwreck.json), [beached definition](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/structure/shipwreck_beached.json), and [shared placement set](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/structure_set/shipwrecks.json)
- [Ocean eligibility tag](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/shipwreck.json), [ocean members](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/biome/is_ocean.json), [deep-ocean members](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/biome/is_deep_ocean.json), [beached eligibility tag](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/shipwreck_beached.json), and [beach members](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/biome/is_beach.json)
- [Registered structure codec](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java), [generation entry point](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/structures/ShipwreckStructure.java), and [template selection, placement, and chest-marker mapping](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/structures/ShipwreckPieces.java)
- [Full-hull template](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/structure/shipwreck/rightsideup_full.nbt), [back-half template](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/structure/shipwreck/rightsideup_backhalf.nbt), [upright front half](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/structure/shipwreck/rightsideup_fronthalf.nbt), and [upside-down front half](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/structure/shipwreck/upsidedown_fronthalf.nbt)
- [Template resource loading](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager.java), [data-marker dispatch](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/TemplateStructurePiece.java), and [chest loot assignment and loading](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/RandomizableContainer.java)
- [Supply loot](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/chests/shipwreck_supply.json), [map loot](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/chests/shipwreck_map.json), and [treasure loot](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/chests/shipwreck_treasure.json)
- [Exploration-map function and defaults](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/functions/ExplorationMapFunction.java); [treasure destination tag](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/structure/on_treasure_maps.json)
