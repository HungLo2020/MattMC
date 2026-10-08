# Recorded Rust vs Frozen Java speed (2026-10-08; candidate d7ee0335d, RTX 2070, moving camera, ABAB, 6,000 frames; FPS per run)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,123.0 / 1,067.9 | 1,200.4 / 1,174.9 |
| Vanilla + DH | 601.7 / 705.0 | 740.2 / 682.4 |
| Shaders | 352.4 / 348.7 | 320.9 / 316.1 |
| Shaders + DH | 252.3 / 243.3 | 227.7 / 225.9 |
Recorded in `artifacts/graphics-captures/validation/batch2/summary.json` in the original checkout; raw benchmark receipts are unavailable.
Vanilla/DH average-FPS floors remain unproven (paired medians −7.8%/−8.1%); shader medians +10.1%/+9.3%. Verification-only changes add no runtime speed claim.
Reproduce with retained inputs: `python3 DevUtils/tests/rendering/RunValidation.py --label <new> --perf` (see rendering verification docs).
