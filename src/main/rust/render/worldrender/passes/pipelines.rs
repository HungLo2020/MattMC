//! Mesh pipeline keys, raster state and creation of pipelines and resource sets.

use crate::render::worldrender::*;

pub(in crate::render::worldrender) fn mesh_vertex_abi_for_builtin(key: MeshResourceKey) -> MeshVertexAbi {
    if !key.g_buffer
        && key.stratum == WORLD_STRATUM_TERRAIN
        && matches!(
            key.material_mode,
            WORLD_MATERIAL_MODE_OPAQUE
                | WORLD_MATERIAL_MODE_CUTOUT
                | WORLD_MATERIAL_MODE_TRANSLUCENT
        )
        && key.texture_id == WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS
        && !key.standard_item_foil
        && key.view_layering.is_none()
        && key.decal_vertex_count == 0
    {
        MeshVertexAbi::DirectTerrain32
    } else {
        MeshVertexAbi::Rich80
    }
}

pub(in crate::render::worldrender) fn mesh_pipeline_key(key: MeshResourceKey) -> GalResult<MeshPipelineResourceKey> {
    let vertex_abi = mesh_vertex_abi_for_builtin(key);
    static SPECIALIZATION_DISABLED: std::sync::OnceLock<bool> = std::sync::OnceLock::new();
    let static_terrain_specialization = !key.texture_animated
        && !*SPECIALIZATION_DISABLED.get_or_init(|| {
            matches!(
                std::env::var("MATTMC_RUST_DISABLE_STATIC_TERRAIN_SPECIALIZATION").as_deref(),
                Ok("1") | Ok("true") | Ok("yes")
            )
        });
    let shader_program_identity = if vertex_abi == MeshVertexAbi::DirectTerrain32 {
        ProgramIdentity::new(match (key.material_mode, static_terrain_specialization) {
            (WORLD_MATERIAL_MODE_OPAQUE, true) => STATIC_COMPACT_DIRECT_TERRAIN_OPAQUE_PROGRAM_ID,
            (WORLD_MATERIAL_MODE_CUTOUT, true) => STATIC_COMPACT_DIRECT_TERRAIN_CUTOUT_PROGRAM_ID,
            (WORLD_MATERIAL_MODE_TRANSLUCENT, true) => {
                STATIC_COMPACT_DIRECT_TERRAIN_TRANSLUCENT_PROGRAM_ID
            }
            (WORLD_MATERIAL_MODE_OPAQUE, false) => COMPACT_DIRECT_TERRAIN_OPAQUE_PROGRAM_ID,
            (WORLD_MATERIAL_MODE_CUTOUT, false) => COMPACT_DIRECT_TERRAIN_CUTOUT_PROGRAM_ID,
            (WORLD_MATERIAL_MODE_TRANSLUCENT, false) => {
                COMPACT_DIRECT_TERRAIN_TRANSLUCENT_PROGRAM_ID
            }
            _ => unreachable!("compact terrain predicate admits only opaque/cutout/translucent"),
        })
    } else if key.standard_item_foil {
        if key.material_mode != WORLD_MATERIAL_MODE_GLINT {
            return Err(GalError::invalid_argument(
                "standard foil binding requires glint material",
            ));
        }
        let program = if key.decal_vertex_count > 0 {
            minimal_direct_world_decal_foil_program()
        } else {
            minimal_direct_standard_item_foil_program()
        };
        program.identity
    } else {
        terrain_program_for_mode(key.material_mode, key.g_buffer)?.identity
    };
    Ok(MeshPipelineResourceKey {
        vertex_abi,
        raster_y_direction: key.raster_y_direction,
        g_buffer: key.g_buffer,
        material_mode: key.material_mode,
        winding: key.winding,
        depth_policy: key.depth_policy,
        cull_policy: key.cull_policy,
        color_format: key.color_format,
        shader_program_identity,
        shader_resource_layout: None,
    })
}

pub(in crate::render::worldrender) fn source_mesh_resource_key(
    mesh: MeshResourceKey,
    candidate: &TerrainSourceProgramCandidate,
) -> SourceMeshResourceKey {
    SourceMeshResourceKey {
        mesh,
        shader_program_identity: candidate.program.identity.clone(),
        shader_resource_layout: candidate.binding.map(|binding| binding.resource_layout),
        shader_resource_generation: candidate
            .binding
            .map(|binding| binding.resource_generation)
            .unwrap_or(0),
    }
}

pub(in crate::render::worldrender) fn mesh_pipeline_layouts(
    mesh_material_layout: Handle,
    shader_resource_layout: Option<Handle>,
) -> GalResult<Vec<Handle>> {
    let mut layouts = vec![mesh_material_layout];
    if let Some(shader_layout) = shader_resource_layout {
        if shader_layout == mesh_material_layout {
            return Err(GalError::invalid_argument(
                "shader-pack resource layout must be distinct from the mesh/material layout",
            ));
        }
        layouts.push(shader_layout);
    }
    Ok(layouts)
}

pub(in crate::render::worldrender) fn depth_compare_for_policy(depth_policy: u32) -> GalResult<Option<CompareOp>> {
    match depth_policy {
        WORLD_DEPTH_POLICY_DISABLED => Ok(None),
        WORLD_DEPTH_POLICY_TEST_WRITE | WORLD_DEPTH_POLICY_TEST_NO_WRITE => {
            Ok(Some(CompareOp::LessOrEqual))
        }
        WORLD_DEPTH_POLICY_TEST_EQUAL_WRITE => Ok(Some(CompareOp::Equal)),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world mesh depth policy {depth_policy}"),
        )),
    }
}

pub(in crate::render::worldrender) fn effective_cull_mode_for_winding(policy: u32, winding: u32) -> GalResult<CullMode> {
    match (policy, winding) {
        (WORLD_CULL_NONE, WORLD_WINDING_CCW | WORLD_WINDING_CW) => Ok(CullMode::None),
        (WORLD_CULL_BACK, WORLD_WINDING_CCW) => Ok(CullMode::Back),
        (WORLD_CULL_FRONT, WORLD_WINDING_CCW) => Ok(CullMode::Front),
        (WORLD_CULL_BACK, WORLD_WINDING_CW) => Ok(CullMode::Front),
        (WORLD_CULL_FRONT, WORLD_WINDING_CW) => Ok(CullMode::Back),
        (_, WORLD_WINDING_CCW | WORLD_WINDING_CW) => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world mesh cull policy {policy}"),
        )),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world mesh winding {winding}"),
        )),
    }
}

/// Material quads use one canonical vertex payload.  The explicit winding
/// declaration is realized by the immutable index buffer, matching Frozen's
/// cloud shader vertex selection without smuggling a per-face branch into the
/// Rust shader.  Raster culling remains the copied pipeline policy.
pub(in crate::render::worldrender) fn material_quad_indices_for_winding(winding: u32) -> GalResult<[u32; 6]> {
    match winding {
        WORLD_WINDING_CCW => Ok([0, 1, 2, 2, 3, 0]),
        // Frozen's nearby inside-cloud faces select `3 - quadVertex` for the
        // ordinary sequential QUADS index stream.  This is that exact indexed
        // triangle expansion: [3, 2, 1, 1, 0, 3].
        WORLD_WINDING_CW => Ok([3, 2, 1, 1, 0, 3]),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world material winding {winding}"),
        )),
    }
}

/// One capture process may opt into a bounded source-raster isolation probe.
/// The setting is deliberately private to the source-derived diagnostic route;
/// normal gameplay never reads it and cannot inherit relaxed raster state.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) enum SelectedSourceRasterProbe {
    None,
    NoCull,
    DepthDisabled,
    BlendDisabled,
    InvertFrontFace,
}

pub(in crate::render::worldrender) fn selected_source_raster_probe() -> GalResult<SelectedSourceRasterProbe> {
    selected_source_raster_probe_from(
        std::env::var("MATTMC_RUST_SELECTED_SOURCE_RASTER_PROBE")
            .ok()
            .as_deref(),
    )
}

pub(in crate::render::worldrender) fn selected_source_raster_probe_from(value: Option<&str>) -> GalResult<SelectedSourceRasterProbe> {
    match value.map(str::trim) {
        None | Some("") => Ok(SelectedSourceRasterProbe::None),
        Some("no-cull") => Ok(SelectedSourceRasterProbe::NoCull),
        Some("depth-disabled") => Ok(SelectedSourceRasterProbe::DepthDisabled),
        Some("blend-disabled") => Ok(SelectedSourceRasterProbe::BlendDisabled),
        Some("invert-front-face") => Ok(SelectedSourceRasterProbe::InvertFrontFace),
        Some(other) => Err(GalError::invalid_argument(format!(
            "unknown selected-source raster probe '{other}'; expected no-cull, depth-disabled, blend-disabled, or invert-front-face"
        ))),
    }
}

/// Produces the generic material raster state from explicit semantic policy.
/// A probe is process-local capture instrumentation; absent it, this is the
/// production state for every material, including clouds.
pub(in crate::render::worldrender) fn generic_material_raster_state(
    source_program: u32,
    material_mode: u32,
    depth_policy: u32,
    cull_policy: u32,
) -> GalResult<(CullMode, FrontFace, BlendMode, Option<CompareOp>)> {
    // A capture probe is deliberately limited to the cloud family under
    // investigation.  Other generic material producers share this backend
    // helper, and letting their pixels respond would make a cloud experiment
    // inconclusive rather than proving the selected cloud draw's state.
    let probe = if source_program == WORLD_MATERIAL_SOURCE_CLOUDS {
        selected_source_raster_probe()?
    } else {
        SelectedSourceRasterProbe::None
    };
    generic_material_raster_state_for(probe, material_mode, depth_policy, cull_policy)
}

pub(in crate::render::worldrender) fn generic_material_raster_state_for(
    probe: SelectedSourceRasterProbe,
    material_mode: u32,
    depth_policy: u32,
    cull_policy: u32,
) -> GalResult<(CullMode, FrontFace, BlendMode, Option<CompareOp>)> {
    let mut cull_mode = cull_mode_from_policy(cull_policy)?;
    let mut front_face = FrontFace::CounterClockwise;
    let mut blend = if material_mode == WORLD_MATERIAL_MODE_GLINT {
        BlendMode::SrcColorAdditive
    } else if material_mode_uses_alpha_blending(material_mode) {
        BlendMode::Alpha
    } else {
        BlendMode::Disabled
    };
    let mut depth_compare = if material_mode == WORLD_MATERIAL_MODE_GLINT {
        Some(CompareOp::Equal)
    } else {
        match depth_policy {
            WORLD_DEPTH_POLICY_DISABLED => None,
            WORLD_DEPTH_POLICY_TEST_WRITE | WORLD_DEPTH_POLICY_TEST_NO_WRITE => {
                Some(CompareOp::LessOrEqual)
            }
            _ => {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!("unknown world material depth policy {depth_policy}"),
                ));
            }
        }
    };
    match probe {
        SelectedSourceRasterProbe::None | SelectedSourceRasterProbe::InvertFrontFace => {}
        SelectedSourceRasterProbe::NoCull => cull_mode = CullMode::None,
        SelectedSourceRasterProbe::DepthDisabled => depth_compare = None,
        SelectedSourceRasterProbe::BlendDisabled => blend = BlendMode::Disabled,
    }
    front_face = selected_source_raster_probe_front_face_for(probe, front_face)?;
    Ok((cull_mode, front_face, blend, depth_compare))
}

pub(crate) fn selected_source_raster_probe_cull_mode() -> GalResult<CullMode> {
    match selected_source_raster_probe()? {
        SelectedSourceRasterProbe::NoCull => Ok(CullMode::None),
        SelectedSourceRasterProbe::None
        | SelectedSourceRasterProbe::DepthDisabled
        | SelectedSourceRasterProbe::BlendDisabled
        | SelectedSourceRasterProbe::InvertFrontFace => Ok(CullMode::Back),
    }
}

pub(crate) fn selected_source_raster_probe_depth_compare(
    normal: Option<CompareOp>,
) -> GalResult<Option<CompareOp>> {
    if matches!(
        selected_source_raster_probe()?,
        SelectedSourceRasterProbe::DepthDisabled
    ) {
        Ok(None)
    } else {
        Ok(normal)
    }
}

pub(crate) fn selected_source_raster_probe_front_face(normal: FrontFace) -> GalResult<FrontFace> {
    selected_source_raster_probe_front_face_for(selected_source_raster_probe()?, normal)
}

pub(in crate::render::worldrender) fn selected_source_raster_probe_front_face_for(
    probe: SelectedSourceRasterProbe,
    normal: FrontFace,
) -> GalResult<FrontFace> {
    match probe {
        SelectedSourceRasterProbe::InvertFrontFace => Ok(match normal {
            FrontFace::CounterClockwise => FrontFace::Clockwise,
            FrontFace::Clockwise => FrontFace::CounterClockwise,
        }),
        SelectedSourceRasterProbe::None
        | SelectedSourceRasterProbe::NoCull
        | SelectedSourceRasterProbe::DepthDisabled
        | SelectedSourceRasterProbe::BlendDisabled => Ok(normal),
    }
}

pub(in crate::render::worldrender) fn asset_generation_for_key(_mesh_key: u64, asset: &MeshAssetStore) -> GalResult<u64> {
    if asset.sections.is_empty() {
        return Err(GalError::invalid_argument(
            "world mesh asset has no sections",
        ));
    }
    Ok(asset.mesh_generation)
}

pub(in crate::render::worldrender) fn create_material_data_slot(
    gal: &mut VulkanicGal,
    label: &str,
    resource_layout: Handle,
    texture_view: Handle,
    sampler: Handle,
) -> GalResult<MaterialDataSlot> {
    let uniform_buffer = gal.create_buffer(BufferDesc {
        label: format!("{label}.material-data"),
        size: WORLD_MATERIAL_UNIFORM_BYTES,
        memory: MemoryDomain::Upload,
        usages: vec![
            BufferUsage::Storage,
            BufferUsage::TransferDst,
            BufferUsage::HostWrite,
        ],
    })?;
    let resource_set = match gal.create_resource_set(ResourceSetDesc {
        label: format!("{label}.resource-set"),
        layout: resource_layout,
        bindings: vec![
            ResourceBinding {
                binding: 0,
                array_index: 0,
                resource: uniform_buffer,
                kind: ResourceBindingKind::StorageBuffer,
                access: AccessFlags::READ,
                dynamic_offsets: Vec::new(),
                buffer_range: None,
            },
            ResourceBinding {
                binding: 1,
                array_index: 0,
                resource: texture_view,
                kind: ResourceBindingKind::SampledTexture,
                access: AccessFlags::READ,
                dynamic_offsets: Vec::new(),
                buffer_range: None,
            },
            ResourceBinding {
                binding: 2,
                array_index: 0,
                resource: sampler,
                kind: ResourceBindingKind::Sampler,
                access: AccessFlags::READ,
                dynamic_offsets: Vec::new(),
                buffer_range: None,
            },
        ],
    }) {
        Ok(resource_set) => resource_set,
        Err(error) => {
            let _ = gal.destroy(uniform_buffer);
            return Err(error);
        }
    };
    Ok(MaterialDataSlot {
        uniform_buffer,
        resource_set,
    })
}

pub(in crate::render::worldrender) fn create_mesh_resource_set(
    gal: &mut VulkanicGal,
    label: &str,
    resource_layout: Handle,
    vertex_buffer: Handle,
    vertex_range: u64,
    instance_buffer: Handle,
    instance_range: u64,
    texture_view: Handle,
    sampler: Handle,
    standard_foil: bool,
    observation_buffer: Option<Handle>,
) -> GalResult<Handle> {
    gal.create_resource_set(ResourceSetDesc {
        label: format!("{label}.resource-set"),
        layout: resource_layout,
        bindings: vec![
            ResourceBinding {
                binding: 0,
                array_index: 0,
                resource: vertex_buffer,
                kind: ResourceBindingKind::StorageBuffer,
                access: AccessFlags::READ,
                dynamic_offsets: vec![0],
                buffer_range: Some(vertex_range),
            },
            ResourceBinding {
                binding: 1,
                array_index: 0,
                resource: instance_buffer,
                kind: ResourceBindingKind::StorageBuffer,
                access: AccessFlags::READ,
                dynamic_offsets: vec![0],
                buffer_range: Some(instance_range),
            },
            ResourceBinding {
                binding: 2,
                array_index: 0,
                resource: texture_view,
                kind: ResourceBindingKind::SampledTexture,
                access: AccessFlags::READ,
                dynamic_offsets: Vec::new(),
                buffer_range: None,
            },
            ResourceBinding {
                binding: 3,
                array_index: 0,
                resource: sampler,
                kind: ResourceBindingKind::Sampler,
                access: AccessFlags::READ,
                dynamic_offsets: Vec::new(),
                buffer_range: None,
            },
        ]
        .into_iter()
        .chain(standard_foil.then_some(ResourceBinding {
            binding: 4,
            array_index: 0,
            resource: instance_buffer,
            kind: ResourceBindingKind::StorageBuffer,
            access: AccessFlags::READ,
            dynamic_offsets: vec![0],
            buffer_range: Some(instance_range),
        }))
        .chain(observation_buffer.map(diagnostics::vertex_observation::binding))
        .collect(),
    })
}

pub(in crate::render::worldrender) fn create_entity_outline_resource_set(
    gal: &mut VulkanicGal,
    label: &str,
    resource_layout: Handle,
    vertex_buffer: Handle,
    vertex_range: u64,
    instance_buffer: Handle,
    instance_range: u64,
    texture_view: Handle,
    sampler: Handle,
) -> GalResult<Handle> {
    if instance_range == 0 {
        return Err(GalError::invalid_argument(
            "entity outline resource set requires a non-empty instance range",
        ));
    }
    gal.create_resource_set(ResourceSetDesc {
        label: format!("{label}.resource-set"),
        layout: resource_layout,
        bindings: vec![
            ResourceBinding {
                binding: 0,
                array_index: 0,
                resource: vertex_buffer,
                kind: ResourceBindingKind::StorageBuffer,
                access: AccessFlags::READ,
                dynamic_offsets: vec![0],
                buffer_range: Some(vertex_range),
            },
            ResourceBinding {
                binding: 1,
                array_index: 0,
                resource: instance_buffer,
                kind: ResourceBindingKind::StorageBuffer,
                access: AccessFlags::READ,
                dynamic_offsets: vec![0],
                buffer_range: Some(instance_range),
            },
            // Vanilla outline silhouettes sample owned texture alpha; RGB
            // comes exclusively from the semantic outline color.
            ResourceBinding {
                binding: 2,
                array_index: 0,
                resource: texture_view,
                kind: ResourceBindingKind::SampledTexture,
                access: AccessFlags::READ,
                dynamic_offsets: Vec::new(),
                buffer_range: None,
            },
            ResourceBinding {
                binding: 3,
                array_index: 0,
                resource: sampler,
                kind: ResourceBindingKind::Sampler,
                access: AccessFlags::READ,
                dynamic_offsets: Vec::new(),
                buffer_range: None,
            },
        ],
    })
}

pub(in crate::render::worldrender) fn create_texture_view(
    gal: &mut VulkanicGal,
    label: &str,
    texture: Handle,
    format: TextureFormat,
) -> GalResult<Handle> {
    gal.create_texture_view(TextureViewDesc {
        label: label.to_string(),
        texture,
        format,
        base_mip: 0,
        mip_count: 1,
        base_layer: 0,
        layer_count: 1,
    })
}

pub(in crate::render::worldrender) fn sampled_binding(binding: u32, resource: Handle) -> ResourceBinding {
    ResourceBinding {
        binding,
        array_index: 0,
        resource,
        kind: ResourceBindingKind::SampledTexture,
        access: AccessFlags::READ,
        dynamic_offsets: Vec::new(),
        buffer_range: None,
    }
}

pub(in crate::render::worldrender) fn sampler_binding(binding: u32, resource: Handle) -> ResourceBinding {
    ResourceBinding {
        binding,
        array_index: 0,
        resource,
        kind: ResourceBindingKind::Sampler,
        access: AccessFlags::READ,
        dynamic_offsets: Vec::new(),
        buffer_range: None,
    }
}
