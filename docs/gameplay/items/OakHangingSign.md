# Oak Hanging Sign

Oak Hanging Sign (`minecraft:oak_hanging_sign`) is the stackable item for both ceiling and wall-hanging Oak signs. It stacks to **16** and is separate from an ordinary [Oak Sign](OakSign.md). [Item registration][items] · [Hanging placement][hanging-item]

## Obtaining

At a crafting table, put **two Iron Chains in the top corners**, with **six Stripped Oak Logs filling the two rows below**, to make **six Oak Hanging Signs**. The recipe does not substitute planks, unstripped logs, or all-bark stripped wood. [Recipe][recipe-oak-hanging-sign]

Ordinary hand mining recovers one item from either placed form; an axe is faster, and Silk Touch is unnecessary. The item drop does not preserve the text or decoration. [Loot][loot-oak-hanging-sign] · [Shared wall loot][wall-loot] · [Axe tag][axe-tag] · [Registration][blocks]

## Usage

Hang it beneath a support with a sturdy underside center, or extend it sideways from a full sturdy face. It can connect to other appropriately aligned hanging signs. Four lines fit on each face, with a narrower text width than an ordinary sign. The shared guide explains [orientation and support](../blocks/Signs.md#placement-orientation-and-support), [editing](../blocks/Signs.md#writing-and-editing-both-faces), and [dye, glow, ink, and wax](../blocks/Signs.md#dye-glow-ink-and-wax). [Ceiling placement][ceiling] · [Wall placement][wall-hanging] · [Hanging text width][hanging-entity]

## Behavior

The ceiling form breaks when its valid overhead support is lost. **The current wall-hanging implementation checks support at placement but does not automatically break on ordinary side-support removal**; see the source-qualified [wall-hanging distinction](../blocks/Signs.md#wall-hanging-signs). Both forms support waterlogging. [Ceiling support][ceiling] · [Wall update][wall-hanging] · [Inherited survival][default-survival] · [Waterlogging][waterlogged]

## Related pages

- [All hanging sign variants](../blocks/Signs.md#hanging-sign-variants)
- [Iron Chain](IronChain.md)
- [Oak Sign](OakSign.md)

## Sources and verification

Reviewed at `7af3a1594956f41ed57c3bf67d11ce006e61530f` on 2026-10-02. Source and bundled-data review only; no in-game placement, editing, crafting, support-removal, or harvesting test.

[items]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/Items.java#L1419-L1511
[hanging-item]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/HangingSignItem.java
[recipe-oak-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/oak_hanging_sign.json
[loot-oak-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/oak_hanging_sign.json
[wall-loot]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L1302-L1750
[ceiling]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/CeilingHangingSignBlock.java
[wall-hanging]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/WallHangingSignBlock.java
[hanging-entity]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/entity/HangingSignBlockEntity.java
[default-survival]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L311
[waterlogged]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java
