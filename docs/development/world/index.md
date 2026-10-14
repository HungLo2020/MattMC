# World systems

- [Entity world state](entities/index.md): retained position/bounds components,
  ordinary camera queries and compatibility verification.

- [World physics](physics/index.md): collision-grid Boolean joins, merged boxes,
  rotations/reflections and parity checks.
- [Chunk storage](chunk/index.md): ordered biome/block scans, palette loading,
  growth, histograms, save packing, native DH height fields and verification.
- [Chunk loading](chunk-loading/index.md): client residency, server chunk-task
  queues and native distance fields for player/loading/simulation tickets.
- [World Lighting](lighting/index.md): skylight-source reconstruction, live light
  layers, canonical map snapshots and scalar sampling, Rust propagation, DH
  lighting/emitter ownership and scoped verification.
- [Biomes](biome/index.md): climate selection, live native palettes, sky/fog
  sampling with generation-validated reuse, section color fields and scoped
  Java/native parity checks.
- [World Generation](levelgen/index.md): noise, density, surface evaluation,
  seed compatibility, and verification workflows.
- [World time and weather](time-weather/index.md): native clock, weather-cycle
  and sleep-threshold ownership, shared-random and derived-dimension contracts,
  and Frozen/pre-change differential verification.
