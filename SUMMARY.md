# Rust vs Frozen Java speed (2026-10-09; release bc2207fb; RTX3080Ti; settled rotating view;6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,379.7 / 1,094.1 | 1,152.2 / 1,182.9 |
| Vanilla + DH | 709.3 / 751.0 | 749.6 / 613.8 |
| Shaders | 342.0 / 344.6 | 317.3 / 316.4 |
| Shaders + DH | 255.1 / 251.8 | 228.1 / 227.1 |
Receipt:`validation/native-live-biomes-20261009/summary.json`:all16 ABAB/exact6000 clean,VUID/exception/orphan0;all7 lifecycle and reviewed diagnostic vanilla/Iris+DH compatibility pairs/DH coverage pass.Source/native/Frozen/user-edit integrity passes.
Performance OPEN:medianFPS exceeds Frozen in all4 modes;p99 Current/Frozen vanilla3.498/3.075ms,DH5.429/5.601,shaders5.024/6.317,shaderDH7.167/8.242.Gate FAILS vanilla p99;repeat variance,no isolated migration gain.
FullRust2454 pass/3 ignored;Java1812tests/2 skipped/0failures(test1714+parity98);6 executors mapbc2207fb;Wiki2494/43+13 driver checks pass.Live-biome owner/direct sky verified;Current CPU flight validated,native sky sampled13 times/Java sky-grid0.All4 profiles/12F3 reviews pass,4 copies retired;Java alloc0.974/2.319GB,sampledsky0/168MB (Rust excluded).Range arithmetic fixed viaactualFrozen12288case oracle;finalc3aa5fed suitesRust2455/3ignored+Java1812/2skip pass;revised-library7 lifecycle/newreviewed pairs pass,VUID/orphan0,DHcoverage pass;9 copies retired,allidentity guards pass.Ordinary visible-map/entry/pop-in and Rust-only app remain open.
