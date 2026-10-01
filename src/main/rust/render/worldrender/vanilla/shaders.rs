//! GLSL of the built-in world passes (files under `glsl/`).

pub(in crate::render::worldrender) const WORLD_LINE_VERTEX_SHADER: &[u8] = include_bytes!("glsl/line_vertex.glsl");

pub(in crate::render::worldrender) const WORLD_LINE_FRAGMENT_SHADER: &[u8] = include_bytes!("glsl/line_fragment.glsl");

// Matches Frozen's `SkyRenderer.buildSkyDisc(16.0F)`: one centre vertex and
// nine perimeter vertices from -180 through 180 degrees in 45 degree steps.
// Keeping the geometry in the shader makes it immutable backend-private data,
// while Java supplies only copied sky/camera semantics.
pub(in crate::render::worldrender) const WORLD_SKY_DISC_VERTEX_SHADER: &[u8] = include_bytes!("glsl/sky_disc_vertex.glsl");

pub(in crate::render::worldrender) const WORLD_SKY_DISC_FRAGMENT_SHADER: &[u8] = include_bytes!("glsl/sky_disc_fragment.glsl");

// The direct vanilla route writes straight to the acquired presentation
// target, rather than the source-execution G-buffer.  It uses the identical
// semantic sky/fog calculation above, with the single attachment required by
// that explicit direct pass.
pub(in crate::render::worldrender) const WORLD_SKY_DISC_FORWARD_FRAGMENT_SHADER: &[u8] = include_bytes!("glsl/sky_disc_forward_fragment.glsl");

pub(in crate::render::worldrender) const WORLD_CRACK_VERTEX_SHADER: &[u8] = include_bytes!("glsl/crack_vertex.glsl");

pub(in crate::render::worldrender) const WORLD_CRACK_FRAGMENT_SHADER: &[u8] = include_bytes!("glsl/crack_fragment.glsl");

pub(in crate::render::worldrender) const WORLD_BORDER_VERTEX_SHADER: &[u8] = include_bytes!("glsl/border_vertex.glsl");

pub(in crate::render::worldrender) const WORLD_BORDER_FRAGMENT_SHADER: &[u8] = include_bytes!("glsl/border_fragment.glsl");

pub(in crate::render::worldrender) const WORLD_MATERIAL_VERTEX_SHADER: &[u8] = include_bytes!("glsl/material_vertex.glsl");

// A DH generic box keeps one 64-byte Rust-owned record while this vertex
// program derives the same six ordered, back-face-culled material faces as the
// old 192-byte-per-face quad stream. The fragment/lightmap contract is shared.
pub(in crate::render::worldrender) const WORLD_DH_GENERIC_BOX_VERTEX_SHADER: &[u8] = include_bytes!("glsl/dh_generic_box_vertex.glsl");

pub(in crate::render::worldrender) const WORLD_MATERIAL_FRAGMENT_SHADER: &[u8] = include_bytes!("glsl/material_fragment.glsl");

// Frozen's weather and particle streams use the particle vertex contract: their
// copied UV2 is converted to a 16x16 integer lightmap coordinate and fetched
// without filtering.  This is a distinct source-family pipeline, so ordinary
// material producers never acquire a lightmap dependency just because they
// share the compact quad stream.
pub(in crate::render::worldrender) const WORLD_PARTICLE_MATERIAL_FRAGMENT_SHADER: &[u8] = include_bytes!("glsl/particle_material_fragment.glsl");

// Frozen core/position_tex discards a zero-alpha texture sample before
// modulation. A generic zero-threshold material would instead replace target
// alpha with zero under Overlay blending, violating the celestial contract.
pub(in crate::render::worldrender) const WORLD_CELESTIAL_MATERIAL_FRAGMENT_SHADER: &[u8] = include_bytes!("glsl/celestial_material_fragment.glsl");
