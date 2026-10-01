//! The wire records Java and Rust share, by family. Every type here is
//! `#[repr(C)]`. Requests start with an `FfiHeader` whose ABI version must be
//! one the bridge accepts and whose byte size must equal the Rust layout
//! exactly (`memory::validate_header`); `layout` reports sizes and field
//! offsets so Java can check its own layouts.

mod version;
mod limits;
mod common;
mod context;
mod frame;
mod resources;
mod submission;
mod gui;
mod shader_pack;
mod lod;
mod whole_frame;
mod world_assets;
mod world;

pub use self::version::*;
pub use self::limits::*;
pub use self::common::*;
pub use self::context::*;
pub use self::frame::*;
pub use self::resources::*;
pub use self::submission::*;
pub use self::gui::*;
pub use self::shader_pack::*;
pub use self::lod::*;
pub use self::whole_frame::*;
pub use self::world_assets::*;
pub use self::world::*;

use crate::render::bridge::*;

