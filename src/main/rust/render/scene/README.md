# scene

The renderer-facing description of what to draw: draw strata, material source
kinds, world mesh assets and terrain voxel sources. The bridge decodes Java's
records into these types; native CPU producers may share immutable scene data
directly with the renderer. The world renderer and shader pack consume them.
Data and wire vocabulary only (GAL value types like `IndexType` allowed).

Modules: strata, material (modes, ids, depth policy, sources), textures,
mesh (assets, instance flags, cull/winding/topology), lod (including immutable
selected-column references shared by the DH collector and frame owner), background,
overlays (line style, world border), voxel_source, and the semantic viewport
bound.
