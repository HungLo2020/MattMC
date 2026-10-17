//! Creating buffers, textures, texture views and samplers, with descriptor validation.

use super::*;

/// What a texture view addresses, as returned by
/// `VulkanicGal::texture_view_info`.
#[derive(Clone, Debug)]
pub struct TextureViewInfo {
    /// The texture the view belongs to.
    pub texture: Handle,
    /// The view's format (always the texture's format).
    pub format: TextureFormat,
    /// The texture's full extent at mip 0.
    pub extent: Extent3d,
    /// The mips and layers the view covers.
    pub range: TextureSubresourceRange,
    /// The usages the texture was created with.
    pub usages: Vec<TextureUsage>,
}

impl VulkanicGal {
    /// Creates a buffer. The size must be non-zero and within the backend
    /// limit; storage usage requires storage-buffer support.
    pub fn create_buffer(&mut self, desc: BufferDesc) -> GalResult<Handle> {
        if desc.size == 0 || desc.usages.is_empty() {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "buffer size and usages must be non-empty",
            ));
        }
        let capabilities = self.capabilities();
        if desc.size > capabilities.limits.max_buffer_size {
            return self.unsupported(format!(
                "buffer '{}' size {} exceeds backend '{}' limit {}",
                desc.label, desc.size, capabilities.name, capabilities.limits.max_buffer_size
            ));
        }
        if desc.usages.contains(&BufferUsage::Storage)
            && !capabilities.supports(BackendFeature::StorageBuffers)
        {
            return self.unsupported(format!(
                "backend '{}' does not support storage buffers",
                capabilities.name
            ));
        }
        let handle = self.buffers.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::Buffer(&desc))?;
        self.metrics.resource_creates += 1;
        self.buffers.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    /// Creates a 2D or 3D texture. Extent, mips and layers are checked
    /// against backend limits and the format against the requested usages;
    /// 3D textures support sampled, storage and transfer use only.
    pub fn create_texture(&mut self, desc: TextureDesc) -> GalResult<Handle> {
        if desc.extent.width == 0
            || desc.extent.height == 0
            || desc.extent.depth == 0
            || desc.mip_levels == 0
            || desc.array_layers == 0
            || desc.usages.is_empty()
        {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "texture extent, levels, layers, and usages must be non-empty",
            ));
        }
        let capabilities = self.capabilities();
        match desc.dimension {
            TextureDimension::D2 => {
                if desc.extent.depth != 1 {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "D2 textures require depth 1",
                    ));
                }
            }
            TextureDimension::D3 => {
                if desc.array_layers != 1 {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "D3 textures cannot have array layers",
                    ));
                }
                if !capabilities.supports(BackendFeature::Texture3d) {
                    return self.unsupported(format!(
                        "backend '{}' does not support D3 textures",
                        capabilities.name
                    ));
                }
                if desc.extent.width > capabilities.limits.max_texture_extent_3d
                    || desc.extent.height > capabilities.limits.max_texture_extent_3d
                    || desc.extent.depth > capabilities.limits.max_texture_extent_3d
                {
                    return self.unsupported(format!(
                        "texture '{}' extent {}x{}x{} exceeds backend '{}' 3D extent limit {}",
                        desc.label,
                        desc.extent.width,
                        desc.extent.height,
                        desc.extent.depth,
                        capabilities.name,
                        capabilities.limits.max_texture_extent_3d
                    ));
                }
            }
            _ => return self.unsupported("only D2 and D3 texture dimensions are modeled"),
        }
        let max_mips_for_extent = max_texture_mip_levels(desc.extent);
        if desc.mip_levels > max_mips_for_extent {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                format!(
                    "texture '{}' requests {} mips for {}x{}x{} extent (maximum {})",
                    desc.label,
                    desc.mip_levels,
                    desc.extent.width,
                    desc.extent.height,
                    desc.extent.depth,
                    max_mips_for_extent
                ),
            ));
        }
        if desc.format == TextureFormat::R8Uint
            && (desc.usages.contains(&TextureUsage::ColorAttachment)
                || desc.usages.contains(&TextureUsage::DepthStencilAttachment)
                || desc.usages.contains(&TextureUsage::Present))
        {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "R8Uint is a sampled/storage/transfer format, not a render target format",
            ));
        }
        if is_depth_format(desc.format)
            && (desc.usages.contains(&TextureUsage::ColorAttachment)
                || desc.usages.contains(&TextureUsage::Storage)
                || desc.usages.contains(&TextureUsage::Present))
        {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "depth formats cannot be used as color, storage, or present textures",
            ));
        }
        if desc.dimension == TextureDimension::D2
            && (desc.extent.width > capabilities.limits.max_texture_extent_2d
                || desc.extent.height > capabilities.limits.max_texture_extent_2d)
        {
            return self.unsupported(format!(
                "texture '{}' extent {}x{} exceeds backend '{}' 2D extent limit {}",
                desc.label,
                desc.extent.width,
                desc.extent.height,
                capabilities.name,
                capabilities.limits.max_texture_extent_2d
            ));
        }
        if desc.mip_levels > capabilities.limits.max_texture_mip_levels {
            return self.unsupported(format!(
                "texture '{}' mip count {} exceeds backend '{}' limit {}",
                desc.label,
                desc.mip_levels,
                capabilities.name,
                capabilities.limits.max_texture_mip_levels
            ));
        }
        if desc.mip_levels > 1 && !capabilities.supports(BackendFeature::TextureMipLevels) {
            return self.unsupported(format!(
                "backend '{}' does not support multi-mip textures",
                capabilities.name
            ));
        }
        if desc.array_layers > capabilities.limits.max_texture_array_layers {
            return self.unsupported(format!(
                "texture '{}' layer count {} exceeds backend '{}' limit {}",
                desc.label,
                desc.array_layers,
                capabilities.name,
                capabilities.limits.max_texture_array_layers
            ));
        }
        if desc.array_layers > 1 && !capabilities.supports(BackendFeature::TextureArrayLayers) {
            return self.unsupported(format!(
                "backend '{}' does not support texture arrays",
                capabilities.name
            ));
        }
        if desc.usages.contains(&TextureUsage::Storage)
            && !capabilities.supports(BackendFeature::StorageTextures)
        {
            return self.unsupported(format!(
                "backend '{}' does not support storage textures",
                capabilities.name
            ));
        }
        if desc.usages.contains(&TextureUsage::Present)
            && !capabilities.supports(BackendFeature::Presentation)
        {
            return self.unsupported(format!(
                "backend '{}' does not support presentation textures in the isolated path",
                capabilities.name
            ));
        }
        if desc.dimension == TextureDimension::D3
            && (desc.usages.contains(&TextureUsage::ColorAttachment)
                || desc.usages.contains(&TextureUsage::DepthStencilAttachment)
                || desc.usages.contains(&TextureUsage::Present))
        {
            return self.unsupported(
                "D3 textures are modeled for sampled, storage, and transfer use, not render targets",
            );
        }
        if desc.dimension == TextureDimension::D3 {
            for usage in desc.usages.iter().copied() {
                if !capabilities.supports_texture_3d_usage(desc.format, usage) {
                    return self.unsupported(format!(
                        "backend '{}' does not support D3 texture format {:?} with usage {:?}",
                        capabilities.name, desc.format, usage
                    ));
                }
            }
        }
        let handle = self.textures.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::Texture(&desc))?;
        self.metrics.resource_creates += 1;
        self.textures.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    /// Creates a view of a texture's mip and layer range. Views keep the
    /// texture's format; 3D views address the single 3D layer.
    pub fn create_texture_view(&mut self, desc: TextureViewDesc) -> GalResult<Handle> {
        let (texture_dimension, texture_mip_levels, texture_array_layers, texture_format) = {
            let texture = self.textures.get(desc.texture)?;
            (
                texture.desc.dimension,
                texture.desc.mip_levels,
                texture.desc.array_layers,
                texture.desc.format,
            )
        };
        let Some(mip_end) = desc.base_mip.checked_add(desc.mip_count) else {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "texture view mip range overflows",
            ));
        };
        let Some(layer_end) = desc.base_layer.checked_add(desc.layer_count) else {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "texture view layer range overflows",
            ));
        };
        if desc.mip_count == 0
            || desc.layer_count == 0
            || mip_end > texture_mip_levels
            || layer_end > texture_array_layers
        {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "texture view range is outside the texture",
            ));
        }
        if desc.format != texture_format {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "texture view format reinterpretation is not modeled",
            ));
        }
        if texture_dimension == TextureDimension::D3
            && (desc.base_layer != 0 || desc.layer_count != 1)
        {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "D3 texture views must address the single 3D image layer",
            ));
        }
        let handle = self.texture_views.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::TextureView(&desc))?;
        self.add_dependency(desc.texture, handle);
        self.metrics.resource_creates += 1;
        self.texture_views.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    /// Creates a sampler.
    pub fn create_sampler(&mut self, desc: SamplerDesc) -> GalResult<Handle> {
        let handle = self.samplers.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::Sampler(&desc))?;
        self.metrics.resource_creates += 1;
        self.samplers.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    /// Creates a logical texture-view/sampler pairing. This is separate from
    /// either child resource so a source shader can require one combined
    /// sampling binding without exposing backend descriptor mechanics.
    pub fn create_combined_texture_sampler(
        &mut self,
        desc: CombinedTextureSamplerDesc,
    ) -> GalResult<Handle> {
        let view = self.texture_view_info(desc.texture_view)?;
        let sampler = self.samplers.get(desc.sampler)?;
        if trace_label_matches("MATTMC_GAL_TRACE_SAMPLER_LABEL", &desc.label) {
            crate::core::console::stderr(format_args!(
                "[MattMC sampler-trace] combined label={} view=0x{:016x} texture=0x{:016x} sampler=0x{:016x}",
                desc.label,
                desc.texture_view.raw(),
                view.texture.raw(),
                desc.sampler.raw(),
            ));
        }
        if !view.usages.contains(&TextureUsage::Sampled) {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "combined texture sampler requires sampled texture usage",
            ));
        }
        if view.format == TextureFormat::R8Uint
            && (sampler.desc.min_filter != SamplerFilter::Nearest
                || sampler.desc.mag_filter != SamplerFilter::Nearest
                || sampler.desc.mip_filter != SamplerFilter::Nearest)
        {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "integer combined texture samplers require nearest min, mag, and mip filters",
            ));
        }
        if sampler.desc.comparison.is_some() && !is_depth_format(view.format) {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "comparison combined texture samplers require a depth texture view",
            ));
        }
        let handle = self.combined_texture_samplers.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::CombinedTextureSampler(&desc))?;
        self.add_dependency(desc.texture_view, handle);
        self.add_dependency(desc.sampler, handle);
        self.metrics.resource_creates += 1;
        self.combined_texture_samplers.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    /// The texture, format, extent, range and usages a view addresses.
    pub fn texture_view_info(&self, view: Handle) -> GalResult<TextureViewInfo> {
        let view_record = self.texture_views.get(view)?;
        let texture_record = self.textures.get(view_record.desc.texture)?;
        Ok(TextureViewInfo {
            texture: view_record.desc.texture,
            format: view_record.desc.format,
            extent: texture_record.desc.extent,
            range: TextureSubresourceRange {
                base_mip: view_record.desc.base_mip,
                mip_count: view_record.desc.mip_count,
                base_layer: view_record.desc.base_layer,
                layer_count: view_record.desc.layer_count,
            },
            usages: texture_record.desc.usages.clone(),
        })
    }
}

pub(super) fn is_depth_stencil_format(format: TextureFormat) -> bool {
    matches!(
        format,
        TextureFormat::Depth24Stencil8 | TextureFormat::Depth32Float
    )
}

pub(super) fn texture_mip_extent(base: Extent3d, mip: u32) -> Extent3d {
    Extent3d {
        width: (base.width >> mip).max(1),
        height: (base.height >> mip).max(1),
        depth: (base.depth >> mip).max(1),
    }
}

pub(super) fn max_texture_mip_levels(extent: Extent3d) -> u32 {
    let largest_dimension = extent.width.max(extent.height).max(extent.depth);
    u32::BITS - largest_dimension.leading_zeros()
}

pub(super) fn is_depth_format(format: TextureFormat) -> bool {
    matches!(
        format,
        TextureFormat::Depth24Stencil8 | TextureFormat::Depth32Float
    )
}
