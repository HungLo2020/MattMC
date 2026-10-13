# Rust particle capture and frames

**Verified migration batch, 2026-10-13. Scope: particle capture and frame
transport; particle simulation remains Java.**

Quad particle extraction now writes immutable scalar facts into bounded native
CPU buffers. Ordinary atlas layers transfer in bulk into the pending particle
stream. Terrain and custom-atlas producers append to that same stream. Rust
maps collection order to the material partition and encodes complete records;
the normal frame path no longer constructs particle records containing Java
arrays and then copies them again for ordering and encoding.

The owner is
[`render/particle_frame`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/particle_frame).
[`NativeParticleBuffer`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/bridge/NativeParticleBuffer.java)
provides producer writes and compatibility projections. Particle simulation,
camera interpolation, hooks, layer membership and texture admission remain
Java. Rust already owns billboard expansion and rendering policy; VulkanicGAL
continues to own resources, synchronization and presentation.

## Lifetime and ordering

- Extraction admits at most 65,536 particles per group. Pending particles
  share the existing 262,144-entry combined queue bound with material quads;
  final submission independently enforces its stricter 65,536-entry bound.
- Capture publishes its count after a complete slot write. Native layer
  admission validates the complete layer before appending it. Group failure
  rolls back the pending suffix, preserving earlier streams.
- Frame consumption makes an independent native snapshot. Producer clear,
  growth or rollback cannot change that snapshot. This bounded CPU copy avoids
  retaining a mutable extraction buffer through frame submission.
- Frame completion, abort and shutdown release snapshots through the existing
  completion path. Failed frame construction releases its snapshot. Unreachable
  extraction layers also have scope cleanup; cleanup actions retain no wrapper.
- Particle insertion indices retain order around ordinary material quads.
  Rust maps indices to the bridge's modulated-then-compact material partition
  without mutating the original snapshot. Invalid order fails before output.
- Custom atlas eligibility and staging retain their scalar Java callback path.
  API callers and diagnostics may request Java records; normal submission uses
  native records directly. Signed sizes and reversed UVs remain valid inputs.

## Verification

Use JDK 25 and rebuild Java and the native library together. The whole-frame
particle ABI remains the existing 72-byte semantic record; new owner functions
do not carry GPU handles.

```sh
./gradlew -PmattmcRustProfile=release test --tests '*Particle*'
CARGO_TARGET_DIR=build/rust/target cargo test --locked \
  --manifest-path src/main/rust/Cargo.toml --profile suite --lib \
  particle_frame -- --test-threads=1
```

The focused run passes 49 Java tests, including completion-failure cleanup,
and two Rust tests. The controls cover native growth, immutable snapshots, closed views, layer
rejection, combined capacity, rollback and exact encoding against the preceding
semantic encoder. They are supplemental correctness checks; the runtime evidence below
covers actual Frozen captures, gameplay and lifecycle transitions.

Fresh paired 30-second gameplay JFRs at `0d23141bd` are retained in
`build/native-next-systems-profile/paired/`. Current particle enqueue, order
mapping and encoding stacks carry approximately 91, 107 and 85 MB of sampled
allocation weight respectively. These inclusive weights overlap; they are not
rates or isolated migration gains. Both clients completed and native/source
guards passed. These diagnostic recordings are not performance acceptance.

The initial focused run caught an earlier-than-existing pending-queue capacity
rejection; the corrected bound passes the existing collection test. The first
benchmark attempt was rejected for measured JIT activity. A longer-warmup run
passes field checks but omits the prior producer's second validation pass; its
performance numbers are diagnostic only. The corrected control passes all
field/checksum and source/class/native guards. Its medians per 128 frames are:

| Particle count / path | Current / prior Java CPU ms | Current / prior Java heap bytes |
| --- | --- | --- |
| 64 / bulk | 1.081 / 0.908 | 88,064 / 7,413,760 |
| 64 / scalar | 0.913 / 0.770 | 591,872 / 7,413,760 |
| 1,024 / bulk | 5.457 / 15.171 | 88,064 / 118,005,760 |
| 1,024 / scalar | 3.182 / 13.085 | 8,471,552 / 118,005,760 |

These are internal transport measurements, not Frozen gameplay acceptance.
Small batches regress 18–19% in the final run (about 1.1–1.4
microseconds per frame). A direct-offset writer experiment did not reduce
allocation and slowed the bulk case; it was reverted. The remaining scalar
64-byte-per-particle allocation comes from the existing Java validation array,
not the memory view. `measurement-4/` retains that failed experiment.

The full check passes 2,790 Rust tests with three ignored and 2,141 Java tests
with two skipped on the final restored writer (native `6dc8870e`). The first full
Java run failed because a shadow-isolation fixture inserted untyped object
sentinels into the native queue. It now inserts real particles and checks that
shadow-only data cannot replace camera data. Final test receipt: `final-check.json`.
All attempts remain in `build/native-particle-frame/`; the retained writer's
transport receipt is `measurement-5/results.json`. Three interleaved JVMs per
side/case use 256 warmup samples and eight measured samples each; field checks,
source/class/native guards and measured-JIT quietness pass.

Reproduce the transport comparison after rebuilding:

```sh
python3 DevUtils/tests/rendering/VerifyParticleFrames.py \
  --java-home /absolute/path/to/jdk-25 \
  --output build/particle-frame-new-attempt
```

Two completed profile world copies were retired. Verified lossless event-JSON
compression reclaimed 624,190,903 bytes; original JFRs remain.

## Runtime evidence

Native `6dc8870e`, after documentation-only upstream `82df923b4`: two clean
6,000-frame rotating-camera Current/Frozen pairs for vanilla, one per other
mode. The table reports medians across all retained pairs. Saved inputs,
camera/settings/pack and observed JVM controls match; live entity counts differ.
These are scoped measurements with the minimap hidden, not an isolated gain,
sustained streaming evaluation or broad parity acceptance.

| Mode | Current / Frozen FPS | Current / Frozen p99 ms | Live entities |
| --- | --- | --- | --- |
| Vanilla | 1268.2 / 1246.6 | 3.530 / 3.720 | See run receipts |
| Shaders | 343.4 / 305.3 | 6.288 / 8.177 | 188 / 176 |
| DH | 786.5 / 656.1 | 4.623 / 6.955 | 165 / 167 |
| Shaders + DH | 284.2 / 220.9 | 5.246 / 9.764 | 165 / 164 |

All scoped FPS and p99 floors pass. The first vanilla pair was
1207.8/1222.4 FPS (1.2% below Frozen); the second was 1328.6/1270.8.
Both remain in the aggregate, without timing exclusions. Separate pipelined and synchronous 6,000-frame lifetime windows
complete cleanly. World unload/reload, resource reload and view-distance
decrease also pass. Receipts: `build/native-particle-frame/{runtime,gameplay-comparison}.json`.

The ordinary-atlas shriek pair passes: all 632 pixels selected by its delayed
control match exactly. The terrain-cutout hidden control is rejected before
pixel comparison: Current/Frozen constructor tint differs despite equal pose,
UVs, source pixels, lighting and blend radius. Both clients completed cleanly.
A read-only probe confirmed early construction retained plains tint `77ab2f`,
while the settled world supplied forest blend `59ae30`, matching Frozen.
Current fixture installation now waits for actual chunks across the blend and
quart-lookup neighborhood. Temporary logs are removed; the original failure
and probe remain in `tint-diagnostic/`. Corrected hidden/visible comparisons pass with matching constructor tint;
both clients change 12,317 pixels in the visible cutout region, including the
expected transparent holes. Frozen behavior and ordinary particle construction are unchanged.

Explicit visible-particle runs with pipelined ordinary frames and with
synchronous frames both pass the 632-pixel shriek comparison. Capture frames
themselves remain synchronous by design. The three lifecycle images are hazy
and establish structural transition behavior, not detailed scene parity.

First-turn terrain gaps/flicker, the first shader world frame, sustained
streaming and long host/GPU memory behavior remain open. Initial gameplay
peak RSS was not uniformly lower than Frozen; no total-memory improvement
is claimed. The Java simulation and remaining scalar validation allocation
are further migration work.

All 21 retained original images were manually reviewed, including the two
rejected cutout-control images. Twelve Current captures have matching Rust
presenter correlations, and 19 runtime artifacts identify the tested native
library. Source/class/native/Frozen and workload guards pass; the original
failed runtime receipt remains failed, with corrected follow-up results kept
separately. See `build/native-particle-frame/{final-integrity,image-review,
followup,repeat-vanilla,retention}.json` for the verification and retention records.
