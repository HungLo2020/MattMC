MINECRAFT.md – Minecraft Java Rendering, Geometry, and UV System

Scope: This document describes, in detail, how vanilla Java Edition renders blocks and items using its JSON model system, how geometry and UVs are derived, how face ordering/winding works, which core classes are involved, and how inventory / item rendering relates to in-world block rendering.
Names of classes and packages differ slightly between official, Yarn, and Forge/NeoForge mappings; I’ll call those out where helpful.

1. High-Level Overview

At a very high level, the client’s rendering flow looks like this:

Load assets (JSON models, blockstates, textures) from resource packs.

Parse JSON into “unbaked” models:

Blockstate JSON → maps [BlockState] → [UnbakedModel(s) + transforms].

Model JSON → describes cuboids (elements), textures, and transforms.
Minecraft Wiki
+1

Bake models:

ModelBakery / ModelBaker resolves parents, texture references, rotations, and converts UnbakedModel → BakedModel (final lists of quads with UVs and textures).
Forge Documentation
+1

Chunk compilation:

Chunk renderer (SectionCompiler/SectionRenderDispatcher in modern versions) walks blocks in a chunk section, asks BlockRenderDispatcher + BlockModelRenderer to emit only visible faces, and packs them into vertex buffers grouped by render layer.
NeoForged Documentation

Per-frame rendering:

For each frame, the engine binds the relevant buffers, sets the projection/view matrices, then draws chunk VBOs.

Items (in hand, in the world, in inventory) are rendered by ItemRenderer based on BakedModels and ItemDisplayContext (formerly TransformType).
Maven FabricMC
+1

The same baked model system is used for:

World blocks,

Block entities that rely on block models (vs custom renderers),

Items in GUIs / in hand / in frames.

Blocks and items generally share geometry via JSON model parents.

2. JSON Files and Their Roles

There are three key JSON types in this system:

Blockstate JSON (assets/<namespace>/blockstates/<block>.json)

Block model JSON (assets/<namespace>/models/block/*.json)

Item model JSON (assets/<namespace>/models/item/*.json)

And a couple of closely related resource files:

Texture files (PNG) in textures/.

Block/item tags & registries, which tell the game which blocks/items exist, but not their geometry.

2.1 Blockstate JSON

Blockstate JSON files are purely about mapping block states to models, including rotations and weights.
Minecraft Wiki

Core ideas:

Each block’s BlockState is encoded as a string key like
facing=north,lit=true.

Two patterns:

variants: maps exact state strings to one or more models.

multipart: a list of conditional model entries, each with when and apply clauses.

Typical responsibilities:

Select the right model for different states (e.g., direction for stairs, age for crops).

Apply simple transforms per variant:

x/y rotation (in 90° steps).

uvlock (locks UVs to the world so rotating the model doesn't rotate the texture pattern).

weight (for random variant selection).

The blockstate JSON does not contain geometry; it just says “For this state, use these models with these transforms.”

2.2 Model JSON (Block & Item)

Model JSON files define geometry and UVs and are used for both blocks and items. Conceptually:
NeoForged Documentation
+1

A model is a collection of cuboids called elements.

Each element has:

from: [x, y, z] from 0–16 in “model space”.

to: [x, y, z] from 0–16.

Optional rotation: axis, origin, angle.

faces: a mapping of directions (north, south, east, west, up, down) → per-face data:

uv: [u1, v1, u2, v2] in 0–16 texture-space units.

texture: a texture reference like #all, which then resolves to textures section entries.

Optional cullface: direction to use for face culling.

Optional tintindex: used with color multipliers (e.g. grass, foliage).

There's a top-level textures map that binds names like "all" or "side" to actual sprite IDs like "minecraft:block/stone".

These JSON models can have a parent:

Common parents like block/cube_all, block/cube_column, item/generated, item/handheld.

Parent models provide default elements, texture slots, and default item transforms.

2.3 Item Model JSON and Display Transforms

Item models are fundamentally the same format as block models, but:

They usually have a parent item/generated or item/handheld for flat items, or a block model parent for block items.

They include a display section defining transforms for different contexts (GUI, first person, third person, ground, frame, head, etc.), which map to ItemDisplayContext enum values.
NeoForged Documentation

display entries typically include:

rotation: [x, y, z] degrees

translation: [x, y, z] (in “pixels”, scaled by 1/16 of a block).

scale: [x, y, z]

Minecraft’s render engine knows multiple perspectives; for example: GUI, first-person left/right, third-person left/right, ground, fixed (item frames). These are used by the ItemRenderer when drawing items in different places.
NeoForged Documentation
+1

3. Model Baking: From JSON to BakedModel

The baking process converts high-level JSON description into GPU-ready quads.

Key classes (mapping names may differ slightly between Yarn / official / Forge/NeoForge):

ModelBakery / ModelBaker (1.20+: net.minecraft.client.render.model.ModelBaker)
Maven FabricMC
+1

UnbakedModel and its main implementation BlockModel.

BakedModel – the final form returned by UnbakedModel#bake(...).
Forge Documentation

ModelManager – runtime registry / cache of baked models, accessible via client singleton.
NeoForged Documentation
+1

3.1 UnbakedModel (BlockModel)

BlockModel is basically:

A collection of:

parent reference string.

elements (cuboids, faces).

textures map.

optionally display transforms.

Implements UnbakedModel:

Can resolve its parent and texture dependencies.

Has a bake method that turns the logical description into a BakedModel.

Each BlockModel:

Resolves its parent (recursively) to inherit elements and textures.

Merges textures and elements from parents and itself.

Applies its model-space transform (ModelState / Transformation).

3.2 BakedModel

BakedModel is the baked result. Forge docs describe it as geometry that is nearly ready to go to the GPU.
Forge Documentation

Characteristics:

Stores geometry as quads (BakedQuad) grouped by face:

Typically List<BakedQuad> quadsByDirection[6] + a list for “general” (non-directional) quads.

Each BakedQuad contains:

Packed vertex data (positions, UVs, lightmap, color, normal, etc.).

A reference to the TextureAtlasSprite (texture region).

Face direction (EnumFacing/Direction).

Tint index (for color multiplier).

Implements methods like:

getQuads(state, side, random) – asked by the renderer to provide quads for a given state and face.

useAmbientOcclusion()

isGui3d() (affects how items are rendered in inventory).

getParticleIcon() (for block breaking particles).

The BakedModel is agnostic about where it will be used (world, GUI, held item); usage context is encoded via transforms and render code.

3.3 ModelManager

ModelManager holds all baked models and is reachable via the client singleton, e.g.:

Minecraft.getInstance().getModelManager() or similar, depending on mappings.
NeoForged Documentation
+1

It exposes:

Item models (e.g. getItemModel).

Block models via a BlockModelShaper / BlockStateModel system (e.g. getBlockModelShaper().getBlockModel(state) in modern code).
NeoForged Documentation
+1

4. Geometry Generation and UV Mapping

Now, into the guts: how do we get from elements & UVs to actual quads?

4.1 Coordinate Systems

There are three main spaces of interest here:

Model space (JSON):

All positions in elements.from / elements.to are measured in [0, 16] units.

(0,0,0) typically corresponds to one corner of the block; (16,16,16) the opposite corner.

Block space (in-world):

Model space is scaled to world units, usually such that 16 model units = 1 world unit (block).

When rendering, the chunk renderer adds the block’s integer position to model positions.

Clip space (GPU):

After model, view, and projection matrices, vertices go into normalized device coordinates.

This is the usual OpenGL pipeline; Minecraft uses a custom vertex format but the math is standard.

4.2 From Elements to Faces to Quads

For each element:

It defines an axis-aligned cuboid [from, to] in model space.

For each direction (north, south, east, west, up, down), if a face is present:

The model system emits one quad (four vertices) in the plane of that face.

The position of each vertex is computed based on from and to.

If the element has rotation, the vertex positions are rotated around the specified origin before being stored.

The JSON does not directly specify vertices — it gives enough information for the code to generate them.

4.3 UV Mapping

UVs in JSON:

Each face can specify uv: [u1, v1, u2, v2] in 0–16 units.

If omitted, MC defaults to mapping the face edges directly to [from/to] coordinates (auto UV).

u1, v1 → one corner of the face; u2, v2 → opposite corner.

Conversion:

The model loader computes u/v coordinates 0–1 relative to the sprite:

It takes the sprite’s pixel width / height and maps 0–16 range onto the full sprite (or part of it).

When the model is baked, UVs are stored with per-vertex precision (floats).

When faces are rotated (e.g. by 90° increments or face rotation in the JSON), the loader:

Reorders the mapping of (u,v) corners to vertex positions, essentially rotating the UV rectangle to keep the texture aligned visually.

4.4 Face Winding and Normals

Face winding determines which side of a quad is “front” for backface culling and lighting.

Vanilla’s quads:

For each face direction (e.g. north), the code always emits vertices in a consistent order.

OpenGL’s default front face is counter-clockwise when looking at the face; MC’s vertex order is chosen to match that (or adjusted via GL state).

Because the direction is known, the engine can also derive a face normal (Direction → vector).

This consistency is critical for:

Backface culling: The renderer can discard back faces cheaply.

Lighting: AO and directional lighting use face direction and vertex positions to compute shading.

Although the JSON itself doesn’t state winding, the generator’s templates for each direction effectively fix the ordering.

5. World Block Rendering Pipeline

Once models are baked, the world is drawn mostly by the chunk renderer.

Key classes (names vary slightly):

BlockRenderDispatcher (Forge/NeoForge: net.minecraft.client.renderer.BlockRenderDispatcher).

BlockModelRenderer (in net.minecraft.client.renderer.block or ...render.block).
nekoyue.github.io
+1

SectionCompiler & SectionRenderDispatcher in modern versions.
NeoForged Documentation

5.1 Chunk Compilation

When a chunk section needs to be updated (block changes, lighting changes, or initial load):

The SectionRenderDispatcher schedules a compile task for that section.

SectionCompiler (1.21+) or equivalent older code:

Iterates over all positions (x,y,z) in the section.

For each block:

Gets its BlockState.

Checks which render layer(s) the block uses (solid, cutout, translucent, etc.).

Calls BlockRenderDispatcher.renderBatched (or similar) for appropriate layers.

BlockRenderDispatcher:

Retrieves the BakedModel for this BlockState via BlockModelShaper/ModelManager.

Delegates to BlockModelRenderer to actually emit geometry.

BlockModelRenderer:

Calls BakedModel#getQuads(state, side, random) for:

Each face direction (when doing face-based rendering),

Or null side to fetch “general” quads (e.g., for complex models).

For each BakedQuad, performs:

Face culling (if neighbor block fully occludes this face).

Lighting (per-vertex brightness from block + neighbor light).

Color tinting (e.g. foliage).

Writes vertices into the chunk’s layer-specific BufferBuilder.

Once all blocks are processed:

SectionCompiler finalizes buffers → GPU vertex buffers (VBOs).

SectionRenderDispatcher stores them for use during frame rendering.
NeoForged Documentation

5.2 Face Culling Logic

For each face of a block:

The renderer checks the neighboring block in that face’s direction.

If the neighbor’s state fully covers/occludes that face (e.g. they’re both opaque full cubes), the face is omitted.

If not, the face is emitted to the appropriate render layer.

Things that affect this:

BlockState shape (full cube vs partial; uses voxel shapes).

Render layer (solid vs translucent; translucent faces generally aren’t culled by equal translucency).

Special blocks (fluids, panes, fences) use custom shape rules.

5.3 Lighting and AO (Briefly)

When a quad is emitted, the renderer:

Samples block light and sky light values around the block.

For ambient occlusion, it inspects neighboring blocks around each vertex (up to 4 neighbors per vertex), which yields a brightness factor.

Combines these into per-vertex brightness and optionally a per-face color multiplier (e.g., tinted blocks).

This is where the BakedQuad’s vertex data structure includes per-vertex lightmap coordinates and color.

6. Item Rendering and Its Relationship to Block Rendering

Now, the part you care a lot about: items (including block items) in inventory, GUI, hand, and world, and how all that relates to the block model system.

Key classes (based on Yarn/NeoForge docs):
Maven FabricMC
+1

ItemRenderer – central item rendering class (net.minecraft.client.render.item.ItemRenderer in Yarn).

ItemModels / ItemModelShaper – maps Item → model ID and uses ModelManager to get the baked item model.

BuiltinModelItemRenderer – handles special “builtin” item models (e.g. compasses, shields, tridents) that have custom logic.

HeldItemRenderer – coordinates rendering of items in the player’s hands in first person.

6.1 ItemRenderer’s Role

ItemRenderer is responsible for:

Looking up the correct BakedModel for an ItemStack.

Applying model overrides (bow pulling, compass orientation, trident in use, custom modded overrides).

Rendering that model with the appropriate display transform, depending on context (GUI, first person, ground, frame, etc.).

From Yarn docs, ItemRenderer maintains references to:
Maven FabricMC

MinecraftClient / Minecraft.

ItemModels (where item→model relationships are stored).

TextureManager.

ItemColors (for tinted items).

BuiltinModelItemRenderer (for special-case code).

6.2 Mapping ItemStacks to Models

The process:

An ItemStack (item + NBT) is passed to ItemRenderer.

ItemRenderer uses ItemModels (sometimes called ItemModelShaper) to get a base model ID for that item; the association is usually established when items are registered and models loaded.
NeoForged Documentation
+1

ModelManager returns a BakedModel for that model ID.

Model overrides are applied:

For example, the bow model JSON can define “pulling” overrides with conditions on pull property.

BakedModel’s getOverrides() system fetches an alternative model for given ItemStack properties.

After resolving overrides, the final BakedModel is used for rendering.

6.3 Display Transforms and ItemDisplayContext

Before drawing, ItemRenderer applies a transform based on ItemDisplayContext (previously TransformType).
NeoForged Documentation

Contexts include (not exhaustive):

GUI – inventory slots, creative menu, hotbar icons (2D-ish).

GROUND – item entities lying on the ground.

FIXED – item frames, some GUI contexts.

FIRST_PERSON_LEFT_HAND / FIRST_PERSON_RIGHT_HAND.

THIRD_PERSON_LEFT_HAND / THIRD_PERSON_RIGHT_HAND.

HEAD – worn on the head (e.g., carved pumpkins).

Each BakedModel holds or has access to its ModelTransformation data (from the JSON display section):

The context is passed to that transformation, which yields a matrix:

Rotation (around X/Y/Z),

Translation,

Scale.

Rendering pipeline for an item:

Setup a local transformation matrix for the item.

Apply the display transform for the given ItemDisplayContext.

Bind the block texture atlas (items + blocks share the atlas).

For each quad (BakedQuad) from BakedModel#getQuads(...):

Emit vertices into a buffer with positions adjusted by the transform.

Use same vertex format as blocks (position, color, UV, light, etc.).

This is the key relation:
Items use the same baked quad data as blocks, but with different transforms, orientation, and lighting depending on context.

6.4 BlockItems and Shared Geometry

Consider a standard block item:

The item model JSON often has "parent": "minecraft:block/<block_name>".

This means the item inherits the block’s geometry (same elements, same textures).

The only difference is the display section, which might be defined in:

The block model, or

A layered parent (e.g. an item model that itself has a block parent).

So:

In the world, the block is rendered using BlockModelRenderer and the same BakedModel.

In the inventory, the ItemRenderer uses the same BakedModel, but applies GUI transforms and uses isGui3d() to decide if the item appears flat or 3D.

This is why, if a block’s model JSON is missing or invalid, you can get the well-known purple-black “missing texture” both in the world and as an item.

6.5 Flat 2D Item Models vs 3D Models

Many items are not 3D cuboids but flat sprites:

These use parents like item/generated or item/handheld.

The parent defines a single flat quad aligned in the plane, with the item’s sprite as texture.

The baking process:

Treats them just like any other model but with a single element or built-in geometry.

isGui3d() returns false for them, making inventory rendering treat them as 2D sprites with no perspective.

In contrast, block items often set isGui3d() to true (3D) so that they’re rendered as miniature 3D blocks with perspective in the GUI.

7. Relationship Between World Rendering and Item Rendering (Side by Side)

Let’s line up the two pipelines:

Aspect	World Block	Item (Inventory / Hand / Ground)
Geometry source	BakedModel from ModelManager via BlockState	BakedModel from ModelManager via ItemModels/ItemStack
Lookup key	BlockState → blockstate JSON → model ID	Item → item model JSON (+ overrides via ItemStack properties)
Quads	getQuads(state, side, random)	getQuads(null, side?, random) (often side is null for all quads)
Transform	World transform: block position, orientation	display transform: GUI, first person, third person, etc.
Lighting	Uses world lightmap and AO	GUI uses fixed lighting; world-held items use dynamic lighting
Render code	BlockRenderDispatcher → BlockModelRenderer → chunk VBOs	ItemRenderer (and HeldItemRenderer for hands)
Atlas	Block texture atlas	Same atlas (items & blocks share it)