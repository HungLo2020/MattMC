# Latest Rust vs Frozen performance (2026-10-12; native691c0b95; DH metadata/IO)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1317.80 / 1264.50 | 3.563 / 3.710 |
| Vanilla + DH | 753.75 / 701.45 | 3.992 / 6.264 |
| Shaders | 348.45 / 313.65 | 4.679 / 7.220 |
| Shaders + DH | 264.55 / 225.95 | 6.270 / 8.842 |
All20 exact6000-frame runs clean; matched live JVM controls and source/native/class/Frozen/prompt/prepared-input/corpus guards pass. All FPS/p99 floors PASS in this scope. Integrated upstream sky/id-map/GUI changes included; no isolated whole-game gain claimed. RSS DH8.83–9.40GiB,combined9.00/9.15GiB below guard; long memory remains open.
Rust2723/3ignored; Java2043/2skip; actual Frozen606 metadata/696830 point rows match. Seven lifecycles/three settled pairs pass,VUID0;13 originals/10 native identities reviewed. Controller143 recovery uses completed guarded captures; failure retained. Real24-DTO oldCurrent/current/Frozen78.73/43.62/41.51ms;44.6% faster old,still5.1% slower Frozen. Broad/temporal/streaming acceptance and Java removal remain open. [Results](docs/development/rendering/GAMEPLAY-PERFORMANCE.md#dh-metadata-and-bulk-io-checkpoint).
