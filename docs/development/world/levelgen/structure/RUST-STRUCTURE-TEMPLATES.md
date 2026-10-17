# Native structure templates

`StructureTemplate.placeInWorld` hands a palette to
[`NativeTemplates`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/NativeTemplates.java)
after its own palette choice and emptiness checks. When the template can run
natively,
[`decoration/template.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/decoration/template.rs)
does `processBlockInfos` and the write loop over the decoration region bound
for the chunk being decorated:

- the position transform about `getRotationPivot()`, processors in order, then
  each block's `mirror`/`rotate` (Java precomputes all twelve per state);
- the bounding-box filter, the barrier written under block-entity blocks,
  `setBlock` with the call's flags through the decoration write path;
- loot seeds for randomizable containers, drawn from the placement random in
  loop order;
- waterlogging: the source list, `SimpleWaterloggedBlock.placeLiquid` and the
  flowing-fluid pass.

Java keeps the template data and NBT. Each block-entity load is a replayed
event in write order: Java copies the template's tag, applies the rule edits
Rust recorded (append loot with its seed, append static, clear), adds the loot
seed and loads the block entity. Shape updates (unknown shape only) and
entities run in Java after the call, as `placeInWorld` orders them.

## Supported

| Piece | Supported |
| --- | --- |
| Processors | block ignore, jigsaw replacement (before any rule), rule, block rot, gravity, protected blocks, block age, blackstone replace, lava submerged block, capped (constant or uniform limit, one delegate), nop |
| Rule tests | always true, block, tag, block state, random block, random block state |
| Position tests | always true, linear, axis-aligned linear |
| Block-entity modifiers | passthrough, append loot, append static, clear |
| Liquid containers | simple waterloggable blocks with the default `placeLiquid`, slabs (a double slab's does nothing), dried ghasts, campfires and candles (waterlogged and unlit; a lit candle writes with flags 11); kelp, kelp plant, seagrass and tall seagrass (theirs do nothing) |

Java's path keeps a template when it has another processor or liquid
container, a jigsaw block without NBT or with an unparsable `final_state`, a
lava-submerged processor with a dynamic-shape state, a settings random other
than the placement random, a placement random that is not a `WorldgenRandom`
over Xoroshiro (a bare Xoroshiro source derives `nextLong` and `nextInt`
differently), or writes outside the bound 3×3 chunks (the settings box, or the
whole extent without one). When the settings random is the placement random
(fossils), block rot and block age draw from it in processing order, before the
write loop's loot seeds. Processors read at every template position before the
bounding box applies: reads beyond the bound 3×3 go to Java's generation
region through the pure-read upcall, and a rule reads the world only when one
of its location tests needs it. Capped processors run in `finalizeProcessing`
with `RandomSource.create(seed).forkPositional().at(origin)` and
`Util.toShuffledList`'s order.

`javaTemplates={reason=count}` in the decoration statistics counts templates
Java placed inside a bound region, by decline reason.

Compiled templates are cached by (template, palette, processor list identity).
Worker threads share templates (one builds a start's jigsaws while another
places a piece's data markers), so `StructureTemplate.Palette`'s block-list and
jigsaw caches are concurrent.
Programs need counter policies for every state they can write; a placement
declines (`JAVA_ROUTE`) only before writing, when the write path is not ready.

## Verify

```sh
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> --rounds 0 --radius 3 \
  --sides reference,current --reference-root build/reference-<commit> --dimensions minecraft:overworld \
  --structures minecraft:village_plains,minecraft:trial_chambers,minecraft:ancient_city,...
```

`templates=` in `native_decoration` counts native placements. Structure
regions share their spacing with biome regions: run `--structures` and
`--biomes` separately so each list keeps its own centres. A `structures`
field diff that is only jigsaw piece boxes is the reload effect described in
the [known differences](../RUST-FULL-GENERATION.md#known-differences-2026-10-14).
