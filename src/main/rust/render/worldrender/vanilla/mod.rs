//! The built-in (non-shader-pack) world route: frame recording and its resources.

mod recording;

pub(in crate::render::worldrender) mod shaders;

mod resources;

pub(crate) use self::resources::*;
use self::shaders::*;

use super::*;

