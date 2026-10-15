# Data-driven definitions

Data packs still define biomes and the data-driven entity and item
registries. Rust parses their files, decodes and stores the definitions, and
encodes them for saves and registry synchronization
([`content/data`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/content/data)).
Datapacks can still override and add entries. Java objects are projections.

Registries: biomes, painting variants, damage types, banner patterns, jukebox
songs, trim patterns and materials, instruments, enchantments, enchantment
providers, and cat, frog, wolf, wolf sound, pig, cow, chicken and zombie
nautilus variants.

## How it works

- **Schemas:** [`registries.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/data/registries.rs)
  declares each registry's direct and network record. The fields, presence
  rules and defaults match the former Java codecs.
- **Decode:** [`schema.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/data/schema.rs)
  follows DFU over `JsonOps`, or `NbtOps` for synchronized NBT. That covers
  required, optional, default, `orElse` and lenient fields, Java's number
  conversions, `Float.compareTo` ranges, dispatch on `type`, and lists that
  drop bad entries with a logged error.
- **Files:** `RegistryDataLoader` hands files to Rust as bytes.
  [`content/json.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/json.rs)
  parses them as Gson's strict mode does, keeping each number's text. Text
  Rust can't parse goes back to the loader's own `StrictJsonParser`, so a
  malformed file fails with Gson's exception.
- **Other inputs** (network NBT, inline definitions): Java walks the
  `DynamicOps` value into a tree for Rust, numbered so that delegated parts
  resolve from the original nodes.
- **Encode:** Rust writes each definition back in the field order
  `RecordCodecBuilder` produces. Records of five or more members are regrouped
  (`GROUP_ORDER`, measured from DFU 8.0.16). Defaults are omitted as DFU omits
  them, and `"type"` is written last.
- **Java-owned fields:** sounds, the single-enchantment
  holder, and holder sets are decoded by their original Java codecs:
  - holder sets: items, enchantments, structures, biomes, and biome carvers
    and features, the last two with their `promotePartial` logging;
  - each field is `S::Java` in
    [`registries.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/data/registries.rs),
    by `NativeValues.JavaCodec` ordinal.

  Rust calls the codec while it decodes, at the field's position, once, on
  the calling thread. A file Rust parsed itself sends the field's binary
  input tree, preserving number text, which the loader's ops decode. Errors
  and partials compose with Rust's in DFU's order, and an exception the codec
  throws leaves the decode unchanged.
  Projections read the decoded values. The same codecs encode them.
- **Text, int providers, particle options and enchantment effect components** are
  [native value types](NATIVE-VALUES.md), decoded inline.
- **Errors:** a failed decode keeps DFU's partial definition, stored and
  projected like a decoded one, and released with its Java object.

[`NativeData`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/resources/NativeData.java)
owns the FFI and the generic codec. Each projection keeps its stored
definition alive, with native release registered through a Java Cleaner.
Collection does not establish a deterministic release deadline or a bounded
reload-memory result. Projections live in
[`NativeDataProjections`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/resources/NativeDataProjections.java)
and, for biomes,
[`NativeBiomes`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/biome/NativeBiomes.java).

Objects built in Java, such as inline trim patterns, have no stored
definition. Their unprojector describes them, and Rust checks the result by
encoding and decoding it, as DFU validates on encode. Rust then stores and
encodes it. A network-decoded object encoded in direct form takes the same
path, so its Java defaults fill the fields synchronization omits.

The data generator (`VanillaRegistries`) loads these registries from the
vanilla data pack through Rust. The Java builders that duplicated the files
(`BiomeData`, `Enchantments.bootstrap` and the like) are removed, and their
resource keys remain.

## Adding a registry or field

1. Add the schema to `registries.rs` and its `Registry` entry. The order must
   match `NativeData.Kind`.
2. Write a projector and an unprojector that read and build fields in schema order.
   A field Java still owns is `S::Java` with a new `NativeValues.JavaCodec`
   entry for its original codec; the projector reads it with `context.slot`
   and the unprojector writes it with `builder.slot`, both with that codec.
3. Point the class's `DIRECT_CODEC` and `NETWORK_CODEC` at `NativeData.codec`,
   and its data generator bootstrap at `NativeData.bootstrap`.
4. Run the checks below, including the fuzz observer, which compares accept,
   reject and encoding outcomes, error messages and partials with Frozen.

## Performance

The following are source-author measurements from 2026-10-13, not new
documentation-review runs. Bootstrap/decode numbers are medians of alternating
fresh JVMs on the release native library.
[`TimeContent.py`](https://github.com/HungLo2020/MattMC/blob/6b136158a2a3860e1be61d9a6ea4fdf7b47013f0/DevUtils/tests/content/TimeContent.py)
reports medians without enforcing a performance threshold. Its biome timing
runs after the full worldgen registry load and at least one warmup round, even
with `--rounds 1`; the original “first pass” labels below are author descriptions,
not proof of an untouched cold biome-codec path.

- **Against pre-change Current, cold single pass (10 pairs):** bootstrap
  3,592 ms against 3,584 ms, and worldgen registry load 522 ms against 517 ms
  (both within noise). The first biome decode pass is 11.4 ms against 14.6 ms.
- **Against Frozen, cold single pass (10 pairs):** bootstrap 3,819 ms against
  4,000 ms, and worldgen load 557 ms against 545 ms (within the run-to-run
  spread). The first biome pass is 11.5 ms against 16.9 ms.
- **Synthetic warm loop:** after 300 decodes of the same files, the JIT-warmed
  Java path is faster (4.7 ms against 6.4 ms per 68 biomes). This repeated-file
  workload differs from a single load; it remains a measured regression for
  that scope, not evidence that every repeated-load workload is unaffected.
- **Per registry (2026-10-15, at `594aeaf2`):**
  [`RegistryTiming`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/content/RegistryTiming.java)
  (`TimeContent.py --observer RegistryTiming`) times each worldgen
  registry's files through the loader's per-file path after warmup, which
  shows where a whole-load difference comes from.
  - **Warm decode vs Frozen, 5 pairs:** 75.2 ms against 71.2 ms per round.
    Rust-decoded registries account for most of the difference, enchantments
    most of all (2.08 against 1.41 ms). That enchantment cost was the same
    before the effect move (2.07 against 2.19 ms on `186614ce`).
  - **Cold `worldgen_load`, 9 pairs, release references:**
    - 512 ms against Frozen's 476 ms (+7.6%);
    - 504 ms against `186614ce`'s 519 ms.
    Matched runs of the same code differed by about 5%.
- **After Java-owned fields decode in place (2026-10-15, release,
  matched Frozen; load/biome measurements at `37776ed0`, wolf follow-up at `a8af4006`):**
  - `worldgen_load` 525.5 against Frozen's 488.5 ms (+7.6%), level with `e35d2450`.
  - Biome decode per round 7.86 against Frozen's 7.04 ms (+11.7%).
  - `RegistryTiming` total +5–8%. Text is inline, so text-only registries
    (trim, jukebox, instrument) are faster than Frozen.
  - **Owner-approved exception: `wolf_sound_variant`.** Seven files per round,
    median 0.052 ms on Frozen against 0.091 ms (+75%, 9 pairs). Current
    ranged 0.067–0.158 ms.
    - About half was there before (`e35d2450`: 0.068 ms). That's the data
      path's fixed per-file cost: the native call, payload read, projection
      and Cleaner.
    - The rest is six sound callbacks per file, about 0.4 µs each over
      `RegistryFileCodec`'s own lookup.
    - The exception covers only this workload. It isn't a general waiver.
- **Gameplay** (small DH fixture, moving camera, four alternating runs per side
  against pre-change Current): average FPS 82.3 against 81.2, median frame
  8.59 ms against 8.46 ms, and p99 59.8 ms against 68.5 ms. Swap reached
  2.3–4.7 GB, so one run per side is an outlier. Runs:
  `artifacts/performance/realworld-20261011/dhab10-*`. This swapped, small-fixture
  comparison does not establish a matched four-mode Frozen floor, isolated
  content-migration speedup or sustained gameplay/memory acceptance.

Copy FFI bytes through direct `ByteBuffer`s (`segment.asByteBuffer()`), not
heap segments (`MemorySegment.ofArray`, `toArray`). In an earlier build, heap
segments passing through the JDK's shared segment-access code during world
load slowed hot native-segment readers such as `NativeLiveBlockSection.get`.
Their JIT profiles turned polymorphic, and gameplay p99 rose from 57 ms to
79 ms (`dhab9-*`, JFR in `dhjfr-*`). The direct-buffer copies removed the
regression.

## Known differences

- Before 2026-10-15 (until `e35d2450`), data-path errors differed from
  Frozen: they had no partial, joined only one decode phase's message, and
  malformed files threw a different exception. `DataFuzzReference` measured
  11,244 differing rows. Those causes are fixed. The corpus is measured to
  match, not proven equivalent for every input.
- `VanillaRegistries.createLookup()` (IDE-only command validation and data
  generation) fails on biomes because their files name placed features that
  the Java worldgen generator lacks. Frozen failed there too, on custom biome
  keys. Worldgen ownership is a separate migration goal.
- Packet stream codecs (`DIRECT_STREAM_CODEC`, used for inline holders in
  item stacks) are unchanged Java packet code.

## Verification (2026-10-13)

The source author records the following alternating fresh-JVM results. The
documentation review inspected implementation and driver assertions; it did
not rerun these experiments or independently inspect their external artifacts:

- **Frozen:** the `data_registries` section has 362 identical rows (two pairs),
  and `DataFuzzReference` has 37,418 identical rows (37,003 mutated cases).
- **Pre-change Current:** both are also identical.
- **Suites:** Rust 2,808 passed, and Java `test` plus `parityTest` ran 2,147
  with 0 failed.
- **Not established:** `RunFeatureParity.py` (banners, armor trims, held
  shields) produced no images. Its fixtures pose the camera in the canonical
  `Origin` world, which is not retained on this machine. With a substituted
  save, Current, pre-change Current and Frozen all failed before reaching the
  world (`artifacts/graphics-captures/feature-parity/shared-content-20261013*`).
  Rendered item, trim and banner parity is therefore unverified. The
  definition dumps match within the recorded observer scope (above).

The later [integration commit](https://github.com/HungLo2020/MattMC/commit/6b136158a2a3860e1be61d9a6ea4fdf7b47013f0)
reports 2,825 Rust and 2,188 Java tests, separately from this page's 2,808/2,147
checkpoint. Preserve both provenances; they are not one independently rerun suite.

[`VerifyContent.py`](https://github.com/HungLo2020/MattMC/blob/6b136158a2a3860e1be61d9a6ea4fdf7b47013f0/DevUtils/tests/content/VerifyContent.py)
gates exact dump equality across sides and repeatability within each side. It
records initial checkout identities and observer/driver hashes, but does not
require a pinned clean Frozen checkout, prove the mapped native library or
compiled production-class identities, or reseal those inputs after execution.
The selected reference must already have compiled main classes. Report which
reference and build were used; do not inherit stronger guards from other oracles.
Equal rows can include `round_trip=false`, `UNDECODABLE`, `UNEQUAL` or `ERROR`
on both sides. The comparator does not independently reject those markers;
successful round trips require inspecting the dump as well as its equality
result. Fuzz equality covers the deterministic cases and normalized outcomes emitted by
the observer, not every malformed input, callback, reload or live-world behavior.

## Checks

```bash
cargo test --manifest-path src/main/rust/Cargo.toml --lib content::data
cargo test --manifest-path src/main/rust/Cargo.toml --lib content::json
./gradlew -PmattmcRustProfile=release test --tests '*NativeDataTest' --tests '*NativeBiomesTest'
JAVA_HOME=/path/to/jdk-25 python3 DevUtils/tests/content/VerifyContent.py --sections data_registries --output build/data-verification
JAVA_HOME=/path/to/jdk-25 python3 DevUtils/tests/content/VerifyContent.py --observer DataFuzzReference --output build/data-fuzz
```

- The Rust tests decode, encode and re-decode every shipped file, and check
  the decode rules and encode order.
- `NativeDataTest` encodes Java-built copies of loaded non-biome elements
  and compares them with their originals. Its generator-registry loop also
  excludes biomes. `NativeBiomesTest` separately covers biome copy/codec
  behavior; it does not remove the generator-biome limitation above.
- The `data_registries` section compares each element's registry ID, tags,
  save encoding and network encoding with Frozen.
- [`DataFuzzReference`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/content/DataFuzzReference.java)
  sends about 37,000 deterministic mutations through the loader's file path,
  the direct codec over JSON, and the network codec over NBT. Every outcome
  must match Frozen.
