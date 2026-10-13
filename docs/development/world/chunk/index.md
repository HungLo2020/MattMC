# Chunk storage

- [Rust DH conversion readers](RUST-DH-CONVERSION-READERS.md): scoped primary-column
  reads without persistent Java column wrappers during render conversion.

- [Rust DH saved-metadata codecs](RUST-DH-METADATA-CODEC.md): primary-plane imports,
  bounded IO, fixed payloads with variable logical extents and Frozen verification.

- [Rust DH saved-point codecs](RUST-DH-POINT-CODEC.md): direct primary-column
  decoding/encoding, bounded byte IO, corruption prefixes and lazy closed readers.

- [Rust DH source occlusion](RUST-DH-SOURCE-OCCLUSION.md): owner-based admission,
  detachment, ordered culling and sparse CPU reader projection.

- [Rust DH source metadata](RUST-DH-SOURCE-METADATA.md): authoritative generation,
  compression and uniformity planes, direct merge/parent consumers and list lifetimes.

- [Retained Rust DH source columns](RUST-DH-SOURCE-TABLE.md): primary column
  leases, source replacement and direct tint consumption without Java descriptors.

- [Rust DH change hashes](RUST-DH-CHANGE-HASH.md): retained-input sampling,
  cached field semantics, mutable emitter boundaries and actual Frozen replay.

- [Retained Rust chunk inputs](RUST-DH-CHUNK-INPUTS.md): shared authoritative
  block/counter/biome bindings, direct height/emitter/lighting/column/hash consumers
  and separate current runtime acceptance.

- [Rust DH material maps](RUST-DH-MATERIAL-MAPS.md): ordered native identity/index
  ownership, bulk remapping, duplicate/import policy and compatibility bindings.

- [Rust DH sky light](RUST-DH-SKY-LIGHT.md): data-source sky light baked in one
  Rust pass over the primary column table, with Java's exact visit order.

- [Rust DH source updates](RUST-DH-SOURCE-UPDATES.md): complete same-detail merge
  policy, direct recipient publication, borrowed inputs and compatibility gates.

- [Rust DH contributor transport](RUST-DH-CONTRIBUTORS.md): immutable footprint
  production, direct retained consumers and public copy compatibility.

- [Rust DH full-data columns](RUST-DH-FULL-DATA.md): retained native recipient
  points, parent reduction/metadata, ordered whole-source occlusion and compatibility constraints.

- [Rust DH columns](RUST-DH-COLUMNS.md): whole chunk-to-column producer, retained
  material IDs, CPU views, mutation compatibility and historical release evidence.

- [Rust DH height fields](RUST-DH-HEIGHTMAPS.md): direct live-section scans,
  intrinsic collision policy, bounded field reuse and release-scoped verification.

- [Rust ordered palette values](RUST-PALETTE-DISTINCT.md): first-occurrence biome
  and block scans, callback compatibility and complete caller verification.
- [Rust global palette loading](RUST-PALETTE-UNPACKING.md): saved-data repacking,
  exact codec compatibility and complete caller performance verification.
- [Direct saved-palette owners](RUST-SAVED-PALETTES.md): local/global block and
  biome imports into primary Rust storage, compatibility constraints and Frozen replay.
- [Retained Rust region tapes](RUST-REGION-TAPE.md): direct region-to-decoder CPU
  ownership, explicit bulk release, automatic wrapper lifetime, compatibility
  escapes and verification.
- [Rust decoded-section ownership](RUST-DECODED-SECTIONS.md): saved NBT directly
  to primary block, biome and light owners, decode scopes and real-chunk verification.
- [Rust palette resizing](RUST-PALETTE-RESIZE.md): bulk storage remapping during
  block palette growth, ownership constraints and focused verification.
- [Rust palette histograms](RUST-PALETTE-HISTOGRAM.md): ordered counting and
  compatibility recounts with historical helper parity/performance checks.
- [Rust palette packing](RUST-PALETTE-PACKING.md): block-section save packing,
  exact serialization compatibility, and focused performance verification.
- [Rust chunk section serialization](RUST-CHUNK-SECTIONS.md): saving a chunk's
  sections list as NBT tape in Rust, exact key order and the save path.

- [Rust loaded-section snapshots](RUST-SECTION-SNAPSHOTS.md): immutable native
  rebuild state, Java compatibility views, bulk halo reads and lifetime rules.

- [Native section reclamation](NATIVE-RECLAIMER.md): batched phantom-reference
  release of section, view, counter and contributor owners, referent rules and stats.

- [Rust live block sections](RUST-LIVE-SECTIONS.md): authoritative packed storage,
  palette growth, CPU views, bulk consumers and remaining compatibility work.
- [Live biome sections and color sampling](../biome/RUST-LIVE-BIOMES.md): admitted
  4³ palettes, loaded-client residency, direct native sky/fog consumers and
  generation-validated reuse.

- [Rust section counters](RUST-SECTION-COUNTERS.md): fused live mutations,
  direct recounts, section-local ownership and generation inputs.
