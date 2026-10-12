# Rust entity-section coordinates and spatial index

Current source routes production `EntitySectionStorage` through the existing
Rust library for its primary coordinate map, ordered key index, chunk relations
and spatial coordinate search. Both client and server section managers use
that storage. Java retains section behavior bindings, initial visibility
callbacks, and membership/visibility/consumer callbacks. The subsequent [residency owner](RUST-ENTITY-RESIDENCY.md) moves primary chunk
residency and persistence admission into Rust with scoped verification.
Section visibility transitions, behavior callbacks and persistence I/O remain
further migration work.

Implementation: [native owner](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/entity_sections/index_ffi.rs),
[physical map](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/entity_sections/section_map.rs),
[threaded ordered index](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/entity_sections/section_tree.rs)
and [behavior bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/entity/NativeEntitySectionIndex.java).
This complements [native section membership](RUST-ENTITY-SECTIONS.md).
There is no Java primary section map or sorted-key mirror, no per-query Java
candidate array, and no new crate or GPU handle crossing this boundary.

## Mutation and lifetime rules

- Preserve Frozen's captured hash insertion slot across the initial visibility
  callback. Reentrant mutation can replace/shrink the table or overwrite that
  slot. Preserve committed prefixes and failures; do not re-find the key.
- Position streams capture live threaded nodes, including removed prefetched
  nodes. They are neither immutable snapshots nor newly fail-fast iterators.
- Spatial traversal supplies both key and current binding in one native call;
  position streams preserve their separate live lookup behavior.
- Spatial searches leave the next raw node and future x-ranges unopened until
  the next call. A consumer may mutate future sections before traversal resumes.
- Native bindings are generational and retired promptly. Cursor leases retain
  the owner; explicit close removes their registrations. Automatic arenas
  release abandoned leases without a Java callback under the native mutex.
- Closed spatial cursors return to a small per-index pool (up to 8), and
  `mattmc_section_index_spatial_reopen` restarts one with new bounds, so entity
  queries allocate no lease or arena per search. Reopening an open lease fails.
  Nested searches take another pooled cursor or allocate one.
- Thread links use an arena with reachability-based retirement rather than
  owning reference cycles. Unreachable retired slots are reused. Follow the
  existing world-thread mutation contract; this does not add concurrent APIs.
- Java chunk sets are required API snapshots projected from native key order;
  they do not serve as a second primary coordinate map.

## Verification status and commands

Native `168dd924` passes thirteen focused controls,
including 20,000 randomized AVL mutations, physical hash resize/shrink,
callback slot capture, detached-node retention and owner/cursor lifetimes.
The expanded actual Frozen corpus matches all 6,721 cases with source/class/
native guards; manager lookup matches all 4,417 cases. Full suites pass 2,698
Rust tests (three ignored) and 2,022 executed Java/parity tests (two skipped).
The initial fixture failure omitted registry bootstrap; that failed attempt
and the preceding 6,693-case replay remain in `build/native-section-index/`.

The fixed-source controller passes seven lifecycle cases, two world comparisons
and cow body/shadow parity with zero VUIDs. Fourteen original images were
individually reviewed and ten Current native identities match. Small head
orientation/shading differences are outside the body/shadow fixture scope;
these checks do not accept broad animation, leash or HUD behavior. All twenty matched timing runs finish cleanly, with identical live heap/GC/
JIT controls and final source/native/input guards. Both shader modes pass;
vanilla and DH FPS miss by 0.75% and 6.41%, while every p99 floor passes.
Aggregate performance remains unaccepted; no isolated gain/regression cause
is claimed. See the [full matrix](../../rendering/GAMEPLAY-PERFORMANCE.md#local-section-coordinate-checkpoint).
These CPU and scoped settled-runtime checks do not establish broad runtime
parity, temporal behavior, performance or long-session memory acceptance. `SUMMARY.md` records this newer index
matrix and its open FPS misses. Candidate receipts:
`build/native-section-index/candidate/`.

Build with JDK25 and a release native library, then run:

```sh
./gradlew -PmattmcRustProfile=release buildRustNative classes
python3 DevUtils/tests/world/entity/VerifySectionIndex.py \
  --java-home /path/to/jdk25 --output build/section-index-reference
CARGO_TARGET_DIR=build/rust/target-tests cargo test --profile suite \
  --manifest-path src/main/rust/Cargo.toml world::level::entity_sections \
  -- --test-threads=1
```

The observer compiles separately against actual Current and unchanged Frozen
production classes. Its fixtures exercise mutation/query order, lazy streams,
removal/reinsertion/rotations after cursor creation, callback exceptions and
reentrant physical-map insertion. Source, compiled class and loaded native
identities must remain unchanged throughout replay. CPU replays supplement
real gameplay, performance and long-session resource checks.
