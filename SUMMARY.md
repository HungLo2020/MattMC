# Rust vs Frozen Java speed (2026-10-09; release59171b74; RTX3080Ti; moving camera;6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,034.0 / 1,070.0 | 1,131.6 / 1,179.5 |
| Vanilla + DH | 597.6 / 729.3 | 705.1 / 818.8 |
| Shaders | 336.8 / 327.5 | 309.8 / 308.9 |
| Shaders + DH | 247.5 / 241.5 | 222.8 / 221.2 |
Receipt:`validation/native-section-counters-final-20261009/summary.json`:all16 ABAB/exact6000 clean,VUID/exception/orphan0;performance floors FAIL. Source/native/Frozen/protected-user-edit integrity passes;25 generated copies retired. No isolated transaction speedup proven.
Performance OPEN:vanilla−9.0%,p99 3.583/3.253ms;DH−12.9%,p99 4.695/4.630;shaders+7.4%,p99 5.910/6.622;shader+DH+10.1%,p99 7.422/8.282. DH repeats vary substantially;the prior shader+DH14.764ms tail is absent here but its cause remains unproven.
Rust2426(3 ignores),focused Java121/full Java1762(2 skips),all7 lifecycle cases,reviewed coast pairs andWiki2487/43 pass. Rust owns live section counters/fused writes/recounts/stage inputs and DH cloud preparation/direct pose consumption. Next:item-layer/scene preparation and world-state consumers;Rust-only app remains unfinished.
