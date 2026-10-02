# Suspicious Sand

**Suspicious Sand** (`minecraft:suspicious_sand`) is registered as a block item, but ordinary mining is not a way to recover it. Its block-loot table has no item pools, including no Silk Touch branch. [Item registration][registration] · [Block registration][sand-reg] · [Loot][loot]

## Work on the placed block

Keep it supported and follow the [Brush archaeology instructions](Brush.md#archaeology-basics). Successful brushing emits any configured stored loot and leaves ordinary [Sand](Sand.md). A block without stored contents does not gain an artifact merely from being suspicious. [Brush call][brush-use] · [Completion][brush-finish]

Removing support can start a fall after the scheduled check. Its ordinary landing cancels placement and item recovery, unlike the normal falling-Sand route. See [suspicious terrain handling](../blocks/SoilSandAndGravel.md#suspicious-sand-and-suspicious-gravel) before excavating around it. [Falling callback][brush-fall] · [Cancellation flag][cancel-fall] · [Landing branch][fall-land]

Related: [Soil, Sand, and Gravel](../blocks/SoilSandAndGravel.md) · [Brush](Brush.md) · [Blocks](../blocks/Blocks.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. No in-game archaeology or falling test was run. Acquisition by structures, commands, or inventory placement and every possible stored loot table are outside this page’s scope.

[registration]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L146
[sand-reg]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java#L313-L347
[loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/suspicious_sand.json
[brush-use]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/BrushItem.java#L68-L98
[brush-finish]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L117-L145
[brush-fall]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/BrushableBlock.java#L62-L98
[cancel-fall]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L311-L313
[fall-land]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L180-L232
