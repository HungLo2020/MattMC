# Rust migration performance (2026-10-12)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1322.30 / 1231.60 | 3.243 / 3.650 |
| Vanilla + DH | 796.85 / 873.55 | 3.363 / 4.368 |
| Shaders | 347.40 / 314.85 | 5.376 / 7.484 |
| Shaders + DH | 264.00 / 226.95 | 6.492 / 9.940 |
FPS rows describe published native a9286e05: DH misses Frozen by 8.8%; the 13.25 GiB startup RSS failure remains open. The conversion-policy candidate has no runtime/FPS acceptance.
DH full-source CPU Current/preceding/Frozen50.29/63.57/64.69ms; heap5.85/34.23/29.85MB. Rust policy/colors/scratch pass output/guard checks;22.3% faster Frozen in this CPU fixture. Rust2736/Java2058 pass (3ignored/2skipped); 7lifecycle/3settled pairs pass,VUID0; no new FPS acceptance. Integrated check/Wiki pass. [Report](docs/development/rendering/RUST-DH-CONVERSION.md).
