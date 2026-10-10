# Rust vs Frozen Java speed (2026-10-10; DH lighting/emitter candidate3f00217d atopdb49d8816; RTX3080Ti; rotating view; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,420.3 / 1,391.7 | 1,185.4 / 1,142.3 |
| Vanilla + DH | 763.2 / 722.8 / 835.0 / 736.0 | 792.5 / 696.8 / 651.8 / 833.4 |
| Shaders | 341.6 / 349.7 | 315.5 / 312.9 |
| Shaders + DH | 255.0 / 254.7 | 215.3 / 224.5 |
Author-recorded receipt: build/native-dh-lighting-migration/performance.json; all20 ABAB/exact6000 clean, all source/native/Frozen/protected-prompt guards and10 Current fingerprints pass. Two repeats/side/mode; DH has four, with no discarded runs.
Measured floors PASS: medianFPS C/F1406.0/1163.85 vanilla,749.6/744.65 DH,345.65/314.2 shaders,254.85/219.9 combined; medianp99ms3.117/3.454,4.142/5.161,4.856/6.320,6.127/9.685. Initial DH two-repeat FPS miss0.22% retained; predeclared extension includes all4. Narrow DH margin/repeat variance; no isolated gain or broad acceptance.
Published sky1b9b10339/fog3adbe6d5d/lightmaps971e0226b/face-policy+cacheaf6921cf5/DH heightsdb49d8816. Whole native DH lighting/emitter owner committed at bffd0eef8; author reports: 20 Frozen cases/60 passes plus cold neighborhoods, five JNI checks, Rust2483pass/3ignored, Java1843tests/2skip/0fail; all7 lifecycle and reviewed settled vanilla/Iris+DH pairs pass, VUID0, DH28.806%. Actual moving admission/all6 matching F3 endpoints verified; weightedJava1.108/4.448GB C/F excludes native allocation; legacyqueues36.70/34.60MB remain. ShortCurrentRSS4.05GiB only. Earlier teardown race, broad/temporal parity, long-memory behavior and complete Java removal remain open.
