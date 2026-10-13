# Rust migration performance (2026-10-12)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1322.30 / 1231.60 | 3.243 / 3.650 |
| Vanilla + DH | 796.85 / 873.55 | 3.363 / 4.368 |
| Shaders | 347.40 / 314.85 | 5.376 / 7.484 |
| Shaders + DH | 264.00 / 226.95 | 6.492 / 9.940 |
FPS rows describe published native a9286e05: DH misses Frozen by 8.8%; the 13.25 GiB startup RSS failure remains open. New biome-query iteration: vanilla1320.6/1068.7FPS, p993.736/4.428ms; one pair, not new all-mode acceptance.
Live-biome selection CPU76.07/216.02ms Current/Frozen (64.8% faster, zero Java allocation); direct retained client reads. Frozen162701 cases, Rust2737/Java2060 pass (3ignored/2skipped); 2settled pairs/3lifecycles pass,VUID0. [Report](docs/development/world/biome/RUST-LIVE-BIOMES.md).
