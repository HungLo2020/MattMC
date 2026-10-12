# Rust entity residency and persistence admission

Current source routes client ticking-chunk membership and server known UUIDs,
chunk visibility, load status and unload membership through the existing Rust
library. Initial client visibility, load admission, unload selection and
captured-status store decisions also come from Rust. Java retains gameplay
callbacks, persistence I/O, entity behavior objects, and the asynchronous
semantic loading inbox. This is manager state/admission ownership, not complete
entity simulation or persistence serialization.

Implementation: [residency state and policy](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/entity_sections/residency.rs),
[physical set/cursors](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/entity_sections/residency_set.rs),
[scalar boundary](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/entity_sections/residency_ffi.rs)
and [Java behavior views](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/entity/NativeEntityResidency.java).
Both production entity managers use this owner alongside the
[coordinate index](RUST-ENTITY-SECTION-INDEX.md) and
[membership arrays](RUST-ENTITY-SECTIONS.md). No new crate or GPU handle is used.

## Compatibility and boundaries

- Nullable UUID and all-zero UUID are distinct. Registration commits before
  position and behavior callbacks; getters/loggers run outside native locks.
- Visibility/load maps distinguish absent/default, present null and enum values.
  Internal Java enum views preserve existing interface call sites; they contain
  no Java key/value table. Their entry-export operation is deliberately not
  exposed: production save candidates come from the native loaded-key projection.
- Keep store decisions tied to the load status captured before virtual
  `shouldBeSaved` calls. Later reentrant changes cannot replace that captured
  status. Pending loads suppress enumeration; request admission commits before
  calling persistence I/O. Empty loaded chunks still emit an empty store.
- Preserve physical unload-set cursor behavior during callback mutation,
  collision shifts and wrapped removals, including committed failure prefixes.
  Do not replace this with snapshots or newly fail-fast iteration.
- Cursor leases retain their owner; scoped remove-if closes its registration
  on both success and exception. Abandoned cursor storage uses automatic arenas.
  Follow the existing world-thread mutation contract. Java owns the concurrent
  inbox until persistence/loading transport moves into Rust.
- Host/GPU resources and presentation remain owned by Rust/GAL. These are CPU
  world-state owners and bounded semantic/callback boundaries.

## Verification status

**October 12 verified checkpoint:** native `35b68379` passes eighteen focused controls,
including residency defaults/nulls, captured store decisions, collision/wrapped
set removal and clear-during-callback failure prefixes. The refreshed release build passes after getter/allocation cleanup. Actual
unchanged Frozen matches all 5,264 residency cases with source/class/native
guards, and thirteen selected Java integration tests pass. Full suites pass 2,703 Rust tests (three ignored) and 2,026 executed Java/parity
tests (two skipped). Refreshed index 6,721 and lookup 4,417 cases also match
Frozen with guards. Seven hardware lifecycle checks, two world comparisons and cow body/shadow
checks also pass, with zero VUIDs. All fourteen source originals were reviewed
and ten Current artifacts identify this exact native image. These are scoped
settled captures; first-frame, first-turn, sustained streaming and long-memory
acceptance remain open. All twenty matched performance runs finish cleanly and every FPS/p99 floor
passes, with identical effective heap/GC/JIT controls and final source/native/
Frozen/prompt/prepared-input guards. See the [complete matrix](../../rendering/GAMEPLAY-PERFORMANCE.md#local-entity-residency-checkpoint).
No isolated performance gain is attributed to this slice.

With JDK25, a built release library and unchanged Frozen:

```sh
./gradlew -PmattmcRustProfile=release buildRustNative classes
python3 DevUtils/tests/world/entity/VerifyEntityResidency.py \
  --java-home /path/to/jdk25 --output build/entity-residency-reference
CARGO_TARGET_DIR=build/rust/target-tests cargo test --profile suite \
  --manifest-path src/main/rust/Cargo.toml world::level::entity_sections \
  -- --test-threads=1
./gradlew -PmattmcRustProfile=release test -x testRustNative \
  --tests '*EntityResidencyIntegrationTest' --tests '*EntityLookupIntegrationTest'
```

The observer bootstraps both repositories and calls their actual managers,
including delayed load completion, persistence callbacks, unload reentry,
UUID/position failures, saving callbacks and client ticking transitions.
It compares full exception/event/state output without normalization and pins
source, compiled classes and the loaded native image. These CPU fixtures
supplement real gameplay, realistic timing and long-session memory checks.
Receipts belong under `build/native-entity-residency/`.
