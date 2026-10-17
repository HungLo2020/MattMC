# Jungle Temple

A **Jungle Temple** is a cobblestone and mossy-cobblestone building with **two treasure chests, two arrow-dispenser traps and a lever-operated piston mechanism**. Bring a pickaxe, usable [Shears](../items/Shears.md), lighting and spare blocks. The lower corridors need inspection before you cross them. [Trap corridor][jungle-trap-one] · [Second trap and first chest][jungle-trap-two] · [Levers and hidden chest][jungle-door]

## Where to search

Search **[Jungle or Bamboo Jungle](../biomes/JunglesAndSwamps.md)** in the normal Overworld. **Sparse Jungle is not eligible** in the bundled temple biome tag. The structure's set uses random-spread placement with **32-chunk spacing and 8-chunk separation**; those candidate controls do not guarantee a building in every eligible patch. [Definition][jungle-definition] · [Allowed biomes][jungle-biomes] · [Placement set][jungle-set] · [Spread calculation][positions] · [Normal world][normal] · [Biome parameters][parameters] · [Preset mapping][parameter-map] · [Jungle choices][biome-map]

These rules describe the bundled **normal Overworld**. New starts need structure generation enabled, an eligible biome and a successful placement candidate. Data packs, custom presets and already-generated terrain can differ. The loaded definition and structure set are used by the active generation path. [Generation option][structure-option] · [Registry data][world-load] · [Resource and tag loading][resource-load] · [Set filtering][set-filter] · [Start creation][generate] · [Biome check][biome-filter]

Look for the low stone building through the vegetation. Its main piece is **12×15 blocks**, rotates horizontally and settles to average checked ground height. The initial terrain check can reject a start when a sampled corner is below sea level. Stairs connect the visible building to its lower corridor. [Piece dimensions and ground call][jungle-piece] · [Average-height adjustment][average] · [Terrain eligibility][single] · [Corner samples][corners] · [Lower stairs][jungle-stairs] · [Stone mixture][masonry]

With permission level 2, the exact command is:

```text
/locate structure minecraft:jungle_pyramid
```

The registered **structure ID is `jungle_pyramid`**; `jungle_temple` is the implementation type named inside its definition. Search in the Overworld and read the [locate cautions](Structures.md#finding-a-structure) before using its coordinates. [Definition][jungle-definition] · [Set's structure ID][jungle-set] · [Registered types][types] · [Locate registry and permission][locate] · [Current-dimension search][locate-search]

## Lower corridor and arrow traps

The generated lower level contains **two attached tripwire lines**, each connected by Redstone Dust to its own Dispenser. Vines partly cover the dispenser approach. One chest stands at the end of this trapped route. Finding and disabling one line does not disable the other. [First line, wiring and dispenser][jungle-trap-one] · [Second line and chest][jungle-trap-two]

An eligible entity crossing the wire can power its hooks. **Mining an armed wire with an ordinary tool can also trigger a pulse.** Hold usable Shears in the main hand and **mine the String segment** to mark that segment disarmed before removal. This is a breaking action, not right-clicking the wire. Stay out of the firing corridor while doing it, and inspect both lines before advancing. See [Tripwire disarming](../blocks/Tripwire.md#breaking-and-disarming) for existing signals and tool limitations. [Contact detection][wire-contact] · [Removal and Shears handler][wire-cut] · [Hook's disarmed check][hook]

The Dispensers use a separate loot table with **1–2 rolls of 2–7 ordinary Arrows**, giving **2–14 arrows per fresh dispenser before any shots**. Their contents are finite: each projectile shot consumes one Arrow. Once the route is secure, remaining Arrows and the components can be useful supplies. Do not assume an old trap is empty just because another player has passed through. [Dispenser assignment][dispenser-create] · [Registered loot key][loot-keys] · [Arrow table][arrows] · [Power and dispatch][dispense] · [Arrow behavior registration][arrow-register] · [Projectile consumption][projectile]

## Lever wall and hidden chest

The other chest sits in a small chamber beside the lower corridor's **three-lever wall**. Its generated mechanism contains **three Sticky Pistons, Redstone Dust and one Repeater**. The levers switch ordinary redstone signals; the moving barrier is built from ordinary blocks. It does not require a special temple key or defeating a guardian. [Chamber and mechanism][jungle-door] · [Lever action][lever] · [Piston response][piston]

For a direct looting route, secure the trap corridor and mine through the masonry around the lever wall to expose the small chamber. If you want to preserve or investigate the puzzle, leave its wiring and blocks intact, change one lever at a time and observe the barrier from a safe position. This guide has not tested a lever sequence in game. See [Redstone](../redstone/Redstone.md), [Pistons](../blocks/Pistons.md) and [Repeaters](../blocks/RedstoneRepeater.md) when examining the mechanism.

## Chest rewards

Both chest positions use the **same Jungle Temple loot table**, independently of the arrow-dispensing table. The piece records its placed chests and traps; the attached container loot is filled once, so revisiting does not renew the treasure. [Saved placement flags][jungle-piece] · [Main chest][jungle-trap-two] · [Hidden chest][jungle-door] · [Chest loot attachment][chest-create] · [Access][container-open] · [One-time fill][container-loot]

| Pool | Possible rewards |
| --- | --- |
| 2–6 weighted general rolls | Diamonds, Iron/Gold Ingots, Bamboo, Emeralds, Bones, Rotten Flesh, Leather, Copper/Iron/Golden/Diamond Horse Armor, or an enchanted Book |
| One separate template roll | **Two Wild Armor Trim Smithing Templates**, or nothing |

[General chest table][jungle-loot] · [Template table][jungle-trim]

The template pool has item weight 1 against empty weight 2, so a chest has a **1-in-3 chance of two Wild templates** using the unchanged table. General rolls may repeat an entry; neither chest guarantees Diamonds or a Book. The two dispensers do not roll Wild templates. [Template weights][jungle-trim] · [General pool][jungle-loot] · [Dispenser contents][arrows]

## Before leaving

- Check both chest locations; the trapped corridor chest is only one of the two
- Mark a clear route back through the vegetation
- Keep recovered redstone parts together if you want to study or rebuild the circuit
- Light and inspect rooms before treating the building as shelter

The procedural piece places no fixed mob resident, mob spawner or archaeology block, and the definition has **no structure-specific spawn override**. Ordinary biome mobs can still occupy the area. A temple visit is a chest-and-circuit expedition; use the [archaeology routes](../blocks/DecoratedPot.md) when Pottery Sherds are the goal. [Definition][jungle-definition] · [Piece factory][jungle-wiring] · [Construction][jungle-piece] · [Mechanisms][jungle-door]

## Related pages

- [Jungles and Swamps](../biomes/JunglesAndSwamps.md)
- [Dispenser and Dropper](../blocks/DispenserAndDropper.md)
- [Desert Pyramid](DesertPyramid.md)
- [Swamp Hut](SwampHut.md)
- [Structures](Structures.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`. The loaded world-generation definitions, biome eligibility, active procedural piece construction and placement, relevant block/entity interactions and loot assignments were inspected. No in-game search, generation, trap, looting, brushing, encounter or production-rate test was performed. Preparation advice is derived from those source rules.

This route builds a procedural piece, without loading a saved temple NBT template. Circuit placement and active component handlers were checked; no tested puzzle sequence or trap timing is claimed. [Piece factory][jungle-wiring] · [Active placement][active-place] · [Piece dispatch][piece-dispatch]

[jungle-trap-one]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JungleTemplePiece.java#L171-L235
[jungle-trap-two]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JungleTemplePiece.java#L236-L310
[jungle-door]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JungleTemplePiece.java#L321-L354
[jungle-definition]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/structure/jungle_pyramid.json#L1-L6
[jungle-biomes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/jungle_temple.json#L1-L6
[jungle-set]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/structure_set/jungle_temples.json#L1-L14
[positions]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement.java#L67-L84
[normal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L1-L13
[parameters]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/overworld.json#L1-L3
[parameter-map]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L73-L109
[biome-map]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java#L76-L88
[structure-option]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L59
[world-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[resource-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L309-L334
[set-filter]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L50-L64
[generate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L494
[biome-filter]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L548-L576
[jungle-piece]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JungleTemplePiece.java#L30-L89
[average]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/ScatteredFeaturePiece.java#L41-L66
[single]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/SinglePieceStructure.java#L21-L33
[corners]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/Structure.java#L171-L180
[jungle-stairs]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JungleTemplePiece.java#L133-L158
[masonry]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JungleTemplePiece.java#L358-L365
[types]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java#L23-L43
[locate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/commands/LocateCommand.java#L55-L68
[locate-search]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/server/commands/LocateCommand.java#L95-L107
[wire-contact]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/TripWireBlock.java#L150-L195
[wire-cut]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/TripWireBlock.java#L109-L123
[hook]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/TripWireHookBlock.java#L109-L166
[dispenser-create]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java#L472-L486
[loot-keys]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/storage/loot/BuiltInLootTables.java#L40-L42
[arrows]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/chests/jungle_temple_dispenser.json#L1-L32
[dispense]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L82-L129
[arrow-register]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L65-L69
[projectile]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/core/dispenser/ProjectileDispenseBehavior.java#L25-L41
[lever]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/LeverBlock.java#L90-L95
[piston]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L75-L126
[chest-create]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/StructurePiece.java#L447-L469
[container-open]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/entity/RandomizableContainerBlockEntity.java#L49-L89
[container-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/RandomizableContainer.java#L72-L89
[jungle-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/chests/jungle_temple.json#L4-L168
[jungle-trim]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/chests/jungle_temple.json#L169-L189
[jungle-wiring]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JungleTempleStructure.java#L8-L19
[active-place]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L333-L344
[piece-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L102
