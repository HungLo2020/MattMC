# worldrender

World renderer: turns the world state Java sends (terrain sections, entities,
particles, block outlines and cracks, world text, sky, weather, Distant Horizons
LODs) into draws on the shared renderer and the VulkanicGAL.

Will receive: `vulkanic/world_primitive_frontend*` (including `lod`, `particle`,
`outline`, `world_text`, `gpu_profile_scopes`), `vulkanic/terrain/` and `render/chunk/`.

Rules: no backend API names (`ash`, `glow`, Vulkan/OpenGL branches); ask the GAL
for capabilities. Content is data (meshes, materials), not per-mob or per-block code.
