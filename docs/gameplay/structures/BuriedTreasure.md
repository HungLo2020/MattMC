# Buried Treasure

**Buried Treasure** (`minecraft:buried_treasure`) is a buried chest with a **Heart of the Sea** in its bundled loot table. A treasure map from a [Shipwreck](Shipwreck.md) or [Ocean Ruin](OceanRuins.md) can lead to its horizontal location. Bring a shovel, a pickaxe, and a plan for digging safely near water.

## Getting a treasure map

Treasure maps are filled maps with a destination marker, not a separate registered treasure-map item.

- A **shipwreck map chest** always selects one treasure-map attempt in its dedicated map pool. Not every shipwreck template includes that chest
- A **small ocean-ruin chest** selects the map entry with a 5-in-12 chance; a **large ruin chest** uses 10 in 23
- A [Dolphin](../mobs/Dolphin.md) can help locate those source structures, but its own destination tag does not contain Buried Treasure

Each map-producing entry starts with an Empty Map and runs a bounded structure search from the chest's location. The function's default destination tag contains only Buried Treasure. The tables set zoom level 1, use the red-X marker, and explicitly allow previously located structures.

**A selected map entry is not proof that a destination was found.** If lookup fails, the function leaves the Empty Map item unchanged, while the next function still gives it the Buried Treasure Map name. A named item without a working filled map/red X is therefore possible in source. Check that it actually displays a destination before starting an expedition.

## Using a treasure map

Hold a working map while travelling in the dimension where it was created, normally the Overworld. It tracks the player's position and updates terrain while held. A distant player uses an off-map marker; moving toward the mapped area brings the position onto the map. Bring your marker toward the red X, then search the ground there.

The bundled map scale covers **256×256 blocks**, with two blocks per terrain pixel. The X identifies an **X/Z location**, not a chest depth, doorway, or safe place to teleport. The lookup does not inspect the contents of the chest. Multiple maps can point to the same treasure, including one another player already opened or removed.

Opening or revisiting a chest does not reroll its original loot table: the chest clears its loot-table reference when that table is unpacked. Finding a second map is therefore not a way to refill the first treasure chest.

## Where the chest generates

The bundled allowed-biome tag contains **Beach and Snowy Beach**, not Stony Shore or the general ocean tag. The placement set considers chunks with a 0.01 frequency filter before the remaining generation checks. That is not a promise of one chest per 100 beach chunks in a particular world.

The buried-treasure piece uses **local chunk X=9 and Z=9**. Its locate offset uses the same horizontal position. If you use chunk coordinates to narrow the search, that gives the source-defined target column; the map alone does not display a precise depth.

From the ocean-floor height, the piece searches downward for a position above **Sandstone, Stone, Andesite, Granite, or Diorite**, then attempts to place the chest there. Consequently it is not guaranteed to lie just under a shallow layer of sand. The piece can finish without placing a chest if it finds no suitable support before reaching the world minimum, and later changes can also remove an existing chest.

With permission level 2, `/locate structure minecraft:buried_treasure` is another search route. Run it in the Overworld for the bundled normal-world route, and follow the [coordinate cautions](Structures.md#finding-a-structure). Structure generation, biome selection, and data packs still govern availability.

## Loot and why to collect it

A newly generated, untouched chest using the unchanged table includes **one [Heart of the Sea](../items/HeartOfTheSea.md)**. The active crafting recipe surrounds it with **eight Nautilus Shells** to make one [Conduit](../items/Conduit.md).

Other pools provide:

- **5–8 weighted rolls** for Iron Ingots, Gold Ingots, or TNT
- **1–3 rolls** for Emeralds, Diamonds, or Prismarine Crystals
- **0–1 roll** for a Leather Chestplate or Iron Sword
- **Two rolls** for Cooked Cod or Cooked Salmon, with 2–4 fish per selected entry
- **0–2 potion items** whose table entry assigns no potion contents; the registered default also has empty contents. Do not rely on finding Water Breathing potions here

These are table selections, not guaranteed distinct item types. The Heart of the Sea guarantee belongs to the unopened chest's unchanged loot table; it does not mean every map destination still contains an available heart.

## Digging safely

Work from a stable opening with an exit, especially where the search column reaches water. Keep air access and food available, and use the pickaxe if the search continues into stone. Collect the contents before leaving, then record that location as visited so another map to the same chest does not send you on a duplicate trip.

## Related pages

- [Shipwreck](Shipwreck.md)
- [Ocean Ruins](OceanRuins.md)
- [Dolphin](../mobs/Dolphin.md)
- [Map](../items/Map.md)
- [Structures](Structures.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. Map-table defaults and explicit overrides, location lookup, chest placement, loot loading, and the Conduit recipe were inspected. No in-game map creation, navigation, excavation, or chest-opening test was run. Custom data packs, world settings, already-generated terrain, and other players' changes can alter the result.

- [Structure definition](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/structure/buried_treasure.json), [allowed-biome tag](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/buried_treasure.json), [beach members](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/biome/is_beach.json), and [frequency/locate-offset settings](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/structure_set/buried_treasures.json)
- [Registered structure codec](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java), [piece location](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/structures/BuriedTreasureStructure.java), [downward search and chest placement](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/structures/BuriedTreasurePieces.java), and [placement frequency and locate-coordinate calculation](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/placement/StructurePlacement.java)
- [Shipwreck map entry](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/chests/shipwreck_map.json), [small-ruin entry](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/chests/underwater_ruin_small.json), and [large-ruin entry](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/chests/underwater_ruin_big.json)
- [Map function codec, defaults, and failed-lookup behavior](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/functions/ExplorationMapFunction.java), [treasure destination tag](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/structure/on_treasure_maps.json), [server lookup](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/level/ServerLevel.java#L1329-L1342), and [structure-reference handling](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L272-L304)
- [Map creation and held updates](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/MapItem.java), [map scale, dimension, and player markers](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/saveddata/maps/MapItemSavedData.java), and [one-time chest loot loading](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/RandomizableContainer.java)
- [Buried-treasure loot](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/chests/buried_treasure.json), [default potion contents](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L1770-L1778), and [Conduit recipe](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/conduit.json)
