# Remaining Files with OpenGL Imports

## Status: 16 Files Still Using OpenGL Outside backend/opengl

This document lists all files in the project that still contain OpenGL imports but are NOT located in the `mattmc.client.renderer.backend.opengl` package.

**According to the architecture rules, ALL files that import `org.lwjgl.opengl.*` MUST be in the backend/opengl package.**

---

## Files That Need To Be Moved or Refactored

### 1. Core Window Management

**File:** `src/main/java/mattmc/client/Window.java`

**OpenGL Usage:**
- `glDisable(GL_DEPTH_TEST)`
- `glEnable(GL_BLEND)`, `glBlendFunc()`
- `glViewport()`, `glMatrixMode()`, `glLoadIdentity()`, `glOrtho()`

**Purpose:** GLFW window initialization and OpenGL context setup

**Status:** ⚠️ Infrastructure - needs evaluation (may need to stay for GLFW setup)

---

### 2. GUI Component Renderers (4 files)

#### `src/main/java/mattmc/client/gui/components/ButtonRenderer.java`
**OpenGL Usage:**
- `glEnable(GL_TEXTURE_2D)`, `glEnable(GL_BLEND)`, `glBlendFunc()`
- `glBegin(GL_QUADS)`, `glTexCoord2f()`, `glVertex2f()`, `glEnd()`
- Immediate mode rendering for buttons

**Status:** ❌ MUST MOVE - Contains direct OpenGL rendering

#### `src/main/java/mattmc/client/gui/components/TextRenderer.java`
**OpenGL Usage:**
- `glPushMatrix()`, `glTranslatef()`, `glScalef()`, `glPopMatrix()`
- Matrix transformations for text

**Status:** ❌ MUST MOVE - Contains OpenGL matrix operations

#### `src/main/java/mattmc/client/gui/components/TrueTypeFont.java`
**OpenGL Usage:**
- `glBindTexture()`, `glTexImage2D()`, `glTexParameteri()`
- `glBegin(GL_QUADS)`, `glTexCoord2f()`, `glVertex2f()`, `glEnd()`
- Texture management and immediate mode rendering

**Status:** ❌ MUST MOVE - Contains direct OpenGL texture and rendering

#### `src/main/java/mattmc/client/gui/screens/AbstractMenuScreen.java`
**OpenGL Usage:**
- `glViewport()`, `glMatrixMode()`, `glLoadIdentity()`, `glOrtho()`
- Projection setup for menu screens

**Status:** ❌ MUST MOVE - Contains OpenGL projection setup

---

### 3. Screen Implementations (8 files)

All screen files use OpenGL for viewport setup and rendering:

1. **`ControlsScreen.java`** - `glViewport()`
2. **`CreateWorldScreen.java`** - Full projection setup + immediate mode drawing
3. **`DevplayInputHandler.java`** - Minimal OpenGL (just imports)
4. **`DevplayScreen.java`** - Uses OpenGL for projection and rendering
5. **`InventoryScreen.java`** - OpenGL viewport and rendering
6. **`PauseScreen.java`** - OpenGL viewport and UI rendering
7. **`SelectWorldScreen.java`** - OpenGL viewport and world preview rendering
8. **`TitleScreen.java`** - OpenGL viewport and menu rendering

**Status:** ❌ ALL MUST MOVE - All contain direct OpenGL calls

---

### 4. Inventory Renderer

**File:** `src/main/java/mattmc/client/gui/screens/inventory/InventoryRenderer.java`

**OpenGL Usage:**
- Full immediate mode rendering for inventory slots
- `glBegin/glEnd`, texture binding, matrix operations

**Status:** ❌ MUST MOVE - Contains extensive OpenGL rendering

---

### 5. Utility Files (2 files)

#### `src/main/java/mattmc/client/util/SystemInfo.java`
**OpenGL Usage:**
- Queries OpenGL strings (`glGetString()`)
- Gets GPU info for system information display

**Status:** ⚠️ Needs evaluation - May be infrastructure for diagnostics

#### `src/main/java/mattmc/util/ColorUtils.java`
**OpenGL Usage:**
- `glColor3f()`, `glColor4f()` calls
- Color utility functions

**Status:** ❌ MUST MOVE or REFACTOR - Contains OpenGL calls

---

## Summary Statistics

| Category | Count | Status |
|----------|-------|--------|
| Window/Infrastructure | 1 | Needs evaluation |
| GUI Components | 4 | Must move |
| Screen Implementations | 8 | Must move |
| Inventory Renderer | 1 | Must move |
| Utilities | 2 | Must move/refactor |
| **TOTAL** | **16** | **15 must be addressed** |

---

## Recommendations

### Immediate Action Required

1. **Move to backend/opengl:** All 15 non-infrastructure files
2. **Refactor where possible:** Extract logic from rendering
3. **Update imports:** Ensure all references are updated

### Files That May Stay (After Review)

- `Window.java` - If it's purely GLFW/context setup infrastructure
- `SystemInfo.java` - If it's purely diagnostic queries

### Next Steps

1. Create backend/opengl subdirectories for gui components
2. Move files systematically
3. Update all imports and package declarations
4. Verify build still works
5. Document any architectural decisions

---

## Architectural Notes

The goal is:
- **renderer logic** → backend abstraction → **backend.opengl** implementation
- NO OpenGL imports outside backend.opengl (except possibly core infrastructure)
- All game logic and high-level rendering should be API-agnostic

Current violation: 16 files with OpenGL imports outside backend/opengl
Target: 0 (or maybe 1 for Window.java if justified)
