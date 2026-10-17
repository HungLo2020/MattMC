# Pitcher Pod

Pitcher Pod is the plantable item `minecraft:pitcher_pod`. In MattMC it also activates conversion of an ordinary Nether portal into a **Primordial Caves portal**. [Planting registration][ancient-seed-items] · [Portal conversion][portal-pod]

## Obtaining

The Sniffer's active digging tick calls its drop method, which executes the registered digging gift table. The bundled table makes one equal-weight choice between **one Torchflower Seed and one Pitcher Pod**, giving a **50% pod chance per pool execution**. See [Sniffer seeds and pods](../blocks/Torchflower.md#sniffer-seeds-and-pods) for the exact ground, AI conditions, and caller chain; this is not a fixed digging-time guarantee or a complete guide to acquiring the first Sniffer. [Tick caller][sniffer-tick] · [Drop method][sniffer-conditions] · [Table registration][loot-names] · [Loot table][sniffer-loot] · [Default weights][loot-weights]

Breaking a complete immature Pitcher Crop at **ages 0–3** returns **one pod** from its lower half. At mature **age 4**, it returns **one Pitcher Plant and no pod**. Growing the pod therefore does not multiply pods for portal conversion. [Crop loot][pitcher-crop-loot] · [Two-block handling][double-plant-support]

## Portal conversion

Drop **one pod, separated from the rest of the stack**, into a complete active Nether portal. MattMC attempts to convert the portal and then discards the **whole dropped item stack**. The completion check can fail without preserving that stack, so do not experiment with a valuable stack. [Conversion and item removal][portal-pod]

The [Primordial Caves guide](../dimensions/PrimordialCaves.md) explains the destination and return route. Conversion replaces the portal blocks; it does not preserve their ordinary Nether destination alongside the new one. [Block replacement][portal-pod]

## Planting

The [Pitcher Crop guide](../blocks/PitcherPlant.md) covers its full lifecycle. Plant a pod on Farmland with raw brightness **at least 8**. The crop has ages **0–4** and adds its upper half at age 3, so keep that cell clear. Each accepted Bone Meal use advances one stage when the crop's light and space checks permit it; either half can receive Bone Meal once it is tall. [Crop support][pitcher-survival] · [Light threshold][crop-light-ravager] · [Growth and upper space][pitcher-growth] · [Bone Meal][pitcher-bonemeal]

Harvest by breaking and replanting. This crop belongs to the double-plant family and does not inherit ordinary Wheat's use-to-harvest or hoe-area-harvest callbacks. Pods also have a **30%** chance to raise a partly filled Composter by one level; an empty Composter accepts its first compostable item automatically. [Crop class][pitcher-class] · [Default interaction][default-use] · [Compost value][seed-compost] · [Compost roll][compost-roll]

## Related pages

- [Pitcher Plant and Pitcher Crop](../blocks/PitcherPlant.md)
- [Pitcher Plant item](PitcherPlant.md)
- [Primordial Caves](../dimensions/PrimordialCaves.md)
- [Sniffer](../mobs/Sniffer.md)
- [Farmland](../blocks/Farmland.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `85428ea17c51c31ddb67b6c1060b78f4c3a13515` on 2026-10-02. Inline source links identify the exact implementation and bundled data used here. No in-game growth timing, harvest, mob-interaction, or placement test was run. Data packs, world-generation settings, and game rules can change these results; quoted ordinary drops assume block drops are enabled. The portal conversion was source-reviewed; no in-game portal conversion or round trip was performed.

[ancient-seed-items]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/Items.java#L2236-L2237
[portal-pod]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L110-L172
[sniffer-tick]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L349-L360
[sniffer-conditions]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L258-L284
[loot-names]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/storage/loot/BuiltInLootTables.java#L100-L106
[sniffer-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/gameplay/sniffer_digging.json#L1-L20
[loot-weights]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L45-L55
[pitcher-crop-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/pitcher_crop.json#L8-L154
[double-plant-support]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L40-L87
[pitcher-survival]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/PitcherCropBlock.java#L85-L111
[crop-light-ravager]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CropBlock.java#L149-L167
[pitcher-growth]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/PitcherCropBlock.java#L135-L183
[pitcher-bonemeal]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/PitcherCropBlock.java#L185-L213
[pitcher-class]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/PitcherCropBlock.java#L32-L50
[default-use]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L202-L210
[seed-compost]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L108-L109
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L317
