# Rust migration performance (2026-10-13)

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1241.40 / 1095.00 | 3.522 / 4.213 |
| Vanilla + DH | 801.00 / 693.50 | 4.060 / 6.067 |
| Shaders | 354.90 / 300.00 | 5.356 / 7.122 |
| Shaders + DH | 278.90 / 227.50 | 5.769 / 8.859 |
Native a9928d75: one fresh 6000-frame pair per mode; scoped FPS/p99 floors pass, not an isolated/sustained gain. Inputs/JVM controls match; live entity counts vary slightly. Historical RSS13.25GiB and long-memory concerns remain open.
Typed world queries: empty groups23.37/25.16ms Current/Frozen; bounds misses35.00/34.22ms, all-hit63.71/35.88ms. Current Java heap0; all-hit/small-section CPU gaps remain. Rust2764/Java2102/Frozen6735, two settled pairs/three lifecycles pass. [Report](docs/development/world/entities/RUST-ENTITY-SECTION-INDEX.md#stock-typed-traversal-scoped-verification-2026-10-13).
