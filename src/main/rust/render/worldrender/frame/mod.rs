//! The world frame the bridge hands the renderer: request records
//! (`requests`), limits, and validation of the frame header, background,
//! material/crack/border quads, meshes and LOD records before any GPU work.

pub(crate) mod background;
pub(crate) mod border_quads;
pub(crate) mod crack_quads;
pub(crate) mod entity_culling;
pub(crate) mod header;
pub(crate) mod limits;
pub(crate) mod material_quads;
pub(crate) mod requests;
pub(crate) mod shadow_casters;
pub(crate) mod validation;
