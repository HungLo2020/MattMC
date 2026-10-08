# Rust game model (proposal)

> **Status: proposal; Phase 1 implemented.** These pages describe how blocks,
> block states, items, block entities and entities should be represented in
> Rust. Phase 1, the [Rust block registry](RUST-BLOCK-REGISTRY.md), is current
> behavior; later definitions, behavior and component systems remain proposals.
> “Implemented” describes source ownership, not completion of every acceptance
> check. See the [verification scope](BLOCK-REGISTRY-VERIFICATION.md).
> The surveys behind the proposal are dated 2026-10-07.

## End state

Java is removed completely. Rust owns the block, item and entity
definitions, and the gameplay that uses them. The Java↔Rust stages in the
[migration plan](MIGRATION-PLAN.md) are only a way to get there with parity
checked at every step. Design choices are made for the Rust-only game, not
for the bridge.

## Goals

1. **Adding content stays easy.** A new block or item should take one Rust
   file plus its assets. It should not mean edits across a dozen registries.
2. **Rust-native.** Use plain data, IDs, traits and composition, not
   inheritance chains or object graphs.
3. **Fast hot paths.** Lighting, collision, meshing, worldgen and saving read
   compact per-state tables and never make virtual calls.
4. **Exact parity.** Rust and Java agree on every ID, state, property value
   and saved byte. Behavior stays identical to vanilla until well after the
   migration; no deliberate deviations are planned before then.
5. **Vanilla worlds stay compatible.** The save format (Anvil regions, NBT)
   stays as it is, and old worlds keep loading, including through the data
   fixers.

## Decisions (2026-10-07)

| Question | Decision |
|---|---|
| Save compatibility | Required, long term. The format does not change; data fixers are eventually ported too. |
| Network compatibility with vanilla | Not a goal. MattMC already has blocks vanilla lacks, so only MattMC clients and servers talk to each other. |
| Behavior parity | Exact, through the migration and well beyond it. |
| Extensibility | No third-party mods or plugin API. Content is added in this repository, and that must stay easy. |
| Data vs code | What vanilla defines in data files (models, loot, recipes, tags, datapack registries) stays in data files. What Java defines in code (`Blocks.java`, `Items.java`) becomes Rust definitions. |
| Integrated mods | Alex's Caves, Alex's Mobs and TaCZ content stays in the `minecraft` namespace as first-class content, not a separate layer. |
| Threading | Follow Java's model as it is. |
| Client and server | Like vanilla: an integrated server in single-player, with separate client and server worlds sharing the same definitions. |
| State ID width | 16 bits (`StateId(u16)`), with a startup check against the 65,536 ceiling in the original proposal. See the implemented limit below. |
| Dependencies | No off-the-shelf ECS. New crates need the user's approval; the component store is written in-house. |
| Rendering | The rendering agent keeps working. Once this plan is final, it is reconciled with that agent's work before Phase 1 touches render tables. |
| Scope now | Blocks, block states and items in detail. Entities stay high-level. |

The original 16-bit decision described the theoretical 65,536-value space.
The [implemented registry](RUST-BLOCK-REGISTRY.md#constraints) reserves
`0xffff` for “no state,” so it permits at most **65,535 registered states**.
Exceeding that limit makes installation decline; it does not implement the
proposal's suggested loud startup failure.

Rendering reconciliation has since reached terrain meshing: block facts come
from the shared registry, while rendering keeps models, materials, passes,
shader-pack IDs, tint and sprites, with a separate cache lifecycle. This updates
the implementation status without changing the recorded coordination decision.

## Core idea in one paragraph

Content is **data in registries**: blocks, states, items and entity types are
dense IDs into frozen tables, built once at startup. Hot code reads the
tables. Rare and complex **behavior** (ticks, interaction, placement) lives in
small trait implementations, one per behavior family. The proposed reuse is
roughly a hundred behaviors for the 1,235 registered blocks; the original
Java class survey is described in [block behavior](BLOCK-BEHAVIOR.md). **Per-instance data**
(a chest's items, a mob's health, a sign's text) lives in **components**. Mobs,
items on the ground, minecarts *and* block entities are all entities in the
same component store. A block entity is simply an entity with a `BlockAnchor`
component, so a chest and a chest minecart share one `Inventory` component and
one set of systems.

## Pages

- [Registries and IDs](REGISTRIES-AND-IDS.md): ID types, registry layout,
  ordering and the single source of truth.
- [Block states](BLOCK-STATES.md): property encoding, per-state tables and
  shapes.
- [Block behavior](BLOCK-BEHAVIOR.md): behavior traits, families, the world
  context, and keeping hot paths free of dispatch.
- [Components and entities](COMPONENTS-AND-ENTITIES.md): the component store,
  block entities as anchored entities, tick order and persistence.
- [Items](ITEMS.md): item definitions, stacks and data components.
- [Adding content](ADDING-CONTENT.md): what adding a block or item looks like,
  compared with today.
- [Migration plan](MIGRATION-PLAN.md): the order to build this alongside Java,
  parity checks, and open decisions.
- [Rust block registry](RUST-BLOCK-REGISTRY.md) (current): how Phase 1 works,
  how to add a column or consumer, constraints and tests.
- [Block registry verification](BLOCK-REGISTRY-VERIFICATION.md) (current):
  Phase 1's parity and benchmark results.

## Where it lives in the crate

`content/` (registries and definitions) and `gameplay/` (behavior and systems)
are the owners in the [project architecture](../PROJECT-ARCHITECTURE.md).
`content/block/` holds the implemented block registry. `gameplay/tacz/` already
contains the TaCZ function-modifier kernel; the proposed block-behavior and
component systems are not implemented. `world/`, `storage/` and terrain meshing
read shared block facts from `content/`, while each consumer owns its view.
