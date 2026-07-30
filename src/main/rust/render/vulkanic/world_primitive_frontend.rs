use std::collections::BTreeMap;

mod background;
mod crack;
pub(crate) mod material;
mod material_registry;
mod outline;
mod shared;
mod world_border;

use super::commands::{
    AttachmentLoadOp, AttachmentStoreOp, CommandOp, PassAttachment, ResourceBarrier,
    SubmissionBatch, TextureOrigin3d, TextureUsageState,
};
use super::error::{GalError, GalResult, StatusCode};
use super::gal::VulkanicGal;
use super::handles::Handle;
use super::resources::{
    AccessFlags, BackendApi, BlendMode, BufferDesc, BufferUsage, ColorFormat, CompareOp, Extent3d,
    GraphicsPipelineDesc, IndexType, MemoryDomain, PipelineLayoutDesc, PipelineStageFlags,
    PrimitiveTopology, QueueClass, RenderPassDesc, ResourceBinding, ResourceBindingDesc,
    ResourceBindingKind, ResourceLayoutDesc, ResourceSetDesc, SamplerAddressMode, SamplerDesc,
    SamplerFilter, ShaderCodeFormat, ShaderModuleDesc, ShaderStage, TextureDesc, TextureDimension,
    TextureFormat, TextureUsage, TextureViewDesc,
};
use super::{BufferImageCopyRegion, CommandList, CommandListDesc, CullMode};

pub const WORLD_MAX_LINE_SEGMENTS: usize = 512;
pub const WORLD_MAX_CRACK_QUADS: usize = 512;
pub const WORLD_MAX_BORDER_QUADS: usize = 64;
pub const WORLD_MAX_MATERIAL_QUADS: usize = 512;
pub const WORLD_DEPTH_POLICY_DISABLED: u32 = 0;
pub const WORLD_DEPTH_POLICY_TEST_WRITE: u32 = 1;
pub const WORLD_DEPTH_POLICY_TEST_NO_WRITE: u32 = 2;
pub const WORLD_MATERIAL_MODE_OPAQUE: u32 = 1;
pub const WORLD_MATERIAL_MODE_CUTOUT: u32 = 2;
pub const WORLD_TOPOLOGY_TRIANGLES: u32 = 1;
pub const WORLD_CULL_NONE: u32 = 0;
pub const WORLD_CULL_BACK: u32 = 1;
pub const WORLD_CULL_FRONT: u32 = 2;
pub const WORLD_WINDING_CCW: u32 = 1;
pub const WORLD_WINDING_CW: u32 = 2;
pub const WORLD_STRATUM_WORLD_BORDER: u32 = 80;
pub const WORLD_STRATUM_BLOCK_OUTLINE: u32 = 100;
pub const WORLD_STRATUM_BLOCK_BREAKING_CRACK: u32 = 90;
pub const WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY: u32 = 70;
pub const WORLD_BORDER_TEXTURE_FORCEFIELD: u32 = 1;
pub const WORLD_MATERIAL_TEXTURE_STONE: u32 = 0x21df_896f;
pub const WORLD_MATERIAL_TEXTURE_DIRT: u32 = 0x0b0b_bd25;
pub const WORLD_MATERIAL_TEXTURE_OAK_LEAVES: u32 = 0x7232_1ec7;
pub const WORLD_MATERIAL_TEXTURE_DEEPSLATE: u32 = 0x715d_8d65;
pub const WORLD_MATERIAL_TEXTURE_WHITE_WOOL: u32 = 0x2253_a2ef;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER: u32 = 0x447d_596a;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_00: u32 = 0x665d_a7aa;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_01: u32 = 0x50e8_8e0f;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_02: u32 = 0x079e_2b74;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_03: u32 = 0x4a7c_2b71;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_04: u32 = 0x35e9_0ae6;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_05: u32 = 0x2f21_fecb;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_06: u32 = 0x2a27_abf0;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_07: u32 = 0x0ea4_c92d;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_08: u32 = 0x4473_cce2;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_09: u32 = 0x0ab5_51c7;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_10: u32 = 0x7a25_0241;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_11: u32 = 0x1f43_9384;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_12: u32 = 0x4bab_8f5f;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_13: u32 = 0x4316_88fa;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_14: u32 = 0x0b2b_dbbd;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_15: u32 = 0x0194_76c0;
pub const WORLD_MATERIAL_TEXTURE_DEFAULT: u32 = WORLD_MATERIAL_TEXTURE_STONE;
pub const WORLD_MATERIAL_ID_OPAQUE_TEXTURED: u32 = 0x6a2f_d335;
pub const WORLD_MATERIAL_ID_CUTOUT_TEXTURED: u32 = 0x129b_1b90;
pub const WORLD_MATERIAL_ID_BLOCK_MARKER_CUTOUT: u32 = 0x224a_8659;
pub const WORLD_MATERIAL_ID_DEFAULT_OPAQUE: u32 = WORLD_MATERIAL_ID_OPAQUE_TEXTURED;
pub const WORLD_MATERIAL_ID_DEFAULT_CUTOUT: u32 = WORLD_MATERIAL_ID_CUTOUT_TEXTURED;
pub const WORLD_BORDER_BLEND_OVERLAY: u32 = 1;
pub const WORLD_BORDER_CULL_NONE: u32 = 0;
pub const WORLD_BACKGROUND_SKY_OVERWORLD: u32 = 1;
pub const WORLD_BACKGROUND_SKY_NETHER: u32 = 2;
pub const WORLD_BACKGROUND_SKY_END: u32 = 3;
pub const WORLD_BACKGROUND_SKY_CUSTOM: u32 = 4;
pub const WORLD_BACKGROUND_LOAD_CLEAR: u32 = 1;
pub const WORLD_BACKGROUND_STORE_STORE: u32 = 1;
const WORLD_LINE_HEADER_BYTES: usize = 144;
const WORLD_LINE_SEGMENT_BYTES: usize = 48;
const WORLD_LINE_UNIFORM_BYTES: u64 =
    (WORLD_LINE_HEADER_BYTES + WORLD_MAX_LINE_SEGMENTS * WORLD_LINE_SEGMENT_BYTES) as u64;
const WORLD_CRACK_HEADER_BYTES: usize = 144;
const WORLD_CRACK_QUAD_BYTES: usize = 96;
const WORLD_CRACK_UNIFORM_BYTES: u64 =
    (WORLD_CRACK_HEADER_BYTES + WORLD_MAX_CRACK_QUADS * WORLD_CRACK_QUAD_BYTES) as u64;
const WORLD_BORDER_HEADER_BYTES: usize = 144;
const WORLD_BORDER_QUAD_BYTES: usize = 112;
const WORLD_BORDER_UNIFORM_BYTES: u64 =
    (WORLD_BORDER_HEADER_BYTES + WORLD_MAX_BORDER_QUADS * WORLD_BORDER_QUAD_BYTES) as u64;
const WORLD_MATERIAL_HEADER_BYTES: usize = 144;
const WORLD_MATERIAL_QUAD_BYTES: usize = 112;
const WORLD_MATERIAL_UNIFORM_BYTES: u64 =
    (WORLD_MATERIAL_HEADER_BYTES + WORLD_MAX_MATERIAL_QUADS * WORLD_MATERIAL_QUAD_BYTES) as u64;
const WORLD_MATERIAL_INDEX_BYTES: u64 = 6 * 4;
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
    int segment = gl_VertexIndex / 6;
    int corner = gl_VertexIndex - segment * 6;
    int endpoint = (corner == 0 || corner == 3 || corner == 5) ? 0 : 1;
    float side = (corner == 0 || corner == 1 || corner == 5) ? -1.0 : 1.0;
    vec3 start = segments[segment].start.xyz * (1.0 - (1.0 / 256.0));
    vec3 end = segments[segment].end.xyz * (1.0 - (1.0 / 256.0));
    vec4 start_clip = projection * view * vec4(start, 1.0);
    vec4 end_clip = projection * view * vec4(end, 1.0);
    vec2 start_ndc = start_clip.xy / start_clip.w;
    vec2 end_ndc = end_clip.xy / end_clip.w;
    vec2 screen_delta = (end_ndc - start_ndc) * viewport.xy;
    float length_px = max(length(screen_delta), 0.0001);
    vec2 normal = vec2(-screen_delta.y, screen_delta.x) / length_px;
    float width_px = max(segments[segment].start.w, 1.0);
    vec2 offset_ndc = normal * (width_px / viewport.xy) * side;
    vec4 clip = endpoint == 0 ? start_clip : end_clip;
    clip.xy += offset_ndc * clip.w;
    gl_Position = clip;
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

const WORLD_BORDER_VERTEX_SHADER_VULKAN: &[u8] = br#"#version 450
struct BorderQuad {
    vec4 p0;
    vec4 p1;
    vec4 p2;
    vec4 p3;
    vec4 uv_region;
    vec4 scroll_border_distance;
    vec4 color;
};
layout(set = 0, binding = 0, std140) uniform WorldBorderBatch {
    mat4 view;
    mat4 projection;
    vec4 viewport;
    BorderQuad quads[64];
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
    BorderQuad quad = quads[gl_InstanceIndex];
    vec2 c = corner[vertex];
    vec3 top = mix(quad.p0.xyz, quad.p1.xyz, c.x);
    vec3 bottom = mix(quad.p3.xyz, quad.p2.xyz, c.x);
    vec3 position = mix(top, bottom, c.y);
    gl_Position = projection * view * vec4(position, 1.0);
    v_uv = vec2(
        quad.uv_region.x + c.x * quad.uv_region.z + quad.scroll_border_distance.x,
        quad.uv_region.y + c.y * quad.uv_region.w + quad.scroll_border_distance.y
    );
    v_color = quad.color;
}
"#;

const WORLD_BORDER_FRAGMENT_SHADER_VULKAN: &[u8] = br#"#version 450
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

const WORLD_MATERIAL_VERTEX_SHADER_VULKAN: &[u8] = br#"#version 450
struct MaterialQuad {
    vec4 p0;
    vec4 p1;
    vec4 p2;
    vec4 p3;
    vec4 uv0_uv1;
    vec4 uv2_uv3;
    vec4 color;
};
layout(set = 0, binding = 0, std430) readonly buffer WorldMaterialBatch {
    mat4 view;
    mat4 projection;
    vec4 viewport_cutout;
    MaterialQuad quads[512];
};
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec4 v_color;
layout(location = 2) flat out vec4 v_material;
const vec2 corner[4] = vec2[4](
    vec2(0.0, 0.0),
    vec2(1.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 1.0)
);
void main() {
    MaterialQuad quad = quads[gl_InstanceIndex];
    int vertex = gl_VertexIndex;
    if (quad.p0.w > 1.5) {
        vertex = int[4](0, 3, 2, 1)[vertex];
    }
    vec2 c = corner[vertex];
    vec3 top = mix(quad.p0.xyz, quad.p1.xyz, c.x);
    vec3 bottom = mix(quad.p3.xyz, quad.p2.xyz, c.x);
    vec3 position = mix(top, bottom, c.y);
    vec2 uv_top = mix(quad.uv0_uv1.xy, quad.uv0_uv1.zw, c.x);
    vec2 uv_bottom = mix(quad.uv2_uv3.zw, quad.uv2_uv3.xy, c.x);
    gl_Position = projection * view * vec4(position, 1.0);
    v_uv = mix(uv_top, uv_bottom, c.y);
    v_color = quad.color;
    v_material = vec4(viewport_cutout.z, 0.0, 0.0, 0.0);
}
"#;

const WORLD_MATERIAL_FRAGMENT_SHADER_VULKAN: &[u8] = br#"#version 450
layout(set = 0, binding = 1) uniform texture2D Tex0;
layout(set = 0, binding = 2) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 2) flat in vec4 v_material;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 color = texture(sampler2D(Tex0, Samp0), v_uv) * v_color;
    if (v_material.x > 0.0 && color.a < v_material.x) {
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
pub struct WorldBorderQuadRequest {
    pub stratum: u32,
    pub texture_id: u32,
    pub depth_policy: u32,
    pub blend_policy: u32,
    pub cull_policy: u32,
    pub color_argb: u32,
    pub border_size: f32,
    pub distance_to_border: f32,
    pub scroll: [f32; 2],
    pub uv_region: [f32; 4],
    pub vertices: [[f32; 3]; 4],
    pub viewport_width: u32,
    pub viewport_height: u32,
}

#[derive(Clone, Debug)]
pub struct WorldMaterialQuadRequest {
    pub stratum: u32,
    pub material_id: u32,
    pub texture_id: u32,
    pub material_mode: u32,
    pub depth_policy: u32,
    pub cull_policy: u32,
    pub topology: u32,
    pub winding: u32,
    pub color_argb: u32,
    pub vertices: [[f32; 3]; 4],
    pub uvs: [[f32; 2]; 4],
    pub viewport_width: u32,
    pub viewport_height: u32,
}

#[derive(Clone, Debug)]
pub struct WorldBorderAssetPayload {
    pub texture_id: u32,
    pub png_bytes: Vec<u8>,
}

#[derive(Clone, Debug)]
pub struct WorldMaterialAssetPayload {
    pub texture_id: u32,
    pub png_bytes: Vec<u8>,
}

#[derive(Clone, Debug)]
pub struct WorldCrackAssetPayload {
    pub stage: u32,
    pub png_bytes: Vec<u8>,
}

#[derive(Clone, Copy, Debug, Default)]
pub struct WorldBackgroundRequest {
    pub enabled: bool,
    pub sky_type: u32,
    pub color_argb: u32,
    pub load_intent: u32,
    pub store_intent: u32,
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
    pub background: WorldBackgroundRequest,
    pub segments: Vec<WorldLineSegmentRequest>,
    pub crack_quads: Vec<WorldCrackQuadRequest>,
    pub border_quads: Vec<WorldBorderQuadRequest>,
    pub material_quads: Vec<WorldMaterialQuadRequest>,
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
    pub border_quad_count: u64,
    pub border_batch_count: u64,
    pub border_draw_count: u64,
    pub material_quad_count: u64,
    pub material_batch_count: u64,
    pub material_draw_count: u64,
    pub border_cache_hits: u64,
    pub border_cache_misses: u64,
    pub material_cache_hits: u64,
    pub material_cache_misses: u64,
    pub border_asset_generation: u64,
    pub border_asset_payload_bytes: u64,
    pub border_asset_update_failures: u64,
    pub crack_asset_generation: u64,
    pub crack_asset_payload_bytes: u64,
    pub crack_asset_update_failures: u64,
    pub material_asset_generation: u64,
    pub material_asset_payload_bytes: u64,
    pub material_asset_update_failures: u64,
    pub background_clear_count: u64,
    pub background_diagnostic_fallback_count: u64,
    pub background_sky_type: u64,
    pub background_color_argb: u64,
}

struct WorldLineResources {
    uniform_buffer: Handle,
    vertex_shader: Handle,
    fragment_shader: Handle,
    resource_layout: Handle,
    resource_set: Handle,
    pipeline_layout: Handle,
    pipeline_depth_disabled: Handle,
    pipeline_depth_test_no_write: Handle,
    pipeline_depth_test_write: Handle,
}

impl WorldLineResources {
    fn handles_in_destroy_order(&self) -> [Handle; 9] {
        [
            self.pipeline_depth_test_write,
            self.pipeline_depth_test_no_write,
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

struct BorderResources {
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

impl BorderResources {
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

#[derive(Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd)]
struct MaterialResourceKey {
    material_id: u32,
    texture_id: u32,
    material_mode: u32,
    depth_policy: u32,
    cull_policy: u32,
    color_format: ColorFormat,
}

struct MaterialResources {
    upload_buffer: Handle,
    index_upload_buffer: Handle,
    index_buffer: Handle,
    texture: Handle,
    sampler: Handle,
    vertex_shader: Handle,
    fragment_shader: Handle,
    texture_view: Handle,
    resource_layout: Handle,
    pipeline_layout: Handle,
    pipeline: Handle,
    data_slots: Vec<MaterialDataSlot>,
}

struct MaterialDataSlot {
    uniform_buffer: Handle,
    resource_set: Handle,
}

impl MaterialResources {
    fn handles_in_destroy_order(&self) -> Vec<Handle> {
        let mut handles = vec![
            self.pipeline,
            self.pipeline_layout,
            self.resource_layout,
            self.texture_view,
            self.fragment_shader,
            self.vertex_shader,
            self.sampler,
            self.texture,
            self.index_buffer,
            self.index_upload_buffer,
            self.upload_buffer,
        ];
        for slot in self.data_slots.iter().rev() {
            handles.push(slot.resource_set);
            handles.push(slot.uniform_buffer);
        }
        handles
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
    border_asset_generation: u64,
    border_asset_override: Option<WorldBorderTextureAsset>,
    border_asset_payload_bytes: u64,
    border_asset_update_failures: u64,
    crack_asset_generation: u64,
    crack_asset_overrides: BTreeMap<u32, WorldCrackTextureAsset>,
    crack_asset_payload_bytes: u64,
    crack_asset_update_failures: u64,
    material_asset_generation: u64,
    material_asset_overrides: BTreeMap<u32, WorldMaterialTextureAsset>,
    material_asset_payload_bytes: u64,
    material_asset_update_failures: u64,
    resources: Option<WorldLineResources>,
    resource_format: Option<ColorFormat>,
    crack_resources: Option<CrackResources>,
    crack_resource_format: Option<ColorFormat>,
    border_resources: Option<BorderResources>,
    border_resource_format: Option<ColorFormat>,
    material_resources: BTreeMap<MaterialResourceKey, MaterialResources>,
    depth_attachment: Option<DepthAttachmentResources>,
    cached_pass: Option<CachedPass>,
    pending_depth_attachment_retires: u64,
}

#[derive(Clone, Debug)]
struct WorldBorderTextureAsset {
    rgba: Vec<u8>,
    width: u32,
    height: u32,
}

#[derive(Clone, Debug)]
struct WorldCrackTextureAsset {
    rgba: Vec<u8>,
}

#[derive(Clone, Debug)]
struct WorldMaterialTextureAsset {
    rgba: Vec<u8>,
    width: u32,
    height: u32,
}

impl WorldPrimitiveFrontend {
    pub fn reset(&mut self, gal: &mut VulkanicGal) {
        self.destroy_resources(gal);
        self.generation = 0;
        self.border_asset_generation = 0;
        self.border_asset_override = None;
        self.border_asset_payload_bytes = 0;
        self.border_asset_update_failures = 0;
        self.crack_asset_generation = 0;
        self.crack_asset_overrides.clear();
        self.crack_asset_payload_bytes = 0;
        self.crack_asset_update_failures = 0;
        self.material_asset_generation = 0;
        self.material_asset_overrides.clear();
        self.material_asset_payload_bytes = 0;
        self.material_asset_update_failures = 0;
        self.pending_depth_attachment_retires = 0;
    }

    pub fn apply_world_border_asset_update(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payload: WorldBorderAssetPayload,
    ) -> GalResult<()> {
        let result = self.apply_world_border_asset_update_inner(gal, generation, payload);
        if result.is_err() {
            self.border_asset_update_failures = self.border_asset_update_failures.saturating_add(1);
        }
        result
    }

    fn apply_world_border_asset_update_inner(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payload: WorldBorderAssetPayload,
    ) -> GalResult<()> {
        if generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world-border asset generation must be non-zero",
            ));
        }
        if generation <= self.border_asset_generation {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "stale world-border asset generation {generation}; current generation is {}",
                    self.border_asset_generation
                ),
            ));
        }
        if payload.texture_id != WORLD_BORDER_TEXTURE_FORCEFIELD {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world-border texture id {}", payload.texture_id),
            ));
        }
        let replacement = if payload.png_bytes.is_empty() {
            let _ = forcefield_texture_bytes()?;
            None
        } else {
            let (rgba, width, height) =
                decode_png_rgba(&payload.png_bytes, "world border forcefield override")?;
            Some(WorldBorderTextureAsset {
                rgba,
                width,
                height,
            })
        };
        self.border_asset_generation = generation;
        self.border_asset_payload_bytes = payload.png_bytes.len() as u64;
        self.border_asset_override = replacement;
        self.destroy_border_resources(gal);
        Ok(())
    }

    pub fn apply_world_crack_asset_update(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payloads: Vec<WorldCrackAssetPayload>,
    ) -> GalResult<()> {
        let result = self.apply_world_crack_asset_update_inner(gal, generation, payloads);
        if result.is_err() {
            self.crack_asset_update_failures = self.crack_asset_update_failures.saturating_add(1);
        }
        result
    }

    fn apply_world_crack_asset_update_inner(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payloads: Vec<WorldCrackAssetPayload>,
    ) -> GalResult<()> {
        if generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world crack asset generation must be non-zero",
            ));
        }
        if generation <= self.crack_asset_generation {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "stale world crack asset generation {generation}; current generation is {}",
                    self.crack_asset_generation
                ),
            ));
        }
        let mut overrides = BTreeMap::new();
        let mut payload_bytes = 0u64;
        for payload in payloads {
            if payload.stage >= CRACK_STAGE_COUNT {
                return Err(GalError::ffi(
                    StatusCode::UnknownEnum,
                    format!("unknown world crack stage {}", payload.stage),
                ));
            }
            if overrides.contains_key(&payload.stage) {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "duplicate world crack asset payload for stage {}",
                        payload.stage
                    ),
                ));
            }
            if payload.png_bytes.is_empty() {
                continue;
            }
            payload_bytes = payload_bytes.saturating_add(payload.png_bytes.len() as u64);
            let (rgba, width, height) = decode_png_rgba(
                &payload.png_bytes,
                &format!("world crack destroy_stage_{} override", payload.stage),
            )?;
            if width != CRACK_STAGE_SIZE || height != CRACK_STAGE_SIZE {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "world crack destroy_stage_{} must be {}x{}, got {}x{}",
                        payload.stage, CRACK_STAGE_SIZE, CRACK_STAGE_SIZE, width, height
                    ),
                ));
            }
            overrides.insert(payload.stage, WorldCrackTextureAsset { rgba });
        }
        self.crack_asset_generation = generation;
        self.crack_asset_payload_bytes = payload_bytes;
        self.crack_asset_overrides = overrides;
        self.destroy_crack_resources(gal);
        Ok(())
    }

    pub fn apply_world_material_asset_update(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payloads: Vec<WorldMaterialAssetPayload>,
    ) -> GalResult<()> {
        let result = self.apply_world_material_asset_update_inner(gal, generation, payloads);
        if result.is_err() {
            self.material_asset_update_failures =
                self.material_asset_update_failures.saturating_add(1);
        }
        result
    }

    fn apply_world_material_asset_update_inner(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payloads: Vec<WorldMaterialAssetPayload>,
    ) -> GalResult<()> {
        if generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world material asset generation must be non-zero",
            ));
        }
        if generation <= self.material_asset_generation {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "stale world material asset generation {generation}; current generation is {}",
                    self.material_asset_generation
                ),
            ));
        }
        let mut overrides = BTreeMap::new();
        let mut payload_bytes = 0u64;
        for payload in payloads {
            let texture_id =
                material::canonical_texture_id(payload.texture_id).ok_or_else(|| {
                    GalError::ffi(
                        StatusCode::UnknownEnum,
                        format!("unknown world material texture id {}", payload.texture_id),
                    )
                })?;
            if overrides.contains_key(&texture_id) {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "duplicate world material asset payload for texture {}",
                        texture_id
                    ),
                ));
            }
            if payload.png_bytes.is_empty() {
                continue;
            }
            payload_bytes = payload_bytes.saturating_add(payload.png_bytes.len() as u64);
            let (rgba, width, height) = decode_png_rgba(
                &payload.png_bytes,
                &format!("world material texture {} override", texture_id),
            )?;
            overrides.insert(
                texture_id,
                WorldMaterialTextureAsset {
                    rgba,
                    width,
                    height,
                },
            );
        }
        self.material_asset_generation = generation;
        self.material_asset_payload_bytes = payload_bytes;
        self.material_asset_overrides = overrides;
        self.destroy_material_resources(gal);
        Ok(())
    }

    pub fn clear_frame_pass(&mut self, gal: &mut VulkanicGal) {
        if let Some(pass) = self.cached_pass.take() {
            let _ = gal.destroy(pass.pass);
        }
    }

    pub fn clear_frame_passes_for_targets(&mut self, gal: &mut VulkanicGal, targets: &[Handle]) {
        let Some(pass) = self.cached_pass else {
            return;
        };
        if targets.contains(&pass.frame_target) {
            self.cached_pass = None;
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
        let (mut ops, mut stats) =
            self.append_frame_ops_inner(gal, generation, frame_target, frame, true)?;
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

    pub fn submit_partial_frame(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
    ) -> GalResult<WorldPrimitiveSubmitStats> {
        let (ops, mut stats) =
            self.append_frame_ops_inner(gal, generation, frame_target, frame, false)?;
        stats.command_lists = 1;
        stats.command_ops = ops.len() as u64;
        let token = gal.submit(SubmissionBatch {
            label: "minecraft.world-primitives.partial-frame".to_string(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "minecraft.world-primitives.partial-frame.commands".to_string(),
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
        self.append_frame_ops_inner(gal, generation, frame_target, frame, true)
    }

    fn append_frame_ops_inner(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
        clear_background: bool,
    ) -> GalResult<(Vec<CommandOp>, WorldPrimitiveSubmitStats)> {
        let vulkan_backend = gal.capabilities().api == BackendApi::Vulkan;
        if !vulkan_backend
            && (clear_background || frame.background.enabled || !frame.border_quads.is_empty())
        {
            return Err(GalError::unsupported_feature(
                "OpenGL partial world primitive submit supports line and crack primitives only",
            ));
        }
        if self.generation == 0 {
            self.generation = generation;
        }
        validate_frame(&frame)?;
        let color_format = gal.pass_target_color_format(frame_target)?;
        let color_attachment = gal.pass_target_color_attachment(frame_target)?;
        let had_resources = self.resources.is_some() && self.resource_format == Some(color_format);
        let had_crack_resources =
            self.crack_resources.is_some() && self.crack_resource_format == Some(color_format);
        let had_border_resources =
            self.border_resources.is_some() && self.border_resource_format == Some(color_format);
        let material_batches = material_batches(&frame, color_format);
        let material_cache_hits = material_batches
            .iter()
            .filter(|batch| self.material_resources.contains_key(&batch.key))
            .count() as u64;
        if !frame.segments.is_empty() {
            self.ensure_resources(gal, color_format)?;
        }
        if !frame.crack_quads.is_empty() {
            self.ensure_crack_resources(gal, color_format)?;
        }
        if !frame.border_quads.is_empty() {
            self.ensure_border_resources(gal, color_format)?;
        }
        for batch in &material_batches {
            self.ensure_material_resources(gal, batch.key)?;
        }
        let mut material_slot_counts = BTreeMap::new();
        for batch in &material_batches {
            let count = material_slot_counts.entry(batch.key).or_insert(0usize);
            *count += 1;
            self.ensure_material_resource_slots(gal, batch.key, *count)?;
        }
        let (depth_texture, depth_view, created_depth, retired_depth) = self
            .ensure_depth_attachment(
                gal,
                frame_target,
                frame.viewport_width,
                frame.viewport_height,
            )?;
        let pending_depth_retires = std::mem::take(&mut self.pending_depth_attachment_retires);
        let pass = self.frame_pass(gal, frame_target, depth_view)?;
        let mut stats = WorldPrimitiveSubmitStats {
            segment_count: frame.segments.len() as u64,
            vertex_count: (frame.segments.len() * 6) as u64,
            primitive_batch_count: line_batches(&frame).len() as u64,
            crack_quad_count: frame.crack_quads.len() as u64,
            crack_batch_count: crack_batches(&frame).len() as u64,
            border_quad_count: frame.border_quads.len() as u64,
            border_batch_count: border_batches(&frame).len() as u64,
            material_quad_count: frame.material_quads.len() as u64,
            material_batch_count: material_batches.len() as u64,
            depth_attachment_creates: u64::from(created_depth),
            depth_attachment_reuses: u64::from(!created_depth),
            depth_attachment_retires: retired_depth.saturating_add(pending_depth_retires),
            border_asset_generation: self.border_asset_generation,
            border_asset_payload_bytes: self.border_asset_payload_bytes,
            border_asset_update_failures: self.border_asset_update_failures,
            crack_asset_generation: self.crack_asset_generation,
            crack_asset_payload_bytes: self.crack_asset_payload_bytes,
            crack_asset_update_failures: self.crack_asset_update_failures,
            material_asset_generation: self.material_asset_generation,
            material_asset_payload_bytes: self.material_asset_payload_bytes,
            material_asset_update_failures: self.material_asset_update_failures,
            background_clear_count: u64::from(frame.background.enabled),
            background_diagnostic_fallback_count: u64::from(!frame.background.enabled),
            background_sky_type: frame.background.sky_type as u64,
            background_color_argb: frame.background.color_argb as u64,
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
        if !frame.border_quads.is_empty() && had_border_resources {
            stats.cache_hits += 1;
            stats.border_cache_hits = 1;
        } else if !frame.border_quads.is_empty() {
            stats.cache_misses += 1;
            stats.border_cache_misses = 1;
            stats.resource_creates += 12;
        }
        if !material_batches.is_empty() {
            stats.material_cache_hits = material_cache_hits;
            stats.material_cache_misses = material_batches.len() as u64 - material_cache_hits;
            stats.cache_hits = stats.cache_hits.saturating_add(stats.material_cache_hits);
            stats.cache_misses = stats
                .cache_misses
                .saturating_add(stats.material_cache_misses);
            stats.resource_creates = stats
                .resource_creates
                .saturating_add(stats.material_cache_misses.saturating_mul(13));
        }
        let background_color = background_clear_color(&frame.background);
        let batches = line_batches(&frame);
        let crack_batches = crack_batches(&frame);
        let border_batches = border_batches(&frame);
        let mut ops = Vec::with_capacity(
            6 + batches.len() * 8
                + crack_batches.len() * 8
                + border_batches.len() * 8
                + material_batches.len() * 9,
        );
        if clear_background {
            ops.push(CommandOp::Barrier(texture_barrier(
                depth_texture,
                TextureUsageState::Undefined,
                TextureUsageState::DepthStencilAttachment,
            )));
            ops.push(CommandOp::BeginPass {
                pass,
                target: frame_target,
                colors: vec![PassAttachment {
                    view: color_attachment,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: Some(background_color),
                }],
                depth_stencil: Some(PassAttachment {
                    view: depth_view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            ops.push(CommandOp::EndPass);
        }
        if !material_batches.is_empty() {
            let mut material_slot_indices = BTreeMap::new();
            let mut material_draws = Vec::with_capacity(material_batches.len());
            for batch in &material_batches {
                let slot_index = material_slot_indices.entry(batch.key).or_insert(0usize);
                let resources = self.material_resources.get(&batch.key).ok_or_else(|| {
                    GalError::backend("world material resources vanished before submit")
                })?;
                let slot = resources
                    .data_slots
                    .get(*slot_index)
                    .ok_or_else(|| GalError::backend("world material data slot missing"))?;
                *slot_index += 1;
                let uniforms = packed_material_uniforms_for_batch(&frame, batch)?;
                ops.push(CommandOp::Barrier(buffer_barrier(
                    slot.uniform_buffer,
                    TextureUsageState::ShaderRead,
                    TextureUsageState::TransferDst,
                )));
                ops.push(CommandOp::HostWriteBuffer {
                    buffer: slot.uniform_buffer,
                    offset: 0,
                    data: uniforms,
                });
                ops.push(CommandOp::Barrier(buffer_barrier(
                    slot.uniform_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )));
                material_draws.push((
                    resources.pipeline,
                    resources.pipeline_layout,
                    slot.resource_set,
                    resources.index_buffer,
                    batch.count() as u32,
                ));
            }
            ops.push(CommandOp::BeginPass {
                pass,
                target: frame_target,
                colors: vec![loaded_frame_color_attachment(color_attachment)],
                depth_stencil: Some(PassAttachment {
                    view: depth_view,
                    load_op: AttachmentLoadOp::Load,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            for (pipeline, pipeline_layout, resource_set, index_buffer, instance_count) in
                material_draws
            {
                ops.push(CommandOp::BindGraphicsPipeline(pipeline));
                ops.push(CommandOp::BindResourceSet {
                    pipeline_layout,
                    set_index: 0,
                    set: resource_set,
                });
                ops.push(CommandOp::SetIndexBuffer {
                    buffer: index_buffer,
                    offset: 0,
                    index_type: IndexType::U32,
                });
                ops.push(CommandOp::DrawIndexed {
                    indices: 6,
                    instances: instance_count,
                });
            }
            ops.push(CommandOp::EndPass);
        }
        let mut first_batch = false;
        if !border_batches.is_empty() {
            let resources = self.border_resources.as_ref().ok_or_else(|| {
                GalError::backend("world border resources vanished before submit")
            })?;
            for batch in border_batches {
                let uniforms = packed_border_uniforms_for_batch(&frame, &batch)?;
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
                    colors: vec![loaded_frame_color_attachment(color_attachment)],
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
                    colors: vec![loaded_frame_color_attachment(color_attachment)],
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
                colors: vec![loaded_frame_color_attachment(color_attachment)],
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
            ops.push(CommandOp::BindGraphicsPipeline(match batch.depth_policy {
                WORLD_DEPTH_POLICY_TEST_WRITE => resources.pipeline_depth_test_write,
                WORLD_DEPTH_POLICY_TEST_NO_WRITE => resources.pipeline_depth_test_no_write,
                _ => resources.pipeline_depth_disabled,
            }));
            ops.push(CommandOp::BindResourceSet {
                pipeline_layout: resources.pipeline_layout,
                set_index: 0,
                set: resources.resource_set,
            });
            ops.push(CommandOp::Draw {
                vertices: (batch.count * 6) as u32,
                instances: 1,
            });
            ops.push(CommandOp::EndPass);
            first_batch = false;
        }
        stats.world_draws = stats.primitive_batch_count;
        stats.crack_draw_count = stats.crack_batch_count;
        stats.border_draw_count = stats.border_batch_count;
        stats.material_draw_count = stats.material_batch_count;
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
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::None,
                blend: BlendMode::Alpha,
                depth_compare: None,
                depth_write: false,
                color_formats: vec![color_format],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(pipeline_depth_disabled);
            let pipeline_depth_test_no_write =
                gal.create_graphics_pipeline(GraphicsPipelineDesc {
                    label: format!("{label}.pipeline.depth-test-no-write"),
                    layout: pipeline_layout,
                    vertex_shader,
                    fragment_shader,
                    topology: PrimitiveTopology::Triangles,
                    cull_mode: CullMode::None,
                    blend: BlendMode::Alpha,
                    depth_compare: Some(CompareOp::LessOrEqual),
                    depth_write: false,
                    color_formats: vec![color_format],
                    depth_format: Some(TextureFormat::Depth32Float),
                })?;
            created.push(pipeline_depth_test_no_write);
            let pipeline_depth_test_write = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline.depth-test-write"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::None,
                blend: BlendMode::Alpha,
                depth_compare: Some(CompareOp::LessOrEqual),
                depth_write: true,
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
                pipeline_depth_test_no_write,
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
        let atlas = crack_atlas_bytes(&self.crack_asset_overrides)?;
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
                depth_compare: None,
                depth_write: false,
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
                depth_write: true,
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

    fn ensure_border_resources(
        &mut self,
        gal: &mut VulkanicGal,
        color_format: ColorFormat,
    ) -> GalResult<()> {
        if self.border_resources.is_some() && self.border_resource_format == Some(color_format) {
            return Ok(());
        }
        self.destroy_border_resources(gal);
        let label = format!("world-border-gen{}", self.generation);
        let (texture_bytes, texture_width, texture_height) = self.world_border_texture_bytes()?;
        let mut created = Vec::new();
        let result = (|| -> GalResult<BorderResources> {
            let upload_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.texture-upload"),
                size: texture_bytes.len() as u64,
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
                size: WORLD_BORDER_UNIFORM_BYTES,
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
                    width: texture_width,
                    height: texture_height,
                    depth: 1,
                },
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
            })?;
            created.push(texture);
            let sampler = gal.create_sampler(SamplerDesc {
                label: format!("{label}.sampler"),
                min_filter: SamplerFilter::Linear,
                mag_filter: SamplerFilter::Linear,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::Repeat,
                address_v: SamplerAddressMode::Repeat,
                address_w: SamplerAddressMode::Repeat,
            })?;
            created.push(sampler);
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: WORLD_BORDER_VERTEX_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: WORLD_BORDER_FRAGMENT_SHADER_VULKAN.to_vec(),
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
                blend: BlendMode::Overlay,
                depth_compare: None,
                depth_write: false,
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
                blend: BlendMode::Overlay,
                depth_compare: Some(CompareOp::LessOrEqual),
                depth_write: true,
                color_formats: vec![color_format],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(pipeline_depth_test_write);
            let resources = BorderResources {
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
            self.upload_border_resources(
                gal,
                &resources,
                texture_bytes,
                texture_width,
                texture_height,
            )?;
            Ok(resources)
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        self.border_resources = Some(result?);
        self.border_resource_format = Some(color_format);
        Ok(())
    }

    fn world_border_texture_bytes(&self) -> GalResult<(Vec<u8>, u32, u32)> {
        if let Some(asset) = &self.border_asset_override {
            return Ok((asset.rgba.clone(), asset.width, asset.height));
        }
        forcefield_texture_bytes()
    }

    fn upload_border_resources(
        &mut self,
        gal: &mut VulkanicGal,
        resources: &BorderResources,
        texture_bytes: Vec<u8>,
        texture_width: u32,
        texture_height: u32,
    ) -> GalResult<()> {
        gal.submit(SubmissionBatch {
            label: "world-border.upload".to_string(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "world-border.upload.commands".to_string(),
                operations: vec![
                    CommandOp::HostWriteBuffer {
                        buffer: resources.upload_buffer,
                        offset: 0,
                        data: texture_bytes,
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
                        bytes_per_row: texture_width * 4,
                        rows_per_image: texture_height,
                        texture: resources.texture,
                        texture_mip: 0,
                        texture_layer: 0,
                        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                        extent: Extent3d {
                            width: texture_width,
                            height: texture_height,
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

    fn ensure_material_resources(
        &mut self,
        gal: &mut VulkanicGal,
        key: MaterialResourceKey,
    ) -> GalResult<()> {
        if self.material_resources.contains_key(&key) {
            return Ok(());
        }
        let label = format!(
            "world-material-reg{}-{}-texture{}-mode{}-depth{}-cull{}-gen{}",
            material_registry::WORLD_MATERIAL_REGISTRY_VERSION,
            key.material_id,
            key.texture_id,
            key.material_mode,
            key.depth_policy,
            key.cull_policy,
            self.generation
        );
        let (texture_bytes, texture_width, texture_height) =
            self.world_material_texture_bytes(key.texture_id)?;
        let mut created = Vec::new();
        let result = (|| -> GalResult<MaterialResources> {
            let upload_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.texture-upload"),
                size: texture_bytes.len() as u64,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::TransferSrc,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(upload_buffer);
            let index_upload_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.index-upload"),
                size: WORLD_MATERIAL_INDEX_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::TransferSrc,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(index_upload_buffer);
            let index_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.index"),
                size: WORLD_MATERIAL_INDEX_BYTES,
                memory: MemoryDomain::DeviceLocal,
                usages: vec![BufferUsage::Index, BufferUsage::TransferDst],
            })?;
            created.push(index_buffer);
            let texture = gal.create_texture(TextureDesc {
                label: format!("{label}.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Rgba8Unorm,
                extent: Extent3d {
                    width: texture_width,
                    height: texture_height,
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
                code: WORLD_MATERIAL_VERTEX_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: WORLD_MATERIAL_FRAGMENT_SHADER_VULKAN.to_vec(),
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
                        kind: ResourceBindingKind::StorageBuffer,
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
            let slot = create_material_data_slot(
                gal,
                &format!("{label}.slot0"),
                resource_layout,
                texture_view,
                sampler,
            )?;
            created.push(slot.uniform_buffer);
            created.push(slot.resource_set);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts: vec![resource_layout],
            })?;
            created.push(pipeline_layout);
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: cull_mode_from_policy(key.cull_policy)?,
                blend: BlendMode::Disabled,
                depth_compare: match key.depth_policy {
                    WORLD_DEPTH_POLICY_DISABLED => None,
                    WORLD_DEPTH_POLICY_TEST_WRITE | WORLD_DEPTH_POLICY_TEST_NO_WRITE => {
                        Some(CompareOp::LessOrEqual)
                    }
                    _ => {
                        return Err(GalError::ffi(
                            StatusCode::UnknownEnum,
                            format!("unknown world material depth policy {}", key.depth_policy),
                        ))
                    }
                },
                depth_write: key.depth_policy == WORLD_DEPTH_POLICY_TEST_WRITE,
                color_formats: vec![key.color_format],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(pipeline);
            let resources = MaterialResources {
                upload_buffer,
                index_upload_buffer,
                index_buffer,
                texture,
                sampler,
                vertex_shader,
                fragment_shader,
                texture_view,
                resource_layout,
                pipeline_layout,
                pipeline,
                data_slots: vec![slot],
            };
            self.upload_material_resources(
                gal,
                &resources,
                texture_bytes,
                texture_width,
                texture_height,
            )?;
            Ok(resources)
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        self.material_resources.insert(key, result?);
        Ok(())
    }

    fn ensure_material_resource_slots(
        &mut self,
        gal: &mut VulkanicGal,
        key: MaterialResourceKey,
        required_slots: usize,
    ) -> GalResult<()> {
        let resources = self.material_resources.get_mut(&key).ok_or_else(|| {
            GalError::backend("world material resources missing during slot growth")
        })?;
        while resources.data_slots.len() < required_slots {
            let slot_index = resources.data_slots.len();
            let label = format!(
                "world-material-{}-texture{}-mode{}-depth{}-cull{}-slot{}-gen{}",
                key.material_id,
                key.texture_id,
                key.material_mode,
                key.depth_policy,
                key.cull_policy,
                slot_index,
                self.generation
            );
            let slot = create_material_data_slot(
                gal,
                &label,
                resources.resource_layout,
                resources.texture_view,
                resources.sampler,
            )?;
            resources.data_slots.push(slot);
        }
        Ok(())
    }

    fn world_material_texture_bytes(&self, texture_id: u32) -> GalResult<(Vec<u8>, u32, u32)> {
        let texture_id = material::canonical_texture_id(texture_id).ok_or_else(|| {
            GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world material texture id {texture_id}"),
            )
        })?;
        if !material::is_known_texture_id(texture_id) {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world material texture id {texture_id}"),
            ));
        }
        if let Some(asset) = self.material_asset_overrides.get(&texture_id) {
            return Ok((asset.rgba.clone(), asset.width, asset.height));
        }
        bundled_world_material_texture_bytes(texture_id)
    }

    fn upload_material_resources(
        &mut self,
        gal: &mut VulkanicGal,
        resources: &MaterialResources,
        texture_bytes: Vec<u8>,
        texture_width: u32,
        texture_height: u32,
    ) -> GalResult<()> {
        let mut index_bytes = Vec::with_capacity(WORLD_MATERIAL_INDEX_BYTES as usize);
        for index in [0_u32, 1, 2, 2, 3, 0] {
            push_u32(&mut index_bytes, index);
        }
        gal.submit(SubmissionBatch {
            label: "world-material.upload".to_string(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "world-material.upload.commands".to_string(),
                operations: vec![
                    CommandOp::HostWriteBuffer {
                        buffer: resources.upload_buffer,
                        offset: 0,
                        data: texture_bytes,
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
                        bytes_per_row: texture_width * 4,
                        rows_per_image: texture_height,
                        texture: resources.texture,
                        texture_mip: 0,
                        texture_layer: 0,
                        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                        extent: Extent3d {
                            width: texture_width,
                            height: texture_height,
                            depth: 1,
                        },
                    }),
                    CommandOp::Barrier(sampled_texture_barrier(
                        resources.texture,
                        TextureUsageState::TransferDst,
                        TextureUsageState::ShaderRead,
                    )),
                    CommandOp::HostWriteBuffer {
                        buffer: resources.index_upload_buffer,
                        offset: 0,
                        data: index_bytes,
                    },
                    CommandOp::Barrier(buffer_barrier(
                        resources.index_upload_buffer,
                        TextureUsageState::TransferDst,
                        TextureUsageState::TransferSrc,
                    )),
                    CommandOp::Barrier(buffer_barrier(
                        resources.index_buffer,
                        TextureUsageState::Undefined,
                        TextureUsageState::TransferDst,
                    )),
                    CommandOp::CopyBuffer {
                        src: resources.index_upload_buffer,
                        dst: resources.index_buffer,
                        size: WORLD_MATERIAL_INDEX_BYTES,
                    },
                    CommandOp::Barrier(buffer_barrier(
                        resources.index_buffer,
                        TextureUsageState::TransferDst,
                        TextureUsageState::IndexRead,
                    )),
                ],
            })],
        })?;
        Ok(())
    }

    fn ensure_depth_attachment(
        &mut self,
        gal: &mut VulkanicGal,
        pass_target: Handle,
        width: u32,
        height: u32,
    ) -> GalResult<(Handle, Handle, bool, u64)> {
        let extent = Extent3d {
            width,
            height,
            depth: 1,
        };
        if let Some((texture, view)) = gal.pass_target_depth_attachment(pass_target)? {
            if let Some(depth) = self.depth_attachment.take() {
                for handle in depth.handles_in_destroy_order() {
                    let _ = gal.destroy(handle);
                }
                self.pending_depth_attachment_retires =
                    self.pending_depth_attachment_retires.saturating_add(1);
                self.clear_frame_pass(gal);
            }
            return Ok((texture, view, false, 0));
        }
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
        pass_target: Handle,
        depth_view: Handle,
    ) -> GalResult<Handle> {
        if let Some(cached) = self.cached_pass {
            if cached.frame_target == pass_target && cached.depth_view == depth_view {
                return Ok(cached.pass);
            }
            gal.destroy(cached.pass)?;
            self.cached_pass = None;
        }
        let pass = gal.create_render_pass(RenderPassDesc {
            label: "minecraft.world.block-outline.pass".to_string(),
            target: pass_target,
            color_formats: vec![gal.pass_target_color_format(pass_target)?],
            depth_format: Some(TextureFormat::Depth32Float),
        })?;
        self.cached_pass = Some(CachedPass {
            frame_target: pass_target,
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
        self.destroy_border_resources(gal);
        self.destroy_material_resources(gal);
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

    fn destroy_border_resources(&mut self, gal: &mut VulkanicGal) {
        if let Some(resources) = self.border_resources.take() {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        self.border_resource_format = None;
    }

    fn destroy_material_resources(&mut self, gal: &mut VulkanicGal) {
        let resources = std::mem::take(&mut self.material_resources);
        for (_, resources) in resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
    }
}

fn validate_frame(frame: &WorldPrimitiveFrame) -> GalResult<()> {
    shared::validate_frame_header(frame)?;
    background::validate_background(frame)?;
    for segment in &frame.segments {
        outline::validate_segment(segment, frame)?;
    }
    for quad in &frame.crack_quads {
        crack::validate_quad(quad, frame)?;
    }
    for quad in &frame.border_quads {
        world_border::validate_quad(quad, frame)?;
    }
    for quad in &frame.material_quads {
        material::validate_quad(quad, frame)?;
    }
    Ok(())
}

fn cull_mode_from_policy(policy: u32) -> GalResult<CullMode> {
    match policy {
        WORLD_CULL_NONE => Ok(CullMode::None),
        WORLD_CULL_BACK => Ok(CullMode::Back),
        WORLD_CULL_FRONT => Ok(CullMode::Front),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world material cull policy {policy}"),
        )),
    }
}

fn background_clear_color(background: &WorldBackgroundRequest) -> super::commands::ClearColor {
    if background.enabled {
        argb_clear_color(background.color_argb)
    } else {
        super::commands::ClearColor {
            r: 0.063,
            g: 0.157,
            b: 0.855,
            a: 1.0,
        }
    }
}

fn argb_clear_color(color_argb: u32) -> super::commands::ClearColor {
    let a = ((color_argb >> 24) & 0xff) as f32 / 255.0;
    let r = ((color_argb >> 16) & 0xff) as f32 / 255.0;
    let g = ((color_argb >> 8) & 0xff) as f32 / 255.0;
    let b = (color_argb & 0xff) as f32 / 255.0;
    super::commands::ClearColor { r, g, b, a }
}

fn loaded_frame_color_attachment(color_attachment: Handle) -> PassAttachment {
    PassAttachment {
        view: color_attachment,
        load_op: AttachmentLoadOp::Load,
        store_op: AttachmentStoreOp::Store,
        clear_color: None,
    }
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

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
struct BorderBatch {
    start: usize,
    count: usize,
    depth_policy: u32,
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct MaterialBatch {
    key: MaterialResourceKey,
    indices: Vec<usize>,
}

impl MaterialBatch {
    fn count(&self) -> usize {
        self.indices.len()
    }
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

fn border_batches(frame: &WorldPrimitiveFrame) -> Vec<BorderBatch> {
    let mut batches = Vec::new();
    let Some(first) = frame.border_quads.first() else {
        return batches;
    };
    let mut current = BorderBatch {
        start: 0,
        count: 0,
        depth_policy: first.depth_policy,
    };
    for (index, quad) in frame.border_quads.iter().enumerate() {
        if current.count > 0 && quad.depth_policy != current.depth_policy {
            batches.push(current);
            current = BorderBatch {
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

fn material_batches(frame: &WorldPrimitiveFrame, color_format: ColorFormat) -> Vec<MaterialBatch> {
    let mut batches: Vec<MaterialBatch> = Vec::new();
    let mut key_to_batch = BTreeMap::<MaterialResourceKey, usize>::new();
    for (index, quad) in frame.material_quads.iter().enumerate() {
        let key = material_key(quad, color_format);
        if let Some(batch_index) = key_to_batch.get(&key).copied() {
            batches[batch_index].indices.push(index);
        } else {
            let batch_index = batches.len();
            key_to_batch.insert(key, batch_index);
            batches.push(MaterialBatch {
                key,
                indices: vec![index],
            });
        }
    }
    batches
}

fn material_key(quad: &WorldMaterialQuadRequest, color_format: ColorFormat) -> MaterialResourceKey {
    MaterialResourceKey {
        material_id: quad.material_id,
        texture_id: quad.texture_id,
        material_mode: quad.material_mode,
        depth_policy: quad.depth_policy,
        cull_policy: quad.cull_policy,
        color_format,
    }
}

fn create_material_data_slot(
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
        push_f32(&mut out, segment.line_width);
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
        let atlas_width = (CRACK_STAGE_COUNT * CRACK_STAGE_SIZE) as f32;
        let stage_x = (quad.stage * CRACK_STAGE_SIZE) as f32;
        push_f32(&mut out, (stage_x + 0.5) / atlas_width);
        push_f32(&mut out, 0.5 / CRACK_STAGE_SIZE as f32);
        push_f32(&mut out, (CRACK_STAGE_SIZE - 1) as f32 / atlas_width);
        push_f32(
            &mut out,
            (CRACK_STAGE_SIZE - 1) as f32 / CRACK_STAGE_SIZE as f32,
        );
        for value in argb_to_rgba(quad.color_argb) {
            push_f32(&mut out, value);
        }
    }
    Ok(out)
}

fn packed_border_uniforms_for_batch(
    frame: &WorldPrimitiveFrame,
    batch: &BorderBatch,
) -> GalResult<Vec<u8>> {
    let mut out = Vec::with_capacity(WORLD_BORDER_UNIFORM_BYTES as usize);
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
    for quad in &frame.border_quads[batch.start..batch.start + batch.count] {
        for vertex in quad.vertices {
            for value in vertex {
                push_f32(&mut out, value);
            }
            push_f32(&mut out, 1.0);
        }
        for value in quad.uv_region {
            push_f32(&mut out, value);
        }
        push_f32(&mut out, quad.scroll[0]);
        push_f32(&mut out, quad.scroll[1]);
        push_f32(&mut out, quad.border_size);
        push_f32(&mut out, quad.distance_to_border);
        for value in argb_to_rgba(quad.color_argb) {
            push_f32(&mut out, value);
        }
    }
    Ok(out)
}

fn packed_material_uniforms_for_batch(
    frame: &WorldPrimitiveFrame,
    batch: &MaterialBatch,
) -> GalResult<Vec<u8>> {
    let mut out = Vec::with_capacity(WORLD_MATERIAL_UNIFORM_BYTES as usize);
    for value in frame.view_matrix {
        push_f32(&mut out, value);
    }
    for value in frame.projection_matrix {
        push_f32(&mut out, value);
    }
    push_f32(&mut out, frame.viewport_width as f32);
    push_f32(&mut out, frame.viewport_height as f32);
    push_f32(
        &mut out,
        material_registry::cutout_threshold(batch.key.material_id),
    );
    push_f32(&mut out, 0.0);
    for index in &batch.indices {
        let quad = &frame.material_quads[*index];
        for (vertex_index, vertex) in quad.vertices.iter().enumerate() {
            for value in vertex {
                push_f32(&mut out, *value);
            }
            push_f32(
                &mut out,
                if vertex_index == 0 {
                    quad.winding as f32
                } else {
                    1.0
                },
            );
        }
        push_f32(&mut out, quad.uvs[0][0]);
        push_f32(&mut out, quad.uvs[0][1]);
        push_f32(&mut out, quad.uvs[1][0]);
        push_f32(&mut out, quad.uvs[1][1]);
        push_f32(&mut out, quad.uvs[2][0]);
        push_f32(&mut out, quad.uvs[2][1]);
        push_f32(&mut out, quad.uvs[3][0]);
        push_f32(&mut out, quad.uvs[3][1]);
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

fn push_u32(out: &mut Vec<u8>, value: u32) {
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
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

fn crack_atlas_bytes(overrides: &BTreeMap<u32, WorldCrackTextureAsset>) -> GalResult<Vec<u8>> {
    let atlas_width = CRACK_STAGE_COUNT * CRACK_STAGE_SIZE;
    let mut stages: Vec<Vec<u8>> = Vec::with_capacity(CRACK_STAGE_COUNT as usize);
    for stage in 0..CRACK_STAGE_COUNT {
        if let Some(asset) = overrides.get(&stage) {
            stages.push(asset.rgba.clone());
        } else {
            let bytes = bundled_crack_stage_png(stage);
            let decoded = decode_crack_stage(bytes).expect("bundled crack stage texture is valid");
            stages.push(decoded);
        }
    }
    let mut out = Vec::with_capacity((atlas_width * CRACK_STAGE_SIZE * 4) as usize);
    let stage_row_bytes = (CRACK_STAGE_SIZE * 4) as usize;
    for row in 0..CRACK_STAGE_SIZE as usize {
        let row_start = row * stage_row_bytes;
        let row_end = row_start + stage_row_bytes;
        for stage_bytes in &stages {
            out.extend_from_slice(&stage_bytes[row_start..row_end]);
        }
    }
    Ok(out)
}

fn bundled_crack_stage_png(stage: u32) -> &'static [u8] {
    match stage {
        0 => {
            include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_0.png")
                .as_slice()
        }
        1 => {
            include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_1.png")
                .as_slice()
        }
        2 => {
            include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_2.png")
                .as_slice()
        }
        3 => {
            include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_3.png")
                .as_slice()
        }
        4 => {
            include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_4.png")
                .as_slice()
        }
        5 => {
            include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_5.png")
                .as_slice()
        }
        6 => {
            include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_6.png")
                .as_slice()
        }
        7 => {
            include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_7.png")
                .as_slice()
        }
        8 => {
            include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_8.png")
                .as_slice()
        }
        9 => {
            include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_9.png")
                .as_slice()
        }
        _ => unreachable!(),
    }
}

fn forcefield_texture_bytes() -> GalResult<(Vec<u8>, u32, u32)> {
    decode_png_rgba(
        include_bytes!("../../../resources/assets/minecraft/textures/misc/forcefield.png")
            .as_slice(),
        "world border forcefield",
    )
}

fn bundled_world_material_texture_bytes(texture_id: u32) -> GalResult<(Vec<u8>, u32, u32)> {
    let Some(texture) = material_registry::texture(texture_id) else {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world material texture id {texture_id}"),
        ));
    };
    decode_png_rgba(texture.default_png, texture.resource_location)
}

fn decode_crack_stage(bytes: &[u8]) -> GalResult<Vec<u8>> {
    let (rgba, width, height) = decode_png_rgba(bytes, "crack stage")?;
    if width != CRACK_STAGE_SIZE || height != CRACK_STAGE_SIZE {
        return Err(GalError::backend(format!(
            "unexpected crack stage dimensions {}x{}",
            width, height
        )));
    }
    Ok(rgba)
}

fn decode_png_rgba(bytes: &[u8], label: &str) -> GalResult<(Vec<u8>, u32, u32)> {
    let mut decoder = png::Decoder::new(std::io::BufReader::new(bytes));
    decoder.set_transformations(png::Transformations::EXPAND | png::Transformations::STRIP_16);
    let mut reader = decoder
        .read_info()
        .map_err(|error| GalError::backend(format!("failed to decode {label}: {error}")))?;
    let mut buf = vec![0; reader.output_buffer_size()];
    let info = reader
        .next_frame(&mut buf)
        .map_err(|error| GalError::backend(format!("failed to read {label}: {error}")))?;
    let data = &buf[..info.buffer_size()];
    let rgba = match info.color_type {
        png::ColorType::Rgba => data.to_vec(),
        png::ColorType::Rgb => {
            let mut rgba = Vec::with_capacity((info.width * info.height * 4) as usize);
            for pixel in data.chunks_exact(3) {
                rgba.extend_from_slice(&[pixel[0], pixel[1], pixel[2], 255]);
            }
            rgba
        }
        png::ColorType::GrayscaleAlpha => {
            let mut rgba = Vec::with_capacity((info.width * info.height * 4) as usize);
            for pixel in data.chunks_exact(2) {
                rgba.extend_from_slice(&[pixel[0], pixel[0], pixel[0], pixel[1]]);
            }
            rgba
        }
        png::ColorType::Grayscale => {
            let mut rgba = Vec::with_capacity((info.width * info.height * 4) as usize);
            for value in data {
                rgba.extend_from_slice(&[*value, *value, *value, 255]);
            }
            rgba
        }
        png::ColorType::Indexed => Err(GalError::backend(
            "indexed texture was not expanded by the PNG decoder",
        ))?,
    };
    Ok((rgba, info.width, info.height))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::backends::opengl::OpenGlBackend;
    use crate::render::vulkanic::backends::vulkan::VulkanBackend;
    use crate::render::vulkanic::backends::{
        mock::MockBackend, presentation_capabilities, vulkan_capabilities,
    };
    use crate::render::vulkanic::commands::ClearColor;
    use crate::render::vulkanic::resources::{
        BufferDesc, BufferUsage, FrameTargetDesc, MemoryDomain, RenderTargetDesc, TextureDesc,
        TextureDimension, TextureUsage, TextureViewDesc,
    };
    use xxhash_rust::xxh32::xxh32;

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
            background: WorldBackgroundRequest {
                enabled: true,
                sky_type: WORLD_BACKGROUND_SKY_OVERWORLD,
                color_argb: 0xff102844,
                load_intent: WORLD_BACKGROUND_LOAD_CLEAR,
                store_intent: WORLD_BACKGROUND_STORE_STORE,
                viewport_width: 128,
                viewport_height: 128,
            },
            segments,
            crack_quads: Vec::new(),
            border_quads: Vec::new(),
            material_quads: Vec::new(),
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

    fn border_quad(depth_policy: u32) -> WorldBorderQuadRequest {
        WorldBorderQuadRequest {
            stratum: WORLD_STRATUM_WORLD_BORDER,
            texture_id: WORLD_BORDER_TEXTURE_FORCEFIELD,
            depth_policy,
            blend_policy: WORLD_BORDER_BLEND_OVERLAY,
            cull_policy: WORLD_BORDER_CULL_NONE,
            color_argb: 0xdd55ff55,
            border_size: 8.0,
            distance_to_border: 2.0,
            scroll: [0.25, 0.25],
            uv_region: [0.0, 1.0, 2.0, -4.0],
            vertices: [
                [-1.0, -2.0, -3.0],
                [1.0, -2.0, -3.0],
                [1.0, 2.0, -3.0],
                [-1.0, 2.0, -3.0],
            ],
            viewport_width: 128,
            viewport_height: 128,
        }
    }

    fn material_quad(material_mode: u32, depth_policy: u32) -> WorldMaterialQuadRequest {
        WorldMaterialQuadRequest {
            stratum: WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY,
            material_id: if material_mode == WORLD_MATERIAL_MODE_CUTOUT {
                WORLD_MATERIAL_ID_DEFAULT_CUTOUT
            } else {
                WORLD_MATERIAL_ID_DEFAULT_OPAQUE
            },
            texture_id: WORLD_MATERIAL_TEXTURE_DEFAULT,
            material_mode,
            depth_policy,
            cull_policy: WORLD_CULL_BACK,
            topology: WORLD_TOPOLOGY_TRIANGLES,
            winding: WORLD_WINDING_CCW,
            color_argb: 0xffffffff,
            vertices: [
                [-0.5, -0.5, -1.0],
                [0.5, -0.5, -1.0],
                [0.5, 0.5, -1.0],
                [-0.5, 0.5, -1.0],
            ],
            uvs: [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
            viewport_width: 128,
            viewport_height: 128,
        }
    }

    fn read_f32(bytes: &[u8], index: usize) -> f32 {
        let offset = index * std::mem::size_of::<f32>();
        f32::from_le_bytes(bytes[offset..offset + 4].try_into().unwrap())
    }

    fn frame_with_world_work(width: u32, height: u32) -> WorldPrimitiveFrame {
        let mut frame = frame(vec![segment(WORLD_DEPTH_POLICY_TEST_WRITE, 0xff000000)]);
        frame.viewport_width = width;
        frame.viewport_height = height;
        frame.background.viewport_width = width;
        frame.background.viewport_height = height;
        frame.segments[0].viewport_width = width;
        frame.segments[0].viewport_height = height;
        let mut crack = crack_quad(4, WORLD_DEPTH_POLICY_TEST_WRITE);
        crack.viewport_width = width;
        crack.viewport_height = height;
        frame.crack_quads.push(crack);
        frame
    }

    fn frame_with_border(width: u32, height: u32) -> WorldPrimitiveFrame {
        let mut frame = frame(Vec::new());
        frame.viewport_width = width;
        frame.viewport_height = height;
        frame.background.viewport_width = width;
        frame.background.viewport_height = height;
        let mut border = border_quad(WORLD_DEPTH_POLICY_TEST_WRITE);
        border.viewport_width = width;
        border.viewport_height = height;
        frame.border_quads.push(border);
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
    fn material_batches_group_interleaved_compatible_state_by_key() {
        let mut frame = frame(Vec::new());
        let opaque = material_quad(WORLD_MATERIAL_MODE_OPAQUE, WORLD_DEPTH_POLICY_TEST_WRITE);
        let cutout = material_quad(WORLD_MATERIAL_MODE_CUTOUT, WORLD_DEPTH_POLICY_TEST_WRITE);
        let cutout_no_depth =
            material_quad(WORLD_MATERIAL_MODE_CUTOUT, WORLD_DEPTH_POLICY_DISABLED);
        frame.material_quads = vec![
            opaque.clone(),
            cutout.clone(),
            opaque.clone(),
            cutout_no_depth,
            cutout,
            opaque,
        ];
        let batches = material_batches(&frame, ColorFormat::Bgra8Unorm);
        assert_eq!(3, batches.len());
        assert_eq!(3, batches[0].count());
        assert_eq!(vec![0, 2, 5], batches[0].indices);
        assert_eq!(WORLD_MATERIAL_MODE_OPAQUE, batches[0].key.material_mode);
        assert_eq!(2, batches[1].count());
        assert_eq!(vec![1, 4], batches[1].indices);
        assert_eq!(WORLD_MATERIAL_MODE_CUTOUT, batches[1].key.material_mode);
        assert_eq!(WORLD_DEPTH_POLICY_DISABLED, batches[2].key.depth_policy);
    }

    #[test]
    fn cutout_material_batches_carry_alpha_discard_threshold() {
        let mut frame = frame(Vec::new());
        frame.material_quads = vec![
            material_quad(WORLD_MATERIAL_MODE_OPAQUE, WORLD_DEPTH_POLICY_TEST_WRITE),
            material_quad(WORLD_MATERIAL_MODE_CUTOUT, WORLD_DEPTH_POLICY_TEST_WRITE),
        ];
        let batches = material_batches(&frame, ColorFormat::Bgra8Unorm);
        assert_eq!(2, batches.len());

        let opaque_uniforms = packed_material_uniforms_for_batch(&frame, &batches[0]).unwrap();
        let cutout_uniforms = packed_material_uniforms_for_batch(&frame, &batches[1]).unwrap();

        assert_eq!(0.0, read_f32(&opaque_uniforms, 34));
        assert_eq!(0.5, read_f32(&cutout_uniforms, 34));
    }

    #[test]
    fn semantic_material_registry_canonicalizes_legacy_keys_and_carries_metadata() {
        assert_eq!(
            Some(WORLD_MATERIAL_TEXTURE_STONE),
            material::canonical_texture_id(1)
        );
        assert_eq!(
            Some(WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_15),
            material::canonical_texture_id(215)
        );
        assert_eq!(
            Some(WORLD_MATERIAL_ID_BLOCK_MARKER_CUTOUT),
            material::canonical_material_id(100)
        );
        assert!(material::material_matches_mode(
            WORLD_MATERIAL_ID_BLOCK_MARKER_CUTOUT,
            WORLD_MATERIAL_MODE_CUTOUT
        ));
        assert!(!material::material_matches_mode(
            WORLD_MATERIAL_ID_BLOCK_MARKER_CUTOUT,
            WORLD_MATERIAL_MODE_OPAQUE
        ));
        let texture = material_registry::texture(WORLD_MATERIAL_TEXTURE_OAK_LEAVES).unwrap();
        assert_eq!(
            "minecraft:textures/block/oak_leaves.png",
            texture.resource_location
        );
        assert_eq!(
            0.5,
            material_registry::cutout_threshold(WORLD_MATERIAL_ID_CUTOUT_TEXTURED)
        );
    }

    #[test]
    fn packed_material_uniforms_store_only_render_needed_per_quad_data() {
        let mut frame = frame(Vec::new());
        let mut quad = material_quad(WORLD_MATERIAL_MODE_OPAQUE, WORLD_DEPTH_POLICY_TEST_WRITE);
        quad.winding = WORLD_WINDING_CW;
        frame.material_quads.push(quad);
        let batches = material_batches(&frame, ColorFormat::Bgra8Unorm);

        let uniforms = packed_material_uniforms_for_batch(&frame, &batches[0]).unwrap();

        assert_eq!(
            WORLD_MATERIAL_HEADER_BYTES + WORLD_MATERIAL_QUAD_BYTES,
            uniforms.len()
        );
        assert_eq!(WORLD_WINDING_CW as f32, read_f32(&uniforms, 39));
    }

    #[test]
    fn interleaved_material_quads_lower_to_one_draw_per_material_key() {
        let mut gal = gal();
        let target = frame_target(&mut gal, 1, 128, 128);
        let mut frontend = WorldPrimitiveFrontend::default();
        let mut frame = frame(Vec::new());
        let mut stone = material_quad(WORLD_MATERIAL_MODE_OPAQUE, WORLD_DEPTH_POLICY_TEST_WRITE);
        stone.texture_id = WORLD_MATERIAL_TEXTURE_STONE;
        let mut dirt = material_quad(WORLD_MATERIAL_MODE_OPAQUE, WORLD_DEPTH_POLICY_TEST_WRITE);
        dirt.texture_id = WORLD_MATERIAL_TEXTURE_DIRT;
        let mut leaves = material_quad(WORLD_MATERIAL_MODE_CUTOUT, WORLD_DEPTH_POLICY_TEST_WRITE);
        leaves.texture_id = WORLD_MATERIAL_TEXTURE_OAK_LEAVES;
        frame.material_quads = vec![
            stone.clone(),
            dirt.clone(),
            leaves.clone(),
            stone.clone(),
            dirt.clone(),
            leaves.clone(),
            stone,
            dirt,
            leaves,
        ];

        let (_ops, stats) = frontend
            .append_frame_ops(&mut gal, 1, target, frame)
            .unwrap();

        assert_eq!(9, stats.material_quad_count);
        assert_eq!(3, stats.material_batch_count);
        assert_eq!(3, stats.material_draw_count);
    }

    #[test]
    fn validate_frame_rejects_unknown_depth_policy() {
        let error = validate_frame(&frame(vec![segment(99, 0xff000000)])).unwrap_err();
        assert_eq!(StatusCode::UnknownEnum, error.code);
    }

    #[test]
    fn validate_frame_rejects_malformed_material_quads() {
        let mut mismatched_material_frame = frame(Vec::new());
        let mut quad = material_quad(WORLD_MATERIAL_MODE_OPAQUE, WORLD_DEPTH_POLICY_TEST_WRITE);
        quad.material_id = WORLD_MATERIAL_ID_DEFAULT_CUTOUT;
        mismatched_material_frame.material_quads.push(quad);
        let error = validate_frame(&mismatched_material_frame).unwrap_err();
        assert_eq!(StatusCode::InvalidArgument, error.code);

        let mut bad_texture_frame = frame(Vec::new());
        let mut quad = material_quad(WORLD_MATERIAL_MODE_OPAQUE, WORLD_DEPTH_POLICY_TEST_WRITE);
        quad.texture_id = 99;
        bad_texture_frame.material_quads.push(quad);
        let error = validate_frame(&bad_texture_frame).unwrap_err();
        assert_eq!(StatusCode::UnknownEnum, error.code);
    }

    #[test]
    fn material_quads_lower_to_indexed_draw_and_reuse_resources() {
        let mut gal = gal();
        let target = frame_target(&mut gal, 1, 128, 128);
        let mut frontend = WorldPrimitiveFrontend::default();
        let mut frame = frame(Vec::new());
        frame.material_quads.push(material_quad(
            WORLD_MATERIAL_MODE_OPAQUE,
            WORLD_DEPTH_POLICY_TEST_WRITE,
        ));
        let (ops, stats) = frontend
            .append_frame_ops(&mut gal, 1, target, frame.clone())
            .unwrap();
        assert_eq!(1, stats.material_quad_count);
        assert_eq!(1, stats.material_batch_count);
        assert_eq!(1, stats.material_draw_count);
        assert_eq!(1, stats.material_cache_misses);
        assert!(ops
            .iter()
            .any(|op| matches!(op, CommandOp::SetIndexBuffer { .. })));
        assert!(ops.iter().any(|op| matches!(
            op,
            CommandOp::DrawIndexed {
                indices: 6,
                instances: 1
            }
        )));

        let (_ops, stats) = frontend
            .append_frame_ops(&mut gal, 2, target, frame)
            .unwrap();
        assert_eq!(1, stats.material_cache_hits);
        assert_eq!(0, stats.material_cache_misses);
    }

    #[test]
    fn material_data_slot_reuses_streaming_buffer_across_frames() {
        let mut gal = gal();
        let target = frame_target(&mut gal, 1, 128, 128);
        let mut frontend = WorldPrimitiveFrontend::default();
        let mut frame = frame(Vec::new());
        frame.material_quads = vec![
            material_quad(WORLD_MATERIAL_MODE_OPAQUE, WORLD_DEPTH_POLICY_TEST_WRITE),
            material_quad(WORLD_MATERIAL_MODE_OPAQUE, WORLD_DEPTH_POLICY_TEST_WRITE),
        ];

        let (_ops, first_stats) = frontend
            .append_frame_ops(&mut gal, 1, target, frame.clone())
            .unwrap();
        assert_eq!(1, first_stats.material_batch_count);
        assert_eq!(1, first_stats.material_cache_misses);
        let key = material_key(&frame.material_quads[0], ColorFormat::Bgra8Unorm);
        let resources = frontend.material_resources.get(&key).unwrap();
        assert_eq!(1, resources.data_slots.len());
        let first_uniform_buffer = resources.data_slots[0].uniform_buffer;
        let first_resource_set = resources.data_slots[0].resource_set;

        let (_ops, second_stats) = frontend
            .append_frame_ops(&mut gal, 2, target, frame)
            .unwrap();
        assert_eq!(1, second_stats.material_batch_count);
        assert_eq!(1, second_stats.material_cache_hits);
        assert_eq!(0, second_stats.material_cache_misses);
        let resources = frontend.material_resources.get(&key).unwrap();
        assert_eq!(1, resources.data_slots.len());
        assert_eq!(first_uniform_buffer, resources.data_slots[0].uniform_buffer);
        assert_eq!(first_resource_set, resources.data_slots[0].resource_set);
    }

    #[test]
    fn material_asset_update_rejects_stale_and_preserves_last_valid_generation() {
        let mut gal = gal();
        let mut frontend = WorldPrimitiveFrontend::default();
        frontend
            .apply_world_material_asset_update(
                &mut gal,
                1,
                vec![WorldMaterialAssetPayload {
                    texture_id: WORLD_MATERIAL_TEXTURE_DEFAULT,
                    png_bytes: Vec::new(),
                }],
            )
            .unwrap();
        assert_eq!(1, frontend.material_asset_generation);
        let error = frontend
            .apply_world_material_asset_update(
                &mut gal,
                1,
                vec![WorldMaterialAssetPayload {
                    texture_id: WORLD_MATERIAL_TEXTURE_DEFAULT,
                    png_bytes: Vec::new(),
                }],
            )
            .unwrap_err();
        assert_eq!(StatusCode::InvalidArgument, error.code);
        assert_eq!(1, frontend.material_asset_generation);
    }

    #[test]
    fn shared_runtime_material_scene_matches_opengl_and_vulkan_when_available() {
        let vulkan = match run_runtime_material_scene(RuntimeBackend::Vulkan, 160, 112) {
            Ok(report) => report,
            Err(error) => {
                assert_environment_gap(&error, "Vulkan");
                return;
            }
        };
        let opengl = match run_runtime_material_scene(RuntimeBackend::OpenGl, 160, 112) {
            Ok(report) => report,
            Err(error) => {
                eprintln!("OpenGL material runtime conformance unavailable: {error}");
                assert_environment_gap(&error, "OpenGL");
                return;
            }
        };

        assert_eq!(vulkan.semantic_hash, opengl.semantic_hash);
        assert_eq!(vulkan.initial_hash, vulkan.rollback_hash);
        assert_eq!(opengl.initial_hash, opengl.rollback_hash);
        assert_ne!(vulkan.initial_hash, vulkan.reloaded_hash);
        assert_ne!(opengl.initial_hash, opengl.reloaded_hash);
        assert_material_feature_parity(&vulkan, &opengl);
        assert!(vulkan.initial_stats.material_quad_count >= 8);
        assert!(vulkan.initial_stats.material_batch_count >= 5);
        assert!(vulkan.initial_stats.material_draw_count >= 5);
        assert!(vulkan.initial_stats.material_cache_misses >= 5);
        assert!(vulkan.rollback_stats.material_cache_hits >= 5);
        assert_eq!(
            vulkan.initial_stats.material_quad_count,
            opengl.initial_stats.material_quad_count
        );
        assert_eq!(
            vulkan.initial_stats.material_batch_count,
            opengl.initial_stats.material_batch_count
        );
        assert_eq!(
            vulkan.initial_stats.material_draw_count,
            opengl.initial_stats.material_draw_count
        );
        assert!(vulkan.resize_hash != 0);
        assert!(opengl.resize_hash != 0);
        write_material_comparison_report(&vulkan, &opengl).unwrap();
    }

    #[test]
    fn semantic_background_clear_is_first_frame_operation() {
        let mut gal = gal();
        let target = frame_target(&mut gal, 1, 128, 128);
        let mut frontend = WorldPrimitiveFrontend::default();
        let frame = frame(Vec::new());
        let (ops, stats) = frontend
            .append_frame_ops(&mut gal, 1, target, frame)
            .unwrap();

        assert_eq!(1, stats.background_clear_count);
        assert_eq!(0, stats.background_diagnostic_fallback_count);
        assert_eq!(
            WORLD_BACKGROUND_SKY_OVERWORLD as u64,
            stats.background_sky_type
        );
        assert_eq!(0xff102844, stats.background_color_argb);
        assert!(matches!(ops.first(), Some(CommandOp::Barrier(_))));
        let Some(CommandOp::BeginPass { colors, .. }) = ops.get(1) else {
            panic!("background clear should begin the first pass after the depth barrier");
        };
        let clear = colors[0].clear_color.expect("background clear color");
        assert!((clear.r - (0x10 as f32 / 255.0)).abs() < 0.001);
        assert!((clear.g - (0x28 as f32 / 255.0)).abs() < 0.001);
        assert!((clear.b - (0x44 as f32 / 255.0)).abs() < 0.001);
    }

    #[test]
    fn diagnostic_background_fallback_remains_explicit() {
        let mut gal = gal();
        let target = frame_target(&mut gal, 1, 128, 128);
        let mut frontend = WorldPrimitiveFrontend::default();
        let mut frame = frame(Vec::new());
        frame.background = WorldBackgroundRequest::default();

        let (ops, stats) = frontend
            .append_frame_ops(&mut gal, 1, target, frame)
            .unwrap();

        assert_eq!(0, stats.background_clear_count);
        assert_eq!(1, stats.background_diagnostic_fallback_count);
        let Some(CommandOp::BeginPass { colors, .. }) = ops.get(1) else {
            panic!("diagnostic fallback should still clear the shell frame");
        };
        let clear = colors[0].clear_color.expect("diagnostic clear color");
        assert!((clear.r - 0.063).abs() < 0.001);
        assert!((clear.g - 0.157).abs() < 0.001);
        assert!((clear.b - 0.855).abs() < 0.001);
    }

    #[test]
    fn partial_crack_submission_loads_existing_borrowed_depth_before_first_draw() {
        let mut gal = gal();
        let target = frame_target(&mut gal, 1, 128, 128);
        let mut frontend = WorldPrimitiveFrontend::default();
        let mut frame = frame(Vec::new());
        frame.background = WorldBackgroundRequest::default();
        frame
            .crack_quads
            .push(crack_quad(4, WORLD_DEPTH_POLICY_TEST_WRITE));

        let (ops, stats) = frontend
            .append_frame_ops_inner(&mut gal, 1, target, frame, false)
            .unwrap();

        assert_eq!(1, stats.crack_quad_count);
        let first_pass = ops
            .iter()
            .find_map(|op| match op {
                CommandOp::BeginPass {
                    colors,
                    depth_stencil,
                    ..
                } => Some((colors, depth_stencil)),
                _ => None,
            })
            .expect("partial crack submission should begin a draw pass");
        assert_eq!(AttachmentLoadOp::Load, first_pass.0[0].load_op);
        assert_eq!(
            AttachmentLoadOp::Load,
            first_pass
                .1
                .as_ref()
                .expect("crack pass should carry a depth attachment")
                .load_op
        );
    }

    #[test]
    fn partial_outline_submission_loads_existing_borrowed_depth_before_first_draw() {
        let mut gal = gal();
        let target = frame_target(&mut gal, 1, 128, 128);
        let mut frontend = WorldPrimitiveFrontend::default();
        let frame = frame(vec![segment(WORLD_DEPTH_POLICY_TEST_NO_WRITE, 0xff000000)]);

        let (ops, stats) = frontend
            .append_frame_ops_inner(&mut gal, 1, target, frame, false)
            .unwrap();

        assert_eq!(1, stats.segment_count);
        let first_pass = ops
            .iter()
            .find_map(|op| match op {
                CommandOp::BeginPass {
                    colors,
                    depth_stencil,
                    ..
                } => Some((colors, depth_stencil)),
                _ => None,
            })
            .expect("partial outline submission should begin a draw pass");
        assert_eq!(AttachmentLoadOp::Load, first_pass.0[0].load_op);
        assert_eq!(
            AttachmentLoadOp::Load,
            first_pass
                .1
                .as_ref()
                .expect("outline pass should carry a depth attachment")
                .load_op
        );
    }

    #[test]
    fn validate_frame_rejects_malformed_background_metadata() {
        let mut bad_sky = frame(Vec::new());
        bad_sky.background.sky_type = 99;
        assert_eq!(
            StatusCode::UnknownEnum,
            validate_frame(&bad_sky).unwrap_err().code
        );

        let mut bad_viewport = frame(Vec::new());
        bad_viewport.background.viewport_width = 127;
        assert_eq!(
            StatusCode::InvalidArgument,
            validate_frame(&bad_viewport).unwrap_err().code
        );
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
    fn border_batches_preserve_contiguous_depth_groups() {
        let mut frame = frame(Vec::new());
        frame.border_quads = vec![
            border_quad(WORLD_DEPTH_POLICY_DISABLED),
            border_quad(WORLD_DEPTH_POLICY_DISABLED),
            border_quad(WORLD_DEPTH_POLICY_TEST_WRITE),
        ];
        let batches = border_batches(&frame);
        assert_eq!(2, batches.len());
        assert_eq!(
            BorderBatch {
                start: 0,
                count: 2,
                depth_policy: WORLD_DEPTH_POLICY_DISABLED,
            },
            batches[0]
        );
        assert_eq!(1, batches[1].count);
        assert_eq!(WORLD_DEPTH_POLICY_TEST_WRITE, batches[1].depth_policy);
    }

    #[test]
    fn packed_crack_uniforms_select_stage_regions() {
        let atlas_width = (CRACK_STAGE_COUNT * CRACK_STAGE_SIZE) as f32;
        for stage in 0..CRACK_STAGE_COUNT {
            let mut frame = frame(Vec::new());
            frame
                .crack_quads
                .push(crack_quad(stage, WORLD_DEPTH_POLICY_TEST_WRITE));
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
            let v = f32::from_ne_bytes(bytes[uv_offset + 4..uv_offset + 8].try_into().unwrap());
            let width =
                f32::from_ne_bytes(bytes[uv_offset + 8..uv_offset + 12].try_into().unwrap());
            let height =
                f32::from_ne_bytes(bytes[uv_offset + 12..uv_offset + 16].try_into().unwrap());
            assert_eq!(((stage * CRACK_STAGE_SIZE) as f32 + 0.5) / atlas_width, u);
            assert_eq!(0.5 / CRACK_STAGE_SIZE as f32, v);
            assert_eq!((CRACK_STAGE_SIZE - 1) as f32 / atlas_width, width);
            assert_eq!(
                (CRACK_STAGE_SIZE - 1) as f32 / CRACK_STAGE_SIZE as f32,
                height
            );
        }
    }

    #[test]
    fn crack_atlas_bytes_pack_stage_rows_horizontally() {
        let mut overrides = BTreeMap::new();
        for stage in 0..CRACK_STAGE_COUNT {
            let mut rgba = Vec::with_capacity((CRACK_STAGE_SIZE * CRACK_STAGE_SIZE * 4) as usize);
            for y in 0..CRACK_STAGE_SIZE {
                for x in 0..CRACK_STAGE_SIZE {
                    rgba.extend_from_slice(&[stage as u8, x as u8, y as u8, 255]);
                }
            }
            overrides.insert(stage, WorldCrackTextureAsset { rgba });
        }

        let atlas = crack_atlas_bytes(&overrides).unwrap();
        let atlas_width = CRACK_STAGE_COUNT * CRACK_STAGE_SIZE;
        assert_eq!((atlas_width * CRACK_STAGE_SIZE * 4) as usize, atlas.len());
        for stage in [0_u32, 4, 9] {
            for (x, y) in [(0_u32, 0_u32), (7, 3), (15, 15)] {
                let offset = (((y * atlas_width) + (stage * CRACK_STAGE_SIZE + x)) * 4) as usize;
                assert_eq!(
                    &[stage as u8, x as u8, y as u8, 255],
                    &atlas[offset..offset + 4]
                );
            }
        }
    }

    #[test]
    fn packed_border_uniforms_include_scroll_uv_and_color() {
        let mut frame = frame(Vec::new());
        frame
            .border_quads
            .push(border_quad(WORLD_DEPTH_POLICY_TEST_WRITE));
        let bytes = packed_border_uniforms_for_batch(
            &frame,
            &BorderBatch {
                start: 0,
                count: 1,
                depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
            },
        )
        .unwrap();
        assert_eq!(
            WORLD_BORDER_HEADER_BYTES + WORLD_BORDER_QUAD_BYTES,
            bytes.len()
        );
        let read = |index: usize| {
            f32::from_ne_bytes(
                bytes[index * 4..index * 4 + 4]
                    .try_into()
                    .expect("float slice"),
            )
        };
        let base = WORLD_BORDER_HEADER_BYTES / 4;
        assert_eq!(0.0, read(base + 16));
        assert_eq!(1.0, read(base + 17));
        assert_eq!(2.0, read(base + 18));
        assert_eq!(-4.0, read(base + 19));
        assert_eq!(0.25, read(base + 20));
        assert_eq!(8.0, read(base + 22));
    }

    #[test]
    fn world_border_asset_update_copies_valid_generation_and_rejects_stale() {
        let mut gal = gal();
        let mut frontend = WorldPrimitiveFrontend::default();
        let mut bytes =
            include_bytes!("../../../resources/assets/minecraft/textures/misc/forcefield.png")
                .to_vec();
        frontend
            .apply_world_border_asset_update(
                &mut gal,
                2,
                WorldBorderAssetPayload {
                    texture_id: WORLD_BORDER_TEXTURE_FORCEFIELD,
                    png_bytes: bytes.clone(),
                },
            )
            .unwrap();
        bytes.clear();
        assert_eq!(2, frontend.border_asset_generation);
        assert!(frontend.border_asset_override.is_some());
        let stale = frontend
            .apply_world_border_asset_update(
                &mut gal,
                2,
                WorldBorderAssetPayload {
                    texture_id: WORLD_BORDER_TEXTURE_FORCEFIELD,
                    png_bytes: Vec::new(),
                },
            )
            .unwrap_err();
        assert_eq!(StatusCode::InvalidArgument, stale.code);
        assert_eq!(2, frontend.border_asset_generation);
        assert!(frontend.border_asset_override.is_some());
    }

    #[test]
    fn malformed_world_border_asset_update_preserves_last_valid_texture() {
        let mut gal = gal();
        let mut frontend = WorldPrimitiveFrontend::default();
        frontend
            .apply_world_border_asset_update(
                &mut gal,
                2,
                WorldBorderAssetPayload {
                    texture_id: WORLD_BORDER_TEXTURE_FORCEFIELD,
                    png_bytes: include_bytes!(
                        "../../../resources/assets/minecraft/textures/misc/forcefield.png"
                    )
                    .to_vec(),
                },
            )
            .unwrap();
        let before = frontend
            .border_asset_override
            .as_ref()
            .unwrap()
            .rgba
            .clone();
        let error = frontend
            .apply_world_border_asset_update(
                &mut gal,
                3,
                WorldBorderAssetPayload {
                    texture_id: WORLD_BORDER_TEXTURE_FORCEFIELD,
                    png_bytes: vec![1, 2, 3, 4],
                },
            )
            .unwrap_err();
        assert!(error
            .message
            .contains("failed to decode world border forcefield override"));
        assert_eq!(2, frontend.border_asset_generation);
        assert_eq!(1, frontend.border_asset_update_failures);
        assert_eq!(
            before,
            frontend.border_asset_override.as_ref().unwrap().rgba
        );
    }

    #[test]
    fn unknown_world_border_asset_texture_is_rejected_without_generation_advance() {
        let mut gal = gal();
        let mut frontend = WorldPrimitiveFrontend::default();
        let error = frontend
            .apply_world_border_asset_update(
                &mut gal,
                2,
                WorldBorderAssetPayload {
                    texture_id: 99,
                    png_bytes: include_bytes!(
                        "../../../resources/assets/minecraft/textures/misc/forcefield.png"
                    )
                    .to_vec(),
                },
            )
            .unwrap_err();
        assert_eq!(StatusCode::UnknownEnum, error.code);
        assert_eq!(0, frontend.border_asset_generation);
        assert_eq!(1, frontend.border_asset_update_failures);
        assert!(frontend.border_asset_override.is_none());
    }

    #[test]
    fn world_border_asset_update_invalidates_border_resources_without_per_frame_uploads() {
        let mut gal = gal();
        let target = frame_target(&mut gal, 1, 128, 128);
        let mut frontend = WorldPrimitiveFrontend::default();
        let (_, first) = frontend
            .append_frame_ops(&mut gal, 1, target, frame_with_border(128, 128))
            .unwrap();
        assert_eq!(1, first.border_cache_misses);
        let (_, second) = frontend
            .append_frame_ops(&mut gal, 2, target, frame_with_border(128, 128))
            .unwrap();
        assert_eq!(1, second.border_cache_hits);
        assert_eq!(0, second.border_cache_misses);

        frontend
            .apply_world_border_asset_update(
                &mut gal,
                2,
                WorldBorderAssetPayload {
                    texture_id: WORLD_BORDER_TEXTURE_FORCEFIELD,
                    png_bytes: include_bytes!(
                        "../../../resources/assets/minecraft/textures/misc/forcefield.png"
                    )
                    .to_vec(),
                },
            )
            .unwrap();
        let (_, after_reload) = frontend
            .append_frame_ops(&mut gal, 3, target, frame_with_border(128, 128))
            .unwrap();
        assert_eq!(1, after_reload.border_cache_misses);
        assert_eq!(0, after_reload.border_cache_hits);
        let (_, steady) = frontend
            .append_frame_ops(&mut gal, 4, target, frame_with_border(128, 128))
            .unwrap();
        assert_eq!(1, steady.border_cache_hits);
        assert_eq!(0, steady.border_cache_misses);
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

    #[derive(Clone, Copy)]
    enum RuntimeBackend {
        Vulkan,
        OpenGl,
    }

    impl RuntimeBackend {
        fn label(self) -> &'static str {
            match self {
                Self::Vulkan => "vulkan",
                Self::OpenGl => "opengl",
            }
        }

        fn name(self) -> &'static str {
            match self {
                Self::Vulkan => "Rust Vulkan",
                Self::OpenGl => "Rust OpenGL",
            }
        }
    }

    #[derive(Clone)]
    struct RuntimeMaterialReport {
        semantic_hash: u32,
        initial_hash: u32,
        rollback_hash: u32,
        reloaded_hash: u32,
        resize_hash: u32,
        feature_non_clear: Vec<usize>,
        initial_stats: WorldPrimitiveSubmitStats,
        rollback_stats: WorldPrimitiveSubmitStats,
    }

    fn assert_material_feature_parity(
        vulkan: &RuntimeMaterialReport,
        opengl: &RuntimeMaterialReport,
    ) {
        assert_eq!(
            vulkan.feature_non_clear.len(),
            opengl.feature_non_clear.len()
        );
        for (index, (vulkan_count, opengl_count)) in vulkan
            .feature_non_clear
            .iter()
            .zip(opengl.feature_non_clear.iter())
            .enumerate()
        {
            assert!(
                *vulkan_count > 0,
                "Vulkan material feature crop {index} was blank"
            );
            assert!(
                *opengl_count > 0,
                "OpenGL material feature crop {index} was blank"
            );
            let max = (*vulkan_count).max(*opengl_count) as f32;
            let delta = vulkan_count.abs_diff(*opengl_count) as f32 / max;
            assert!(
                delta <= 0.10,
                "material feature crop {index} diverged too far: Vulkan={vulkan_count}, OpenGL={opengl_count}, delta={delta:.3}"
            );
        }
    }

    fn write_material_comparison_report(
        vulkan: &RuntimeMaterialReport,
        opengl: &RuntimeMaterialReport,
    ) -> GalResult<()> {
        let root = repo_root().join("logs/rust-vulkanic/material-conformance");
        std::fs::create_dir_all(&root).map_err(|error| {
            GalError::backend(format!(
                "failed to create material conformance comparison dir: {error}"
            ))
        })?;
        let max_feature_delta = vulkan
            .feature_non_clear
            .iter()
            .zip(opengl.feature_non_clear.iter())
            .map(|(left, right)| {
                let max = (*left).max(*right) as f32;
                if max == 0.0 {
                    0.0
                } else {
                    left.abs_diff(*right) as f32 / max
                }
            })
            .fold(0.0_f32, f32::max);
        let json = format!(
            "{{\n  \"artifact_class\":\"world_material_runtime_conformance_comparison\",\n  \"semantic_requests_identical\":{},\n  \"semantic_request_hash\":\"{:08x}\",\n  \"comparison\":\"bounded_normalized_feature_crops\",\n  \"normalization_reason\":\"opaque/cutout material semantics are equivalent, but full-frame byte hashes may differ at API rasterization and texture-sampling edges\",\n  \"vulkan_initial_hash\":\"{:08x}\",\n  \"opengl_initial_hash\":\"{:08x}\",\n  \"max_feature_relative_delta\":{:.6},\n  \"vulkan_material_quad_count\":{},\n  \"opengl_material_quad_count\":{},\n  \"vulkan_material_batch_count\":{},\n  \"opengl_material_batch_count\":{},\n  \"vulkan_material_draw_count\":{},\n  \"opengl_material_draw_count\":{}\n}}\n",
            vulkan.semantic_hash == opengl.semantic_hash,
            vulkan.semantic_hash,
            vulkan.initial_hash,
            opengl.initial_hash,
            max_feature_delta,
            vulkan.initial_stats.material_quad_count,
            opengl.initial_stats.material_quad_count,
            vulkan.initial_stats.material_batch_count,
            opengl.initial_stats.material_batch_count,
            vulkan.initial_stats.material_draw_count,
            opengl.initial_stats.material_draw_count
        );
        std::fs::write(root.join("comparison-latest.json"), json).map_err(|error| {
            GalError::backend(format!(
                "failed to write material conformance comparison report: {error}"
            ))
        })
    }

    fn run_runtime_material_scene(
        backend: RuntimeBackend,
        width: u32,
        height: u32,
    ) -> GalResult<RuntimeMaterialReport> {
        let backend_impl: Box<dyn crate::render::vulkanic::backends::Backend> = match backend {
            RuntimeBackend::Vulkan => Box::new(VulkanBackend::new(
                "MattMC world material runtime conformance",
            )?),
            RuntimeBackend::OpenGl => Box::new(OpenGlBackend::new(
                "MattMC world material runtime conformance",
            )?),
        };
        let mut gal = VulkanicGal::new_with_backend(backend_impl, false);
        let mut frontend = WorldPrimitiveFrontend::default();
        let initial_asset = material_scene_png(0);
        frontend.apply_world_material_asset_update(
            &mut gal,
            1,
            vec![WorldMaterialAssetPayload {
                texture_id: WORLD_MATERIAL_TEXTURE_DEFAULT,
                png_bytes: initial_asset,
            }],
        )?;
        let semantic_frame = material_runtime_frame(width, height);
        let semantic_hash = semantic_hash(&semantic_frame);
        let initial = render_material_scene(
            &mut gal,
            &mut frontend,
            1,
            width,
            height,
            semantic_frame.clone(),
            &format!("{}-initial", backend.label()),
        )?;

        let malformed = frontend.apply_world_material_asset_update(
            &mut gal,
            2,
            vec![WorldMaterialAssetPayload {
                texture_id: WORLD_MATERIAL_TEXTURE_DEFAULT,
                png_bytes: b"not-a-png".to_vec(),
            }],
        );
        assert!(malformed.is_err());
        let rollback = render_material_scene(
            &mut gal,
            &mut frontend,
            2,
            width,
            height,
            semantic_frame.clone(),
            &format!("{}-rollback", backend.label()),
        )?;

        frontend.apply_world_material_asset_update(
            &mut gal,
            3,
            vec![WorldMaterialAssetPayload {
                texture_id: WORLD_MATERIAL_TEXTURE_DEFAULT,
                png_bytes: material_scene_png(1),
            }],
        )?;
        let reloaded = render_material_scene(
            &mut gal,
            &mut frontend,
            3,
            width,
            height,
            semantic_frame,
            &format!("{}-reloaded", backend.label()),
        )?;

        let resize_frame = material_runtime_frame(width + 32, height + 16);
        let resized = render_material_scene(
            &mut gal,
            &mut frontend,
            4,
            width + 32,
            height + 16,
            resize_frame,
            &format!("{}-resized", backend.label()),
        )?;

        write_material_report(
            backend,
            width,
            height,
            semantic_hash,
            &initial,
            &rollback,
            &reloaded,
            &resized,
        )?;

        Ok(RuntimeMaterialReport {
            semantic_hash,
            initial_hash: initial.hash,
            rollback_hash: rollback.hash,
            reloaded_hash: reloaded.hash,
            resize_hash: resized.hash,
            feature_non_clear: initial.feature_non_clear,
            initial_stats: initial.stats,
            rollback_stats: rollback.stats,
        })
    }

    #[derive(Clone)]
    struct RuntimeRenderResult {
        hash: u32,
        pixels: Vec<u8>,
        feature_non_clear: Vec<usize>,
        stats: WorldPrimitiveSubmitStats,
    }

    fn render_material_scene(
        gal: &mut VulkanicGal,
        frontend: &mut WorldPrimitiveFrontend,
        generation: u64,
        width: u32,
        height: u32,
        frame: WorldPrimitiveFrame,
        label: &str,
    ) -> GalResult<RuntimeRenderResult> {
        let extent = Extent3d {
            width,
            height,
            depth: 1,
        };
        let color = gal.create_texture(TextureDesc {
            label: format!("{label}.color"),
            dimension: TextureDimension::D2,
            format: TextureFormat::Rgba8Unorm,
            extent,
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::ColorAttachment, TextureUsage::TransferSrc],
        })?;
        let color_view = gal.create_texture_view(TextureViewDesc {
            label: format!("{label}.color.view"),
            texture: color,
            format: TextureFormat::Rgba8Unorm,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })?;
        let depth = gal.create_texture(TextureDesc {
            label: format!("{label}.depth"),
            dimension: TextureDimension::D2,
            format: TextureFormat::Depth32Float,
            extent,
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::DepthStencilAttachment],
        })?;
        let depth_view = gal.create_texture_view(TextureViewDesc {
            label: format!("{label}.depth.view"),
            texture: depth,
            format: TextureFormat::Depth32Float,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })?;
        let target = gal.create_render_target(RenderTargetDesc {
            label: format!("{label}.target"),
            color_views: vec![color_view],
            depth_stencil_view: Some(depth_view),
            extent,
        })?;
        let clear_pass = gal.create_render_pass(RenderPassDesc {
            label: format!("{label}.clear-pass"),
            target,
            color_formats: vec![TextureFormat::Rgba8Unorm],
            depth_format: Some(TextureFormat::Depth32Float),
        })?;
        let readback = gal.create_buffer(BufferDesc {
            label: format!("{label}.readback"),
            size: u64::from(width) * u64::from(height) * 4,
            memory: MemoryDomain::Readback,
            usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
        })?;
        let mut ops = vec![
            CommandOp::Barrier(texture_barrier(
                color,
                TextureUsageState::Undefined,
                TextureUsageState::ColorAttachment,
            )),
            CommandOp::Barrier(texture_barrier(
                depth,
                TextureUsageState::Undefined,
                TextureUsageState::DepthStencilAttachment,
            )),
            CommandOp::BeginPass {
                pass: clear_pass,
                target,
                colors: vec![PassAttachment {
                    view: color_view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: Some(ClearColor {
                        r: 0.03,
                        g: 0.04,
                        b: 0.07,
                        a: 1.0,
                    }),
                }],
                depth_stencil: Some(PassAttachment {
                    view: depth_view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            },
            CommandOp::EndPass,
        ];
        let (mut material_ops, mut stats) =
            frontend.append_frame_ops_inner(gal, generation, target, frame, false)?;
        ops.append(&mut material_ops);
        ops.push(CommandOp::Barrier(texture_barrier(
            color,
            TextureUsageState::ColorAttachment,
            TextureUsageState::TransferSrc,
        )));
        ops.push(CommandOp::CopyTextureToBuffer(BufferImageCopyRegion {
            buffer: readback,
            buffer_offset: 0,
            bytes_per_row: width * 4,
            rows_per_image: height,
            texture: color,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent,
        }));
        ops.push(CommandOp::Barrier(buffer_barrier(
            readback,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::HostReadBuffer {
            buffer: readback,
            offset: 0,
            size: u64::from(width) * u64::from(height) * 4,
        });
        stats.command_ops = ops.len() as u64;
        let list = gal.create_command_list(CommandListDesc {
            label: format!("{label}.commands"),
            operations: ops,
        })?;
        let token = gal.submit(SubmissionBatch {
            label: format!("{label}.submit"),
            command_lists: vec![list],
        })?;
        gal.retire_through_for_test(token.submission)?;
        let reads = gal.completed_host_reads();
        let pixels = reads
            .iter()
            .rev()
            .find(|read| read.buffer == readback)
            .map(|read| read.bytes.clone())
            .ok_or_else(|| GalError::backend("material scene readback produced no bytes"))?;
        let hash = xxh32(&pixels, 0x4d_41_54_51);
        let feature_non_clear = material_feature_crops(width, height)
            .iter()
            .map(|crop| non_clear_pixels(&pixels, width, *crop))
            .collect::<Vec<_>>();
        Ok(RuntimeRenderResult {
            hash,
            pixels,
            feature_non_clear,
            stats,
        })
    }

    fn material_runtime_frame(width: u32, height: u32) -> WorldPrimitiveFrame {
        let mut frame = frame(Vec::new());
        frame.viewport_width = width;
        frame.viewport_height = height;
        frame.background.enabled = false;
        frame.background.viewport_width = width;
        frame.background.viewport_height = height;
        frame.material_quads = vec![
            material_scene_quad(
                -0.52,
                0.48,
                -0.30,
                0.86,
                0.25,
                WORLD_MATERIAL_MODE_OPAQUE,
                WORLD_CULL_NONE,
                WORLD_WINDING_CCW,
                0xffff8080,
                [0.5, 0.0, 1.0, 0.5],
                width,
                height,
            ),
            material_scene_quad(
                -0.24,
                0.48,
                -0.02,
                0.86,
                0.30,
                WORLD_MATERIAL_MODE_OPAQUE,
                WORLD_CULL_NONE,
                WORLD_WINDING_CCW,
                0xff80ff80,
                [0.5, 0.0, 1.0, 0.5],
                width,
                height,
            ),
            material_scene_quad(
                0.04,
                0.48,
                0.26,
                0.86,
                0.28,
                WORLD_MATERIAL_MODE_CUTOUT,
                WORLD_CULL_NONE,
                WORLD_WINDING_CCW,
                0xffffffff,
                [0.5, 0.0, 1.0, 0.5],
                width,
                height,
            ),
            material_scene_quad(
                0.32,
                0.48,
                0.54,
                0.86,
                0.28,
                WORLD_MATERIAL_MODE_OPAQUE,
                WORLD_CULL_BACK,
                WORLD_WINDING_CCW,
                0xffff8080,
                [0.5, 0.5, 1.0, 1.0],
                width,
                height,
            ),
            material_scene_quad(
                0.60,
                0.48,
                0.82,
                0.86,
                0.28,
                WORLD_MATERIAL_MODE_OPAQUE,
                WORLD_CULL_FRONT,
                WORLD_WINDING_CW,
                0xff8080ff,
                [0.0, 0.0, 1.0, 1.0],
                width,
                height,
            ),
            material_scene_quad(
                -0.32,
                -0.18,
                0.22,
                0.25,
                0.15,
                WORLD_MATERIAL_MODE_OPAQUE,
                WORLD_CULL_NONE,
                WORLD_WINDING_CCW,
                0xffffffff,
                [0.5, 0.0, 1.0, 0.5],
                width,
                height,
            ),
            material_scene_quad(
                -0.26,
                -0.12,
                0.16,
                0.18,
                0.65,
                WORLD_MATERIAL_MODE_CUTOUT,
                WORLD_CULL_NONE,
                WORLD_WINDING_CCW,
                0xffffff80,
                [0.0, 0.5, 0.5, 1.0],
                width,
                height,
            ),
            material_scene_quad(
                0.42,
                -0.72,
                0.92,
                -0.28,
                0.45,
                WORLD_MATERIAL_MODE_OPAQUE,
                WORLD_CULL_NONE,
                WORLD_WINDING_CCW,
                0xffb0e0ff,
                [0.15, 0.15, 0.85, 0.85],
                width,
                height,
            ),
        ];
        frame.material_quads[7].vertices[1][2] = 0.78;
        frame.material_quads[7].vertices[2][2] = 0.78;
        frame.material_quads[7].depth_policy = WORLD_DEPTH_POLICY_DISABLED;
        frame
    }

    #[allow(clippy::too_many_arguments)]
    fn material_scene_quad(
        x0: f32,
        y0: f32,
        x1: f32,
        y1: f32,
        z: f32,
        material_mode: u32,
        cull_policy: u32,
        winding: u32,
        color_argb: u32,
        uv: [f32; 4],
        width: u32,
        height: u32,
    ) -> WorldMaterialQuadRequest {
        WorldMaterialQuadRequest {
            stratum: WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY,
            material_id: if material_mode == WORLD_MATERIAL_MODE_CUTOUT {
                WORLD_MATERIAL_ID_DEFAULT_CUTOUT
            } else {
                WORLD_MATERIAL_ID_DEFAULT_OPAQUE
            },
            texture_id: WORLD_MATERIAL_TEXTURE_DEFAULT,
            material_mode,
            depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
            cull_policy,
            topology: WORLD_TOPOLOGY_TRIANGLES,
            winding,
            color_argb,
            vertices: [[x0, y0, z], [x1, y0, z], [x1, y1, z], [x0, y1, z]],
            uvs: [
                [uv[0], uv[1]],
                [uv[2], uv[1]],
                [uv[2], uv[3]],
                [uv[0], uv[3]],
            ],
            viewport_width: width,
            viewport_height: height,
        }
    }

    fn material_scene_png(variant: u8) -> Vec<u8> {
        let mut pixels = Vec::with_capacity(8 * 8 * 4);
        for y in 0..8 {
            for x in 0..8 {
                let mut color = match (x >= 4, y >= 4) {
                    (false, false) => [230, 40 + variant * 40, 30, 255],
                    (true, false) => [30, 210, 60 + variant * 50, 255],
                    (false, true) => [35 + variant * 70, 65, 235, 255],
                    (true, true) => [240, 225 - variant * 60, 40, 255],
                };
                if (x + y) % 3 == 0 {
                    color[3] = 0;
                }
                if x == y {
                    color = [255, 255, 255, 255];
                }
                pixels.extend_from_slice(&color);
            }
        }
        let mut encoded = Vec::new();
        {
            let mut encoder = png::Encoder::new(&mut encoded, 8, 8);
            encoder.set_color(png::ColorType::Rgba);
            encoder.set_depth(png::BitDepth::Eight);
            let mut writer = encoder.write_header().unwrap();
            writer.write_image_data(&pixels).unwrap();
        }
        encoded
    }

    fn semantic_hash(frame: &WorldPrimitiveFrame) -> u32 {
        let mut bytes = Vec::new();
        push_u32(&mut bytes, frame.material_quads.len() as u32);
        for quad in &frame.material_quads {
            push_u32(&mut bytes, quad.stratum);
            push_u32(&mut bytes, quad.material_id);
            push_u32(&mut bytes, quad.texture_id);
            push_u32(&mut bytes, quad.material_mode);
            push_u32(&mut bytes, quad.depth_policy);
            push_u32(&mut bytes, quad.cull_policy);
            push_u32(&mut bytes, quad.topology);
            push_u32(&mut bytes, quad.winding);
            push_u32(&mut bytes, quad.color_argb);
            for value in quad
                .vertices
                .iter()
                .flatten()
                .chain(quad.uvs.iter().flatten())
            {
                push_f32(&mut bytes, *value);
            }
        }
        xxh32(&bytes, 0x53_45_4d_51)
    }

    #[derive(Clone, Copy)]
    struct Crop {
        name: &'static str,
        x: u32,
        y: u32,
        width: u32,
        height: u32,
    }

    fn material_feature_crops(width: u32, height: u32) -> Vec<Crop> {
        vec![
            crop_from_ndc("front_ccw_opaque", width, height, -0.52, 0.48, -0.30, 0.86),
            crop_from_ndc("compatible_tinted", width, height, -0.24, 0.48, -0.02, 0.86),
            crop_from_ndc("cutout_discard", width, height, 0.04, 0.48, 0.26, 0.86),
            crop_from_ndc("back_cull_ccw", width, height, 0.32, 0.48, 0.54, 0.86),
            crop_from_ndc("front_cull_cw", width, height, 0.60, 0.48, 0.82, 0.86),
            crop_from_ndc("depth_overlap", width, height, -0.32, -0.18, 0.22, 0.25),
            crop_from_ndc(
                "side_tilt_uv_subregion",
                width,
                height,
                0.42,
                -0.72,
                0.92,
                -0.28,
            ),
        ]
    }

    fn crop_from_ndc(
        name: &'static str,
        width: u32,
        height: u32,
        x0: f32,
        y0: f32,
        x1: f32,
        y1: f32,
    ) -> Crop {
        let left = (((x0 * 0.5 + 0.5) * width as f32).floor() as i32).clamp(0, width as i32 - 1);
        let right = (((x1 * 0.5 + 0.5) * width as f32).ceil() as i32).clamp(1, width as i32);
        let top =
            (((1.0 - (y1 * 0.5 + 0.5)) * height as f32).floor() as i32).clamp(0, height as i32 - 1);
        let bottom =
            (((1.0 - (y0 * 0.5 + 0.5)) * height as f32).ceil() as i32).clamp(1, height as i32);
        Crop {
            name,
            x: left as u32,
            y: top as u32,
            width: (right - left).max(1) as u32,
            height: (bottom - top).max(1) as u32,
        }
    }

    fn non_clear_pixels(pixels: &[u8], width: u32, crop: Crop) -> usize {
        let mut count = 0;
        for y in crop.y..crop.y + crop.height {
            for x in crop.x..crop.x + crop.width {
                let index = ((y * width + x) * 4) as usize;
                let px = &pixels[index..index + 4];
                if px[0].abs_diff(8) > 4 || px[1].abs_diff(10) > 4 || px[2].abs_diff(18) > 4 {
                    count += 1;
                }
            }
        }
        count
    }

    fn write_material_report(
        backend: RuntimeBackend,
        width: u32,
        height: u32,
        semantic_hash: u32,
        initial: &RuntimeRenderResult,
        rollback: &RuntimeRenderResult,
        reloaded: &RuntimeRenderResult,
        resized: &RuntimeRenderResult,
    ) -> GalResult<()> {
        let root = repo_root().join("logs/rust-vulkanic/material-conformance");
        let dir = root.join(backend.label());
        std::fs::create_dir_all(&dir).map_err(|error| {
            GalError::backend(format!(
                "failed to create material conformance dir: {error}"
            ))
        })?;
        write_png(
            &dir.join("full-initial.png"),
            width,
            height,
            &initial.pixels,
        )?;
        for crop in material_feature_crops(width, height) {
            write_crop(
                &dir.join(format!("crop-{}.png", crop.name)),
                width,
                &initial.pixels,
                crop,
            )?;
        }
        write_png(
            &dir.join("full-rollback.png"),
            width,
            height,
            &rollback.pixels,
        )?;
        write_png(
            &dir.join("full-reloaded.png"),
            width,
            height,
            &reloaded.pixels,
        )?;
        write_png(
            &dir.join("full-resized.png"),
            width + 32,
            height + 16,
            &resized.pixels,
        )?;
        let crop_counts = material_feature_crops(width, height)
            .into_iter()
            .zip(initial.feature_non_clear.iter())
            .map(|(crop, count)| format!("\"{}\":{}", crop.name, count))
            .collect::<Vec<_>>()
            .join(",");
        let json = format!(
            "{{\n  \"artifact_class\":\"world_material_runtime_conformance\",\n  \"backend\":\"{}\",\n  \"semantic_request_hash\":\"{:08x}\",\n  \"width\":{},\n  \"height\":{},\n  \"initial_hash\":\"{:08x}\",\n  \"rollback_hash\":\"{:08x}\",\n  \"reloaded_hash\":\"{:08x}\",\n  \"resized_hash\":\"{:08x}\",\n  \"feature_non_clear\":{{{}}},\n  \"material_quad_count\":{},\n  \"material_batch_count\":{},\n  \"material_draw_count\":{},\n  \"material_cache_misses\":{},\n  \"rollback_material_cache_hits\":{},\n  \"rollback_preserved_last_valid_texture\":{},\n  \"screenshots\":[\"full-initial.png\",\"full-rollback.png\",\"full-reloaded.png\",\"full-resized.png\"]\n}}\n",
            backend.name(),
            semantic_hash,
            width,
            height,
            initial.hash,
            rollback.hash,
            reloaded.hash,
            resized.hash,
            crop_counts,
            initial.stats.material_quad_count,
            initial.stats.material_batch_count,
            initial.stats.material_draw_count,
            initial.stats.material_cache_misses,
            rollback.stats.material_cache_hits,
            initial.hash == rollback.hash
        );
        std::fs::write(dir.join("latest.json"), json).map_err(|error| {
            GalError::backend(format!(
                "failed to write material conformance report: {error}"
            ))
        })
    }

    fn write_png(path: &std::path::Path, width: u32, height: u32, pixels: &[u8]) -> GalResult<()> {
        let file = std::fs::File::create(path).map_err(|error| {
            GalError::backend(format!("failed to create PNG {}: {error}", path.display()))
        })?;
        let mut encoder = png::Encoder::new(file, width, height);
        encoder.set_color(png::ColorType::Rgba);
        encoder.set_depth(png::BitDepth::Eight);
        let mut writer = encoder
            .write_header()
            .map_err(|error| GalError::backend(format!("failed to write PNG header: {error}")))?;
        writer
            .write_image_data(pixels)
            .map_err(|error| GalError::backend(format!("failed to write PNG pixels: {error}")))
    }

    fn write_crop(
        path: &std::path::Path,
        source_width: u32,
        pixels: &[u8],
        crop: Crop,
    ) -> GalResult<()> {
        let mut cropped = Vec::with_capacity((crop.width * crop.height * 4) as usize);
        for y in crop.y..crop.y + crop.height {
            let start = ((y * source_width + crop.x) * 4) as usize;
            let end = start + (crop.width * 4) as usize;
            cropped.extend_from_slice(&pixels[start..end]);
        }
        write_png(path, crop.width, crop.height, &cropped)
    }

    fn repo_root() -> std::path::PathBuf {
        std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
            .parent()
            .and_then(|path| path.parent())
            .and_then(|path| path.parent())
            .expect("rust crate should live under src/main/rust")
            .to_path_buf()
    }

    fn assert_environment_gap(error: &GalError, backend: &str) {
        let text = error.to_string();
        assert!(
            text.contains(backend)
                || text.contains("physical device")
                || text.contains("EGL")
                || text.contains("GLX")
                || text.contains("OpenGL")
                || text.contains("Vulkan"),
            "unexpected runtime material conformance failure: {text}"
        );
    }
}
