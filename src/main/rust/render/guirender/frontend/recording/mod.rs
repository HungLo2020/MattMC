//! Recording of ordered GUI requests into command operations.

mod target;
mod blur_boundary;
mod batches;

pub(in crate::render::guirender::frontend) use self::batches::*;

use super::*;
