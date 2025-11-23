
# RENDERING REFACTOR PLAN

> **Goal:** Remove direct OpenGL calls from arbitrary gameplay / engine classes and route all rendering through a small, well-defined abstraction layer.  
> This should **prepare** (but **not yet implement**) support for multiple graphics backends (OpenGL now, Vulkan later) and enable **headless tests** that do not require an OpenGL context so both you and Copilot Agent can debug rendering logic via terminal output and unit tests.

---

## 0. Scope & Principles

### 0.1 What we are changing

- Many classes in the project currently call OpenGL directly (e.g. `glBind*`, `glDraw*`, `glUseProgram`, etc.).
- This makes:
  - Testing and debugging hard without a window / GL context.
  - Copilot Agent unable to run or reason about rendering code in a headless environment.
  - Future backend changes (e.g. Vulkan) significantly more painful.

### 0.2 What we are **not** doing (yet)

- **We are NOT implementing Vulkan now.**
- We are NOT changing high-level game logic (world/chunks/entities) semantics.
- We are NOT rewriting the entire renderer in one step.

Instead, we will:

1. Introduce an abstraction layer for rendering.
2. Incrementally migrate existing OpenGL code into a single backend implementation.
3. Add headless / GL-free tests and tools.

---

## 1. Target Architecture (Conceptual)

The renderer should be split into three conceptual layers:

1. **Game / World Layer**  
   - Blocks, chunks, entities, items, lighting data, etc.  
   - No direct knowledge of OpenGL, Vulkan, or GPU APIs.

2. **Rendering Front-End (API-agnostic logic)**  
   - Decides **what** to draw:
     - Chunk meshes, item models, UI quads, etc.
   - Computes:
     - Mesh references, transforms, materials, per-vertex light colors, etc.
   - Produces a list of **abstract draw commands** (data only, no GL calls).

3. **Rendering Back-End (API-specific)**  
   - Knows **how** to draw using a real graphics API:
     - OpenGL backend now.
     - Vulkan backend in the future (not implemented yet, but the design should allow it).
   - Translates abstract draw commands into real draw calls.

### 1.1 `DrawCommand` (Conceptual)

A `DrawCommand` represents a single “thing to draw” in an API-agnostic way:

```java
// NOTE: This is conceptual / pseudo-code.
// Copilot Agent must inspect the repo and adapt names & fields to fit existing code.
final class DrawCommand {
	int meshId;          // handle/index into a mesh table owned by the renderer
	int materialId;      // handle/index into a material/shader table
	int transformIndex;  // index into a transform buffer or array
	RenderPass pass;     // OPAQUE, TRANSLUCENT, SHADOW, UI, etc.
	// Optional flags: wireframe, debug overlays, etc.
}
```

- The **front-end** fills an array/list of `DrawCommand`s each frame.
- The **back-end** iterates that list and issues API-specific calls.

### 1.2 `RenderBackend` Interface (Conceptual)

```java
// API-agnostic rendering backend interface
interface RenderBackend {
	void beginFrame();
	void submit(DrawCommand cmd);
	void endFrame();
}
```

Concrete implementations:

- `OpenGLRenderBackend` – the only backend that actually calls OpenGL.
- `DebugRenderBackend` – headless backend that **records or prints** commands for testing (no GL).
- (Future) `VulkanRenderBackend` – **must not be implemented yet**, but the design should make it easy.

---

## 2. High-Level Refactor Strategy

We will refactor in **small, testable stages** so Copilot Agent can handle it step by step.

### Stage 0 – Repository Scan & Mapping

**Objective:** Understand where OpenGL calls are and how rendering flows today.

**Tasks for Copilot Agent:**

1. **Search for all OpenGL usage**:
   - Locate all `gl*` calls (e.g. `glBindBuffer`, `glDrawElements`, `glUseProgram`, etc.).
   - Group them by class (e.g., chunk renderer, item renderer, UI renderer, etc.).
2. **Map the main render loop**:
   - Identify the main “frame entry point”, e.g. `Client.render()`, `WorldRenderer.render()`, or equivalent.
   - Document roughly which subsystems are called: world, chunks, entities, items, UI.
3. **Create a short summary in a comment or doc**:
   - Where GL calls live.
   - Which classes do both logic (meshing, transforms) and GL work.

> This stage is purely research / documentation. No behavior changes yet.

---

### Stage 1 – Introduce Core Abstractions (No Behavior Change)

**Objective:** Add the **interfaces and data structures** needed for abstraction without changing existing logic.

**Tasks:**

1. **Add a `RenderPass` enum** (or equivalent if one already exists):

   ```java
   enum RenderPass {
   	OPAQUE,
   	TRANSPARENT,
   	SHADOW,
   	UI
   }
   ```

2. **Create a `DrawCommand` class** in a neutral package (e.g. `client.renderer`):
   - Fields:
     - `meshId` (int or similar)
     - `materialId` (int or similar)
     - `transformIndex` (int)
     - `RenderPass pass`
   - Keep it simple and POD-like (no GL handles).

3. **Create a `RenderBackend` interface**:
   - Methods:
     - `beginFrame()`
     - `submit(DrawCommand cmd)`
     - `endFrame()`
   - No OpenGL imports / types in this interface.

4. **Add TODO-style comments**:
   - Note that this abstraction is intended to support **future Vulkan backend**, but Vulkan must **not** be implemented yet.
   - Clarify that GL-specific details must not leak into these core types.

> At the end of Stage 1, nothing should be using these yet; they just exist.

---

### Stage 2 – Implement `OpenGLRenderBackend`

**Objective:** Move OpenGL calls into one place behind `RenderBackend`.

**Tasks:**

1. **Create `OpenGLRenderBackend` class**:
   - Implements `RenderBackend`.
   - Holds references to:
     - Mesh / buffer managers
     - Shader / material managers
     - Any other GL state wrappers the engine already has.
   - In `submit(DrawCommand cmd)`:
     - Translate `meshId`/`materialId`/`transformIndex` into:
       - Bound VAOs / VBOs
       - Bound shader program
       - Uniform / UBO / SSBO state
     - Issue appropriate `glDraw*` calls.

2. **For now**, this backend can be thin wrappers around existing GL behavior:
   - It is acceptable temporarily to call existing GL helper methods (e.g. `Mesh.bindAndDraw()`).
   - The goal is *localizing* GL calls, not fully optimizing yet.

3. **Do NOT wire this into the main render loop yet.**
   - This class can be compiled but unused until next stages.

---

### Stage 3 – Split Chunk Rendering into Logic + Backend

**Objective:** Remove direct GL calls from chunk rendering classes and route through `RenderBackend`.

**Tasks:**

1. **Identify the chunk rendering entry point**:
   - E.g. `ChunkRenderer.renderChunks(...)` or equivalent.
   - Find where it currently:
     - Binds VAOs/VBOs.
     - Sets shaders.
     - Calls `glDraw*`.

2. **Create a “chunk rendering logic” class** (name may differ):
   - Example name: `ChunkRenderLogic`, `WorldRendererFrontEnd`, etc.
   - Responsibilities:
     - Determine which chunks are visible.
     - For each visible chunk, determine:
       - Which mesh to use.
       - Which material/shader.
       - Chunk transform relative to camera.
     - Create and fill `DrawCommand` objects for each chunk.

   Conceptual pseudo-code:

   ```java
   class ChunkRenderLogic {
   	void buildCommands(World world, Camera camera, CommandBuffer out) {
   		for (Chunk chunk : world.getVisibleChunks(camera)) {
   			int meshId = meshRegistry.getIdForChunk(chunk);
   			int materialId = materialRegistry.getIdForChunk(chunk);
   			int transformIndex = transforms.store(chunk.getModelMatrix());

   			DrawCommand cmd = new DrawCommand(meshId, materialId, transformIndex, RenderPass.OPAQUE);
   			out.add(cmd);
   		}
   	}
   }
   ```

3. **Introduce a simple “command buffer” type** (if helpful):
   - A thin wrapper around `List<DrawCommand>` to avoid passing lists around directly.

4. **Modify the main render loop**:
   - Instead of chunk renderer calling GL directly:
     - Create a `CommandBuffer` or `List<DrawCommand>`.
     - Call chunk logic to fill it.
     - Call `renderBackend.beginFrame()`.
     - Submit each command: `renderBackend.submit(cmd)`.
     - Call `renderBackend.endFrame()`.

5. **Remove or stub out direct GL calls from chunk classes**:
   - They should now only deal with meshes/data, not GL APIs.

> After Stage 3, chunks should be rendered exclusively via `RenderBackend`.

---

### Stage 4 – Split Item / UI Rendering into Logic + Backend

**Objective:** Apply the same pattern to item rendering, GUI rendering, etc.

**Tasks:**

1. **Identify item rendering and UI rendering entry points**:
   - E.g. `ItemRenderer`, `GuiRenderer`, inventory / hotbar rendering, etc.
   - Find where these classes perform:
     - Item model transformations.
     - Direct GL calls (matrix setup, draw calls).

2. **Extract “logic-only” parts**:
   - Example: `ItemRenderLogic` class that:
     - Computes the appropriate transform for each item (GUI, first person, third person).
     - Selects the correct mesh/model + material for each item stack.
     - Fills `DrawCommand`s for items and UI quads.

3. **Route draw calls via `RenderBackend`**:
   - After building commands for items/UI, submit them through the same backend.
   - Ensure items/UI use appropriate `RenderPass` values (e.g. `UI` or `TRANSPARENT` as needed).

4. **Remove direct GL calls** from item/UI logic classes.

> After Stage 4, all “normal” rendering (world, items, UI) should go through `RenderBackend`.

---

### Stage 5 – Centralize Render Pass Ordering

**Objective:** Ensure rendering order is defined in one place, independent of GL.

**Tasks:**

1. **Create a central “frame renderer” or “render pipeline”**:
   - Responsible for:
     - Creating command buffers.
     - Calling chunk/item/UI logic in the right order.
     - Submitting commands grouped by `RenderPass`.

2. **Define a simple order**, e.g.:

   ```text
   1. SHADOW pass (if implemented)
   2. OPAQUE
   3. TRANSPARENT
   4. UI
   ```

3. **In this central renderer**, call:

   ```java
   backend.beginFrame();

   // Build commands
   ChunkRenderLogic.buildCommands(..., commands);
   ItemRenderLogic.buildCommands(..., commands);
   UIRenderLogic.buildCommands(..., commands);

   // (Optional) sort commands by pass/material for efficiency
   for (RenderPass pass : RenderPass.values()) {
   	for (DrawCommand cmd : commandsForPass(pass)) {
   		backend.submit(cmd);
   	}
   }

   backend.endFrame();
   ```

4. Ensure no other part of the codebase calls GL directly.

---

## 3. Headless / No-OpenGL Testing Strategy

The key requirement: **Copilot Agent and you must be able to test rendering logic without an OpenGL context or window.**

### 3.1 `DebugRenderBackend`

Create a backend implementation that **does not use OpenGL at all**:

```java
class DebugRenderBackend implements RenderBackend {

	private final List<DrawCommand> commands = new ArrayList<>();

	@Override
	public void beginFrame() {
		commands.clear();
	}

	@Override
	public void submit(DrawCommand cmd) {
		// Store a copy of the command for inspection
		commands.add(cmd);
	}

	@Override
	public void endFrame() {
		// no-op
	}

	public List<DrawCommand> getCommands() {
		return Collections.unmodifiableList(commands);
	}

	public void dumpToStdout() {
		for (DrawCommand cmd : commands) {
			System.out.println("DRAW mesh=" + cmd.meshId
				+ " material=" + cmd.materialId
				+ " transformIndex=" + cmd.transformIndex
				+ " pass=" + cmd.pass);
		}
	}
}
```

- This backend can be used:
  - In **unit tests**.
  - In a **headless CLI mode** (e.g. `--headless-dump-frame`) that prints rendered commands to the terminal.

### 3.2 Unit Tests Without OpenGL

Create tests that use `DebugRenderBackend` and logic classes only:

#### 3.2.1 Chunk meshing / rendering tests

- Construct small test worlds / chunks in code:
  - Example: a single solid block, a plus-shape, stairs, slabs, etc.
- Run:

  ```java
  DebugRenderBackend backend = new DebugRenderBackend();
  FrameRenderer renderer = new FrameRenderer(backend); // central pipeline
  renderer.render(testWorld, testCamera);
  ```

- Assertions:
  - Number of draw commands for expected chunks.
  - That certain chunks produce at least one command.
  - That correct `RenderPass` is used.

#### 3.2.2 Model / UV / transform tests

- For item rendering:
  - Create an item stack.
  - Call item render logic with a GUI display context and `DebugRenderBackend`.
  - Assert:
    - A command is submitted.
    - The correct mesh/material IDs are used.
    - Transform index or associated matrix matches expected orientation (if accessible via a test hook).

#### 3.2.3 Golden-file tests (optional)

- For more complex scenes:
  - Run rendering once with `DebugRenderBackend`.
  - Serialize `commands` to JSON and save as a “golden” file in the repo.
  - Tests:
    - Regenerate commands.
    - Compare to golden file (exactly or with some tolerance).
  - This makes regressions visible as text diffs.

### 3.3 Headless CLI Mode for Copilot

Add a simple CLI entry point or debug flag:

- E.g. `--headless-dump-frame`, `--test-scene <name>`.

Behavior:

1. Create a minimal world / scene (or load one).
2. Create a `DebugRenderBackend`.
3. Run the frame pipeline once.
4. Print commands to stdout or write JSON to disk.

This allows Copilot Agent to:

- Run the command in a terminal-only environment.
- Inspect outputs as pure text.
- Debug “why does this block/item/model not render correctly?” without a GL context.

---

## 4. Vulkan Considerations (No Implementation Yet)

The abstraction is designed to be **compatible** with Vulkan in the future, but the actual Vulkan backend **must not** be implemented as part of this refactor.

Design constraints to keep Vulkan feasible later:

1. **No raw GL handles in shared structures**:
   - `DrawCommand`, mesh registries, material registries, etc. should use opaque IDs or references.
   - OpenGL backend maps IDs → VAOs, VBOs, textures, shaders internally.
   - Vulkan backend would map the same IDs → Vulkan buffers, pipelines, descriptors.

2. **Render passes defined at a higher level**:
   - The `RenderPass` enum and “frame pipeline” should not depend on GL-specific concepts.
   - Vulkan can later map these passes to subpasses / attachments as needed.

3. **Resource lifetime centralized**:
   - Mesh / texture / shader creation and destruction should be centralized.
   - Avoid ad-hoc creation/deletion of GL resources scattered around the codebase.

4. **Explicit note in code / docs**:
   - Add comments or doc notes stating:
     - “This backend abstraction is intended to support a future Vulkan backend.”
     - “Vulkan is not to be implemented yet; focus on OpenGL + testability first.”

---

## 5. Recommended Task Breakdown for Copilot Agent

To avoid overwhelming the agent, tasks should be given in **small, sequential chunks**. For example:

1. **Task 1: Scan and document OpenGL usage**
   - Output a summary file or comments listing classes and methods with GL calls.

2. **Task 2: Add `RenderPass`, `DrawCommand`, and `RenderBackend`**
   - No behavior changes, just new types and minimal comments.

3. **Task 3: Implement `OpenGLRenderBackend`**
   - Wire it to existing mesh/material managers without changing call sites yet.

4. **Task 4: Refactor chunk rendering to use `DrawCommand` + `RenderBackend`**
   - Move GL calls from chunk-related classes into `OpenGLRenderBackend`.
   - Ensure world/chunk code no longer calls GL directly.

5. **Task 5: Refactor item / UI rendering similarly**
   - Same idea for items, inventory, UI elements.

6. **Task 6: Introduce `DebugRenderBackend` and a simple headless CLI mode**
   - Implement backend that records commands.
   - Add a `main` or debug entrypoint that runs one frame with this backend and prints the commands.

7. **Task 7: Write initial unit tests using `DebugRenderBackend`**
   - At least:
     - One chunk rendering test.
     - One item rendering test.
   - Focus on verifying that commands are generated (no GL context required).

8. **Task 8: Cleanup and documentation**
   - Remove any leftover direct GL usage outside the backend.
   - Add inline documentation pointing to this `RENDERINGREFACTOR.md`.
   - Add TODOs for future Vulkan backend once the system is stable.

---

## 6. Final Notes

- This refactor should **not** significantly impact performance if implemented with care:
  - `DrawCommand` objects should be simple and reuse existing data wherever possible.
  - Avoid unnecessary allocations in the per-frame hot path.
- The **main benefit** is:
  - Testability (headless, CI, Copilot Agent).
  - Cleaner separation of concerns.
  - A solid foundation for future Vulkan (or other backend) support.

Copilot Agent should treat this document as a **conceptual guide**, not exact code.  
It must inspect the actual project structure (class names, packages, existing render code) and adapt these patterns to fit the current design.
