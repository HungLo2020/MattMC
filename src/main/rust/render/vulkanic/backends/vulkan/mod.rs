mod device;
mod lowering;
#[cfg(test)]
mod renderdoc;
mod resources;
pub mod shaderc_spirv_compiler;
mod swapchain;
mod trace;

use std::sync::{Arc, Mutex, MutexGuard};

use super::{graphics_backend_lock, vulkan_capabilities, Backend, BackendCreateDesc, BackendToken};
use crate::render::vulkanic::commands::ValidatedSubmissionBatch;
use crate::render::vulkanic::error::GalResult;
use crate::render::vulkanic::handles::{Handle, HandleKind};
use crate::render::vulkanic::resources::BackendCapabilities;
use crate::render::vulkanic::sync::SubmissionId;

use self::device::{ValidationMode, VulkanContext};
use self::lowering::SubmissionLowerer;
use self::resources::VulkanObjects;

pub(in crate::render::vulkanic) struct VulkanBackend {
    context: Arc<VulkanContext>,
    objects: VulkanObjects,
    lowerer: Mutex<SubmissionLowerer>,
    _global_lock: MutexGuard<'static, ()>,
}

#[allow(dead_code)]
impl VulkanBackend {
    pub(super) fn new(label: &str) -> GalResult<Self> {
        let global_lock = graphics_backend_lock().lock().map_err(|_| {
            crate::render::vulkanic::error::GalError::backend(
                "graphics backend global lock poisoned",
            )
        })?;
        let context = VulkanContext::new(label, ValidationMode::from_env())?;
        Ok(Self {
            objects: VulkanObjects::new(context.clone()),
            lowerer: Mutex::new(SubmissionLowerer::new(context.clone())),
            context,
            _global_lock: global_lock,
        })
    }

    #[cfg(test)]
    pub(super) fn completed_host_reads_for_test(&self) -> Vec<lowering::CompletedHostRead> {
        self.lowerer
            .lock()
            .map(|lowerer| lowerer.completed_host_reads_for_test().to_vec())
            .unwrap_or_default()
    }
}

impl Backend for VulkanBackend {
    fn capabilities(&self) -> BackendCapabilities {
        vulkan_capabilities()
    }

    fn create(&mut self, handle: Handle, desc: BackendCreateDesc<'_>) -> GalResult<BackendToken> {
        let _zone = trace::Zone::new("vulkan.backend.resource.create");
        self.objects.create(handle, desc)
    }

    fn destroy(&mut self, handle: Handle, kind: HandleKind, token: BackendToken) -> GalResult<()> {
        let _zone = trace::Zone::new("vulkan.backend.resource.destroy");
        self.objects.destroy(handle, kind, token)
    }

    fn encode_passes(&mut self, batch: &ValidatedSubmissionBatch) -> GalResult<()> {
        let _zone = trace::Zone::new("vulkan.backend.lowering.encode");
        self.lowerer
            .lock()
            .map_err(|_| {
                crate::render::vulkanic::error::GalError::backend("Vulkan lowerer lock poisoned")
            })?
            .encode(&self.objects, batch)
    }

    fn submit(&mut self, id: SubmissionId, _batch: &ValidatedSubmissionBatch) -> GalResult<()> {
        let _zone = trace::Zone::new("vulkan.backend.submit");
        self.lowerer
            .lock()
            .map_err(|_| {
                crate::render::vulkanic::error::GalError::backend("Vulkan lowerer lock poisoned")
            })?
            .submit(id)
    }

    fn completed_submission(&self) -> SubmissionId {
        self.lowerer
            .lock()
            .map(|mut lowerer| lowerer.completed_submission())
            .unwrap_or(SubmissionId(0))
    }

    fn retire(&mut self, completed: SubmissionId) -> GalResult<()> {
        let _zone = trace::Zone::new("vulkan.backend.retire");
        let mut lowerer = self.lowerer.lock().map_err(|_| {
            crate::render::vulkanic::error::GalError::backend("Vulkan lowerer lock poisoned")
        })?;
        let polled = lowerer.completed_submission();
        lowerer.retire(std::cmp::max(completed, polled))
    }

    #[cfg(test)]
    fn as_any(&self) -> &dyn std::any::Any {
        self
    }

    #[cfg(test)]
    fn as_any_mut(&mut self) -> &mut dyn std::any::Any {
        self
    }
}

impl Drop for VulkanBackend {
    fn drop(&mut self) {
        let _ = self.context.wait_idle();
        self.objects.destroy_all();
    }
}

#[cfg(test)]
pub(super) mod conformance;

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::commands::{CommandListDesc, CommandOp, SubmissionBatch};
    use crate::render::vulkanic::gal::VulkanicGal;
    use crate::render::vulkanic::resources::{
        AccessFlags, BufferDesc, BufferUsage, MemoryDomain, PipelineLayoutDesc, PipelineStageFlags,
        ResourceBinding, ResourceBindingDesc, ResourceBindingKind, ResourceLayoutDesc,
        ResourceSetDesc,
    };

    #[test]
    fn vulkan_backend_can_bootstrap_or_reports_environment_gap() {
        match VulkanBackend::new("MattMC backend bootstrap test") {
            Ok(mut backend) => {
                backend
                    .retire(SubmissionId(0))
                    .expect("retirement should be no-op");
            }
            Err(error) => {
                let message = error.to_string();
                assert!(
                    message.contains("Vulkan")
                        || message.contains("vulkan")
                        || message.contains("physical device"),
                    "unexpected bootstrap failure: {message}"
                );
            }
        }
    }

    #[test]
    fn vulkan_backend_consumes_owned_validated_submission_batch() {
        let backend = match VulkanBackend::new("MattMC validated submission test") {
            Ok(backend) => backend,
            Err(error) => {
                assert!(
                    error.to_string().contains("Vulkan")
                        || error.to_string().contains("physical device"),
                    "unexpected Vulkan bootstrap failure: {error}"
                );
                return;
            }
        };
        let mut gal = VulkanicGal::new_with_backend(Box::new(backend), false);
        let src = gal
            .create_buffer(BufferDesc {
                label: "copy-src".to_string(),
                size: 16,
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::TransferSrc, BufferUsage::HostWrite],
            })
            .expect("source buffer should be created");
        let dst = gal
            .create_buffer(BufferDesc {
                label: "copy-dst".to_string(),
                size: 16,
                memory: MemoryDomain::Readback,
                usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
            })
            .expect("destination buffer should be created");
        let list = gal
            .create_command_list(CommandListDesc {
                label: "copy-list".to_string(),
                operations: vec![CommandOp::CopyBuffer { src, dst, size: 16 }],
            })
            .expect("copy command should validate");
        let token = gal
            .submit(SubmissionBatch {
                label: "copy-submit".to_string(),
                command_lists: vec![list],
            })
            .expect("validated submission should be consumed by Vulkan backend");
        assert_eq!(1, token.submission.0);
        let _ = gal
            .retire_completed()
            .expect("retirement polling should not fail");
    }

    #[test]
    fn vulkan_backend_supports_dynamic_descriptor_offsets_and_optional_sets() {
        let backend = match VulkanBackend::new("MattMC descriptor hardening test") {
            Ok(backend) => backend,
            Err(error) => {
                assert!(
                    error.to_string().contains("Vulkan")
                        || error.to_string().contains("physical device"),
                    "unexpected Vulkan bootstrap failure: {error}"
                );
                return;
            }
        };
        let mut gal = VulkanicGal::new_with_backend(Box::new(backend), false);
        let uniform = gal
            .create_buffer(BufferDesc {
                label: "dynamic-uniform".to_string(),
                size: 256,
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::Uniform],
            })
            .expect("dynamic uniform buffer should be created");
        let layout = gal
            .create_resource_layout(ResourceLayoutDesc {
                label: "dynamic-layout".to_string(),
                bindings: vec![
                    ResourceBindingDesc {
                        binding: 0,
                        kind: ResourceBindingKind::UniformBuffer,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 1,
                    },
                    ResourceBindingDesc {
                        binding: 1,
                        kind: ResourceBindingKind::Sampler,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: true,
                        dynamic_offset_count: 0,
                    },
                ],
            })
            .expect("dynamic resource layout should be created");
        let set = gal
            .create_resource_set(ResourceSetDesc {
                label: "dynamic-set".to_string(),
                layout,
                bindings: vec![ResourceBinding {
                    binding: 0,
                    array_index: 0,
                    resource: uniform,
                    kind: ResourceBindingKind::UniformBuffer,
                    access: AccessFlags::READ,
                    dynamic_offsets: vec![64],
                }],
            })
            .expect("dynamic resource set should allow optional sampler omission");
        let pipeline_layout = gal
            .create_pipeline_layout(PipelineLayoutDesc {
                label: "dynamic-pipeline-layout".to_string(),
                resource_layouts: vec![layout],
            })
            .expect("pipeline layout should accept dynamic descriptor layout");
        assert_ne!(0, set.raw());
        assert_ne!(0, pipeline_layout.raw());
    }

    #[test]
    fn vulkan_backend_stresses_repeated_submissions_and_deferred_destroy() {
        let backend = match VulkanBackend::new("MattMC lifetime stress test") {
            Ok(backend) => backend,
            Err(error) => {
                assert!(
                    error.to_string().contains("Vulkan")
                        || error.to_string().contains("physical device"),
                    "unexpected Vulkan bootstrap failure: {error}"
                );
                return;
            }
        };
        let mut gal = VulkanicGal::new_with_backend(Box::new(backend), false);
        let mut tokens = Vec::new();
        for batch_index in 0..12 {
            let src = gal
                .create_buffer(BufferDesc {
                    label: format!("stress-src-{batch_index}"),
                    size: 256,
                    memory: MemoryDomain::Upload,
                    usages: vec![BufferUsage::TransferSrc, BufferUsage::HostWrite],
                })
                .expect("stress src buffer should be created");
            let dst = gal
                .create_buffer(BufferDesc {
                    label: format!("stress-dst-{batch_index}"),
                    size: 256,
                    memory: MemoryDomain::Readback,
                    usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
                })
                .expect("stress dst buffer should be created");
            let list = gal
                .create_command_list(CommandListDesc {
                    label: format!("stress-list-{batch_index}"),
                    operations: vec![CommandOp::CopyBuffer {
                        src,
                        dst,
                        size: 256,
                    }],
                })
                .expect("stress copy should validate");
            let token = gal
                .submit(SubmissionBatch {
                    label: format!("stress-submit-{batch_index}"),
                    command_lists: vec![list],
                })
                .expect("stress submit should reach Vulkan");
            gal.destroy(src)
                .expect("in-flight source destroy should defer");
            gal.destroy(dst)
                .expect("in-flight destination destroy should defer");
            tokens.push(token.submission);
        }
        let last = *tokens.last().expect("stress submitted at least one batch");
        let retired = gal
            .retire_through_for_test(last)
            .expect("stress resources should retire cleanly");
        assert_eq!(24, retired.len());
    }
}
