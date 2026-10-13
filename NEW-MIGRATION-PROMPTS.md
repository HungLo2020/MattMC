# Rust migration prompts

Move authoritative state, decisions, production callers and lifetimes into Rust; kernels or transport alone do not complete a goal. Reuse the existing [migration requirements](docs/development/RUST-MIGRATION.md), subsystem docs and verification tools. Preserve all retained content, custom behavior and intentional exclusions. Compare against unchanged Frozen and pre-change Current with matched inputs and effective runtime controls under the existing acceptance rules, preserving documented intentional semantic differences. Reject correctness/performance regressions, preserve failures, and never lower floors or cut scope to meet a date. Run focused checks per change and integrated checks daily, with parallel preparation and early native integration. Dates below are completion targets, not start dates or promises of one-day effort. The final runtime is one Rust executable for client/server modes, at most one separately loaded Rust library, and no JVM. Reserve November 1, 2026 for final-candidate verification, not a nineteenth porting goal; port completion alone does not establish separate hardware FPS/memory targets.

## 1. October 14, 2026: Shared gameplay data

Move remaining entity, item and biome definitions, components, synchronized data and stable identities into authoritative Rust owners. Preserve gameplay, save and packet representations, including supported custom fields. Complete this goal when retained content round-trips through native storage, serialization and synchronization without duplicate Java authority.

## 2. October 15, 2026: Remaining world generation

Move remaining carvers, features, structures, decoration, sequencing and chunk publication into Rust, reusing native noise, aquifers, surfaces and storage while preserving custom generation. Verify fixed-seed FULL chunks and metadata against Frozen, including cancellation and failure paths that must not publish partial results.

## 3. October 16, 2026: World ticks and chunk lifecycle

Move tick-phase ordering, scheduled block/fluid work, random-tick dispatch, weather, time, sleep, tickets, chunk promotion/unload and lighting coordination into Rust. Verify edits, boundary lighting, teleports and unloads through real world lifecycles; completion requires native world policy and orchestration, not Java scheduling around native storage.

## 4. October 17, 2026: Entity movement and lifecycle

Move travel, fluid movement, passengers, portals, spawning, projectiles, death/respawn and player state into Rust, reusing native collision and membership owners. Preserve every supported custom movement path and callback order. Verify native state serialization/synchronization and end-to-end movement, transfer and lifecycle behavior.

## 5. October 18, 2026: Living behavior, AI and combat

Move navigation, goals/brains, targeting, attributes, damage, effects, breeding, taming and species behavior into Rust. Implement and verify every retained behavior family, including Alex's Mobs, using representative interactions and deterministic traces. Completion requires native behavior execution with no required Java AI callback.

## 6. October 19, 2026: Items and inventories

Move item use, equipment, durability, containers/transfers, crafting, food, potions, tools, firearms and supported custom items into Rust. Preserve components, consumption, results and callback order. Verify client/server agreement across interactions, persistence and interrupted transfers, with no item duplication, loss or substituted behavior.

## 7. October 20, 2026: Blocks and server rules

Move block entities, redstone, fluids, neighbor updates, workstations, farming, commands/functions, advancements and statistics into Rust. Cover every supported family, chained updates and inventory-bearing blocks through gameplay and save tests. Native registration alone is insufficient: production behavior and server-rule execution must have native authority.

## 8. October 21, 2026: Persistence and upgrades

Move save orchestration, write ordering, entity/player/POI/level persistence, flushing, shutdown and supported-version upgrades into Rust while retaining the current format. Verify old and newly written worlds reopen losslessly, including IO failures and interrupted saves. Unsupported versions must fail explicitly rather than silently losing or reinterpreting data.

## 9. October 22, 2026: Packet codecs

Move framing, serialization, compression, protocol data and supported custom-payload codecs into Rust using the shared native components. Verify golden packets, fragmented streams and malformed inputs with bounded memory and preserved errors. Production encoding and decoding must no longer require a Java codec.

## 10. October 23, 2026: Live networking

Move transport, protocol transitions, connection lifecycle, dispatch, reconnects, timeouts and backpressure into Rust. Verify native integrated and dedicated clients/servers joining, moving, interacting, exchanging chunks and synchronizing inventories, including disconnect/recovery paths. Completion requires live native networking without Netty or Java dispatch.

## 11. October 24, 2026: Resource and content reloads

Move remaining codecs, registry publication, contextual lookup and data/resource-pack reload orchestration into Rust, reusing native pack, atlas and model owners. Verify coherent generations across consumers and repeated reloads; failed reloads must preserve a valid prior state. Native parsing alone does not complete publication or lifecycle ownership.

## 12. October 25, 2026: UI, text and HUD

Move menus, widgets, text layout/bidi, focus, chat, tooltips, inventory screens, HUD, VoxelMap and the recipe viewer into native producers and behavior. Verify keyboard, mouse and text input, scaling, resizing, screen transitions and reloads. Rust GUI quads alone do not establish native UI ownership or usable parity.

## 13. October 26, 2026: Scene and effect producers

Move remaining entity/item/hand extraction, animation, particle simulation/providers, weather, camera and material/tint production into Rust. Preserve interpolation, gameplay-driven behavior and frame lifetimes through ordinary play and transitions. Scene inputs must originate from native owners; replacing only their transport or final rendering is insufficient.

## 14. October 27, 2026: Renderer and shader gaps

Close remaining ordering, compatibility, reload and lifetime gaps across transparency, hands, entities, particles, HUD and shader transitions. Move remaining rendering policy into Rust without hidden Java fallbacks. Verify ordinary gameplay, the first shader-world frame and first-turn captures, preserving strict visual comparisons and performance floors.

## 15. October 28, 2026: Complete DH runtime ownership

Move remaining DH generation/retrieval queues, tree orchestration, database work, update publication, contextual callbacks and shutdown into Rust, reusing native generation, sources, conversion and builds. Verify edits, movement, dimension changes, reopen, draining and sustained streaming with correct lifetimes. Native geometry alone does not complete the DH runtime.

## 16. October 29, 2026: Audio and client services

Move remaining audio IO, contextual decisions, custom tickables, device handling, subtitles and supported client-service orchestration into Rust while preserving intentional exclusions. Verify physical output, reloads, device loss/recovery and shutdown with pending work. Null-sink policy tests alone cannot establish audible, recovery or lifecycle acceptance.

## 17. October 30, 2026: Native application and platform

Move client/server loops, window/input handling, configuration, paths, timers, workers, errors and shutdown into the native application. Integrate already ported systems continuously and test early rather than postponing startup until this date. One executable must run both modes without Java bootstrap, with verified platform and lifecycle behavior.

## 18. October 31, 2026: JVM removal and integrated acceptance

Remove required Java, Fabric, JNI and FFM launch/runtime dependencies and package the Rust application. On a clean host without Java, verify both modes, save/load, ordinary play, reconnects, reloads, dimensions and shutdown, then perform the November 1 final-candidate check. No required Java owner, missing retained feature or release-blocking correctness/performance regression may remain.
