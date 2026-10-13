# Rust migration prompts

## 1. October 14, 2026: Shared gameplay data

Move remaining entity, item and biome definitions, components, synchronized data and stable identities into authoritative Rust owners. Preserve gameplay, save and packet representations, including supported custom fields. Complete this goal when retained content round-trips through native storage, serialization and synchronization without duplicate Java authority. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 2. October 15, 2026: Remaining world generation

Move remaining carvers, features, structures, decoration, sequencing and chunk publication into Rust, reusing native noise, aquifers, surfaces and storage while preserving custom generation. Verify fixed-seed FULL chunks and metadata against Frozen, including cancellation and failure paths that must not publish partial results. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 3. October 16, 2026: World ticks and chunk lifecycle

Move tick-phase ordering, scheduled block/fluid work, random-tick dispatch, weather, time, sleep, tickets, chunk promotion/unload and lighting coordination into Rust. Verify edits, boundary lighting, teleports and unloads through real world lifecycles; completion requires native world policy and orchestration, not Java scheduling around native storage. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 4. October 17, 2026: Entity movement and lifecycle

Move travel, fluid movement, passengers, portals, spawning, projectiles, death/respawn and player state into Rust, reusing native collision and membership owners. Preserve every supported custom movement path and callback order. Verify native state serialization/synchronization and end-to-end movement, transfer and lifecycle behavior. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 5. October 18, 2026: Living behavior, AI and combat

Move navigation, goals/brains, targeting, attributes, damage, effects, breeding, taming and species behavior into Rust. Implement and verify every retained behavior family, including Alex's Mobs, using representative interactions and deterministic traces. Completion requires native behavior execution with no required Java AI callback. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 6. October 19, 2026: Items and inventories

Move item use, equipment, durability, containers/transfers, crafting, food, potions, tools, firearms and supported custom items into Rust. Preserve components, consumption, results and callback order. Verify client/server agreement across interactions, persistence and interrupted transfers, with no item duplication, loss or substituted behavior. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 7. October 20, 2026: Blocks and server rules

Move block entities, redstone, fluids, neighbor updates, workstations, farming, commands/functions, advancements and statistics into Rust. Cover every supported family, chained updates and inventory-bearing blocks through gameplay and save tests. Native registration alone is insufficient: production behavior and server-rule execution must have native authority. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 8. October 21, 2026: Persistence and upgrades

Move save orchestration, write ordering, entity/player/POI/level persistence, flushing, shutdown and supported-version upgrades into Rust while retaining the current format. Verify old and newly written worlds reopen losslessly, including IO failures and interrupted saves. Unsupported versions must fail explicitly rather than silently losing or reinterpreting data. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 9. October 22, 2026: Packet codecs

Move framing, serialization, compression, protocol data and supported custom-payload codecs into Rust using the shared native components. Verify golden packets, fragmented streams and malformed inputs with bounded memory and preserved errors. Production encoding and decoding must no longer require a Java codec. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 10. October 23, 2026: Live networking

Move transport, protocol transitions, connection lifecycle, dispatch, reconnects, timeouts and backpressure into Rust. Verify native integrated and dedicated clients/servers joining, moving, interacting, exchanging chunks and synchronizing inventories, including disconnect/recovery paths. Completion requires live native networking without Netty or Java dispatch. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 11. October 24, 2026: Resource and content reloads

Move remaining codecs, registry publication, contextual lookup and data/resource-pack reload orchestration into Rust, reusing native pack, atlas and model owners. Verify coherent generations across consumers and repeated reloads; failed reloads must preserve a valid prior state. Native parsing alone does not complete publication or lifecycle ownership. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 12. October 25, 2026: UI, text and HUD

Move menus, widgets, text layout/bidi, focus, chat, tooltips, inventory screens, HUD, VoxelMap and the recipe viewer into native producers and behavior. Verify keyboard, mouse and text input, scaling, resizing, screen transitions and reloads. Rust GUI quads alone do not establish native UI ownership or usable parity. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 13. October 26, 2026: Scene and effect producers

Move remaining entity/item/hand extraction, animation, particle simulation/providers, weather, camera and material/tint production into Rust. Preserve interpolation, gameplay-driven behavior and frame lifetimes through ordinary play and transitions. Scene inputs must originate from native owners; replacing only their transport or final rendering is insufficient. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 14. October 27, 2026: Renderer and shader gaps

Close remaining ordering, compatibility, reload and lifetime gaps across transparency, hands, entities, particles, HUD and shader transitions. Move remaining rendering policy into Rust without hidden Java fallbacks. Verify ordinary gameplay, the first shader-world frame and first-turn captures, preserving strict visual comparisons and performance floors. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 15. October 28, 2026: Complete DH runtime ownership

Move remaining DH generation/retrieval queues, tree orchestration, database work, update publication, contextual callbacks and shutdown into Rust, reusing native generation, sources, conversion and builds. Verify edits, movement, dimension changes, reopen, draining and sustained streaming with correct lifetimes. Native geometry alone does not complete the DH runtime. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 16. October 29, 2026: Audio and client services

Move remaining audio IO, contextual decisions, custom tickables, device handling, subtitles and supported client-service orchestration into Rust while preserving intentional exclusions. Verify physical output, reloads, device loss/recovery and shutdown with pending work. Null-sink policy tests alone cannot establish audible, recovery or lifecycle acceptance. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 17. October 30, 2026: Native application and platform

Move client/server loops, window/input handling, configuration, paths, timers, workers, errors and shutdown into the native application. Integrate already ported systems continuously and test early rather than postponing startup until the final migration stage. Deliver one Rust executable for client and dedicated-server modes, at most one separately loaded Rust library, and no Java/JVM bootstrap; verify platform and lifecycle behavior. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 18. October 31, 2026: JVM removal and integrated acceptance

Remove required Java, Fabric, JNI and FFM launch/runtime dependencies and package one Rust executable for client and dedicated-server modes with at most one separately loaded Rust library and no JVM. On a clean host without Java, verify both modes, save/load, ordinary play, reconnects, reloads, dimensions and shutdown, then perform the November 1, 2026 final-candidate check. No required Java owner or missing retained feature may remain. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under the [acceptance requirements](docs/development/RUST-MIGRATION.md). Accept no correctness or performance regressions.

## 19. Post-migration architecture cleanup

Clean up MattMC’s completed Rust migration: remove obsolete bridges, fallbacks and scaffolding; consolidate duplicate abstractions and representations; clarify authoritative ownership and dependencies; and make startup, shutdown, errors and resource lifetimes coherent. Align tests, build tooling and documentation with the resulting architecture. Preserve all retained behavior, content and intentional differences, and verify meaningful lifecycle and integrated client/server scenarios against the documented acceptance requirements in docs/development/RUST-MIGRATION.md, including matched Frozen and pre-change Current comparisons. Accept no correctness or performance regressions.

## 20. Remove migration shortcuts and restore extensibility

Audit MattMC’s Rust implementation for hardcoded current outcomes, fixed registry capacities, ID special cases, magic numbers and shortcuts that prevent new definitions or state combinations. Restore extensible registry, property and behavior mechanisms while preserving retained content and intentional differences; keep legitimate lookup tables, intrinsic constants and documented rule-derived optimizations supported by evidence. Test novel definitions and state combinations through the intended mechanisms without unrelated engine patches, verify the general rules, and compare correctness and performance under docs/development/RUST-MIGRATION.md against matched Frozen and pre-change Current. Accept no correctness or performance regressions.

## 21. Failure recovery and test-suite audit

Harden MattMC’s Rust recovery from interrupted saves/IO, inventory disconnects, failed reloads, stale or canceled chunk jobs after unload/dimension changes, and shutdown with pending work. Make error propagation, rollback, cancellation and lifetimes consistent. Audit Rust tests for meaningful behavior, contracts and regression protection; consolidate or remove redundant, obsolete-bridge, implementation-mirroring or misleading mock tests while preserving unique regressions and important differential/property coverage. Replace gaps with focused integration and injected failure/cancellation/recovery checks proving no corruption, item loss/duplication, dangling work, silent fallback or substituted behavior. Never reduce tests to meet an arbitrary count. Preserve retained content and intentional differences/exclusions; compare matched Frozen and pre-change Current under docs/development/RUST-MIGRATION.md. Accept no correctness or performance regressions.
