# End feature computations

Spikes, gateways and the obsidian platform share ordered geometry in
[`decoration/end.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/decoration/end.rs).
This is an extension of the existing native decoration implementation, not a
new first port of those world-generation features.

## Working on the boundary

The existing decoration programs use native region storage for gateway and
platform writes. Direct feature calls and the platform helper use
[`NativeEndFeatures`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeEndFeatures.java)
and the End-specific synchronous effect bridge. Rust chooses geometry and the
next operation; Java performs actual reads, writes, destruction, entity creation
and block-entity changes on the calling thread. Live `ServerLevel` work must
keep those Java effects: raw section writes would omit gameplay callbacks.

The live executor can pass up to 32 independent write arguments together.
Java still executes every write in order, including refused writes and callback
failures. Flush before a read, iterator operation, crystal, gateway exit or
mutable-origin query; adjacent writes alone do not establish independence.
Mutable origins use the scalar route. Platform cells keep their real Java read,
call the shared pure Rust replacement decision, then perform Java destruction
and writing with the same cursor. Its critical downcall must remain free of
allocation, callbacks, locks and owner access.

Spike programs invoke the configured feature through the existing Java-leaf
adapter, then enter the native End executor. Inside an admitted decoration
call, it reuses that call's native generation storage. Each iterator/crystal
callback uses the existing metadata/event synchronizer; ordinary body and cage
writes avoid a Java round trip per block. Java supplies the original list
iterator for each call, resolving the seed cache only when the configured list
is empty. Do not cache copied descriptors: `SpikeConfiguration` retains a
mutable list, and subsequent calls must observe its changes. The descriptor
cache and its public list objects remain Java-owned.

Preserve these ordering constraints:

- Each spike's body and cage precede its crystal. Java creates the crystal,
  draws yaw from the actual caller random only when creation succeeds, adds
  the entity, then places bedrock and context-dependent fire.
- Gateway exit mutation runs immediately after the centre gateway write,
  before the remaining half of the frame. An absent configured exit does not
  query a block entity. Advance the retained mutable frame cursor before
  passing the immutable centre copy to Java; write observers can retain the
  previous frame position and inspect it during this centre write.
- Platform positions run Z, X, then Y. Compare block identity; only a mismatch
  destroys (when requested) and writes. Retain the same mutable cursor through
  the read/destroy/write sequence, including changes made by a callback.
- Exceptions escape with their original identity and completed effect prefix.
  Each reentrant call has independent state; no native lock or borrowed owner guard
  may survive a Java effect callback.

Custom level/context/feature types keep their Java implementation. Custom,
iterator-overflowing and short spike descriptors use the original per-spike Java
method. For heights at most 55, the body iterator ends in skipped cells: an
observer retaining its mutable write cursor must still see those final positions.
Negative radii use Java's normalized `betweenClosed` bounds. Preserve Java
integer arithmetic and fallback before replacing an unsupported operation.
The dragon fight, respawn animation, gateway teleport policy and portal entry
policy remain Java; native feature calls do not transfer those state machines.

## Verify

Use the bundled Java 25 installation, not a system Java 21:

```sh
JAVA_HOME="$PWD/libraries/jdk/linux-x64" PATH="$PWD/libraries/jdk/linux-x64/bin:$PATH" \
  ./gradlew test -PmattmcRustProfile=release \
  --tests net.minecraft.world.level.levelgen.NativeEndFeaturesTest
python3 DevUtils/tests/worldgen/VerifyEndFeatures.py \
  --output build/end-features/<new-label>
```

The driver compiles the same fixture against untouched Frozen and Current,
runs real dedicated servers, and compares controlled proto-chunk neighborhoods
using actual End-level services. It fails on differences or missing output.
`comparison.json` records the verdict; `fixtures.json` retains exact rows.
Entity UUIDs are normalized. The live inventory-drop tape compares item type,
count and position, excluding the item's unseeded initial yaw; seeded crystal
yaw and the final caller random are compared. Reopen checks wait for real
entity loading and ignore only gateway age and fire changes during those ticks.
Completion requires actual nonzero block/entity/exit work, not just route
counters. The 31 fixtures include all ten seed-derived towers, negative-radius
and mutable-list cases, failures with completed prefixes, and live write
callbacks that change the cursor/world and reenter platform placement. Separate
End transactions check effect and native-write rollback followed by retry.
All production Java/Rust sources, compiled Java classes, native images and audit
inputs are sealed before and after each campaign; each Current process must map
the expected native image. `--skip-build` reuses existing artifacts and requires
separately verified source/build provenance.

For per-family timing, use alternating fresh JVMs with the same CPU set and
retain each round's samples and cold operation separately:

```sh
python3 DevUtils/tests/worldgen/VerifyEndFeatures.py \
  --output build/end-features/<timing-label> --skip-build --timing \
  --rounds 3 --warmup 1000 --samples 15 --iterations 50 --cpus 4-7
```

This measures three generation and four live workloads (including both
platform destruction modes), checking final state and RNG as well as timing.
Generation sides invoke equivalent cached placed features, including their
placement modifiers; a configured-feature-only control is a different workload.
Reset work stays outside the measured operation. `timing-summary.json` reports
each paired ratio and the applicable limit; inspect every workload and round.
Warmup requires both the requested count and three seconds, followed by a
500 ms quiet interval in the compilation counter. Every sample attempt records
compiler/GC observations; attempts with advancing compilation are retained but
excluded from accepted samples. Each sample resets the caller seed so variable
warmup/retry counts cannot change cross-build state/RNG comparisons. These
controls describe this fixture's measurements, not whole-server throughput.
Repeat with `--fresh-writes` to replace spike/gateway blocks with end stone
before every operation. Keep this result separate from steady-state calls over
existing geometry; neither workload substitutes for the other.

To retain a pre-change comparison, pass `--sides current,reference,current-java,frozen`
and `--reference-artifacts <sealed-directory>`. The reference manifest records
original class/native hashes. A targeted class overlay is accepted only while
every other production Java dependency still matches that base; after unrelated
Java integration, use a complete independently sealed runtime/classpath.

Use `--workloads worldgen-1 --profile-boundary` with timing for gateway boundary
attribution. It instruments region refresh, writable-state copying, publication
and upcalls. These times are inclusive and nested: do not add them together.
Instrumented output is diagnostic and cannot pass a performance budget. Keep it
separate from uninstrumented steady and fresh-write acceptance runs.

## Recorded migration debt (2026-10-15)

The integrated r5 steady run used base `dc84c628`, the release native image
`ab6344eca4c2feeb3921d92820a1eed85bb73473e10875cf3ad917ea191a7e10`,
and production source seal
`659595b5ac1dab56cb1d80a83e383190ad93a78e2d86d323bdd95a3aae0ed8cc`.
On an i7-12700K, CPUs 4–7, three alternating rounds used four sides,
1,000 operations plus three seconds of warmup and 15 samples of 50 operations.
State, caller RNG and artifact identities matched. Raw samples and separate cold
times remain in `build/end-features/s21-integrated-steady/timing-summary.json`
and each run's `timings.json`.

| Steady workload | Frozen median | Pre-change `683ab835` | Candidate | Paired candidate/Frozen |
| --- | ---: | ---: | ---: | ---: |
| Generation gateway | 5.065 µs | 13.483 µs | 12.491 µs | 2.288–2.777 |
| Live platform destruction | 303.751 µs | 373.821 µs | 385.658 µs | 1.246–1.289 |

These are **budget failures**, accepted only as bounded temporary migration debt
by [coordinator decision 205](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18956208).
Destruction is 3.2% slower than pre-change Current, about 11.8 µs extra; it is
not an improving workload. The other five steady workloads met their limits.
The separate fresh-write run used the same sealed build and settings:

| Fresh-write workload | Frozen median | Pre-change `683ab835` | Candidate | Paired candidate/Frozen |
| --- | ---: | ---: | ---: | ---: |
| Generation gateway | 16.863 µs | 21.983 µs | 19.559 µs | 1.152–1.186 |
| Live platform destruction | 314.368 µs | 377.656 µs | 381.804 µs | 1.187–1.228 |

[Decision 206](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18956403)
accepts the fresh gateway's measured debt separately. The other six fresh
workloads meet their limits. Raw fresh samples/cold times remain under
`build/end-features/s21-integrated-fresh/`. These historical measurements precede
the cursor repairs. Generation gateway and platform execution paths remain
unchanged; affected spike/live-gateway paths were measured again after repair.

| Affected workload | Steady paired candidate/Frozen | Fresh-write paired candidate/Frozen |
| --- | ---: | ---: |
| Generation spike | 0.523–0.530 | 0.531–0.553 |
| Live spike | 1.010–1.060 | 0.944–1.106 |
| Live gateway | 0.718–1.063 | 1.172 / 1.173 / **1.277** |

The final fresh live-gateway run has medians Frozen 56.891 µs, pre-change
Current 69.257 µs and candidate 67.735 µs. Its third round **fails the 25%
allowance**. [Decision 207](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18956933)
accepts this additional measured debt: the candidate is 2.2% faster than
pre-change Current, with a 10.844 µs gap to Frozen. It is not a budget pass.

Affected-path evidence is retained under `build/end-features/` in
`s21-repaired-spike-steady`, `s21-final-gateway-steady` and
`s21-final-spike-gateway-fresh`. The last two use production source seal
`f5b5a8a7ca9b6901b017a139a82f63fe1e3226e716262051061a5d8278b43804`
and compiled-main seal
`162eec732ea3958b596738e9f200321207d60c2171d01f460701dfeccb15a30c`;
the release native image above is unchanged. Cold samples remain separate;
the warmed-ratio limits do not certify cold-start latency.

The diagnostic gateway run measured about 2.11 µs in region refresh and 0.266 µs
in its nested writable-state copy per operation. That does not explain the whole
gap or establish that a footprint mask would remove it. S0021's desktop worker
owns this debt record; dot must scope the subsequent connected native
generation/storage/effect ownership work. Its exit criterion is matched
uninstrumented steady and fresh-write measurements within the applicable Frozen
allowance, with unchanged correctness and lifecycle coverage. Shared region/write
expansion is not part of this slice.

Correctness verification on 2026-10-15: both retained-cursor witnesses failed
against unchanged Java and untouched Frozen before repair. The repaired
`dc84c628` candidate passed 31 fixtures across six server processes, including
guarded/unguarded negative radii, exact null-descriptor messages, live effects,
transactions and reopen. Eleven focused End tests passed, including heights
55/56 and bounded failure prefixes for negative/extreme integer heights/radii.
All three final cage/platform/crystal mutations were detected; exact source and
all four executor classes were restored, then eleven positive tests executed
again.

After normal reconciliation onto `646986043`, all four executor classes and
the scoped End sources remained unchanged. Sealed timing/mutation evidence
above keeps its original build identities; it is reused for those unchanged
paths, rather than relabeled as measurements of the new whole-library image.
The combined release image is
`1cdd4792485ccec1e88c7ba8bcbc071c2fe2f6682a6651b182efc5a77a10ea9c`.
The rebuilt-image 31-fixture oracle passed again. The combined full check passed:
Rust 2,884 (nine ignored), ordinary Java 2,209 (two skipped), parity 186 and
slow 11. Its production source seal is
`c72a3f033e38fbe928213748ff22dd4f4796a7d7bd08561e0fa8a00ca974ce83`.

Final lifecycle evidence is retained under `build/full-generation/` with
prefix `s21-final-646-` and `build/generation-failure/s21-final-646`:

- FULL and CARVERS save/unload/resume each matched all 648 retained pre-change
  Current chunks across four dimensions. Overworld, End and Nether also matched
  Frozen; Primordial Caves retained its documented host-ore difference.
- Native and Java failure routes stopped the same chunk `(63,-47)`, restored
  all nine affected chunks identically, and left it at CARVERS in memory and
  absent on disk. The failure driver's acceptance and same-chunk identity were
  checked separately.
- Ticket cancellation returned holder counts from 760 to zero in both End and
  Overworld, with saved partial stages. The cancellation harness uses the
  existing agent's halt/status path and checks its markers and holder bound;
  it does not claim an exact pending-future count or equal asynchronous states.

These runs resealed production source, compiled classes, native image and audit
sources, and observed the expected image mapped in each Current server.
They do not certify later edits, rendered-client FPS or long-session play. See
[FULL generation verification](../RUST-FULL-GENERATION.md)
for the broader generation and transaction gates; a proto-chunk fixture is not
live gameplay coverage.
