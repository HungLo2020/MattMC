//! Creating render targets and render passes.

use super::*;

impl VulkanicGal {
    /// Creates a render target from color views and an optional
    /// depth-stencil view of matching extent.
    pub fn create_render_target(&mut self, desc: RenderTargetDesc) -> GalResult<Handle> {
        if desc.color_views.is_empty() && desc.depth_stencil_view.is_none() {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "render target requires at least one attachment",
            ));
        }
        if desc.extent.width == 0 || desc.extent.height == 0 || desc.extent.depth == 0 {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "render target extent must be non-empty",
            ));
        }
        let capabilities = self.capabilities();
        if desc.color_views.len() > capabilities.limits.max_color_attachments as usize {
            return self.unsupported(format!(
                "render target '{}' color attachment count {} exceeds backend '{}' limit {}",
                desc.label,
                desc.color_views.len(),
                capabilities.name,
                capabilities.limits.max_color_attachments
            ));
        }
        if desc.color_views.len() > 1
            && !capabilities.supports(BackendFeature::MultipleColorAttachments)
        {
            return self.unsupported(format!(
                "backend '{}' does not support multiple color attachments",
                capabilities.name
            ));
        }
        if desc.color_views.is_empty() && !capabilities.supports(BackendFeature::DepthOnlyPass) {
            return self.unsupported(format!(
                "backend '{}' does not support depth-only render targets",
                capabilities.name
            ));
        }
        for view in &desc.color_views {
            let info = self.texture_view_info(*view)?;
            if is_depth_stencil_format(info.format) {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "color attachment view uses a depth/stencil format",
                ));
            }
            if !info.usages.contains(&TextureUsage::ColorAttachment) {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "color attachment texture lacks color attachment usage",
                ));
            }
            if info.extent != desc.extent {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "color attachment extent does not match render target",
                ));
            }
        }
        if let Some(view) = desc.depth_stencil_view {
            let info = self.texture_view_info(view)?;
            if !is_depth_stencil_format(info.format) {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "depth attachment view does not use a depth/stencil format",
                ));
            }
            if !info.usages.contains(&TextureUsage::DepthStencilAttachment) {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "depth attachment texture lacks depth/stencil attachment usage",
                ));
            }
            if info.extent != desc.extent {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "depth attachment extent does not match render target",
                ));
            }
        }
        let handle = self.render_targets.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::RenderTarget(&desc))?;
        for view in &desc.color_views {
            self.add_dependency(*view, handle);
        }
        if let Some(view) = desc.depth_stencil_view {
            self.add_dependency(view, handle);
        }
        self.metrics.resource_creates += 1;
        self.render_targets.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    /// Creates a render pass over a render target or frame target, with load
    /// and store operations for its attachments.
    pub fn create_render_pass(&mut self, desc: RenderPassDesc) -> GalResult<Handle> {
        let (target_color_formats, target_depth_format) = {
            match desc.target.kind() {
                Some(HandleKind::RenderTarget) => {
                    let target = self.render_targets.get(desc.target)?;
                    let color_views = target.desc.color_views.clone();
                    let depth_view = target.desc.depth_stencil_view;
                    let color_formats = color_views
                        .iter()
                        .map(|view| self.texture_view_info(*view).map(|info| info.format))
                        .collect::<GalResult<Vec<_>>>()?;
                    let depth_format = depth_view
                        .map(|view| self.texture_view_info(view).map(|info| info.format))
                        .transpose()?;
                    (color_formats, depth_format)
                }
                Some(HandleKind::FrameTarget) => {
                    let target = self.frame_targets.get(desc.target)?;
                    (vec![target.desc.color_format], desc.depth_format)
                }
                _ => {
                    return self.validation_error(GalError::resource(
                        StatusCode::WrongHandleType,
                        "render pass target must be a render target or frame target",
                    ))
                }
            }
        };
        if desc.color_formats != target_color_formats {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "render pass color formats do not match target",
            ));
        }
        // A target may own a depth image while an individual pass deliberately
        // omits it (for example a fullscreen post-process that samples the
        // completed depth texture).  Dynamic rendering permits this subset;
        // requiring an attachment merely because the target has one would
        // force illegal sampled/attachment feedback on Vulkan.
        if desc.depth_format != target_depth_format
            && !(desc.depth_format.is_none() && target_depth_format.is_some())
        {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "render pass depth format does not match target",
            ));
        }
        let handle = self.render_passes.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::RenderPass(&desc))?;
        self.tag_gpu_profile(handle, GpuProfiledObject::RenderPass, &desc.label);
        self.add_dependency(desc.target, handle);
        self.metrics.resource_creates += 1;
        self.render_passes.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    pub(super) fn render_pass_desc(&self, pass: Handle) -> GalResult<&RenderPassDesc> {
        self.render_passes.get(pass).map(|record| &record.desc)
    }
}
