# Rust migration performance (2026-10-13)

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1251.00 / 1181.10 | 3.690 / 3.706 |
| Vanilla + DH | 833.30 / 629.90 | 3.927 / 6.507 |
| Shaders | 341.60 / 319.30 | 5.103 / 7.014 |
| Shaders + DH | 277.40 / 226.10 | 5.398 / 9.295 |
Native e6a65319: one fresh 6000-frame pair per mode; scoped FPS/p99 floors pass, not an isolated/sustained gain. Two settled pairs/three lifecycles pass; inputs/JVM controls match, live entity counts vary slightly. Historical RSS13.25GiB and long-memory concerns remain open.
Rust random ticks: Frozen600/Rust2769/Java2110 pass. Sparse CPU1.31/1.73ms Current/Frozen; dense5.41/3.08ms remains slower (previous Current3.92ms). Sparse Java allocation1716B/7.08MB; behavior callbacks remain Java. [Report](docs/development/world/chunk/RUST-RANDOM-TICKS.md).
