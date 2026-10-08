# Rust vs Frozen Java speed (2026-10-08; master 97e309226 + native state graphs, uncommitted; RTX 2070; moving camera; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,080.0 / 1,052.2 | 1,114.6 / 1,162.8 |
| Vanilla + DH | 597.7 / 618.9 | 597.2 / 817.0 |
| Shaders | 337.0 / 328.3 | 312.0 / 309.9 |
| Shaders + DH | 239.5 / 234.3 | 217.7 / 215.0 |
Receipts under graphics captures: latest vanilla `goal5/dense-mesh-cache-measurement-v3/results.json` (control/Frozen rows); other modes `validation/state-graphs-master-20261008/summary.json` (ABAB). All listed runs clean, VUID0; rejected slot cache excluded.
Performance FAIL: vanilla/DH FPS medians −6.4%/−14.0%; shader medians +7.0%/+9.5%. Latest vanilla p99 Current/Frozen3.595/3.206 ms; other failing tails remain vanilla+DH and shaders+DH.
Full Java/Rust suites, seven lifecycle cases and visually reviewed vanilla/Iris+DH coast pairs pass; broader gameplay/memory parity and Rust-only application remain incomplete.
