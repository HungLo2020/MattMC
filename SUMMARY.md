# Rust vs Frozen Java speed (2026-10-10; native face-policy/shared-cache release30586383 tested atop1e060cb74; RTX3080Ti; rotating view; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,333.0 / 1,389.6 | 1,174.8 / 1,120.5 |
| Vanilla + DH | 867.5 / 698.1 | 620.3 / 727.6 |
| Shaders | 351.2 / 340.0 | 318.5 / 317.3 |
| Shaders + DH | 261.9 / 259.2 | 227.6 / 226.5 |
Receipt: build/native-terrain-culling-migration/shared-cache/runtime-verification.json; all16 corrected ABAB/exact6000 clean, VUID/error/orphan0; all7 lifecycle and both manually reviewed settled diagnostic pairs/DH coverage pass; current/Frozen/native/protected-prompt guards and8 Current fingerprints pass. Original interrupted and env-mismatched retry batches remain rejected.
Performance FAIL: vanilla p99ms3.407/3.284 C/F despite1361.3/1147.65 FPS. DH782.8/673.95 FPS,p99ms4.207/5.969; shaders345.6/317.9,p99ms5.498/6.030; combined260.55/227.05,p99ms5.753/7.424 pass. DH repeats vary substantially; no isolated cache gain or original outlier cause proved.
Published sky1b9b10339/fog3adbe6d5d/lightmaps971e0226b. Native face policy consumes retained world IDs; cache readers overlap, reload remains exclusive. Rust2473pass/3ignored, Java1833tests/2skip/0fail, six JNI workers exactlibrary. Earlier2f profiles: Javaallocation0.818/2.401GB ordinary,1.707/4.453GB DH C/F; nativealloc excluded. Accepted new wait profile0/193contended meshing points vs62/238; all8workers,3F3views and actualDH verified;26 completed copies retired. No full Java removal, broad gameplay/temporal or long-memory acceptance.
