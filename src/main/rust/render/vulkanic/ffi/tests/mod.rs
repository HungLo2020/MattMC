use super::*;
use crate::render::vulkanic::resources::{BackendApi, BackendFeatureFlags, BackendLimits};
use crate::render::vulkanic::world_primitive_frontend::{
    WORLD_MATERIAL_TEXTURE_STONE, WorldShaderEnvironmentFrame, WorldVoxelVolumeFrame,
};

fn test_capabilities() -> BackendCapabilities {
    BackendCapabilities {
        api: BackendApi::Mock,
        name: "ffi-test",
        features: BackendFeatureFlags {
            graphics: true,
            descriptor_arrays: true,
            optional_bindings: true,
            uniform_buffers: true,
            storage_buffers: true,
            texture_subresource_copies: true,
            host_buffer_access: true,
            presentation: true,
            ..BackendFeatureFlags::default()
        },
        limits: BackendLimits {
            max_buffer_size: 1024 * 1024,
            max_texture_extent_2d: 4096,
            max_texture_extent_3d: 0,
            max_texture_mip_levels: 1,
            max_texture_array_layers: 1,
            max_resource_layout_bindings: 16,
            max_binding_array_count: 16,
            max_color_attachments: 1,
            max_dynamic_offsets_per_binding: 0,
            max_command_lists_per_submission: 16,
            max_commands_per_list: 1024,
            max_draw_count: 1024,
            max_dispatch_groups_per_axis: 1,
        },
    }
}

#[test]
fn ffi_barrier_rejects_deprecated_stage_access_bits() {
    let barrier = FfiResourceBarrierAbi {
        byte_size: std::mem::size_of::<FfiResourceBarrierAbi>() as u32,
        resource: FfiHandle {
            raw: Handle::new(HandleKind::Texture, 1, 1).unwrap().raw(),
        },
        has_subresources: 0,
        subresources: FfiTextureSubresourceRange {
            base_mip: 0,
            mip_count: 0,
            base_layer: 0,
            layer_count: 0,
        },
        before: TextureUsageState::TransferDst as u32,
        after: TextureUsageState::ShaderRead as u32,
        stage_bits: 1,
        access_bits: 0,
        src_queue: QueueClass::Graphics as u32,
        dst_queue: QueueClass::Graphics as u32,
    };
    let error = super::submission::decode_barrier(&barrier).unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, error.code);
}

fn test_vulkan_capabilities() -> BackendCapabilities {
    BackendCapabilities {
        api: BackendApi::Vulkan,
        name: "ffi-test-vulkan",
        ..test_capabilities()
    }
}

fn sprite_request() -> FfiGuiSpriteRequest {
    FfiGuiSpriteRequest {
        byte_size: size_of::<FfiGuiSpriteRequest>() as u32,
        stratum: 50,
        sprite_id: 1,
        selected_slot: -1,
        progress_fraction: 1.0,
        fill_direction: 0,
        color_argb: 0xffff_ffff,
        x: 10,
        y: 20,
        width: 15,
        height: 15,
        gui_width: 320,
        gui_height: 180,
    }
}

fn frame_request(sprites: &[FfiGuiSpriteRequest]) -> FfiGuiFrameSubmitRequest {
    FfiGuiFrameSubmitRequest {
        header: FfiHeader {
            version: FFI_ABI_VERSION,
            byte_size: size_of::<FfiGuiFrameSubmitRequest>() as u32,
        },
        generation: 7,
        frame_id: 11,
        frame_target: FfiHandle::from(
            Handle::new(HandleKind::FrameTarget, 3, 1).expect("test handle"),
        ),
        gui_width: 320,
        gui_height: 180,
        sprites: FfiSlice {
            ptr: sprites.as_ptr(),
            count: sprites.len() as u64,
        },
        negotiated_feature_bits: FfiFeatureBits::GRAPHICS
            | FfiFeatureBits::DESCRIPTOR_ARRAYS
            | FfiFeatureBits::OPTIONAL_BINDINGS
            | FfiFeatureBits::UNIFORM_BUFFERS
            | FfiFeatureBits::STORAGE_BUFFERS
            | FfiFeatureBits::TEXTURE_SUBRESOURCE_COPIES
            | FfiFeatureBits::HOST_BUFFER_ACCESS
            | FfiFeatureBits::PRESENTATION,
    }
}

fn line_segment_request() -> FfiWorldLineSegmentRequest {
    FfiWorldLineSegmentRequest {
        byte_size: size_of::<FfiWorldLineSegmentRequest>() as u32,
        stratum: 100,
        style: 1,
        depth_policy: 0,
        color_argb: 0x6600_0000,
        line_width: 1.0,
        start_x: 1.0,
        start_y: 2.0,
        start_z: 3.0,
        end_x: 4.0,
        end_y: 5.0,
        end_z: 6.0,
        viewport_width: 854,
        viewport_height: 480,
    }
}

fn crack_quad_request() -> FfiWorldCrackQuadRequest {
    FfiWorldCrackQuadRequest {
        byte_size: size_of::<FfiWorldCrackQuadRequest>() as u32,
        stratum: 90,
        stage: 4,
        depth_policy: 1,
        blend_policy: 1,
        cull_policy: 0,
        color_argb: 0xffff_ffff,
        reserved0: 0,
        p0_x: 1.0,
        p0_y: 2.0,
        p0_z: 3.0,
        p1_x: 4.0,
        p1_y: 5.0,
        p1_z: 6.0,
        p2_x: 7.0,
        p2_y: 8.0,
        p2_z: 9.0,
        p3_x: 10.0,
        p3_y: 11.0,
        p3_z: 12.0,
        viewport_width: 854,
        viewport_height: 480,
    }
}

fn border_quad_request() -> FfiWorldBorderQuadRequest {
    FfiWorldBorderQuadRequest {
        byte_size: size_of::<FfiWorldBorderQuadRequest>() as u32,
        stratum: 80,
        texture_id: 1,
        depth_policy: 1,
        blend_policy: 1,
        cull_policy: 0,
        color_argb: 0xdd55_ff55,
        reserved0: 0,
        border_size: 8.0,
        distance_to_border: 2.0,
        scroll_u: 0.25,
        scroll_v: 0.25,
        uv_u: 0.0,
        uv_v: 0.0,
        uv_width: 1.0,
        uv_height: -4.0,
        p0_x: -1.0,
        p0_y: -2.0,
        p0_z: -3.0,
        p1_x: 1.0,
        p1_y: -2.0,
        p1_z: -3.0,
        p2_x: 1.0,
        p2_y: 2.0,
        p2_z: -3.0,
        p3_x: -1.0,
        p3_y: 2.0,
        p3_z: -3.0,
        viewport_width: 854,
        viewport_height: 480,
    }
}

fn material_quad_request() -> FfiWorldMaterialQuadRequest {
    FfiWorldMaterialQuadRequest {
        byte_size: size_of::<FfiWorldMaterialQuadRequest>() as u32,
        stratum: WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY,
        material_id: WORLD_MATERIAL_ID_OPAQUE_TEXTURED,
        texture_id: WORLD_MATERIAL_TEXTURE_STONE,
        material_mode: WORLD_MATERIAL_MODE_OPAQUE,
        depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
        cull_policy: WORLD_CULL_BACK,
        topology: WORLD_TOPOLOGY_TRIANGLES,
        color_argb: 0xffff_ffff,
        reserved0: 0,
        p0_x: -1.0,
        p0_y: -1.0,
        p0_z: -2.0,
        p1_x: 1.0,
        p1_y: -1.0,
        p1_z: -2.0,
        p2_x: 1.0,
        p2_y: 1.0,
        p2_z: -2.0,
        p3_x: -1.0,
        p3_y: 1.0,
        p3_z: -2.0,
        uv0_u: 0.0,
        uv0_v: 0.0,
        uv1_u: 1.0,
        uv1_v: 0.0,
        uv2_u: 1.0,
        uv2_v: 1.0,
        uv3_u: 0.0,
        uv3_v: 1.0,
        viewport_width: 854,
        viewport_height: 480,
    }
}

fn material_table_record() -> FfiWorldMaterialTableRecord {
    FfiWorldMaterialTableRecord {
        byte_size: size_of::<FfiWorldMaterialTableRecord>() as u32,
        stratum: WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY,
        material_id: WORLD_MATERIAL_ID_OPAQUE_TEXTURED,
        texture_id: WORLD_MATERIAL_TEXTURE_STONE,
        material_mode: WORLD_MATERIAL_MODE_OPAQUE,
        depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
        cull_policy: WORLD_CULL_BACK,
        topology: WORLD_TOPOLOGY_TRIANGLES,
        winding: WORLD_WINDING_CCW,
        reserved0: 0,
    }
}

fn compact_material_quad_request() -> FfiWorldMaterialCompactQuadRequest {
    FfiWorldMaterialCompactQuadRequest {
        byte_size: size_of::<FfiWorldMaterialCompactQuadRequest>() as u32,
        material_index: 0,
        color_argb: 0xffff_ffff,
        reserved0: 0,
        p0_x: -1.0,
        p0_y: -1.0,
        p0_z: -2.0,
        p1_x: 1.0,
        p1_y: -1.0,
        p1_z: -2.0,
        p2_x: 1.0,
        p2_y: 1.0,
        p2_z: -2.0,
        p3_x: -1.0,
        p3_y: 1.0,
        p3_z: -2.0,
        uv0_u: 0.0,
        uv0_v: 0.0,
        uv1_u: 1.0,
        uv1_v: 0.0,
        uv2_u: 1.0,
        uv2_v: 1.0,
        uv3_u: 0.0,
        uv3_v: 1.0,
    }
}

fn whole_frame_request(
    segments: &[FfiWorldLineSegmentRequest],
    sprites: &[FfiGuiSpriteRequest],
) -> FfiWholeFrameSubmitRequest {
    let mut view_matrix = [0.0; 16];
    let mut projection_matrix = [0.0; 16];
    view_matrix[0] = 1.0;
    view_matrix[5] = 1.0;
    view_matrix[10] = 1.0;
    view_matrix[15] = 1.0;
    projection_matrix[0] = 1.0;
    projection_matrix[5] = 1.0;
    projection_matrix[10] = 1.0;
    projection_matrix[15] = 1.0;
    FfiWholeFrameSubmitRequest {
        header: FfiHeader {
            version: FFI_ABI_VERSION,
            byte_size: size_of::<FfiWholeFrameSubmitRequest>() as u32,
        },
        generation: 7,
        frame_id: 11,
        correlation_id: 13,
        frame_target: FfiHandle::from(
            Handle::new(HandleKind::FrameTarget, 3, 1).expect("test handle"),
        ),
        gui_width: 320,
        gui_height: 180,
        viewport_width: 854,
        viewport_height: 480,
        view_matrix,
        projection_matrix,
        world_background: FfiWorldBackgroundRequest {
            byte_size: size_of::<FfiWorldBackgroundRequest>() as u32,
            enabled: 1,
            sky_type: WORLD_BACKGROUND_SKY_OVERWORLD,
            load_intent: WORLD_BACKGROUND_LOAD_CLEAR,
            store_intent: WORLD_BACKGROUND_STORE_STORE,
            color_argb: 0xff102844,
            viewport_width: 854,
            viewport_height: 480,
        },
        world_segments: FfiSlice {
            ptr: segments.as_ptr(),
            count: segments.len() as u64,
        },
        world_crack_quads: FfiSlice {
            ptr: std::ptr::null(),
            count: 0,
        },
        world_border_quads: FfiSlice {
            ptr: std::ptr::null(),
            count: 0,
        },
        world_material_quads: FfiSlice {
            ptr: std::ptr::null(),
            count: 0,
        },
        world_material_table: FfiSlice {
            ptr: std::ptr::null(),
            count: 0,
        },
        world_material_compact_quads: FfiSlice {
            ptr: std::ptr::null(),
            count: 0,
        },
        world_mesh_instances: FfiSlice {
            ptr: std::ptr::null(),
            count: 0,
        },
        gui_sprites: FfiSlice {
            ptr: sprites.as_ptr(),
            count: sprites.len() as u64,
        },
        negotiated_feature_bits: FfiFeatureBits::GRAPHICS
            | FfiFeatureBits::DESCRIPTOR_ARRAYS
            | FfiFeatureBits::OPTIONAL_BINDINGS
            | FfiFeatureBits::UNIFORM_BUFFERS
            | FfiFeatureBits::STORAGE_BUFFERS
            | FfiFeatureBits::TEXTURE_SUBRESOURCE_COPIES
            | FfiFeatureBits::HOST_BUFFER_ACCESS
            | FfiFeatureBits::PRESENTATION,
        voxel_volume_frame: FfiWorldVoxelVolumeFrame {
            byte_size: size_of::<FfiWorldVoxelVolumeFrame>() as u32,
            enabled: 0,
            reserved0: 0,
            reserved1: 0,
            world_generation: 0,
            resource_generation: 0,
            camera_x: 0.0,
            camera_y: 0.0,
            camera_z: 0.0,
            reserved2: 0,
        },
        shader_environment_frame: FfiWorldShaderEnvironmentFrame {
            byte_size: size_of::<FfiWorldShaderEnvironmentFrame>() as u32,
            enabled: 0,
            frame_counter: 0,
            world_day: 0,
            world_generation: 0,
            world_time: 0,
            frame_time_seconds: 0.0,
            frame_time_counter: 0.0,
            time_of_day: 0.0,
            rain_strength: 0.0,
            thunder_strength: 0.0,
            sky_darken: 0.0,
            moon_phase: 0,
            eye_submersion: 0,
            screen_brightness: 0.0,
            far_plane: 0.0,
            relative_eye_x: 0.0,
            relative_eye_y: 0.0,
            relative_eye_z: 0.0,
            sky_color_r: 0.0,
            sky_color_g: 0.0,
            sky_color_b: 0.0,
            darkness_light_factor: 0.0,
            night_vision: 0.0,
            fog_color_r: 0.0,
            fog_color_g: 0.0,
            fog_color_b: 0.0,
            biome_precipitation: 0,
            biome_resource_location_utf8: FfiBytes {
                ptr: core::ptr::null(),
                len: 0,
            },
            main_hand_item_model_resource_location_utf8: FfiBytes {
                ptr: core::ptr::null(),
                len: 0,
            },
            off_hand_item_model_resource_location_utf8: FfiBytes {
                ptr: core::ptr::null(),
                len: 0,
            },
            main_hand_item_light_emission: 0,
            off_hand_item_light_emission: 0,
        },
    }
}

fn whole_frame_request_with_borders(
    borders: &[FfiWorldBorderQuadRequest],
    sprites: &[FfiGuiSpriteRequest],
) -> FfiWholeFrameSubmitRequest {
    let mut request = whole_frame_request(&[], sprites);
    request.world_border_quads = FfiSlice {
        ptr: borders.as_ptr(),
        count: borders.len() as u64,
    };
    request
}

fn whole_frame_request_with_cracks(
    segments: &[FfiWorldLineSegmentRequest],
    cracks: &[FfiWorldCrackQuadRequest],
    sprites: &[FfiGuiSpriteRequest],
) -> FfiWholeFrameSubmitRequest {
    let mut request = whole_frame_request(segments, sprites);
    request.world_crack_quads = FfiSlice {
        ptr: cracks.as_ptr(),
        count: cracks.len() as u64,
    };
    request
}

fn whole_frame_request_with_materials(
    materials: &[FfiWorldMaterialQuadRequest],
) -> FfiWholeFrameSubmitRequest {
    let mut request = whole_frame_request(&[], &[]);
    request.world_material_quads = FfiSlice {
        ptr: materials.as_ptr(),
        count: materials.len() as u64,
    };
    request
}

fn whole_frame_request_with_compact_materials(
    table: &[FfiWorldMaterialTableRecord],
    materials: &[FfiWorldMaterialCompactQuadRequest],
) -> FfiWholeFrameSubmitRequest {
    let mut request = whole_frame_request(&[], &[]);
    request.world_material_table = FfiSlice {
        ptr: table.as_ptr(),
        count: table.len() as u64,
    };
    request.world_material_compact_quads = FfiSlice {
        ptr: materials.as_ptr(),
        count: materials.len() as u64,
    };
    request
}

fn whole_frame_request_with_mesh_instances(
    instances: &[FfiWorldMeshInstanceRecord],
) -> FfiWholeFrameSubmitRequest {
    let mut request = whole_frame_request(&[], &[]);
    request.world_mesh_instances = FfiSlice {
        ptr: instances.as_ptr(),
        count: instances.len() as u64,
    };
    request
}

fn asset_update_request(assets: &[FfiGuiAssetPayload]) -> FfiGuiAssetUpdateRequest {
    FfiGuiAssetUpdateRequest {
        header: FfiHeader {
            version: FFI_ABI_VERSION,
            byte_size: size_of::<FfiGuiAssetUpdateRequest>() as u32,
        },
        generation: 9,
        assets: FfiSlice {
            ptr: assets.as_ptr(),
            count: assets.len() as u64,
        },
        negotiated_feature_bits: FfiFeatureBits::GRAPHICS
            | FfiFeatureBits::DESCRIPTOR_ARRAYS
            | FfiFeatureBits::OPTIONAL_BINDINGS
            | FfiFeatureBits::UNIFORM_BUFFERS
            | FfiFeatureBits::STORAGE_BUFFERS
            | FfiFeatureBits::TEXTURE_SUBRESOURCE_COPIES
            | FfiFeatureBits::HOST_BUFFER_ACCESS
            | FfiFeatureBits::PRESENTATION,
    }
}

fn world_border_asset_update_request(bytes: &[u8]) -> FfiWorldBorderAssetUpdateRequest {
    FfiWorldBorderAssetUpdateRequest {
        header: FfiHeader {
            version: FFI_ABI_VERSION,
            byte_size: size_of::<FfiWorldBorderAssetUpdateRequest>() as u32,
        },
        generation: 9,
        texture_id: 1,
        reserved0: 0,
        png_bytes: FfiBytes {
            ptr: bytes.as_ptr(),
            len: bytes.len() as u64,
        },
        negotiated_feature_bits: FfiFeatureBits::GRAPHICS
            | FfiFeatureBits::DESCRIPTOR_ARRAYS
            | FfiFeatureBits::OPTIONAL_BINDINGS
            | FfiFeatureBits::UNIFORM_BUFFERS
            | FfiFeatureBits::STORAGE_BUFFERS
            | FfiFeatureBits::TEXTURE_SUBRESOURCE_COPIES
            | FfiFeatureBits::HOST_BUFFER_ACCESS
            | FfiFeatureBits::PRESENTATION,
    }
}

fn world_crack_asset_update_request(
    assets: &[FfiWorldCrackAssetPayload],
) -> FfiWorldCrackAssetUpdateRequest {
    FfiWorldCrackAssetUpdateRequest {
        header: FfiHeader {
            version: FFI_ABI_VERSION,
            byte_size: size_of::<FfiWorldCrackAssetUpdateRequest>() as u32,
        },
        generation: 9,
        assets: FfiSlice {
            ptr: assets.as_ptr(),
            count: assets.len() as u64,
        },
        negotiated_feature_bits: FfiFeatureBits::GRAPHICS
            | FfiFeatureBits::DESCRIPTOR_ARRAYS
            | FfiFeatureBits::OPTIONAL_BINDINGS
            | FfiFeatureBits::UNIFORM_BUFFERS
            | FfiFeatureBits::STORAGE_BUFFERS
            | FfiFeatureBits::TEXTURE_SUBRESOURCE_COPIES
            | FfiFeatureBits::HOST_BUFFER_ACCESS
            | FfiFeatureBits::PRESENTATION,
    }
}

fn world_material_asset_update_request(
    assets: &[FfiWorldMaterialAssetPayload],
) -> FfiWorldMaterialAssetUpdateRequest {
    FfiWorldMaterialAssetUpdateRequest {
        header: FfiHeader {
            version: FFI_ABI_VERSION,
            byte_size: size_of::<FfiWorldMaterialAssetUpdateRequest>() as u32,
        },
        generation: 9,
        assets: FfiSlice {
            ptr: assets.as_ptr(),
            count: assets.len() as u64,
        },
        negotiated_feature_bits: FfiFeatureBits::GRAPHICS
            | FfiFeatureBits::DESCRIPTOR_ARRAYS
            | FfiFeatureBits::OPTIONAL_BINDINGS
            | FfiFeatureBits::UNIFORM_BUFFERS
            | FfiFeatureBits::STORAGE_BUFFERS
            | FfiFeatureBits::TEXTURE_SUBRESOURCE_COPIES
            | FfiFeatureBits::HOST_BUFFER_ACCESS
            | FfiFeatureBits::PRESENTATION,
    }
}

fn shader_pack_source_update_request(
    pack_name: &[u8],
    files: &[FfiShaderPackSourceFile],
) -> FfiShaderPackSourceUpdateRequest {
    FfiShaderPackSourceUpdateRequest {
        header: FfiHeader {
            version: FFI_ABI_VERSION,
            byte_size: size_of::<FfiShaderPackSourceUpdateRequest>() as u32,
        },
        generation: 9,
        pack_name_utf8: FfiBytes {
            ptr: pack_name.as_ptr(),
            len: pack_name.len() as u64,
        },
        files: FfiSlice {
            ptr: files.as_ptr(),
            count: files.len() as u64,
        },
    }
}

fn world_mesh_asset_update_request(
    meshes: &[FfiWorldMeshAssetRecord],
    textures: &[FfiWorldMeshTextureAssetPayload],
) -> FfiWorldMeshAssetUpdateRequest {
    FfiWorldMeshAssetUpdateRequest {
        header: FfiHeader {
            version: FFI_ABI_VERSION,
            byte_size: size_of::<FfiWorldMeshAssetUpdateRequest>() as u32,
        },
        generation: 9,
        meshes: FfiSlice {
            ptr: meshes.as_ptr(),
            count: meshes.len() as u64,
        },
        textures: FfiSlice {
            ptr: textures.as_ptr(),
            count: textures.len() as u64,
        },
        sorted_indices: FfiSlice {
            ptr: std::ptr::null(),
            count: 0,
        },
        negotiated_feature_bits: FfiFeatureBits::GRAPHICS
            | FfiFeatureBits::DESCRIPTOR_ARRAYS
            | FfiFeatureBits::OPTIONAL_BINDINGS
            | FfiFeatureBits::UNIFORM_BUFFERS
            | FfiFeatureBits::STORAGE_BUFFERS
            | FfiFeatureBits::TEXTURE_SUBRESOURCE_COPIES
            | FfiFeatureBits::HOST_BUFFER_ACCESS
            | FfiFeatureBits::PRESENTATION,
    }
}

fn mesh_vertex() -> FfiWorldMeshVertex {
    FfiWorldMeshVertex {
        byte_size: size_of::<FfiWorldMeshVertex>() as u32,
        color_argb: 0xffff_ffff,
        normal_packed: 0,
        light: 0xf000_f000,
        x: 0.0,
        y: 0.0,
        z: -1.0,
        u: 0.0,
        v: 0.0,
        atlas_u: 0.25,
        atlas_v: 0.5,
        shader_block_id: 10232,
        shader_material_type: -1,
        mid_block_packed: 0x0020_2020,
    }
}

fn mesh_section(texture_id: u32) -> FfiWorldMeshSectionRecord {
    FfiWorldMeshSectionRecord {
        byte_size: size_of::<FfiWorldMeshSectionRecord>() as u32,
        material_id: WORLD_MATERIAL_ID_OPAQUE_TEXTURED,
        texture_id,
        material_mode: WORLD_MATERIAL_MODE_OPAQUE,
        cull_policy: WORLD_CULL_BACK,
        winding: WORLD_WINDING_CCW,
        index_offset: 0,
        index_count: 3,
    }
}

fn mesh_asset<'a>(
    vertices: &'a [FfiWorldMeshVertex],
    index_bytes: &'a [u8],
    sections: &'a [FfiWorldMeshSectionRecord],
) -> FfiWorldMeshAssetRecord {
    FfiWorldMeshAssetRecord {
        byte_size: size_of::<FfiWorldMeshAssetRecord>() as u32,
        vertex_layout_version: 1,
        index_type: IndexType::U16 as u32,
        reserved0: 0,
        mesh_key: 44,
        mesh_generation: 9,
        vertices: FfiSlice {
            ptr: vertices.as_ptr(),
            count: vertices.len() as u64,
        },
        index_bytes: FfiBytes {
            ptr: index_bytes.as_ptr(),
            len: index_bytes.len() as u64,
        },
        sections: FfiSlice {
            ptr: sections.as_ptr(),
            count: sections.len() as u64,
        },
    }
}

fn mesh_instance() -> FfiWorldMeshInstanceRecord {
    FfiWorldMeshInstanceRecord {
        byte_size: size_of::<FfiWorldMeshInstanceRecord>() as u32,
        stratum: WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY,
        mesh_section_index: 0,
        depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
        cull_policy: WORLD_CULL_BACK,
        winding: WORLD_WINDING_CCW,
        color_argb: 0xffff_ffff,
        viewport_width: 854,
        viewport_height: 480,
        mesh_key: 44,
        mesh_generation: 9,
        transform: [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ],
    }
}

#[test]
fn semantic_gui_ffi_decode_copies_caller_memory() {
    let mut sprites = vec![sprite_request()];
    let request = frame_request(&sprites);
    let (_generation, _target, owned) =
        unsafe { decode_gui_frame_submit(&request, test_capabilities()).unwrap() };
    sprites[0].x = 99;
    assert_eq!(owned[0].x, 10);
    assert_eq!(owned[0].sprite_id, 1);
    assert_eq!(owned[0].gui_width, 320);
}

#[test]
fn semantic_gui_ffi_rejects_malformed_sprite_records() {
    let mut sprites = vec![sprite_request()];
    sprites[0].byte_size -= 4;
    let request = frame_request(&sprites);
    let error = unsafe { decode_gui_frame_submit(&request, test_capabilities()) }
        .expect_err("malformed sprite must fail");
    assert_eq!(error.code, StatusCode::InvalidArgument);
    assert!(error.message.contains("GUI sprite byte size mismatch"));
}

#[test]
fn semantic_gui_ffi_rejects_wrong_frame_target_kind() {
    let sprites = vec![sprite_request()];
    let mut request = frame_request(&sprites);
    request.frame_target =
        FfiHandle::from(Handle::new(HandleKind::Texture, 3, 1).expect("test handle"));
    let error = unsafe { decode_gui_frame_submit(&request, test_capabilities()) }
        .expect_err("wrong frame target kind must fail");
    assert_eq!(error.code, StatusCode::WrongHandleType);
}

#[test]
fn whole_frame_world_primitive_ffi_decode_copies_caller_memory() {
    let mut segments = vec![line_segment_request()];
    let sprites = vec![sprite_request()];
    let request = whole_frame_request(&segments, &sprites);
    let (_generation, _target, frame, gui) =
        unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()).unwrap() };
    segments[0].start_x = 99.0;
    assert_eq!(frame.frame_id, 11);
    assert_eq!(frame.correlation_id, 13);
    assert!(frame.background.enabled);
    assert_eq!(WORLD_BACKGROUND_SKY_OVERWORLD, frame.background.sky_type);
    assert_eq!(0xff102844, frame.background.color_argb);
    assert_eq!(frame.segments[0].start[0], 1.0);
    assert_eq!(frame.segments[0].end[2], 6.0);
    assert_eq!(gui.len(), 1);
}

#[test]
fn whole_frame_voxel_volume_semantics_decode_and_reject_malformed_state() {
    let mut request = whole_frame_request(&[], &[]);
    request.voxel_volume_frame = FfiWorldVoxelVolumeFrame {
        byte_size: size_of::<FfiWorldVoxelVolumeFrame>() as u32,
        enabled: 1,
        reserved0: 0,
        reserved1: 0,
        world_generation: 17,
        resource_generation: 23,
        camera_x: 12.5,
        camera_y: 64.0,
        camera_z: -3.25,
        reserved2: 0,
    };
    let (_generation, _target, frame, _gui) =
        unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()).unwrap() };
    assert_eq!(
        frame.voxel_volume,
        WorldVoxelVolumeFrame {
            enabled: true,
            world_generation: 17,
            resource_generation: 23,
            camera_world_position: [12.5, 64.0, -3.25],
        }
    );

    request.voxel_volume_frame.world_generation = 0;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("enabled voxel volume must have a world generation");
    assert_eq!(error.code, StatusCode::InvalidArgument);

    request = whole_frame_request(&[], &[]);
    request.voxel_volume_frame.camera_x = 1.0;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("disabled voxel volume must be zeroed");
    assert_eq!(error.code, StatusCode::InvalidArgument);

    request = whole_frame_request(&[], &[]);
    request.voxel_volume_frame.enabled = 1;
    request.voxel_volume_frame.world_generation = 17;
    request.voxel_volume_frame.resource_generation = 23;
    request.voxel_volume_frame.camera_x = f32::NAN;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("voxel volume camera coordinates must be finite");
    assert_eq!(error.code, StatusCode::InvalidArgument);
}

#[test]
fn whole_frame_shader_environment_semantics_decode_and_reject_malformed_state() {
    let biome_resource_location = b"minecraft:snowy_plains";
    let main_hand_item_model_resource_location = b"minecraft:lava_bucket";
    let off_hand_item_model_resource_location = b"minecraft:totem_of_undying";
    let mut request = whole_frame_request(&[], &[]);
    request.shader_environment_frame = FfiWorldShaderEnvironmentFrame {
        byte_size: size_of::<FfiWorldShaderEnvironmentFrame>() as u32,
        enabled: 1,
        frame_counter: 42,
        world_day: 17,
        world_generation: 17,
        world_time: 24_013,
        frame_time_seconds: 0.016,
        frame_time_counter: 12.5,
        time_of_day: 0.25,
        rain_strength: 0.2,
        thunder_strength: 0.1,
        sky_darken: 0.75,
        moon_phase: 3,
        eye_submersion: 1,
        screen_brightness: 0.5,
        far_plane: 192.0,
        relative_eye_x: 0.25,
        relative_eye_y: -0.5,
        relative_eye_z: 0.75,
        sky_color_r: 0.2,
        sky_color_g: 0.4,
        sky_color_b: 0.6,
        darkness_light_factor: 0.125,
        night_vision: 0.875,
        fog_color_r: 0.3,
        fog_color_g: 0.5,
        fog_color_b: 0.7,
        biome_precipitation: 2,
        biome_resource_location_utf8: FfiBytes {
            ptr: biome_resource_location.as_ptr(),
            len: biome_resource_location.len() as u64,
        },
        main_hand_item_model_resource_location_utf8: FfiBytes {
            ptr: main_hand_item_model_resource_location.as_ptr(),
            len: main_hand_item_model_resource_location.len() as u64,
        },
        off_hand_item_model_resource_location_utf8: FfiBytes {
            ptr: off_hand_item_model_resource_location.as_ptr(),
            len: off_hand_item_model_resource_location.len() as u64,
        },
        main_hand_item_light_emission: 13,
        off_hand_item_light_emission: 7,
    };
    let (_generation, _target, frame, _gui) =
        unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()).unwrap() };
    assert_eq!(
        frame.shader_environment,
        WorldShaderEnvironmentFrame {
            enabled: true,
            world_generation: 17,
            world_time: 24_013,
            frame_counter: 42,
            frame_time_seconds: 0.016,
            frame_time_counter: 12.5,
            world_day: 17,
            moon_phase: 3,
            time_of_day: 0.25,
            rain_strength: 0.2,
            thunder_strength: 0.1,
            sky_darken: 0.75,
            eye_submersion: 1,
            screen_brightness: 0.5,
            far_plane: 192.0,
            relative_eye_position: [0.25, -0.5, 0.75],
            sky_color: [0.2, 0.4, 0.6],
            darkness_light_factor: 0.125,
            night_vision: 0.875,
            fog_color: [0.3, 0.5, 0.7],
            biome_precipitation: 2,
            biome_resource_location: "minecraft:snowy_plains".to_string(),
            main_hand_item_model_resource_location: "minecraft:lava_bucket".to_string(),
            off_hand_item_model_resource_location: "minecraft:totem_of_undying".to_string(),
            main_hand_item_light_emission: 13,
            off_hand_item_light_emission: 7,
        }
    );

    request.shader_environment_frame.time_of_day = f32::NAN;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject non-finite values");
    assert_eq!(error.code, StatusCode::InvalidArgument);

    request.shader_environment_frame.time_of_day = 0.25;
    request
        .shader_environment_frame
        .main_hand_item_light_emission = 16;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject out-of-range held-item emission");
    assert_eq!(error.code, StatusCode::InvalidArgument);

    request.shader_environment_frame.time_of_day = 0.25;
    let malformed_biome_resource_location = b"Minecraft:Snowy Plains";
    request
        .shader_environment_frame
        .biome_resource_location_utf8 = FfiBytes {
        ptr: malformed_biome_resource_location.as_ptr(),
        len: malformed_biome_resource_location.len() as u64,
    };
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject a non-canonical biome identity");
    assert_eq!(error.code, StatusCode::InvalidArgument);
    request
        .shader_environment_frame
        .biome_resource_location_utf8 = FfiBytes {
        ptr: biome_resource_location.as_ptr(),
        len: biome_resource_location.len() as u64,
    };
    let malformed_main_hand_item_model_resource_location = b"Minecraft:Lava Bucket";
    request
        .shader_environment_frame
        .main_hand_item_model_resource_location_utf8 = FfiBytes {
        ptr: malformed_main_hand_item_model_resource_location.as_ptr(),
        len: malformed_main_hand_item_model_resource_location.len() as u64,
    };
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject a non-canonical held-item identity");
    assert_eq!(error.code, StatusCode::InvalidArgument);
    request
        .shader_environment_frame
        .main_hand_item_model_resource_location_utf8 = FfiBytes {
        ptr: main_hand_item_model_resource_location.as_ptr(),
        len: main_hand_item_model_resource_location.len() as u64,
    };
    request.shader_environment_frame.night_vision = f32::NAN;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject non-finite night vision");
    assert_eq!(error.code, StatusCode::InvalidArgument);
    request.shader_environment_frame.night_vision = 0.875;
    request.shader_environment_frame.fog_color_g = f32::NAN;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject non-finite fog color");
    assert_eq!(error.code, StatusCode::InvalidArgument);
    request.shader_environment_frame.fog_color_g = 0.5;
    request.shader_environment_frame.biome_precipitation = 3;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject unknown biome precipitation");
    assert_eq!(error.code, StatusCode::InvalidArgument);
    request.shader_environment_frame.biome_precipitation = 2;
    request.shader_environment_frame.frame_counter = 720_720;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject an unwrapped frame counter");
    assert_eq!(error.code, StatusCode::InvalidArgument);

    request.shader_environment_frame.frame_counter = 42;
    request.shader_environment_frame.eye_submersion = 4;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject unknown camera submersion");
    assert_eq!(error.code, StatusCode::InvalidArgument);

    request.shader_environment_frame.eye_submersion = 1;
    request.shader_environment_frame.sky_color_b = f32::NAN;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject non-finite sky color");
    assert_eq!(error.code, StatusCode::InvalidArgument);

    request.shader_environment_frame.sky_color_b = 0.6;
    request.shader_environment_frame.far_plane = 0.0;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject a non-positive far plane");
    assert_eq!(error.code, StatusCode::InvalidArgument);

    request.shader_environment_frame.far_plane = 192.0;
    request.shader_environment_frame.frame_time_seconds = f32::NAN;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject a non-finite frame time");
    assert_eq!(error.code, StatusCode::InvalidArgument);

    request.shader_environment_frame.frame_time_seconds = 0.016;
    request.shader_environment_frame.relative_eye_z = f32::NAN;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject a non-finite relative eye position");
    assert_eq!(error.code, StatusCode::InvalidArgument);

    request.shader_environment_frame.frame_counter = 42;
    request.shader_environment_frame.frame_time_counter = 3600.0;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject an unreset frame timer");
    assert_eq!(error.code, StatusCode::InvalidArgument);

    request.shader_environment_frame.frame_time_counter = 12.5;
    request.shader_environment_frame.moon_phase = 8;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("shader environment must reject an invalid moon phase");
    assert_eq!(error.code, StatusCode::InvalidArgument);

    request = whole_frame_request(&[], &[]);
    request.shader_environment_frame.world_time = 1;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("disabled shader environment must be zeroed");
    assert_eq!(error.code, StatusCode::InvalidArgument);
}

#[test]
fn whole_frame_world_primitive_ffi_rejects_bad_segment_size_and_non_vulkan() {
    let mut segments = vec![line_segment_request()];
    let sprites = vec![sprite_request()];
    let request = whole_frame_request(&segments, &sprites);
    let error = unsafe { decode_whole_frame_submit(&request, test_capabilities()) }
        .expect_err("non-Vulkan whole-frame submit must fail");
    assert_eq!(error.code, StatusCode::UnsupportedFeature);
    segments[0].byte_size -= 4;
    let request = whole_frame_request(&segments, &sprites);
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("malformed world line segment must fail");
    assert_eq!(error.code, StatusCode::InvalidArgument);
}

#[test]
fn whole_frame_world_background_ffi_rejects_malformed_payloads() {
    let mut request = whole_frame_request(&[], &[]);
    request.world_background.byte_size -= 4;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("malformed world background must fail");
    assert_eq!(error.code, StatusCode::InvalidArgument);

    request = whole_frame_request(&[], &[]);
    request.world_background.sky_type = 99;
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("unknown sky type must fail validation");
    assert_eq!(error.code, StatusCode::UnknownEnum);
}

#[test]
fn whole_frame_world_crack_ffi_copies_and_rejects_malformed_payloads() {
    let mut cracks = vec![crack_quad_request()];
    let request = whole_frame_request_with_cracks(&[], &cracks, &[]);
    let (_generation, _target, frame, gui) =
        unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()).unwrap() };
    cracks[0].p0_x = 99.0;
    assert_eq!(frame.crack_quads.len(), 1);
    assert_eq!(frame.crack_quads[0].vertices[0][0], 1.0);
    assert_eq!(frame.crack_quads[0].stage, 4);
    assert!(gui.is_empty());

    cracks[0] = crack_quad_request();
    cracks[0].blend_policy = 99;
    let request = whole_frame_request_with_cracks(&[], &cracks, &[]);
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("unknown crack blend policy must fail");
    assert_eq!(error.code, StatusCode::UnknownEnum);

    cracks[0] = crack_quad_request();
    cracks[0].byte_size -= 4;
    let request = whole_frame_request_with_cracks(&[], &cracks, &[]);
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("malformed crack quad must fail");
    assert_eq!(error.code, StatusCode::InvalidArgument);
}

#[test]
fn whole_frame_world_border_ffi_copies_and_rejects_malformed_payloads() {
    let mut borders = vec![border_quad_request()];
    let request = whole_frame_request_with_borders(&borders, &[]);
    let (_generation, _target, frame, gui) =
        unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()).unwrap() };
    borders[0].p0_x = 99.0;
    assert_eq!(frame.border_quads.len(), 1);
    assert_eq!(frame.border_quads[0].vertices[0][0], -1.0);
    assert_eq!(frame.border_quads[0].texture_id, 1);
    assert_eq!(frame.border_quads[0].uv_region[3], -4.0);
    assert!(gui.is_empty());

    borders[0] = border_quad_request();
    borders[0].texture_id = 99;
    let request = whole_frame_request_with_borders(&borders, &[]);
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("unknown border texture must fail");
    assert_eq!(error.code, StatusCode::UnknownEnum);

    borders[0] = border_quad_request();
    borders[0].byte_size -= 4;
    let request = whole_frame_request_with_borders(&borders, &[]);
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("malformed border quad must fail");
    assert_eq!(error.code, StatusCode::InvalidArgument);
}

#[test]
fn whole_frame_world_material_ffi_copies_and_rejects_malformed_payloads() {
    let mut materials = vec![material_quad_request()];
    let request = whole_frame_request_with_materials(&materials);
    let (_generation, _target, frame, gui) =
        unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()).unwrap() };
    materials[0].p0_x = 99.0;
    assert_eq!(frame.material_quads.len(), 1);
    assert_eq!(frame.material_quads[0].vertices[0][0], -1.0);
    assert_eq!(frame.material_quads[0].uvs[2], [1.0, 1.0]);
    assert_eq!(
        frame.material_quads[0].material_id,
        WORLD_MATERIAL_ID_OPAQUE_TEXTURED
    );
    assert!(gui.is_empty());

    materials[0] = material_quad_request();
    materials[0].material_id = 99;
    let request = whole_frame_request_with_materials(&materials);
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("unknown material id must fail validation");
    assert_eq!(error.code, StatusCode::UnknownEnum);

    materials[0] = material_quad_request();
    materials[0].byte_size -= 4;
    let request = whole_frame_request_with_materials(&materials);
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("malformed material quad must fail");
    assert_eq!(error.code, StatusCode::InvalidArgument);
}

#[test]
fn compact_world_material_ffi_decodes_copies_and_deduplicates_table() {
    let table = vec![material_table_record()];
    let mut materials = vec![
        compact_material_quad_request(),
        compact_material_quad_request(),
    ];
    materials[1].p0_x = -2.0;
    materials[1].color_argb = 0xff80_4020;
    let request = whole_frame_request_with_compact_materials(&table, &materials);
    let (_generation, _target, frame, gui) =
        unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()).unwrap() };
    materials[0].p0_x = 99.0;
    assert_eq!(2, frame.material_quads.len());
    assert_eq!(
        WORLD_MATERIAL_ID_OPAQUE_TEXTURED,
        frame.material_quads[0].material_id
    );
    assert_eq!(
        WORLD_MATERIAL_TEXTURE_STONE,
        frame.material_quads[0].texture_id
    );
    assert_eq!(-1.0, frame.material_quads[0].vertices[0][0]);
    assert_eq!(-2.0, frame.material_quads[1].vertices[0][0]);
    assert_eq!(0xff80_4020, frame.material_quads[1].color_argb);
    assert_eq!([1.0, 1.0], frame.material_quads[0].uvs[2]);
    assert!(gui.is_empty());
}

#[test]
fn compact_world_material_ffi_rejects_malformed_indexes_and_mixed_legacy_payloads() {
    let table = vec![material_table_record()];
    let mut compact = vec![compact_material_quad_request()];
    compact[0].material_index = 1;
    let request = whole_frame_request_with_compact_materials(&table, &compact);
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("out-of-range compact material index must fail");
    assert_eq!(StatusCode::InvalidArgument, error.code);

    compact[0] = compact_material_quad_request();
    compact[0].byte_size -= 4;
    let request = whole_frame_request_with_compact_materials(&table, &compact);
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("malformed compact material record must fail");
    assert_eq!(StatusCode::InvalidArgument, error.code);

    let mut bad_table = vec![material_table_record()];
    bad_table[0].material_mode = 999;
    let request =
        whole_frame_request_with_compact_materials(&bad_table, &[compact_material_quad_request()]);
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("unknown compact material mode must fail");
    assert_eq!(StatusCode::UnknownEnum, error.code);

    let legacy = vec![material_quad_request()];
    let mut request =
        whole_frame_request_with_compact_materials(&table, &[compact_material_quad_request()]);
    request.world_material_quads = FfiSlice {
        ptr: legacy.as_ptr(),
        count: legacy.len() as u64,
    };
    let error = unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()) }
        .expect_err("mixed legacy and compact material payloads must fail");
    assert_eq!(StatusCode::InvalidArgument, error.code);
}

#[test]
fn semantic_gui_asset_ffi_copies_payload_memory() {
    let mut bytes = vec![7u8, 8, 9, 10];
    let assets = vec![FfiGuiAssetPayload {
        byte_size: size_of::<FfiGuiAssetPayload>() as u32,
        sprite_id: 1,
        png_bytes: FfiBytes {
            ptr: bytes.as_ptr(),
            len: bytes.len() as u64,
        },
    }];
    let request = asset_update_request(&assets);
    let (_generation, owned) =
        unsafe { decode_gui_asset_update(&request, test_capabilities()).unwrap() };
    bytes.fill(0);
    assert_eq!(vec![7u8, 8, 9, 10], owned[0].png_bytes);
}

#[test]
fn semantic_gui_asset_ffi_rejects_duplicates_and_bad_item_size() {
    let bytes = [1u8, 2, 3];
    let mut assets = vec![
        FfiGuiAssetPayload {
            byte_size: size_of::<FfiGuiAssetPayload>() as u32,
            sprite_id: 1,
            png_bytes: FfiBytes {
                ptr: bytes.as_ptr(),
                len: bytes.len() as u64,
            },
        },
        FfiGuiAssetPayload {
            byte_size: size_of::<FfiGuiAssetPayload>() as u32,
            sprite_id: 1,
            png_bytes: FfiBytes {
                ptr: bytes.as_ptr(),
                len: bytes.len() as u64,
            },
        },
    ];
    let duplicate =
        unsafe { decode_gui_asset_update(&asset_update_request(&assets), test_capabilities()) }
            .unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, duplicate.code);
    assets[1].sprite_id = 2;
    assets[1].byte_size -= 4;
    let malformed =
        unsafe { decode_gui_asset_update(&asset_update_request(&assets), test_capabilities()) }
            .unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, malformed.code);
}

#[test]
fn world_border_asset_ffi_copies_payload_memory() {
    let mut bytes = vec![11u8, 12, 13, 14];
    let request = world_border_asset_update_request(&bytes);
    let (generation, owned) =
        unsafe { decode_world_border_asset_update(&request, test_capabilities()).unwrap() };
    bytes.fill(0);
    assert_eq!(9, generation);
    assert_eq!(1, owned.texture_id);
    assert_eq!(vec![11u8, 12, 13, 14], owned.png_bytes);
}

#[test]
fn world_border_asset_ffi_rejects_bad_generation_and_size() {
    let bytes = [1u8, 2, 3];
    let mut request = world_border_asset_update_request(&bytes);
    request.generation = 0;
    let bad_generation =
        unsafe { decode_world_border_asset_update(&request, test_capabilities()) }.unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, bad_generation.code);

    request = world_border_asset_update_request(&bytes);
    request.header.byte_size -= 4;
    let bad_size =
        unsafe { decode_world_border_asset_update(&request, test_capabilities()) }.unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, bad_size.code);
}

#[test]
fn world_crack_asset_ffi_copies_payload_memory() {
    let mut bytes = vec![21u8, 22, 23, 24];
    let assets = vec![FfiWorldCrackAssetPayload {
        byte_size: size_of::<FfiWorldCrackAssetPayload>() as u32,
        stage: 4,
        png_bytes: FfiBytes {
            ptr: bytes.as_ptr(),
            len: bytes.len() as u64,
        },
    }];
    let request = world_crack_asset_update_request(&assets);
    let (generation, owned) =
        unsafe { decode_world_crack_asset_update(&request, test_capabilities()).unwrap() };
    bytes.fill(0);
    assert_eq!(9, generation);
    assert_eq!(4, owned[0].stage);
    assert_eq!(vec![21u8, 22, 23, 24], owned[0].png_bytes);
}

#[test]
fn world_crack_asset_ffi_rejects_duplicates_bad_stage_and_size() {
    let bytes = [1u8, 2, 3];
    let mut assets = vec![
        FfiWorldCrackAssetPayload {
            byte_size: size_of::<FfiWorldCrackAssetPayload>() as u32,
            stage: 4,
            png_bytes: FfiBytes {
                ptr: bytes.as_ptr(),
                len: bytes.len() as u64,
            },
        },
        FfiWorldCrackAssetPayload {
            byte_size: size_of::<FfiWorldCrackAssetPayload>() as u32,
            stage: 4,
            png_bytes: FfiBytes {
                ptr: bytes.as_ptr(),
                len: bytes.len() as u64,
            },
        },
    ];
    let duplicate = unsafe {
        decode_world_crack_asset_update(
            &world_crack_asset_update_request(&assets),
            test_capabilities(),
        )
    }
    .unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, duplicate.code);

    assets[1].stage = 10;
    let bad_stage = unsafe {
        decode_world_crack_asset_update(
            &world_crack_asset_update_request(&assets),
            test_capabilities(),
        )
    }
    .unwrap_err();
    assert_eq!(StatusCode::UnknownEnum, bad_stage.code);

    assets[1].stage = 5;
    assets[1].byte_size -= 4;
    let malformed = unsafe {
        decode_world_crack_asset_update(
            &world_crack_asset_update_request(&assets),
            test_capabilities(),
        )
    }
    .unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, malformed.code);
}

#[test]
fn world_material_asset_ffi_copies_payload_memory() {
    let mut bytes = vec![31u8, 32, 33, 34];
    let assets = vec![FfiWorldMaterialAssetPayload {
        byte_size: size_of::<FfiWorldMaterialAssetPayload>() as u32,
        texture_id: WORLD_MATERIAL_TEXTURE_STONE,
        png_bytes: FfiBytes {
            ptr: bytes.as_ptr(),
            len: bytes.len() as u64,
        },
    }];
    let request = world_material_asset_update_request(&assets);
    let (generation, owned) =
        unsafe { decode_world_material_asset_update(&request, test_capabilities()).unwrap() };
    bytes.fill(0);
    assert_eq!(9, generation);
    assert_eq!(WORLD_MATERIAL_TEXTURE_STONE, owned[0].texture_id);
    assert_eq!(vec![31u8, 32, 33, 34], owned[0].png_bytes);
}

#[test]
fn world_material_asset_ffi_rejects_duplicates_and_bad_item_size() {
    let bytes = [1u8, 2, 3];
    let mut assets = vec![
        FfiWorldMaterialAssetPayload {
            byte_size: size_of::<FfiWorldMaterialAssetPayload>() as u32,
            texture_id: WORLD_MATERIAL_TEXTURE_STONE,
            png_bytes: FfiBytes {
                ptr: bytes.as_ptr(),
                len: bytes.len() as u64,
            },
        },
        FfiWorldMaterialAssetPayload {
            byte_size: size_of::<FfiWorldMaterialAssetPayload>() as u32,
            texture_id: WORLD_MATERIAL_TEXTURE_STONE,
            png_bytes: FfiBytes {
                ptr: bytes.as_ptr(),
                len: bytes.len() as u64,
            },
        },
    ];
    let duplicate = unsafe {
        decode_world_material_asset_update(
            &world_material_asset_update_request(&assets),
            test_capabilities(),
        )
    }
    .unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, duplicate.code);

    assets[1].texture_id = 2;
    assets[1].byte_size -= 4;
    let malformed = unsafe {
        decode_world_material_asset_update(
            &world_material_asset_update_request(&assets),
            test_capabilities(),
        )
    }
    .unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, malformed.code);
}

#[test]
fn whole_frame_world_mesh_ffi_copies_and_rejects_malformed_payloads() {
    let mut instances = vec![mesh_instance()];
    let request = whole_frame_request_with_mesh_instances(&instances);
    let (_generation, _target, frame, _gui) =
        unsafe { decode_whole_frame_submit(&request, test_vulkan_capabilities()).unwrap() };
    instances[0].mesh_key = 99;
    instances[0].transform[12] = 42.0;

    assert_eq!(1, frame.mesh_instances.len());
    assert_eq!(44, frame.mesh_instances[0].mesh_key);
    assert_eq!(9, frame.mesh_instances[0].mesh_generation);
    assert_eq!(0.0, frame.mesh_instances[0].transform[12]);

    instances[0] = mesh_instance();
    instances[0].stratum = WORLD_STRATUM_TERRAIN;
    let (_generation, _target, frame, _gui) = unsafe {
        decode_whole_frame_submit(
            &whole_frame_request_with_mesh_instances(&instances),
            test_vulkan_capabilities(),
        )
        .unwrap()
    };
    assert_eq!(WORLD_STRATUM_TERRAIN, frame.mesh_instances[0].stratum);

    instances[0].stratum = 77;
    let error = unsafe {
        decode_whole_frame_submit(
            &whole_frame_request_with_mesh_instances(&instances),
            test_vulkan_capabilities(),
        )
    }
    .unwrap_err();
    assert_eq!(StatusCode::UnknownEnum, error.code);

    instances[0] = mesh_instance();
    instances[0].byte_size -= 4;
    let error = unsafe {
        decode_whole_frame_submit(
            &whole_frame_request_with_mesh_instances(&instances),
            test_vulkan_capabilities(),
        )
    }
    .unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, error.code);
}

#[test]
fn world_mesh_asset_ffi_copies_payload_memory() {
    let mut vertices = vec![mesh_vertex()];
    let mut index_bytes = vec![0u8, 0, 1, 0, 2, 0];
    let sections = vec![mesh_section(123)];
    let meshes = vec![mesh_asset(&vertices, &index_bytes, &sections)];
    let mut png = vec![41u8, 42, 43, 44];
    let textures = vec![FfiWorldMeshTextureAssetPayload {
        byte_size: size_of::<FfiWorldMeshTextureAssetPayload>() as u32,
        texture_id: 123,
        png_bytes: FfiBytes {
            ptr: png.as_ptr(),
            len: png.len() as u64,
        },
        frame_width: 0,
        frame_height: 0,
        frame_count: 1,
        frame_ticks: 1,
        animation_flags: 0,
        frame_row_size: 0,
        interpolation_policy: 0,
        reserved0: 0,
        animation_frames: FfiSlice {
            ptr: std::ptr::null(),
            count: 0,
        },
    }];
    let request = world_mesh_asset_update_request(&meshes, &textures);
    let (generation, owned_meshes, owned_textures, owned_sorted_indices) =
        unsafe { decode_world_mesh_asset_update(&request, test_capabilities()).unwrap() };

    vertices[0].x = 99.0;
    index_bytes.fill(9);
    png.fill(0);

    assert_eq!(9, generation);
    assert!(owned_sorted_indices.is_empty());
    assert_eq!(44, owned_meshes[0].mesh_key);
    assert_eq!(0.0, owned_meshes[0].vertices[0].position[0]);
    assert_eq!([0.25, 0.5], owned_meshes[0].vertices[0].shader_atlas_uv);
    assert_eq!(10232, owned_meshes[0].vertices[0].shader_block_id);
    assert_eq!(-1, owned_meshes[0].vertices[0].shader_material_type);
    assert_eq!(vec![0u8, 0, 1, 0, 2, 0], owned_meshes[0].index_bytes);
    assert_eq!(123, owned_meshes[0].sections[0].texture_id);
    assert_eq!(vec![41u8, 42, 43, 44], owned_textures[0].png_bytes);
}

#[test]
fn world_mesh_asset_ffi_rejects_duplicate_bad_index_and_bad_item_size() {
    let vertices = vec![mesh_vertex()];
    let index_bytes = vec![0u8, 0, 1, 0, 2, 0];
    let sections = vec![mesh_section(123)];
    let mut meshes = vec![
        mesh_asset(&vertices, &index_bytes, &sections),
        mesh_asset(&vertices, &index_bytes, &sections),
    ];
    let duplicate = unsafe {
        decode_world_mesh_asset_update(
            &world_mesh_asset_update_request(&meshes, &[]),
            test_capabilities(),
        )
    }
    .unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, duplicate.code);

    meshes.pop();
    meshes[0].index_type = 99;
    let bad_index = unsafe {
        decode_world_mesh_asset_update(
            &world_mesh_asset_update_request(&meshes, &[]),
            test_capabilities(),
        )
    }
    .unwrap_err();
    assert_eq!(StatusCode::UnknownEnum, bad_index.code);

    meshes[0].index_type = IndexType::U16 as u32;
    meshes[0].byte_size -= 4;
    let malformed = unsafe {
        decode_world_mesh_asset_update(
            &world_mesh_asset_update_request(&meshes, &[]),
            test_capabilities(),
        )
    }
    .unwrap_err();
    assert_eq!(StatusCode::InvalidArgument, malformed.code);
}

#[test]
fn shader_pack_source_ffi_copies_owned_utf8_files() {
    let mut pack_name = b"complementary-test".to_vec();
    let mut path = b"program/gbuffers_terrain.glsl".to_vec();
    let mut contents = b"#version 150\nvoid main() {}\n".to_vec();
    let files = [FfiShaderPackSourceFile {
        byte_size: size_of::<FfiShaderPackSourceFile>() as u32,
        reserved0: 0,
        path_utf8: FfiBytes {
            ptr: path.as_ptr(),
            len: path.len() as u64,
        },
        contents_utf8: FfiBytes {
            ptr: contents.as_ptr(),
            len: contents.len() as u64,
        },
    }];
    let request = shader_pack_source_update_request(&pack_name, &files);
    let decoded = unsafe { super::shader_pack::decode_shader_pack_source_update(&request) }
        .expect("valid source update");

    pack_name.fill(b'x');
    path.fill(b'x');
    contents.fill(b'x');

    assert_eq!("complementary-test", decoded.pack_name);
    assert_eq!(9, decoded.generation);
    assert_eq!("program/gbuffers_terrain.glsl", decoded.files[0].path);
    assert_eq!("#version 150\nvoid main() {}\n", decoded.files[0].contents);
}

#[test]
fn shader_pack_source_ffi_rejects_malformed_file_records() {
    let pack_name = b"test";
    let path = b"program/gbuffers_terrain.glsl";
    let contents = b"void main() {}";
    let mut files = [FfiShaderPackSourceFile {
        byte_size: size_of::<FfiShaderPackSourceFile>() as u32,
        reserved0: 1,
        path_utf8: FfiBytes {
            ptr: path.as_ptr(),
            len: path.len() as u64,
        },
        contents_utf8: FfiBytes {
            ptr: contents.as_ptr(),
            len: contents.len() as u64,
        },
    }];
    let request = shader_pack_source_update_request(pack_name, &files);
    let reserved = unsafe { super::shader_pack::decode_shader_pack_source_update(&request) }
        .expect_err("reserved field must be rejected");
    assert_eq!(StatusCode::InvalidArgument, reserved.code);

    files[0].reserved0 = 0;
    files[0].byte_size -= 4;
    let malformed_request = shader_pack_source_update_request(pack_name, &files);
    let malformed =
        unsafe { super::shader_pack::decode_shader_pack_source_update(&malformed_request) }
            .expect_err("truncated item must be rejected");
    assert_eq!(StatusCode::InvalidArgument, malformed.code);
}

#[test]
fn shader_pack_source_ffi_accepts_an_explicit_empty_generation() {
    let pack_name = b"disabled";
    let request = shader_pack_source_update_request(pack_name, &[]);
    let decoded = unsafe { super::shader_pack::decode_shader_pack_source_update(&request) }
        .expect("empty source generation clears a prior pack without stale reuse");
    assert_eq!("disabled", decoded.pack_name);
    assert!(decoded.files.is_empty());
}
