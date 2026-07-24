// This module is intentionally private to render::vulkanic. Rust backend
// implementations must stay behind the Vulkanic frontend boundary, matching the
// Java rule that non-Vulkanic code cannot import net.vulkanic.backends.*.
pub(super) mod opengl;
pub(super) mod vulkan;

#[cfg(test)]
mod conformance_matrix;

use std::sync::{Mutex, OnceLock};

use super::commands::ValidatedSubmissionBatch;
use super::error::GalResult;
use super::handles::{Handle, HandleKind};
use super::resources::{
    BackendCapabilities, BackendFeatureFlags, BackendLimits, BufferDesc, ComputePipelineDesc,
    GraphicsPipelineDesc, PipelineLayoutDesc, RenderPassDesc, RenderTargetDesc, ResourceLayoutDesc,
    ResourceSetDesc, SamplerDesc, ShaderModuleDesc, TextureDesc, TextureViewDesc,
};
use super::sync::SubmissionId;

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(super) struct BackendToken(pub u64);

pub(super) fn graphics_backend_lock() -> &'static Mutex<()> {
    static LOCK: OnceLock<Mutex<()>> = OnceLock::new();
    LOCK.get_or_init(|| Mutex::new(()))
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(super) enum BackendCreateDesc<'a> {
    Buffer(&'a BufferDesc),
    Texture(&'a TextureDesc),
    TextureView(&'a TextureViewDesc),
    Sampler(&'a SamplerDesc),
    ShaderModule(&'a ShaderModuleDesc),
    ResourceLayout(&'a ResourceLayoutDesc),
    ResourceSet(&'a ResourceSetDesc),
    PipelineLayout(&'a PipelineLayoutDesc),
    GraphicsPipeline(&'a GraphicsPipelineDesc),
    ComputePipeline(&'a ComputePipelineDesc),
    RenderTarget(&'a RenderTargetDesc),
    RenderPass(&'a RenderPassDesc),
}

pub(super) trait Backend {
    fn capabilities(&self) -> BackendCapabilities;
    fn create(&mut self, handle: Handle, desc: BackendCreateDesc<'_>) -> GalResult<BackendToken>;
    fn destroy(&mut self, handle: Handle, kind: HandleKind, token: BackendToken) -> GalResult<()>;
    fn encode_passes(&mut self, batch: &ValidatedSubmissionBatch) -> GalResult<()>;
    fn submit(&mut self, id: SubmissionId, batch: &ValidatedSubmissionBatch) -> GalResult<()>;
    fn completed_submission(&self) -> SubmissionId;
    fn retire(&mut self, completed: SubmissionId) -> GalResult<()>;

    #[cfg(test)]
    fn as_any(&self) -> &dyn std::any::Any;

    #[cfg(test)]
    fn as_any_mut(&mut self) -> &mut dyn std::any::Any;
}

pub(super) fn vulkan_capabilities() -> BackendCapabilities {
    BackendCapabilities {
        name: "Rust Vulkan",
        features: BackendFeatureFlags {
            graphics: true,
            compute: true,
            descriptor_arrays: true,
            optional_bindings: true,
            dynamic_buffer_offsets: true,
            uniform_buffers: true,
            storage_buffers: true,
            storage_textures: true,
            indirect_draw: true,
            indirect_dispatch: true,
            multiple_color_attachments: true,
            depth_only_pass: true,
            blended_pass: true,
            texture_subresource_copies: true,
            texture_mip_levels: true,
            texture_array_layers: true,
            host_buffer_access: true,
            presentation: false,
            renderdoc_capture: true,
            tracy_zones: true,
        },
        limits: BackendLimits {
            max_buffer_size: 256 * 1024 * 1024,
            max_texture_extent_2d: 8192,
            max_texture_mip_levels: 13,
            max_texture_array_layers: 64,
            max_resource_layout_bindings: 32,
            max_binding_array_count: 16,
            max_color_attachments: 4,
            max_dynamic_offsets_per_binding: 8,
            max_command_lists_per_submission: 64,
            max_commands_per_list: 16_384,
            max_draw_count: 4096,
            max_dispatch_groups_per_axis: 65_535,
        },
    }
}

pub(super) fn opengl_capabilities() -> BackendCapabilities {
    BackendCapabilities {
        name: "Rust OpenGL",
        features: BackendFeatureFlags {
            graphics: true,
            compute: false,
            descriptor_arrays: true,
            optional_bindings: true,
            dynamic_buffer_offsets: true,
            uniform_buffers: true,
            storage_buffers: true,
            storage_textures: false,
            indirect_draw: false,
            indirect_dispatch: false,
            multiple_color_attachments: true,
            depth_only_pass: true,
            blended_pass: true,
            texture_subresource_copies: true,
            texture_mip_levels: false,
            texture_array_layers: false,
            host_buffer_access: true,
            presentation: false,
            renderdoc_capture: true,
            tracy_zones: true,
        },
        limits: BackendLimits {
            max_buffer_size: 64 * 1024 * 1024,
            max_texture_extent_2d: 4096,
            max_texture_mip_levels: 1,
            max_texture_array_layers: 1,
            max_resource_layout_bindings: 16,
            max_binding_array_count: 8,
            max_color_attachments: 4,
            max_dynamic_offsets_per_binding: 4,
            max_command_lists_per_submission: 32,
            max_commands_per_list: 8192,
            max_draw_count: 1,
            max_dispatch_groups_per_axis: 0,
        },
    }
}

#[cfg(test)]
pub(super) mod mock {
    use std::collections::{BTreeMap, VecDeque};

    use super::*;
    use crate::render::vulkanic::error::GalError;

    #[derive(Default)]
    pub(in crate::render::vulkanic) struct MockBackend {
        pub(in crate::render::vulkanic) creates: Vec<(Handle, HandleKind)>,
        pub(in crate::render::vulkanic) destroys: Vec<(Handle, HandleKind)>,
        pub(in crate::render::vulkanic) submissions: Vec<SubmissionId>,
        pub(in crate::render::vulkanic) encoded_batches: usize,
        pub(in crate::render::vulkanic) completed: SubmissionId,
        pub(in crate::render::vulkanic) fail_next_create: bool,
        pub(in crate::render::vulkanic) capabilities: Option<BackendCapabilities>,
        pub(in crate::render::vulkanic) live: BTreeMap<Handle, BackendToken>,
        next_token: u64,
        pub(in crate::render::vulkanic) submitted_labels: VecDeque<String>,
    }

    impl MockBackend {
        pub(in crate::render::vulkanic) fn fail_next_create(&mut self) {
            self.fail_next_create = true;
        }

        pub(in crate::render::vulkanic) fn complete_through(&mut self, id: SubmissionId) {
            self.completed = id;
        }

        pub(in crate::render::vulkanic) fn with_capabilities(
            capabilities: BackendCapabilities,
        ) -> Self {
            Self {
                capabilities: Some(capabilities),
                ..Self::default()
            }
        }
    }

    impl Backend for MockBackend {
        fn capabilities(&self) -> BackendCapabilities {
            self.capabilities.unwrap_or_else(vulkan_capabilities)
        }

        fn create(
            &mut self,
            handle: Handle,
            desc: BackendCreateDesc<'_>,
        ) -> GalResult<BackendToken> {
            if self.fail_next_create {
                self.fail_next_create = false;
                return Err(GalError::backend("mock create failure"));
            }
            let kind = match desc {
                BackendCreateDesc::Buffer(_) => HandleKind::Buffer,
                BackendCreateDesc::Texture(_) => HandleKind::Texture,
                BackendCreateDesc::TextureView(_) => HandleKind::TextureView,
                BackendCreateDesc::Sampler(_) => HandleKind::Sampler,
                BackendCreateDesc::ShaderModule(_) => HandleKind::ShaderModule,
                BackendCreateDesc::ResourceLayout(_) => HandleKind::ResourceLayout,
                BackendCreateDesc::ResourceSet(_) => HandleKind::ResourceSet,
                BackendCreateDesc::PipelineLayout(_) => HandleKind::PipelineLayout,
                BackendCreateDesc::GraphicsPipeline(_) => HandleKind::GraphicsPipeline,
                BackendCreateDesc::ComputePipeline(_) => HandleKind::ComputePipeline,
                BackendCreateDesc::RenderTarget(_) => HandleKind::RenderTarget,
                BackendCreateDesc::RenderPass(_) => HandleKind::RenderPass,
            };
            self.next_token += 1;
            let token = BackendToken(self.next_token);
            self.live.insert(handle, token);
            self.creates.push((handle, kind));
            Ok(token)
        }

        fn destroy(
            &mut self,
            handle: Handle,
            kind: HandleKind,
            token: BackendToken,
        ) -> GalResult<()> {
            if self.live.remove(&handle) != Some(token) {
                return Err(GalError::backend("mock destroy for unknown token"));
            }
            self.destroys.push((handle, kind));
            Ok(())
        }

        fn encode_passes(&mut self, batch: &ValidatedSubmissionBatch) -> GalResult<()> {
            self.encoded_batches += batch.command_lists.len();
            Ok(())
        }

        fn submit(&mut self, id: SubmissionId, batch: &ValidatedSubmissionBatch) -> GalResult<()> {
            self.submissions.push(id);
            self.submitted_labels.push_back(batch.label.clone());
            Ok(())
        }

        fn completed_submission(&self) -> SubmissionId {
            self.completed
        }

        fn retire(&mut self, _completed: SubmissionId) -> GalResult<()> {
            Ok(())
        }

        fn as_any(&self) -> &dyn std::any::Any {
            self
        }

        fn as_any_mut(&mut self) -> &mut dyn std::any::Any {
            self
        }
    }
}
