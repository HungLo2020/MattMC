# MATTMCvMC.md  
**Comparative Analysis of MattMC vs Minecraft Rendering, Geometry, and UV Systems**

---

## 1. Overview

This document compares the rendering architecture of **Minecraft: Java Edition** with **MattMC**, focusing on:

- JSON model & blockstate systems  
- Geometry and UV generation  
- Face ordering / winding & culling  
- Chunk rendering pipelines  
- Lighting models  
- Item rendering (especially inventory / GUI)  
- Class & responsibility mapping  

It also includes a **step-by-step plan to make MattMC behave more like vanilla Minecraft’s renderer**.

MattMC’s current system is already strongly inspired by Minecraft: JSON-based models, parent inheritance, texture atlases, chunk meshes, and smooth lighting are all present.  
Minecraft adds a more rigid **baked model abstraction**, a unified model pipeline for **both blocks and items**, and a more formalized split between **unbaked models, baked models, and render layers**.  

---

## 2. High-Level Architecture Comparison

### 2.1 Conceptual Pipelines

**Minecraft**

- **Assets**: blockstates, block models, item models, textures.  
- **Unbaked models**: `BlockModel` (implements `UnbakedModel`) parsed from JSON.  
- **Baker**: `ModelBakery` / `ModelLoader` → `BakedModel`s (lists of `BakedQuad`s).  
- **Registry**: `BakedModelManager` stores all baked models and provides block & item accessors.  
- **World rendering**: `BlockRenderDispatcher` & `BlockModelRenderer` tesselate `BakedModel`s into chunk buffers, managed by `ChunkRenderDispatcher` / `SectionRenderDispatcher`.  
- **Item rendering**: `ItemRenderer` + `ItemModels` use those same `BakedModel`s with `ItemDisplayContext` transforms.  

**MattMC**

- **Assets**: blockstates, block models, item models, textures – same conceptual split.  
- **Model resolution**: `ResourceManager` loads `BlockModel`, applies parent merging and texture variable resolution.  
- **Geometry generation**:  
  - Cube-like faces: `BlockFaceGeometry` / `BlockGeometryCapture`.  
  - Complex blocks: `StairsGeometryBuilder`, `TorchGeometryBuilder`, etc.  
- **World rendering**:  
  - `BlockFaceCollector` decides visible faces, including cross-chunk logic.  
  - `MeshBuilder` builds VBO-ready arrays, stored in `ChunkMeshBuffer` & rendered by `LevelRenderer`.  
- **Lighting**: `VertexLightSampler` + `WorldLightManager` provide smooth per-vertex lighting with **RGB block light**.  
- **Item rendering**: `ItemRenderer` uses **captured 3D geometry + isometric projection** and OpenGL immediate mode for block items; flat sprites for non-block items.  

**Big picture:**

- You already have 80% of the **conceptual shape** of Minecraft’s rendering system.  
- The main structural difference is that MC packs *everything* through a **BakedModel / BakedQuad abstraction**, a unified model pipeline for **both blocks and items**, and a clear multi-layer render pass system.

---

## 3. JSON Models & Blockstates

### 3.1 Blockstates

**Minecraft**

- Blockstate JSON maps **`BlockState` → (one or more model variants)** with rotations, UV lock, and weights.  
- Supports:
  - `variants`: property strings → model & rotation
  - `multipart`: conditional composition of multiple models  
- The renderer doesn’t know “stairs” or “torch” as special geometry; it just gets a *model choice* per state and lets the model decide geometry.  

**MattMC**

- Same overall structure:
  - Blockstate JSON maps block properties to model variants with `model`, `x/y/z` rotations, and `uvlock`.  
- Complex blocks (stairs, torches) still have custom **geometry builder classes** keyed off block state (`StairsGeometryBuilder`, `TorchGeometryBuilder`), even though they read model JSON for things like torch thickness.  

**Difference**

- Minecraft leans harder on JSON models + baked quads to express geometry – the blockstate only selects models.  
- MattMC uses blockstates similarly, but *some geometry* is still wired into code by block type, not just model definitions.

**Implication for convergence**

- Moving more geometry decisions into **models & baking**, and making the blockstate purely select models, brings you closer to MC.

---

### 3.2 Model JSON

**Minecraft**

- Uses `BlockModel` with `elements` `[from, to]`, per-face UVs, `textures`, `parent`, `ambientocclusion`, `display`, etc.  
- Parent chaining + texture variables (`#all`, `#side`) unify block and item models.  

**MattMC**

- JSON format is essentially the same:
  - `parent`, `textures`, `elements`, face `uv`, `cullface`, `tintindex`, `rotation`, `shade`, etc.  
- Parent resolving & texture variable resolution is implemented in `ResourceManager` using a recursive merge.  

**Difference**

- Structurally, you’re **already matching MC** very closely here.  
- The main gaps are *downstream*: how those `BlockModel`s are turned into a reusable, cached baked representation.

---

## 4. Geometry & UV Generation

### 4.1 Geometry

**Minecraft**

- `BlockModel#bake(...)` converts JSON `elements` into **`BakedQuad`s**, each quad being a 4-vertex strip with positions, UVs, face direction, tintIndex, and sprite reference.  
- Geometry is “frozen” into `BakedModel`, and later **`BlockModelRenderer`** reads quads from `model.getQuads(state, side, random)` to tesselate into buffers.  
- Complex shapes (stairs, fences, etc.) are expressed by model elements & alternate models, not separate code-based geometry builders (except some special-cased ones / custom loaders).

**MattMC**

- Blocks:
  - Cube faces via `BlockFaceGeometry` and `MeshBuilder`, building raw vertex arrays with full attributes.  
  - Custom shapes: `StairsGeometryBuilder`, `TorchGeometryBuilder` generating shapes procedurally.  
- Items:
  - Geometry is **captured** (`VertexCapture`) while using the same geometry functions (`BlockGeometryCapture`) and then projected to 2D.  

**Difference**

- Minecraft has a strong **BakedModel abstraction**; MattMC has **ad-hoc geometry builders** (per block type) and direct mesh building:
  - In MC, *every* model ends up as a `BakedModel` used by both blocks & items.
  - In MattMC, block geometry & item geometry share code but are not unified via a `BakedModel` object.

---

### 4.2 UV Mapping & Atlas

**Minecraft**

- UVs are defined in 0–16 units per face and are converted to sprite UV coordinates in the texture atlas.  
- `BakedQuad` stores atlas-relative UVs referencing a `TextureAtlasSprite`.  

**MattMC**

- UVs from JSON (0–16) → 0–1 → **remapped to atlas region** via `TextureAtlas.UVMapping`.  
- Atlas mapping: `atlasU = u0 + (u1-u0) * modelU`, same conceptual math as MC.  

**Difference**

- Conceptually almost identical; your UV machinery is already “MC-like”.

---

## 5. Face Ordering, Winding, and Culling

### 5.1 Winding & Normals

**Minecraft**

- Uses consistent vertex order per face (typically CCW for front faces) to cooperate with OpenGL backface culling and directional normals.  
- `Direction` (N/S/E/W/UP/DOWN) plus precomputed vertex arrangement define quads.  

**MattMC**

- Explicitly uses **counter-clockwise winding** for all faces when viewed from outside.  
- Splits quads into two triangles; sets per-face normals (0,±1,0 etc.).  

**Difference**

- None that matter: you already match MC’s approach.

### 5.2 Face Culling

**Minecraft**

- Culling is done at chunk tesselation:
  - `BlockModelRenderer` checks neighbor block’s shape/opacity; if fully occluding, the face is skipped.  

**MattMC**

- `BlockFaceCollector` checks neighbor blocks, handles cross-chunk queries via `ChunkNeighborAccessor`, and only collects faces that are visible (air or non-solid neighbor).  

**Difference**

- Again, you’re already in the same ballpark; MC uses more advanced voxel shape logic, but conceptually it’s the same algorithm.

---

## 6. Chunk Rendering

### 6.1 Minecraft

- **Chunk renderer** (e.g. `ChunkRenderDispatcher` / `SectionRenderDispatcher`) owns chunk sections.  
- For each section:
  - `BlockRenderDispatcher` → `BlockModelRenderer` tesselate `BakedModel` quads into buffers keyed by **render layer** (`ChunkSectionLayer` / `RenderType`).  
- Vertex data is kept in VBOs; draw calls are batched by layer and sorted by distance or CPU-managed ordering.

### 6.2 MattMC

- `BlockFaceCollector` → `MeshBuilder` creates vertex & index arrays → `ChunkMeshBuffer` (VBO/EBO + VAO).  
- `LevelRenderer` binds atlas, shader, VAO and calls `glDrawElements` per chunk.  

**Differences**

- MC uses **RenderType / ChunkSectionLayer** to separate solid, cutout, translucent, etc., with dedicated passes.  
- MattMC currently has a single “chunk mesh” path (you can add layers, but they’re not fully MC-like yet).

---

## 7. Lighting

### 7.1 Minecraft

- Uses **two channels**: `skyLight` and `blockLight` (0–15 each).  
- Block light is not RGB; colored lighting isn’t in vanilla.  
- Vertex lighting & AO are applied during tesselation, similar to your `VertexLightSampler` logic but with more complex rules and no full RGB.  

### 7.2 MattMC

- **Sky Light**: 0–15.  
- **Block Light**: full RGB with intensity; stored as 4-bit per channel + intensity and propagated through the world.  
- `VertexLightSampler` samples 4 neighbor positions per vertex, averages non-zero values to avoid “darkening by solids,” and passes `[skyLight, blockLightR, blockLightG, blockLightB, ao]` into a shader.  

**Differences**

- You are **strictly more advanced** than vanilla regarding color lighting.  
- Convergence to vanilla is more about *behavior* (how light propagates/decays, how AO is computed) than structure.

---

## 8. Item Rendering (GUI, Hotbar, Held)

### 8.1 Minecraft

- `ItemRenderer` & `ItemModels` fetch `BakedModel` for an `ItemStack`, apply **model overrides**, then render the model using `ItemDisplayContext` transforms (GUI, first-person, third-person, ground, fixed, head).  
- Items and blocks share:
  - Same `BakedModel` / `BakedQuad` data.  
  - Same atlas.  
- The difference is purely **which transform & lighting** is applied.  
- GUI items are usually rendered as:
  - 3D models with perspective (for `isGui3d()`), or  
  - Flat quads for `item/generated` style models.

### 8.2 MattMC

- Uses **two different item paths**:  
  - **Block items**:
    - Capture 3D geometry via `VertexCapture` + `BlockGeometryCapture`.  
    - Project to 2D using an **isometric projection** (SW→NE) and render with immediate mode (`glBegin/glEnd`), applying per-face brightness (top 100%, west 80%, north 60%).  
  - **Flat items**:
    - Render as 2D quads using a single texture (layer0).  
- Items and blocks share **geometry functions**, but not a `BakedModel`-style abstraction.

**Differences**

- Minecraft: *exact same model abstraction* used for both world & items.  
- MattMC: items use **captured geometry + custom isometric view**, independent from the chunk mesh pipeline.

This is the single biggest conceptual difference between your system and vanilla.

---

## 9. Class & Responsibility Mapping

| Concept | Minecraft | MattMC |
|--------|-----------|--------|
| Model registry & baking | `ModelLoader` / `ModelBakery`, `BakedModelManager` | `ResourceManager` + `BlockModel` (unbaked), no central *baked* registry yet |
| Unbaked model | `BlockModel` (implements `UnbakedModel`) | `BlockModel` (JSON representation) |
| Baked model abstraction | `BakedModel` → `BakedQuad` lists | *Not yet formalized*; geometry goes direct to `MeshBuilder` or `VertexCapture` |
| Blockstate → model mapping | Blockstate JSON + `BlockModels` / `BlockModelShaper` | Blockstate JSON + `BlockState` + custom builders (stairs, torch) |
| World block renderer | `BlockRenderDispatcher` + `BlockModelRenderer` | `BlockFaceCollector` + `MeshBuilder` + `LevelRenderer` |
| Chunk representation | `ChunkRenderDispatcher.RenderChunk` / `SectionRenderDispatcher` | `ChunkMeshBuffer` + chunk-level VBO / VAO |
| Texture atlas | `SpriteAtlasManager` + `TextureAtlasSprite` | `TextureAtlas` + `UVMapping` |
| Item → model mapping | `ItemModels` (maps `Item` → `ModelIdentifier` → `BakedModel`) | `ItemRenderer` + ResourceManager; uses model JSON but not via a `BakedModel` layer |
| Item renderer | `ItemRenderer` (3D/2D depending on model & context) | `ItemRenderer` using isometric 3D projection for block items; immediate mode rendering |
| Light engine | `LightEngine` (sky/block, non-RGB) | `WorldLightManager` (sky + RGB block light) |

---

## 10. Step-by-Step Plan to Make MattMC More “Minecraft-Like”

Below is a concrete **roadmap**, in phases, that you can feed into Copilot Agent piece-by-piece. Each step references your existing classes so it should be easy to hook in.

### Phase 1 – Introduce a BakedModel / BakedQuad Layer

**Goal:** Add a `BakedModel` abstraction in MattMC that sits between `BlockModel` and the chunk/item renderers, mirroring Minecraft.

#### Step 1.1 – Define BakedQuad

Create a `BakedQuad` class:

- Fields:
  - `float[] vertices` (or structured type) with position + UV + normal + maybe pre-baked color/light.
  - `Direction faceDirection`
  - `TextureAtlas.UVMapping` or a `TextureRegion` handle.
  - `int tintIndex`
- Methods:
  - `getVertexData()`, `getFace()`, etc.

#### Step 1.2 – Define BakedModel Interface

Define something like:

```java
public interface BakedModel {
	List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random);
	boolean useAmbientOcclusion();
	boolean isGui3d();
	TextureAtlas.UVMapping getParticleTexture();
}
```

- Initially, you can implement `SimpleBakedModel` that just wraps a `List<BakedQuad>`.

#### Step 1.3 – Implement a ModelBaker

Add a `ModelBaker` (or extend `ResourceManager`) that:

- Takes a `BlockModel` (your JSON representation) and produces a `BakedModel`:
  - Walk `elements`, for each face:
    - Compute quad vertex positions (0–1 coordinates).
    - Apply element rotation if any.
    - Compute atlas UVs via `TextureAtlas`.
    - Generate `BakedQuad` for each face.
- Store baked models in a `ModelManager`-like registry:
  - `Map<ModelIdentifier, BakedModel>` keyed by `"namespace:path"` + model variant.

#### Step 1.4 – Wire Blockstate → BakedModel

- When you resolve a blockstate variant (`BlockStateVariant`), instead of using custom geometry classes by default, have it refer to a `ModelIdentifier` (string).
- On load, pre-bake **all models** and store in `ModelManager`.
- Add `getBlockModel(BlockState)` in something like `BlockModelManager` that returns the `BakedModel` for that state.

> You can keep `StairsGeometryBuilder` / `TorchGeometryBuilder` as *special `BakedModel` implementations* or custom loader equivalents instead of separate hard-coded mesh builders.

---

### Phase 2 – Refactor Chunk Rendering to Use BakedModels

**Goal:** Make `BlockFaceCollector` / `MeshBuilder` operate on `BakedModel` quads instead of direct cube geometry.

#### Step 2.1 – Replace Per-Block Geometry with getQuads

- In `BlockFaceCollector`, instead of specialized cube code:
  - For each `BlockState`:
    - Ask `ModelManager` for its `BakedModel`.
    - For each direction:
      - Apply your current culling logic.
      - If visible, call `model.getQuads(state, direction, random)` and add those quads to the face list, with world position offset.

#### Step 2.2 – Teach MeshBuilder to Consume BakedQuads

- Modify `MeshBuilder` so it accepts `BakedQuad`s instead of hand-built faces:
  - For each quad:
    - Transform from model space [0–1] to world space by adding block position.
    - Sample lighting at each vertex (using `VertexLightSampler`).
    - Write vertices + indices to your arrays as you do now.

#### Step 2.3 – Move Custom Shapes into Custom BakedModels

- Refactor `StairsGeometryBuilder` and `TorchGeometryBuilder` to produce `BakedModel` implementations:
  - Either as:
    - Custom `UnbakedModel` that `bake()`s into quads, **or**
    - A class implementing `BakedModel` that procedurally generates quads when `getQuads` is called.
- This keeps the world renderer blind to “stairs vs cube” – it just sees quads.

---

### Phase 3 – Make Item Rendering Use the Same BakedModel System

**Goal:** Make `ItemRenderer` in MattMC behave like Minecraft’s: items are just `BakedModel`s rendered with different transforms.

#### Step 3.1 – Add Item → Model Mapping

- Equivalent of `ItemModels`:
  - Create `ItemModels` / `ItemModelShaper` that maps `Item` → `ModelIdentifier`.
  - Load item model JSON (you already do) and ensure they’re baked into `BakedModel`s in the same `ModelManager`.

#### Step 3.2 – Implement ItemDisplayContext-like Transforms

- Add an enum `ItemDisplayContext` (GUI, GROUND, FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND, …).
- Have `BlockModel` parse the `display` section of JSON into a transform map.
- At render time, `ItemRenderer` will:
  - Get the `BakedModel` for the `ItemStack`.
  - Choose the appropriate `display` transform.
  - Push a matrix, apply rotation/translation/scale, then render quads in full 3D instead of isometric.

#### Step 3.3 – Replace Isometric Immediate Mode with 3D Quad Rendering

- Gradually deprecate the current isometric `VertexCapture` + `glBegin` path:
  - Replace it with a simple “render model in a small 3D box”:
    - Bind atlas.
    - Apply orthographic or perspective projection suitable for GUI icons.
    - Use your existing VBO pipeline or a small immediate-mode path based on `BakedQuad`s rather than captured vertices.
- You can keep the **isometric look** if you like by choosing an appropriate camera / transform – you no longer need special geometry capture.

---

### Phase 4 – Introduce Render Layers / RenderTypes

**Goal:** Align your render architecture with Minecraft’s `RenderType` / `ChunkSectionLayer` separation for solid/cutout/translucent.

#### Step 4.1 – Add a Simple RenderLayer Enum

- Define something like:

```java
public enum RenderLayer {
	SOLID,
	CUTOUT,
	TRANSLUCENT
}
```

- Associate each block / model with a render layer (e.g., via metadata or JSON extension).

#### Step 4.2 – Separate ChunkMeshBuffers per Layer

- Instead of one `ChunkMeshBuffer` per chunk:
  - Maintain one per `RenderLayer`.
- During compile:
  - Check block/model’s layer.
  - Push quads into the right `MeshBuilder` / buffer.

#### Step 4.3 – Render Layers in Correct Order

- In `LevelRenderer`:
  - Draw SOLID chunks first (depth write on).
  - Then CUTOUT.
  - Then TRANSLUCENT with blending & depth read only.
- This mirrors Minecraft’s `ChunkSectionLayer` + `RenderType` pipeline.  

---

### Phase 5 – Tighten Lighting to Be Minecraft-Like (Optional)

You’re already more advanced than vanilla, so this is optional unless you want strict parity.

#### Step 5.1 – Emulate Vanilla Light Falloff

- Ensure sky and block light values propagate with MC’s rules (distance-based, with consideration for opacity).
- Optionally add a “vanilla mode” flag that:
  - Treats block light as grayscale (max of your RGB).
  - Applies AO similar to MC (per-vertex corner sampling with hard-coded attenuation factors).

#### Step 5.2 – Keep RGB Lighting as a Superset

- Keep your RGB system under the hood.
- For “vanilla compatibility mode,” you can:
  - Derive a scalar light from your RGB and feed that into a more MC-like shader path.

---

### Phase 6 – Clean Abstractions & Test Strategy

#### Step 6.1 – Separate Unbaked vs Baked Concerns

- `BlockModel` and blockstate parsing live in a “resource” / asset layer.
- `BakedModel` and `ModelManager` live in a rendering/model layer.
- Chunk and item renderers don’t know JSON at all; they only see `BakedModel`s.

#### Step 6.2 – Create Regression Tests / Visual Tests

For each significant change:

- Select a few “reference blocks”:
  - Full cube (stone), partial cube (slab, stairs), alpha cutout (leaves), translucent (glass), torch, grass (tint).
- For each:
  - Compare **before/after** screenshots in:
    - World (various angles).
    - Inventory / hotbar / item frame.

This helps you ensure each refactor doesn’t subtly break something.

---

## 11. Summary

- **Where you’re already close to Minecraft:**
  - JSON model & blockstate design.
  - Parent resolution and texture variable handling.
  - Texture atlasing and UV remapping.
  - Chunk mesh pipeline, face culling, and smooth per-vertex lighting.

- **Where you differ most:**
  - No explicit `BakedModel` / `BakedQuad` abstraction as central currency.
  - Items use a **custom isometric capture path** instead of the same model system.
  - Render layers (solid/cutout/translucent) are not as formalized as MC’s `RenderType`/`ChunkSectionLayer`.

- **Biggest wins for “feel like vanilla”:**
  1. Introduce a `BakedModel` layer and bake everything into quads.
  2. Make both block and item rendering consume those `BakedModel`s.
  3. Add simple render layers and draw order.
  4. Optionally, add a “vanilla lighting mode” on top of your RGB lighting core.
