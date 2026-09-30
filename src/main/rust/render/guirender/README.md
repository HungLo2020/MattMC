# guirender

GUI renderer: sprites, text, item icons (flat, 3D and foil), GUI meshes, the
panorama and GUI post effects, drawn on the shared renderer and the VulkanicGAL.

Will receive: `vulkanic/gui_frontend.rs`, `gui_mesh_frontend.rs`, `gui_item_*`,
`gui_tiling.rs`, `gui_atlas_reference.rs`, `view_layering.rs` and the foil modules.

Rules: no backend API names; ask the GAL for capabilities.
