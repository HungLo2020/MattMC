# Rust migration performance (2026-10-13)

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1273.1 / 1205.4 | 3.508 / 3.910 |
| Vanilla + DH | 786.1 / 622.0 | 5.314 / 6.564 |
| Shaders | 350.2 / 307.7 | 10.358 / 7.998 |
| Shaders + DH | 276.4 / 223.2 | 13.331 / 9.910 |
Flat-item + incoming DH native e491d123: one clean6000-frame pair/mode; all FPS floors pass, both shader p99 floors miss. Saved inputs/JVM options match; live entity counts differ. Three lifecycle checks pass.
Rust2793/Java2138 pass; eight GUI cases accepted. Same-build retained/inline gameplay control shows no large regression; no isolated gameplay gain claimed. [Report](docs/development/rendering/RUST-FLAT-ITEM-FRAMES.md). Full migration remains open.
