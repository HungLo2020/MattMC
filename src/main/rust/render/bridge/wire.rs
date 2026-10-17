//! Wire values to GAL values: enums, flags, usage bits and handles.

use crate::render::bridge::*;

pub(crate) fn require_any_handle(handle: FfiHandle, label: &str) -> GalResult<Handle> {
    let handle = Handle::from(handle);
    if handle.is_null() || handle.kind().is_none() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} is null or has unknown kind"),
        ));
    }
    Ok(handle)
}

pub(crate) fn require_handle(
    handle: FfiHandle,
    kind: HandleKind,
    label: &str,
) -> GalResult<Handle> {
    let handle = Handle::from(handle);
    if handle.is_null() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} handle is null"),
        ));
    }
    handle.require_kind(kind)?;
    Ok(handle)
}

pub(crate) fn require_handle_any(
    handle: FfiHandle,
    kinds: &[HandleKind],
    label: &str,
) -> GalResult<Handle> {
    let handle = Handle::from(handle);
    if handle.is_null() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} handle is null"),
        ));
    }
    let Some(actual) = handle.kind() else {
        return Err(GalError::ffi(
            StatusCode::WrongHandleType,
            format!("{label} has unknown handle kind"),
        ));
    };
    if !kinds.contains(&actual) {
        return Err(GalError::ffi(
            StatusCode::WrongHandleType,
            format!("{label} has kind {actual:?}, expected one of {kinds:?}"),
        ));
    }
    Ok(handle)
}

pub(crate) fn optional_handle(
    handle: FfiHandle,
    kind: HandleKind,
    label: &str,
) -> GalResult<Option<Handle>> {
    if handle.raw == 0 {
        return Ok(None);
    }
    Ok(Some(require_handle(handle, kind, label)?))
}

pub(crate) fn handle_kind(raw: u32) -> GalResult<HandleKind> {
    HandleKind::from_raw(u8::try_from(raw).unwrap_or(0)).ok_or_else(|| {
        GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown handle kind {raw}"),
        )
    })
}

pub(crate) fn memory_domain(raw: u32) -> GalResult<MemoryDomain> {
    match raw {
        1 => Ok(MemoryDomain::DeviceLocal),
        2 => Ok(MemoryDomain::Upload),
        3 => Ok(MemoryDomain::Readback),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown memory domain {raw}"),
        )),
    }
}

pub(crate) fn texture_dimension(raw: u32) -> GalResult<TextureDimension> {
    match raw {
        1 => Ok(TextureDimension::D1),
        2 => Ok(TextureDimension::D2),
        3 => Ok(TextureDimension::D3),
        4 => Ok(TextureDimension::Cube),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown texture dimension {raw}"),
        )),
    }
}

pub(crate) fn texture_format(raw: u32) -> GalResult<TextureFormat> {
    match raw {
        1 => Ok(TextureFormat::Rgba8Unorm),
        2 => Ok(TextureFormat::Bgra8Unorm),
        3 => Ok(TextureFormat::Rgba16Float),
        4 => Ok(TextureFormat::Depth24Stencil8),
        5 => Ok(TextureFormat::Depth32Float),
        6 => Ok(TextureFormat::R8Uint),
        7 => Ok(TextureFormat::R11fG11fB10f),
        8 => Ok(TextureFormat::R32Float),
        9 => Ok(TextureFormat::Rgb16Float),
        10 => Ok(TextureFormat::R8Unorm),
        11 => Ok(TextureFormat::Rgba8Snorm),
        12 => Ok(TextureFormat::R16Float),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown texture format {raw}"),
        )),
    }
}

pub(crate) fn optional_texture_format(raw: u32) -> GalResult<Option<TextureFormat>> {
    if raw == 0 {
        Ok(None)
    } else {
        texture_format(raw).map(Some)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn texture_format_decodes_append_only_shader_pack_color_formats() {
        assert_eq!(TextureFormat::R8Uint, texture_format(6).unwrap());
        assert_eq!(TextureFormat::R11fG11fB10f, texture_format(7).unwrap());
        assert_eq!(TextureFormat::R32Float, texture_format(8).unwrap());
        assert_eq!(TextureFormat::Rgb16Float, texture_format(9).unwrap());
        assert_eq!(TextureFormat::R8Unorm, texture_format(10).unwrap());
        assert_eq!(TextureFormat::Rgba8Snorm, texture_format(11).unwrap());
        assert_eq!(TextureFormat::R16Float, texture_format(12).unwrap());
        assert!(texture_format(13).is_err());
    }
}

pub(crate) fn present_mode(raw: u32) -> GalResult<PresentMode> {
    match raw {
        1 => Ok(PresentMode::Immediate),
        2 => Ok(PresentMode::Mailbox),
        3 => Ok(PresentMode::Fifo),
        4 => Ok(PresentMode::AutoVsync),
        5 => Ok(PresentMode::AutoNoVsync),
        6 => Ok(PresentMode::FifoRelaxed),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown present mode {raw}"),
        )),
    }
}

pub(crate) fn acquire_status_raw(status: FrameAcquireStatus) -> u32 {
    status as u32
}

pub(crate) fn present_status_raw(status: FramePresentStatus) -> u32 {
    status as u32
}

pub(crate) fn sampler_filter(raw: u32) -> GalResult<SamplerFilter> {
    match raw {
        1 => Ok(SamplerFilter::Nearest),
        2 => Ok(SamplerFilter::Linear),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown sampler filter {raw}"),
        )),
    }
}

pub(crate) fn sampler_address(raw: u32) -> GalResult<SamplerAddressMode> {
    match raw {
        1 => Ok(SamplerAddressMode::ClampToEdge),
        2 => Ok(SamplerAddressMode::Repeat),
        3 => Ok(SamplerAddressMode::MirroredRepeat),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown sampler address mode {raw}"),
        )),
    }
}

pub(crate) fn shader_stage(raw: u32) -> GalResult<ShaderStage> {
    match raw {
        1 => Ok(ShaderStage::Vertex),
        2 => Ok(ShaderStage::Fragment),
        3 => Ok(ShaderStage::Compute),
        4 => Ok(ShaderStage::Geometry),
        5 => Ok(ShaderStage::TessControl),
        6 => Ok(ShaderStage::TessEvaluation),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown shader stage {raw}"),
        )),
    }
}

pub(crate) fn shader_code_format(raw: u32) -> GalResult<ShaderCodeFormat> {
    match raw {
        1 => Ok(ShaderCodeFormat::Spirv),
        2 => Ok(ShaderCodeFormat::BackendPortableIr),
        3 => Ok(ShaderCodeFormat::Glsl),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown shader code format {raw}"),
        )),
    }
}

pub(crate) fn resource_binding_kind(raw: u32) -> GalResult<ResourceBindingKind> {
    match raw {
        1 => Ok(ResourceBindingKind::UniformBuffer),
        2 => Ok(ResourceBindingKind::StorageBuffer),
        3 => Ok(ResourceBindingKind::SampledTexture),
        4 => Ok(ResourceBindingKind::StorageTexture),
        5 => Ok(ResourceBindingKind::Sampler),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown resource binding kind {raw}"),
        )),
    }
}

pub(crate) fn primitive_topology(raw: u32) -> GalResult<PrimitiveTopology> {
    match raw {
        1 => Ok(PrimitiveTopology::Points),
        2 => Ok(PrimitiveTopology::Lines),
        3 => Ok(PrimitiveTopology::Triangles),
        4 => Ok(PrimitiveTopology::TriangleFan),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown primitive topology {raw}"),
        )),
    }
}

pub(crate) fn cull_mode(raw: u32) -> GalResult<CullMode> {
    match raw {
        1 => Ok(CullMode::None),
        2 => Ok(CullMode::Front),
        3 => Ok(CullMode::Back),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown cull mode {raw}"),
        )),
    }
}

pub(crate) fn blend_mode(raw: u32) -> GalResult<BlendMode> {
    match raw {
        1 => Ok(BlendMode::Disabled),
        2 => Ok(BlendMode::Alpha),
        3 => Ok(BlendMode::Additive),
        4 => Ok(BlendMode::Invert),
        5 => Ok(BlendMode::Multiply),
        6 => Ok(BlendMode::Overlay),
        7 => Ok(BlendMode::SrcColorAdditive),
        8 => Ok(BlendMode::InverseSrcColorModulate),
        9 => Ok(BlendMode::Premultiplied),
        10 => Ok(BlendMode::AlphaFirstAttachmentOnly),
        11 => Ok(BlendMode::AlphaPreserveAlpha),
        12 => Ok(BlendMode::DoubleModulate),
        13 => Ok(BlendMode::AlphaSource),
        14 => Ok(BlendMode::DepthMask),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown blend mode {raw}"),
        )),
    }
}

pub(crate) fn compare_op(raw: u32) -> GalResult<CompareOp> {
    match raw {
        1 => Ok(CompareOp::Always),
        2 => Ok(CompareOp::Less),
        3 => Ok(CompareOp::LessOrEqual),
        4 => Ok(CompareOp::Equal),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown compare op {raw}"),
        )),
    }
}

pub(crate) fn optional_compare_op(raw: u32) -> GalResult<Option<CompareOp>> {
    if raw == 0 {
        Ok(None)
    } else {
        compare_op(raw).map(Some)
    }
}

pub(crate) fn load_op(raw: u32) -> GalResult<AttachmentLoadOp> {
    match raw {
        1 => Ok(AttachmentLoadOp::Load),
        2 => Ok(AttachmentLoadOp::Clear),
        3 => Ok(AttachmentLoadOp::DontCare),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown attachment load op {raw}"),
        )),
    }
}

pub(crate) fn store_op(raw: u32) -> GalResult<AttachmentStoreOp> {
    match raw {
        1 => Ok(AttachmentStoreOp::Store),
        2 => Ok(AttachmentStoreOp::DontCare),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown attachment store op {raw}"),
        )),
    }
}

pub(crate) fn texture_usage_state(raw: u32) -> GalResult<TextureUsageState> {
    match raw {
        1 => Ok(TextureUsageState::Undefined),
        2 => Ok(TextureUsageState::ShaderRead),
        3 => Ok(TextureUsageState::ShaderWrite),
        4 => Ok(TextureUsageState::ColorAttachment),
        5 => Ok(TextureUsageState::DepthStencilAttachment),
        6 => Ok(TextureUsageState::TransferSrc),
        7 => Ok(TextureUsageState::TransferDst),
        8 => Ok(TextureUsageState::Present),
        9 => Ok(TextureUsageState::IndexRead),
        10 => Ok(TextureUsageState::ShaderStorageRead),
        11 => Ok(TextureUsageState::IndirectRead),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown texture usage state {raw}"),
        )),
    }
}

pub(crate) fn ffi_index_type(raw: u32) -> GalResult<IndexType> {
    match raw {
        0 | 2 => Ok(IndexType::U32),
        1 => Ok(IndexType::U16),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown index type {raw}"),
        )),
    }
}

pub(crate) fn queue_class(raw: u32) -> GalResult<QueueClass> {
    match raw {
        1 => Ok(QueueClass::Graphics),
        2 => Ok(QueueClass::Compute),
        3 => Ok(QueueClass::Transfer),
        4 => Ok(QueueClass::Present),
        5 => Ok(QueueClass::External),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown queue class {raw}"),
        )),
    }
}

pub(crate) fn stage_flags(bits: u32) -> GalResult<PipelineStageFlags> {
    let known = PipelineStageFlags::DRAW.0
        | PipelineStageFlags::COMPUTE.0
        | PipelineStageFlags::TRANSFER.0
        | PipelineStageFlags::PRESENT.0;
    if bits & !known != 0 {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown pipeline stage bits 0x{:x}", bits & !known),
        ));
    }
    Ok(PipelineStageFlags(bits))
}

pub(crate) fn access_flags(bits: u32) -> GalResult<AccessFlags> {
    let known = AccessFlags::READ.0
        | AccessFlags::WRITE.0
        | AccessFlags::COLOR_ATTACHMENT.0
        | AccessFlags::DEPTH_STENCIL.0
        | AccessFlags::TRANSFER.0;
    if bits & !known != 0 {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown access bits 0x{:x}", bits & !known),
        ));
    }
    Ok(AccessFlags(bits))
}

pub(crate) fn buffer_usage_bits(bits: u64) -> GalResult<Vec<BufferUsage>> {
    let table = [
        (1_u64 << 0, BufferUsage::Vertex),
        (1_u64 << 1, BufferUsage::Index),
        (1_u64 << 2, BufferUsage::Uniform),
        (1_u64 << 3, BufferUsage::Storage),
        (1_u64 << 4, BufferUsage::TransferSrc),
        (1_u64 << 5, BufferUsage::TransferDst),
        (1_u64 << 6, BufferUsage::Indirect),
        (1_u64 << 7, BufferUsage::HostRead),
        (1_u64 << 8, BufferUsage::HostWrite),
    ];
    usage_bits(bits, &table, "buffer usage")
}

pub(crate) fn texture_usage_bits(bits: u64) -> GalResult<Vec<TextureUsage>> {
    let table = [
        (1_u64 << 0, TextureUsage::Sampled),
        (1_u64 << 1, TextureUsage::Storage),
        (1_u64 << 2, TextureUsage::ColorAttachment),
        (1_u64 << 3, TextureUsage::DepthStencilAttachment),
        (1_u64 << 4, TextureUsage::TransferSrc),
        (1_u64 << 5, TextureUsage::TransferDst),
        (1_u64 << 6, TextureUsage::Present),
        (1_u64 << 7, TextureUsage::HostRead),
        (1_u64 << 8, TextureUsage::HostWrite),
    ];
    usage_bits(bits, &table, "texture usage")
}

pub(crate) fn usage_bits<T: Copy>(bits: u64, table: &[(u64, T)], label: &str) -> GalResult<Vec<T>> {
    let known = table.iter().fold(0_u64, |acc, (bit, _)| acc | *bit);
    if bits == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} bits must be non-zero"),
        ));
    }
    if bits & !known != 0 {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown {label} bits 0x{:x}", bits & !known),
        ));
    }
    Ok(table
        .iter()
        .filter_map(|(bit, value)| if bits & *bit != 0 { Some(*value) } else { None })
        .collect())
}
