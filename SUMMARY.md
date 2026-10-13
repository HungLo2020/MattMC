# Rust migration performance (2026-10-13)

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1269.90 / 1191.40 | 3.691 / 3.755 |
| Vanilla + DH | 808.60 / 621.80 | 3.405 / 6.030 |
| Shaders | 359.20 / 315.80 | 4.752 / 7.753 |
| Shaders + DH | 282.10 / 226.20 | 5.856 / 9.185 |
Native ec10875b: one fresh 6000-frame pair per mode; scoped floors pass, not an isolated/sustained gain. Inputs/JVM controls match; live entity counts vary slightly. Historical startup RSS13.25GiB and long-memory concerns remain open.
Rust mesh lifetime: stable-rig CPU21% lower and Java allocation0/31.46MB vs prior Java; changing rigs22% slower, scalar paths also slower. Rust2763/Java2100/replay200000, two settled pairs and three lifecycles pass. [Report](docs/development/rendering/RUST-MESH-LIFETIME.md).
