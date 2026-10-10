# Rust migration working record

## Goal and constraints

- ACTIVE: remove Java/JVM entirely; one Rust library and one executable for client/server.
- Work on master in this checkout; publish tested larger milestones normally.
- Frozen Java OpenGL alone is the baseline: `../MattMC_JavaPerfTesting/MattMC`, HEAD `7a4d181717dc0c7da9086f1a17687dfd522ce417`; never modify Frozen.
- Preserve Rust/GAL GPU ownership and one presenter. Transitional Java exposes bounded CPU semantics.
- Protect/exclude `docs/STANDARD-COPILOT-PROMPTS.md`, SHA c5695e9b. SUMMARY≤10 lines; this record≤100; concise assumptions in ASSUMPTIONS.md.
- Practical developer guides go beside their subsystem; tooling in DevUtils/tests/; Wiki after documentation changes.

## Ownership and migration priorities

- Rust owns rendering/GAL/presentation, terrain assembly/publication/visibility, retained DH inputs and many content/world kernels.
- Live canonical block/biome sections, rebuild captures, counters, light layers/maps, loaded-biome residency and direct sky/fog/scalar light consumers have native owners.
- Java still owns general gameplay/simulation, chunk/light orchestration, contextual callbacks, assets/platform/network/startup and remaining extraction. Cargo builds a library; app/ remains empty.
- Prioritize authoritative world-state producers with direct consumers. Avoid Java construction/repacking followed by equivalent Rust reconstruction. Performance issues guide migration rather than stopping it.
- Published: sky1b9b10339, fog/cache3adbe6d5d, light-map/direct scalar consumers971e0226b. Guides retain their scoped tests, runtime and historical performance.

## Current native face policy and cache work

- Rust owns one bounded immutable canonical geometry/policy table and derives block/fluid/occlusion facts from its block registry. Java exports cached intrinsic geometry/source implementation identities once.
- Native meshing consumes retained 18³ world IDs directly; Java reads admission metadata only. No native result projection/repacking. Bars/tags, leaves/hooks, custom providers/states retain original callbacks; Java occlusion cache is lazy.
- Private compact header5; whole-frame ABI/GAL unchanged. Native-controlled records reject invalid/missing/stale state before dispatch; replay compatibility remains.
- Actual Frozen corpus:787,992 decisions,31,809 states,21,007 shape identities→146 unique geometries,all21,316 unique pairs. Checked-in generator reproduces the fixture byte-for-byte. Actual Java export/CABI tests cover admitted decisions and glass/glass versus glass/air without Java masks/hints.
- The following2f8255f4 evidence predates the cache change: fullRust2472pass/3ignored; Java1833tests/2skip/0fail, six JNI workers exactlibrary. All7 lifecycle cases and both manually reviewed settled vanilla/Iris+DH pairs/DH coverage pass; errors/panics/dependencies/GAL0.
- All16 ABAB/exact6000 clean; source/library/Frozen/prompt guards and eight Current native fingerprints pass.25 completed runtime copies retired; compact failed-performance evidence retained.
- Historical2f medianFPS C/F: vanilla1327.85/1175.95; DH630.5/795.85 FAIL; shaders337.2/317.75; combined255.4/226.4. New305 corrected results follow below.
- Medianp99ms C/F: vanilla3.538/3.121 FAIL; DH7.325/4.439 FAIL; shaders5.688/6.256; shadersDH6.925/8.349. Slow DH repeat:23GC/717ms vs4/15ms and RSS6.9/5.1GiB, little GPU-time change; cause remains unisolated.
- Current optimization: model/state/selector tables now share one Rust owner. Independent scans hold concurrent read guards; registration/reload are exclusive and clear all tables together. No GPU/ABI ownership change.
- New cache: focused77 Rust tests pass; final fullRust2473pass/3ignored includes eight overlapping readers protected from reload and eight actual CABI builders producing identical bytes. Release30586383 builds; fullJava1833tests/2skip/0fail pass, sixJNIworkers exactlibrary; all7 lifecycle and both manually inspected settled pairs/DH coverage pass. Wrapper interrupted143 during FPS; source/native/Frozen/prompt guards pass; exactowned orphan stopped; corrected16 ABAB/exact6000 runs now complete/clean, all source/library/prompt guards and8 Current fingerprints pass. Original interrupted suite and env-mismatched retry remain rejected. Corrected separate ABAB batches use production env; integrated receipt retains these distinctions.
- New305 medianFPS C/F: vanilla1361.3/1147.65; DH782.8/673.95; shaders345.6/317.9; combined260.55/227.05. Medianp99ms3.407/3.284 vanilla FAIL;4.207/5.969 DH;5.498/6.030 shaders;5.753/7.424 combined pass. Overall performance still FAIL. Substantial DH repeat variance; no isolated cache gain claimed.
- Guide: docs/development/rendering/RUST-TERRAIN-CULLING.md. Original receipts: build/native-terrain-culling-migration/ and validation/native-terrain-culling-20261009/. New verification uses shared-cache/ and a new validation label.

## Profiles and evidence-backed next work

- Four accepted ordinary8s CPU/allocation profiles: all12 F3 positions reviewed, final source/library/Frozen/prompt/process guards pass. Replacement Current allocation accepted; interrupted original remains rejected.
- WeightedJava allocation0.818/2.401GB C/F; Current culling cache1.05MB/2CPU vs preceding integrated8.39MB/40CPU. Native policy1sample; section preparation20.97MB/345CPU remains. Sparse weights overlap, exclude native allocation and establish no isolated throughput gain.
- Two accepted DH8s allocation profiles plus Current wall profile: all9F3 positions reviewed; equivalent prepared source/radius32/cached format2/distant generation off; actual native DH submissions in both videos. All terminal/integrity/process guards pass; three completed copies retired.
- DH weightedJava allocation1.707/4.453GB C/F, DH stack subsets1.141/3.072GB. Both include costly builder event lookup, lighting scratch arrays, full-data hashing and heightmap construction. Benchmark render-thread allocation counters miss these workers.
- Native wall profile:62 of238 meshing sample points wait on exclusive cache mutex across all8 workers. This motivates concurrent cache reads, but does not prove the original DH timing outlier's cause. Exact305 replacement wait profile passes:0 contended/193native meshing samples across8 workers vs62/238 before; all3F3 images reviewed, forward drift0.545blocks, actualDHopaque/translucent/water submissions positive. Source/library/Frozen/prompt/prepared-source/process guards pass. Parent-only signal trace exits0; original interrupted profile remains rejected/unexplained.
- Endpoints cross seven X chunk columns; maximum ordinary drift1.089blocks, DH drift0.545blocks. Images are not time registered; settled endpoints do not prove transient pop-in/flicker parity.
- Next migration targets: DH authoritative chunk/full-data/lighting producers and direct native consumers; remaining Java contextual lighting/tint work. Preserve custom hooks and original state/lifetime semantics.

## Sync and publication

- Requested600s work then sync completed earlier; integrated testing/recorder/allocation/resource changes through81440bf19 before published971e0226b. Integrated b729 medianFPS C/F1327.95/1171.55 vanilla,769.6/645.15 DH,347.4/315 shaders,258.95/227.15 combined; vanilla p99 failed. Those results are historical.
- Latest sync: docs-only1e060cb74 fast-forwarded, scoped SUMMARY conflict keeps newer2f metrics and incoming source/evidence qualifiers. All25 local path hashes/modes/symlinks restored; empty staging and Wiki verified. Scoped stash/recovery removed; two older stashes untouched. No runtime source changes from this sync.
- Face-policy/cache milestone source/runtime checks complete; vanilla p99 performance gap and broad gameplay remain open. Fresh upstream review finds master/origin-master identical at1e060cb74; publish this tested face-policy/cache milestone normally. Exact release305 evidence and remaining gaps are recorded above. Fresh fetch/review before eventual normal publication; never stage the protected prompt.

## Storage and test integrity

- Bulk pruning recovered~350GiB earlier;211GiB before latest retirement. Keep unique failure/crash/incomplete evidence and required source saves; retire only proven completed inactive copies.
- Cleanup mistake: parent-wide retirement deleted the interrupted original allocation world copy. Its logs/images/raw profile/interruption receipt remain; no recovery claimed. Correction receipt retained.
- Repaired helper now requires every enclosing flight.json to prove recognized completion and driver exit0. Incomplete/malformed/unknown/symlinked receipts retain workspaces. Two tests reproduced old deletion; all13 retention tests and combined43 retention/flight Python tests pass. Failed DH preflight/setup and prepared source remain. Latest cleanup retires26 completed inactive copies (16timing,1acceptedprofile,7gate,2parity); interrupted worlds preserved.
- Clean timing excludes builds/profilers/other games; require two repeats per side/mode, exact6000frames, VUID0, FPS≥Frozen and p99≤Frozen for full performance acceptance. No overall acceptance yet.

## Open risks and completion

- Broad gameplay, first shader world frame, temporal pop-in/flicker and long-session host/GPU memory remain unproved. Settled diagnostic pairs do not certify ordinary bulk-light pixels.
- Current ordinary gameplay prior receipt d9d1a9d6: entry/travel frame-time outliers and only16.45blocks travel before a barrier; not sustained-streaming proof.
- General simulation, Java removal and Rust executable remain substantial work. No concrete user-decision blocker; keep implementing/debugging.
