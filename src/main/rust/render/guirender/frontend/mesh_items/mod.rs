//! GUI mesh items: geometry residency, shared programs and composite batches.

mod geometry;
mod composite;
mod recording;

pub(in crate::render::guirender::frontend) use self::geometry::*;
pub(in crate::render::guirender::frontend) use self::composite::*;

use super::*;
