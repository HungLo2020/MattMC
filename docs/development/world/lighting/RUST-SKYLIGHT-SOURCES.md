# Rust skylight-source ownership

`ChunkSkyLightSources` is a VM facade over the packed source payload in
[`source_owner/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/lighting/source_owner/mod.rs).
Rust owns construction bounds, packed values, incremental updates, reconstruction,
scan cursors and the five-source snapshot consumed by lighting. Java supplies
chunk resources and performs original virtual reader, shape and position effects.
The stock lighting consumer reads the actual native source owners; its Java
array is a derived compatibility transport. See [light propagation](RUST-LIGHT-PROPAGATION.md).

## Working on this subsystem

- Preserve constructor accessor order, sentinel values, packed padding, error
  identity, committed prefixes and escaped mutable-position effects. Custom height
  accessors and readers must keep their original conditional calls and reentry.
- Use ordinary FFM for owner operations. Source locks and native borrows must end
  before Java effects. A pending fill/update cursor owns the actual source `Arc`,
  so it can outlive collection of the facade and its base handle.
- Canonical reconstruction uses the existing
  [scan kernel](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/lighting/skylight_sources/scan.rs)
  and registry-derived face table. Java supplies the known pure chunk's highest
  section and a synchronous lease on each primary live section owner. Rust reads
  that owner's actual state under its existing lock, without a VM word/palette
  export. Air sections need no word buffer; compatibility sections still export
  packed resources only when needed. Rust clears empty sources without a cursor or
  section scratch and otherwise begins at that highest section. Unsupported
  resources decline before publication and use the native virtual-effect cursor.
- Preserve face continuity across nonempty sections and reset it at air gaps.
  An open column gets the below-world sentinel. Do not publish an abandoned
  canonical scan or retain a borrowed VM address.
- Keep the `nativeRaw()` array's identity and outside writes. It is an escaped
  compatibility view: import writes before source operations and export commits
  before returning or invoking another Java effect. The Rust payload remains
  authoritative between these required synchronization points.
- The six additive methods are `nativeWordCount`, `nativeCopyTo`, `nativeCopyFrom`,
  `nativeSnapshot`, `nativeRestore` and `nativeMatches`. For every subclass they
  perform the literal original operation through its actual virtual `nativeRaw()`
  exactly once, including inherited overrides, without another `nativeBits()`
  effect. A short restore preserves the suffix; snapshots are independent.
- Section ownership still belongs to the chunk subsystem. Keep its existing
  `stageOwner` / `with_state_reader` APIs unchanged. A typed bridge lease pins the
  primary owner only through its synchronous scan; no lock, borrow or section
  reference survives into a VM effect or pending source publication. Preserve
  local/global palette admission and the zero-width single-value capture.
  Compatibility sections use their original accessors; keep them package-private. S33 owns `NativeDecorationRegion` and `DecorationTransaction`;
  their legacy word-array consumers are not migrated by this checkpoint.

The former Java reconstruction bridge is now
[test-only](https://github.com/HungLo2020/MattMC/blob/master/src/test/java/net/minecraft/world/level/chunk/NativeSkyLightSources.java).
The independent [Java test oracle](https://github.com/HungLo2020/MattMC/blob/master/src/test/java/net/minecraft/world/level/lighting/JavaSkyLightSources.java)
contains the original Frozen `7a4d181717dc0c7da9086f1a17687dfd522ce417` algorithm.
Its unused constant-bounds base owner is only a test wrapper; constructor effects
are checked separately against the actual Frozen runtime.

## Verify a change

```sh
./gradlew test --tests '*NativeSkyLightSourcesTest'
python3 DevUtils/tests/lighting/sources/VerifyNativeSkySources.py
```

The portable driver requires Linux `/proc` to verify the mapped native library.
It builds matching release inputs and compares public effects with checked-in
original Frozen traces. MattMC word-transport extensions use the complete
published `5e933129855fd5b82198204a1f093cb541127e04` literal-operation reference.
It also requires the real native bulk route. A second overlay makes VM section
word export and cursor fallback fail, requiring the primary-owner/air route on
the same 50 original Frozen fixtures. A Current-only scope observer compares the
actual direct scan with the independent Java oracle, closes the real section
arena, checks that source words remain valid, and requires closed-section reuse
to fail without changing the source. This uses no synthetic release counter.
The driver exercises actual lighting consumers,
and checks pending native ownership after collection within five seconds. A
test-only facade that omits the actual base release must fail that same observer
on its retained-owner assertion; neither overlay changes production files.
Use a fresh `--output build/...` directory for each run. `--skip-build` requires
an already matching release library; it does not skip Java compilation or guards.

The ten reconstruction JUnit cases retain the registered-state/shape catalog,
256 seeded fixtures, 125 all-state fixtures, 16 saved chunks, padding, incremental
updates, custom effects, malformed IDs, parallel readers and GC coverage. Their
expected producer uses the independent frozen Java implementation. Storage tests
use the supported escaped-array view rather than a private Java heightmap field.

The older `DevUtils/tests/lighting/VerifyRustSkyLightSources.py` checks the pinned
Java oracle and can run its strict reconstruction timing campaign. Its historical
results below do not certify current owner costs or complete lighting.

The direct-section handoff was verified on base `542dfd165`. Its matching
release `66ff7a5d327285c9dc68c3c5ff8465fcadc9c33c25e8bb0ecb6296efe3cd6355`
passed 378 focused JVM tests and 248 portable checks. These include 50 original
Frozen fixtures with both VM section export and cursor fallback made fatal,
and two Current-only actual section-arena close/reuse checks. The real omitted
base-release negative control still fails on its retained-owner assertion.
Eleven source tests and all 134 native lighting tests passed. The actual full
serial native result was **3,157 passed, 57 failed, nine ignored**; the same
historical names and bodies matched exactly, normalizing only printed OS thread
IDs. This is a raw failed suite with its existing exact host qualification.
Earlier broad Java receipts keep their earlier runtime identities; recorded
source/class/resource/dependency impacts accompany latest affected execution.
Fresh saved-light ordering and custody checks also passed. All four initial
selected save-byte comparisons matched, and 125 fresh chunks plus their own-save
reopen passed. The original 25 Primordial differences remain, so full Frozen
dimension parity is still false.

## Current ownership evidence and open costs

The isolated Source6 checkpoint used base `7619d2db`, paired Main7 Java and native
`48af78ca87f29ad5ae18be923f19c3642d9034d897664c1ffa875f45f4531508`:
196 focused JVM cases, eight native ownership tests and the ten independent-oracle
JUnit cases passed. Omitting the actual base release in a test overlay made the
same lifetime observer reject the retained native owner. These receipts certify
that prototype, rather than a later library.

The integrated `c298904f` plus source-owner candidate was separately verified with
release `e7508174bb4aab8fe48f03b14da0faee7c8be2a555b1fa84e34368e35f6d460c`:
365 focused tests and all 196 portable contracts passed, including the actual
release-leak control. Ordinary Java passed 2,738 tests with ten skips; parity
passed 193 and slow passed 11, with fresh reports and final input guards.
The full serial native run was **3,150 passed, 57 failed, nine ignored**; all
57 names and bodies matched the historical host failures exactly, normalizing
only printed OS thread IDs. Its eight source, seven worldgen and three critical
admission controls passed. That raw native run remains a failure, with its exact
historical qualification recorded separately.

Fresh item contracts, both 16-check hopper/save-reload runs, saved-light ordering,
save custody and 125 fresh chunks plus own-save reopen also passed on that
candidate. The original 25 Primordial differences remain; full Frozen dimension
parity is false. The equipment-digest reconciliation was separately rebuilt on `a438a03c` with
matching Main/native: 368 focused tests, the 196 source contracts, original public
null-equipment/remap/remove traces, item contracts and hopper/save-reload passed.
The later `58c31979` Gaussian reconciliation was again rebuilt with release
`b437739d9b9926376d4bf59ef3337774c787546fdb68bfed5d2921d8d5b50a2b`:
378 focused tests, the 196 source contracts, the same equipment observer, and seven
Gaussian contracts plus 36,504 state observations against Frozen/pre passed.
Its actual full serial native result was **3,154 passed, 57 failed, nine ignored**;
the same 57 historical names and bodies were unchanged, with only printed OS
thread IDs normalized. Eight source, seven worldgen, four new Gaussian and three
critical-admission controls passed. This remains a raw failed suite with an exact
historical qualification. Fresh item contracts, both hopper/save-reload runs,
saved-light ordering/custody and 125 fresh chunks plus own-save reopen passed on
this later pair too. The original 25 Primordial differences still prevent full
Frozen parity. Earlier broad Java scopes are not renamed as testing later images;
source/class/configuration/fixture impact records accompany the affected checks.

Resource debt remains. A short complete fill plus 256 public reads and a maximum
read measured empty **6.70×** and solid **6.96×** original Frozen, shapes **0.410×**,
and saved Overworld **1.030×**. Outputs, quiet samples and origins matched; this
screen failed performance acceptance. Java allocation per call was respectively
200, 496, 496 and 528 bytes. Earlier Source4 regressions remain in their original
receipts rather than becoming passing results.

A separate short real fill → engine source consumption → queue-drain screen
measured Source6 at 226.21 µs/3,040 Java bytes per cycle versus original Frozen
251.06 µs/210,064 bytes, with eight quiet samples each and matching work/output.
The first published-build control had only five quiet samples and is inconclusive.
One fresh control, with seven quiet samples, measured 274.70 µs/3,264 bytes;
Source6's unchanged sample was 0.823× that control. The initial log-prefix parser
failure is retained; its corrected assessment reads the same raw samples.
A fresh integrated `c298904f`/`e750` row, using the same observer and workload,
measured 231.16 µs/3,040 Java bytes with eight quiet samples, matching work/output,
verified class/native origins and no swap. Against the unchanged original Frozen
and published-build samples above, its ratios were 0.921 and 0.842. Those baseline
samples keep their original runtime identities; neither was retimed or relabeled.
The direct-section `542dfd165` candidate with release `66ff` separately measured
236.24 µs/2,696 Java bytes with eight quiet samples and the same complete workload
and output. Against the unchanged original Frozen and published-build samples,
its ratios were 0.941 and 0.860. No baseline was retimed; the earlier integrated
231.16 µs/3,040-byte row retains its earlier identity. These observations do not
isolate a latency improvement or close the standalone empty/solid cost debt.
These are intermediate workload observations, not FPS, native allocator/retirement
plateau, final performance or whole-lighting acceptance.

## Status

The results below are historical: they measured the original slice, when Java
built the descriptor table. The [block registry verification record](../../game-model/BLOCK-REGISTRY-VERIFICATION.md)
separately reports the author's later comparison against the slice's previous
code, with its own measurement limits; these original acceptance results were
not rerun for this documentation review.

Release acceptance passed on 2026-10-01: Ryzen 5 5600G, Linux x86_64,
OpenJDK 25.0.4.1. Ten Java tests and three Rust tests passed with zero differences.
The catalog covers all 31,809 registered states and all 2,256 pairs of original
vertical face objects (28 compact faces). Additional tests include 256 seeded
fixtures / 65,536 columns, 125 all-state fixtures and 64 saved-terrain comparisons.

All 12 timing cases passed in every independent JVM pair and their confidence
bounds. The smallest individual-pair reduction was **45.9%**, with a conservative
95% bound of **45.8%**. Saved-terrain median process timings per complete call:

| Dimension | Original Java | Rust including boundary | Less time |
|---|---:|---:|---:|
| Overworld | 43.02 µs | 10.28 µs | 76.1% |
| Nether | 4.23 µs | 2.09 µs | 50.5% |
| End | 22.15 µs | 4.84 µs | 78.1% |
| Primordial | 4.31 µs | 2.04 µs | 52.8% |

The initial general empty-section path was rejected for an empty-chunk regression.
The final bounded clear takes 85 ns versus 372 ns for Java, including the call
boundary (77.1% less time). Shape-heavy stress results are larger because the
truth table avoids repeated shape-union work; they are not normal terrain averages.

`build/skylight-sources-migration/acceptance/REPORT.md` gives all 12 workloads;
`results.json` preserves raw rounds, checksums and hashes. Final source/library
hashes matched the acceptance record. These are warmed measurements on this
machine and corpus; finite parity tests cannot enumerate every possible input.
