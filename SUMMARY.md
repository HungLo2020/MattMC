# Rust vs Frozen Java speed (2026-10-09; stage-handoff SHAc7c95f4a; RTX 3080 Ti; moving camera; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,027.8 / 1,033.3 | 1,101.9 / 1,127.6 |
| Vanilla + DH | 615.2 / 610.6 | 731.2 / 614.0 |
| Shaders | 326.9 / 332.2 | 312.4 / 307.6 |
| Shaders + DH | 244.2 / 249.5 | 220.6 / 221.2 |
Receipt: `validation/native-stage-handoff-final-20261009/summary.json` FAILED performance floors. All16 ABAB/exact6000 runs clean,VUID0,exceptions0,orphans0;source/library/user-edit/Frozen integrity passes;25 copies retired. No isolated handoff speedup proven;Frozen DH repeats vary substantially.
Performance OPEN:vanilla−7.6%,p99 4.021/3.467ms;DH−8.9%,p99 6.649/5.731ms;shaders+6.3%,p99 6.223/6.820ms;shader+DH+11.7%,p99 7.151/9.051ms. Vanilla/DH fail both floors;shader modes pass both.
Rust2417(3 ignores),focused Java26,full Java1751(2 skips),all7 lifecycle cases,reviewed coast pairs andWiki2485/43 pass. Canonical NOISE/SURFACE/CARVERS transfer stays in Rust;JFR handoff Java allocation108→~1–3KB/chunk is diagnostic,not throughput acceptance. Per-frame producers,broader world orchestration andRust-only app remain unfinished.
