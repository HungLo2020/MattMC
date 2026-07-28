use std::collections::BTreeMap;

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
pub const WORLD_MAX_BORDER_QUADS: usize = 64;
pub const WORLD_DEPTH_POLICY_DISABLED: u32 = 0;
pub const WORLD_DEPTH_POLICY_TEST_WRITE: u32 = 1;
pub const WORLD_DEPTH_POLICY_TEST_NO_WRITE: u32 = 2;
pub const WORLD_STRATUM_WORLD_BORDER: u32 = 80;
pub const WORLD_STRATUM_BLOCK_OUTLINE: u32 = 100;
pub const WORLD_STRATUM_BLOCK_BREAKING_CRACK: u32 = 90;
pub const WORLD_BORDER_TEXTURE_FORCEFIELD: u32 = 1;
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
pub struct WorldBorderAssetPayload {
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
    pub border_cache_hits: u64,
    pub border_cache_misses: u64,
    pub border_asset_generation: u64,
    pub border_asset_payload_bytes: u64,
    pub border_asset_update_failures: u64,
    pub crack_asset_generation: u64,
    pub crack_asset_payload_bytes: u64,
    pub crack_asset_update_failures: u64,
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
    resources: Option<WorldLineResources>,
    resource_format: Option<ColorFormat>,
    crack_resources: Option<CrackResources>,
    crack_resource_format: Option<ColorFormat>,
    border_resources: Option<BorderResources>,
    border_resource_format: Option<ColorFormat>,
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
                    format!("duplicate world crack asset payload for stage {}", payload.stage),
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
        let vulkan_backend = gal
            .capabilities()
            .name
            .to_ascii_lowercase()
            .contains("vulkan");
        if !vulkan_backend
            && (clear_background
                || frame.background.enabled
                || !frame.border_quads.is_empty())
        {
            return Err(GalError::unsupported_feature(
                "OpenGL partial world primitive submit supports line and crack primitives only",
            ));
        }
        if self.generation == 0 {
            self.generation = generation;
        }
        validate_frame(&frame)?;
        let color_format = gal.frame_target_color_format(frame_target)?;
        let had_resources = self.resources.is_some() && self.resource_format == Some(color_format);
        let had_crack_resources =
            self.crack_resources.is_some() && self.crack_resource_format == Some(color_format);
        let had_border_resources =
            self.border_resources.is_some() && self.border_resource_format == Some(color_format);
        if !frame.segments.is_empty() {
            self.ensure_resources(gal, color_format)?;
        }
        if !frame.crack_quads.is_empty() {
            self.ensure_crack_resources(gal, color_format)?;
        }
        if !frame.border_quads.is_empty() {
            self.ensure_border_resources(gal, color_format)?;
        }
        let (depth_texture, depth_view, created_depth, retired_depth) =
            self.ensure_depth_attachment(gal, frame.viewport_width, frame.viewport_height)?;
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
            depth_attachment_creates: u64::from(created_depth),
            depth_attachment_reuses: u64::from(!created_depth),
            depth_attachment_retires: retired_depth.saturating_add(pending_depth_retires),
            border_asset_generation: self.border_asset_generation,
            border_asset_payload_bytes: self.border_asset_payload_bytes,
            border_asset_update_failures: self.border_asset_update_failures,
            crack_asset_generation: self.crack_asset_generation,
            crack_asset_payload_bytes: self.crack_asset_payload_bytes,
            crack_asset_update_failures: self.crack_asset_update_failures,
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
        let background_color = background_clear_color(&frame.background);
        let batches = line_batches(&frame);
        let crack_batches = crack_batches(&frame);
        let border_batches = border_batches(&frame);
        let mut ops = Vec::with_capacity(
            6 + batches.len() * 8 + crack_batches.len() * 8 + border_batches.len() * 8,
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
                    view: frame_target,
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
                    colors: vec![loaded_frame_color_attachment(frame_target)],
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
                    colors: vec![loaded_frame_color_attachment(frame_target)],
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
                colors: vec![loaded_frame_color_attachment(frame_target)],
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
        self.destroy_border_resources(gal);
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
    if frame.background.enabled {
        if !matches!(
            frame.background.sky_type,
            WORLD_BACKGROUND_SKY_OVERWORLD
                | WORLD_BACKGROUND_SKY_NETHER
                | WORLD_BACKGROUND_SKY_END
                | WORLD_BACKGROUND_SKY_CUSTOM
        ) {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!(
                    "unknown world background sky type {}",
                    frame.background.sky_type
                ),
            ));
        }
        if frame.background.load_intent != WORLD_BACKGROUND_LOAD_CLEAR {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!(
                    "unknown world background load intent {}",
                    frame.background.load_intent
                ),
            ));
        }
        if frame.background.store_intent != WORLD_BACKGROUND_STORE_STORE {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!(
                    "unknown world background store intent {}",
                    frame.background.store_intent
                ),
            ));
        }
        if frame.background.viewport_width != frame.viewport_width
            || frame.background.viewport_height != frame.viewport_height
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world background viewport metadata must match the frame viewport",
            ));
        }
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
    if frame.border_quads.len() > WORLD_MAX_BORDER_QUADS {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "world border quad count {} exceeds maximum {}",
                frame.border_quads.len(),
                WORLD_MAX_BORDER_QUADS
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
        if segment.depth_policy > WORLD_DEPTH_POLICY_TEST_NO_WRITE {
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
    for quad in &frame.border_quads {
        if quad.stratum != WORLD_STRATUM_WORLD_BORDER {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!("unsupported world border stratum {}", quad.stratum),
            ));
        }
        if quad.texture_id != WORLD_BORDER_TEXTURE_FORCEFIELD {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world border texture id {}", quad.texture_id),
            ));
        }
        if quad.blend_policy != WORLD_BORDER_BLEND_OVERLAY {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world border blend policy {}", quad.blend_policy),
            ));
        }
        if quad.cull_policy != WORLD_BORDER_CULL_NONE {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world border cull policy {}", quad.cull_policy),
            ));
        }
        if quad.depth_policy > WORLD_DEPTH_POLICY_TEST_WRITE {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world border depth policy {}", quad.depth_policy),
            ));
        }
        if quad.viewport_width != frame.viewport_width
            || quad.viewport_height != frame.viewport_height
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world border viewport metadata must match the frame viewport",
            ));
        }
        for value in quad
            .vertices
            .iter()
            .flatten()
            .chain(quad.uv_region.iter())
            .chain(quad.scroll.iter())
            .chain([quad.border_size, quad.distance_to_border].iter())
        {
            if !value.is_finite() {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "world border quad metadata must be finite",
                ));
            }
        }
        if quad.border_size < 0.0 || quad.distance_to_border < 0.0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "world border size and distance must be non-negative",
            ));
        }
    }
    Ok(())
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

fn loaded_frame_color_attachment(frame_target: Handle) -> PassAttachment {
    PassAttachment {
        view: frame_target,
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

fn crack_atlas_bytes(overrides: &BTreeMap<u32, WorldCrackTextureAsset>) -> GalResult<Vec<u8>> {
    let mut out =
        Vec::with_capacity((CRACK_STAGE_COUNT * CRACK_STAGE_SIZE * CRACK_STAGE_SIZE * 4) as usize);
    for stage in 0..CRACK_STAGE_COUNT {
        if let Some(asset) = overrides.get(&stage) {
            out.extend_from_slice(&asset.rgba);
        } else {
            let bytes = bundled_crack_stage_png(stage);
            let decoded = decode_crack_stage(bytes).expect("bundled crack stage texture is valid");
            out.extend_from_slice(&decoded);
        }
    }
    Ok(out)
}

fn bundled_crack_stage_png(stage: u32) -> &'static [u8] {
    match stage {
        0 => include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_0.png")
            .as_slice(),
        1 => include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_1.png")
            .as_slice(),
        2 => include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_2.png")
            .as_slice(),
        3 => include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_3.png")
            .as_slice(),
        4 => include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_4.png")
            .as_slice(),
        5 => include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_5.png")
            .as_slice(),
        6 => include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_6.png")
            .as_slice(),
        7 => include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_7.png")
            .as_slice(),
        8 => include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_8.png")
            .as_slice(),
        9 => include_bytes!("../../../resources/assets/minecraft/textures/block/destroy_stage_9.png")
            .as_slice(),
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
    fn validate_frame_rejects_unknown_depth_policy() {
        let error = validate_frame(&frame(vec![segment(99, 0xff000000)])).unwrap_err();
        assert_eq!(StatusCode::UnknownEnum, error.code);
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
}
