# Rust vs Frozen Java speed (2026-10-09; integrated b7297d06 atop81440bf19; RTX3080Ti; settled rotating view; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,358.8 / 1,297.1 | 1,149.6 / 1,193.5 |
| Vanilla + DH | 718.7 / 820.5 | 607.9 / 682.4 |
| Shaders | 354.7 / 340.1 | 313.6 / 316.4 |
| Shaders + DH | 259.2 / 258.7 | 227.1 / 227.2 |
Receipt: validation/native-light-map-integrated-20261009/summary.json; all16 ABAB/exact6000 clean, VUID/error/orphan0; both manually reviewed diagnostic pairs/DH coverage pass; source/library/Frozen/prompt guards pass. Raw gate6/7: expected shutdown close misclassified; all7 retained logs pass corrected classifier; fresh affected transition passes, integrity/native/cleanup guards pass.
Performance OPEN: medianFPS C/F vanilla1327.95/1171.55, DH769.6/645.15, shaders347.4/315.0, shaderDH258.95/227.15; p99ms vanilla3.277/3.097 FAIL, DH3.868/6.245, shaders4.590/6.411, shaderDH6.004/8.191 pass. Combined upstream+map changes; no isolated gain.
Published sky1b9b10339/fog3adbe6d5d; local Rust light maps/direct scalar consumers: freshRust2467/3ignored + Java1829/2skipped pass, six JNI workers exactb7297d06. Freshb729 four ordinary profiles/12F3 reviews pass; weightedJava allocation0.823/2.398GB C/F, mapcopy-path0sampled/94.37MB; excludes Rust, no isolated gain. Pop-in, long memory, simulation and Rust-only executable remain open.
