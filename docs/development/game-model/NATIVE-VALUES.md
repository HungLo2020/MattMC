# Native value types

Rust decodes, validates and encodes the value types that definitions embed
([`values.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/data/values.rs)).
The Java objects are projections built by
[`NativeValues`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/resources/NativeValues.java)
codecs. Their former Java codecs are removed.

Current kinds (`NativeValues.Kind`, the order of `VALUES` in Rust):

- **Int providers** (`IntProvider.CODEC`, `codec(min, max)`, `NON_NEGATIVE_CODEC`, `POSITIVE_CODEC`).
- **Float providers** (`FloatProvider.CODEC`, `codec(min, max)`).
- **Level-based values** (`LevelBasedValue.CODEC`).
- **Uniform ints** without a type (`UniformInt.CODEC`).
- **Particle options** (`ParticleTypes.CODEC`).
- **Position sources** (`PositionSource.CODEC`).
- **Text components** (`ComponentSerialization.CODEC`, [`text.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/data/text.rs)),
  styles (`Style.Serializer`), and click and hover events.
- **Item stacks** (`ItemStack.CODEC`/`MAP_CODEC`, `SINGLE_ITEM_CODEC`), slotted
  stacks (`ItemStackWithSlot.CODEC`) and component patches (`DataComponentPatch.CODEC`)
  ([`items.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/data/items.rs)).
- **Enchantment effects**: the effect component map (`EnchantmentEffectComponents.CODEC`)
  and the value, entity and location effect types
  ([`enchant.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/data/enchant.rs)).

Definitions decoded by Rust use these schemas directly. Biome particles and
enchantment provider costs are decoded inline, with no Java round trip.
Java codecs that still embed a value, such as features and loot, call the
native codec.

## How it works

- **Bounded codecs.** `ValueCodec.bounded(min, max)` passes the range to
  Rust. Rust checks the provider's range as `codec(min, max)` did, with the
  same messages.
- **Type registries.** The int provider, float provider and level-based value
  type registries still hold one entry per case. `dispatchCase` builds each
  entry's `MapCodec` from the native codec. Rust decodes that case's fields
  from a map without `type`, as `/particle` reads options.
- **Identities.** Particle cases are listed in registry order.
  `NativeParticles` checks that Rust's case names (`NativeValues.caseNames`)
  match `BuiltInRegistries.PARTICLE_TYPE`, and fails at first use if they differ.
- **Block states.** These are read from the
  [Rust block registry](RUST-BLOCK-REGISTRY.md).
  - Rust asks Java to install the registry (`NativeBlockRegistry.ready()`)
    on the first block state a decode or encode meets. Values without block
    states, such as nearly every biome, never wait for it.
  - Kinds that embed block states, and biome decoding, still throw before
    bootstrap finishes.
  - If installing fails, the Java entry point throws `IllegalStateException`
    ("Decoding <kind> needs the native block registry") after the call, with
    the original installation failure as its cause. The callback itself never
    throws into Rust.
  - Don't move the install back to an eager check before decoding. That
    put the whole registry export inside worldgen registry loading, about
    +15% of its cold time.
- **Delegated parts.** Components without a native codec, dialogs, profiles
  and NBT payloads stay Java codecs. Rust calls back into Java to decode them
  in order, and they resolve through `NativeData.Context`, like a registry's
  slots.
- **Callbacks and buffers.** Every callback runs exactly once.
  - A result too large for Java's buffer is kept by Rust and copied out by
    `mattmc_value_take_pending`; the call itself never runs again.
  - A callback answer too large for Rust's buffer is kept by Java for Rust's
    immediate retry.
  - Each codec call holds its own per-thread scratch depth, so a nested call
    never overwrites a result that is still being read.
  - `NativeValuesTest` covers exception identity, nesting and oversized
    results through the package-private `NativeValues.testDelegates` seam.

## Item components

- **Native components:** a component whose value codec is native is decoded
  and encoded by Rust. Its schema is in `component_schema` in `items.rs`, and
  its projection is in `NativeItemStacks` and `NativeItemComponents`. There
  are 35 so far, including the plain values, text, lore and containers.
  `NativeItemStacks` checks at startup that the Java and Rust lists match.
- **Other components:** every other component runs its Java codec while Rust
  decodes (`schema::java_delegate`), so errors and partial results still
  compose exactly.
- **Encoder cache:** types that vanilla caches (`cacheEncoding`) are always
  encoded through the type's cache-wrapped codec. An equal value then reuses
  its first encoding exactly as before; for example, the order of a
  `tooltip_display` hidden set comes from the first equal value encoded.
- **Leaf parsers:** selector patterns and untrusted URIs are checked by
  Java's own parsers, which Rust calls back while decoding (`schema::Leaf`).

## Enchantment effects

- **Native:** the component map, every effect type and their records, with
  the projection in
  [`NativeEnchantmentEffects`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/item/enchantment/NativeEnchantmentEffects.java).
  The effect type registries hold `dispatchCase` entries, so the entity and
  location registries now hold distinct `MapCodec` objects for shared names.
- **Delegated leaves:** these still run their original Java codec while
  Rust decodes (`S::Java`, `NativeValues.JavaCodec`): requirements (loot
  conditions, validated against the component's context), damage types,
  attributes, game events, block state providers, block predicates,
  explosion particles, holder sets and sound events.
- **Data packs:** for a file Rust parsed, a leaf's JSON text is sent to the
  callback; `NativeValues.dataCall` gives the decode a frame, and an
  exception the leaf throws leaves the load as that same exception.
- **Ordering:** a component map of eight or more entries iterates in identity
  hash order, as vanilla's did, so it differs between JVM runs. The
  differential compares such maps sorted.
- **Registry ops:** sound references need `RegistryOps`; the value FFI mode
  carries it (bit 1).
- **Verified (2026-10-15, at `186614ce` plus this change):** the `enchant`
  section matches Frozen (10,728 rows), and the full value fuzz matches
  pre-change (42,944 rows). Content and data fuzz differentials are
  unchanged. `worldgen_load`, which loads every enchantment, was within 10%
  of Frozen and level with pre-change (`TimeContent.py`, 9 pairs).

## DFU behavior Rust reproduces

These rules live in [`schema.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/data/schema.rs)
and were measured against DFU 8.0.16:

- **Error wording.** Messages are worded per `DynamicOps`:
  - `JsonOps` prints values as Gson does, e.g. `Not a JSON object: …`.
  - `NbtOps` prints SNBT with sorted keys. Its number and string errors
    carry no value.
- **Combined errors.** Record and list errors are joined last field first,
  separated by `; `.
- **Partial results.** A list keeps its decoded elements as a partial result.
  `Codec.either` returns the side that has a partial; otherwise it reports
  both messages.
  - A check (`validate`, `comapFlatMap`) runs on a partial value too, and
    appends its message.
  - A strict optional field with an invalid value leaves the absent value (or
    its default) as the partial. That includes a delegated default Java holds
    (`P::JavaDefault`, such as an explode effect's `block_particles`).
  - `NativeValues` returns partials to Java, so `resultOrPartial` callers see
    the same objects.
- **promotePartial.** `promotePartial` logs once, with the combined message.
- **Exceptions.** Decoding stops at the first exception, as Java's would.
  - If a Java constructor would throw (a weighted list's total weight
    overflows), Rust records the message and Java throws the same
    `IllegalArgumentException` or `NullPointerException`.
  - A Java codec or leaf parser that throws inside a callback leaves the
    decode as that same `Throwable`, of any class.
  - Once an exception is pending, no further callback runs. Each native call
    keeps its own record, so a callback that re-enters native decoding can't
    clear or replace the outer call's exception.
- **Number text.** Floats print as `Float.toString` (`java_float`).

## Checks

```bash
cargo test --manifest-path src/main/rust/Cargo.toml --lib content::data
./gradlew -PmattmcRustProfile=release test --tests '*NativeValuesTest'
JAVA_HOME=/path/to/jdk-25 python3 DevUtils/tests/content/VerifyContent.py --observer ValueFuzzReference --sections values --output build/values-frozen
JAVA_HOME=/path/to/jdk-25 python3 DevUtils/tests/content/VerifyContent.py --observer ValueFuzzReference --reference-repo ../pre-change --output build/values-pre
```

`--reference-repo` runs that checkout's compiled classes and
`build/rust/native` as they are; the drivers never build it. After checking
out a different commit there, rebuild it with
`./gradlew -PmattmcRustProfile=release classes`. A Gradle test task rebuilds
`build/rust/native` in the dev profile, so don't time a checkout after running
its tests without rebuilding.
**Integer wrapping:** debug builds check integer overflow and release
  builds don't, so arithmetic that Java wraps must use `wrapping_*`, such as
  `Mth.floor` for an out-of-range color channel.

[`ValueFuzzReference`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/content/ValueFuzzReference.java)
compiles unchanged against both builds. It covers:

- Each codec and bounded form over generated inputs, with every path replaced
  or removed, decoded from JSON and NBT. Rows record full error messages,
  both encodings, and each partial value's encodings, not just its presence.
- The per-type case codecs.
- Encoding of Java-built values.
- Its `worldgen` section, which dumps every loaded worldgen element: features,
  carvers, biomes and enchantments embed these types.
- Its `enchant` section: every effect component and case over generated
  inputs, including `equipment_drops` restrictions, recursive `all_of`,
  invalid requirements and exceptions thrown by delegated leaves.

## Verification (2026-10-14)

- **Frozen (2026-10-15):** the `values` section has 30,463 identical rows,
  covering providers, particles, text and item stacks. Of those, 17,852 are
  live successes, 41,292 are matching errors or partials, and 1,601 are
  matching throws.
- **Frozen (2026-10-14):** 20,040 identical rows, before text and items.
- **Pre-change Current:** all sections are identical (21,477 rows), and
  `DataFuzzReference` is identical (37,418 rows).
- **Frozen worldgen:** 17 rows differ, because ore features name different
  replaceable tags (`replaceable/stone_ores` in Frozen). Pre-change Current
  has the same difference, so it predates this work.

## Performance (2026-10-15, matched Frozen)

`ValueTiming`, `ContainerTiming` and `ContentTiming` (in
`DevUtils/tests/content`) run with alternating fresh JVMs on the release
native library, through `TimeContent.py`. All figures are milliseconds,
medians, from the candidate on master 4a45b79c.

| Workload | Frozen | Current | Change |
|---|---|---|---|
| Stack decode, cold | 59.6 | 29.9 | −50% |
| Stack decode, warm | 3.93 | 3.73 | −5% |
| Text decode, cold | 38.9 | 13.3 | −66% |
| Text decode, warm | 2.75 | 3.24 | +18% |
| Stack encode, first use | 69.5 | 65.3 | −6% |
| Stack encode, second pass (pre-JIT) | 8.63 | 13.16 | +52% |
| **Stack encode, warm** | 1.71 | 2.61 | **+53%** |
| **Container save, warm** (400 chests) | 6.81 | 12.37 | **+82%** |
| Worldgen registry load, cold | 482.6 | 501.5 | +4% |

- **Over budget, under an approved exception:** warm stack encode and
  container saves exceed the 25% boundary allowance.
  - The owner approved a narrow temporary exception for exactly this
    encoding and save cost on 2026-10-15 (swarm board seq 55 and 58). The
    approved container figure is 13.3 ms for 7,200 stacks; a faster Frozen
    run doesn't change it.
  - It doesn't relax correctness or any other budget, and it doesn't cover
    any further regression.
- **Encode passes:** the first use is no slower than Frozen. The gap widens
  as Frozen's C2 compiles its codecs (about 1.2 ms per pass, against 2.3 ms
  here), so the early-pass excess is the same per-stack boundary cost.
- **Worldgen load** is noisy run to run (+4% to +14% against Frozen across
  final runs, against the previous build's +8%). With C1-only compilation the
  gap to the previous build is about 3–5%.
- **Where the cost is:** the per-stack boundary. Java describes the stack,
  makes the downcall, Rust re-reads the description, and Java rebuilds the
  NBT tags.

  | Container save (`ContainerTiming`) | Frozen | Current |
  |---|---|---|
  | Java-built stacks | 6.81 | 12.37 |
  | Loaded from NBT | 6.80 | 12.30 |
  | Without component patches | 3.17 | 6.55 |

- **What didn't fix it** (measured, not shipped):
  - A bulk encode with one call per container saved about 15%.
  - Retaining decoded patch state can't help, because most patches are
    empty or text.
- **Remedy:** Rust-owned stack and component state, so saves read Rust state
  directly, and eventually Rust-written saves. Before anything changes, the
  operation-trace harness `ComponentMapTrace` (in `DevUtils/tests/content`)
  has to pass for the native patch map. Its 9,600 trace rows currently match
  Frozen. One structural write path it found: `entrySet().iterator().remove()`
  on an exposed patch.

## Known differences

- **NBT array types:** value trees don't carry them. An `IntArrayTag` input
  reads as a list, which only changes how an error message prints the value.
- **Sibling lists:** decoded text components keep their siblings in a mutable
  list. DFU's record form gave an immutable one, so a caller that appended to
  a decoded component would have failed there.
