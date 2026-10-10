# Map

A filled **Map** (`minecraft:filled_map`) refers to saved terrain data for a particular region and dimension. Start one by using an [Empty Map](EmptyMap.md), then hold it in either hand while exploring to update its terrain. Carrying it elsewhere in the inventory still participates in marker tracking, but does not trigger the ordinary terrain-update branch. [Creation and held updates][map] · [Saved data][data]

## Region and scale

A new ordinary map starts at scale 0. Its region is aligned to the game's map grid rather than centered at your exact standing position. Two maps created within the same grid region can therefore cover the same area. [Fresh map centering][data]

| Scale | Blocks per terrain pixel | Region width and height |
| --- | ---: | --- |
| 0 | 1 | 128 × 128 blocks |
| 1 | 2 | 256 × 256 blocks |
| 2 | 4 | 512 × 512 blocks |
| 3 | 8 | 1,024 × 1,024 blocks |
| 4 | 16 | 2,048 × 2,048 blocks |

Larger scales show more territory with less detail per pixel. Surveying is dimension-specific: an Overworld map does not start recording a Nether region just because you carry it through a portal. In ceiling dimensions, the terrain updater uses a special generated color pattern instead of revealing a normal cave-floor survey. [Scale and terrain algorithm][map] · [Scale data][data]

## Reading and navigating

A held map keeps **north at the top, south at the bottom, west on the left, and east on the right**. North is decreasing Z; east is increasing X. Turning does not rotate the terrain to keep your forward direction at the top. On an Overworld map, your player pointer turns with your facing while it is within the map's in-area marker range. Use that pointer to orient yourself, then follow your position against recognizable terrain such as a coastline or river. [World directions][directions] · [Player position and rotation][navigation-data] · [Map and marker drawing][navigation-render]

A Nether map is an exception to the facing advice: its in-area marker rotation changes with world time, so that pointer is not a reliable heading. Use the map in its own dimension; carrying it through a portal does not make its terrain or position tracking describe the other dimension. [Dimension and player updates][navigation-tracking] · [Nether rotation][navigation-data]

### Off-map and missing markers

When your position leaves the in-area marker range, the player symbol is clamped to an edge or corner. **That off-map symbol does not turn with your facing.** Read its position to decide which direction leads inward:

- **Top edge:** travel south
- **Bottom edge:** travel north
- **Left edge:** travel east
- **Right edge:** travel west

At a corner, combine the directions: a top-left marker calls for southeast travel. An edge position gives no distance measurement and can stay there through a long journey, even while you are heading the right way. Once the pointer comes into the interior, use its position to approach your chosen landmark or destination. [Marker range, edge clamping, and fixed off-map rotation][navigation-data]

On an ordinary map made from an Empty Map, travelling far enough can **remove the player marker altogether**. The cutoff is measured from the map's center, separately along X and Z: the marker disappears when either horizontal offset reaches **320 × 2^scale blocks**. That means 320, 640, 1,280, 2,560, or 5,120 blocks at scales 0–4. This is a square limit, not a circular distance or a distance measured from the map's edge. Keep coordinates or a known return landmark for the region; a missing marker gives you no direction back. [Ordinary map creation][ordinary-creation] · [Distance cutoff][navigation-data]

Successful exploration maps, including the bundled shipwreck treasure map, enable distant tracking and keep an off-limits player marker beyond that cutoff. Its edge position still guides you toward the mapped region, rather than giving a precise distance or facing. The shipwreck map uses scale 1 and a red X; follow the [Buried Treasure guide](../structures/BuriedTreasure.md#using-a-treasure-map) once you reach the destination area. [Exploration-map creation][exploration-creation] · [Shipwreck map settings][shipwreck-map]

## Copies, enlargement, and locking

Use a [Cartography Table](../blocks/CartographyTable.md) for the practical one-material operations:

- Paper enlarges an eligible unlocked map
- An Empty Map produces two copies sharing the input map ID
- A Glass Pane creates a locked terrain snapshot with a new map ID

The [workstation guide](../blocks/CartographyTable.md#the-three-operations) explains costs, eligibility, fresh terrain after scaling, and the separate crafting recipe's locked-map limitation.

Crafting also supports one filled map plus one or more Empty Maps in separate occupied slots, producing the same number of copies as total inputs. Stacking several blanks in one slot does not count as several recipe ingredients. Another recipe surrounds a filled map with eight Paper to enlarge a qualifying non-exploration map below scale 4. These are special recipes registered in the current serializer table. [Clone recipe implementation][clone] · [Enlargement implementation][extend] · [Recipe registrations][serializers]

## Markers and treasure maps

Using a filled map on a Banner toggles that banner's marker when it is in the allowed map area and marker limits permit. Repeating the same interaction can remove the marker. The marker can use the banner's color and name. [Banner interaction][map] · [Marker bounds and toggle][data]

A map can also carry structure markers. [Buried Treasure maps](../structures/BuriedTreasure.md) are specialized filled maps produced through exploration-map loot functions, not a separate registered item. Making an ordinary terrain map does not automatically mark every nearby structure. A supplied structure marker gives a destination, not a guarantee of unlooted contents or a safe digging depth. An Empty Map merely named “Buried Treasure Map” can result from a failed destination lookup; use the linked guide to distinguish it from a working map.

## Sources and verification

Source-reviewed on 2026-10-02 at `3e85592c4c78ebb420302360667a6c230dc0318d`. No in-game surveying, marker, copying, scaling, or locking test was run. Custom map components, data packs, and existing saved data can alter particular items.

Navigation and marker behavior were additionally source-reviewed on **2026-10-10** at `f86206767dadde696adfed4e04c5ee97cd0d0885`. The review followed server map updates, client data receipt, held-map drawing, and the active native/Rust map-material route. No in-game navigation or rendering test was run. [Held updates][held-updates] · [Client updates][client-updates] · [Held-map rendering][held-render] · [Native map submission][native-render] · [Rust map materials][rust-materials]

Related: [Empty Map](EmptyMap.md) · [Compass](Compass.md) · [Cartography Table](../blocks/CartographyTable.md) · [Items](Items.md)

[map]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/MapItem.java
[data]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/saveddata/maps/MapItemSavedData.java
[clone]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/crafting/MapCloningRecipe.java
[extend]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/crafting/MapExtendingRecipe.java
[serializers]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/crafting/RecipeSerializer.java#L14-L15
[directions]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/core/Direction.java#L32-L38
[navigation-data]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/saveddata/maps/MapItemSavedData.java#L245-L334
[navigation-render]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/client/renderer/MapRenderer.java#L37-L85
[navigation-tracking]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/saveddata/maps/MapItemSavedData.java#L151-L175
[ordinary-creation]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/EmptyMapItem.java#L16-L31
[exploration-creation]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/storage/loot/functions/ExplorationMapFunction.java#L73-L90
[shipwreck-map]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/loot_table/chests/shipwreck_map.json#L7-L25
[held-updates]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/item/MapItem.java#L267-L279
[client-updates]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java#L1615-L1627
[held-render]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java#L309-L327
[native-render]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L4783-L4791
[rust-materials]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/rust/render/worldrender/vanilla/resources/materials.rs#L151-L179
