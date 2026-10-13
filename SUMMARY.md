# Rust migration performance (2026-10-13)

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1280.50 / 1241.00 | 3.180 / 3.675 |
| Vanilla + DH | 815.40 / 684.40 | 3.793 / 6.812 |
| Shaders | 352.30 / 313.80 | 5.221 / 7.619 |
| Shaders + DH | 277.80 / 226.20 | 6.003 / 9.620 |
Native03026814: one6000-frame pair/mode passes all scoped FPS/p99 floors. Inputs/JVM controls match; live entity counts differ. Two settled pairs/three lifecycles pass,VUID0; hazy images structural only. Current peak RSS5.18–8.46GiB; sustained performance/memory acceptance remains open.
Rust2784/Java2130, Frozen24 generation/231212 collision/600 tick cases pass. Collision CPU83.32/79.52ms Current/Frozen; Java heap34.25/75.23MB per65536queries. CPU gap4.8%. [Report](docs/development/world/levelgen/RUST-STAGE-HANDOFF.md). Broad Java migration remains unfinished.
