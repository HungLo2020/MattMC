# Rust migration performance (2026-10-13)

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1246.70 / 1213.60 | 3.474 / 3.935 |
| Vanilla + DH | 802.90 / 661.10 | 3.877 / 6.265 |
| Shaders | 360.10 / 319.10 | 4.225 / 7.146 |
| Shaders + DH | 280.30 / 227.90 | 5.742 / 8.583 |
Native6ba548a6: one fresh6000-frame pair/mode; all scoped FPS/p99 floors pass. Inputs/JVM controls match; live entity counts differ. Single pairs do not establish an isolated/sustained gain. Two settled pairs/three lifecycles pass,VUID0; hazy gates are structural only. Long-memory acceptance remains open.
Entity collection: Rust2783/Java2126/Frozen6886 pass. All-hit CPU41.84/67.65/26.31ms Current/prior consumer/Frozen; Java heap6.52/18.78/18.87MB per4096queries. Frozen CPU gaps remain. [Report](docs/development/world/entities/RUST-ENTITY-COLLECTION.md). Broad Java migration remains unfinished.
