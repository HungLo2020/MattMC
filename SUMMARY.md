# Last measured Rust vs Frozen performance (2026-10-11; native 9c9f085c)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1358.35 / 1155.90 | 3.049 / 3.109 |
| Vanilla + DH | 803.60 / 704.00 | 3.360 / 5.739 |
| Shaders | 346.25 / 316.50 | 5.508 / 6.757 |
| Shaders + DH | 266.75 / 227.85 | 6.118 / 8.369 |
All20 alternating exact6000-frame runs clean; every FPS/p99 floor and source/native/Frozen/prompt/three input guard passes. 360settle/240warmup; DH4 repeats per side, others2. Rust2685/3ignored; Java2010executed/2skip; Frozen lookup4417/geometry11489/order4143/range645 pass. Seven lifecycles/two world/four GUI item pairs pass,VUID0;12 original reviews/13 Current identities match.
Rust visible tracking and profile-backed text-copy cleanup verify this bounded checkpoint. Prior vanilla p99 miss5.1% retained; no isolated gain/tail cause proved. Leash width and reload-fixture crosshair issues remain, as do broad/temporal/streaming/memory acceptance and Java removal. [Results](docs/development/rendering/GAMEPLAY-PERFORMANCE.md#local-visible-tracking-checkpoint).
