# Rust migration performance (2026-10-12)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1273.00 / 1330.20 | 3.395 / 3.298 |
| Vanilla + DH | 796.85 / 873.55 | 3.363 / 4.368 |
| Shaders | 347.40 / 314.85 | 5.376 / 7.484 |
| Shaders + DH | 264.00 / 226.95 | 6.492 / 9.940 |
Vanilla: native971d56d8, one integration pair; FPS misses4.3%,p99 misses2.9%; no isolated gameplay gain. Other rows: historical nativea9286e05; DH misses8.8%. Startup RSS13.25GiB and long-memory concerns remain open.
Camera queries: CPU121.58/127.65ms Current/Frozen (4.8% less); Java heap0/319.29MB per1048576queries. native971d56d8 Frozen49152+4/Rust2750/Java2087 pass; earlier broader runtime checks retain d885 identity. [Report](docs/development/rendering/RUST-CAMERA-ENVIRONMENT.md).
