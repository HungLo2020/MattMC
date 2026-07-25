mod device;
mod lowering;
#[cfg(test)]
mod renderdoc;
mod resources;
pub mod shaderc_spirv_compiler;
mod swapchain;
mod trace;

use std::sync::{Arc, Mutex, MutexGuard};

use super::{
    graphics_backend_lock, presentation_capabilities, vulkan_capabilities, Backend,
    BackendCreateDesc, BackendToken, CompletedHostRead,
};
use crate::render::vulkanic::commands::ValidatedSubmissionBatch;
use crate::render::vulkanic::error::GalResult;
use crate::render::vulkanic::frame::{
    AcquiredFrame, FrameAcquireDesc, FrameResizeDesc, FrameResizeResult, FrameSurfaceDesc,
    PresentFrameDesc, PresentedFrame,
};
use crate::render::vulkanic::handles::{Handle, HandleKind};
use crate::render::vulkanic::resources::BackendCapabilities;
use crate::render::vulkanic::sync::SubmissionId;

use self::device::{ValidationMode, VulkanContext};
use self::lowering::SubmissionLowerer;
use self::resources::VulkanObjects;
use self::swapchain::{SurfaceOwner, VulkanSwapchain};

pub(in crate::render::vulkanic) struct VulkanBackend {
    context: Arc<VulkanContext>,
    objects: VulkanObjects,
    lowerer: Mutex<SubmissionLowerer>,
    swapchain: Option<VulkanSwapchain>,
    presentation_capable: bool,
    _global_lock: MutexGuard<'static, ()>,
}

#[allow(dead_code)]
impl VulkanBackend {
    pub(in crate::render::vulkanic) fn new(label: &str) -> GalResult<Self> {
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
            swapchain: None,
            presentation_capable: false,
            _global_lock: global_lock,
        })
    }

    pub(in crate::render::vulkanic::backends) fn new_windowed(
        label: &str,
        surface_owner: &dyn SurfaceOwner,
        surface_desc: FrameSurfaceDesc,
    ) -> GalResult<Self> {
        let global_lock = graphics_backend_lock().lock().map_err(|_| {
            crate::render::vulkanic::error::GalError::backend(
                "graphics backend global lock poisoned",
            )
        })?;
        let context = VulkanContext::new_with_surface(
            label,
            ValidationMode::from_env(),
            Some(surface_owner),
        )?;
        let stable_window_id = surface_owner.stable_window_id();
        let swapchain = VulkanSwapchain::new(context.clone(), surface_desc, stable_window_id)?;
        let _ = swapchain.stable_window_id();
        Ok(Self {
            objects: VulkanObjects::new(context.clone()),
            lowerer: Mutex::new(SubmissionLowerer::new(context.clone())),
            context,
            swapchain: Some(swapchain),
            presentation_capable: true,
            _global_lock: global_lock,
        })
    }

    pub(super) fn completed_host_reads_snapshot(&self) -> Vec<lowering::CompletedHostRead> {
        self.lowerer
            .lock()
            .map(|lowerer| lowerer.completed_host_reads_snapshot().to_vec())
            .unwrap_or_default()
    }

    #[cfg(test)]
    pub(super) fn completed_host_reads_for_test(&self) -> Vec<lowering::CompletedHostRead> {
        self.completed_host_reads_snapshot()
    }

    #[cfg(test)]
    pub(super) fn clear_acquired_frame_for_test(
        &mut self,
        frame: crate::render::vulkanic::frame::FrameId,
        color: [f32; 4],
    ) -> GalResult<()> {
        let Some(swapchain) = &mut self.swapchain else {
            return Err(crate::render::vulkanic::error::GalError::backend(
                "Vulkan backend has no test swapchain",
            ));
        };
        swapchain.clear_acquired_for_test(frame, color)
    }
}

impl Backend for VulkanBackend {
    fn capabilities(&self) -> BackendCapabilities {
        if self.presentation_capable {
            presentation_capabilities(vulkan_capabilities())
        } else {
            vulkan_capabilities()
        }
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
        trace::message(&format!("gal.submission backend=vulkan id={}", id.0));
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

    fn configure_frame_surface(&mut self, desc: &FrameSurfaceDesc) -> GalResult<()> {
        let Some(swapchain) = &mut self.swapchain else {
            return Err(
                crate::render::vulkanic::error::GalError::unsupported_feature(
                    "Vulkan backend was not created with a presentation surface",
                ),
            );
        };
        swapchain.configure(desc)
    }

    fn acquire_frame(&mut self, desc: &FrameAcquireDesc) -> GalResult<AcquiredFrame> {
        let Some(swapchain) = &mut self.swapchain else {
            return Err(
                crate::render::vulkanic::error::GalError::unsupported_feature(
                    "Vulkan backend was not created with a presentation surface",
                ),
            );
        };
        swapchain.acquire(desc)
    }

    fn resize_frame_surface(&mut self, desc: &FrameResizeDesc) -> GalResult<FrameResizeResult> {
        let Some(swapchain) = &mut self.swapchain else {
            return Err(
                crate::render::vulkanic::error::GalError::unsupported_feature(
                    "Vulkan backend was not created with a presentation surface",
                ),
            );
        };
        swapchain.resize(desc)
    }

    fn present_frame(&mut self, desc: &PresentFrameDesc) -> GalResult<PresentedFrame> {
        let Some(swapchain) = &mut self.swapchain else {
            return Err(
                crate::render::vulkanic::error::GalError::unsupported_feature(
                    "Vulkan backend was not created with a presentation surface",
                ),
            );
        };
        swapchain.present(desc)
    }

    fn shutdown_frame_surface(&mut self) -> GalResult<()> {
        if let Some(swapchain) = &mut self.swapchain {
            swapchain.shutdown();
        }
        Ok(())
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
        if let Some(swapchain) = &mut self.swapchain {
            swapchain.shutdown();
        }
        self.objects.destroy_all();
    }
}

#[cfg(test)]
pub(super) mod conformance;

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::commands::{CommandListDesc, CommandOp, SubmissionBatch};
    use crate::render::vulkanic::frame::{
        FrameAcquireDesc, FrameAcquireStatus, FrameCorrelationId, FrameResizeDesc,
        FrameSurfaceDesc, PresentFrameDesc, PresentMode,
    };
    use crate::render::vulkanic::gal::VulkanicGal;
    use crate::render::vulkanic::resources::{
        AccessFlags, BufferDesc, BufferUsage, Extent3d, MemoryDomain, PipelineLayoutDesc,
        PipelineStageFlags, ResourceBinding, ResourceBindingDesc, ResourceBindingKind,
        ResourceLayoutDesc, ResourceSetDesc, TextureFormat,
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

    #[cfg(target_os = "linux")]
    #[test]
    fn vulkan_windowed_conformance_acquires_presents_resizes_or_reports_environment_gap() {
        if std::env::var("MATTMC_RUN_WINDOWED_CONFORMANCE")
            .map(|value| value != "1" && !value.eq_ignore_ascii_case("true"))
            .unwrap_or(true)
        {
            return;
        }
        let event_loop = match winit_event_loop() {
            Ok(event_loop) => event_loop,
            Err(error) => {
                assert!(
                    error.to_string().contains("window")
                        || error.to_string().contains("display")
                        || error.to_string().contains("event loop"),
                    "unexpected event-loop creation failure: {error}"
                );
                return;
            }
        };
        let window = match winit_test_window(&event_loop, 64, 48, 1) {
            Ok(window) => window,
            Err(error) => {
                assert!(
                    error.to_string().contains("window")
                        || error.to_string().contains("display")
                        || error.to_string().contains("surface"),
                    "unexpected window creation failure: {error}"
                );
                return;
            }
        };
        let surface = FrameSurfaceDesc {
            label: "test-window".to_string(),
            extent: Extent3d {
                width: 64,
                height: 48,
                depth: 1,
            },
            color_format: TextureFormat::Bgra8Unorm,
            present_mode: PresentMode::Fifo,
            max_frames_in_flight: 2,
        };
        let backend = match VulkanBackend::new_windowed(
            "MattMC windowed VulkanicGAL conformance",
            &window,
            surface,
        ) {
            Ok(backend) => backend,
            Err(error) => {
                let message = error.to_string();
                assert!(
                    message.contains("Vulkan")
                        || message.contains("vulkan")
                        || message.contains("surface")
                        || message.contains("present"),
                    "unexpected windowed Vulkan bootstrap failure: {message}"
                );
                return;
            }
        };
        let mut gal = VulkanicGal::new_with_backend(Box::new(backend), false);
        for frame_index in 0..2 {
            let acquired = gal
                .acquire_frame(FrameAcquireDesc {
                    correlation_id: FrameCorrelationId(frame_index + 1),
                    expected_extent: Extent3d {
                        width: 64,
                        height: 48,
                        depth: 1,
                    },
                })
                .expect("windowed Vulkan acquire should succeed");
            assert!(matches!(
                acquired.status,
                FrameAcquireStatus::Ready | FrameAcquireStatus::Suboptimal
            ));
            gal.vulkan_backend_mut()
                .unwrap()
                .clear_acquired_frame_for_test(acquired.frame, [0.1, 0.2, 0.8, 1.0])
                .expect("test clear should prepare the swapchain image for present");
            let presented = gal
                .present_frame(PresentFrameDesc {
                    frame: acquired.frame,
                    correlation_id: acquired.correlation_id,
                    wait_for: SubmissionId(0),
                })
                .expect("windowed Vulkan present should succeed");
            assert!(matches!(
                presented.status,
                crate::render::vulkanic::frame::FramePresentStatus::Presented
                    | crate::render::vulkanic::frame::FramePresentStatus::Suboptimal
            ));
        }
        let resized = gal
            .resize_frame_surface(FrameResizeDesc {
                correlation_id: FrameCorrelationId(10),
                extent: Extent3d {
                    width: 32,
                    height: 32,
                    depth: 1,
                },
            })
            .expect("windowed Vulkan resize should recreate swapchain");
        assert_eq!(resized.status, FrameAcquireStatus::Resized);
        let minimized = gal
            .resize_frame_surface(FrameResizeDesc {
                correlation_id: FrameCorrelationId(11),
                extent: Extent3d {
                    width: 0,
                    height: 0,
                    depth: 1,
                },
            })
            .expect("zero-size Vulkan resize should be modeled as minimized");
        assert_eq!(minimized.status, FrameAcquireStatus::Minimized);
        gal.shutdown_frame_surface()
            .expect("windowed Vulkan shutdown should be clean");
        drop(gal);
        drop(window);

        for cycle in 0..2 {
            let window = match winit_test_window(&event_loop, 32, 32, 10 + cycle) {
                Ok(window) => window,
                Err(_) => return,
            };
            let backend = match VulkanBackend::new_windowed(
                "MattMC repeated windowed VulkanicGAL conformance",
                &window,
                FrameSurfaceDesc {
                    label: format!("repeated-window-{cycle}"),
                    extent: Extent3d {
                        width: 32,
                        height: 32,
                        depth: 1,
                    },
                    color_format: TextureFormat::Bgra8Unorm,
                    present_mode: PresentMode::Fifo,
                    max_frames_in_flight: 2,
                },
            ) {
                Ok(backend) => backend,
                Err(_) => return,
            };
            let mut gal = VulkanicGal::new_with_backend(Box::new(backend), false);
            let acquired = gal
                .acquire_frame(FrameAcquireDesc {
                    correlation_id: FrameCorrelationId(20 + u64::from(cycle)),
                    expected_extent: Extent3d {
                        width: 32,
                        height: 32,
                        depth: 1,
                    },
                })
                .expect("repeated window acquire should succeed");
            if matches!(
                acquired.status,
                FrameAcquireStatus::Ready | FrameAcquireStatus::Suboptimal
            ) {
                gal.vulkan_backend_mut()
                    .unwrap()
                    .clear_acquired_frame_for_test(acquired.frame, [0.0, 0.3, 0.2, 1.0])
                    .expect("test clear should prepare repeated window frame");
                gal.present_frame(PresentFrameDesc {
                    frame: acquired.frame,
                    correlation_id: acquired.correlation_id,
                    wait_for: SubmissionId(0),
                })
                .expect("repeated window present should succeed");
            }
            gal.shutdown_frame_surface()
                .expect("repeated window shutdown should be clean");
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

    #[cfg(target_os = "linux")]
    struct WinitTestWindow {
        _window: winit::window::Window,
        raw_display: raw_window_handle::RawDisplayHandle,
        raw_window: raw_window_handle::RawWindowHandle,
        stable_window_id: u64,
    }

    #[cfg(target_os = "linux")]
    impl SurfaceOwner for WinitTestWindow {
        fn required_instance_extensions(&self) -> Vec<&'static std::ffi::CStr> {
            match self.raw_display {
                raw_window_handle::RawDisplayHandle::Xlib(_) => {
                    vec![ash::khr::surface::NAME, ash::khr::xlib_surface::NAME]
                }
                raw_window_handle::RawDisplayHandle::Xcb(_) => {
                    vec![ash::khr::surface::NAME, ash::khr::xcb_surface::NAME]
                }
                raw_window_handle::RawDisplayHandle::Wayland(_) => {
                    vec![ash::khr::surface::NAME, ash::khr::wayland_surface::NAME]
                }
                _ => vec![ash::khr::surface::NAME],
            }
        }

        fn create_surface(
            &self,
            entry: &ash::Entry,
            instance: &ash::Instance,
        ) -> GalResult<ash::vk::SurfaceKHR> {
            match (self.raw_display, self.raw_window) {
                (
                    raw_window_handle::RawDisplayHandle::Xlib(display),
                    raw_window_handle::RawWindowHandle::Xlib(window),
                ) => {
                    let display = display.display.ok_or_else(|| {
                        crate::render::vulkanic::error::GalError::backend(
                            "winit Xlib display handle is empty",
                        )
                    })?;
                    let loader = ash::khr::xlib_surface::Instance::new(entry, instance);
                    let info = ash::vk::XlibSurfaceCreateInfoKHR::default()
                        .dpy(display.as_ptr().cast::<ash::vk::Display>())
                        .window(window.window);
                    unsafe { loader.create_xlib_surface(&info, None) }.map_err(|error| {
                        crate::render::vulkanic::error::GalError::backend(format!(
                            "failed to create winit Xlib Vulkan surface: {error:?}"
                        ))
                    })
                }
                (
                    raw_window_handle::RawDisplayHandle::Xcb(display),
                    raw_window_handle::RawWindowHandle::Xcb(window),
                ) => {
                    let connection = display.connection.ok_or_else(|| {
                        crate::render::vulkanic::error::GalError::backend(
                            "winit XCB connection handle is empty",
                        )
                    })?;
                    let loader = ash::khr::xcb_surface::Instance::new(entry, instance);
                    let info = ash::vk::XcbSurfaceCreateInfoKHR::default()
                        .connection(connection.as_ptr().cast::<ash::vk::xcb_connection_t>())
                        .window(window.window.get());
                    unsafe { loader.create_xcb_surface(&info, None) }.map_err(|error| {
                        crate::render::vulkanic::error::GalError::backend(format!(
                            "failed to create winit XCB Vulkan surface: {error:?}"
                        ))
                    })
                }
                (
                    raw_window_handle::RawDisplayHandle::Wayland(display),
                    raw_window_handle::RawWindowHandle::Wayland(window),
                ) => {
                    let loader = ash::khr::wayland_surface::Instance::new(entry, instance);
                    let info = ash::vk::WaylandSurfaceCreateInfoKHR::default()
                        .display(display.display.as_ptr())
                        .surface(window.surface.as_ptr());
                    unsafe { loader.create_wayland_surface(&info, None) }.map_err(|error| {
                        crate::render::vulkanic::error::GalError::backend(format!(
                            "failed to create winit Wayland Vulkan surface: {error:?}"
                        ))
                    })
                }
                _ => Err(crate::render::vulkanic::error::GalError::backend(
                    "winit produced unsupported Linux window/display handle pair for Vulkan",
                )),
            }
        }

        fn stable_window_id(&self) -> u64 {
            self.stable_window_id
        }
    }

    #[cfg(target_os = "linux")]
    fn winit_event_loop() -> GalResult<winit::event_loop::EventLoop<()>> {
        use winit::event_loop::EventLoop;

        let mut builder = EventLoop::builder();
        winit::platform::x11::EventLoopBuilderExtX11::with_any_thread(&mut builder, true);
        winit::platform::wayland::EventLoopBuilderExtWayland::with_any_thread(&mut builder, true);
        builder.build().map_err(|error| {
            crate::render::vulkanic::error::GalError::backend(format!(
                "failed to create winit event loop for windowed conformance: {error}"
            ))
        })
    }

    #[cfg(target_os = "linux")]
    fn winit_test_window(
        event_loop: &winit::event_loop::EventLoop<()>,
        width: u32,
        height: u32,
        stable_window_id: u64,
    ) -> GalResult<WinitTestWindow> {
        use raw_window_handle::{HasDisplayHandle, HasWindowHandle};
        use winit::dpi::PhysicalSize;
        use winit::window::Window;

        #[allow(deprecated)]
        let window = event_loop
            .create_window(
                Window::default_attributes()
                    .with_title("MattMC VulkanicGAL windowed conformance")
                    .with_inner_size(PhysicalSize::new(width, height))
                    .with_visible(true),
            )
            .map_err(|error| {
                crate::render::vulkanic::error::GalError::backend(format!(
                    "failed to create winit window for conformance: {error}"
                ))
            })?;
        let raw_display = window
            .display_handle()
            .map_err(|error| {
                crate::render::vulkanic::error::GalError::backend(format!(
                    "failed to read winit display handle: {error}"
                ))
            })?
            .as_raw();
        let raw_window = window
            .window_handle()
            .map_err(|error| {
                crate::render::vulkanic::error::GalError::backend(format!(
                    "failed to read winit window handle: {error}"
                ))
            })?
            .as_raw();
        Ok(WinitTestWindow {
            _window: window,
            raw_display,
            raw_window,
            stable_window_id,
        })
    }
}
