# Rust migration performance (2026-10-13)

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1331.20 / 1262.80 | 3.347 / 4.006 |
| Vanilla + DH | 794.20 / 663.60 | 4.334 / 7.003 |
| Shaders | 347.40 / 314.85 | 5.376 / 7.484 |
| Shaders + DH | 264.00 / 226.95 | 6.492 / 9.940 |
Vanilla/DH: combined native2ad8899a, one fresh pair each; scoped floors pass, not a sustained/isolated gain. Shader rows: historical nativea9286e05 medians. Startup RSS13.25GiB and long-memory concerns remain open.
Collision queries: CPU80.61/79.93ms Current/Frozen per65536queries; Java heap34.25/75.23MB (54.5% less). Frozen231212/Rust2759/Java2095, shader+DH settled pair and three lifecycles pass. [Report](docs/development/world/physics/RUST-BLOCK-COLLISIONS.md).
