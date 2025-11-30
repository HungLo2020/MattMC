# MattMC Project Restructure Plan

## Overview

This document outlines a plan to reorganize the MattMC codebase from a client/server model to an **engine/game** architecture. Since multiplayer is not planned, the current `client` package naming is misleading. The new structure will better reflect the project's single-player focus and separate reusable engine code from game-specific logic.

## Current Structure Analysis

```
src/main/java/mattmc/
├── client/              # Currently holds EVERYTHING client-side
│   ├── main/           # Entry point (Main.java)
│   ├── MattMC.java     # Game loop and screen management
│   ├── gui/            # User interface
│   │   ├── screens/    # Game screens (title, pause, options, etc.)
│   │   └── components/ # UI components (buttons, text boxes, etc.)
│   ├── resources/      # Resource management (models, textures)
│   ├── settings/       # Game settings and keybindings
│   ├── util/           # Client utilities
│   └── renderer/       # Rendering systems
│       ├── backend/    # Render backend abstraction
│       ├── chunk/      # Chunk rendering
│       ├── block/      # Block rendering
│       └── ... more renderer subpackages
├── world/              # World and level management (shared/core)
│   ├── entity/         # Entities
│   ├── item/           # Item system
│   ├── phys/           # Physics and collision
│   └── level/          # Level systems (chunks, blocks, lighting, etc.)
├── nbt/                # NBT data structures
└── util/               # General utilities
```

### Problems with Current Structure

1. **`client` package implies multiplayer** - Suggests client/server split that doesn't exist
2. **Unclear separation of concerns** - Engine code mixed with game logic
3. **`world` at root level** - Inconsistent with `client` package naming
4. **Resource management in `client`** - Could be more general engine code

---

## Proposed New Structure

```
src/main/java/mattmc/
├── engine/                     # Reusable engine code (graphics, input, resources)
│   ├── core/                   # Core engine systems
│   │   ├── MattMC.java         # Game loop, lifecycle (moved from client)
│   │   └── Main.java           # Entry point (moved from client/main)
│   ├── render/                 # All rendering systems
│   │   ├── backend/            # Render backend abstraction (from client/renderer/backend)
│   │   │   └── opengl/         # OpenGL implementation
│   │   ├── chunk/              # Chunk rendering
│   │   ├── block/              # Block rendering
│   │   ├── item/               # Item rendering
│   │   ├── level/              # Level/world rendering
│   │   ├── panorama/           # Panorama rendering
│   │   ├── shader/             # Shader management
│   │   ├── texture/            # Texture management
│   │   ├── window/             # Window management
│   │   ├── gui/                # GUI rendering primitives
│   │   └── *.java              # Top-level render utilities (WorldRenderer, etc.)
│   ├── resources/              # Resource loading/management
│   │   ├── ResourceManager.java
│   │   ├── model/              # Model loading
│   │   └── metadata/           # Resource metadata
│   ├── input/                  # Input handling (keyboard, mouse)
│   │   ├── KeybindManager.java # (moved from client/settings)
│   │   └── KeyNameParser.java
│   └── util/                   # Engine utilities
│       ├── CoordinateUtils.java
│       └── SystemInfo.java
│
├── game/                       # Game-specific code
│   ├── settings/               # Game settings (not input bindings)
│   │   └── OptionsManager.java # Game options (FOV, render distance, etc.)
│   ├── gui/                    # Game UI
│   │   ├── screens/            # All game screens
│   │   │   ├── Screen.java     # Base screen class
│   │   │   ├── TitleScreen.java
│   │   │   ├── GameScreen.java
│   │   │   ├── PauseScreen.java
│   │   │   └── ... other screens
│   │   ├── components/         # UI components
│   │   │   ├── Button.java
│   │   │   └── EditBox.java
│   │   └── SplashTextLoader.java
│   └── world/                  # Game world (moved from root)
│       ├── Gamemode.java
│       ├── entity/             # Entities
│       │   └── player/
│       ├── item/               # Items
│       ├── phys/               # Physics
│       └── level/              # Level management
│           ├── Level.java
│           ├── block/          # Block types
│           ├── chunk/          # Chunk system
│           ├── levelgen/       # World generation
│           ├── lighting/       # Light system
│           └── storage/        # Save/load
│
├── nbt/                        # NBT data structures (unchanged - shared utility)
│   ├── NBTUtil.java
│   ├── BitPackedArray.java
│   └── ...
│
└── util/                       # General shared utilities (unchanged)
    ├── AppPaths.java
    ├── ColorUtils.java
    ├── LightUtils.java
    ├── MathUtils.java
    ├── ResourceLoader.java
    └── Validate.java
```

---

## Migration Strategy

### Phase 1: Create New Package Structure (Low Risk)
1. Create `engine/` and `game/` directories
2. Create subdirectory structure within each

### Phase 2: Move Engine Code
Move files from `client/` to `engine/`:

| From | To |
|------|-----|
| `client/main/Main.java` | `engine/core/Main.java` |
| `client/MattMC.java` | `engine/core/MattMC.java` |
| `client/renderer/**` | `engine/render/**` |
| `client/resources/**` | `engine/resources/**` |
| `client/settings/KeybindManager.java` | `engine/input/KeybindManager.java` |
| `client/settings/KeyNameParser.java` | `engine/input/KeyNameParser.java` |
| `client/util/**` | `engine/util/**` |

### Phase 3: Move Game Code
Move files to `game/`:

| From | To |
|------|-----|
| `client/gui/**` | `game/gui/**` |
| `client/settings/OptionsManager.java` | `game/settings/OptionsManager.java` |
| `world/**` | `game/world/**` |

### Phase 4: Update Imports & References
1. Update all `import` statements across the codebase
2. Update `build.gradle.kts` main class: `mattmc.engine.core.Main`
3. Update any resource paths or reflection if used

### Phase 5: Update Tests
Mirror the new structure in `src/test/java/mattmc/`:
```
src/test/java/mattmc/
├── engine/
│   ├── render/
│   ├── resources/
│   └── util/
├── game/
│   ├── gui/
│   ├── settings/
│   └── world/
├── nbt/
├── util/
└── performance/
```

### Phase 6: Documentation Updates
1. Update README.md project structure section
2. Update any doc files referencing old paths

---

## Key Decisions & Rationale

### 1. Why `engine` + `game` instead of other options?

| Alternative | Reason Not Chosen |
|-------------|-------------------|
| `core` + `game` | "core" is too vague, doesn't convey graphics/rendering |
| `framework` + `app` | Too generic, sounds like a library not a game |
| `lib` + `main` | Misleading - this isn't a reusable library |
| `engine` + `game` | ✅ Clear: engine handles tech, game handles gameplay |

### 2. Why move `world` under `game`?

The world system is game-specific - it contains Minecraft-style chunks, blocks, items, and player logic. A different game would have completely different world representation. Engine code is meant to be theoretically reusable.

### 3. Why separate `input` from `settings`?

- **Input (engine)**: Low-level key binding, mouse handling - engine concerns
- **Settings (game)**: FOV, render distance, game-specific options - game concerns

### 4. Why keep `nbt` and `util` at root?

These are truly shared utilities that don't belong exclusively to engine or game:
- `nbt/`: Data serialization format, used by both world storage and potentially other systems
- `util/`: Generic utilities like math, color, validation

---

## Files to Update

### build.gradle.kts
```kotlin
application {
    mainClass.set("mattmc.engine.core.Main")  // was: mattmc.client.main.Main
}
```

### README.md
Update the Project Structure section to reflect new organization.

---

## Estimated Effort

| Phase | Complexity | Files Affected |
|-------|------------|----------------|
| Phase 1: Create directories | Low | 0 (new dirs only) |
| Phase 2: Move engine code | Medium | ~50+ files |
| Phase 3: Move game code | Medium | ~40+ files |
| Phase 4: Update imports | High | Nearly all files |
| Phase 5: Update tests | Medium | ~20+ test files |
| Phase 6: Documentation | Low | 2-3 files |

**Total: Medium-High effort, but manageable incrementally**

---

## Benefits After Refactor

1. **Clearer mental model** - Engine vs Game separation is intuitive
2. **No multiplayer confusion** - No more "client" implying servers
3. **Better organization** - Related code grouped logically
4. **Easier navigation** - Find rendering? Look in `engine/render`
5. **Future-proof** - If making a different game, could reuse engine

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Breaking existing code | Comprehensive testing after each phase |
| IDE confusion during move | Use IDE refactoring tools (IntelliJ "Move Package") |
| Merge conflicts | Do refactor in one focused session |
| Missing import updates | Compile after each phase to catch errors |

---

## Conclusion

This refactor is primarily organizational - no functional changes. The goal is clarity and maintainability. The `engine/game` split accurately reflects the single-player architecture and groups code by responsibility rather than by an obsolete client/server model.

**Recommendation**: Execute this refactor using IDE refactoring tools (IntelliJ's "Move" refactoring) which will automatically update imports. Manual file moves are error-prone for a codebase of this size.
