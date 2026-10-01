# scene

The renderer-facing description of what to draw: draw strata, material source
kinds, world mesh assets and terrain voxel sources. The bridge decodes Java's
records into these types; the world renderer and the shader pack consume them.
Data and wire vocabulary only (GAL value types like `IndexType` allowed).

The world frontend re-exports these until it moves to `render/worldrender`;
the remaining `WORLD_*` vocabulary moves here with it.
