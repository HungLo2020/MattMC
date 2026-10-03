//! Bounded source lowering from legacy terrain fragment syntax to explicit
//! shader-pack semantics.
//!
//! This is intentionally a source transform, not an Iris compatibility layer:
//! it owns text copied from the selected pack and emits GLSL 450 with named
//! terrain outputs. Remaining compatibility syntax is reported by dialect
//! preflight and keeps execution unavailable until a complete lowering exists.

mod probes;
mod text;
mod outputs;
mod contracts;
mod pairs;
mod stages;
mod fragment;
mod vertex;
mod fullscreen;
mod fullscreen_coordinates;
mod fullscreen_vertex;
mod celestial;
mod horizon;
mod varyings;
mod opaque_resources;
mod uniforms;

use self::probes::*;
use self::text::*;
pub(crate) use self::text::{rename_glsl_main, main_function_declaration_start,
    main_function_closing_brace as source_main_function_closing_brace};
pub use self::outputs::*;
pub use self::contracts::*;
pub use self::pairs::*;
pub use self::stages::*;
use self::fragment::*;
pub use self::vertex::*;
use self::fullscreen::*;
use self::fullscreen_coordinates::*;
use self::fullscreen_vertex::*;
use self::celestial::*;
use self::horizon::*;
pub use self::varyings::*;
pub use self::opaque_resources::*;
pub use self::uniforms::*;

use std::collections::{BTreeMap, BTreeSet};

use crate::render::vulkanic::error::{GalError, GalResult};

use crate::render::shaderpack::source::dialect::{analyze_glsl_text, GlslDialectReport};
use crate::render::shaderpack::source::preprocess::PreprocessedShaderSource;
use crate::render::shaderpack::contracts::terrain::parse_draw_buffers_slots;
use crate::render::shaderpack::resources::bindings::{TerrainSourceResourceBindings, TerrainSourceResourceRole};

#[cfg(test)]
mod tests;
