# Rust migration performance (2026-10-13)

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1268.20 / 1246.60 | 3.530 / 3.720 |
| Vanilla + DH | 786.50 / 656.10 | 4.623 / 6.955 |
| Shaders | 343.40 / 305.30 | 6.288 / 8.177 |
| Shaders + DH | 284.20 / 220.90 | 5.246 / 9.764 |
Particle native6dc8870e: two clean6000-frame pairs for vanilla, one per other mode; all scoped FPS/p99 floors pass. Inputs/JVM controls match; live entity counts differ. Not an isolated gain or broad acceptance.
Rust2790/Java2141 pass; particle comparisons, pipelined/synchronous lifetimes and three lifecycle cases pass. Native capture/frame transport replaces Java records and ordering copies; small-batch CPU overhead remains. [Report](docs/development/rendering/RUST-PARTICLE-FRAMES.md). Full migration and long/GPU memory acceptance stay open.
