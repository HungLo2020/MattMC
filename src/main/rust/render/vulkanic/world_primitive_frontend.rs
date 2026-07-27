use super::commands::{
    AttachmentLoadOp, AttachmentStoreOp, CommandOp, PassAttachment, ResourceBarrier,
    SubmissionBatch, TextureOrigin3d, TextureUsageState,
};
use super::error::{GalError, GalResult, StatusCode};
use super::gal::VulkanicGal;
use super::handles::Handle;
use super::resources::{
    AccessFlags, BlendMode, BufferDesc, BufferUsage, ColorFormat, CompareOp, Extent3d,
    GraphicsPipelineDesc, MemoryDomain, PipelineLayoutDesc, PipelineStageFlags, PrimitiveTopology,
    QueueClass, RenderPassDesc, ResourceBinding, ResourceBindingDesc, ResourceBindingKind,
    ResourceLayoutDesc, ResourceSetDesc, SamplerAddressMode, SamplerDesc, SamplerFilter,
    ShaderCodeFormat, ShaderModuleDesc, ShaderStage, TextureDesc, TextureDimension, TextureFormat,
    TextureUsage, TextureViewDesc,
};
use super::{BufferImageCopyRegion, CommandList, CommandListDesc, CullMode};

pub const WORLD_MAX_LINE_SEGMENTS: usize = 512;
pub const WORLD_MAX_CRACK_QUADS: usize = 512;
pub const WORLD_DEPTH_POLICY_DISABLED: u32 = 0;
pub const WORLD_DEPTH_POLICY_TEST_WRITE: u32 = 1;
pub const WORLD_STRATUM_BLOCK_OUTLINE: u32 = 100;
pub const WORLD_STRATUM_BLOCK_BREAKING_CRACK: u32 = 90;
const WORLD_LINE_HEADER_BYTES: usize = 144;
const WORLD_LINE_SEGMENT_BYTES: usize = 48;
const WORLD_LINE_UNIFORM_BYTES: u64 =
    (WORLD_LINE_HEADER_BYTES + WORLD_MAX_LINE_SEGMENTS * WORLD_LINE_SEGMENT_BYTES) as u64;
const WORLD_CRACK_HEADER_BYTES: usize = 144;
const WORLD_CRACK_QUAD_BYTES: usize = 96;
const WORLD_CRACK_UNIFORM_BYTES: u64 =
    (WORLD_CRACK_HEADER_BYTES + WORLD_MAX_CRACK_QUADS * WORLD_CRACK_QUAD_BYTES) as u64;
const CRACK_STAGE_COUNT: u32 = 10;
const CRACK_STAGE_SIZE: u32 = 16;

const WORLD_LINE_VERTEX_SHADER_VULKAN: &[u8] = br#"#version 450
struct LineSegment {
    vec4 start;
    vec4 end;
    vec4 color;
};
layout(set = 0, binding = 0, std140) uniform WorldLineBatch {
    mat4 view;
    mat4 projection;
    vec4 viewport;
    LineSegment segments[512];
};
layout(location = 0) flat out vec4 v_color;
void main() {
    int segment = gl_VertexIndex / 2;
    int endpoint = gl_VertexIndex & 1;
    vec3 position = endpoint == 0 ? segments[segment].start.xyz : segments[segment].end.xyz;
    gl_Position = projection * view * vec4(position, 1.0);
    v_color = segments[segment].color;
}
"#;

const WORLD_LINE_FRAGMENT_SHADER_VULKAN: &[u8] = br#"#version 450
layout(location = 0) flat in vec4 v_color;
layout(location = 0) out vec4 out_color;
void main() {
    out_color = v_color;
}
"#;

const WORLD_CRACK_VERTEX_SHADER_VULKAN: &[u8] = br#"#version 450
struct CrackQuad {
    vec4 p0;
    vec4 p1;
    vec4 p2;
    vec4 p3;
    vec4 uv_region;
    vec4 color;
};
layout(set = 0, binding = 0, std140) uniform CrackQuadBatch {
    mat4 view;
    mat4 projection;
    vec4 viewport;
    CrackQuad quads[512];
};
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec4 v_color;
const vec2 corner[6] = vec2[6](
    vec2(0.0, 0.0),
    vec2(1.0, 0.0),
    vec2(1.0, 1.0),
    vec2(1.0, 1.0),
    vec2(0.0, 1.0),
    vec2(0.0, 0.0)
);
void main() {
    int vertex = gl_VertexIndex;
    CrackQuad quad = quads[gl_InstanceIndex];
    vec2 c = corner[vertex];
    vec3 top = mix(quad.p0.xyz, quad.p1.xyz, c.x);
    vec3 bottom = mix(quad.p3.xyz, quad.p2.xyz, c.x);
    vec3 position = mix(top, bottom, c.y);
    gl_Position = projection * view * vec4(position, 1.0);
    v_uv = vec2(
        quad.uv_region.x + c.x * quad.uv_region.z,
        quad.uv_region.y + c.y * quad.uv_region.w
    );
    v_color = quad.color;
}
"#;

const WORLD_CRACK_FRAGMENT_SHADER_VULKAN: &[u8] = br#"#version 450
layout(set = 0, binding = 1) uniform texture2D Tex0;
layout(set = 0, binding = 2) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 color = texture(sampler2D(Tex0, Samp0), v_uv) * v_color;
    if (color.a <= 0.0) {
        discard;
    }
    out_color = color;
}
"#;

#[derive(Clone, Copy, Debug)]
pub struct WorldLineSegmentRequest {
    pub stratum: u32,
    pub style: u32,
    pub depth_policy: u32,
    pub color_argb: u32,
    pub line_width: f32,
    pub start: [f32; 3],
    pub end: [f32; 3],
    pub viewport_width: u32,
    pub viewport_height: u32,
}

#[derive(Clone, Debug)]
pub struct WorldCrackQuadRequest {
    pub stratum: u32,
    pub stage: u32,
    pub depth_policy: u32,
    pub color_argb: u32,
    pub vertices: [[f32; 3]; 4],
    pub viewport_width: u32,
    pub viewport_height: u32,
}

#[derive(Clone, Debug)]
pub struct WorldPrimitiveFrame {
    pub frame_id: u64,
    pub correlation_id: u64,
    pub viewport_width: u32,
    pub viewport_height: u32,
    pub view_matrix: [f32; 16],
    pub projection_matrix: [f32; 16],
    pub segments: Vec<WorldLineSegmentRequest>,
    pub crack_quads: Vec<WorldCrackQuadRequest>,
}

#[derive(Clone, Debug, Default)]
pub struct WorldPrimitiveSubmitStats {
    pub submission_id: u64,
    pub segment_count: u64,
    pub vertex_count: u64,
    pub primitive_batch_count: u64,
    pub cache_hits: u64,
    pub cache_misses: u64,
    pub outline_cache_hits: u64,
    pub outline_cache_misses: u64,
    pub crack_cache_hits: u64,
    pub crack_cache_misses: u64,
    pub resource_creates: u64,
    pub depth_attachment_creates: u64,
    pub depth_attachment_reuses: u64,
    pub depth_attachment_retires: u64,
    pub command_lists: u64,
    pub command_ops: u64,
    pub world_draws: u64,
    pub crack_quad_count: u64,
    pub crack_batch_count: u64,
    pub crack_draw_count: u64,
}

struct WorldLineResources {
    uniform_buffer: Handle,
    vertex_shader: Handle,
    fragment_shader: Handle,
    resource_layout: Handle,
    resource_set: Handle,
    pipeline_layout: Handle,
    pipeline_depth_disabled: Handle,
    pipeline_depth_test_write: Handle,
}

impl WorldLineResources {
    fn handles_in_destroy_order(&self) -> [Handle; 8] {
        [
            self.pipeline_depth_test_write,
            self.pipeline_depth_disabled,
            self.pipeline_layout,
            self.resource_set,
            self.resource_layout,
            self.fragment_shader,
            self.vertex_shader,
            self.uniform_buffer,
        ]
    }
}

struct DepthAttachmentResources {
    texture: Handle,
    view: Handle,
    extent: Extent3d,
}

struct CrackResources {
    upload_buffer: Handle,
    uniform_buffer: Handle,
    texture: Handle,
    sampler: Handle,
    vertex_shader: Handle,
    fragment_shader: Handle,
    texture_view: Handle,
    resource_layout: Handle,
    resource_set: Handle,
    pipeline_layout: Handle,
    pipeline_depth_disabled: Handle,
    pipeline_depth_test_write: Handle,
}

impl CrackResources {
    fn handles_in_destroy_order(&self) -> [Handle; 12] {
        [
            self.pipeline_depth_test_write,
            self.pipeline_depth_disabled,
            self.pipeline_layout,
            self.resource_set,
            self.resource_layout,
            self.texture_view,
            self.fragment_shader,
            self.vertex_shader,
            self.sampler,
            self.texture,
            self.uniform_buffer,
            self.upload_buffer,
        ]
    }
}

impl DepthAttachmentResources {
    fn handles_in_destroy_order(&self) -> [Handle; 2] {
        [self.view, self.texture]
    }
}

#[derive(Clone, Copy)]
struct CachedPass {
    frame_target: Handle,
    depth_view: Handle,
    pass: Handle,
}

#[derive(Default)]
pub struct WorldPrimitiveFrontend {
    generation: u64,
    resources: Option<WorldLineResources>,
    resource_format: Option<ColorFormat>,
    crack_resources: Option<CrackResources>,
    crack_resource_format: Option<ColorFormat>,
    depth_attachment: Option<DepthAttachmentResources>,
    cached_pass: Option<CachedPass>,
    pending_depth_attachment_retires: u64,
}

impl WorldPrimitiveFrontend {
    pub fn reset(&mut self, gal: &mut VulkanicGal) {
        self.destroy_resources(gal);
        self.generation = 0;
        self.pending_depth_attachment_retires = 0;
    }

    pub fn clear_frame_pass(&mut self, gal: &mut VulkanicGal) {
        if let Some(pass) = self.cached_pass.take() {
            let _ = gal.destroy(pass.pass);
        }
    }

    pub fn submit_whole_frame(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
        gui_ops: Vec<CommandOp>,
    ) -> GalResult<WorldPrimitiveSubmitStats> {
        let (mut ops, mut stats) = self.append_frame_ops(gal, generation, frame_target, frame)?;
        ops.extend(gui_ops);
        stats.command_lists = 1;
        stats.command_ops = ops.len() as u64;
        let token = gal.submit(SubmissionBatch {
            label: "minecraft.world-and-gui.frame".to_string(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "minecraft.world-and-gui.frame.commands".to_string(),
                operations: ops,
            })],
        })?;
        stats.submission_id = token.submission.0;
        Ok(stats)
    }

    pub fn append_frame_ops(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
    ) -> GalResult<(Vec<CommandOp>, WorldPrimitiveSubmitStats)> {
        if !gal
            .capabilities()
            .name
            .to_ascii_lowercase()
            .contains("vulkan")
        {
            return Err(GalError::unsupported_feature(
                "world primitive frontend currently supports the Rust Vulkan whole-frame path only",
            ));
        }
        if self.generation == 0 {
            self.generation = generation;
        }
        validate_frame(&frame)?;
        if frame.segments.is_empty() && frame.crack_quads.is_empty() {
            return Ok((
                Vec::new(),
                WorldPrimitiveSubmitStats {
                    command_ops: 0,
                    ..WorldPrimitiveSubmitStats::default()
                },
            ));
        }
        let color_format = gal.frame_target_color_format(frame_target)?;
        let had_resources = self.resources.is_some() && self.resource_format == Some(color_format);
        let had_crack_resources =
            self.crack_resources.is_some() && self.crack_resource_format == Some(color_format);
        if !frame.segments.is_empty() {
            self.ensure_resources(gal, color_format)?;
        }
        if !frame.crack_quads.is_empty() {
            self.ensure_crack_resources(gal, color_format)?;
        }
        let (depth_texture, depth_view, created_depth, retired_depth) =
            self.ensure_depth_attachment(gal, frame.viewport_width, frame.viewport_height)?;
        let pending_depth_retires = std::mem::take(&mut self.pending_depth_attachment_retires);
        let pass = self.frame_pass(gal, frame_target, depth_view)?;
        let mut stats = WorldPrimitiveSubmitStats {
            segment_count: frame.segments.len() as u64,
            vertex_count: (frame.segments.len() * 2) as u64,
            primitive_batch_count: line_batches(&frame).len() as u64,
            crack_quad_count: frame.crack_quads.len() as u64,
            crack_batch_count: crack_batches(&frame).len() as u64,
            depth_attachment_creates: u64::from(created_depth),
            depth_attachment_reuses: u64::from(!created_depth),
            depth_attachment_retires: retired_depth.saturating_add(pending_depth_retires),
            ..WorldPrimitiveSubmitStats::default()
        };
        if !frame.segments.is_empty() && had_resources {
            stats.cache_hits = 1;
            stats.outline_cache_hits = 1;
        } else if !frame.segments.is_empty() {
            stats.cache_misses = 1;
            stats.outline_cache_misses = 1;
            stats.resource_creates = 8;
        }
        if !frame.crack_quads.is_empty() && had_crack_resources {
            stats.cache_hits += 1;
            stats.crack_cache_hits = 1;
        } else if !frame.crack_quads.is_empty() {
            stats.cache_misses += 1;
            stats.crack_cache_misses = 1;
            stats.resource_creates += 12;
        }
        let batches = line_batches(&frame);
        let crack_batches = crack_batches(&frame);
        let mut ops = Vec::with_capacity(4 + batches.len() * 8 + crack_batches.len() * 8);
        ops.push(CommandOp::Barrier(texture_barrier(
            depth_texture,
            TextureUsageState::Undefined,
            TextureUsageState::DepthStencilAttachment,
        )));
        let mut first_batch = true;
        if !crack_batches.is_empty() {
            let resources = self
                .crack_resources
                .as_ref()
                .ok_or_else(|| GalError::backend("world crack resources vanished before submit"))?;
            for batch in crack_batches {
                let uniforms = packed_crack_uniforms_for_batch(&frame, &batch)?;
                ops.push(CommandOp::Barrier(buffer_barrier(
                    resources.uniform_buffer,
                    TextureUsageState::ShaderRead,
                    TextureUsageState::TransferDst,
                )));
                ops.push(CommandOp::HostWriteBuffer {
                    buffer: resources.uniform_buffer,
                    offset: 0,
                    data: uniforms,
                });
                ops.push(CommandOp::Barrier(buffer_barrier(
                    resources.uniform_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )));
                ops.push(CommandOp::BeginPass {
                    pass,
                    target: frame_target,
                    colors: Vec::new(),
                    depth_stencil: Some(PassAttachment {
                        view: depth_view,
                        load_op: if first_batch {
                            AttachmentLoadOp::Clear
                        } else {
                            AttachmentLoadOp::Load
                        },
                        store_op: AttachmentStoreOp::Store,
                        clear_color: None,
                    }),
                });
                ops.push(CommandOp::BindGraphicsPipeline(
                    if batch.depth_policy == WORLD_DEPTH_POLICY_TEST_WRITE {
                        resources.pipeline_depth_test_write
                    } else {
                        resources.pipeline_depth_disabled
                    },
                ));
                ops.push(CommandOp::BindResourceSet {
                    pipeline_layout: resources.pipeline_layout,
                    set_index: 0,
                    set: resources.resource_set,
                });
                ops.push(CommandOp::Draw {
                    vertices: 6,
                    instances: batch.count as u32,
                });
                ops.push(CommandOp::EndPass);
                first_batch = false;
            }
        }
        let resources = self.resources.as_ref().filter(|_| !batches.is_empty());
        for batch in batches {
            let resources = resources.ok_or_else(|| {
                GalError::backend("world primitive resources vanished before submit")
            })?;
            let uniforms = packed_line_uniforms_for_batch(&frame, &batch)?;
            ops.push(CommandOp::Barrier(buffer_barrier(
                resources.uniform_buffer,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferDst,
            )));
            ops.push(CommandOp::HostWriteBuffer {
                buffer: resources.uniform_buffer,
                offset: 0,
                data: uniforms,
            });
            ops.push(CommandOp::Barrier(buffer_barrier(
                resources.uniform_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
            ops.push(CommandOp::BeginPass {
                pass,
                target: frame_target,
                colors: Vec::new(),
                depth_stencil: Some(PassAttachment {
                    view: depth_view,
                    load_op: if first_batch {
                        AttachmentLoadOp::Clear
                    } else {
                        AttachmentLoadOp::Load
                    },
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            ops.push(CommandOp::BindGraphicsPipeline(
                if batch.depth_policy == WORLD_DEPTH_POLICY_TEST_WRITE {
                    resources.pipeline_depth_test_write
                } else {
                    resources.pipeline_depth_disabled
                },
            ));
            ops.push(CommandOp::BindResourceSet {
                pipeline_layout: resources.pipeline_layout,
                set_index: 0,
                set: resources.resource_set,
            });
            ops.push(CommandOp::Draw {
                vertices: (batch.count * 2) as u32,
                instances: 1,
            });
            ops.push(CommandOp::EndPass);
            first_batch = false;
        }
        stats.world_draws = stats.primitive_batch_count;
        stats.crack_draw_count = stats.crack_batch_count;
        stats.command_ops = ops.len() as u64;
        Ok((ops, stats))
    }

    fn ensure_resources(
        &mut self,
        gal: &mut VulkanicGal,
        color_format: ColorFormat,
    ) -> GalResult<()> {
        if self.resources.is_some() && self.resource_format == Some(color_format) {
            return Ok(());
        }
        self.destroy_render_resources(gal);
        let label = format!("world-block-outline-gen{}", self.generation);
        let mut created = Vec::new();
        let result = (|| -> GalResult<WorldLineResources> {
            let uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.uniform"),
                size: WORLD_LINE_UNIFORM_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(uniform_buffer);
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: WORLD_LINE_VERTEX_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: WORLD_LINE_FRAGMENT_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(fragment_shader);
            let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.resource-layout"),
                bindings: vec![ResourceBindingDesc {
                    binding: 0,
                    kind: ResourceBindingKind::UniformBuffer,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                }],
            })?;
            created.push(resource_layout);
            let resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.resource-set"),
                layout: resource_layout,
                bindings: vec![ResourceBinding {
                    binding: 0,
                    array_index: 0,
                    resource: uniform_buffer,
                    kind: ResourceBindingKind::UniformBuffer,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                }],
            })?;
            created.push(resource_set);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts: vec![resource_layout],
            })?;
            created.push(pipeline_layout);
            let pipeline_depth_disabled = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline.depth-disabled"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Lines,
                cull_mode: CullMode::None,
                blend: BlendMode::Alpha,
                depth_compare: Some(CompareOp::Always),
                color_formats: vec![color_format],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(pipeline_depth_disabled);
            let pipeline_depth_test_write = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline.depth-test-write"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Lines,
                cull_mode: CullMode::None,
                blend: BlendMode::Alpha,
                depth_compare: Some(CompareOp::LessOrEqual),
                color_formats: vec![color_format],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(pipeline_depth_test_write);
            Ok(WorldLineResources {
                uniform_buffer,
                vertex_shader,
                fragment_shader,
                resource_layout,
                resource_set,
                pipeline_layout,
                pipeline_depth_disabled,
                pipeline_depth_test_write,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        self.resources = Some(result?);
        self.resource_format = Some(color_format);
        Ok(())
    }

    fn ensure_crack_resources(
        &mut self,
        gal: &mut VulkanicGal,
        color_format: ColorFormat,
    ) -> GalResult<()> {
        if self.crack_resources.is_some() && self.crack_resource_format == Some(color_format) {
            return Ok(());
        }
        self.destroy_crack_resources(gal);
        let label = format!("world-block-breaking-crack-gen{}", self.generation);
        let atlas = crack_atlas_bytes();
        let mut created = Vec::new();
        let result = (|| -> GalResult<CrackResources> {
            let upload_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.texture-upload"),
                size: atlas.len() as u64,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::TransferSrc,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(upload_buffer);
            let uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.uniform"),
                size: WORLD_CRACK_UNIFORM_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(uniform_buffer);
            let texture = gal.create_texture(TextureDesc {
                label: format!("{label}.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Rgba8Unorm,
                extent: Extent3d {
                    width: CRACK_STAGE_COUNT * CRACK_STAGE_SIZE,
                    height: CRACK_STAGE_SIZE,
                    depth: 1,
                },
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
            })?;
            created.push(texture);
            let sampler = gal.create_sampler(SamplerDesc {
                label: format!("{label}.sampler"),
                min_filter: SamplerFilter::Nearest,
                mag_filter: SamplerFilter::Nearest,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
            })?;
            created.push(sampler);
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: WORLD_CRACK_VERTEX_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: WORLD_CRACK_FRAGMENT_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(fragment_shader);
            let texture_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.texture-view"),
                texture,
                format: TextureFormat::Rgba8Unorm,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(texture_view);
            let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.resource-layout"),
                bindings: vec![
                    ResourceBindingDesc {
                        binding: 0,
                        kind: ResourceBindingKind::UniformBuffer,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 1,
                        kind: ResourceBindingKind::SampledTexture,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 2,
                        kind: ResourceBindingKind::Sampler,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                ],
            })?;
            created.push(resource_layout);
            let resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.resource-set"),
                layout: resource_layout,
                bindings: vec![
                    ResourceBinding {
                        binding: 0,
                        array_index: 0,
                        resource: uniform_buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                    },
                    ResourceBinding {
                        binding: 1,
                        array_index: 0,
                        resource: texture_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                    },
                    ResourceBinding {
                        binding: 2,
                        array_index: 0,
                        resource: sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                    },
                ],
            })?;
            created.push(resource_set);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts: vec![resource_layout],
            })?;
            created.push(pipeline_layout);
            let pipeline_depth_disabled = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline.depth-disabled"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::None,
                blend: BlendMode::Multiply,
                depth_compare: Some(CompareOp::Always),
                color_formats: vec![color_format],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(pipeline_depth_disabled);
            let pipeline_depth_test_write = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline.depth-test-write"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::None,
                blend: BlendMode::Multiply,
                depth_compare: Some(CompareOp::LessOrEqual),
                color_formats: vec![color_format],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(pipeline_depth_test_write);
            let resources = CrackResources {
                upload_buffer,
                uniform_buffer,
                texture,
                sampler,
                vertex_shader,
                fragment_shader,
                texture_view,
                resource_layout,
                resource_set,
                pipeline_layout,
                pipeline_depth_disabled,
                pipeline_depth_test_write,
            };
            self.upload_crack_resources(gal, &resources, atlas)?;
            Ok(resources)
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        self.crack_resources = Some(result?);
        self.crack_resource_format = Some(color_format);
        Ok(())
    }

    fn upload_crack_resources(
        &mut self,
        gal: &mut VulkanicGal,
        resources: &CrackResources,
        atlas: Vec<u8>,
    ) -> GalResult<()> {
        gal.submit(SubmissionBatch {
            label: "world-block-breaking-crack.upload".to_string(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "world-block-breaking-crack.upload.commands".to_string(),
                operations: vec![
                    CommandOp::HostWriteBuffer {
                        buffer: resources.upload_buffer,
                        offset: 0,
                        data: atlas,
                    },
                    CommandOp::Barrier(buffer_barrier(
                        resources.upload_buffer,
                        TextureUsageState::TransferDst,
                        TextureUsageState::TransferSrc,
                    )),
                    CommandOp::Barrier(sampled_texture_barrier(
                        resources.texture,
                        TextureUsageState::Undefined,
                        TextureUsageState::TransferDst,
                    )),
                    CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                        buffer: resources.upload_buffer,
                        buffer_offset: 0,
                        bytes_per_row: CRACK_STAGE_COUNT * CRACK_STAGE_SIZE * 4,
                        rows_per_image: CRACK_STAGE_SIZE,
                        texture: resources.texture,
                        texture_mip: 0,
                        texture_layer: 0,
                        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                        extent: Extent3d {
                            width: CRACK_STAGE_COUNT * CRACK_STAGE_SIZE,
                            height: CRACK_STAGE_SIZE,
                            depth: 1,
                        },
                    }),
                    CommandOp::Barrier(sampled_texture_barrier(
                        resources.texture,
                        TextureUsageState::TransferDst,
                        TextureUsageState::ShaderRead,
                    )),
                ],
            })],
        })?;
        Ok(())
    }

    fn ensure_depth_attachment(
        &mut self,
        gal: &mut VulkanicGal,
        width: u32,
        height: u32,
    ) -> GalResult<(Handle, Handle, bool, u64)> {
        let extent = Extent3d {
            width,
            height,
            depth: 1,
        };
        if let Some(depth) = self.depth_attachment.as_ref() {
            if depth.extent == extent {
                return Ok((depth.texture, depth.view, false, 0));
            }
        }
        let mut retired = 0;
        if let Some(depth) = self.depth_attachment.take() {
            for handle in depth.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
            retired = 1;
        }
        self.clear_frame_pass(gal);
        let label = format!("world-block-outline-depth-gen{}", self.generation);
        let texture = gal.create_texture(TextureDesc {
            label: format!("{label}.texture"),
            dimension: TextureDimension::D2,
            format: TextureFormat::Depth32Float,
            extent,
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::DepthStencilAttachment],
        })?;
        let view = match gal.create_texture_view(TextureViewDesc {
            label: format!("{label}.view"),
            texture,
            format: TextureFormat::Depth32Float,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        }) {
            Ok(view) => view,
            Err(error) => {
                let _ = gal.destroy(texture);
                return Err(error);
            }
        };
        self.depth_attachment = Some(DepthAttachmentResources {
            texture,
            view,
            extent,
        });
        Ok((texture, view, true, retired))
    }

    fn frame_pass(
        &mut self,
        gal: &mut VulkanicGal,
        frame_target: Handle,
        depth_view: Handle,
    ) -> GalResult<Handle> {
        if let Some(cached) = self.cached_pass {
            if cached.frame_target == frame_target && cached.depth_view == depth_view {
                return Ok(cached.pass);
            }
            gal.destroy(cached.pass)?;
            self.cached_pass = None;
        }
        let pass = gal.create_render_pass(RenderPassDesc {
            label: "minecraft.world.block-outline.pass".to_string(),
            target: frame_target,
            color_formats: vec![gal.frame_target_color_format(frame_target)?],
            depth_format: Some(TextureFormat::Depth32Float),
        })?;
        self.cached_pass = Some(CachedPass {
            frame_target,
            depth_view,
            pass,
        });
        Ok(pass)
    }

    fn destroy_resources(&mut self, gal: &mut VulkanicGal) {
        self.clear_frame_pass(gal);
        self.destroy_render_resources(gal);
    }

    fn destroy_render_resources(&mut self, gal: &mut VulkanicGal) {
        if let Some(resources) = self.resources.take() {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        self.destroy_crack_resources(gal);
        if let Some(depth) = self.depth_attachment.take() {
            for handle in depth.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
            self.pending_depth_attachment_retires =
                self.pending_depth_attachment_retires.saturating_add(1);
        }
        self.resource_format = None;
    }

    fn destroy_crack_resources(&mut self, gal: &mut VulkanicGal) {
        if let Some(resources) = self.crack_resources.take() {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        self.crack_resource_format = None;
    }
}

fn validate_frame(frame: &WorldPrimitiveFrame) -> GalResult<()> {
    if frame.frame_id == 0 || frame.correlation_id == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world primitive frame and correlation ids must be non-zero",
        ));
    }
    if frame.viewport_width == 0 || frame.viewport_height == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "world primitive viewport must be non-empty",
        ));
    }
    if frame.segments.len() > WORLD_MAX_LINE_SEGMENTS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world primitive line segment count {} exceeds maximum {}",
                frame.segments.len(),
                WORLD_MAX_LINE_SEGMENTS
            ),
        ));
    }
    if frame.crack_quads.len() > WORLD_MAX_CRACK_QUADS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world crack quad count {} exceeds maximum {}",
                frame.crack_quads.len(),
                WORLD_MAX_CRACK_QUADS
            ),
        ));
    }
    for value in frame
        .view_matrix
        .iter()
        .chain(frame.projection_matrix.iter())
    {
        if !value.is_finite() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world primitive matrices must contain finite values",
            ));
        }
    }
    for segment in &frame.segments {
        if segment.stratum != WORLD_STRATUM_BLOCK_OUTLINE {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("unsupported world primitive stratum {}", segment.stratum),
            ));
        }
        if segment.style > 2 {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world primitive style {}", segment.style),
            ));
        }
        if segment.depth_policy > WORLD_DEPTH_POLICY_TEST_WRITE {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!(
                    "unknown world primitive depth policy {}",
                    segment.depth_policy
                ),
            ));
        }
        if segment.line_width <= 0.0 || !segment.line_width.is_finite() {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world primitive line width must be finite and positive",
            ));
        }
        if segment.viewport_width != frame.viewport_width
            || segment.viewport_height != frame.viewport_height
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world primitive segment viewport metadata must match the frame viewport",
            ));
        }
        for value in segment.start.iter().chain(segment.end.iter()) {
            if !value.is_finite() {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "world primitive segment coordinates must be finite",
                ));
            }
        }
    }
    for quad in &frame.crack_quads {
        if quad.stratum != WORLD_STRATUM_BLOCK_BREAKING_CRACK {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("unsupported world crack stratum {}", quad.stratum),
            ));
        }
        if quad.stage >= CRACK_STAGE_COUNT {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!(
                    "unknown block-breaking crack animation stage {}",
                    quad.stage
                ),
            ));
        }
        if quad.depth_policy > WORLD_DEPTH_POLICY_TEST_WRITE {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world crack depth policy {}", quad.depth_policy),
            ));
        }
        if quad.viewport_width != frame.viewport_width
            || quad.viewport_height != frame.viewport_height
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world crack viewport metadata must match the frame viewport",
            ));
        }
        for value in quad.vertices.iter().flatten() {
            if !value.is_finite() {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "world crack quad coordinates must be finite",
                ));
            }
        }
    }
    Ok(())
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
struct LineBatch {
    start: usize,
    count: usize,
    depth_policy: u32,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
struct CrackBatch {
    start: usize,
    count: usize,
    depth_policy: u32,
}

fn line_batches(frame: &WorldPrimitiveFrame) -> Vec<LineBatch> {
    let mut batches = Vec::new();
    let Some(first) = frame.segments.first() else {
        return batches;
    };
    let mut current = LineBatch {
        start: 0,
        count: 0,
        depth_policy: first.depth_policy,
    };
    for (index, segment) in frame.segments.iter().enumerate() {
        if current.count > 0 && segment.depth_policy != current.depth_policy {
            batches.push(current);
            current = LineBatch {
                start: index,
                count: 0,
                depth_policy: segment.depth_policy,
            };
        }
        current.count += 1;
    }
    if current.count > 0 {
        batches.push(current);
    }
    batches
}

fn crack_batches(frame: &WorldPrimitiveFrame) -> Vec<CrackBatch> {
    let mut batches = Vec::new();
    let Some(first) = frame.crack_quads.first() else {
        return batches;
    };
    let mut current = CrackBatch {
        start: 0,
        count: 0,
        depth_policy: first.depth_policy,
    };
    for (index, quad) in frame.crack_quads.iter().enumerate() {
        if current.count > 0 && quad.depth_policy != current.depth_policy {
            batches.push(current);
            current = CrackBatch {
                start: index,
                count: 0,
                depth_policy: quad.depth_policy,
            };
        }
        current.count += 1;
    }
    if current.count > 0 {
        batches.push(current);
    }
    batches
}

fn packed_line_uniforms_for_batch(
    frame: &WorldPrimitiveFrame,
    batch: &LineBatch,
) -> GalResult<Vec<u8>> {
    let mut out = Vec::with_capacity(WORLD_LINE_UNIFORM_BYTES as usize);
    for value in frame.view_matrix {
        push_f32(&mut out, value);
    }
    for value in frame.projection_matrix {
        push_f32(&mut out, value);
    }
    push_f32(&mut out, frame.viewport_width as f32);
    push_f32(&mut out, frame.viewport_height as f32);
    push_f32(&mut out, 0.0);
    push_f32(&mut out, 0.0);
    for segment in &frame.segments[batch.start..batch.start + batch.count] {
        for value in segment.start {
            push_f32(&mut out, value);
        }
        push_f32(&mut out, 1.0);
        for value in segment.end {
            push_f32(&mut out, value);
        }
        push_f32(&mut out, 1.0);
        for value in argb_to_rgba(segment.color_argb) {
            push_f32(&mut out, value);
        }
    }
    Ok(out)
}

fn packed_crack_uniforms_for_batch(
    frame: &WorldPrimitiveFrame,
    batch: &CrackBatch,
) -> GalResult<Vec<u8>> {
    let mut out = Vec::with_capacity(WORLD_CRACK_UNIFORM_BYTES as usize);
    for value in frame.view_matrix {
        push_f32(&mut out, value);
    }
    for value in frame.projection_matrix {
        push_f32(&mut out, value);
    }
    push_f32(&mut out, frame.viewport_width as f32);
    push_f32(&mut out, frame.viewport_height as f32);
    push_f32(&mut out, 0.0);
    push_f32(&mut out, 0.0);
    for quad in &frame.crack_quads[batch.start..batch.start + batch.count] {
        for vertex in quad.vertices {
            for value in vertex {
                push_f32(&mut out, value);
            }
            push_f32(&mut out, 1.0);
        }
        let u = quad.stage as f32 / CRACK_STAGE_COUNT as f32;
        push_f32(&mut out, u);
        push_f32(&mut out, 0.0);
        push_f32(&mut out, 1.0 / CRACK_STAGE_COUNT as f32);
        push_f32(&mut out, 1.0);
        for value in argb_to_rgba(quad.color_argb) {
            push_f32(&mut out, value);
        }
    }
    Ok(out)
}

fn argb_to_rgba(argb: u32) -> [f32; 4] {
    let a = ((argb >> 24) & 0xff) as f32 / 255.0;
    let r = ((argb >> 16) & 0xff) as f32 / 255.0;
    let g = ((argb >> 8) & 0xff) as f32 / 255.0;
    let b = (argb & 0xff) as f32 / 255.0;
    [r, g, b, a]
}

fn push_f32(out: &mut Vec<u8>, value: f32) {
    out.extend_from_slice(&value.to_ne_bytes());
}

fn buffer_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        stages: PipelineStageFlags(PipelineStageFlags::DRAW.0 | PipelineStageFlags::TRANSFER.0),
        access: AccessFlags(AccessFlags::READ.0 | AccessFlags::TRANSFER.0),
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

fn texture_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        stages: PipelineStageFlags::DRAW,
        access: AccessFlags::DEPTH_STENCIL,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

fn sampled_texture_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        stages: PipelineStageFlags(PipelineStageFlags::DRAW.0 | PipelineStageFlags::TRANSFER.0),
        access: AccessFlags(AccessFlags::READ.0 | AccessFlags::TRANSFER.0),
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

fn crack_atlas_bytes() -> Vec<u8> {
    let mut out =
        Vec::with_capacity((CRACK_STAGE_COUNT * CRACK_STAGE_SIZE * CRACK_STAGE_SIZE * 4) as usize);
    for stage in 0..CRACK_STAGE_COUNT {
        let bytes = match stage {
            0 => include_bytes!(
                "../../../resources/assets/minecraft/textures/block/destroy_stage_0.png"
            )
            .as_slice(),
            1 => include_bytes!(
                "../../../resources/assets/minecraft/textures/block/destroy_stage_1.png"
            )
            .as_slice(),
            2 => include_bytes!(
                "../../../resources/assets/minecraft/textures/block/destroy_stage_2.png"
            )
            .as_slice(),
            3 => include_bytes!(
                "../../../resources/assets/minecraft/textures/block/destroy_stage_3.png"
            )
            .as_slice(),
            4 => include_bytes!(
                "../../../resources/assets/minecraft/textures/block/destroy_stage_4.png"
            )
            .as_slice(),
            5 => include_bytes!(
                "../../../resources/assets/minecraft/textures/block/destroy_stage_5.png"
            )
            .as_slice(),
            6 => include_bytes!(
                "../../../resources/assets/minecraft/textures/block/destroy_stage_6.png"
            )
            .as_slice(),
            7 => include_bytes!(
                "../../../resources/assets/minecraft/textures/block/destroy_stage_7.png"
            )
            .as_slice(),
            8 => include_bytes!(
                "../../../resources/assets/minecraft/textures/block/destroy_stage_8.png"
            )
            .as_slice(),
            9 => include_bytes!(
                "../../../resources/assets/minecraft/textures/block/destroy_stage_9.png"
            )
            .as_slice(),
            _ => unreachable!(),
        };
        let decoded = decode_crack_stage(bytes).expect("bundled crack stage texture is valid");
        out.extend_from_slice(&decoded);
    }
    out
}

fn decode_crack_stage(bytes: &[u8]) -> GalResult<Vec<u8>> {
    let mut decoder = png::Decoder::new(std::io::BufReader::new(bytes));
    decoder.set_transformations(png::Transformations::EXPAND | png::Transformations::STRIP_16);
    let mut reader = decoder
        .read_info()
        .map_err(|error| GalError::backend(format!("failed to decode crack stage: {error}")))?;
    let mut buf = vec![0; reader.output_buffer_size()];
    let info = reader
        .next_frame(&mut buf)
        .map_err(|error| GalError::backend(format!("failed to read crack stage: {error}")))?;
    if info.width != CRACK_STAGE_SIZE || info.height != CRACK_STAGE_SIZE {
        return Err(GalError::backend(format!(
            "unexpected crack stage dimensions {}x{}",
            info.width, info.height
        )));
    }
    let data = &buf[..info.buffer_size()];
    match info.color_type {
        png::ColorType::Rgba => Ok(data.to_vec()),
        png::ColorType::Rgb => {
            let mut rgba = Vec::with_capacity((info.width * info.height * 4) as usize);
            for pixel in data.chunks_exact(3) {
                rgba.extend_from_slice(&[pixel[0], pixel[1], pixel[2], 255]);
            }
            Ok(rgba)
        }
        png::ColorType::GrayscaleAlpha => {
            let mut rgba = Vec::with_capacity((info.width * info.height * 4) as usize);
            for pixel in data.chunks_exact(2) {
                rgba.extend_from_slice(&[pixel[0], pixel[0], pixel[0], pixel[1]]);
            }
            Ok(rgba)
        }
        png::ColorType::Grayscale => {
            let mut rgba = Vec::with_capacity((info.width * info.height * 4) as usize);
            for value in data {
                rgba.extend_from_slice(&[*value, *value, *value, 255]);
            }
            Ok(rgba)
        }
        png::ColorType::Indexed => Err(GalError::backend(
            "indexed crack stage was not expanded by the PNG decoder",
        )),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::backends::{
        mock::MockBackend, presentation_capabilities, vulkan_capabilities,
    };
    use crate::render::vulkanic::resources::FrameTargetDesc;

    fn gal() -> VulkanicGal {
        VulkanicGal::new_with_backend(
            Box::new(MockBackend::with_capabilities(presentation_capabilities(
                vulkan_capabilities(),
            ))),
            false,
        )
    }

    fn frame_target(gal: &mut VulkanicGal, frame_id: u64, width: u32, height: u32) -> Handle {
        gal.create_frame_target(FrameTargetDesc {
            label: format!("test-frame-target-{frame_id}"),
            frame_id,
            extent: Extent3d {
                width,
                height,
                depth: 1,
            },
            color_format: ColorFormat::Bgra8Unorm,
        })
        .unwrap()
    }

    fn segment(depth_policy: u32, color_argb: u32) -> WorldLineSegmentRequest {
        WorldLineSegmentRequest {
            stratum: 100,
            style: 1,
            depth_policy,
            color_argb,
            line_width: 1.0,
            start: [0.0, 0.0, -1.0],
            end: [1.0, 0.0, -1.0],
            viewport_width: 128,
            viewport_height: 128,
        }
    }

    fn frame(segments: Vec<WorldLineSegmentRequest>) -> WorldPrimitiveFrame {
        WorldPrimitiveFrame {
            frame_id: 1,
            correlation_id: 2,
            viewport_width: 128,
            viewport_height: 128,
            view_matrix: [
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ],
            projection_matrix: [
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ],
            segments,
            crack_quads: Vec::new(),
        }
    }

    fn crack_quad(stage: u32, depth_policy: u32) -> WorldCrackQuadRequest {
        WorldCrackQuadRequest {
            stratum: WORLD_STRATUM_BLOCK_BREAKING_CRACK,
            stage,
            depth_policy,
            color_argb: 0xffffffff,
            vertices: [
                [0.0, 0.0, -1.0],
                [1.0, 0.0, -1.0],
                [1.0, 1.0, -1.0],
                [0.0, 1.0, -1.0],
            ],
            viewport_width: 128,
            viewport_height: 128,
        }
    }

    fn frame_with_world_work(width: u32, height: u32) -> WorldPrimitiveFrame {
        let mut frame = frame(vec![segment(WORLD_DEPTH_POLICY_TEST_WRITE, 0xff000000)]);
        frame.viewport_width = width;
        frame.viewport_height = height;
        frame.segments[0].viewport_width = width;
        frame.segments[0].viewport_height = height;
        let mut crack = crack_quad(4, WORLD_DEPTH_POLICY_TEST_WRITE);
        crack.viewport_width = width;
        crack.viewport_height = height;
        frame.crack_quads.push(crack);
        frame
    }

    #[test]
    fn line_batches_preserve_contiguous_depth_groups() {
        let frame = frame(vec![
            segment(WORLD_DEPTH_POLICY_TEST_WRITE, 0xff000000),
            segment(WORLD_DEPTH_POLICY_TEST_WRITE, 0xff000000),
            segment(WORLD_DEPTH_POLICY_TEST_WRITE, 0xff57ffff),
            segment(WORLD_DEPTH_POLICY_TEST_WRITE, 0xff57ffff),
            segment(WORLD_DEPTH_POLICY_DISABLED, 0xff57ffff),
        ]);
        let batches = line_batches(&frame);
        assert_eq!(2, batches.len());
        assert_eq!(
            LineBatch {
                start: 0,
                count: 4,
                depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
            },
            batches[0]
        );
        assert_eq!(1, batches[1].count);
        assert_eq!(WORLD_DEPTH_POLICY_DISABLED, batches[1].depth_policy);
    }

    #[test]
    fn validate_frame_rejects_unknown_depth_policy() {
        let error = validate_frame(&frame(vec![segment(99, 0xff000000)])).unwrap_err();
        assert_eq!(StatusCode::UnknownEnum, error.code);
    }

    #[test]
    fn packed_batch_uniforms_use_batch_color_and_segment_subset() {
        let frame = frame(vec![
            segment(WORLD_DEPTH_POLICY_TEST_WRITE, 0xff000000),
            segment(WORLD_DEPTH_POLICY_TEST_WRITE, 0xff57ffff),
        ]);
        let batch = LineBatch {
            start: 1,
            count: 1,
            depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
        };
        let bytes = packed_line_uniforms_for_batch(&frame, &batch).unwrap();
        assert_eq!(
            WORLD_LINE_HEADER_BYTES + WORLD_LINE_SEGMENT_BYTES,
            bytes.len()
        );
        let color_offset = WORLD_LINE_HEADER_BYTES + 32;
        let color = [
            f32::from_ne_bytes(bytes[color_offset..color_offset + 4].try_into().unwrap()),
            f32::from_ne_bytes(
                bytes[color_offset + 4..color_offset + 8]
                    .try_into()
                    .unwrap(),
            ),
            f32::from_ne_bytes(
                bytes[color_offset + 8..color_offset + 12]
                    .try_into()
                    .unwrap(),
            ),
            f32::from_ne_bytes(
                bytes[color_offset + 12..color_offset + 16]
                    .try_into()
                    .unwrap(),
            ),
        ];
        assert_eq!([87.0 / 255.0, 1.0, 1.0, 1.0], color);
    }

    #[test]
    fn validate_frame_rejects_unknown_crack_stage() {
        let mut frame = frame(Vec::new());
        frame
            .crack_quads
            .push(crack_quad(10, WORLD_DEPTH_POLICY_TEST_WRITE));
        let error = validate_frame(&frame).unwrap_err();
        assert_eq!(StatusCode::UnknownEnum, error.code);
    }

    #[test]
    fn crack_batches_preserve_contiguous_depth_groups() {
        let mut frame = frame(Vec::new());
        frame.crack_quads = vec![
            crack_quad(0, WORLD_DEPTH_POLICY_TEST_WRITE),
            crack_quad(1, WORLD_DEPTH_POLICY_TEST_WRITE),
            crack_quad(2, WORLD_DEPTH_POLICY_DISABLED),
        ];
        let batches = crack_batches(&frame);
        assert_eq!(2, batches.len());
        assert_eq!(
            CrackBatch {
                start: 0,
                count: 2,
                depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
            },
            batches[0]
        );
        assert_eq!(1, batches[1].count);
        assert_eq!(WORLD_DEPTH_POLICY_DISABLED, batches[1].depth_policy);
    }

    #[test]
    fn packed_crack_uniforms_select_stage_region() {
        let mut frame = frame(Vec::new());
        frame
            .crack_quads
            .push(crack_quad(4, WORLD_DEPTH_POLICY_TEST_WRITE));
        let bytes = packed_crack_uniforms_for_batch(
            &frame,
            &CrackBatch {
                start: 0,
                count: 1,
                depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
            },
        )
        .unwrap();
        assert_eq!(
            WORLD_CRACK_HEADER_BYTES + WORLD_CRACK_QUAD_BYTES,
            bytes.len()
        );
        let uv_offset = WORLD_CRACK_HEADER_BYTES + 64;
        let u = f32::from_ne_bytes(bytes[uv_offset..uv_offset + 4].try_into().unwrap());
        let width = f32::from_ne_bytes(bytes[uv_offset + 8..uv_offset + 12].try_into().unwrap());
        assert_eq!(4.0 / CRACK_STAGE_COUNT as f32, u);
        assert_eq!(1.0 / CRACK_STAGE_COUNT as f32, width);
    }

    #[test]
    fn gui_asset_generation_change_does_not_recreate_world_resources() {
        let mut gal = gal();
        let target = frame_target(&mut gal, 1, 128, 128);
        let mut frontend = WorldPrimitiveFrontend::default();
        let (_, first) = frontend
            .append_frame_ops(&mut gal, 1, target, frame_with_world_work(128, 128))
            .unwrap();
        assert_eq!(1, first.outline_cache_misses);
        assert_eq!(1, first.crack_cache_misses);
        assert_eq!(1, first.depth_attachment_creates);

        let (_, second) = frontend
            .append_frame_ops(&mut gal, 2, target, frame_with_world_work(128, 128))
            .unwrap();
        assert_eq!(1, second.outline_cache_hits);
        assert_eq!(1, second.crack_cache_hits);
        assert_eq!(0, second.outline_cache_misses);
        assert_eq!(0, second.crack_cache_misses);
        assert_eq!(0, second.depth_attachment_retires);
        assert_eq!(1, second.depth_attachment_reuses);
    }

    #[test]
    fn viewport_resize_recreates_depth_but_reuses_world_resources() {
        let mut gal = gal();
        let first_target = frame_target(&mut gal, 1, 128, 128);
        let second_target = frame_target(&mut gal, 2, 256, 128);
        let mut frontend = WorldPrimitiveFrontend::default();
        frontend
            .append_frame_ops(&mut gal, 1, first_target, frame_with_world_work(128, 128))
            .unwrap();

        let (_, resized) = frontend
            .append_frame_ops(&mut gal, 1, second_target, frame_with_world_work(256, 128))
            .unwrap();
        assert_eq!(1, resized.outline_cache_hits);
        assert_eq!(1, resized.crack_cache_hits);
        assert_eq!(1, resized.depth_attachment_creates);
        assert_eq!(1, resized.depth_attachment_retires);
        assert_eq!(0, resized.depth_attachment_reuses);
    }
}
