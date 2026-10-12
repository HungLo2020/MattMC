# Rust migration working record

## Goal and constraints

- ACTIVE: remove all Java/JVM; one Rust library and executable with client/server modes.
- Work on master in this checkout. Publish validated batches incrementally; latest authorization supersedes earlier no-publication restrictions.
- Frozen OpenGL alone is the baseline: ../MattMC_JavaPerfTesting/MattMC at 7a4d181717dc0c7da9086f1a17687dfd522ce417. Never modify Frozen.
- Rust/GAL owns GPU resources, ordering and presentation; one presenter. Transitional bridges carry bounded semantic CPU inputs.
- Protect docs/STANDARD-COPILOT-PROMPTS.md (SHA c5695e9b). SUMMARY≤10 lines; this record≤100; ASSUMPTIONS stays concise. New crates require approval.
- Developer guides belong beside subsystems; test drivers in DevUtils/tests/. Run Wiki check after documentation edits.

## Ownership and next subsystem

- Rust owns rendering/GAL/presentation, terrain assembly/visibility, block/biome sections, light fields and biome color windows.
- Rust owns DH points/materials, metadata/occlusion, tint/reduction, box/greedy topology, packet encoding, direct ledger handoff and retained visibility traversal/admission.
- Java still owns DH generation/retrieval scheduling and its generation tree. Ordinary admitted visibility currently exports keys although its caller only needs their count; assess with profiling.
- Client residency owns logical publication, counts, centers, fingerprints, empty membership and the local ordered coordinate index. Java binds chunk references/callbacks.
- Local entity slice owns primary position/bounds and ordinary camera queries. Immutable Java projections and behavioral callbacks remain.
- Local GUI slice retains ordinary cached 3D semantic layers in Rust and sends one placement/foil instance per item, removing per-quad Java records and repeated geometry encoding.
- Active next slice: visible tracking registry, then section membership and direct world/entity consumers. Ordered ticking membership is now connected to the production client/server consumers; CPU/live checks and latest settled floors pass; broad acceptance remains open. The earlier draft/Java-only control is not acceptance. Primary leases follow Java reachability; local removal-state work now retires dense camera membership immediately.
- General simulation, networking, platform/startup, file orchestration and remaining extraction are Java. src/main/rust/app/mod.rs is empty. Java removal is far from complete.

## Published checkpoints

- DH visibility 65457ef80: incremental topology/epochs fence detached and late workers; stock traversal consumes retained Rust topology. Parent retirement detaches under lock before close.
- Its release ebd3c9a3 passes Rust2626/Java1976 (3 ignored/2 skips), actual Frozen1280-walk oracle, seven lifecycles and two reviewed settled pairs, VUID0.
- Visibility20-run matrix passes all floors: medianFPS C/F1431.4/1165.9,795.2/734.0,342.4/313.55,262.2/226.0; p99ms2.963/3.293,3.734/5.110,4.697/7.004,6.037/8.647 (vanilla/DH/shaders/combined). Diagnostic profiles are not isolated gain proof.
- ee809bbab owns decoded region tapes; 45a074369/453df088 construct primary block/biome/light owners from saved NBT; a15c0555 owns saved DH-light decoding and direct handoff.
- Saved palette82-case/40-chunk and saved-light64-case/52-import/256-column Frozen oracles pass. Tape quiet40-chunk controls are 7–9% below Frozen scoped cost; saved-light import cost ~98% lower, scoped only.
- Guides: docs/development/rendering/RUST-DH-VISIBILITY.md and world/{chunk,lighting}/; detailed receipts in build/native-{dh-visibility,owned-tape,saved-palettes,dh-saved-light}/.

## Integrated batch and evidence

- Published f92ea29dd on master: combined client-range/entity geometry/ticking/GUI/tag-view batch, parent6673fac82; remote verified and worktree clean at publication. Temporary4afa14b stash dropped after accounting all70 saved paths; protected stashes retained.
- Client-range Rust index uses64-slot pages; block-entity/piston consumers scan it directly. Actual Frozen645-case callback/ordering oracle passes. Java still binds chunk objects and delivers callbacks.
- Rust entity geometry owns primary position/bounds and ordinary camera queries. Revised consumer retains renderer/equipment/custom/leash callback order; actual Frozen11057 cases pass. Geometry leases still follow Java reachability.
- GUI ABI80 templates retain immutable ordinary3D layers and instantiate them directly in Rust, removing per-quad Java record construction/encoding. Atlas repair retains the templates' actual source assets. Initial unknown-asset crash is preserved in predecessor receipts.
- Tag payloads read retained Rust Arc arrays through immutable CPU views; queued maps/sublists survive retirement and32 reloads/GC. Java holder binding, registry objects and publication walks remain.
- Historical a99: all4 GUI pairs/8 original reviews pass after capture-only readiness repair; moving phases10020/10004 agree. No renderer clock or tolerance changed. Failed moving pair remains under native-registry-tag-views-gui-20261011.
- Published checkpoint c487f375: all20 alternating exact6000-frame timings clean, all final source/native/Frozen/prompt/three prepared-input guards and10 Current identities pass; no clients remain. MedianFPS C/F1351.75/1146.45,833.00/712.00,352.65/312.65,264.80/226.00; p99ms3.410/3.253FAIL,2.815/5.481,4.756/7.181,5.371/9.124 (vanilla/DH/shaders/combined). Controller exits1 only at evaluation; vanilla p99 misses4.8%, cause unproved.
- Allocation diagnostic: GUI batch184→85.5MiB/ordinary3D batch134→28MiB weighted fixed15s samples. Frame counts/revisions differ; no isolated gain, per-frame rate or p99 cause proved.
- Resource replay: all10 records match89630 lines; all9 warm aggregate floors pass. RetagC3.751/F5.603ms, but pair2 is1.153× and cold5.952/4.532ms is slower. PriorCurrent3.506ms was lower and Frozen baseline moved; no isolated improvement/cold fix claim.
- Rust ticking membership/order/snapshots now serve production client/server EntityTickList; Java holds behavior bindings/callbacks. Expanded Frozen4143 corpus rejected a9745542 receiver capture; repaired c487f375 passes five controls/all4143 cases, full Rust2656/3ignored and Java/parity2003/2skip. Refreshed geometry11057/range645 pass. Seven lifecycles/two settled pairs pass,VUID0; four originals reviewed/nine Current native identities match. All20 fresh timings are complete; see the matrix above and SUMMARY. Failed corpus/incomplete predecessor world remain preserved.
- Guides: world/entities/{RUST-ENTITY-GEOMETRY,RUST-ENTITY-ORDER}.md, world/chunk-loading/RUST-CLIENT-RANGES.md, rendering/RUST-GUI-ITEM-TEMPLATES.md and resources/{TAGS-AND-REGISTRIES,VERIFICATION}.md. Current repaired receipts: build/native-entity-order-receiver-fix/ and native-entity-order/receiver-fixed-replay/. Historical/rejected evidence remains in guides/receipts.

## New bounded slice: entity removal and camera retirement

- Rust owns primary removal reason/first-reason policy; removes Entity's Java removalReason field. Camera slots retire immediately on nonnull removal, preserving retained geometry and callbacks; revive/world transfer retain coherent ownership. Unspawned nonremoved bodies still have slots; full tracking/section membership remains future work.
- Native543a592f: fresh full Rust2659 pass/3ignored and Java/parity2005 executed/2skip; seven geometry/lifetime controls pass. Actual unchanged Frozen11489 geometry/removal,4143 tick-order and645 range cases pass with source/native/class guards. Initial fixture assertion and matrix/recorder compile errors corrected; failed logs retained. Live controller74158 exits0: seven lifecycles/two settled pairs pass,VUID0; four originals reviewed/nine runtime native identities match. All20 timings clean, all final guards/three protected inputs/ten timing native identities pass; no clients remain.
- Latest543a592f matrix: medianFPS C/F1366.65/1181.35,876.60/701.70,346.40/315.80,265.25/226.40; p99ms3.121/3.169,2.307/5.288,5.128/6.753,5.266/8.747 (vanilla/DH/shaders/combined). All floors pass; vanilla margin~1.5%, no isolated speedup or prior-tail cause proved. This pre-atlas/model matrix is historical; SUMMARY now records combined9f.
- This preintegration checkpoint verifies its recorded scope; combined9f checks below supersede it. Receipts: build/native-entity-lifecycle/.

## Combined lifecycle/atlas/model verification

- Final fetch found71762d602 (Rust texture atlases/model baking). Preserved17 local paths in e5165c53 stash; verified every saved blob and empty index, fast-forwarded and restored --index. No conflicts; only2 auto-merged docs differ from saved files. Preservation receipt accounts every saved path; protected stashes unchanged.
- Combined nativea48b6a65 passes full Rust2680/3ignored and Java/parity2005/2skip, plus Frozen11489/4143/645 replays under native-entity-lifecycle-model-integration/. Passing543a592f lifecycle/matrix records precede this integration and remain historical; do not accept combined source from them.
- Atlas CPU-only mode matches all8375 record lines against actual Frozen in5 rounds/5 iterations; strict1.0 timing passes C55.014/F66.562ms. Both baseline roles use unchanged Frozen7a4d1817, no Java Vulkan reference. Model records already identical8467688bytes, but warnings differ solely helpful-NPE details (Frozen200detailed/1132plain vsCurrent1332detailed); retain failure. Matched -XX:-OmitStackTraceInFastThrow control matches all67433 lines/4351 warnings over5 rounds; model-only C83.237/F71.367ms FAIL1.166, fullreload129.458/134.165PASS. No production/Frozen source changed for that control.

- Paired model-only15s CPU profiles after20warmups pass process/source/native guards. Current1486/26984 samples in Sprite::transparent; implemented bounded4MiB alpha-border mask for fully valid frames, original error prefix retained for invalid frames. Candidate9f372451 passes fresh full Rust2680/3ignored and Java/parity2005/2skip; Frozen model67433lines/4351warnings match. Strict aggregates C76.915/F79.069 model-only and128.454/135.543 fullreload pass;3/5 model pairs slower and Frozen median moved, no isolated gain. Combined9f runtime passes7lifecycles/2worldpairs/4GUIpairs,VUID0; all12 original images reviewed and13 native identities match. Controller2173 exits0: all20 clean timings/all four FPS+p99 floors pass, final source/native/Frozen/prompt/three prepared-input guards and10 timing native identities match; no clients remain.

- Combined9f medianFPS C/F1313.65/1142.25,808.10/709.80,341.95/312.95,260.60/226.05; p99ms3.248/3.307,3.156/5.266,4.780/7.169,5.275/9.203 (vanilla/DH/shaders/combined). All floors pass; vanilla p99 margin~1.8%, no isolated gain or prior-tail cause proved. Receipts: native-entity-lifecycle-model-integration/alpha-mask-accepted/.

- Final documentation-only696a25e3b integration preserved25 paths in cd828049, resolved6 overlapping pages with both ownership constraints/new results retained. All tested executable/native/Frozen/prompt identities unchanged; wiki2537pages/46indexes passes. Publication/recovery receipts: build/native-entity-lifecycle/publish/.

## Active slice: visible entity tracking

- Published c1428dbc7 (parent696a25e3b): Rust removal/camera retirement and bounded item alpha masks, all recorded CPU/live/Frozen/20timing floors pass; remote verified/worktree clean. Two workflow stashes dropped, protected stashes retained. Retired24 successful generated timing/GUI worlds, retaining40 timing artifacts and all12 reviewed originals.
- New Rust entity_lookup owns primary visible ID/UUID maps, ordered table, physical iterator indices and binding retirement; EntityLookup production client/server consumers use it. Java keeps behavior references/getter/cast/logging callbacks; section membership and persistence known-UUID/load orchestration remain Java.
- Full Rust2685/3ignored, Java2010executed/2skip and actual Frozen lookup/manager4417, geometry11489, ticking4143 and range645 pass with identity guards. Native9c9f085c passes7lifecycles/2worldpairs,VUID0;4 original reviews/9 identities match. Initial iterator-message defect fixed; failed fixtures retained.
- Canonical per-part model observer now requires complete unique frame-correlated parts;3 controls/344 reference tests pass. Guarded published-registry control reproduces old observer failure. Frozen copied-fixture adapter supplies matching leash/player-pose input. Body/shadow strict pairPASS/10 originals reviewed; equivalent leash cropFAIL with thinner Current geometry, also seen in published-registry control. Failed15s finalization, older-JDK launch and observer attempts retained.
- Integrated doc-only cc929bd06, preserving/verifying22 files in stash2639030a; no conflicts, two ownership pages auto-merged. Pending26-file manifest includes later text cleanup/docs. Protected stashes/prompt unchanged.
- Paired warmed15s JFR identifies redundant text layer/clip copies after excluding first-per-thread startup allocation weights. Text inputs now construct final metadata once; font/layout ownership stays Java. Fresh full Rust2685/3ignored, focusedJava13, fullJava2010/2skip, Frozen lookup4417 and wiki2538/46 pass. No isolated speedup or p99 cause proved.
- Controller79417 exits0:7lifecycles/2worldpairs/4GUIitempairs pass,VUID0;12 original reviews/13 native identities match. All20 fresh6000-frame timings clean/all FPS+p99 floors pass; final source/native/Frozen/prompt/three input guards pass; no clients remain. Ready for incremental publication. Native9c unchanged across text cleanup; sole executable delta GuiRenderer.
- Latest C/F medians FPS1358.35/1155.90,803.60/704.00,346.25/316.50,266.75/227.85; p99ms3.049/3.109,3.360/5.739,5.508/6.757,6.118/8.369 (vanilla/DH/shaders/combined). Prior pre-cleanup vanilla p99FAIL5.1% retained. Crosshair missing in some reload fixtures (also published9f), present moving; leash width still differs. Full parity/memory/Java removal remain open.
- Guide: world/entities/RUST-ENTITY-LOOKUP.md. Receipts: build/native-entity-lookup/. Publish this verified batch, then move section membership and its direct consumers into Rust.

## Remaining correctness and performance scope

- The earlier graphics-driver mismatch was resolved by reboot. Current hardware tests execute; do not report that historical condition as an active blocker.
- First shader-enabled world frame, temporal nearby regular-terrain pop-in/flicker, sustained streaming and long host/GPU memory behavior remain unproved.
- Settled captures occur after terrain admission and do not resolve the user's first-turn shader+DH gaps. User saw little flicker in later tests and suggested haze; preserve those observations separately.
- Broad gameplay still needs entities/items/hands, particles/weather, transparency, GUI/HUD, resource packs, representative shaders and DH combinations, plus transitions.
- Previous16.45-block travel and short RSS/allocation profiles are diagnostic, not sustained-streaming/memory acceptance.
- Earlier strict height-world teardown/closed-channel race and parallel Rust GLX BadAccess/poison cascade remain unresolved. Serial test success does not fix them; preserve failures.
- Profile real workloads and migrate live producers/consumers together. Do not weaken GAL, substitute static catalogs or stop migration because a performance floor misses.

## Preservation and cleanup

- Protected stashes:12345bcea835a6969b164f1a358ecd44a5bb22ab andc979e5de03a6682327a293fec0fc5150eaacf0bb. Protected prompt fullSHA is recorded in source-pin receipts.
- Sync-and-resume fast-forwarded b9798f51f with local work/staging restored; temporary7f1c82cd stash dropped. Previous sync-and-push integrated be0499204 before65457 publication; temporary61e418a3 dropped after verification.
- Stage explicit reviewed paths; never force-push/reset/blanket-stage. Publication receipts: build/native-entity-order-publish/publication.json (f92ea29dd) and earlier native-dh-visibility/publish/. Published behavior/runtime checks pass; latest settled matrix now passes all floors; prior tail cause and broad acceptance remain open.
- Prior pruning recovered~350GiB plus21.08GiB archival/17.04GiB unused Rust profiles. Lossless archives retain original/restoration hashes; gzip -dk PATH.json.gz restores them.
- Historical162+18 terminal JSON archives saved12.05+1.615GiB; predecessor captures/logs/results remain. Manifests: build/native-dh-visibility/*archive.json and native-client-ranges/completed-predecessor-archive.json.
- Retired56 superseded profile videos (17.83GiB), retaining447 PNGs/profiles/receipts. Videos are not restorable; native-saved-palettes/retired-profile-videos.json records hashes.
- Retired completed generated worlds: revised entity21, initial entity24, range/readiness22, initial range22, plus earlier chunk/tape/visibility copies. All timing repeats, failed aggregates, profiles/captures and compact receipts remain.
- Retention requires recognized terminal completion, successful driver and no live process. Preserve failed/unknown/active/symlinked scopes, prepared DH inputs and open startup/first-view/closed-pipe worlds.
- Retired2 successful predecessor plain/static GUI fixture copies; all4 original image hashes preserved against review receipts. Failed moving world untouched; receipt: native-registry-tag-views-phase-fix/retired-successful-predecessor-fixtures.json.
- Retired20 published f92ea29dd and20 preintegration543a timing world copies; preserved40 timing files per checkpoint and every failed-floor record. Receipts: native-entity-lifecycle/retired-published-timing-worlds.json and native-entity-lifecycle-model-integration/retired-preintegration-timing-worlds.json. Automatically retire successful generated correctness workspaces; continue scoped pruning as new batches complete.
- Detailed subsystem histories/rejected candidates remain in developer guides and build/native-*/ receipts. Full preceding scratch record retained in build/native-gui-item-templates-atlas-fix/progress-before-condensing.md.
