# Rust vs Frozen Java speed (2026-10-09; live-section SHA526af413; RTX 3080 Ti; moving camera; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,072.4 / 1,032.0 | 1,079.4 / 1,137.0 |
| Vanilla + DH | 608.6 / 597.1 | 867.0 / 725.3 |
| Shaders | 324.3 / 337.5 | 307.7 / 308.0 |
| Shaders + DH | 238.4 / 243.4 | 210.2 / 221.9 |
Receipt: `validation/native-live-block-sections-alias-final-20261009/summary.json` FAILED performance floors. All 16 ABAB runs clean/exact6000,VUID0,exceptions0,owned orphans0; final source/library/Frozen integrity passes. No isolated storage speedup or DH regression cause proven.
Performance OPEN: vanilla−5.1%,p99 3.631/3.319ms;DH−24.3%,p99 6.786/4.189ms;shaders+7.5%,p99 5.512/6.855ms;shader+DH+11.5%,p99 7.771/9.435ms. Vanilla/DH fail both floors;shader modes pass both. Historical DH repeats vary substantially.
Final Rust2415(3 ignores),focused Java113,full Java1746(2 skips),seven lifecycle cases and reviewed coast pairs pass. Wiki2484/43 passes. Rust owns canonical live block palettes/mutation without a Java mirror or per-read FFI; stage/save/network projections, broader world orchestration and Rust-only app remain unfinished. Earlier flight/DH profiles are diagnostic only.
