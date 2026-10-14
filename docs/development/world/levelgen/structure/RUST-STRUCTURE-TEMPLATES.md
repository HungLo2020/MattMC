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
| Processors | block ignore, jigsaw replacement (before any rule), rule, block rot, gravity, protected blocks, nop |
| Rule tests | always true, block, tag, block state, random block, random block state |
| Position tests | always true, linear, axis-aligned linear |
| Block-entity modifiers | passthrough, append loot, append static, clear |
| Liquid containers | simple waterloggable blocks with the default `placeLiquid`; kelp, kelp plant, seagrass and tall seagrass (theirs do nothing) |

Java's path keeps a template when it has capped, block-age, blackstone-replace
or lava-submerged processors, another liquid container, a jigsaw block without
NBT or with an unparsable `final_state`, a settings random other than the
placement random, a placement random that is not a `WorldgenRandom` over
Xoroshiro (a bare Xoroshiro source derives `nextLong` and `nextInt`
differently), or an extent outside the bound 3×3 chunks. When the settings
random is the placement random (fossils), block rot draws from it in
processing order, before the write loop's loot seeds. Processors
read at every template position before the bounding box applies, and Java's
generation region serves chunks beyond that 3×3.

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
