# Rust vs Frozen Java speed (2026-10-09; releasef449557e;RTX3080Ti;settled rotating view;6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,390.1 / 1,059.3 | 1,178.1 / 1,140.1 |
| Vanilla + DH | 730.9 / 709.5 | 600.6 / 722.3 |
| Shaders | 349.3 / 341.1 | 319.0 / 316.4 |
| Shaders + DH | 255.1 / 255.8 | 228.0 / 225.7 |
Receipt:`validation/native-live-biome-fog-cache-20261009/summary.json`:all16 ABAB/exact6000 clean,VUID/exception/orphan0;7lifecycle and manually reviewed vanilla/Iris+DH diagnostic pairs/DHcoverage pass;current/Frozen/native/prompt integrity passes;25copies retired.
Performance OPEN:medianFPS exceeds Frozen in all4 modes;p99 Current/Frozen vanilla3.394/3.322ms,DH5.235/6.405,shaders4.773/6.196,shaderDH6.810/8.602.Gate FAILS vanilla p99;large repeat variance,noisolated migrationgain.
Native sky milestone1b9b10339 published.Fog+generation-validated Rust cache remainslocal:f449 fullRust2456/3ignored,Java1818/2skip,6JNIworkers exactlibrary.Four ordinary profiles/12F3 reviews pass;weightedJava allocation0.951/2.437GB C/F excludes Rust;mapcopy89MB guides nextmigration.Light-map candidate14Rust tests/4096 actualJava-native Frozen operations passes;not production.Entry/pop-in,longmemory,worldsimulation and Rust-only app remainopen.
