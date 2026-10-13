# Rust migration performance (2026-10-12)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1318.95 / 1338.80 | 3.335 / 3.605 |
| Vanilla + DH | 796.85 / 873.55 | 3.363 / 4.368 |
| Shaders | 347.40 / 314.85 | 5.376 / 7.484 |
| Shaders + DH | 264.00 / 226.95 | 6.492 / 9.940 |
Vanilla: native066c6679, two pairs; FPS misses Frozen by1.5%,p99 passes. Other rows: historical nativea9286e05; DH misses8.8%. Startup RSS13.25GiB and long-memory concerns remain open.
Entity-query CPU15.57/6.70ms Current/Frozen (256 members,no hits);66.4% faster than the Java-scan control. Rust2743/Java2068/Frozen6403 pass;2image pairs/3lifecycles pass. [Report](docs/development/world/entities/RUST-ENTITY-SECTIONS.md).
