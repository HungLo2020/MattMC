# Rust vs Frozen Java speed (2026-10-11; DH visibility releaseebd3c9a3; RTX3080Ti;595.99.02; rotating view;6,000 measured frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,432.5 / 1,430.3 | 1,181.5 / 1,150.3 |
| Vanilla + DH | 809.3 / 781.1 / 871.1 / 718.5 | 610.7 / 745.7 / 722.3 / 763.8 |
| Shaders | 350.0 / 334.8 | 317.3 / 309.8 |
| Shaders + DH | 266.5 / 257.9 | 227.8 / 224.2 |
Receipt:build/native-dh-visibility/gameplay/performance.json;all20 predeclared alternating/exact6000 runs clean;360 settle/240 warmup;DH four repeats per side,others two. All runs retained;source/native/Frozen/protected-prompt/prepared-fixture guards and10 Current fingerprints pass.
Performance gate PASS:medianFPS C/F1431.4/1165.9,795.2/734.0,342.4/313.55,262.2/226.0;medianp99ms2.963/3.293,3.734/5.110,4.697/7.004,6.037/8.647 (vanilla/DH/shaders/combined). All eight floors pass. Settled rotating/hidden-minimap workload only;no isolated migration speedup or broad acceptance claim.
Migration active:Rust retains DH topology/lifetime/frustum traversal and admits directly;Java generation/simulation/network/platform/startup remain,Rust executable empty. Releaseebd3c9a3:Rust2626pass/3ignored;Java1978reported/2skips/0fail;1280 actual Frozen walks,7 lifecycle cases,2 reviewed settled Frozen pairs pass,VUID0. Warmed paired profiles pass/guarded:256 native collector samples,Java iterator0 (prior64);compiler activity remains. Previous milestoneee809bbab retained region-to-section tapes.
