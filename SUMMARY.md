# Rust migration performance (2026-10-12)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1235.60 / 1206.95 | 3.606 / 3.487 |
| Vanilla + DH | 796.85 / 873.55 | 3.363 / 4.368 |
| Shaders | 347.40 / 314.85 | 5.376 / 7.484 |
| Shaders + DH | 264.00 / 226.95 | 6.492 / 9.940 |
Vanilla: nativea554bfbc, two pairs; FPS passes, p99 misses Frozen by3.4%. Other rows: historical nativea9286e05; DH misses8.8%. Startup RSS13.25GiB and long-memory concerns remain open.
World-query CPU34.94/21.88ms Current/Frozen (256 sections,no hits);38.9% faster than Java-traversal control. Rust2746/Java2070/Frozen6663+6721 pass;2image pairs/3lifecycles pass. [Report](docs/development/world/entities/RUST-ENTITY-SECTION-INDEX.md#world-queries).
