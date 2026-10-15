# Chunk loading

- [Rust server chunk tasks](RUST-CHUNK-TASKS.md): native priority/FIFO ownership,
  callable boundaries, failure prefixes and actual Frozen queue replay.
- [Rust client chunk residency](RUST-CLIENT-RESIDENCY.md): native window and empty
  membership policy, retained counter scans, callback boundaries and Frozen replay.
- [Retained client chunk ranges](RUST-CLIENT-RANGES.md): direct block-entity and
  piston scan consumers, bounded pages and callback mutation checks.
- [Rust player chunk distances](RUST-PLAYER-DISTANCE.md): native-owned natural
  spawn and player-ticket distance graphs, replay contracts and verification.
- [Rust simulation distance](RUST-SIMULATION-DISTANCE.md): native-owned simulation
  ticket-distance graph, ticket-level mirror contracts and verification.
- [Rust loading distance](RUST-LOADING-DISTANCE.md): native-owned loading ticket
  graph, holder replay and failure contracts, and verification.
- [Rust chunk holder registry](RUST-CHUNK-HOLDERS.md): native-owned holder maps,
  scheduling, unload and eager-save decisions, off-thread lookups and verification.
- [Rust ticket storage](RUST-TICKET-STORAGE.md): native-owned ticket lists, expiry,
  forced chunks and persistence, direct tracker notification and verification.
- [Rust POI village distance](RUST-POI-DISTANCE.md): native-owned section
  distances to village centres, tag-reload resync and verification.
