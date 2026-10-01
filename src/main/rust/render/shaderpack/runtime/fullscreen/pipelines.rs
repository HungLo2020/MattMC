//! Compiled fullscreen pipelines and their bounded cache.

use super::*;

/// Static GAL objects for a prepared fullscreen source pass. Per-frame UBO
/// writes and pass commands are intentionally a later executor concern; this
/// owner proves that source-declared fullscreen stages compile through the
/// same explicit VulkanicGAL pipeline path on Vulkan and OpenGL.
#[derive(Debug)]
pub(crate) struct CompiledFullscreenSourcePass {
    pub target: Handle,
    pub pass: Handle,
    pub source_data_layout: Handle,
    pub pack_resources_layout: Handle,
    pub pipeline_layout: Handle,
    pub vertex_shader: Handle,
    pub fragment_shader: Handle,
    pub pipeline: Handle,
    /// When set, the layouts, shaders, and pipeline above are owned by a
    /// [`FullscreenPipelineCache`] entry and outlive this frame's pass.
    pub(super) shared: Option<std::sync::Arc<FullscreenPipelineObjects>>,
}

/// View-independent GAL objects for one compiled fullscreen source stage.
/// They depend only on the lowered program and its color formats, so one
/// set serves every frame; the render target and pass (which reference the
/// frame's color views) remain per-plan.
#[derive(Debug)]
pub(crate) struct FullscreenPipelineObjects {
    pub(super) source_data_layout: Handle,
    pub(super) pack_resources_layout: Handle,
    pub(super) pipeline_layout: Handle,
    pub(super) vertex_shader: Handle,
    pub(super) fragment_shader: Handle,
    pub(super) pipeline: Handle,
}

impl FullscreenPipelineObjects {
    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        for handle in [
            self.pipeline,
            self.vertex_shader,
            self.fragment_shader,
            self.pipeline_layout,
            self.source_data_layout,
            self.pack_resources_layout,
        ] {
            let _ = gal.destroy(handle);
        }
    }
}

#[derive(Clone, Debug, Eq, Hash, PartialEq)]
pub(super) struct FullscreenPipelineKey {
    pub(super) program_identity: String,
    pub(super) shader_pack_generation: u64,
    pub(super) source_stage_path: String,
    pub(super) color_formats: Vec<TextureFormat>,
    pub(super) celestial_blend: bool,
}

/// One cached pipeline together with the exact compile inputs it was built
/// from; a hit requires identical shader stages and resource layouts.
#[derive(Debug)]
pub(super) struct CachedFullscreenPipeline {
    pub(super) vertex: crate::render::shaderpack::programs::ShaderStageSource,
    pub(super) fragment: crate::render::shaderpack::programs::ShaderStageSource,
    pub(super) source_data_bindings: Vec<crate::render::vulkanic::resources::ResourceBindingDesc>,
    pub(super) pack_resources_bindings: Vec<crate::render::vulkanic::resources::ResourceBindingDesc>,
    pub(super) objects: std::sync::Arc<FullscreenPipelineObjects>,
}

/// Retains compiled fullscreen pipelines across frames. Entries are valid
/// only for the source-candidate epochs that lowered their programs; any
/// epoch change drops them all. An entry still referenced by a live plan is
/// destroyed by that plan's last owner.
#[derive(Debug, Default)]
pub(crate) struct FullscreenPipelineCache {
    pub(super) epochs: std::cell::Cell<Option<(u64, u64)>>,
    pub(super) entries: std::cell::RefCell<
        std::collections::HashMap<FullscreenPipelineKey, Vec<CachedFullscreenPipeline>>,
    >,
}

pub(super) const FULLSCREEN_PIPELINE_CACHE_ENTRIES: usize = 128;

impl FullscreenPipelineCache {
    pub(super) fn release_all(&self, gal: &mut VulkanicGal) {
        let entries = std::mem::take(&mut *self.entries.borrow_mut());
        for cached in entries.into_values().flatten() {
            if let Ok(objects) = std::sync::Arc::try_unwrap(cached.objects) {
                objects.destroy(gal);
            }
        }
    }

    pub(crate) fn destroy(&self, gal: &mut VulkanicGal) {
        self.release_all(gal);
        self.epochs.set(None);
    }
}

impl CompiledFullscreenSourcePass {
    pub(crate) fn destroy(self, gal: &mut VulkanicGal) {
        if let Some(shared) = self.shared {
            let _ = gal.destroy(self.pass);
            let _ = gal.destroy(self.target);
            if let Ok(objects) = std::sync::Arc::try_unwrap(shared) {
                objects.destroy(gal);
            }
            return;
        }
        for handle in [
            self.pipeline,
            self.pass,
            self.target,
            self.vertex_shader,
            self.fragment_shader,
            self.pipeline_layout,
            self.source_data_layout,
            self.pack_resources_layout,
        ] {
            let _ = gal.destroy(handle);
        }
    }
}
