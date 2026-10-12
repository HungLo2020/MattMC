# Rust migration performance (2026-10-12)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1322.30 / 1231.60 | 3.243 / 3.650 |
| Vanilla + DH | 796.85 / 873.55 | 3.363 / 4.368 |
| Shaders | 347.40 / 314.85 | 5.376 / 7.484 |
| Shaders + DH | 264.00 / 226.95 | 6.492 / 9.940 |
FPS rows describe preceding native a9286e05: DH misses Frozen by 8.8%; the 13.25 GiB startup RSS failure remains open. Reader bbd8fc84 has no accepted new gameplay timing: the ordinary pair failed focus/observer checks.
Reader CPU Current/public-reader/Frozen: 56.59/57.20/46.14 ms; Java allocation 7.14/33.85/4.21 MB (78.9% below control). CPU remains 22.6% slower Frozen. All 51 Rust DH tests and 2,049 Java/parity tests pass (2 skipped), plus two lifetime gates/three settled pairs. [Report](docs/development/world/chunk/RUST-DH-CONVERSION-READERS.md).
