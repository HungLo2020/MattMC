//! GAL-level ABI tests: record layouts, ABI versions, capability negotiation,
//! and decoding of resource and submission batches through the bridge.

use std::mem::{align_of, size_of};
use std::ptr::NonNull;

use crate::render::bridge::*;
use crate::render::vulkanic::commands::*;
use crate::render::vulkanic::error::{GalError, StatusCode};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::handles::{Handle, HandleKind};
use crate::render::vulkanic::resources::*;
use crate::render::vulkanic::sync::SubmissionId;
use crate::render::vulkanic::test_support::{opengl_capabilities, vulkan_capabilities};

fn gal() -> VulkanicGal {
    crate::render::vulkanic::test_support::mock_gal()
}

#[test]
fn exported_struct_layouts_fit_mirrored_capacity_and_include_shadow_distance() {
    assert_eq!(size_of::<FfiStructLayout>(), 312);
    for id in 1..=117 {
        let layout = crate::render::bridge::layout::layout_for_struct(id).unwrap();
        assert_eq!(layout.header.byte_size as usize, size_of::<FfiStructLayout>());
        assert!(layout.field_count as usize <= FFI_MAX_STRUCT_LAYOUT_FIELDS);
        assert!(layout.field_offsets[..layout.field_count as usize].iter()
            .all(|offset| *offset < layout.byte_size), "struct {id}");
        assert!(layout.field_offsets[layout.field_count as usize..].iter().all(|offset| *offset == 0));
    }
    let shader = crate::render::bridge::layout::layout_for_struct(73).unwrap();
    assert_eq!(shader.field_count, 65);
    assert_eq!(shader.field_offsets[64], std::mem::offset_of!(FfiWorldShaderEnvironmentFrame,
        configured_shadow_distance_chunks) as u32);
}

fn assert_code<T>(result: Result<T, GalError>, code: StatusCode) {
    let error = match result {
        Ok(_) => panic!("operation unexpectedly succeeded"),
        Err(error) => error,
    };
    assert_eq!(error.code, code, "{error}");
}

fn buffer(label: &str, usages: Vec<BufferUsage>) -> BufferDesc {
    BufferDesc {
        label: label.to_owned(),
        size: 4096,
        memory: MemoryDomain::DeviceLocal,
        usages,
    }
}

fn view(label: &str, texture: Handle, format: TextureFormat) -> TextureViewDesc {
    TextureViewDesc {
        label: label.to_owned(),
        texture,
        format,
        base_mip: 0,
        mip_count: 1,
        base_layer: 0,
        layer_count: 1,
    }
}

fn layout_binding(
    binding: u32,
    kind: ResourceBindingKind,
    stages: PipelineStageFlags,
) -> ResourceBindingDesc {
    ResourceBindingDesc {
        binding,
        kind,
        stages,
        array_count: 1,
        optional: false,
        dynamic_offset_count: 0,
    }
}

fn shader(label: &str, stage: ShaderStage) -> ShaderModuleDesc {
    ShaderModuleDesc {
        label: label.to_owned(),
        stage,
        code_format: ShaderCodeFormat::Spirv,
        code: vec![3, 2, 35, 7],
        entry_point: "main".to_owned(),
    }
}

fn color_attachment(view: Handle) -> PassAttachment {
    PassAttachment {
        view,
        load_op: AttachmentLoadOp::Clear,
        store_op: AttachmentStoreOp::Store,
        clear_color: Some(ClearColor {
            r: 0.0,
            g: 0.0,
            b: 0.0,
            a: 1.0,
        }),
    }
}

#[test]
fn completion_poll_reports_backend_progress_without_implicit_wait() {
    let mut gal = gal();
    let buffer = gal
        .create_buffer(BufferDesc {
            label: "completion-buffer".to_string(),
            size: 16,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::HostWrite],
        })
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "completion-list".to_string(),
            operations: vec![CommandOp::HostWriteBuffer {
                buffer,
                offset: 0,
                data: vec![1; 16],
            }],
        })
        .unwrap();
    let token = gal
        .submit(SubmissionBatch {
            label: "completion-batch".to_string(),
            command_lists: vec![list],
        })
        .unwrap();

    assert_eq!(gal.latest_submission_id(), token.submission);
    assert_eq!(gal.poll_completed(), SubmissionId(0));
    assert_eq!(
        completion_result_for(token.submission, gal.poll_completed()).is_complete,
        0
    );

    gal.mock_backend_mut()
        .unwrap()
        .complete_through(token.submission);
    assert_eq!(gal.poll_completed(), token.submission);
    assert_eq!(
        completion_result_for(token.submission, gal.poll_completed()).is_complete,
        1
    );
}

#[test]
fn ffi_payload_validation_rejects_malformed_inputs() {
    unsafe {
        assert_code(
            validate_buffer_create_request(std::ptr::null()),
            super::StatusCode::NullPointer,
        );

        let request = FfiBufferCreateRequest {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<FfiBufferCreateRequest>() as u32,
            },
            label: FfiBytes {
                ptr: std::ptr::null(),
                len: 0,
            },
            size: 64,
            memory_domain: 99,
            usage_bits: 1,
        };
        assert_code(
            validate_buffer_create_request(&request),
            super::StatusCode::UnknownEnum,
        );

        let misaligned = (&request as *const _ as usize + 1) as *const FfiBufferCreateRequest;
        if (misaligned as usize) % align_of::<FfiBufferCreateRequest>() != 0 {
            assert_code(
                validate_buffer_create_request(misaligned),
                super::StatusCode::Alignment,
            );
        }

        let huge_lists = FfiSlice::<FfiCommandListRequest> {
            ptr: NonNull::<FfiCommandListRequest>::dangling().as_ptr(),
            count: u64::MAX,
        };
        let huge_request = FfiSubmissionRequest {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<FfiSubmissionRequest>() as u32,
            },
            label: FfiBytes {
                ptr: std::ptr::null(),
                len: 0,
            },
            command_lists: huge_lists,
        };
        assert_code(
            validate_submission_request(&huge_request),
            super::StatusCode::LengthOverflow,
        );

        let op_bytes = [1_u8, 2, 3, 4];
        let resource_uses = [FfiResourceUse {
            resource: FfiHandle { raw: 0 },
            stage_bits: 1,
            access_bits: 1,
        }];
        let list = FfiCommandListRequest {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<FfiCommandListRequest>() as u32,
            },
            label: FfiBytes {
                ptr: std::ptr::null(),
                len: 0,
            },
            encoded_ops: FfiBytes {
                ptr: op_bytes.as_ptr(),
                len: op_bytes.len() as u64,
            },
            resource_uses: FfiSlice {
                ptr: resource_uses.as_ptr(),
                count: resource_uses.len() as u64,
            },
        };
        let request = FfiSubmissionRequest {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<FfiSubmissionRequest>() as u32,
            },
            label: FfiBytes {
                ptr: std::ptr::null(),
                len: 0,
            },
            command_lists: FfiSlice {
                ptr: &list,
                count: 1,
            },
        };
        assert_code(
            validate_submission_request(&request),
            super::StatusCode::InvalidArgument,
        );
    }
}

fn ffi_header_for<T>() -> FfiHeader {
    FfiHeader {
        version: FFI_ABI_VERSION,
        byte_size: size_of::<T>() as u32,
    }
}

fn ffi_bytes(bytes: &[u8]) -> FfiBytes {
    FfiBytes {
        ptr: bytes.as_ptr(),
        len: bytes.len() as u64,
    }
}

fn ffi_slice<T>(items: &[T]) -> FfiSlice<T> {
    FfiSlice {
        ptr: items.as_ptr(),
        count: items.len() as u64,
    }
}

fn empty_ffi_slice<T>() -> FfiSlice<T> {
    FfiSlice {
        ptr: std::ptr::null(),
        count: 0,
    }
}

fn ffi_handle(kind: HandleKind, index: u32) -> FfiHandle {
    Handle::new(kind, index, 1).unwrap().into()
}

fn empty_resource_batch() -> FfiResourceBatch {
    FfiResourceBatch {
        header: ffi_header_for::<FfiResourceBatch>(),
        buffers: empty_ffi_slice(),
        textures: empty_ffi_slice(),
        texture_views: empty_ffi_slice(),
        samplers: empty_ffi_slice(),
        shaders: empty_ffi_slice(),
        resource_layouts: empty_ffi_slice(),
        resource_layout_bindings: empty_ffi_slice(),
        resource_sets: empty_ffi_slice(),
        resource_set_bindings: empty_ffi_slice(),
        dynamic_offsets: empty_ffi_slice(),
        pipeline_layouts: empty_ffi_slice(),
        pipeline_layout_resource_layouts: empty_ffi_slice(),
        graphics_pipelines: empty_ffi_slice(),
        compute_pipelines: empty_ffi_slice(),
        render_targets: empty_ffi_slice(),
        render_target_color_views: empty_ffi_slice(),
        render_passes: empty_ffi_slice(),
        render_pass_color_formats: empty_ffi_slice(),
        buffer_updates: empty_ffi_slice(),
        texture_updates: empty_ffi_slice(),
        destroys: empty_ffi_slice(),
        negotiated_feature_bits: 0,
    }
}

#[test]
fn ffi_v1_keeps_private_combined_texture_sampler_bindings_unavailable() {
    assert_code(
        resource_binding_kind(ResourceBindingKind::CombinedTextureSampler as u32),
        super::StatusCode::UnknownEnum,
    );
}

#[test]
fn frozen_ffi_abi_sizes_and_capability_negotiation_are_stable() {
    assert_eq!(FFI_ABI_V1_VERSION, 1);
    assert_eq!(FFI_ABI_V4_VERSION, 4);
    assert_eq!(FFI_ABI_V5_VERSION, 5);
    assert_eq!(FFI_ABI_V6_VERSION, 6);
    assert_eq!(FFI_ABI_V7_VERSION, 7);
    assert_eq!(FFI_ABI_V8_VERSION, 8);
    assert_eq!(FFI_ABI_V9_VERSION, 9);
    assert_eq!(FFI_ABI_V10_VERSION, 10);
    assert_eq!(FFI_ABI_V11_VERSION, 11);
    assert_eq!(FFI_ABI_V12_VERSION, 12);
    assert_eq!(FFI_ABI_V13_VERSION, 13);
    assert_eq!(FFI_ABI_V14_VERSION, 14);
    assert_eq!(FFI_ABI_V15_VERSION, 15);
    assert_eq!(FFI_ABI_V16_VERSION, 16);
    assert_eq!(FFI_ABI_V17_VERSION, 17);
    assert_eq!(FFI_ABI_V18_VERSION, 18);
    assert_eq!(FFI_ABI_V19_VERSION, 19);
    assert_eq!(FFI_ABI_V20_VERSION, 20);
    assert_eq!(FFI_ABI_V21_VERSION, 21);
    assert_eq!(FFI_ABI_V22_VERSION, 22);
    assert_eq!(FFI_ABI_V23_VERSION, 23);
    assert_eq!(FFI_ABI_V24_VERSION, 24);
    assert_eq!(FFI_ABI_V25_VERSION, 25);
    assert_eq!(FFI_ABI_V26_VERSION, 26);
    assert_eq!(FFI_ABI_V27_VERSION, 27);
    assert_eq!(FFI_ABI_V28_VERSION, 28);
    assert_eq!(FFI_ABI_V29_VERSION, 29);
    assert_eq!(FFI_ABI_V30_VERSION, 30);
    assert_eq!(FFI_ABI_V31_VERSION, 31);
    assert_eq!(FFI_ABI_V32_VERSION, 32);
    assert_eq!(FFI_ABI_V33_VERSION, 33);
    assert_eq!(FFI_ABI_V34_VERSION, 34);
    assert_eq!(FFI_ABI_V35_VERSION, 35);
    assert_eq!(FFI_ABI_V36_VERSION, 36);
    assert_eq!(FFI_ABI_V37_VERSION, 37);
    assert_eq!(FFI_ABI_V38_VERSION, 38);
    assert_eq!(FFI_ABI_V39_VERSION, 39);
    assert_eq!(FFI_ABI_V40_VERSION, 40);
    assert_eq!(FFI_ABI_V41_VERSION, 41);
    assert_eq!(FFI_ABI_V42_VERSION, 42);
    assert_eq!(FFI_ABI_VERSION, 78);
    assert!(!FFI_INITIAL_PRESENTATION_SUPPORTED);
    assert_eq!(size_of::<FfiHeader>(), 8);
    assert_eq!(size_of::<FfiHandle>(), 8);
    assert_eq!(size_of::<FfiBytes>(), 16);
    assert_eq!(size_of::<FfiSlice<FfiBufferDescAbi>>(), 16);
    assert_eq!(size_of::<FfiCapabilityQueryRequest>(), 24);
    assert_eq!(size_of::<FfiStatusResult>(), 136);

    let request = FfiCapabilityQueryRequest {
        header: ffi_header_for::<FfiCapabilityQueryRequest>(),
        requested_feature_bits: FfiFeatureBits::GRAPHICS | FfiFeatureBits::HOST_BUFFER_ACCESS,
        reserved0: 0,
    };
    let result = unsafe { answer_capability_query(&request, opengl_capabilities()) }
        .expect("OpenGL should negotiate its supported shared graphics subset");
    assert_eq!(result.header.version, FFI_ABI_VERSION);
    assert_eq!(result.status, super::StatusCode::Ok as i32);
    assert_ne!(result.supported_feature_bits & FfiFeatureBits::GRAPHICS, 0);
    assert_eq!(result.initial_presentation_supported, 0);

    let unsupported = FfiCapabilityQueryRequest {
        requested_feature_bits: FfiFeatureBits::GRAPHICS | FfiFeatureBits::INDIRECT_DRAW,
        ..request
    };
    assert_code(
        unsafe { answer_capability_query(&unsupported, opengl_capabilities()) },
        super::StatusCode::UnsupportedFeature,
    );

    let unknown = FfiCapabilityQueryRequest {
        requested_feature_bits: 1_u64 << 63,
        ..request
    };
    assert_code(
        unsafe { answer_capability_query(&unknown, vulkan_capabilities()) },
        super::StatusCode::UnknownEnum,
    );
}

#[test]
fn ffi_resource_batch_decodes_fixed_layout_descriptions_and_copies_inputs() {
    let mut buffer_label = b"terrain-upload".to_vec();
    let mut shader_label = b"terrain-vertex".to_vec();
    let mut entry = b"main".to_vec();
    let mut code = b"#version 450\nvoid main(){}".to_vec();
    let mut update_bytes = vec![1_u8, 2, 3, 4];
    let buffer = FfiBufferDescAbi {
        byte_size: size_of::<FfiBufferDescAbi>() as u32,
        request_id: 10,
        label: ffi_bytes(&buffer_label),
        size: 4096,
        memory_domain: MemoryDomain::Upload as u32,
        usage_bits: (1 << 0) | (1 << 5) | (1 << 8),
    };
    let shader = FfiShaderModuleDescAbi {
        byte_size: size_of::<FfiShaderModuleDescAbi>() as u32,
        request_id: 11,
        label: ffi_bytes(&shader_label),
        stage: ShaderStage::Vertex as u32,
        code_format: ShaderCodeFormat::Glsl as u32,
        code: ffi_bytes(&code),
        entry_point: ffi_bytes(&entry),
    };
    let update = FfiBufferUpdateAbi {
        byte_size: size_of::<FfiBufferUpdateAbi>() as u32,
        buffer: ffi_handle(HandleKind::Buffer, 1),
        offset: 8,
        data: ffi_bytes(&update_bytes),
    };
    let destroy = FfiDestroyDescAbi {
        byte_size: size_of::<FfiDestroyDescAbi>() as u32,
        handle: ffi_handle(HandleKind::Buffer, 2),
        expected_kind: HandleKind::Buffer as u32,
    };
    let mut batch = empty_resource_batch();
    batch.buffers = ffi_slice(std::slice::from_ref(&buffer));
    batch.shaders = ffi_slice(std::slice::from_ref(&shader));
    batch.buffer_updates = ffi_slice(std::slice::from_ref(&update));
    batch.destroys = ffi_slice(std::slice::from_ref(&destroy));
    batch.negotiated_feature_bits = FfiFeatureBits::GRAPHICS | FfiFeatureBits::HOST_BUFFER_ACCESS;

    let owned = unsafe { decode_resource_batch(&batch, opengl_capabilities()) }
        .expect("resource batch should decode into owned GAL descriptions");
    buffer_label.fill(b'X');
    shader_label.fill(b'Y');
    entry.fill(b'Z');
    code.fill(0);
    update_bytes.fill(9);

    assert_eq!(owned.buffers[0].request_id, 10);
    assert_eq!(owned.buffers[0].desc.label, "terrain-upload");
    assert_eq!(owned.shaders[0].desc.label, "terrain-vertex");
    assert_eq!(owned.shaders[0].desc.entry_point, "main");
    assert!(owned.shaders[0].desc.code.starts_with(b"#version 450"));
    assert_eq!(owned.buffer_updates[0].data, vec![1, 2, 3, 4]);
    assert_eq!(owned.destroys[0].1, HandleKind::Buffer);

    let serialized = serialize_resource_batch_canonical(&owned);
    let reparsed = serialize_resource_batch_canonical(&owned);
    assert_eq!(serialized, reparsed);
}

#[test]
fn ffi_resource_batch_covers_layout_sets_pipelines_targets_and_passes() {
    let layout_binding = FfiResourceBindingDescAbi {
        byte_size: size_of::<FfiResourceBindingDescAbi>() as u32,
        binding: 0,
        kind: ResourceBindingKind::UniformBuffer as u32,
        stage_bits: PipelineStageFlags::DRAW.0,
        array_count: 2,
        optional: 1,
        dynamic_offset_count: 1,
    };
    let layout_label = b"layout";
    let layout = FfiResourceLayoutDescAbi {
        byte_size: size_of::<FfiResourceLayoutDescAbi>() as u32,
        request_id: 20,
        label: ffi_bytes(layout_label),
        bindings: FfiRange {
            offset: 0,
            count: 1,
        },
    };
    let offsets = [64_u64];
    let set_binding = FfiResourceBindingAbi {
        byte_size: size_of::<FfiResourceBindingAbi>() as u32,
        binding: 0,
        array_index: 1,
        resource: ffi_handle(HandleKind::Buffer, 1),
        kind: ResourceBindingKind::UniformBuffer as u32,
        access_bits: AccessFlags::READ.0,
        dynamic_offsets: FfiRange {
            offset: 0,
            count: 1,
        },
    };
    let set = FfiResourceSetDescAbi {
        byte_size: size_of::<FfiResourceSetDescAbi>() as u32,
        request_id: 21,
        label: ffi_bytes(b"set"),
        layout: ffi_handle(HandleKind::ResourceLayout, 1),
        bindings: FfiRange {
            offset: 0,
            count: 1,
        },
    };
    let pipeline_layouts = [ffi_handle(HandleKind::ResourceLayout, 1)];
    let pipeline_layout = FfiPipelineLayoutDescAbi {
        byte_size: size_of::<FfiPipelineLayoutDescAbi>() as u32,
        request_id: 22,
        label: ffi_bytes(b"pipeline-layout"),
        resource_layouts: FfiRange {
            offset: 0,
            count: 1,
        },
    };
    let color_formats = [TextureFormat::Rgba8Unorm as u32];
    let pipeline = FfiGraphicsPipelineDescAbi {
        byte_size: size_of::<FfiGraphicsPipelineDescAbi>() as u32,
        request_id: 23,
        label: ffi_bytes(b"pipeline"),
        layout: ffi_handle(HandleKind::PipelineLayout, 1),
        vertex_shader: ffi_handle(HandleKind::ShaderModule, 1),
        fragment_shader: ffi_handle(HandleKind::ShaderModule, 2),
        topology: PrimitiveTopology::Triangles as u32,
        cull_mode: CullMode::Back as u32,
        blend: BlendMode::Alpha as u32,
        depth_compare: CompareOp::LessOrEqual as u32,
        color_formats: FfiRange {
            offset: 0,
            count: 1,
        },
        depth_format: TextureFormat::Depth32Float as u32,
    };
    let color_views = [ffi_handle(HandleKind::TextureView, 1)];
    let target = FfiRenderTargetDescAbi {
        byte_size: size_of::<FfiRenderTargetDescAbi>() as u32,
        request_id: 24,
        label: ffi_bytes(b"target"),
        color_views: FfiRange {
            offset: 0,
            count: 1,
        },
        depth_stencil_view: ffi_handle(HandleKind::TextureView, 2),
        extent: FfiExtent3d {
            width: 64,
            height: 64,
            depth: 1,
        },
    };
    let pass = FfiRenderPassDescAbi {
        byte_size: size_of::<FfiRenderPassDescAbi>() as u32,
        request_id: 25,
        label: ffi_bytes(b"pass"),
        target: ffi_handle(HandleKind::RenderTarget, 1),
        color_formats: FfiRange {
            offset: 0,
            count: 1,
        },
        depth_format: TextureFormat::Depth32Float as u32,
    };
    let mut batch = empty_resource_batch();
    batch.resource_layouts = ffi_slice(std::slice::from_ref(&layout));
    batch.resource_layout_bindings = ffi_slice(std::slice::from_ref(&layout_binding));
    batch.resource_sets = ffi_slice(std::slice::from_ref(&set));
    batch.resource_set_bindings = ffi_slice(std::slice::from_ref(&set_binding));
    batch.dynamic_offsets = ffi_slice(&offsets);
    batch.pipeline_layouts = ffi_slice(std::slice::from_ref(&pipeline_layout));
    batch.pipeline_layout_resource_layouts = ffi_slice(&pipeline_layouts);
    batch.graphics_pipelines = ffi_slice(std::slice::from_ref(&pipeline));
    batch.render_targets = ffi_slice(std::slice::from_ref(&target));
    batch.render_target_color_views = ffi_slice(&color_views);
    batch.render_passes = ffi_slice(std::slice::from_ref(&pass));
    batch.render_pass_color_formats = ffi_slice(&color_formats);
    batch.negotiated_feature_bits = FfiFeatureBits::GRAPHICS
        | FfiFeatureBits::DESCRIPTOR_ARRAYS
        | FfiFeatureBits::OPTIONAL_BINDINGS
        | FfiFeatureBits::DYNAMIC_BUFFER_OFFSETS
        | FfiFeatureBits::UNIFORM_BUFFERS
        | FfiFeatureBits::MULTIPLE_COLOR_ATTACHMENTS
        | FfiFeatureBits::BLENDED_PASS;

    let owned = unsafe { decode_resource_batch(&batch, opengl_capabilities()) }
        .expect("layout/set/pipeline/pass ABI should decode");
    assert_eq!(owned.resource_layouts[0].desc.bindings[0].array_count, 2);
    assert_eq!(
        owned.resource_sets[0].desc.bindings[0].dynamic_offsets,
        vec![64]
    );
    assert_eq!(owned.pipeline_layouts[0].desc.resource_layouts.len(), 1);
    assert_eq!(owned.graphics_pipelines[0].desc.blend, BlendMode::Alpha);
    assert_eq!(owned.render_targets[0].desc.color_views.len(), 1);
    assert_eq!(
        owned.render_passes[0].desc.depth_format,
        Some(TextureFormat::Depth32Float)
    );
}

#[test]
fn ffi_resource_batch_counts_binding_tables_in_aggregate_limit() {
    let dynamic_offsets = vec![0u64; FFI_MAX_BATCH_ITEMS / 2];
    let pipeline_layout_resources = vec![FfiHandle { raw: 0 }; FFI_MAX_BATCH_ITEMS / 2 + 1];
    let mut batch = empty_resource_batch();
    batch.dynamic_offsets = ffi_slice(&dynamic_offsets);
    batch.pipeline_layout_resource_layouts = ffi_slice(&pipeline_layout_resources);
    let error = unsafe { decode_resource_batch(&batch, opengl_capabilities()) }
        .expect_err("resource binding tables must share the aggregate batch bound");
    assert_eq!(super::StatusCode::LengthOverflow, error.code);
}

#[test]
fn ffi_submission_batch_decodes_complete_commands_and_copies_inline_uploads() {
    let color_attachment = FfiPassAttachmentAbi {
        byte_size: size_of::<FfiPassAttachmentAbi>() as u32,
        view: ffi_handle(HandleKind::TextureView, 1),
        load_op: AttachmentLoadOp::Clear as u32,
        store_op: AttachmentStoreOp::Store as u32,
        has_clear_color: 1,
        clear_color: FfiClearColor {
            r: 0.25,
            g: 0.5,
            b: 0.75,
            a: 1.0,
        },
    };
    let mut upload = vec![1_u8, 3, 5, 7];
    let ops = [
        FfiCommandOpAbi {
            byte_size: size_of::<FfiCommandOpAbi>() as u32,
            op_kind: FfiCommandOpKind::BeginPass as u32,
            primary: ffi_handle(HandleKind::RenderPass, 1),
            secondary: ffi_handle(HandleKind::RenderTarget, 1),
            tertiary: FfiHandle { raw: 0 },
            set_index: 0,
            slot: 0,
            offset: 0,
            size: 0,
            count0: 0,
            count1: 0,
            count2: 0,
            colors: FfiRange {
                offset: 0,
                count: 1,
            },
            depth_stencil: FfiRange {
                offset: 0,
                count: 0,
            },
            copy_region: FfiRange {
                offset: 0,
                count: 0,
            },
            barrier: FfiRange {
                offset: 0,
                count: 0,
            },
            inline_bytes: FfiBytes {
                ptr: std::ptr::null(),
                len: 0,
            },
            subresources: FfiTextureSubresourceRange::default(),
        },
        FfiCommandOpAbi {
            byte_size: size_of::<FfiCommandOpAbi>() as u32,
            op_kind: FfiCommandOpKind::BindGraphicsPipeline as u32,
            primary: ffi_handle(HandleKind::GraphicsPipeline, 1),
            secondary: FfiHandle { raw: 0 },
            tertiary: FfiHandle { raw: 0 },
            set_index: 0,
            slot: 0,
            offset: 0,
            size: 0,
            count0: 0,
            count1: 0,
            count2: 0,
            colors: FfiRange {
                offset: 0,
                count: 0,
            },
            depth_stencil: FfiRange {
                offset: 0,
                count: 0,
            },
            copy_region: FfiRange {
                offset: 0,
                count: 0,
            },
            barrier: FfiRange {
                offset: 0,
                count: 0,
            },
            inline_bytes: FfiBytes {
                ptr: std::ptr::null(),
                len: 0,
            },
            subresources: FfiTextureSubresourceRange::default(),
        },
        FfiCommandOpAbi {
            byte_size: size_of::<FfiCommandOpAbi>() as u32,
            op_kind: FfiCommandOpKind::HostWriteBuffer as u32,
            primary: ffi_handle(HandleKind::Buffer, 1),
            secondary: FfiHandle { raw: 0 },
            tertiary: FfiHandle { raw: 0 },
            set_index: 0,
            slot: 0,
            offset: 16,
            size: 0,
            count0: 0,
            count1: 0,
            count2: 0,
            colors: FfiRange {
                offset: 0,
                count: 0,
            },
            depth_stencil: FfiRange {
                offset: 0,
                count: 0,
            },
            copy_region: FfiRange {
                offset: 0,
                count: 0,
            },
            barrier: FfiRange {
                offset: 0,
                count: 0,
            },
            inline_bytes: ffi_bytes(&upload),
            subresources: FfiTextureSubresourceRange::default(),
        },
        FfiCommandOpAbi {
            byte_size: size_of::<FfiCommandOpAbi>() as u32,
            op_kind: FfiCommandOpKind::DrawIndexed as u32,
            primary: FfiHandle { raw: 0 },
            secondary: FfiHandle { raw: 0 },
            tertiary: FfiHandle { raw: 0 },
            set_index: 0,
            slot: 0,
            offset: 0,
            size: 0,
            count0: 6,
            count1: 1,
            count2: 0,
            colors: FfiRange {
                offset: 0,
                count: 0,
            },
            depth_stencil: FfiRange {
                offset: 0,
                count: 0,
            },
            copy_region: FfiRange {
                offset: 0,
                count: 0,
            },
            barrier: FfiRange {
                offset: 0,
                count: 0,
            },
            inline_bytes: FfiBytes {
                ptr: std::ptr::null(),
                len: 0,
            },
            subresources: FfiTextureSubresourceRange::default(),
        },
        FfiCommandOpAbi {
            byte_size: size_of::<FfiCommandOpAbi>() as u32,
            op_kind: FfiCommandOpKind::EndPass as u32,
            primary: FfiHandle { raw: 0 },
            secondary: FfiHandle { raw: 0 },
            tertiary: FfiHandle { raw: 0 },
            set_index: 0,
            slot: 0,
            offset: 0,
            size: 0,
            count0: 0,
            count1: 0,
            count2: 0,
            colors: FfiRange {
                offset: 0,
                count: 0,
            },
            depth_stencil: FfiRange {
                offset: 0,
                count: 0,
            },
            copy_region: FfiRange {
                offset: 0,
                count: 0,
            },
            barrier: FfiRange {
                offset: 0,
                count: 0,
            },
            inline_bytes: FfiBytes {
                ptr: std::ptr::null(),
                len: 0,
            },
            subresources: FfiTextureSubresourceRange::default(),
        },
    ];
    let list_label = b"list";
    let list = FfiCommandListAbi {
        byte_size: size_of::<FfiCommandListAbi>() as u32,
        label: ffi_bytes(list_label),
        operations: FfiRange {
            offset: 0,
            count: ops.len() as u64,
        },
    };
    let batch = FfiSubmissionBatchAbi {
        header: ffi_header_for::<FfiSubmissionBatchAbi>(),
        label: ffi_bytes(b"submission"),
        command_lists: ffi_slice(std::slice::from_ref(&list)),
        operations: ffi_slice(&ops),
        pass_attachments: ffi_slice(std::slice::from_ref(&color_attachment)),
        copy_regions: empty_ffi_slice(),
        barriers: empty_ffi_slice(),
        negotiated_feature_bits: FfiFeatureBits::GRAPHICS | FfiFeatureBits::HOST_BUFFER_ACCESS,
    };

    let decoded = unsafe { decode_submission_batch(&batch, opengl_capabilities()) }
        .expect("submission batch should decode");
    upload.fill(9);
    assert_eq!(decoded.label, "submission");
    assert_eq!(decoded.command_lists[0].operations.len(), ops.len());
    match &decoded.command_lists[0].operations[2] {
        CommandOp::HostWriteBuffer { data, offset, .. } => {
            assert_eq!(*offset, 16);
            assert_eq!(data, &vec![1, 3, 5, 7]);
        }
        other => panic!("unexpected decoded op {other:?}"),
    }
    assert_eq!(
        serialize_submission_batch_canonical(&decoded),
        serialize_submission_batch_canonical(&decoded)
    );
}

#[test]
fn ffi_rejects_malformed_ranges_unknown_enums_and_unsupported_features() {
    let bad_buffer = FfiBufferDescAbi {
        byte_size: size_of::<FfiBufferDescAbi>() as u32,
        request_id: 1,
        label: ffi_bytes(b"bad"),
        size: 4,
        memory_domain: 77,
        usage_bits: 1,
    };
    let mut batch = empty_resource_batch();
    batch.buffers = ffi_slice(std::slice::from_ref(&bad_buffer));
    assert_code(
        unsafe { decode_resource_batch(&batch, vulkan_capabilities()) },
        super::StatusCode::UnknownEnum,
    );

    let storage_texture = FfiTextureDescAbi {
        byte_size: size_of::<FfiTextureDescAbi>() as u32,
        request_id: 2,
        label: ffi_bytes(b"storage-image"),
        dimension: TextureDimension::D2 as u32,
        format: TextureFormat::Rgba8Unorm as u32,
        extent: FfiExtent3d {
            width: 4,
            height: 4,
            depth: 1,
        },
        mip_levels: 1,
        array_layers: 1,
        usage_bits: 1 << 1,
    };
    let mut batch = empty_resource_batch();
    batch.textures = ffi_slice(std::slice::from_ref(&storage_texture));
    assert_code(
        unsafe { decode_resource_batch(&batch, opengl_capabilities()) },
        super::StatusCode::UnsupportedFeature,
    );

    let private_d3_texture = FfiTextureDescAbi {
        byte_size: size_of::<FfiTextureDescAbi>() as u32,
        request_id: 3,
        label: ffi_bytes(b"private-d3-volume"),
        dimension: TextureDimension::D3 as u32,
        format: TextureFormat::Rgba16Float as u32,
        extent: FfiExtent3d {
            width: 4,
            height: 4,
            depth: 4,
        },
        mip_levels: 1,
        array_layers: 1,
        usage_bits: 1 << 0,
    };
    let mut batch = empty_resource_batch();
    batch.textures = ffi_slice(std::slice::from_ref(&private_d3_texture));
    let error = unsafe { decode_resource_batch(&batch, vulkan_capabilities()) }
        .expect_err("stable Java FFI must not admit private D3 resources");
    assert_eq!(error.code, super::StatusCode::UnsupportedFeature);
    assert!(error.message.contains("internal-only"));

    let op = FfiCommandOpAbi {
        byte_size: size_of::<FfiCommandOpAbi>() as u32,
        op_kind: FfiCommandOpKind::DrawIndirect as u32,
        primary: ffi_handle(HandleKind::Buffer, 1),
        secondary: FfiHandle { raw: 0 },
        tertiary: FfiHandle { raw: 0 },
        set_index: 0,
        slot: 0,
        offset: 0,
        size: 0,
        count0: 1,
        count1: 0,
        count2: 0,
        colors: FfiRange {
            offset: 0,
            count: 0,
        },
        depth_stencil: FfiRange {
            offset: 0,
            count: 0,
        },
        copy_region: FfiRange {
            offset: 0,
            count: 0,
        },
        barrier: FfiRange {
            offset: 0,
            count: 0,
        },
        inline_bytes: FfiBytes {
            ptr: std::ptr::null(),
            len: 0,
        },
        subresources: FfiTextureSubresourceRange::default(),
    };
    let list = FfiCommandListAbi {
        byte_size: size_of::<FfiCommandListAbi>() as u32,
        label: ffi_bytes(b"list"),
        operations: FfiRange {
            offset: 0,
            count: 1,
        },
    };
    let submission = FfiSubmissionBatchAbi {
        header: ffi_header_for::<FfiSubmissionBatchAbi>(),
        label: ffi_bytes(b"submission"),
        command_lists: ffi_slice(std::slice::from_ref(&list)),
        operations: ffi_slice(std::slice::from_ref(&op)),
        pass_attachments: empty_ffi_slice(),
        copy_regions: empty_ffi_slice(),
        barriers: empty_ffi_slice(),
        negotiated_feature_bits: FfiFeatureBits::GRAPHICS,
    };
    assert_code(
        unsafe { decode_submission_batch(&submission, opengl_capabilities()) },
        super::StatusCode::UnsupportedFeature,
    );

    let malformed_list = FfiCommandListAbi {
        operations: FfiRange {
            offset: 10,
            count: 1,
        },
        ..list
    };
    let malformed = FfiSubmissionBatchAbi {
        command_lists: ffi_slice(std::slice::from_ref(&malformed_list)),
        ..submission
    };
    assert_code(
        unsafe { decode_submission_batch(&malformed, vulkan_capabilities()) },
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn ffi_large_batches_and_partial_failures_are_deterministic() {
    let mut ops = Vec::new();
    for _ in 0..128 {
        ops.push(FfiCommandOpAbi {
            byte_size: size_of::<FfiCommandOpAbi>() as u32,
            op_kind: FfiCommandOpKind::Draw as u32,
            primary: FfiHandle { raw: 0 },
            secondary: FfiHandle { raw: 0 },
            tertiary: FfiHandle { raw: 0 },
            set_index: 0,
            slot: 0,
            offset: 0,
            size: 0,
            count0: 3,
            count1: 1,
            count2: 0,
            colors: FfiRange {
                offset: 0,
                count: 0,
            },
            depth_stencil: FfiRange {
                offset: 0,
                count: 0,
            },
            copy_region: FfiRange {
                offset: 0,
                count: 0,
            },
            barrier: FfiRange {
                offset: 0,
                count: 0,
            },
            inline_bytes: FfiBytes {
                ptr: std::ptr::null(),
                len: 0,
            },
            subresources: FfiTextureSubresourceRange::default(),
        });
    }
    let list = FfiCommandListAbi {
        byte_size: size_of::<FfiCommandListAbi>() as u32,
        label: ffi_bytes(b"large-list"),
        operations: FfiRange {
            offset: 0,
            count: ops.len() as u64,
        },
    };
    let submission = FfiSubmissionBatchAbi {
        header: ffi_header_for::<FfiSubmissionBatchAbi>(),
        label: ffi_bytes(b"large"),
        command_lists: ffi_slice(std::slice::from_ref(&list)),
        operations: ffi_slice(&ops),
        pass_attachments: empty_ffi_slice(),
        copy_regions: empty_ffi_slice(),
        barriers: empty_ffi_slice(),
        negotiated_feature_bits: FfiFeatureBits::GRAPHICS,
    };
    let decoded = unsafe { decode_submission_batch(&submission, opengl_capabilities()) }
        .expect("large but bounded command batch should decode");
    assert_eq!(decoded.command_lists[0].operations.len(), 128);

    let good = FfiBufferDescAbi {
        byte_size: size_of::<FfiBufferDescAbi>() as u32,
        request_id: 1,
        label: ffi_bytes(b"good"),
        size: 64,
        memory_domain: MemoryDomain::Upload as u32,
        usage_bits: 1,
    };
    let bad = FfiShaderModuleDescAbi {
        byte_size: size_of::<FfiShaderModuleDescAbi>() as u32,
        request_id: 2,
        label: ffi_bytes(b"bad"),
        stage: 999,
        code_format: ShaderCodeFormat::Glsl as u32,
        code: ffi_bytes(b"void main(){}"),
        entry_point: ffi_bytes(b"main"),
    };
    let mut batch = empty_resource_batch();
    batch.buffers = ffi_slice(std::slice::from_ref(&good));
    batch.shaders = ffi_slice(std::slice::from_ref(&bad));
    let first = unsafe { decode_resource_batch(&batch, vulkan_capabilities()) };
    let second = unsafe { decode_resource_batch(&batch, vulkan_capabilities()) };
    assert_code(first, super::StatusCode::UnknownEnum);
    assert_code(second, super::StatusCode::UnknownEnum);
}

#[test]
fn ffi_abi_fuzz_rejects_unknown_versions_enums_and_lengths() {
    let base = FfiCapabilityQueryRequest {
        header: ffi_header_for::<FfiCapabilityQueryRequest>(),
        requested_feature_bits: FfiFeatureBits::GRAPHICS,
        reserved0: 0,
    };
    for version in [
        0,
        FFI_ABI_V27_VERSION,
        FFI_ABI_V28_VERSION,
        FFI_ABI_VERSION + 1,
        u32::MAX,
    ] {
        let mut request = base;
        request.header.version = version;
        assert_code(
            unsafe { answer_capability_query(&request, vulkan_capabilities()) },
            super::StatusCode::InvalidArgument,
        );
    }
    let mut v1_request = base;
    v1_request.header.version = FFI_ABI_V1_VERSION;
    assert!(unsafe { answer_capability_query(&v1_request, vulkan_capabilities()) }.is_ok());

    let tiny_label = [b'a'; FFI_MAX_LABEL_BYTES + 1];
    let buffer = FfiBufferDescAbi {
        byte_size: size_of::<FfiBufferDescAbi>() as u32,
        request_id: 1,
        label: ffi_bytes(&tiny_label),
        size: 64,
        memory_domain: MemoryDomain::Upload as u32,
        usage_bits: 1,
    };
    let mut batch = empty_resource_batch();
    batch.buffers = ffi_slice(std::slice::from_ref(&buffer));
    assert_code(
        unsafe { decode_resource_batch(&batch, vulkan_capabilities()) },
        super::StatusCode::LengthOverflow,
    );

    let huge_ops = FfiSlice::<FfiCommandOpAbi> {
        ptr: NonNull::<FfiCommandOpAbi>::dangling().as_ptr(),
        count: u64::MAX,
    };
    let list = FfiCommandListAbi {
        byte_size: size_of::<FfiCommandListAbi>() as u32,
        label: ffi_bytes(b"list"),
        operations: FfiRange {
            offset: 0,
            count: 1,
        },
    };
    let submission = FfiSubmissionBatchAbi {
        header: ffi_header_for::<FfiSubmissionBatchAbi>(),
        label: ffi_bytes(b"submission"),
        command_lists: ffi_slice(std::slice::from_ref(&list)),
        operations: huge_ops,
        pass_attachments: empty_ffi_slice(),
        copy_regions: empty_ffi_slice(),
        barriers: empty_ffi_slice(),
        negotiated_feature_bits: 0,
    };
    assert_code(
        unsafe { decode_submission_batch(&submission, vulkan_capabilities()) },
        super::StatusCode::LengthOverflow,
    );
}

#[test]
fn ffi_status_completion_and_retirement_results_are_structured() {
    let error = GalError::unsupported_feature("backend does not support indirect draw");
    let status = status_result_from_error(&error);
    assert_eq!(status.header.version, FFI_ABI_VERSION);
    assert_eq!(status.status, super::StatusCode::UnsupportedFeature as i32);
    assert_eq!(status.error_domain, ErrorDomain::Resource as u32);
    assert_eq!(
        status.unsupported_feature,
        BackendFeature::IndirectDraw as u32
    );

    let bad_completion = FfiCompletionQueryRequest {
        header: ffi_header_for::<FfiCompletionQueryRequest>(),
        submission_id: 0,
    };
    assert_code(
        unsafe { validate_completion_query(&bad_completion) },
        super::StatusCode::InvalidArgument,
    );
    let good_completion = FfiCompletionQueryRequest {
        submission_id: 7,
        ..bad_completion
    };
    let decoded = unsafe { validate_completion_query(&good_completion) }
        .expect("non-zero completion query should decode");
    assert_eq!(decoded.submission_id, 7);
    let incomplete = completion_result_for(SubmissionId(7), SubmissionId(6));
    let complete = completion_result_for(SubmissionId(7), SubmissionId(7));
    assert_eq!(incomplete.is_complete, 0);
    assert_eq!(complete.is_complete, 1);

    let handles = [
        ffi_handle(HandleKind::Buffer, 1),
        ffi_handle(HandleKind::Texture, 2),
    ];
    let retirement = FfiRetirementBatch {
        header: ffi_header_for::<FfiRetirementBatch>(),
        completed_submission_id: 9,
        handles: ffi_slice(&handles),
    };
    let (completed, owned) =
        unsafe { decode_retirement_batch(&retirement) }.expect("retirement batch should decode");
    assert_eq!(completed, SubmissionId(9));
    assert_eq!(owned.len(), 2);
    assert_eq!(owned[0].kind(), Some(HandleKind::Buffer));
}

#[test]
fn failed_resource_batch_releases_all_successful_creates() {
    let mut context = BridgeContext {
        gal: gal(), windowed_presenter: false, gui_frontend: Default::default(),
        world_primitive_frontend: Default::default(), ffi_calls: 0, ffi_input_bytes: 0,
        ffi_output_bytes: 0, last_error: String::new(), frame_targets: Default::default(),
        stale_frame_targets: Vec::new(),
    };
    let make = |request_id, size| FfiOwnedCreate { request_id, desc: BufferDesc {
        label: "batch-rollback".into(), size, memory: MemoryDomain::Upload, usages: vec![BufferUsage::Uniform] } };
    let batch = FfiOwnedResourceBatch { buffers: vec![make(1, 64), make(2, 0)], ..Default::default() };
    assert!(execute_resource_batch(&mut context, batch).is_err());
    assert_eq!(context.gal.metrics().resource_creates, context.gal.metrics().resource_destroys);
}
