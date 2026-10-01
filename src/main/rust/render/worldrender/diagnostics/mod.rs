//! Environment-gated diagnostics: selected-source and gameplay attachment
//! capture, static-terrain traces, decal/equipment capture, terrain vertex
//! observation, and the GPU profiling scopes the world installs on the GAL.

pub(crate) mod capture;
pub(crate) mod decal_capture;
pub(crate) mod equipment_capture;
pub(crate) mod gpu_profile_scopes;
pub(crate) mod profile;
pub(crate) mod traces;
pub(crate) mod vertex_observation;
