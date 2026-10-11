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
- Next after this batch: explicit active/despawn/section membership and direct world/entity consumers. Ordered ticking membership is now connected to the production client/server consumers; CPU/live checks pass; vanilla p99 remains open. The earlier draft/Java-only control is not acceptance. Current native entity lifetimes follow Java reachability; removed entities remain until collection.
- General simulation, networking, platform/startup, file orchestration and remaining extraction are Java. src/main/rust/app/mod.rs is empty. Java removal is far from complete.

## Published checkpoints

- DH visibility 65457ef80: incremental topology/epochs fence detached and late workers; stock traversal consumes retained Rust topology. Parent retirement detaches under lock before close.
- Its release ebd3c9a3 passes Rust2626/Java1976 (3 ignored/2 skips), actual Frozen1280-walk oracle, seven lifecycles and two reviewed settled pairs, VUID0.
- Visibility20-run matrix passes all floors: medianFPS C/F1431.4/1165.9,795.2/734.0,342.4/313.55,262.2/226.0; p99ms2.963/3.293,3.734/5.110,4.697/7.004,6.037/8.647 (vanilla/DH/shaders/combined). Diagnostic profiles are not isolated gain proof.
- ee809bbab owns decoded region tapes; 45a074369/453df088 construct primary block/biome/light owners from saved NBT; a15c0555 owns saved DH-light decoding and direct handoff.
- Saved palette82-case/40-chunk and saved-light64-case/52-import/256-column Frozen oracles pass. Tape quiet40-chunk controls are 7–9% below Frozen scoped cost; saved-light import cost ~98% lower, scoped only.
- Guides: docs/development/rendering/RUST-DH-VISIBILITY.md and world/{chunk,lighting}/; detailed receipts in build/native-{dh-visibility,owned-tape,saved-palettes,dh-saved-light}/.

## Local batch and evidence

- Integrated master6673fac82 includes upstream resource ownership398a00782. Existing local client-range/entity/GUI/tag-view work is preserved. Publication workflow now targets this combined batch before another subsystem.
- Client-range Rust index uses64-slot pages; block-entity/piston consumers scan it directly. Actual Frozen645-case callback/ordering oracle passes. Java still binds chunk objects and delivers callbacks.
- Rust entity geometry owns primary position/bounds and ordinary camera queries. Revised consumer retains renderer/equipment/custom/leash callback order; actual Frozen11057 cases pass. Geometry leases still follow Java reachability.
- GUI ABI80 templates retain immutable ordinary3D layers and instantiate them directly in Rust, removing per-quad Java record construction/encoding. Atlas repair retains the templates' actual source assets. Initial unknown-asset crash is preserved in predecessor receipts.
- Tag payloads read retained Rust Arc arrays through immutable CPU views; queued maps/sublists survive retirement and32 reloads/GC. Java holder binding, registry objects and publication walks remain.
- Historical a99: all4 GUI pairs/8 original reviews pass after capture-only readiness repair; moving phases10020/10004 agree. No renderer clock or tolerance changed. Failed moving pair remains under native-registry-tag-views-gui-20261011.
- Current c487f375: all20 alternating exact6000-frame timings clean, all final source/native/Frozen/prompt/three prepared-input guards and10 Current identities pass; no clients remain. MedianFPS C/F1351.75/1146.45,833.00/712.00,352.65/312.65,264.80/226.00; p99ms3.410/3.253FAIL,2.815/5.481,4.756/7.181,5.371/9.124 (vanilla/DH/shaders/combined). Controller exits1 only at evaluation; vanilla p99 misses4.8%, cause unproved.
- Allocation diagnostic: GUI batch184→85.5MiB/ordinary3D batch134→28MiB weighted fixed15s samples. Frame counts/revisions differ; no isolated gain, per-frame rate or p99 cause proved.
- Resource replay: all10 records match89630 lines; all9 warm aggregate floors pass. RetagC3.751/F5.603ms, but pair2 is1.153× and cold5.952/4.532ms is slower. PriorCurrent3.506ms was lower and Frozen baseline moved; no isolated improvement/cold fix claim.
- Rust ticking membership/order/snapshots now serve production client/server EntityTickList; Java holds behavior bindings/callbacks. Expanded Frozen4143 corpus rejected a9745542 receiver capture; repaired c487f375 passes five controls/all4143 cases, full Rust2656/3ignored and Java/parity2003/2skip. Refreshed geometry11057/range645 pass. Seven lifecycles/two settled pairs pass,VUID0; four originals reviewed/nine Current native identities match. All20 fresh timings are complete; see the matrix above and SUMMARY. Failed corpus/incomplete predecessor world remain preserved.
- Guides: world/entities/{RUST-ENTITY-GEOMETRY,RUST-ENTITY-ORDER}.md, world/chunk-loading/RUST-CLIENT-RANGES.md, rendering/RUST-GUI-ITEM-TEMPLATES.md and resources/{TAGS-AND-REGISTRIES,VERIFICATION}.md. Current repaired receipts: build/native-entity-order-receiver-fix/ and native-entity-order/receiver-fixed-replay/. Historical/rejected evidence remains in guides/receipts.

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
- Stage explicit reviewed paths; never force-push/reset/blanket-stage. Prior publication receipts: build/native-dh-visibility/publish/; current combined behavior/runtime checks pass; strict vanilla p99 acceptance remains open.
- Prior pruning recovered~350GiB plus21.08GiB archival/17.04GiB unused Rust profiles. Lossless archives retain original/restoration hashes; gzip -dk PATH.json.gz restores them.
- Historical162+18 terminal JSON archives saved12.05+1.615GiB; predecessor captures/logs/results remain. Manifests: build/native-dh-visibility/*archive.json and native-client-ranges/completed-predecessor-archive.json.
- Retired56 superseded profile videos (17.83GiB), retaining447 PNGs/profiles/receipts. Videos are not restorable; native-saved-palettes/retired-profile-videos.json records hashes.
- Retired completed generated worlds: revised entity21, initial entity24, range/readiness22, initial range22, plus earlier chunk/tape/visibility copies. All timing repeats, failed aggregates, profiles/captures and compact receipts remain.
- Retention requires recognized terminal completion, successful driver and no live process. Preserve failed/unknown/active/symlinked scopes, prepared DH inputs and open startup/first-view/closed-pipe worlds.
- Retired2 successful predecessor plain/static GUI fixture copies; all4 original image hashes preserved against review receipts. Failed moving world untouched; receipt: native-registry-tag-views-phase-fix/retired-successful-predecessor-fixtures.json.
- Current goal5 artifacts52GiB; free disk225GiB at this check. Automatically retire successful generated correctness workspaces; continue scoped pruning as new batches complete.
- Detailed subsystem histories/rejected candidates remain in developer guides and build/native-*/ receipts. Full preceding scratch record retained in build/native-gui-item-templates-atlas-fix/progress-before-condensing.md.
