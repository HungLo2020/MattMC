//! Frame targets and pass-target queries, including owned depth and depth-write tracking.

use super::*;

impl VulkanicGal {
    /// Creates a frame target: a pass target bound to an acquired swapchain
    /// image, plus a GAL-owned `Depth32Float` depth attachment.
    pub fn create_frame_target(&mut self, desc: FrameTargetDesc) -> GalResult<Handle> {
        if desc.extent.width == 0 || desc.extent.height == 0 || desc.extent.depth == 0 {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "frame target extent must be non-empty",
            ));
        }
        let capabilities = self.capabilities();
        if !capabilities.supports(BackendFeature::Presentation) {
            return self.unsupported(format!(
                "backend '{}' was not created with presentation support",
                capabilities.name
            ));
        }
        let handle = self.frame_targets.next_handle()?;
        let depth_texture = self.create_texture(TextureDesc {
            label: format!("{}.depth", desc.label),
            dimension: TextureDimension::D2,
            format: TextureFormat::Depth32Float,
            extent: desc.extent,
            mip_levels: 1,
            array_layers: 1,
            usages: vec![
                TextureUsage::DepthStencilAttachment,
                TextureUsage::Sampled,
                TextureUsage::TransferDst,
                // The opt-in whole-frame attachment audit copies the
                // Rust-owned forward depth image once after rendering. Keep
                // this capability on the same owned image so the diagnostic
                // does not introduce a second depth domain or a presenter
                // handoff.
                TextureUsage::TransferSrc,
            ],
        })?;
        let depth_view = match self.create_texture_view(TextureViewDesc {
            label: format!("{}.depth-view", desc.label),
            texture: depth_texture,
            format: TextureFormat::Depth32Float,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        }) {
            Ok(view) => view,
            Err(error) => {
                let _ = self.destroy(depth_texture);
                return Err(error);
            }
        };
        let token = match self
            .backend
            .create(handle, BackendCreateDesc::FrameTarget(&desc))
        {
            Ok(token) => token,
            Err(error) => {
                let _ = self.destroy(depth_view);
                let _ = self.destroy(depth_texture);
                return Err(error);
            }
        };
        self.frame_target_depth
            .insert(handle, (depth_texture, depth_view));
        self.metrics.resource_creates += 1;
        let result = self.frame_targets.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        );
        if let Err(error) = result {
            self.frame_target_depth.remove(&handle);
            let _ = self.destroy(depth_view);
            let _ = self.destroy(depth_texture);
            let _ = self.backend.destroy(handle, HandleKind::FrameTarget, token);
            return Err(error);
        }
        Ok(handle)
    }

    pub(in crate::render::vulkanic) fn frame_target_color_format(
        &self,
        handle: Handle,
    ) -> GalResult<ColorFormat> {
        Ok(self.frame_targets.get(handle)?.desc.color_format)
    }

    /// Returns only the backend-neutral identity and compatibility facts for
    /// an acquired frame target. Frontends may cache pass resources by this
    /// descriptor, but never receive a backend image, view, framebuffer, or
    /// presentation object.
    pub fn frame_target_desc(
        &self,
        handle: Handle,
    ) -> GalResult<FrameTargetDesc> {
        Ok(self.frame_targets.get(handle)?.desc.clone())
    }

    /// Color format of a render or frame target's first color attachment.
    pub fn pass_target_color_format(
        &self,
        handle: Handle,
    ) -> GalResult<ColorFormat> {
        match handle.kind() {
            Some(HandleKind::FrameTarget) => self.frame_target_color_format(handle),
            Some(HandleKind::RenderTarget) => {
                let target = self.render_targets.get(handle)?;
                let Some(view) = target.desc.color_views.first().copied() else {
                    return Err(GalError::resource(
                        StatusCode::InvalidArgument,
                        "render target must have a color attachment for world primitives",
                    ));
                };
                self.texture_view_info(view).map(|info| info.format)
            }
            _ => Err(GalError::resource(
                StatusCode::WrongHandleType,
                "pass target must be a render target or frame target",
            )),
        }
    }

    /// Returns the semantic extent of a render or acquired frame target.
    /// Frontends use this to size private intermediate resources without
    /// reaching into backend framebuffers or native images.
    pub fn pass_target_extent(
        &self,
        handle: Handle,
    ) -> GalResult<Extent3d> {
        match handle.kind() {
            Some(HandleKind::FrameTarget) => Ok(self.frame_targets.get(handle)?.desc.extent),
            Some(HandleKind::RenderTarget) => Ok(self.render_targets.get(handle)?.desc.extent),
            _ => Err(GalError::resource(
                StatusCode::WrongHandleType,
                "pass target must be a render target or frame target",
            )),
        }
    }

    /// Resolves the color attachment texture behind an owned render target.
    /// This is an explicit GAL resource identity, never a backend/native
    /// handle, and is intentionally unavailable for acquired frame targets.
    pub fn pass_target_color_texture(
        &self,
        handle: Handle,
    ) -> GalResult<Handle> {
        match handle.kind() {
            Some(HandleKind::RenderTarget) => {
                let target = self.render_targets.get(handle)?;
                let view = target.desc.color_views.first().copied().ok_or_else(|| {
                    GalError::resource(
                        StatusCode::InvalidArgument,
                        "render target must have a color attachment",
                    )
                })?;
                Ok(self.texture_view_info(view)?.texture)
            }
            Some(HandleKind::FrameTarget) => Err(GalError::unsupported_feature(
                "acquired frame targets are copied through the explicit frame-target operation",
            )),
            _ => Err(GalError::resource(
                StatusCode::WrongHandleType,
                "pass target must be a render target or frame target",
            )),
        }
    }

    /// The color attachment of a pass target: the frame target itself, or
    /// a render target's first color view.
    pub fn pass_target_color_attachment(
        &self,
        handle: Handle,
    ) -> GalResult<Handle> {
        match handle.kind() {
            Some(HandleKind::FrameTarget) => {
                self.frame_targets.get(handle)?;
                Ok(handle)
            }
            Some(HandleKind::RenderTarget) => {
                let target = self.render_targets.get(handle)?;
                target.desc.color_views.first().copied().ok_or_else(|| {
                    GalError::resource(
                        StatusCode::InvalidArgument,
                        "render target must have a color attachment for world primitives",
                    )
                })
            }
            _ => Err(GalError::resource(
                StatusCode::WrongHandleType,
                "pass target must be a render target or frame target",
            )),
        }
    }

    /// The `(texture, view)` depth attachment of a pass target, if it has one
    /// that may be read. A frame target's owned depth is reported only once a
    /// depth write has begun or been committed, never while merely allocated.
    pub fn pass_target_depth_attachment(
        &self,
        handle: Handle,
    ) -> GalResult<Option<(Handle, Handle)>> {
        match handle.kind() {
            Some(HandleKind::FrameTarget) => {
                self.frame_targets.get(handle)?;
                if self.frame_target_depth_populated.contains(&handle)
                    || self.frame_target_depth_pending.contains(&handle)
                {
                    Ok(self.frame_target_depth.get(&handle).copied())
                } else {
                    // Allocation alone must not expose an unpopulated depth
                    // image to post-effects.
                    Ok(None)
                }
            }
            Some(HandleKind::RenderTarget) => {
                let target = self.render_targets.get(handle)?;
                target
                    .desc
                    .depth_stencil_view
                    .map(|view| {
                        self.texture_view_info(view)
                            .map(|info| (info.texture, view))
                    })
                    .transpose()
            }
            _ => Err(GalError::resource(
                StatusCode::WrongHandleType,
                "pass target must be a render target or frame target",
            )),
        }
    }

    /// The `(texture, view)` depth attachment a frame target owns, whether or
    /// not it has been written this frame.
    pub fn frame_target_owned_depth_attachment(
        &self,
        handle: Handle,
    ) -> GalResult<(Handle, Handle)> {
        if handle.kind() != Some(HandleKind::FrameTarget) {
            return Err(GalError::resource(
                StatusCode::WrongHandleType,
                "owned frame depth attachment requires a frame target",
            ));
        }
        self.frame_targets.get(handle)?;
        self.frame_target_depth
            .get(&handle)
            .copied()
            .ok_or_else(|| {
                GalError::resource(
                    StatusCode::StaleHandle,
                    "frame target depth attachment is unavailable",
                )
            })
    }

    /// Marks a frame target's owned depth as being written by work about to
    /// be submitted. Pair with `commit_frame_target_depth_write` once the
    /// submission is accepted, or `rollback_frame_target_depth_write` if not.
    pub fn begin_frame_target_depth_write(
        &mut self,
        handle: Handle,
    ) -> GalResult<()> {
        if handle.kind() != Some(HandleKind::FrameTarget) {
            return Err(GalError::resource(
                StatusCode::WrongHandleType,
                "populated frame depth requires a frame target",
            ));
        }
        self.frame_targets.get(handle)?;
        if !self.frame_target_depth.contains_key(&handle) {
            return Err(GalError::resource(
                StatusCode::StaleHandle,
                "frame target depth attachment is unavailable",
            ));
        }
        self.frame_target_depth_pending.insert(handle);
        Ok(())
    }

    /// Marks a frame target's owned depth as populated after the writing
    /// submission was accepted.
    pub fn commit_frame_target_depth_write(
        &mut self,
        handle: Handle,
    ) -> GalResult<()> {
        self.frame_targets.get(handle)?;
        self.frame_target_depth_pending.remove(&handle);
        self.frame_target_depth_populated.insert(handle);
        Ok(())
    }

    /// Abandons a depth write begun with `begin_frame_target_depth_write`.
    pub fn rollback_frame_target_depth_write(
        &mut self,
        handle: Handle,
    ) {
        self.frame_target_depth_pending.remove(&handle);
    }
}
