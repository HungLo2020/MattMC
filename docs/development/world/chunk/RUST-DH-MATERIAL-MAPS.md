# Rust DH material maps

Rust owns the ordered serial identities,
lookup index, duplicate/import policy, material remapping and wrapped hash of
admitted `FullDataPointIdMap` instances. Java keeps wrapper bindings for public
getters and persistence callbacks; its compatibility `idMap` stays empty on the
native path. Read-only producer maps keep their original binding readers without
a second Java list. Ordinary map-to-map merge and append consume existing native owners
in one call, without rebuilding strings or looking up every pair in Java.

Source reviewed at [`5de23173a`](https://github.com/HungLo2020/MattMC/commit/5de23173ae5b61fb17887589d4dff69a6b17fc77) on 2026-10-10; the release results below are
implementation-author reports, and this review did not rerun suites or clients.
See the [current CPU checkpoint](../../rendering/GOAL-5-STATUS.md#october-10-retained-world-and-dh-reduction-cpu-checkpoint)
for remaining runtime, performance and sustained-memory acceptance gates.

Implementation: [native map](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/id_map.rs),
[CPU bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhIdMap.java),
[source consumer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/fullData/FullDataPointIdMap.java).
This extends the [complete source merge](RUST-DH-SOURCE-UPDATES.md),
[parent/occlusion consumers](RUST-DH-FULL-DATA.md) and [native producer](RUST-DH-COLUMNS.md).
Source orchestration, metadata storage, contextual transformation and Java removal
remain unfinished.

## Preserve these contracts

- Exact stock wrapper classes have immutable serial identities and stock hashes.
  Rust consumes UTF16 identity facts, derives pair hashes and preserves collisions
  through full key equality. Do not use hashes as identities or normalize strings.
  Initial binding/import copies are still Java preparation; measure that cost.
- Deduplicating insert retains an existing index; explicit append preserves every
  slot and updates lookup to the last duplicate. Frozen deserialization preserves
  duplicate slots **without indexing them**. A later insert can therefore append
  an identity already present in imported slots. Keep this behavior.
- Native self-merge fixes the initial result extent, then publishes the successful
  additions. If imported slots cause the input to grow, Java executes the next add
  and throws the original array-bounds exception with that prefix preserved.
  Self-`addAll` keeps the original loop, which observes a growing input indefinitely;
  the verification corpus does not invoke this unbounded operation. Neither case
  is treated as permission to change Frozen behavior.
- Hashing preserves Frozen's repeated full-list hash fold and signed overflow.
  Rust composes the same affine transform by exponentiation rather than repeating
  a list scan. Existing zero-sentinel hash caching and mutation invalidation remain.
- Custom maps/pairs/wrappers keep their Java callbacks. Before switching to that
  route, restore only entries actually indexed by Rust; never index imported slots
  as a side effect of detachment. Once detached, stay on compatibility until clear.
  Persistence still invokes the original wrapper serialization/deserialization.
- Caller exclusion remains required: this map is not thread-safe. Pin both CPU
  owners through merge. Reserve Java binding capacity before native mutation and
  publish the completed native prefix before reporting allocation failure.
- Keys are local reference-counted immutable values; merging shares keys, not
  source owners or worlds. There is no global identity cache or GPU resource.
  Clearing/unloading releases bindings and owner leases through automatic arenas.
  Retained memory follows live maps; long-session acceptance is still required.

<a id="direct-intrinsic-material-consumer-local-work-in-progress"></a>

## Direct intrinsic material consumer

Identity entries now also retain the registered block-state ID from each stock
compatibility binding. Duplicate lookup keeps the selected original binding;
explicit append retains the incoming binding's state. Key equality and wrapped
hashes remain serial-based, independent of this semantic state fact.

[Native material policy](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/materials.rs)
reads those existing entries directly and derives opacity, air and fluid facts
from Rust's intrinsic catalog. Ordinary occlusion and parent reduction therefore
avoid another Java state-ID array and per-material wrapper scan. Outputs own
independent facts and borrow the map only during creation; clearing/replacing
that map must not invalidate an existing policy lease. Preserve caller exclusion.

Unsupported/custom maps keep the previous material admission/primitive handoff;
unknown state IDs decline before policy publication. Release `21991771` passes
all20 focused DH Rust checks and238 Rust world checks, plus the full Java run:
1,884 reported tests (two skipped), zero failures/errors. Four material-map JNI
controls and36 column/material controls (35 native plus bootstrap regression)
pass. Six full-task workers map the exact library; all39,459 Current/38,176 Frozen,
native and protected-prompt guards match. New controls compare direct/compatibility
facts, duplicate binding selection and source-clear/replacement lifetime; existing
actual Frozen corpora and ordinary saved-source parent consumers still pass.
Receipts: `build/native-dh-material-consumer/`.

The first verification-view compile rejected an implicit boxed-slice autoref;
an explicit borrowed facts view fixed it. The original rejection is retained.
This source does not inherit historical hardware acceptance below. Preparation,
additional per-entry state storage and real-world throughput remain unmeasured.

## Verification

Use JDK25 and the normal release configuration:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib id_map
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests net.minecraft.world.level.levelgen.NativeDhIdMapTest
python3 DevUtils/RunWiki.py check
```

The [Frozen recorder](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/chunk/GenerateFrozenDhIdMapOracle.java)
checks the compiled Frozen map identity and runs its actual operations over64
canonical serial pairs from the saved-column corpus. Strict immutable CPU wrapper
fixtures supply stock serial/hash facts; unexpected callbacks fail. DHI1 records
300 operations across three maps, ordered slots, remaps, hashes and exceptions.
Raw SHA256: `e220c48724a4611d3ae50e96662867510c6485d143e3d3b7d1ef9dc1f9d68549`.
The fixture is supplemental CPU evidence, not gameplay or saved-biome parity.

Earlier CPU checkpoint `c57a1a17` passes all three focused map Rust checks and237 Rust
world checks. Full Java reports1,883 tests (two skipped), zero failures/errors;
35 column/material controls include34 native consumers and one bootstrap
regression. Six full-task workers map the exact release; all39,459 Current/
38,176 Frozen source pins, native and protected-prompt guards match. The ordinary
saved-source consumer proves both Rust index ownership and read-only producer
binding reuse. Import/self-merge and custom detachment controls preserve exact
slots, hashes and exception prefixes. Receipts:
`build/native-dh-id-map/`. Original test syntax and recorder-bootstrap failures,
and the initial actual Frozen self-merge exception, are retained separately.
That checkpoint awaited driver restoration for hardware/full Rust graphics and
realistic performance/memory checks. The [later reported preflight](../../rendering/RENDER-VERIFICATION.md#check-the-hardware-driver-before-runtime-captures)
records driver recovery and full-suite results; new MattMC runtime/performance
and sustained-memory acceptance remain pending. No isolated speedup is claimed.
