# World asset registry and rig admission

**Implemented with scoped verification (2026-10-13).**

Rust owns mesh/texture key membership, insertion order, reusable slots and the
bounded membership journal in
[`render/asset_registry`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/asset_registry).
Java retains an indexed list of immutable payload references for existing
producers and upload projection. It no longer maintains a second key map or
membership journal. The old `MembershipJournaledMap` implementation is removed;
the shared `MembershipJournalMark` is only a checkpoint value.

Stable model rigs keep a native dependency proof over these primary slots.
Unchanged dependencies bypass repeated Java asset/texture lookup and preflight
construction across frames. A relevant payload replacement, removal or slot
reuse invalidates the proof and resumes the existing ordered admission path.
Mutating unrelated assets does not invalidate it. Rust's existing
[mesh lifetime owner](RUST-MESH-LIFETIME.md) still records usage for every frame.

This is semantic CPU ownership. GPU allocation, upload, destruction, completion
and presentation remain under VulkanicGAL. Java still creates mesh/texture
payloads, animates models, projects uploads and constructs instance records.
The general asset validation policy and those producers remain migration work.

## Working constraints

- Hold the render producer lock throughout registry access, including iteration.
  Native functions never call Java. Header and ordered-slot views are borrowed
  CPU memory; the adapter refreshes the slot view after a growth or clear.
- Mesh keys are signed longs; texture keys are signed ints. Zero is representable
  in the registry, while the existing asset admission validates actual IDs.
  The private adapter accepts canonical numeric keys, not arbitrary Java objects
  or null keys. Null payloads remain distinct from absent membership.
- Replacing a value keeps insertion order. Removing and re-adding appends the
  key. Views are read-only; structural mutation invalidates iterators, while
  an existing-key payload replacement remains visible to later iteration.
- A checkpoint records the native epoch and journal position. Added-since
  selects keys whose **first** later membership event was addition, including
  keys removed again. It does not undo replacements of existing payloads.
  At 262,144 events the journal resets and earlier marks expire, as before.
- Payload revisions are separate from membership/iterator revisions. Proofs
  decline permanently when the payload revision saturates. Clear and slot reuse
  cannot revive an old proof. Java pins both registry owners for a proof's entire
  lifetime; the proof contains dependency metadata, not payload copies.
  After successful ordered admission, refresh its existing native storage.
  Failure to resolve every dependency leaves both halves unchanged. Publishing
  the exact same payload reference again does not invalidate its dependencies.
- Java payload references are cleared on removal. Native slots are reused and
  the existing asset budgets still govern admission. Arenas reclaim superseded
  proofs after collection; long-session host/GPU memory acceptance remains open.
- Changed dependencies must preserve ordered admission failure prefixes and
  producer rollback. A warm proof must never bypass validation after retirement.

## Evidence and verification

The paired 30-second profile at `2701a9725` records model-admission allocation
in boxed keys, temporary maps and membership checkpoints. For example,
`prepareModelRigLocked` has about 94 MB of sampled boxed-long allocation weight;
`ensureMeshAssetLocked` about 85 MB. These are diagnostic weights, not rates,
retained memory or proof of a speedup. Receipts:
`build/native-frame-state-profile/{targets.json,paired/}`.

Six focused Rust tests pass for layout/order, membership history, event bounds,
dependency replacement, slot reuse and saturation. The Java adapter and existing
texture/admission checks pass (18 tests), including cross-frame production
admission and retirement. Native `de0fabcc` passes the 200,000-operation internal
replay and source/class/native guards in `build/native-asset-registry/oracle-3/`.
Across three interleaved JVMs per side, warmed admission medians per 1,024
semantic frames are:

| Case | Current / previous Current CPU | Current / previous Current Java allocation |
| --- | --- | --- |
| Stable references | 1.44 / 3.01 ms | 0 / 17.30 MB |
| Equivalent distinct wrappers | 1.43 / 14.98 ms | 0 / 108.53 MB |
| Alternating texture wrappers | 4.24 / 4.20 ms | 5.32 / 23.22 MB |

All measured samples are compiler-quiet. Churn CPU is about 1% slower; this is
not a claim of improvement in that case or a total-memory bound. `oracle-1/`
retains the first candidate's 23% churn regression; retained proof refresh avoids
repeated proof allocation. `oracle-2/` retains a checksum observer failure after
the replay passed: class inspection accidentally initialized an unbootstrapped
renderer. The observer now reads the class without initialization.

Full `check` passes Rust 2,775 (3 ignored) and Java 2,115 (2 skipped).
One fresh 6,000-frame Current/Frozen OpenGL pair per mode completes cleanly:

| Mode | Current / Frozen FPS | Current / Frozen p99 ms |
| --- | --- | --- |
| Vanilla | 1293.20 / 1345.50 | 3.321 / 3.636 |
| Vanilla + DH | 747.40 / 636.50 | 5.112 / 6.612 |
| Shaders | 357.40 / 317.90 | 4.403 / 7.356 |
| Shaders + DH | 284.30 / 225.40 | 5.869 / 9.948 |

Vanilla FPS misses the Frozen floor by 3.9%; other FPS/all p99 floors pass.
These single pairs do not isolate a gameplay gain. Saved inputs and JVM controls
match; live entity counts differ (191/184, 167/165, 183/180 and 166/165 in table
order). The moving-camera workload hides the minimap.

Two settled image pairs and world unload/reload, resource reload and view-distance
decrease pass with no Vulkan validation errors. Seven originals, five presenter
correlations and nine loaded native identities were reviewed. The lifecycle
views have heavy blue haze and establish structural behavior only. Diagnostic
captures disable model rigs; they exercise the registry but do not establish
warm rig visual parity. Ordinary gameplay enables rigs; component tests also
exercise cross-frame admission. Transient defects and long-memory acceptance
remain open.

Source/class/native guards pass; Frozen remains clean at `7a4d181717`. Receipts
are under `build/native-asset-registry/`; originals are in
`artifacts/graphics-captures/validation/native-asset-registry-20261013/`.
Thirteen completed copied worlds were retired after clients exited; originals,
failure receipts and profile evidence remain.

```sh
# JDK25, release native library; keep builds/profilers out of CPU timing windows.
./gradlew -PmattmcRustProfile=release test --tests '*WorldAssetRegistryTest' \
  --tests '*WorldMeshTextureReplacementTest' --tests '*ModelRigLifetimeAdmissionTest'
CARGO_TARGET_DIR=build/rust/target cargo test --locked \
  --manifest-path src/main/rust/Cargo.toml --profile suite --lib \
  render::asset_registry -- --test-threads=1
python3 DevUtils/tests/rendering/VerifyAssetRegistry.py \
  --java-home /path/to/jdk25 --output build/asset-registry/replay --benchmark
```

The observer compares 200,000 registry operations with the previous Current
Java policy, and measures actual `prepareModelRigLocked` with pre-admitted
inert assets. Stable references, equivalent distinct wrappers and texture churn
are separate cases. The previous Current renderer at `372dcacbe` is an internal
causal control, **not** the Frozen correctness baseline. The fixture omits GPU
upload and uses inert payloads; real gameplay and original-image review remain
required through the [render verification tools](RENDER-VERIFICATION.md).
