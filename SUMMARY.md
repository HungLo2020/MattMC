# Rust migration performance (2026-10-13)

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1289.30 / 1190.40 | 3.332 / 3.787 |
| Vanilla + DH | 814.90 / 684.80 | 3.826 / 6.050 |
| Shaders | 357.00 / 312.60 | 5.098 / 7.935 |
| Shaders + DH | 282.80 / 225.80 | 5.889 / 9.223 |
Shader rows: nativef29c8316 after upstream1490391f9, one clean6000-frame pair/mode; scoped FPS/p99 floors pass. Vanilla rows retain native5e09c034. Inputs/JVM controls match; entity counts differ. One settled pair/three lifecycles pass,VUID0; hazy gates structural only. Long/GPU memory acceptance stays open.
Rust2788/Java2137/Frozen108 bounds pass. Shadow capture CPU7–44% below prior transport; mixed-holder Java heap25–26% lower. [Report](docs/development/rendering/RUST-ENTITY-SHADOW-CANDIDATES.md). Broad Java migration remains unfinished.
