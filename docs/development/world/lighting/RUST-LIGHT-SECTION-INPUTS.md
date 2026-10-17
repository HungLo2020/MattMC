# Direct Rust light-section inputs

Silent bulk propagation captures block states directly from the primary
[Rust live section owner](../chunk/RUST-LIVE-SECTIONS.md). Java supplies a bounded
CPU-owner descriptor and a synchronous lifetime pin. Rust maps palette state IDs
to light types and captures packed words directly into the existing pass snapshot.
There is no intermediate packed-state callback payload on this route.
The [effectful block path](RUST-LIGHT-PROPAGATION.md#effectful-block-propagation-under-development)
captures selected live states separately; the snapshot rules below describe the
bulk route.

Implementation: [live packed reader](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/live/mod.rs),
[propagation consumer](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/lighting/propagation/mod.rs)
and [temporary bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeLightBlocks.java).

## Ownership and compatibility

- A generation is mutable: retaining it as an immutable snapshot would change
  the old pass behavior. Capture under the owner lock instead; retain only the
  resulting pass-owned values. No owner lock or borrow crosses another Java callback.
- A typed Java lease pins the current CPU owner through its synchronous native
  capture. The next block callback replaces it; pass completion/failure clears it.
- Preserve first-use snapshot timing, state-to-type mapping, uniform values,
  local/global palette widths, unchanged work counts and affected-section ordering.
  Zero-width aliases capture their shared single value once.
- Admission remains the existing exact standard section/container/native-owner
  route. Air-counter shortcuts, missing chunks, out-of-height reads and custom
  chunk/palette traversal retain their existing behavior. Unsupported light types
  still reject through the established input-journal/replay protocol.
- Legacy packed/full CPU payloads remain compatibility inputs. No GPU resources,
  renderer/presenter ownership or native GPU handles are involved.
- The native coordinator owns stock node policy; Java supplies contextual chunk
  callbacks. Effectful block propagation is being corrected separately; see the
  [current propagation work](RUST-LIGHT-PROPAGATION.md#effectful-block-propagation-under-development).

## Verify changes

With JDK25 and a release native library:

```sh
./gradlew -PmattmcRustProfile=release buildRustNative testClasses -x testRustNative
./gradlew -PmattmcRustProfile=release parityTest -x buildRustNative -x testRustNative --tests '*NativeLightPropagationTest' --tests '*NativeBlockLightSourcesTest'
JAVA_HOME=/path/to/jdk25 python3 DevUtils/tests/lighting/VerifyLightWork.py --output build/light-section-oracle
./gradlew testRustNative
python3 DevUtils/RunWiki.py check
```

The actual Frozen observer uses ordinary ProtoChunks and compares complete block/
sky light layers, copy-on-write publication, section states, notifications and
work counts after every pass. Existing production parity fixtures add36 saved
FULL terrain chunks, edits, multiple generated dimensions and compatibility paths.
Rust regressions compare direct inputs with a full projection across every palette
width in both engines and verify snapshot independence after owner mutation.

Run Java suites separately from capture cleanup. When splitting validation into
separate invocations, pin active proof folders with `.keep` before finishing
the next invocation: `RunValidation` automatically retains only the latest
success and failure. Retire those pins explicitly after preserving the required
originals and receipts. Subsystem timings use
`LightPropagationVerification` terrain/edits/nether cases with fixed CPU/JVM
controls and all samples retained; they supplement matched real gameplay.

## Verification results

Final Rust passes 2,725 tests (three ignored); Java/parity passes 2,043 (two
skipped). Actual unchanged Frozen matches 437,089 rows across 492 passes, with
source/native/class guards and six primary-owner descriptor probes.

Twelve serial JVM runs over complete production lighting workloads match
checksums and source/native/class/predecessor/corpus guards. Predecessor/current
medians: terrain 18.785/18.787 ms, edits 10.589/10.569 ms, Nether 6.878/6.874 ms.
Subsystem performance is effectively unchanged; no speedup is claimed. The
175-sample pre-change Java CPU recording did not localize this boundary's cost.

Seven lifecycle scenarios and vanilla/DH/Iris+DH settled pairs pass, VUID zero.
All 13 originals and ten Current native identities were reviewed. Seven heavily
hazy gate views establish structural evidence only. All twenty completed
6,000-frame timings pass every FPS/p99 floor in the measured scene; see the
[integrated results](../../rendering/GAMEPLAY-PERFORMANCE.md#direct-light-section-input-checkpoint).
These results do not isolate a whole-game gain or establish broad/temporal parity.

The first DH timing attempt failed the RSS guard at 12.95 GiB with zero measured
frames. It remains unroot-caused. The current game-only JFR completes at 9.02 GiB;
a published CPU-bridge control reaches 9.82 GiB but its DH shutdown rejection
makes it unclean. Both diagnostics remain. No normalized allocation reduction
or retained-memory bound is claimed; the failure stays outside timing medians.

The first separate gate proof was automatically retired when pairs finished.
The pre-timing guard caught the missing files. Review hashes/controller logs
remain; the replacement gate passes all seven scenarios with evidence pinned.
All replacement originals were reviewed before measurement. No code or workload
control changed during this recovery.

After clients exited, retention retired 21 completed generated copies and
reclaimed 2.29 GiB, preserving 1,548 selected evidence hashes. Originals, profiles,
per-frame diagnostics and failed/unknown scopes remain; post-cleanup guards pass.
Receipts: `build/native-light-inputs/`.
