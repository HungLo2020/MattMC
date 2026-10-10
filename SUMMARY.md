# Rust vs Frozen Java speed (2026-10-10; DH height-field release574b68f0 atopaf6921cf5; RTX3080Ti; rotating view; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,391.9 / 1,352.9 | 1,177.6 / 1,181.2 |
| Vanilla + DH | 730.2 / 710.9 | 611.4 / 594.0 |
| Shaders | 342.0 / 339.1 | 317.3 / 317.6 |
| Shaders + DH | 258.9 / 247.7 | 227.6 / 221.4 |
Receipt: build/native-dh-heightmap-migration/performance.json; all16 production ABAB/exact6000 clean; VUID/error/orphan0; source/native/Frozen/protected-prompt guards and8 Current fingerprints pass.
Performance PASS for this batch: medianFPS C/F1372.4/1179.4 vanilla,720.6/602.7 DH,340.6/317.5 shaders,253.3/224.5 combined; medianp99ms3.125/3.176,5.471/6.706,4.417/6.141,5.795/7.754 respectively. Repeats vary; no isolated producer gain or broad/long-session acceptance.
Published sky1b9b10339/fog3adbe6d5d/lightmaps971e0226b/face-policy+cacheaf6921cf5. DH height owner directly reads native blocks/counters, uses frozen-verified geometry/policy and read-only CPU leases:31,532 states/16 saved chunks/five JNI checks; Rust2479pass/3ignored, Java1838tests/2skip/0fail. Reviewed settled vanilla/Iris+DH pairs pass, VUID0; six original lifecycle cases plus clean strict different-world rerun. Original closed-channel teardown failure retained; race unresolved. Actual travel admission verified; weighted Java heightmaps7.34/74.45MB C/F, total1.50/4.31GB; short RSS4.36GiB; no full Java removal, broad/temporal or long-memory acceptance.
