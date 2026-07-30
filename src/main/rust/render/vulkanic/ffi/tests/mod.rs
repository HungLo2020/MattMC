use super::*;
use crate::render::vulkanic::resources::{BackendApi, BackendFeatureFlags, BackendLimits};
use crate::render::vulkanic::world_primitive_frontend::WORLD_MATERIAL_TEXTURE_STONE;

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
