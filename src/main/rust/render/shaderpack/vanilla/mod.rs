//! Minecraft's own data-driven shaders: post-effect graphs and their executor,
//! Mojang `#moj_import` expansion and namespaces, engine Globals, the dynamic
//! lightmap and the Fabulous transparency targets.

pub(crate) mod engine_globals;
pub(crate) mod fabulous;
pub(crate) mod imports;
pub mod lightmap;
pub(crate) mod namespaces;
pub mod post_effect;

