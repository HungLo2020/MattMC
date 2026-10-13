# Rust migration performance (2026-10-13)

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1161.50 / 1211.80 | 3.665 / 3.702 |
| Vanilla + DH | 762.80 / 576.60 | 3.856 / 7.267 |
| Shaders | 329.90 / 303.40 | 6.397 / 8.207 |
| Shaders + DH | 278.50 / 219.50 | 6.156 / 9.753 |
Native d5edf850: one fresh6000-frame pair/mode; vanilla FPS misses4.2%, other FPS/all p99 floors pass. Single pairs do not establish an isolated/sustained gain. Two completion modes/two settled pairs/three lifecycles pass,VUID0; historical RSS13.25GiB and long-memory concerns remain open.
Model frames: Rust2779/Java2121 and final focused Java24 pass. CPU0.838/0.770ms ordinary,1.235/0.987 variants vs prior Java per256 frames; Java heap falls68%/73%, CPU gaps remain. [Report](docs/development/rendering/RUST-MODEL-FRAMES.md). Broad Java migration remains unfinished.
