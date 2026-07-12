# Project Architecture

This document describes the Rust source layout for MattMC. It intentionally does not mirror the Java package tree. Rust code is organized by subsystem ownership so the tree can grow toward the long-term native engine architecture.

## Rust Source Layout

```text
src/main/rust/
├── Cargo.toml
├── lib.rs
├── app/
├── assets/
├── compat/
├── content/
├── core/
├── gameplay/
├── network/
├── platform/
├── render/
│   ├── chunk/
│   │   ├── meshing/
│   │   ├── direct_trigger.rs
│   │   ├── gfni_trigger.rs
│   │   ├── index.rs
│   │   ├── occlusion.rs
│   │   ├── render_data.rs
│   │   ├── render_list.rs
│   │   └── translucent.rs
│   └── vulkanic/
│       └── backends/
│           ├── opengl/
│           └── vulkan/
├── tools/
└── world/
    └── level/
```

## Directory Responsibilities

### `app/`

Application-level orchestration belongs here. This is the intended home for future startup, lifecycle, configuration, and high-level runtime coordination that does not belong to a lower-level engine subsystem.

### `core/`

Shared engine primitives belong here. Use this for low-level types, algorithms, memory utilities, math, identifiers, and cross-subsystem foundations that are not specifically rendering, world, platform, or gameplay code.

### `world/`

World simulation and world data helpers belong here. Current Rust world code includes `world/level/color_map_color_util.rs`, which backs Java color-map behavior through native functions.

### `gameplay/`

Gameplay systems belong here. This is the intended home for native implementations of player, entity, item, combat, block interaction, and rule-driven behavior as those systems migrate from Java.

### `content/`

Content definitions and registries belong here. Use this for native representations of blocks, items, fluids, models, recipes, data-driven definitions, and other game content metadata.

### `render/`

Rendering systems belong here. This includes backend-independent render code, native render data structures, chunk rendering helpers, and backend-facing rendering subsystems.

Important current subdirectories:

- `render/chunk/`: native chunk-rendering infrastructure, render lists, occlusion, translucent sorting, index generation, and rebuild triggers.
- `render/chunk/meshing/`: native chunk mesher implementation, including section scanning, static models, fluids, lighting/AO, tinting, culling, packing, assembly, FFI records, and diagnostics.
- `render/vulkanic/`: Rust-side Vulkanic rendering support.
- `render/vulkanic/backends/`: private backend implementation modules. Code outside `render::vulkanic` must not call into backend modules directly.

### `assets/`

Asset loading, decoding, caching, and resource processing belong here. Future Rust-side texture, model, shader, language, and pack-resource work should live here when it is not exclusively part of a rendering backend.

### `network/`

Networking code belongs here. This is the future home for protocol, packet encoding/decoding, synchronization, and multiplayer transport work if those systems move into Rust.

### `platform/`

Platform abstraction belongs here. Use this for OS, filesystem, threading, native library, windowing-adjacent, and platform-specific integration that should not leak into game or render logic.

### `compat/`

Compatibility and interop layers belong here. Use this for bridge code that exists because Java, Fabric, shader packs, mods, or legacy systems still need adaptation during the incremental migration.

### `tools/`

Developer and offline tooling belongs here. This is for Rust code that supports build-time utilities, diagnostics, conversion tools, generators, or replay/benchmark helpers rather than runtime engine behavior.

## Boundary Notes

Rust `render/vulkanic/backends/` is intentionally private. The architecture tests enforce that:

- `render::vulkanic` owns backend routing.
- Rust code outside `render::vulkanic` cannot reference backend implementation modules.
- `ash` and `shaderc` usage stays inside the Vulkan backend.
- `glow` usage stays inside the OpenGL backend.
- OpenGL and Vulkan backend modules do not depend on each other.

Java package names may still appear in Java source and Java tests. The Rust tree should use subsystem ownership instead of Java-style package paths.
