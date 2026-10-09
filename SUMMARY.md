# Rust vs Frozen Java speed (2026-10-09; world-color SHA6d637127; RTX 3080 Ti; moving camera; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,033.0 / 1,097.4 | 1,109.8 / 1,111.8 |
| Vanilla + DH | 602.4 / 736.8 | 674.8 / 592.5 |
| Shaders | 329.8 / 323.7 | 308.0 / 307.2 |
| Shaders + DH | 241.3 / 242.8 | 222.3 / 219.4 |
Receipt: `validation/native-world-color-fields-20261009/summary.json` FAILED vanilla performance floors. All16 runs clean/exact6000,VUID0,exceptions0,owned orphans0;source/library/Frozen unchanged. Measured release predates final boundary/lifetime hardening;no isolated speedup claim.
Performance OPEN: vanilla-4.1%,p99 3.539/3.375ms; DH+5.7%,p99 4.935/6.518ms; shaders+6.2%,p99 6.441/6.620ms; shader+DH+9.6%,p99 7.487/8.511ms. Vanilla fails FPS/p99;other modes pass this comparison. DH repeats vary substantially;between-session differences do not establish causation.
Full Java1732(2 skips) before hardening;final Rust2405(3 ignores)/77 affected Java pass,Wiki2482/43. Seven lifecycle/reviewed pairs pass on measured release. SHA953ac5b4 reviewed proof/profile pass;final Java admission proof passes. Sampled tint159→101/8s,diagnostic only. Loaded-world ownership/Rust-only app unfinished.
