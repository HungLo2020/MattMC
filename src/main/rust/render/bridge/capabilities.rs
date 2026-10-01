//! Capability negotiation: feature bits, capability queries and per-resource capability checks.

use crate::render::bridge::*;

pub fn capability_feature_bits(capabilities: BackendCapabilities) -> u64 {
    let features = capabilities.features;
    let mut bits = 0;
    set_feature(&mut bits, FfiFeatureBits::GRAPHICS, features.graphics);
    set_feature(&mut bits, FfiFeatureBits::COMPUTE, features.compute);
    set_feature(
        &mut bits,
        FfiFeatureBits::DESCRIPTOR_ARRAYS,
        features.descriptor_arrays,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::OPTIONAL_BINDINGS,
        features.optional_bindings,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::DYNAMIC_BUFFER_OFFSETS,
        features.dynamic_buffer_offsets,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::UNIFORM_BUFFERS,
        features.uniform_buffers,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::STORAGE_BUFFERS,
        features.storage_buffers,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::STORAGE_TEXTURES,
        features.storage_textures,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::INDIRECT_DRAW,
        features.indirect_draw,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::INDIRECT_DISPATCH,
        features.indirect_dispatch,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::MULTIPLE_COLOR_ATTACHMENTS,
        features.multiple_color_attachments,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::DEPTH_ONLY_PASS,
        features.depth_only_pass,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::BLENDED_PASS,
        features.blended_pass,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::TEXTURE_SUBRESOURCE_COPIES,
        features.texture_subresource_copies,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::TEXTURE_MIP_LEVELS,
        features.texture_mip_levels,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::TEXTURE_ARRAY_LAYERS,
        features.texture_array_layers,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::HOST_BUFFER_ACCESS,
        features.host_buffer_access,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::PRESENTATION,
        features.presentation,
    );
    set_feature(
        &mut bits,
        FfiFeatureBits::RENDERDOC_CAPTURE,
        features.renderdoc_capture,
    );
    set_feature(&mut bits, FfiFeatureBits::TRACY_ZONES, features.tracy_zones);
    bits
}

pub unsafe fn answer_capability_query(
    request: *const FfiCapabilityQueryRequest,
    capabilities: BackendCapabilities,
) -> GalResult<FfiCapabilityResult> {
    let request = read_struct(request, "capability query")?;
    validate_header::<FfiCapabilityQueryRequest>(request.header)?;
    reject_unknown_feature_bits(request.requested_feature_bits)?;
    let supported = capability_feature_bits(capabilities);
    if request.requested_feature_bits & !supported != 0 {
        return Err(GalError::unsupported_feature(format!(
            "requested unsupported feature bits 0x{:x}",
            request.requested_feature_bits & !supported
        )));
    }
    Ok(FfiCapabilityResult {
        header: FfiHeader {
            version: FFI_ABI_VERSION,
            byte_size: size_of::<FfiCapabilityResult>() as u32,
        },
        status: StatusCode::Ok as i32,
        error_domain: 0,
        supported_feature_bits: supported,
        negotiated_feature_bits: request.requested_feature_bits,
        limits: FfiBackendLimits::from(capabilities.limits),
        initial_presentation_supported: u32::from(capabilities.features.presentation),
    })
}

pub(crate) fn set_feature(bits: &mut u64, feature: u64, enabled: bool) {
    if enabled {
        *bits |= feature;
    }
}

pub(crate) fn feature_from_bit(bit: u64) -> Option<BackendFeature> {
    match bit {
        FfiFeatureBits::GRAPHICS => Some(BackendFeature::Graphics),
        FfiFeatureBits::COMPUTE => Some(BackendFeature::Compute),
        FfiFeatureBits::DESCRIPTOR_ARRAYS => Some(BackendFeature::DescriptorArrays),
        FfiFeatureBits::OPTIONAL_BINDINGS => Some(BackendFeature::OptionalBindings),
        FfiFeatureBits::DYNAMIC_BUFFER_OFFSETS => Some(BackendFeature::DynamicBufferOffsets),
        FfiFeatureBits::UNIFORM_BUFFERS => Some(BackendFeature::UniformBuffers),
        FfiFeatureBits::STORAGE_BUFFERS => Some(BackendFeature::StorageBuffers),
        FfiFeatureBits::STORAGE_TEXTURES => Some(BackendFeature::StorageTextures),
        FfiFeatureBits::INDIRECT_DRAW => Some(BackendFeature::IndirectDraw),
        FfiFeatureBits::INDIRECT_DISPATCH => Some(BackendFeature::IndirectDispatch),
        FfiFeatureBits::MULTIPLE_COLOR_ATTACHMENTS => {
            Some(BackendFeature::MultipleColorAttachments)
        }
        FfiFeatureBits::DEPTH_ONLY_PASS => Some(BackendFeature::DepthOnlyPass),
        FfiFeatureBits::BLENDED_PASS => Some(BackendFeature::BlendedPass),
        FfiFeatureBits::TEXTURE_SUBRESOURCE_COPIES => {
            Some(BackendFeature::TextureSubresourceCopies)
        }
        FfiFeatureBits::TEXTURE_MIP_LEVELS => Some(BackendFeature::TextureMipLevels),
        FfiFeatureBits::TEXTURE_ARRAY_LAYERS => Some(BackendFeature::TextureArrayLayers),
        FfiFeatureBits::HOST_BUFFER_ACCESS => Some(BackendFeature::HostBufferAccess),
        FfiFeatureBits::PRESENTATION => Some(BackendFeature::Presentation),
        FfiFeatureBits::RENDERDOC_CAPTURE => Some(BackendFeature::RenderDocCapture),
        FfiFeatureBits::TRACY_ZONES => Some(BackendFeature::TracyZones),
        _ => None,
    }
}

pub(crate) fn reject_unknown_feature_bits(bits: u64) -> GalResult<()> {
    if bits & !FfiFeatureBits::ALL_KNOWN != 0 {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!(
                "unknown negotiated feature bits 0x{:x}",
                bits & !FfiFeatureBits::ALL_KNOWN
            ),
        ));
    }
    Ok(())
}

pub(crate) fn require_negotiated_features(
    bits: u64,
    capabilities: BackendCapabilities,
) -> GalResult<()> {
    for index in 0..64 {
        let bit = 1_u64 << index;
        if bits & bit == 0 {
            continue;
        }
        let Some(feature) = feature_from_bit(bit) else {
            continue;
        };
        if !capabilities.supports(feature) {
            return Err(GalError::unsupported_feature(format!(
                "negotiated feature {:?} is unsupported by backend '{}'",
                feature, capabilities.name
            )));
        }
    }
    Ok(())
}

pub(crate) fn check_buffer_capabilities(
    desc: &BufferDesc,
    capabilities: BackendCapabilities,
) -> GalResult<()> {
    if desc.size > capabilities.limits.max_buffer_size {
        return Err(GalError::unsupported_feature(
            "buffer size exceeds backend limit",
        ));
    }
    if desc.usages.contains(&BufferUsage::Storage) {
        require_feature(
            capabilities,
            BackendFeature::StorageBuffers,
            "storage buffer",
        )?;
    }
    if desc.usages.contains(&BufferUsage::Indirect) {
        require_any_feature(
            capabilities,
            &[
                BackendFeature::IndirectDraw,
                BackendFeature::IndirectDispatch,
            ],
            "indirect buffer",
        )?;
    }
    if desc.usages.contains(&BufferUsage::HostRead) || desc.usages.contains(&BufferUsage::HostWrite)
    {
        require_feature(
            capabilities,
            BackendFeature::HostBufferAccess,
            "host buffer access",
        )?;
    }
    Ok(())
}

pub(crate) fn check_texture_capabilities(
    desc: &TextureDesc,
    capabilities: BackendCapabilities,
) -> GalResult<()> {
    // Stable Java resource batches intentionally remain D2-only. D3 is a
    // private backend/GAL prerequisite until a versioned semantic volume
    // transport owns generation, updates, and lifetime end to end.
    if desc.dimension == TextureDimension::D3 {
        return Err(GalError::unsupported_feature(
            "D3 texture resources are internal-only; the stable FFI has no semantic volume transport",
        ));
    }
    if desc.extent.width > capabilities.limits.max_texture_extent_2d
        || desc.extent.height > capabilities.limits.max_texture_extent_2d
    {
        return Err(GalError::unsupported_feature(
            "texture extent exceeds backend limit",
        ));
    }
    if desc.mip_levels > capabilities.limits.max_texture_mip_levels {
        return Err(GalError::unsupported_feature(
            "texture mip count exceeds backend limit",
        ));
    }
    if desc.array_layers > capabilities.limits.max_texture_array_layers {
        return Err(GalError::unsupported_feature(
            "texture layer count exceeds backend limit",
        ));
    }
    if desc.mip_levels > 1 {
        require_feature(
            capabilities,
            BackendFeature::TextureMipLevels,
            "texture mip levels",
        )?;
    }
    if desc.array_layers > 1 {
        require_feature(
            capabilities,
            BackendFeature::TextureArrayLayers,
            "texture array layers",
        )?;
    }
    if desc.usages.contains(&TextureUsage::Storage) {
        require_feature(
            capabilities,
            BackendFeature::StorageTextures,
            "storage texture",
        )?;
    }
    if desc.usages.contains(&TextureUsage::Present) {
        require_feature(
            capabilities,
            BackendFeature::Presentation,
            "presentation texture",
        )?;
        return Err(GalError::unsupported_feature(
            "presentation texture creation is outside the batch ABI; use ABI v2 frame targets",
        ));
    }
    Ok(())
}

pub(crate) fn check_graphics_pipeline_capabilities(
    desc: &GraphicsPipelineDesc,
    capabilities: BackendCapabilities,
) -> GalResult<()> {
    require_feature(capabilities, BackendFeature::Graphics, "graphics pipeline")?;
    check_attachment_count(
        desc.color_formats.len(),
        desc.depth_format.is_some(),
        capabilities,
    )?;
    if desc.blend != BlendMode::Disabled {
        require_feature(capabilities, BackendFeature::BlendedPass, "blended pass")?;
    }
    Ok(())
}

pub(crate) fn check_attachment_count(
    color_count: usize,
    has_depth: bool,
    capabilities: BackendCapabilities,
) -> GalResult<()> {
    if color_count > capabilities.limits.max_color_attachments as usize {
        return Err(GalError::unsupported_feature(
            "color attachment count exceeds backend limit",
        ));
    }
    if color_count > 1 {
        require_feature(
            capabilities,
            BackendFeature::MultipleColorAttachments,
            "multiple color attachments",
        )?;
    }
    if color_count == 0 && has_depth {
        require_feature(
            capabilities,
            BackendFeature::DepthOnlyPass,
            "depth-only pass",
        )?;
    }
    Ok(())
}

pub(crate) fn require_feature(
    capabilities: BackendCapabilities,
    feature: BackendFeature,
    label: &str,
) -> GalResult<()> {
    if capabilities.supports(feature) {
        Ok(())
    } else {
        Err(GalError::unsupported_feature(format!(
            "backend '{}' does not support {label}",
            capabilities.name
        )))
    }
}

pub(crate) fn require_any_feature(
    capabilities: BackendCapabilities,
    features: &[BackendFeature],
    label: &str,
) -> GalResult<()> {
    if features
        .iter()
        .any(|feature| capabilities.supports(*feature))
    {
        Ok(())
    } else {
        Err(GalError::unsupported_feature(format!(
            "backend '{}' does not support {label}",
            capabilities.name
        )))
    }
}
