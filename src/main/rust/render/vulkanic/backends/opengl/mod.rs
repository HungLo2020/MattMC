mod context;
mod lowering;
#[cfg(test)]
mod renderdoc;
mod resources;
mod trace;

use std::sync::{Mutex, MutexGuard};

use super::{
    graphics_backend_lock, opengl_capabilities, Backend, BackendCreateDesc, BackendToken,
    CompletedHostRead,
};
use crate::render::vulkanic::commands::ValidatedSubmissionBatch;
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::handles::{Handle, HandleKind};
use crate::render::vulkanic::resources::BackendCapabilities;
use crate::render::vulkanic::sync::SubmissionId;

use self::context::OpenGlContext;
use self::lowering::OpenGlLowerer;
#[cfg(test)]
use self::lowering::StateCacheSnapshot;
use self::resources::OpenGlObjects;

pub(in crate::render::vulkanic) struct OpenGlBackend {
    context: OpenGlContext,
    objects: OpenGlObjects,
    lowerer: Mutex<OpenGlLowerer>,
    _global_lock: MutexGuard<'static, ()>,
}

#[allow(dead_code)]
impl OpenGlBackend {
    pub(in crate::render::vulkanic) fn new(label: &str) -> GalResult<Self> {
        let _zone = trace::Zone::new("opengl.backend.create");
        let global_lock = graphics_backend_lock()
            .lock()
            .map_err(|_| GalError::backend("OpenGL backend global lock poisoned"))?;
        let context = OpenGlContext::new(label)?;
        let objects = OpenGlObjects::new(context.gl().clone());
        Ok(Self {
            lowerer: Mutex::new(OpenGlLowerer::new(context.gl().clone())),
            context,
            objects,
            _global_lock: global_lock,
        })
    }

    pub(super) fn completed_host_reads_snapshot(&self) -> Vec<self::lowering::CompletedHostRead> {
        self.lowerer
            .lock()
            .map(|lowerer| lowerer.completed_host_reads_snapshot().to_vec())
            .unwrap_or_default()
    }

    #[cfg(test)]
    pub(super) fn completed_host_reads_for_test(&self) -> Vec<self::lowering::CompletedHostRead> {
        self.completed_host_reads_snapshot()
    }

    #[cfg(test)]
    pub(super) fn gl_errors_for_test(&self) -> Vec<String> {
        self.lowerer
            .lock()
            .map(|lowerer| lowerer.gl_errors_for_test().to_vec())
            .unwrap_or_default()
    }

    #[cfg(test)]
    pub(super) fn state_cache_for_test(&self) -> StateCacheSnapshot {
        self.lowerer
            .lock()
            .map(|lowerer| lowerer.state_cache_for_test())
            .unwrap_or_default()
    }
}

impl Backend for OpenGlBackend {
    fn capabilities(&self) -> BackendCapabilities {
        opengl_capabilities()
    }

    fn create(&mut self, handle: Handle, desc: BackendCreateDesc<'_>) -> GalResult<BackendToken> {
        let _zone = trace::Zone::new("opengl.backend.resource.create");
        self.context.make_current()?;
        self.objects.create(handle, desc)
    }

    fn destroy(&mut self, handle: Handle, kind: HandleKind, token: BackendToken) -> GalResult<()> {
        let _zone = trace::Zone::new("opengl.backend.resource.destroy");
        self.context.make_current()?;
        self.objects.destroy(handle, kind, token)
    }

    fn encode_passes(&mut self, batch: &ValidatedSubmissionBatch) -> GalResult<()> {
        let _zone = trace::Zone::new("opengl.backend.lowering.encode");
        self.context.make_current()?;
        self.lowerer
            .lock()
            .map_err(|_| GalError::backend("OpenGL lowerer lock poisoned"))?
            .encode(batch)
    }

    fn submit(&mut self, id: SubmissionId, _batch: &ValidatedSubmissionBatch) -> GalResult<()> {
        let _zone = trace::Zone::new("opengl.backend.submit");
        self.context.make_current()?;
        self.lowerer
            .lock()
            .map_err(|_| GalError::backend("OpenGL lowerer lock poisoned"))?
            .submit(id, &mut self.objects)
    }

    fn completed_submission(&self) -> SubmissionId {
        self.lowerer
            .lock()
            .map(|lowerer| lowerer.completed_submission())
            .unwrap_or(SubmissionId(0))
    }

    fn retire(&mut self, completed: SubmissionId) -> GalResult<()> {
        let _zone = trace::Zone::new("opengl.backend.retire");
        self.context.make_current()?;
        self.lowerer
            .lock()
            .map_err(|_| GalError::backend("OpenGL lowerer lock poisoned"))?
            .retire(completed)
    }

    fn completed_host_reads(&self) -> Vec<CompletedHostRead> {
        self.completed_host_reads_snapshot()
            .into_iter()
            .map(|read| CompletedHostRead {
                submission: read.submission,
                buffer: read.buffer,
                offset: read.offset,
                bytes: read.bytes,
            })
            .collect()
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

impl Drop for OpenGlBackend {
    fn drop(&mut self) {
        let _zone = trace::Zone::new("opengl.backend.drop");
        let _ = self.context.make_current();
        self.objects.destroy_all();
    }
}

#[cfg(test)]
pub(super) mod conformance;

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::commands::{
        CommandListDesc, CommandOp, ResourceBarrier, SubmissionBatch, TextureUsageState,
    };
    use crate::render::vulkanic::gal::VulkanicGal;
    use crate::render::vulkanic::resources::{
        AccessFlags, BufferDesc, BufferUsage, MemoryDomain, PipelineStageFlags, QueueClass,
    };

    #[test]
    fn opengl_backend_can_bootstrap_or_reports_environment_gap() {
        match OpenGlBackend::new("MattMC OpenGL bootstrap") {
            Ok(mut backend) => backend.retire(SubmissionId(0)).unwrap(),
            Err(error) => {
                let text = error.to_string();
                assert!(
                    text.contains("OpenGL") || text.contains("EGL") || text.contains("GL"),
                    "unexpected OpenGL bootstrap failure: {text}"
                );
            }
        }
    }

    #[test]
    fn opengl_backend_consumes_owned_copy_submission() {
        let backend = match OpenGlBackend::new("MattMC OpenGL copy") {
            Ok(backend) => backend,
            Err(error) => {
                assert!(
                    error.to_string().contains("OpenGL") || error.to_string().contains("EGL"),
                    "unexpected OpenGL bootstrap failure: {error}"
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
            .unwrap();
        let dst = gal
            .create_buffer(BufferDesc {
                label: "copy-dst".to_string(),
                size: 16,
                memory: MemoryDomain::Readback,
                usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
            })
            .unwrap();
        let list = gal
            .create_command_list(CommandListDesc {
                label: "copy".to_string(),
                operations: vec![
                    CommandOp::HostWriteBuffer {
                        buffer: src,
                        offset: 0,
                        data: vec![7; 16],
                    },
                    buffer_barrier(src),
                    CommandOp::CopyBuffer { src, dst, size: 16 },
                    buffer_barrier(dst),
                    CommandOp::HostReadBuffer {
                        buffer: dst,
                        offset: 0,
                        size: 16,
                    },
                ],
            })
            .unwrap();
        let token = gal
            .submit(SubmissionBatch {
                label: "copy-submit".to_string(),
                command_lists: vec![list],
            })
            .unwrap();
        gal.retire_through_for_test(token.submission).unwrap();
        let reads = gal
            .opengl_backend()
            .unwrap()
            .completed_host_reads_for_test();
        assert_eq!(reads.last().unwrap().bytes, vec![7; 16]);
    }

    fn buffer_barrier(resource: Handle) -> CommandOp {
        CommandOp::Barrier(ResourceBarrier {
            resource,
            subresources: None,
            before: TextureUsageState::TransferDst,
            after: TextureUsageState::TransferSrc,
            stages: PipelineStageFlags::TRANSFER,
            access: AccessFlags::TRANSFER,
            src_queue: QueueClass::Graphics,
            dst_queue: QueueClass::Graphics,
        })
    }
}
