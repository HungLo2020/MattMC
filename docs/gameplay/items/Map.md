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

## Copies, enlargement, and locking

Use a [Cartography Table](../blocks/CartographyTable.md) for the practical one-material operations:

- Paper enlarges an eligible unlocked map
- An Empty Map produces two copies sharing the input map ID
- A Glass Pane creates a locked terrain snapshot with a new map ID

The [workstation guide](../blocks/CartographyTable.md#the-three-operations) explains costs, eligibility, fresh terrain after scaling, and the separate crafting recipe's locked-map limitation.

Crafting also supports one filled map plus one or more Empty Maps in separate occupied slots, producing the same number of copies as total inputs. Stacking several blanks in one slot does not count as several recipe ingredients. Another recipe surrounds a filled map with eight Paper to enlarge a qualifying non-exploration map below scale 4. These are special recipes registered in the current serializer table. [Clone recipe implementation][clone] · [Enlargement implementation][extend] · [Recipe registrations][serializers]

## Markers and treasure maps

Using a filled map on a Banner toggles that banner's marker when it is in the allowed map area and marker limits permit. Repeating the same interaction can remove the marker. The marker can use the banner's color and name. [Banner interaction][map] · [Marker bounds and toggle][data]

A map can also carry structure markers. [Buried Treasure maps](../structures/BuriedTreasure.md) are specialized filled maps produced through exploration-map loot functions, not a separate registered item. The marker gives a destination, not a guarantee of unlooted contents or a safe digging depth. An Empty Map merely named “Buried Treasure Map” can result from a failed destination lookup; use the linked guide to distinguish it from a working map.

## Sources and verification

Source-reviewed on 2026-10-02 at `3e85592c4c78ebb420302360667a6c230dc0318d`. No in-game surveying, marker, copying, scaling, or locking test was run. Custom map components, data packs, and existing saved data can alter particular items.

Related: [Empty Map](EmptyMap.md) · [Compass](Compass.md) · [Cartography Table](../blocks/CartographyTable.md) · [Items](Items.md)

[map]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/MapItem.java
[data]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/saveddata/maps/MapItemSavedData.java
[clone]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/crafting/MapCloningRecipe.java
[extend]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/crafting/MapExtendingRecipe.java
[serializers]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/crafting/RecipeSerializer.java#L14-L15
