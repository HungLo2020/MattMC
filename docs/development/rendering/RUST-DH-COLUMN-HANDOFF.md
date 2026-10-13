# Native DH column handoff

> **Current state (2026-10-13):** every build publishes through this native
> column; the subclass packet route and `recordOwnedRustSemanticBuiltColumn`
> were removed. `VerifyDhQuads`/`VerifyDhPackets` were retired; commands and
> results below are historical. See [Rust-only DH render build](RUST-DH-RENDER-BUILD.md).
> The body below preserves this earlier ownership and verification checkpoint;
> its Java compatibility routes and retired commands are historical unless the
> current build guide explicitly retains them.

Source reviewed at [`660d19e1`](https://github.com/HungLo2020/MattMC/commit/660d19e1b865bf6a2b481300de3aafc934e406b5). Suite totals, timings, lifecycle runs and image comparisons below are
implementation-author reports. This review inspected source and fixture
assertions; it did not rerun Java/Rust suites, clients, captures or benchmarks,
or independently inspect the untracked runtime receipts.

Ordinary DH container builds now keep final packet bytes in a scoped Rust
column build. Rust splits each of the four layers, retains aligned semantic
companions, computes output coverage and validates publication. The collector
moves those byte buffers directly into its retained column ledger. Ordinary
production no longer allocates Java vertex packets, constructs Java buffer
snapshots, rescans their vertices or stages their bytes back into Rust.

Java still supplies contextual dirt/shade results and collector lifecycle calls.
Material-provenance diagnostics request independent packet/companion copies;
water auditing requests a bounded stream/color sample projection. Subclasses
retain their existing packet-method overrides. This is CPU ownership only:
VulkanicGAL still owns every GPU resource, submission and presentation.

Implementation: [column build](https://github.com/HungLo2020/MattMC/blob/660d19e1b865bf6a2b481300de3aafc934e406b5/src/main/rust/render/dh_quads/column.rs),
[scoped Java lease](https://github.com/HungLo2020/MattMC/blob/660d19e1b865bf6a2b481300de3aafc934e406b5/src/main/java/com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/NativeDhBuiltColumn.java),
[ledger handoff](https://github.com/HungLo2020/MattMC/blob/660d19e1b865bf6a2b481300de3aafc934e406b5/src/main/rust/render/bridge/dh_collector.rs).

## Ownership and ordering

- Append opaque, transparent-side, transparent-up and water-up layers in order.
  Callback failures stop production before publication. Each layer preserves
  its independent immutable plan and original getter/audit event order.
- Native transport segments contain at most 16,384 quads. All three companion
  arrays follow the same splits and source-buffer indexes. Publication keeps
  the existing 512-segment column bound and material/normal validation.
  Encoding checks the actual output material/normal, including overlap-error
  substitution. Each quad's four vertices share these fields. The owned bytes
  have no mutable production alias, so admission uses these retained facts
  without scanning the packet again. Keep this invariant when changing encoding.
- Allocate the generation and read origin before publication validation, as
  the previous snapshot route did. The collector lock covers recording and
  owner acquisition. Existing equality/reuse, reset, retention and late-close
  behavior remains in the shared Rust ledger policy.
- Publication consumes the native payload once, before downstream ledger recording.
  A ledger failure cannot retry the same consumed build; this is not an
  error-atomic publication/rollback guarantee. Closing the build releases its
  companions and any unpublished bytes; published bytes belong to the ledger.
  Close via try-with-resources on success and failure. The Cleaner handles
  abandoned Java leases; reachability fences protect calls, with no promised
  collection deadline or global build cache.
- Requested Java projections are independent copies. They never own a GPU
  object, native rendering handle or another presenter.

## Verification

Use the build commands in [Rust DH packets](RUST-DH-PACKETS.md) and select
`*NativeDhBuiltColumnTest`, `*DistantHorizonsSemanticCollectorTest` and
`*LodRenderSectionLifecycleTest` for focused Java checks.
Include `*NativeDhColumnHandoffTest` to exercise the direct ledger route with
and without capture, provenance equality, generation reuse and owner retirement.

`VerifyDhPackets.java record-column PATH` executes untouched Frozen's four
packet methods on one continuous callback timeline. Run the identical compiled
driver with Current's classpath and `verify-column PATH`. Its saved
`frozen-dh-column-packets.bin.gz` fixture includes seven Frozen class hashes,
96 cases and five callback failure settings. It checks complete layer bytes and
failure prefixes through Current's public native-column producer. Separate
native and Java controls check split companions, source closure, single-use
payload transfer, invalid admission and bounded audit samples.

`VerifyDhQuads.py --mode native-column` (or `grass-native-column`) compares
complete CPU builds against Frozen using quad counts and final byte extents.
For an isolated Current before/after comparison, compile
`VerifyDhColumnPublication.java` with Current's classpath and the compiled
`VerifyDhQuads` driver. Its arguments are `SEED QUADS ROUNDS GRASS_BOOLEAN`.
It runs the actual container publication, future completion and owner release,
with capture disabled. Frozen has no Rust collector API; this second comparison
is against the preceding Current image, not a Frozen publication benchmark.

## Recorded CPU checkpoint

The author reports release `111c5922`, on `d42abf1c`, passing 2,611 Rust tests with three ignored
and 1,958 Java cases reported with two skipped, zero failures/errors (1,956
executed; 357 suite executions). All six Java workers map that exact release.
Public replay passes 1,920 packet checks and 480 complete column callback
sequences, including audit-enabled replay. Source, native, class, driver,
fixture, untouched Frozen and protected-prompt guards match (39,557 Current
and 38,176 Frozen source files). Wiki validation passes.

Six alternating pairs per workload, five 2,000-build warmups and 2,000 measured
builds per JVM, with 2,048 faces per build:

| Workload | Current ms | Frozen ms | Current/Frozen |
|---|---:|---:|---:|
| Smooth | 210.064 | 266.860 | 0.787 |
| Overlapping | 264.614 | 387.627 | 0.683 |
| Scattered | 265.082 | 668.460 | 0.397 |
| Smooth + grass callbacks | 226.006 | 287.424 | 0.786 |
| Overlapping + grass callbacks | 280.196 | 426.954 | 0.656 |
| Scattered + grass callbacks | 278.210 | 696.769 | 0.399 |

All Frozen scoped median gates and checksums pass. These count/byte-extent
workloads differ from the older packet-byte-sum timings; do not compare their
absolute times as a before/after result.

The separate complete-publication comparisons use the preceding packet image
`6ae38d7a`, the identical driver and the same six-pair/warmup protocol:

| Workload | Previous Current ms | New Current ms | New/previous |
|---|---:|---:|---:|
| Smooth | 205.701 | 210.870 | 1.025 |
| Overlapping | 262.683 | 263.822 | 1.004 |
| Scattered | 263.134 | 265.324 | 1.008 |
| Smooth + grass callbacks | 236.599 | 229.743 | 0.971 |
| Overlapping + grass callbacks | 290.387 | 280.367 | 0.965 |
| Scattered + grass callbacks | 289.141 | 283.465 | 0.980 |

Grass publication is 2.0–3.5% faster; the other cases remain 0.4–2.5% slower.
There is no overall isolated speedup claim. The initial implementation was
4.5% slower in scattered publication. Its CPU profile showed repeated native
vertex validation; validation is now fused into immutable encoding. Initial
build, timing and profiling receipts remain alongside the revised results.

Receipts: `build/native-dh-column-handoff/`; final comparisons use the `fused-`
prefix. Initial compilation collided with the existing world-column export;
new build exports use `mattmc_dh_built_column_*`. The failed build is retained.
The author reports fresh hardware verification passing seven Iris+DH lifecycle scenarios with
zero exceptions, panics, dependency violations or GAL validation failures.
Every case selects the RTX3080Ti and fingerprints this exact release. Settled
vanilla and Iris+DH Frozen pairs pass, with original 1280×720 images reviewed.
Mean RGB differences are `[0.151, 0.237, 0.219]` and `[3.644, 4.176, 3.869]`;
DH extension covers 19.39% of the image and passes its existing color gate.
Lighting, water and foliage differences remain. Runtime receipt:
`artifacts/graphics-captures/validation/native-dh-column-handoff-correctness-20261011/summary.json`.
A subsequent [20-run gameplay matrix](GAMEPLAY-PERFORMANCE.md#october-11-native-handoff-matrix)
is clean but fails vanilla p99 and DH FPS; both shader modes pass their measured
floors. Neither these settled checks nor that rotating workload establish
first-frame/temporal rendering, sustained streaming or long-session memory acceptance.

## Review boundaries

The [three build-owner tests](https://github.com/HungLo2020/MattMC/blob/660d19e1b865bf6a2b481300de3aafc934e406b5/src/test/java/com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/NativeDhBuiltColumnTest.java)
inspect recorded four-layer byte/callback replay, source-close independence and
split companions, and incomplete/invalid publication. The [two handoff tests](https://github.com/HungLo2020/MattMC/blob/660d19e1b865bf6a2b481300de3aafc934e406b5/src/test/java/net/vulkanic/world/NativeDhColumnHandoffTest.java)
compare explicit packets with native publication for one constructed column,
generation/provenance reuse and lease retirement, then exercise no-capture
publication and an empty build preserving an existing owner. These are CPU
controls, not real chunk streaming, asynchronous teardown or a GC deadline. Injected
callback failures use visits 0/1/3/11, so the recorded grass failures occur in
the first layer; later-layer partial-build cleanup follows inspected control
flow rather than a direct failure case in that corpus.

Java still allocates callback-event/result arrays and orchestrates collector
publication; capture/provenance requests copy packets and companions. Native
encoding also allocates final payloads. The eliminated ordinary Java packet
round trip therefore does not imply allocation-free or end-to-end zero-copy
rendering. The 16,384-quad split and 512-segment admission limit bound a column's
transport shape, not all simultaneous build/ledger/GPU memory. Water auditing
returns at most 16 distinct stream/color pairs per layer (up to 64 total), but
finding them can scan the retained vertices; output bounds do not imply
constant traversal cost.

The [column recorder](https://github.com/HungLo2020/MattMC/blob/660d19e1b865bf6a2b481300de3aafc934e406b5/DevUtils/tests/rendering/VerifyDhPackets.java)
checks seven embedded hash strings for length, not their correspondence to
the Frozen class files during replay. Its 480 sequences are 96 synthetic cases
with five failure settings, not 480 distinct worlds. Broader source/class/image
guards and reviewed 1280×720 captures remain author receipt claims. The fresh
reported lifecycle/image checks supersede the earlier unstarted hardware
checkpoint only for their stated release and scenarios; they do not establish
new FPS floors, temporal parity, streaming or long-session resource bounds.
