# Rust saved DH lighting

The ordinary DH generation reader borrows saved NBT nibble arrays once per
section. Rust determines skipped pairs, carried skylight, uniform compression
and section counter state, and publishes an immutable light owner. Generation
passes that owner directly to the existing lighting and column producers.
It no longer expands each section into 8,192 Java setters or carries two Java
light-storage maps.

Java still owns file-read orchestration and parsed NBT objects. The bridge
exposes bounded semantic CPU arrays; it does not pack a dense voxel projection.
Custom/malformed inputs retain the existing public `readLight` behavior and
diagnostics. General Java lighting/storage and startup remain migration work.

Implementation: [saved policy](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/lighting/dh/saved.rs),
[borrowed importer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhSavedLighting.java),
[generation handoff](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/common/wrappers/worldGeneration/chunkFileHandling/ChunkCompoundTagParser.java).

## Constraints

- Preserve Frozen's section **list order**, including its disregard of `Y`.
  A missing block or sky array skips the entire pair. A nonempty sky array
  enables full skylight for subsequent empty sky arrays; missing pairs do not
  change that state. Extra bytes after the first 2,048 are ignored.
- Empty arrays still allocate original section policy when the pair is present.
  Preserve signed per-level counters, constant state and allocation presence
  across later mutable copies, clears and edits. An all-zero value field alone
  does not prove original storage was empty.
- An imported field has **no recorded emitter enumeration**, distinct from a
  recorded empty enumeration. Hash/beacon consumers or the next native pass
  enumerate then-current raw palettes at their existing boundary. Import must
  not freeze an empty emitter cache or enumerate emitters early.
- Each call pins its heap nibble spans synchronously. Rust retains independent
  bytes and counters, with no Java source pointer or live chunk/world reference.
  The scoped importer releases on success, decline or exception. Fields keep
  existing automatic arena lifetimes; sustained memory behavior needs runtime
  verification. No global importer cache is added.
- Bound fields to 256 sections and input lists to 4,096 entries. Unsupported
  heights, tag subclasses, malformed lists and short arrays decline before
  publication. The compatibility reader supplies its original failure/log.
- Wrapper adoption requires existing canonical height/block admission. Other
  wrappers receive independent mutable compatibility stores. Public mutation
  still detaches native fields; never publish two mutable authorities.
- VulkanicGAL resources, submission and the presenter are unchanged.

## Verify

Use JDK 25 and the standard release profile:

```sh
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests '*NativeDhSavedLightingTest' --tests '*NativeDhLightingTest' --tests '*NativeDhColumnsTest'
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib world::level::lighting::dh:: -- --test-threads=1
python3 DevUtils/RunWiki.py check
```

`DevUtils/tests/world/lighting/VerifyDhSavedLighting.java record PATH` runs the
actual untouched Frozen reader. Its 64-case corpus pins seven compiled classes
and records every value, allocation, constant, counter and exception, plus
subsequent mutable clear/edit outcomes. Cases cover missing/empty/padded/mixed
arrays, nonzero and negative bounds, extra sections and malformed tags.
JNI replay separately asserts native admission, retained field independence,
deferred emitter enumeration and direct whole-column production.

The same compiled driver accepts `bench CALLS`: five equal warmups followed by
complete saved imports and sampled output consumption. Native output release
uses existing GC-managed arenas; this is CPU component evidence, not gameplay
FPS or long-session memory acceptance. Compare isolated alternating Frozen and
previous-Current runs, pin all sources/classes/native images, and retain every
repeat. Current development receipts: `build/native-dh-saved-light/`.

Compile the driver against the untouched Frozen runtime classpath. The Python
runner accepts newline-terminated classpath files and preserves its protocol,
all process logs and every measured repeat in a new output directory:

```sh
python3 DevUtils/tests/world/lighting/VerifyDhSavedLighting.py --java /path/to/jdk25/bin/java --driver /path/to/compiled-driver --current-classpath-file /path/to/current-classpath.txt --reference-classpath-file /path/to/frozen-classpath.txt --native-dir build/rust/native --reference-kind frozen --output build/saved-light-comparison --pairs 6 --calls 100
```

For a predecessor comparison, use `--reference-kind previous-current`, its
classpath file and `--reference-native-dir` pointing to its preserved library.
Never load the new library into the predecessor JVM. The runner verifies actual
mapped native images before accepting results; equal checksums are required.

## Current verification (2026-10-11)

Candidate `a15c0555` passes full Rust (2,618 passed, three ignored) and Java/JNI
(1,960 executed, two skipped) suites with zero failures. Seven observed native
consumers loaded that exact library. The 64-case Frozen replay, deferred-emitter
controls and 256-column integration all pass; source/class/library/Frozen and
protected-prompt guards match.

Six alternating pairs per reference, five equal warmups and 100 complete
24-section imports per measurement give median Current/Frozen **5.794/289.178 ms**
and Current/previous-Current **5.767/290.383 ms**. Both scoped median gates and
consumed-output checksums pass. The initial runner failed to parse Frozen's
logger-prefixed JSON; its successful process output and failure are retained.
Only the parser changed before rerunning the complete declared comparisons.

The fresh [20-run gameplay matrix](../../rendering/GAMEPLAY-PERFORMANCE.md#current-hardware-evidence-boundary)
is clean and passes all FPS floors plus three p99 floors. Its aggregate gate
fails because vanilla p99 is 3.365/3.337 ms Current/Frozen. Every declared repeat
is retained; no isolated importer FPS improvement is claimed. The clean real DH
CPU profile maps this library and samples native box production, but no importer
calls in that interval. It does not prove live saved-light admission.
Twenty timing world copies and the profile copy were pruned after their drivers
completed; nine correctness world copies were also pruned. Captures, input
manifests and result receipts remain.
This batch also archived 36 large JSON witnesses from older successful,
inactive validation runs, saving 3.31 GiB. Each archive was checked against the
original SHA-256 before removing the raw duplicate; images and preservation
markers remain. Manifest: `build/native-dh-saved-light/completed-json-archive.json`.
Restore an archived witness with `gzip -dk /path/to/witness.json.gz` before
running a historical consumer that requires the original JSON filename.
Seven lifecycle scenarios and both settled Frozen image pairs pass with zero
reported validation errors. Nine Current artifacts fingerprint this library; all
four original 1280×720 images were reviewed. Existing color/lighting differences
remain; these settled images show no new gaps. Receipt:
`build/native-dh-saved-light/hardware-review.json`. Streaming, first-frame, temporal
and sustained memory acceptance remain open.
