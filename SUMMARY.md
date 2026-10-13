# Rust migration performance (2026-10-13)

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1293.20 / 1345.50 | 3.321 / 3.636 |
| Vanilla + DH | 747.40 / 636.50 | 5.112 / 6.612 |
| Shaders | 357.40 / 317.90 | 4.403 / 7.356 |
| Shaders + DH | 284.30 / 225.40 | 5.869 / 9.948 |
Native de0fabcc: one fresh 6000-frame pair per mode; vanilla FPS misses3.9%, other FPS/all p99 floors pass. Single pairs do not establish an isolated/sustained gain. Two settled pairs/three lifecycles pass; inputs/JVM controls match, live entity counts differ. Historical RSS13.25GiB and long-memory concerns remain open.
Rust asset registry: Rust2775/Java2115/replay200000 pass. Admission CPU1.44/3.01ms stable,1.43/14.98 equivalent wrappers,4.24/4.20 churn vs previous Current; stable Java heap0, churn5.32/23.22MB. [Report](docs/development/rendering/RUST-ASSET-REGISTRY.md). Java producers and broad migration remain unfinished.
