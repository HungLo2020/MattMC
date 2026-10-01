//! Resource-layout construction and validation shared by lowered programs.

use super::*;

pub(crate) fn source_storage_access(qualifiers: &str) -> GalResult<AccessFlags> {
    let words = qualifiers.split_whitespace().collect::<Vec<_>>();
    let readonly = words.contains(&"readonly");
    let writeonly = words.contains(&"writeonly");
    if readonly && writeonly {
        return Err(GalError::invalid_argument(
            "terrain source storage image cannot be both readonly and writeonly",
        ));
    }
    Ok(if readonly {
        AccessFlags::READ
    } else if writeonly {
        AccessFlags::WRITE
    } else {
        AccessFlags(AccessFlags::READ.0 | AccessFlags::WRITE.0)
    })
}

pub(crate) fn resource_binding_descriptor(
    binding: TerrainSourceFixedBinding,
    dynamic: bool,
) -> ResourceBindingDesc {
    ResourceBindingDesc {
        binding: binding.binding,
        kind: match binding.kind {
            TerrainSourceBindingKind::StorageBuffer => ResourceBindingKind::StorageBuffer,
            TerrainSourceBindingKind::UniformBuffer => ResourceBindingKind::UniformBuffer,
        },
        stages: PipelineStageFlags::DRAW,
        array_count: 1,
        optional: false,
        dynamic_offset_count: u32::from(dynamic),
    }
}

pub(crate) fn source_pack_resource_layout(
    label: String,
    opaque_resource_bindings: &TerrainSourceOpaqueResourceBindingPlan,
) -> GalResult<ResourceLayoutDesc> {
    let mut bindings = Vec::with_capacity(opaque_resource_bindings.bindings().len());
    for source_binding in opaque_resource_bindings.bindings() {
        let kind = match source_binding.kind() {
            TerrainSourceOpaqueResourceKind::CombinedTextureSampler => {
                ResourceBindingKind::CombinedTextureSampler
            }
            TerrainSourceOpaqueResourceKind::StorageImage => ResourceBindingKind::StorageTexture,
        };
        bindings.push(ResourceBindingDesc {
            binding: source_binding.binding(),
            kind,
            stages: PipelineStageFlags::DRAW,
            array_count: 1,
            optional: false,
            dynamic_offset_count: 0,
        });
    }
    bindings.sort_by_key(|binding| binding.binding);
    validate_unique_layout_bindings(&label, &bindings)?;
    Ok(ResourceLayoutDesc { label, bindings })
}

pub(crate) fn validate_source_scalar_uniform_block(
    scalar_uniforms: Option<TerrainSourceFixedBinding>,
    scalar_uniform_bytes: u32,
    scalar_uniform_fields: &[TerrainSourceUniformField],
    expected_binding: TerrainSourceFixedBinding,
    source_label: &str,
) -> GalResult<()> {
    match (scalar_uniforms, scalar_uniform_fields.is_empty()) {
        (None, true) if scalar_uniform_bytes == 0 => return Ok(()),
        (Some(binding), false) if binding == expected_binding => {}
        (None, false) => {
            return Err(GalError::invalid_argument(format!(
                "{source_label} scalar fields require fixed set 0 binding {}",
                expected_binding.binding
            )));
        }
        (Some(_), true) => {
            return Err(GalError::invalid_argument(format!(
                "{source_label} scalar binding is present without scalar fields"
            )));
        }
        (Some(_), false) => {
            return Err(GalError::invalid_argument(format!(
                "{source_label} scalar block must use fixed set 0 binding {} uniform-buffer ABI",
                expected_binding.binding
            )));
        }
        (None, true) => {
            return Err(GalError::invalid_argument(format!(
                "{source_label} empty scalar block has non-zero byte size"
            )));
        }
    }

    let mut previous_end = 0_u32;
    let mut previous_name = "";
    for field in scalar_uniform_fields {
        if field.name() <= previous_name {
            return Err(GalError::invalid_argument(format!(
                "{source_label} scalar fields must be strictly name-sorted"
            )));
        }
        if field.offset() < previous_end || field.offset() % 4 != 0 {
            return Err(GalError::invalid_argument(format!(
                "{source_label} scalar field '{}' has overlapping or unaligned std140 offset {}",
                field.name(),
                field.offset()
            )));
        }
        let end = field.offset().checked_add(field.size()).ok_or_else(|| {
            GalError::invalid_argument(format!("{source_label} scalar field range overflows u32"))
        })?;
        if field.array_length() == 1 && field.array_stride() != 0 {
            return Err(GalError::invalid_argument(format!(
                "{source_label} scalar field '{}' is not an array but has an array stride",
                field.name()
            )));
        }
        if field.array_length() > 1 && (field.array_stride() == 0 || field.array_stride() % 16 != 0)
        {
            return Err(GalError::invalid_argument(format!(
                "{source_label} scalar array '{}' has invalid std140 stride {}",
                field.name(),
                field.array_stride()
            )));
        }
        previous_end = end;
        previous_name = field.name();
    }
    if scalar_uniform_bytes == 0
        || scalar_uniform_bytes % 16 != 0
        || previous_end > scalar_uniform_bytes
    {
        return Err(GalError::invalid_argument(format!(
            "{source_label} scalar block size is not a valid std140 envelope"
        )));
    }
    Ok(())
}

pub(crate) fn validate_unique_layout_bindings(label: &str, bindings: &[ResourceBindingDesc]) -> GalResult<()> {
    if bindings
        .windows(2)
        .any(|pair| pair[0].binding == pair[1].binding)
    {
        return Err(GalError::invalid_argument(format!(
            "{label} contains duplicate binding numbers"
        )));
    }
    Ok(())
}
