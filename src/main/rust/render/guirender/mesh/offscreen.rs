//! Generation-scoped offscreen targets for mesh item rasters.

use super::*;

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct OffscreenTargetKey {
    pub(super) generation: u64,
    pub(super) item_identity: u64,
    pub(super) width: u32,
    pub(super) height: u32,
}

/// Rust-owned color/depth target for GUI mesh rasterization. This is separate
/// from the final frame target so GUI-item depth cannot interact with terrain
/// depth; later GUI composition consumes only `color_view`.
#[derive(Clone, Copy, Debug)]
pub struct GuiMeshOffscreenTarget {
    pub color: Handle,
    pub color_view: Handle,
    pub depth: Handle,
    pub depth_view: Handle,
    pub target: Handle,
    pub pass: Handle,
    pub extent: Extent3d,
    pub(crate) initialized: bool,
}

#[derive(Default)]
pub struct GuiMeshOffscreenTargetCache {
    pub(super) targets: BTreeMap<OffscreenTargetKey, GuiMeshOffscreenTarget>,
    usage: BTreeMap<Handle, crate::render::vulkanic::commands::SubmissionUsage>,
}

pub(super) const GUI_MESH_MAX_NAMED_ITEM_TARGETS_PER_GENERATION: usize = 63;

pub(super) const GUI_MESH_MAX_OFFSCREEN_TARGETS_PER_GENERATION: usize = 256;

impl GuiMeshOffscreenTargetCache {
    pub(crate) fn len(&self) -> usize {
        self.targets.len()
    }

    /// Returns an owned target for this GUI resource generation and logical
    /// raster extent. A new generation atomically retires old target objects
    /// before staging its replacements; no Java target or view is involved.
    pub fn stage(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        extent: Extent3d,
    ) -> GalResult<GuiMeshOffscreenTarget> {
        self.stage_item(gal, generation, extent, 0)
    }

    pub(crate) fn stage_item(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        extent: Extent3d,
        item_identity: u64,
    ) -> GalResult<GuiMeshOffscreenTarget> {
        if generation == 0 || extent.width == 0 || extent.height == 0 || extent.depth != 1 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh offscreen target requires a non-zero generation and D2 extent",
            ));
        }
        if extent.width > GUI_MESH_MAX_OFFSCREEN_AXIS || extent.height > GUI_MESH_MAX_OFFSCREEN_AXIS
        {
            return Err(GalError::unsupported_feature(format!(
                "GUI mesh offscreen extent {}x{} exceeds bounded axis {}",
                extent.width, extent.height, GUI_MESH_MAX_OFFSCREEN_AXIS
            )));
        }
        let key = OffscreenTargetKey {
            generation,
            item_identity,
            width: extent.width,
            height: extent.height,
        };
        if let Some(target) = self.targets.get(&key).copied() {
            return Ok(target);
        }
        self.destroy_other_generations(gal, generation);
        if item_identity != 0
            && self
                .targets
                .keys()
                .filter(|key| key.item_identity != 0)
                .count()
                >= GUI_MESH_MAX_NAMED_ITEM_TARGETS_PER_GENERATION
        {
            return Err(GalError::unsupported_feature(format!(
                "GUI mesh named item target cache exceeds bounded limit {GUI_MESH_MAX_NAMED_ITEM_TARGETS_PER_GENERATION}"
            )));
        }
        if self.targets.len() >= GUI_MESH_MAX_OFFSCREEN_TARGETS_PER_GENERATION {
            return Err(GalError::unsupported_feature(format!(
                "GUI mesh offscreen target cache exceeds bounded limit {GUI_MESH_MAX_OFFSCREEN_TARGETS_PER_GENERATION}"
            )));
        }
        let label = format!(
            "minecraft.gui.mesh.gen{generation}.{}x{}",
            extent.width, extent.height
        );
        let mut created = Vec::new();
        let result = (|| -> GalResult<GuiMeshOffscreenTarget> {
            let color = gal.create_texture(TextureDesc {
                label: format!("{label}.color"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Rgba8Unorm,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::ColorAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                ],
            })?;
            created.push(color);
            let color_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.color-view"),
                texture: color,
                format: TextureFormat::Rgba8Unorm,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(color_view);
            let depth = gal.create_texture(TextureDesc {
                label: format!("{label}.depth"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Depth32Float,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::DepthStencilAttachment],
            })?;
            created.push(depth);
            let depth_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.depth-view"),
                texture: depth,
                format: TextureFormat::Depth32Float,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(depth_view);
            let target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.target"),
                color_views: vec![color_view],
                depth_stencil_view: Some(depth_view),
                extent,
            })?;
            created.push(target);
            let pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.pass"),
                target,
                color_formats: vec![TextureFormat::Rgba8Unorm],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(pass);
            Ok(GuiMeshOffscreenTarget {
                color,
                color_view,
                depth,
                depth_view,
                target,
                pass,
                extent,
                initialized: false,
            })
        })();
        match result {
            Ok(target) => {
                self.targets.insert(key, target);
                Ok(target)
            }
            Err(error) => {
                for handle in created.into_iter().rev() {
                    let _ = gal.retire(handle);
                }
                Err(error)
            }
        }
    }

    /// Returns an existing named target without creating or mutating native
    /// resources.  The GUI frontend uses this only to decide whether a static
    /// item can reuse its already accepted raster before preparing vertices.
    pub(crate) fn peek_item(
        &self,
        generation: u64,
        extent: Extent3d,
        item_identity: u64,
    ) -> Option<GuiMeshOffscreenTarget> {
        if generation == 0 || item_identity == 0 || extent.depth != 1 {
            return None;
        }
        self.targets
            .get(&OffscreenTargetKey {
                generation,
                item_identity,
                width: extent.width,
                height: extent.height,
            })
            .copied()
    }

    /// Retain the target (and its composite bindings) while commands are prepared.
    pub fn track_use(&mut self, target: Handle) -> CommandOp {
        CommandOp::TrackSubmission(self.usage.entry(target).or_default().clone())
    }

    /// Whether this frame no longer needs a cached identity/extent.
    pub fn has_obsolete_items(&self, generation: u64, needed: &[(u64, Extent3d)]) -> bool {
        if self
            .usage
            .values()
            .any(|usage| usage.has_pending_commands())
        {
            return false;
        }
        self.targets.keys().any(|key| {
            key.generation != generation
                || !needed.iter().any(|(id, extent)| {
                    key.item_identity == *id
                        && key.width == extent.width
                        && key.height == extent.height
                })
        })
    }

    /// Call after releasing composite resource sets that sample these targets.
    pub fn retain_items(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        needed: &[(u64, Extent3d)],
    ) {
        let obsolete: Vec<_> = self
            .targets
            .keys()
            .copied()
            .filter(|key| {
                key.generation != generation
                    || !needed.iter().any(|(id, extent)| {
                        key.item_identity == *id
                            && key.width == extent.width
                            && key.height == extent.height
                    })
            })
            .collect();
        for key in obsolete {
            if let Some(target) = self.targets.remove(&key) {
                self.usage.remove(&target.target);
                destroy_target(gal, target);
            }
        }
    }

    pub fn clear(&mut self, gal: &mut VulkanicGal) {
        self.usage.clear();
        let targets = std::mem::take(&mut self.targets);
        for (_, target) in targets {
            destroy_target(gal, target);
        }
    }

    pub(super) fn destroy_other_generations(&mut self, gal: &mut VulkanicGal, generation: u64) {
        let stale = self
            .targets
            .keys()
            .copied()
            .filter(|key| key.generation != generation)
            .collect::<Vec<_>>();
        for key in stale {
            if let Some(target) = self.targets.remove(&key) {
                self.usage.remove(&target.target);
                destroy_target(gal, target);
            }
        }
    }
}

pub(super) fn destroy_target(gal: &mut VulkanicGal, target: GuiMeshOffscreenTarget) {
    for handle in [
        target.pass,
        target.target,
        target.depth_view,
        target.depth,
        target.color_view,
        target.color,
    ] {
        let _ = gal.retire(handle);
    }
}
