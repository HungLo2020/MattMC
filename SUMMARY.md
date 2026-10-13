# Rust migration performance (2026-10-13)

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1289.30 / 1190.40 | 3.332 / 3.787 |
| Vanilla + DH | 814.90 / 684.80 | 3.826 / 6.050 |
| Shaders | 359.30 / 318.50 | 4.714 / 7.190 |
| Shaders + DH | 275.90 / 227.90 | 5.363 / 8.484 |
Native5e09c034: one6000-frame pair/mode passes scoped FPS/p99 floors. Inputs/JVM controls match; entity counts differ. Two settled pairs/three lifecycles pass,VUID0; hazy images structural only. Current peak RSS5.03–8.48GiB; sustained performance/memory acceptance remains open.
Rust2785/Java2133/Frozen7186 pass. No-source collection CPU34.80/43.28/28.51ms Current/prior consumer/Frozen per4096queries; Java heap0.098/6.52/18.87MB. Frozen CPU gaps remain. [Report](docs/development/world/entities/RUST-ENTITY-COLLECTION.md#screen-shake-source-selection). Broad Java migration remains unfinished.
