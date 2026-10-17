use super::*;

#[test]
fn atlas_replacement_retires_cached_source_bindings_before_material_wrappers() {
    use crate::render::shaderpack::programs::ProgramIdentity;
    use crate::render::vulkanic::resources::{
        ResourceBinding, ResourceBindingDesc, ResourceBindingKind, ResourceLayoutDesc,
        ResourceSetDesc,
    };
    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    let source = complete_bundled_pack_source_for_test();
    let mut runtime = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(1).unwrap();
    runtime.observe_source_candidate(&source);
    frontend.shader_runtime = Some(runtime);
    let atlas = |seed| WorldMeshTextureAssetPayload {
        texture_id: WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS,
        png_bytes: material_scene_png(seed),
        mip_png_bytes: Vec::new(),
        frame_width: 0,
        frame_height: 0,
        frame_count: 1,
        frame_ticks: 1,
        animation_flags: 0,
        frame_row_size: 0,
        interpolation_policy: 0,
        animation_frames: Vec::new(),
        coordinate_origin: 0,
        sampling: None,
        requested_mip_levels: 0,
    };
    frontend
        .apply_world_mesh_asset_update(&mut gal, 1, Vec::new(), vec![atlas(0)])
        .unwrap();
    frontend
        .ensure_candidate_source_material_texture_resources(&mut gal, 1)
        .unwrap();
    let old = frontend
        .shader_runtime
        .as_ref()
        .unwrap()
        .candidate_source_material_atlas_identity()
        .unwrap()
        .0;
    let layout = gal
        .create_resource_layout(ResourceLayoutDesc {
            label: "cached-atlas-layout".into(),
            bindings: vec![ResourceBindingDesc {
                binding: 0,
                kind: ResourceBindingKind::CombinedTextureSampler,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            }],
        })
        .unwrap();
    let mut sets = Vec::new();
    for (index, name) in [
        "terrain",
        "shadow",
        "cutout",
        "translucent",
        "material",
        "entity",
    ]
    .iter()
    .enumerate()
    {
        let set = gal
            .create_resource_set(ResourceSetDesc {
                label: (*name).into(),
                layout,
                bindings: vec![ResourceBinding {
                    binding: 0,
                    array_index: 0,
                    resource: old,
                    kind: ResourceBindingKind::CombinedTextureSampler,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: None,
                }],
            })
            .unwrap();
        sets.push(set);
        let identity = ProgramIdentity::new(*name);
        let generation = source.generation();
        let resource_generations = vec![(TerrainSourceResourceRole::MaterialAtlas, generation)];
        if index < 4 {
            frontend.lowered_source_terrain_pack_resources.insert(
                LoweredSourceTerrainPackKey {
                    shader_program_identity: identity,
                    shader_pack_generation: generation,
                    world_generation: 1,
                    resource_generations,
                },
                LoweredSourceTerrainPackResources { resource_set: set },
            );
        } else if index == 4 {
            frontend
                .lowered_textured_material_source_pack_resources
                .insert(
                    LoweredTexturedMaterialSourcePackKey {
                        shader_program_identity: identity,
                        shader_pack_generation: generation,
                        world_generation: 1,
                        resource_generations,
                        local_texture: None,
                    },
                    LoweredTexturedMaterialSourcePackResources { resource_set: set },
                );
        } else {
            frontend.lowered_entity_source_pack_resources.insert(
                LoweredEntitySourcePackKey {
                    shader_program_identity: identity,
                    shader_pack_generation: generation,
                    world_generation: 1,
                    resource_generations,
                    local_texture: (17, 1),
                },
                LoweredEntitySourcePackResources { resource_set: set },
            );
        }
    }
    frontend
        .apply_world_mesh_asset_update(&mut gal, 2, Vec::new(), vec![atlas(1)])
        .unwrap();
    assert_eq!(
        0,
        gal.metrics().validation_failures,
        "atlas replacement must release every cached sampler consumer first"
    );
    assert!(frontend.lowered_source_terrain_pack_resources.is_empty());
    assert!(frontend
        .lowered_textured_material_source_pack_resources
        .is_empty());
    assert!(frontend.lowered_entity_source_pack_resources.is_empty());
    assert!(sets
        .iter()
        .all(|set| gal.resource_set_descriptor_for_capture(*set).is_err()));
    assert_eq!(0, gal.retired_while_referenced());
    frontend
        .ensure_candidate_source_material_texture_resources(&mut gal, 1)
        .unwrap();
    let new = frontend
        .shader_runtime
        .as_ref()
        .unwrap()
        .candidate_source_material_atlas_identity()
        .unwrap()
        .0;
    assert_ne!(
        old, new,
        "unchanged shader-pack/world identity must still acquire the replacement atlas"
    );
    assert_eq!(0, gal.metrics().validation_failures);
    gal.destroy(layout).unwrap();
}
