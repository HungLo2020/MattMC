use std::ffi::CStr;
use std::sync::Arc;

use ash::vk;

use super::device::VulkanContext;
use super::resources::{aspect_for_format, texture_format};
use super::trace;
use crate::render::vulkanic::backends::vulkan::resources::FrameTargetObject;
use crate::render::vulkanic::backends::BackendToken;
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::frame::{
    AcquiredFrame, FrameAcquireDesc, FrameAcquireStatus, FrameId, FramePresentStatus,
    FrameRenderTargetId, FrameResizeDesc, FrameResizeResult, FrameSurfaceDesc, PresentFrameDesc,
    PresentMode, PresentedFrame,
};
use crate::render::vulkanic::resources::{Extent3d, TextureFormat};
use crate::render::vulkanic::sync::SubmissionId;

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub(super) struct SwapchainRuntimeMetrics {
    pub(super) acquire_nanos: u64,
    pub(super) present_nanos: u64,
    pub(super) present_wait_nanos: u64,
    pub(super) present_mode: u64,
    pub(super) acquired_image_index: u64,
    pub(super) swapchain_generation: u64,
    pub(super) images_in_flight: u64,
    pub(super) available_frame_slots: u64,
}

pub(in crate::render::vulkanic) trait SurfaceOwner {
    fn required_instance_extensions(&self) -> Vec<&'static CStr>;
    fn create_surface(
        &self,
        entry: &ash::Entry,
        instance: &ash::Instance,
    ) -> GalResult<vk::SurfaceKHR>;
    fn stable_window_id(&self) -> u64;
}

pub(super) struct VulkanSwapchain {
    context: Arc<VulkanContext>,
    loader: ash::khr::swapchain::Device,
    desc: FrameSurfaceDesc,
    swapchain: vk::SwapchainKHR,
    format: vk::Format,
    color_format: TextureFormat,
    extent: vk::Extent2D,
    images: Vec<vk::Image>,
    image_layouts: Vec<vk::ImageLayout>,
    image_views: Vec<vk::ImageView>,
    acquired: Vec<AcquiredImage>,
    next_frame: u64,
    stable_window_id: u64,
    recreate_count: u64,
    present_mode: vk::PresentModeKHR,
    metrics: SwapchainRuntimeMetrics,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
struct AcquiredImage {
    frame: FrameId,
    image_index: u32,
    render_target: FrameRenderTargetId,
    suboptimal: bool,
}

impl VulkanSwapchain {
    pub(super) fn new(
        context: Arc<VulkanContext>,
        desc: FrameSurfaceDesc,
        stable_window_id: u64,
    ) -> GalResult<Self> {
        let loader =
            context.swapchain_loader.as_ref().cloned().ok_or_else(|| {
                GalError::backend("Vulkan context was not created for presentation")
            })?;
        let mut owner = Self {
            context,
            loader,
            desc,
            swapchain: vk::SwapchainKHR::null(),
            format: vk::Format::UNDEFINED,
            color_format: TextureFormat::Rgba8Unorm,
            extent: vk::Extent2D {
                width: 0,
                height: 0,
            },
            images: Vec::new(),
            image_layouts: Vec::new(),
            image_views: Vec::new(),
            acquired: Vec::new(),
            next_frame: 0,
            stable_window_id,
            recreate_count: 0,
            present_mode: vk::PresentModeKHR::FIFO,
            metrics: SwapchainRuntimeMetrics::default(),
        };
        owner.recreate(vk::SwapchainKHR::null())?;
        Ok(owner)
    }

    pub(super) fn configure(&mut self, desc: &FrameSurfaceDesc) -> GalResult<()> {
        self.desc = desc.clone();
        self.recreate(self.swapchain)
    }

    pub(super) fn acquire(&mut self, desc: &FrameAcquireDesc) -> GalResult<AcquiredFrame> {
        let _zone = trace::Zone::new("vulkan.swapchain.acquire");
        let acquire_started = std::time::Instant::now();
        self.next_frame += 1;
        let frame = FrameId(self.next_frame);
        if desc.expected_extent.width == 0 || desc.expected_extent.height == 0 {
            self.metrics.acquire_nanos = self
                .metrics
                .acquire_nanos
                .saturating_add(crate::render::vulkanic::metrics::elapsed_nanos_u64(
                    acquire_started,
                ));
            return Ok(AcquiredFrame {
                frame,
                correlation_id: desc.correlation_id,
                status: FrameAcquireStatus::Minimized,
                render_target: FrameRenderTargetId(0),
                extent: desc.expected_extent,
                color_format: self.desc.color_format,
            });
        }
        let fence_info = vk::FenceCreateInfo::default();
        let acquire_fence = unsafe { self.context.device.create_fence(&fence_info, None) }
            .map_err(|error| {
                GalError::backend(format!("failed to create Vulkan acquire fence: {error:?}"))
            })?;
        let acquire_result = unsafe {
            self.loader.acquire_next_image(
                self.swapchain,
                1_000_000_000,
                vk::Semaphore::null(),
                acquire_fence,
            )
        };
        let (image_index, suboptimal) = match acquire_result {
            Ok(result) => {
                if let Err(error) = unsafe {
                    self.context
                        .device
                        .wait_for_fences(&[acquire_fence], true, 1_000_000_000)
                } {
                    unsafe { self.context.device.destroy_fence(acquire_fence, None) };
                    return Err(GalError::backend(format!(
                        "Vulkan acquire fence wait failed: {error:?}"
                    )));
                }
                result
            }
            Err(error) => {
                unsafe { self.context.device.destroy_fence(acquire_fence, None) };
                return Err(match error {
                    vk::Result::ERROR_OUT_OF_DATE_KHR => GalError::backend(
                        "Vulkan swapchain was out-of-date during acquire; resize is required",
                    ),
                    other => {
                        GalError::backend(format!("Vulkan swapchain acquire failed: {other:?}"))
                    }
                });
            }
        };
        unsafe { self.context.device.destroy_fence(acquire_fence, None) };
        let render_target = self.render_target_for_image(image_index);
        self.acquired.push(AcquiredImage {
            frame,
            image_index,
            render_target,
            suboptimal,
        });
        self.metrics.acquire_nanos = self
            .metrics
            .acquire_nanos
            .saturating_add(crate::render::vulkanic::metrics::elapsed_nanos_u64(
                acquire_started,
            ));
        self.metrics.acquired_image_index = u64::from(image_index);
        self.metrics.swapchain_generation = self.recreate_count;
        self.metrics.images_in_flight = self.acquired.len() as u64;
        self.metrics.available_frame_slots = self
            .images
            .len()
            .saturating_sub(self.acquired.len()) as u64;
        trace::message(&format!(
            "gal.frame.acquire backend=vulkan correlation={} frame={} image={} target={} generation={} window={}",
            desc.correlation_id.0,
            frame.0,
            image_index,
            render_target.0,
            self.recreate_count,
            self.stable_window_id
        ));
        Ok(AcquiredFrame {
            frame,
            correlation_id: desc.correlation_id,
            status: if suboptimal {
                FrameAcquireStatus::Suboptimal
            } else {
                FrameAcquireStatus::Ready
            },
            render_target,
            extent: Extent3d {
                width: self.extent.width,
                height: self.extent.height,
                depth: 1,
            },
            color_format: self.color_format,
        })
    }

    pub(super) fn resize(&mut self, desc: &FrameResizeDesc) -> GalResult<FrameResizeResult> {
        self.desc.extent = desc.extent;
        if desc.extent.width == 0 || desc.extent.height == 0 {
            return Ok(FrameResizeResult {
                status: FrameAcquireStatus::Minimized,
                extent: desc.extent,
            });
        }
        self.recreate(self.swapchain)?;
        Ok(FrameResizeResult {
            status: FrameAcquireStatus::Resized,
            extent: self.desc.extent,
        })
    }

    pub(super) fn present(&mut self, desc: &PresentFrameDesc) -> GalResult<PresentedFrame> {
        let _zone = trace::Zone::new("vulkan.swapchain.present");
        let present_started = std::time::Instant::now();
        let Some(position) = self
            .acquired
            .iter()
            .position(|acquired| acquired.frame == desc.frame)
        else {
            return Err(GalError::submission(
                crate::render::vulkanic::StatusCode::InvalidArgument,
                "Vulkan frame was not acquired before present",
            ));
        };
        let acquired = self.acquired.remove(position);
        let wait_started = std::time::Instant::now();
        wait_timeline(&self.context, desc.wait_for)?;
        self.metrics.present_wait_nanos = self
            .metrics
            .present_wait_nanos
            .saturating_add(crate::render::vulkanic::metrics::elapsed_nanos_u64(
                wait_started,
            ));
        let swapchains = [self.swapchain];
        let image_indices = [acquired.image_index];
        let present_info = vk::PresentInfoKHR::default()
            .swapchains(&swapchains)
            .image_indices(&image_indices);
        let status = match unsafe { self.loader.queue_present(self.context.queue, &present_info) } {
            Ok(suboptimal) => {
                if suboptimal || acquired.suboptimal {
                    FramePresentStatus::Suboptimal
                } else {
                    FramePresentStatus::Presented
                }
            }
            Err(vk::Result::ERROR_OUT_OF_DATE_KHR) => FramePresentStatus::OutOfDate,
            Err(error) => {
                return Err(GalError::backend(format!(
                    "Vulkan swapchain present failed: {error:?}"
                )))
            }
        };
        if let Some(layout) = self.image_layouts.get_mut(acquired.image_index as usize) {
            *layout = vk::ImageLayout::PRESENT_SRC_KHR;
        }
        self.metrics.present_nanos = self
            .metrics
            .present_nanos
            .saturating_add(crate::render::vulkanic::metrics::elapsed_nanos_u64(
                present_started,
            ));
        self.metrics.images_in_flight = self.acquired.len() as u64;
        self.metrics.available_frame_slots = self
            .images
            .len()
            .saturating_sub(self.acquired.len()) as u64;
        trace::message(&format!(
            "gal.frame.present backend=vulkan correlation={} frame={} image={} submission={} status={:?} window={}",
            desc.correlation_id.0,
            desc.frame.0,
            acquired.image_index,
            desc.wait_for.0,
            status,
            self.stable_window_id
        ));
        Ok(PresentedFrame {
            frame: desc.frame,
            correlation_id: desc.correlation_id,
            render_target: acquired.render_target,
            status,
            completed_submission: desc.wait_for,
        })
    }

    pub(super) fn shutdown(&mut self) {
        self.destroy_swapchain_objects();
    }

    pub(super) fn stable_window_id(&self) -> u64 {
        self.stable_window_id
    }

    pub(super) fn metrics(&self) -> SwapchainRuntimeMetrics {
        self.metrics
    }

    pub(super) fn frame_target_object(
        &self,
        desc: &crate::render::vulkanic::resources::FrameTargetDesc,
        token: BackendToken,
    ) -> GalResult<FrameTargetObject> {
        self.frame_target_object_for_render_target(
            desc.render_target,
            desc.extent,
            desc.color_format,
            token,
        )
    }

    pub(super) fn frame_target_object_for_render_target(
        &self,
        render_target: FrameRenderTargetId,
        extent: Extent3d,
        color_format: TextureFormat,
        token: BackendToken,
    ) -> GalResult<FrameTargetObject> {
        let acquired = self
            .acquired
            .iter()
            .find(|acquired| acquired.render_target == render_target)
            .ok_or_else(|| {
                GalError::backend(
                    "frame target was used without the matching acquired swapchain image",
                )
            })?;
        let index = acquired.image_index as usize;
        let image = *self
            .images
            .get(index)
            .ok_or_else(|| GalError::backend("acquired swapchain image index is out of range"))?;
        let image_view = *self.image_views.get(index).ok_or_else(|| {
            GalError::backend("acquired swapchain image view index is out of range")
        })?;
        let image_layout = *self.image_layouts.get(index).ok_or_else(|| {
            GalError::backend("acquired swapchain image layout index is out of range")
        })?;
        Ok(FrameTargetObject {
            token,
            frame_id: acquired.frame.0,
            render_target,
            extent,
            color_format,
            image_index: acquired.image_index,
            image,
            image_view,
            image_layout,
        })
    }

    fn render_target_for_image(&self, image_index: u32) -> FrameRenderTargetId {
        FrameRenderTargetId((self.recreate_count << 32) | (u64::from(image_index) + 1))
    }

    fn recreate(&mut self, old_swapchain: vk::SwapchainKHR) -> GalResult<()> {
        let _zone = trace::Zone::new("vulkan.swapchain.recreate");
        if self.desc.extent.width == 0 || self.desc.extent.height == 0 {
            return Ok(());
        }
        let surface = self
            .context
            .surface
            .ok_or_else(|| GalError::backend("Vulkan presentation surface is missing"))?;
        let surface_loader = self
            .context
            .surface_loader
            .as_ref()
            .ok_or_else(|| GalError::backend("Vulkan surface loader is missing"))?;
        let capabilities = unsafe {
            surface_loader
                .get_physical_device_surface_capabilities(self.context.physical_device, surface)
        }
        .map_err(|error| {
            GalError::backend(format!(
                "failed to query Vulkan surface capabilities: {error:?}"
            ))
        })?;
        let formats = unsafe {
            surface_loader
                .get_physical_device_surface_formats(self.context.physical_device, surface)
        }
        .map_err(|error| {
            GalError::backend(format!("failed to query Vulkan surface formats: {error:?}"))
        })?;
        let present_modes = unsafe {
            surface_loader
                .get_physical_device_surface_present_modes(self.context.physical_device, surface)
        }
        .map_err(|error| {
            GalError::backend(format!("failed to query Vulkan present modes: {error:?}"))
        })?;
        let (format, color_format) = choose_format(&formats, self.desc.color_format);
        let extent = choose_extent(capabilities, self.desc.extent);
        let present_mode = choose_present_mode(&present_modes, self.desc.present_mode);
        let image_count = choose_image_count(capabilities, self.desc.max_frames_in_flight);
        let create = vk::SwapchainCreateInfoKHR::default()
            .surface(surface)
            .min_image_count(image_count)
            .image_format(format.format)
            .image_color_space(format.color_space)
            .image_extent(extent)
            .image_array_layers(1)
            .image_usage(vk::ImageUsageFlags::COLOR_ATTACHMENT | vk::ImageUsageFlags::TRANSFER_DST)
            .image_sharing_mode(vk::SharingMode::EXCLUSIVE)
            .pre_transform(capabilities.current_transform)
            .composite_alpha(vk::CompositeAlphaFlagsKHR::OPAQUE)
            .present_mode(present_mode)
            .clipped(true)
            .old_swapchain(old_swapchain);
        let swapchain =
            unsafe { self.loader.create_swapchain(&create, None) }.map_err(|error| {
                GalError::backend(format!("failed to create Vulkan swapchain: {error:?}"))
            })?;
        self.destroy_image_views();
        if old_swapchain != vk::SwapchainKHR::null() {
            unsafe { self.loader.destroy_swapchain(old_swapchain, None) };
        }
        let images = unsafe { self.loader.get_swapchain_images(swapchain) }.map_err(|error| {
            GalError::backend(format!(
                "failed to enumerate Vulkan swapchain images: {error:?}"
            ))
        })?;
        let image_views = images
            .iter()
            .enumerate()
            .map(|(index, image)| {
                create_image_view(&self.context, *image, color_format, index as u32)
            })
            .collect::<GalResult<Vec<_>>>()?;
        self.swapchain = swapchain;
        self.format = format.format;
        self.color_format = color_format;
        self.extent = extent;
        self.present_mode = present_mode;
        self.images = images;
        self.image_layouts = vec![vk::ImageLayout::UNDEFINED; self.images.len()];
        self.image_views = image_views;
        self.acquired.clear();
        self.recreate_count += 1;
        self.metrics.present_mode = present_mode.as_raw() as u64;
        self.metrics.swapchain_generation = self.recreate_count;
        self.metrics.images_in_flight = 0;
        self.metrics.available_frame_slots = self.images.len() as u64;
        trace::message(&format!(
            "gal.swapchain.recreate backend=vulkan count={} extent={}x{} images={} format={} window={}",
            self.recreate_count,
            self.extent.width,
            self.extent.height,
            self.images.len(),
            self.format.as_raw(),
            self.stable_window_id
        ));
        Ok(())
    }

    fn destroy_swapchain_objects(&mut self) {
        self.destroy_image_views();
        if self.swapchain != vk::SwapchainKHR::null() {
            unsafe { self.loader.destroy_swapchain(self.swapchain, None) };
            self.swapchain = vk::SwapchainKHR::null();
        }
        self.acquired.clear();
    }

    fn destroy_image_views(&mut self) {
        for view in self.image_views.drain(..) {
            unsafe { self.context.device.destroy_image_view(view, None) };
        }
        self.images.clear();
        self.image_layouts.clear();
    }
}

#[cfg(test)]
impl VulkanSwapchain {
    pub(super) fn clear_acquired_for_test(
        &mut self,
        frame: FrameId,
        color: [f32; 4],
    ) -> GalResult<()> {
        let Some(acquired) = self
            .acquired
            .iter()
            .find(|acquired| acquired.frame == frame)
        else {
            return Err(GalError::backend("test frame was not acquired"));
        };
        let image = *self
            .images
            .get(acquired.image_index as usize)
            .ok_or_else(|| GalError::backend("test acquired image index was out of range"))?;
        let current_layout = *self
            .image_layouts
            .get(acquired.image_index as usize)
            .ok_or_else(|| GalError::backend("test acquired image layout was out of range"))?;
        let src_stage = if current_layout == vk::ImageLayout::PRESENT_SRC_KHR {
            vk::PipelineStageFlags2::BOTTOM_OF_PIPE
        } else {
            vk::PipelineStageFlags2::TOP_OF_PIPE
        };
        let allocate = vk::CommandBufferAllocateInfo::default()
            .command_pool(self.context.command_pool)
            .level(vk::CommandBufferLevel::PRIMARY)
            .command_buffer_count(1);
        let command_buffer = unsafe { self.context.device.allocate_command_buffers(&allocate) }
            .map_err(|error| {
                GalError::backend(format!(
                    "failed to allocate test present command buffer: {error:?}"
                ))
            })?
            .into_iter()
            .next()
            .ok_or_else(|| GalError::backend("Vulkan returned no test command buffer"))?;
        let begin = vk::CommandBufferBeginInfo::default()
            .flags(vk::CommandBufferUsageFlags::ONE_TIME_SUBMIT);
        unsafe {
            self.context
                .device
                .begin_command_buffer(command_buffer, &begin)
        }
        .map_err(|error| {
            GalError::backend(format!("failed to begin test present command: {error:?}"))
        })?;
        let range = vk::ImageSubresourceRange {
            aspect_mask: vk::ImageAspectFlags::COLOR,
            base_mip_level: 0,
            level_count: 1,
            base_array_layer: 0,
            layer_count: 1,
        };
        let to_transfer = vk::ImageMemoryBarrier2::default()
            .src_stage_mask(src_stage)
            .dst_stage_mask(vk::PipelineStageFlags2::TRANSFER)
            .dst_access_mask(vk::AccessFlags2::TRANSFER_WRITE)
            .old_layout(current_layout)
            .new_layout(vk::ImageLayout::TRANSFER_DST_OPTIMAL)
            .image(image)
            .subresource_range(range);
        unsafe {
            self.context.device.cmd_pipeline_barrier2(
                command_buffer,
                &vk::DependencyInfo::default()
                    .image_memory_barriers(std::slice::from_ref(&to_transfer)),
            );
            self.context.device.cmd_clear_color_image(
                command_buffer,
                image,
                vk::ImageLayout::TRANSFER_DST_OPTIMAL,
                &vk::ClearColorValue { float32: color },
                &[range],
            );
        }
        let to_present = vk::ImageMemoryBarrier2::default()
            .src_stage_mask(vk::PipelineStageFlags2::TRANSFER)
            .src_access_mask(vk::AccessFlags2::TRANSFER_WRITE)
            .dst_stage_mask(vk::PipelineStageFlags2::BOTTOM_OF_PIPE)
            .old_layout(vk::ImageLayout::TRANSFER_DST_OPTIMAL)
            .new_layout(vk::ImageLayout::PRESENT_SRC_KHR)
            .image(image)
            .subresource_range(range);
        unsafe {
            self.context.device.cmd_pipeline_barrier2(
                command_buffer,
                &vk::DependencyInfo::default()
                    .image_memory_barriers(std::slice::from_ref(&to_present)),
            );
            self.context
                .device
                .end_command_buffer(command_buffer)
                .map_err(|error| {
                    GalError::backend(format!("failed to end test present command: {error:?}"))
                })?;
        }
        let fence = unsafe {
            self.context
                .device
                .create_fence(&vk::FenceCreateInfo::default(), None)
        }
        .map_err(|error| {
            GalError::backend(format!("failed to create test present fence: {error:?}"))
        })?;
        let command_buffer_info =
            [vk::CommandBufferSubmitInfo::default().command_buffer(command_buffer)];
        let submit = vk::SubmitInfo2::default().command_buffer_infos(&command_buffer_info);
        let submit_result = unsafe {
            self.context
                .device
                .queue_submit2(self.context.queue, &[submit], fence)
        };
        if let Err(error) = submit_result {
            unsafe {
                self.context.device.destroy_fence(fence, None);
                self.context
                    .device
                    .free_command_buffers(self.context.command_pool, &[command_buffer]);
            }
            return Err(GalError::backend(format!(
                "test present queue submit failed: {error:?}"
            )));
        }
        let wait_result = unsafe {
            self.context
                .device
                .wait_for_fences(&[fence], true, 1_000_000_000)
        };
        unsafe {
            self.context.device.destroy_fence(fence, None);
            self.context
                .device
                .free_command_buffers(self.context.command_pool, &[command_buffer]);
        }
        wait_result.map_err(|error| {
            GalError::backend(format!("test present fence wait failed: {error:?}"))
        })?;
        if let Some(layout) = self.image_layouts.get_mut(acquired.image_index as usize) {
            *layout = vk::ImageLayout::PRESENT_SRC_KHR;
        }
        Ok(())
    }
}

impl Drop for VulkanSwapchain {
    fn drop(&mut self) {
        self.shutdown();
    }
}

fn create_image_view(
    context: &VulkanContext,
    image: vk::Image,
    format: TextureFormat,
    index: u32,
) -> GalResult<vk::ImageView> {
    let view_info = vk::ImageViewCreateInfo::default()
        .image(image)
        .view_type(vk::ImageViewType::TYPE_2D)
        .format(texture_format(format))
        .subresource_range(vk::ImageSubresourceRange {
            aspect_mask: aspect_for_format(format),
            base_mip_level: 0,
            level_count: 1,
            base_array_layer: 0,
            layer_count: 1,
        });
    let view = unsafe { context.device.create_image_view(&view_info, None) }.map_err(|error| {
        GalError::backend(format!(
            "failed to create Vulkan swapchain image view #{index}: {error:?}"
        ))
    })?;
    context.set_object_name(view, &format!("gal.swapchain.image-view.{index}"));
    Ok(view)
}

fn choose_format(
    formats: &[vk::SurfaceFormatKHR],
    requested: TextureFormat,
) -> (vk::SurfaceFormatKHR, TextureFormat) {
    let wanted = texture_format(requested);
    formats
        .iter()
        .copied()
        .find(|format| format.format == wanted)
        .map(|format| (format, requested))
        .or_else(|| {
            formats.iter().copied().find_map(|format| {
                if format.format == vk::Format::B8G8R8A8_UNORM {
                    Some((format, TextureFormat::Bgra8Unorm))
                } else if format.format == vk::Format::R8G8B8A8_UNORM {
                    Some((format, TextureFormat::Rgba8Unorm))
                } else {
                    None
                }
            })
        })
        .unwrap_or((
            vk::SurfaceFormatKHR {
                format: wanted,
                color_space: vk::ColorSpaceKHR::SRGB_NONLINEAR,
            },
            requested,
        ))
}

fn choose_extent(capabilities: vk::SurfaceCapabilitiesKHR, requested: Extent3d) -> vk::Extent2D {
    if capabilities.current_extent.width != u32::MAX {
        return capabilities.current_extent;
    }
    vk::Extent2D {
        width: requested.width.clamp(
            capabilities.min_image_extent.width,
            capabilities.max_image_extent.width,
        ),
        height: requested.height.clamp(
            capabilities.min_image_extent.height,
            capabilities.max_image_extent.height,
        ),
    }
}

fn choose_present_mode(
    available: &[vk::PresentModeKHR],
    requested: PresentMode,
) -> vk::PresentModeKHR {
    let wanted = match requested {
        PresentMode::Immediate => vk::PresentModeKHR::IMMEDIATE,
        PresentMode::Mailbox => vk::PresentModeKHR::MAILBOX,
        PresentMode::Fifo => vk::PresentModeKHR::FIFO,
    };
    if available.contains(&wanted) {
        wanted
    } else {
        vk::PresentModeKHR::FIFO
    }
}

fn choose_image_count(capabilities: vk::SurfaceCapabilitiesKHR, requested: u32) -> u32 {
    let minimum = capabilities.min_image_count.max(2);
    let desired = minimum.max(requested);
    if capabilities.max_image_count == 0 {
        desired
    } else {
        desired.min(capabilities.max_image_count)
    }
}

fn wait_timeline(context: &VulkanContext, id: SubmissionId) -> GalResult<()> {
    let semaphores = [context.timeline];
    let values = [id.0];
    let wait_info = vk::SemaphoreWaitInfo::default()
        .semaphores(&semaphores)
        .values(&values);
    unsafe { context.device.wait_semaphores(&wait_info, 30_000_000_000) }
        .map_err(|error| GalError::backend(format!("Vulkan present wait failed: {error:?}")))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn swapchain_image_count_respects_surface_limits() {
        let mut capabilities = vk::SurfaceCapabilitiesKHR::default();
        capabilities.min_image_count = 2;
        capabilities.max_image_count = 3;
        assert_eq!(choose_image_count(capabilities, 2), 2);
        assert_eq!(choose_image_count(capabilities, 8), 3);
        capabilities.max_image_count = 0;
        assert_eq!(choose_image_count(capabilities, 5), 5);
    }

    #[test]
    fn swapchain_present_mode_falls_back_to_fifo() {
        assert!(
            choose_present_mode(&[vk::PresentModeKHR::FIFO], PresentMode::Mailbox)
                == vk::PresentModeKHR::FIFO
        );
        assert!(
            choose_present_mode(&[vk::PresentModeKHR::MAILBOX], PresentMode::Mailbox)
                == vk::PresentModeKHR::MAILBOX
        );
    }
}
