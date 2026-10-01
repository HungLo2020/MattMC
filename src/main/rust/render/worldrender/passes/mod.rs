//! Render-pass plumbing: depth and frame targets, G-buffer resources
//! (`targets`), pipeline keys and creation (`pipelines`), and oriented world
//! attachments.

pub(crate) mod oriented_target;
pub(crate) mod pipelines;
pub(crate) mod targets;
