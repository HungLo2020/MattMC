use std::collections::BTreeMap;
use std::io::BufReader;

use super::commands::{
    AttachmentLoadOp, AttachmentStoreOp, CommandList, CommandOp, PassAttachment, ResourceBarrier,
    SubmissionBatch, TextureOrigin3d, TextureUsageState,
};
use super::error::{GalError, GalResult, StatusCode};
use super::gal::VulkanicGal;
use super::gui_mesh_frontend::{
    prepare_draws as prepare_gui_mesh_draws, GuiMeshBatchRequest, GuiMeshCompositeResources,
    GuiMeshLightingMode, GuiMeshMaterialMode, GuiMeshOffscreenTargetCache, GuiMeshPassResources,
    GuiMeshPreparedDraw, GuiMeshStreamRange, GUI_MESH_COMPOSITE_UNIFORM_STRIDE,
};
use super::handles::Handle;
use super::resources::{
    AccessFlags, BackendApi, BlendMode, BufferDesc, BufferUsage, ColorFormat, Extent3d,
    GraphicsPipelineDesc, MemoryDomain, PipelineLayoutDesc, PipelineStageFlags, PrimitiveTopology,
    QueueClass, RenderPassDesc, ResourceBinding, ResourceBindingDesc, ResourceBindingKind,
    ResourceLayoutDesc, ResourceSetDesc, SamplerAddressMode, SamplerDesc, SamplerFilter,
    ShaderCodeFormat, ShaderModuleDesc, ShaderStage, TextureDesc, TextureDimension, TextureFormat,
    TextureSubresourceRange, TextureUsage, TextureViewDesc,
};
use super::{BufferImageCopyRegion, CommandListDesc, CullMode};

pub const GUI_MAX_PACKED_SPRITES: usize = 256;
const GUI_UNIFORM_BYTES: usize = 96;
const GUI_PACKED_UNIFORM_BYTES: u64 = (GUI_MAX_PACKED_SPRITES * GUI_UNIFORM_BYTES) as u64;

const VERTEX_SHADER_OPENGL: &[u8] = br#"#version 330 core
struct PackedGuiQuad {
    vec4 origin_axis_u;
    vec4 axis_v_mode;
    vec4 viewport;
    vec4 clip;
    vec4 uv_region;
    vec4 color;
};
layout(std140) uniform GuiSpriteBatch {
    PackedGuiQuad sprites[256];
};
out vec2 v_uv;
out vec2 v_sprite_corner;
out vec2 v_pixel;
out vec4 v_color;
flat out vec4 v_uv_region;
flat out vec4 v_clip;
flat out float v_texture_mode;
flat out float v_clip_enabled;
const vec2 corner[6] = vec2[6](
    vec2(0.0, 0.0),
    vec2(1.0, 0.0),
    vec2(1.0, 1.0),
    vec2(1.0, 1.0),
    vec2(0.0, 1.0),
    vec2(0.0, 0.0)
);
void main() {
    int vertex = gl_VertexID;
    PackedGuiQuad sprite = sprites[gl_InstanceID];
    vec2 pixel = sprite.origin_axis_u.xy + corner[vertex].x * sprite.origin_axis_u.zw + corner[vertex].y * sprite.axis_v_mode.xy;
    float top_left_y = 1.0 - (pixel.y / sprite.viewport.y) * 2.0;
    float ndc_y = mix(top_left_y, -top_left_y, sprite.viewport.w);
    vec2 ndc = vec2((pixel.x / sprite.viewport.x) * 2.0 - 1.0, ndc_y);
    gl_Position = vec4(ndc, sprite.axis_v_mode.w, 1.0);
    v_uv_region = sprite.uv_region;
    v_clip = sprite.clip;
    v_clip_enabled = sprite.viewport.z;
    v_texture_mode = sprite.axis_v_mode.z;
    v_sprite_corner = corner[vertex];
    v_pixel = pixel;
    v_uv = vec2(
        sprite.uv_region.x + corner[vertex].x * sprite.uv_region.z,
        sprite.uv_region.y + corner[vertex].y * sprite.uv_region.w
    );
    v_color = sprite.color;
}
"#;

const VERTEX_SHADER_VULKAN: &[u8] = br#"#version 450
struct PackedGuiQuad {
    vec4 origin_axis_u;
    vec4 axis_v_mode;
    vec4 viewport;
    vec4 clip;
    vec4 uv_region;
    vec4 color;
};
layout(set = 0, binding = 0, std140) uniform GuiSpriteBatch {
    PackedGuiQuad sprites[256];
};
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec2 v_sprite_corner;
layout(location = 2) out vec2 v_pixel;
layout(location = 3) out vec4 v_color;
layout(location = 4) flat out vec4 v_uv_region;
layout(location = 5) flat out vec4 v_clip;
layout(location = 6) flat out float v_texture_mode;
layout(location = 7) flat out float v_clip_enabled;
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
    PackedGuiQuad sprite = sprites[gl_InstanceIndex];
    vec2 pixel = sprite.origin_axis_u.xy + corner[vertex].x * sprite.origin_axis_u.zw + corner[vertex].y * sprite.axis_v_mode.xy;
    float top_left_y = 1.0 - (pixel.y / sprite.viewport.y) * 2.0;
    float ndc_y = mix(top_left_y, -top_left_y, sprite.viewport.w);
    vec2 ndc = vec2((pixel.x / sprite.viewport.x) * 2.0 - 1.0, ndc_y);
    gl_Position = vec4(ndc, sprite.axis_v_mode.w, 1.0);
    v_uv_region = sprite.uv_region;
    v_clip = sprite.clip;
    v_clip_enabled = sprite.viewport.z;
    v_texture_mode = sprite.axis_v_mode.z;
    v_sprite_corner = corner[vertex];
    v_pixel = pixel;
    v_uv = vec2(
        sprite.uv_region.x + corner[vertex].x * sprite.uv_region.z,
        sprite.uv_region.y + corner[vertex].y * sprite.uv_region.w
    );
    v_color = sprite.color;
}
"#;

const FRAGMENT_SHADER_OPENGL: &[u8] = br#"#version 330 core
uniform sampler2D Sampler0;
in vec2 v_uv;
in vec2 v_sprite_corner;
in vec2 v_pixel;
in vec4 v_color;
flat in vec4 v_uv_region;
flat in vec4 v_clip;
flat in float v_texture_mode;
flat in float v_clip_enabled;
out vec4 out_color;
void main() {
    if (v_clip_enabled > 0.5 && (v_pixel.x < v_clip.x || v_pixel.y < v_clip.y || v_pixel.x >= v_clip.z || v_pixel.y >= v_clip.w)) {
        discard;
    }
    ivec2 texture_size = textureSize(Sampler0, 0);
    ivec2 origin = ivec2(round(v_uv_region.xy * vec2(texture_size)));
    ivec2 extent = max(ivec2(round(v_uv_region.zw * vec2(texture_size))), ivec2(1));
    ivec2 local = clamp(ivec2(floor(v_sprite_corner * vec2(extent))), ivec2(0), extent - ivec2(1));
    ivec2 texel = origin + local;
    texel = clamp(texel, ivec2(0), texture_size - ivec2(1));
    vec4 sampled = texelFetch(Sampler0, texel, 0);
    vec4 color = (v_texture_mode < 0.5 ? vec4(1.0, 1.0, 1.0, sampled.r) : sampled) * v_color;
    if (color.a <= 0.0) {
        discard;
    }
    out_color = color;
}
"#;

const FRAGMENT_SHADER_VULKAN: &[u8] = br#"#version 450
layout(set = 0, binding = 1) uniform texture2D Tex0;
layout(set = 0, binding = 2) uniform sampler Samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec2 v_sprite_corner;
layout(location = 2) in vec2 v_pixel;
layout(location = 3) in vec4 v_color;
layout(location = 4) flat in vec4 v_uv_region;
layout(location = 5) flat in vec4 v_clip;
layout(location = 6) flat in float v_texture_mode;
layout(location = 7) flat in float v_clip_enabled;
layout(location = 0) out vec4 out_color;
void main() {
    if (v_clip_enabled > 0.5 && (v_pixel.x < v_clip.x || v_pixel.y < v_clip.y || v_pixel.x >= v_clip.z || v_pixel.y >= v_clip.w)) {
        discard;
    }
    ivec2 texture_size = textureSize(sampler2D(Tex0, Samp0), 0);
    ivec2 origin = ivec2(round(v_uv_region.xy * vec2(texture_size)));
    ivec2 extent = max(ivec2(round(v_uv_region.zw * vec2(texture_size))), ivec2(1));
    ivec2 local = clamp(ivec2(floor(v_sprite_corner * vec2(extent))), ivec2(0), extent - ivec2(1));
    ivec2 texel = origin + local;
    texel = clamp(texel, ivec2(0), texture_size - ivec2(1));
    vec4 sampled = texelFetch(sampler2D(Tex0, Samp0), texel, 0);
    vec4 color = (v_texture_mode < 0.5 ? vec4(1.0, 1.0, 1.0, sampled.r) : sampled) * v_color;
    if (color.a <= 0.0) {
        discard;
    }
    out_color = color;
}
"#;

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
enum TextureGroup {
    Alpha,
    Invert,
    Dynamic(u64),
}

impl TextureGroup {
    fn label(self) -> String {
        match self {
            Self::Alpha => "gui-alpha".to_string(),
            Self::Invert => "gui-invert".to_string(),
            Self::Dynamic(asset_id) => format!("gui-image-{asset_id}"),
        }
    }

    fn blend(self) -> BlendMode {
        match self {
            Self::Alpha => BlendMode::Alpha,
            Self::Invert => BlendMode::Invert,
            Self::Dynamic(_) => BlendMode::Alpha,
        }
    }
}

#[derive(Clone, Copy)]
struct SpriteDef {
    id: u32,
    stratum: u32,
    name: &'static str,
    path: &'static str,
    width: u32,
    height: u32,
    group: TextureGroup,
}

#[derive(Clone, Copy)]
struct AtlasRegion {
    x: u32,
    y: u32,
}

#[derive(Clone)]
struct TextureAtlas {
    width: u32,
    height: u32,
    bytes: Vec<u8>,
    regions: BTreeMap<u32, AtlasRegion>,
}

struct GuiResources {
    upload_buffer: Handle,
    index_buffer: Handle,
    uniform_buffer: Handle,
    texture: Handle,
    sampler: Handle,
    vertex_shader: Handle,
    fragment_shader: Handle,
    texture_view: Handle,
    resource_layout: Handle,
    resource_set: Handle,
    pipeline_layout: Handle,
    pipeline: Handle,
}

impl GuiResources {
    fn handles_in_destroy_order(&self) -> [Handle; 12] {
        [
            self.pipeline,
            self.pipeline_layout,
            self.resource_set,
            self.resource_layout,
            self.texture_view,
            self.fragment_shader,
            self.vertex_shader,
            self.sampler,
            self.texture,
            self.uniform_buffer,
            self.index_buffer,
            self.upload_buffer,
        ]
    }
}

#[derive(Clone, Copy)]
struct CachedPass {
    frame_target: Handle,
    pass: Handle,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
struct GuiMeshRasterKey {
    asset_id: u64,
    material_mode: GuiMeshMaterialMode,
    front_face: super::resources::FrontFace,
    /// The raster pass owns one frame uniform buffer. Its placement and
    /// lighting inputs are therefore part of the cache identity: reusing the
    /// same texture pipeline for a different PIP target must not overwrite an
    /// earlier mesh draw's extent before the command list executes.
    render_extent: [u32; 2],
    alpha_cutoff_bits: u32,
    lighting_mode: GuiMeshLightingMode,
}

fn gui_mesh_raster_key(draw: &GuiMeshPreparedDraw) -> GuiMeshRasterKey {
    GuiMeshRasterKey {
        asset_id: draw.asset_id,
        material_mode: draw.material_mode,
        front_face: draw.front_face,
        render_extent: draw.render_extent,
        alpha_cutoff_bits: draw.alpha_cutoff.to_bits(),
        lighting_mode: draw.lighting_mode,
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
struct GuiMeshCompositeKey {
    width: u32,
    height: u32,
    color_format: ColorFormat,
    depth_format: Option<TextureFormat>,
}

#[derive(Default)]
pub struct GuiFrontend {
    generation: u64,
    asset_generation: u64,
    raw_image_generation: u64,
    asset_overrides: BTreeMap<u32, Vec<u8>>,
    raw_images: BTreeMap<u64, RawGuiImage>,
    atlases: BTreeMap<TextureGroupKey, TextureAtlas>,
    resources: BTreeMap<ResourceKey, GuiResources>,
    cached_pass: Option<CachedPass>,
    mesh_targets: GuiMeshOffscreenTargetCache,
    mesh_rasters: BTreeMap<GuiMeshRasterKey, GuiMeshPassResources>,
    mesh_composites: BTreeMap<GuiMeshCompositeKey, GuiMeshCompositeResources>,
    mesh_composite_uniform_cursor: u64,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
enum TextureGroupKey {
    Alpha,
    Invert,
    Dynamic(u64),
}

impl From<TextureGroup> for TextureGroupKey {
    fn from(value: TextureGroup) -> Self {
        match value {
            TextureGroup::Alpha => Self::Alpha,
            TextureGroup::Invert => Self::Invert,
            TextureGroup::Dynamic(asset_id) => Self::Dynamic(asset_id),
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
struct ResourceKey {
    group: TextureGroupKey,
    color_format: ColorFormat,
    depth_format: Option<TextureFormat>,
}

impl ResourceKey {
    fn new(
        group: TextureGroup,
        color_format: ColorFormat,
        depth_format: Option<TextureFormat>,
    ) -> Self {
        Self {
            group: TextureGroupKey::from(group),
            color_format,
            depth_format,
        }
    }
}

#[derive(Clone, Debug)]
pub struct GuiSpriteRequest {
    pub stratum: u32,
    pub sprite_id: u32,
    pub selected_slot: i32,
    pub progress_fraction: f32,
    pub fill_direction: u32,
    pub color_argb: u32,
    pub x: i32,
    pub y: i32,
    pub width: u32,
    pub height: u32,
    pub gui_width: u32,
    pub gui_height: u32,
    pub sequence: u64,
}

#[derive(Clone, Debug, Default)]
pub struct GuiSubmitStats {
    pub submission_id: u64,
    pub sprite_count: u64,
    pub affine_quad_count: u64,
    /// Coarse standard-3D GUI items that completed owned raster/composite
    /// command construction in this submission.
    pub mesh_item_count: u64,
    /// Semantic mesh layers consumed by the owned GUI-mesh raster path.
    pub mesh_batch_count: u64,
    /// Raster plus compose draws emitted for the mesh items.
    pub mesh_draw_count: u64,
    pub sprite_batch_count: u64,
    pub cache_hits: u64,
    pub cache_misses: u64,
    pub resource_creates: u64,
    pub command_lists: u64,
    pub command_ops: u64,
    /// Private Rust-owned offscreen raster targets used by standard-3D GUI
    /// items before they are composited into the requested GUI target. This
    /// never contains an acquired frame target or a Java-owned surface.
    pub(crate) owned_intermediate_targets: Vec<Handle>,
    /// Frame-local pass objects used only by a bounded diagnostic replay.
    /// They are not cached because their target retires with that capture.
    pub(crate) transient_diagnostic_passes: Vec<Handle>,
}

#[derive(Clone, Debug)]
pub struct GuiAssetPayload {
    pub sprite_id: u32,
    pub png_bytes: Vec<u8>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum GuiRawImageFormat {
    Alpha8,
    Rgba8,
}

impl GuiRawImageFormat {
    fn texture_format(self) -> TextureFormat {
        match self {
            Self::Alpha8 => TextureFormat::R8Unorm,
            Self::Rgba8 => TextureFormat::Rgba8Unorm,
        }
    }

    fn bytes_per_pixel(self) -> usize {
        match self {
            Self::Alpha8 => 1,
            Self::Rgba8 => 4,
        }
    }

    fn shader_mode(self) -> f32 {
        match self {
            Self::Alpha8 => 0.0,
            Self::Rgba8 => 1.0,
        }
    }
}

/// CPU-owned image data. The FFI boundary will carry this as one bounded asset
/// update; it deliberately contains no atlas, renderer, or backend objects.
#[derive(Clone, Debug)]
pub struct GuiRawImageAssetPayload {
    pub asset_id: u64,
    pub format: GuiRawImageFormat,
    pub width: u32,
    pub height: u32,
    pub pixels: Vec<u8>,
}

/// An affine textured GUI primitive. Four explicit corners from Minecraft font
/// layout reduce to an origin plus two axes without losing italic shear.
#[derive(Clone, Debug)]
pub struct GuiAffineQuadRequest {
    pub stratum: u32,
    pub asset_id: u64,
    pub x0: f32,
    pub y0: f32,
    pub x1: f32,
    pub y1: f32,
    pub x3: f32,
    pub y3: f32,
    pub z: f32,
    pub u0: f32,
    pub v0: f32,
    pub u1: f32,
    pub v1: f32,
    pub color_argb: u32,
    pub gui_width: u32,
    pub gui_height: u32,
    pub sequence: u64,
    pub clip_mode: u32,
    pub clip_left: i32,
    pub clip_top: i32,
    pub clip_width: i32,
    pub clip_height: i32,
}

#[derive(Debug)]
enum GuiFrameRequest {
    Sprite(GuiSpriteRequest),
    Affine(GuiAffineQuadRequest),
    Mesh(GuiMeshItem),
}

#[derive(Debug)]
struct GuiMeshItem {
    stratum: u32,
    sequence: u64,
    layers: Vec<GuiMeshBatchRequest>,
}

impl GuiFrameRequest {
    fn stratum(&self) -> u32 {
        match self {
            Self::Sprite(request) => request.stratum,
            Self::Affine(request) => request.stratum,
            Self::Mesh(request) => request.stratum,
        }
    }

    fn sequence(&self) -> u64 {
        match self {
            Self::Sprite(request) => request.sequence,
            Self::Affine(request) => request.sequence,
            Self::Mesh(request) => request.sequence,
        }
    }
}

#[derive(Clone)]
struct RawGuiImage {
    format: GuiRawImageFormat,
    width: u32,
    height: u32,
    pixels: Vec<u8>,
}

#[derive(Clone)]
struct GuiTextureSource {
    width: u32,
    height: u32,
    format: GuiRawImageFormat,
    bytes: Vec<u8>,
}

#[derive(Clone, Copy)]
struct PackedGuiQuad {
    origin: [f32; 2],
    axis_u: [f32; 2],
    axis_v: [f32; 2],
    viewport: [f32; 2],
    clip: [f32; 4],
    clip_enabled: bool,
    /// The selected-source overlay receives one later final copy. Preserve
    /// Java's top-left GUI semantics by precompensating that copy in the
    /// Rust-owned GUI stream. Texture coordinates stay semantic: the later
    /// image copy supplies the corresponding texel-row inversion.
    pre_present_y_flip: bool,
    uv: [f32; 4],
    color: [f32; 4],
    texture_mode: f32,
    z: f32,
}

struct GuiBatch {
    stratum: u32,
    group: TextureGroup,
    quads: Vec<PackedGuiQuad>,
}

impl GuiFrontend {
    pub fn reset(&mut self, gal: &mut VulkanicGal) {
        self.destroy_render_resources(gal);
        self.asset_overrides.clear();
        self.raw_images.clear();
        self.asset_generation = 0;
        self.raw_image_generation = 0;
        self.generation = 0;
    }

    fn destroy_render_resources(&mut self, gal: &mut VulkanicGal) {
        if let Some(pass) = self.cached_pass.take() {
            let _ = gal.destroy(pass.pass);
        }
        let resources = std::mem::take(&mut self.resources);
        for resource in resources.values() {
            for handle in resource.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        let mesh_rasters = std::mem::take(&mut self.mesh_rasters);
        for resources in mesh_rasters.into_values() {
            resources.destroy(gal);
        }
        let mesh_composites = std::mem::take(&mut self.mesh_composites);
        for resources in mesh_composites.into_values() {
            resources.destroy(gal);
        }
        self.mesh_targets.clear(gal);
        self.atlases.clear();
    }

    pub fn apply_asset_update(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payloads: Vec<GuiAssetPayload>,
    ) -> GalResult<()> {
        if generation == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI asset generation must be non-zero",
            ));
        }
        if generation <= self.asset_generation {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "stale GUI asset generation {generation}; current generation is {}",
                    self.asset_generation
                ),
            ));
        }
        let mut overrides = BTreeMap::new();
        for payload in payloads {
            let def = sprite_def(payload.sprite_id)?;
            if payload.png_bytes.is_empty() {
                continue;
            }
            if overrides.contains_key(&payload.sprite_id) {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "duplicate GUI asset payload for sprite id {}",
                        payload.sprite_id
                    ),
                ));
            }
            let _ = decode_sprite_bytes(def, &payload.png_bytes)?;
            overrides.insert(payload.sprite_id, payload.png_bytes);
        }
        for def in SPRITES {
            let bytes = overrides
                .get(&def.id)
                .map(Vec::as_slice)
                .or_else(|| bundled_sprite_bytes(def.path))
                .ok_or_else(|| {
                    GalError::backend(format!("missing bundled GUI sprite '{}'", def.path))
                })?;
            let _ = decode_sprite_bytes(def, bytes)?;
        }
        self.asset_generation = generation;
        self.asset_overrides = overrides;
        self.destroy_render_resources(gal);
        Ok(())
    }

    /// Replaces the complete dynamic GUI image generation atomically. The caller
    /// retains no ownership after this returns; malformed images leave the last
    /// valid generation intact.
    pub fn apply_raw_image_update(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        payloads: Vec<GuiRawImageAssetPayload>,
    ) -> GalResult<()> {
        if generation == 0 || generation <= self.raw_image_generation {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "stale raw GUI image generation {generation}; current generation is {}",
                    self.raw_image_generation
                ),
            ));
        }
        let mut images = BTreeMap::new();
        for payload in payloads {
            if payload.asset_id == 0
                || payload.width == 0
                || payload.height == 0
                || payload.width > 8192
                || payload.height > 8192
            {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "invalid raw GUI image {} dimensions {}x{}",
                        payload.asset_id, payload.width, payload.height
                    ),
                ));
            }
            let expected = (payload.width as usize)
                .checked_mul(payload.height as usize)
                .and_then(|pixels| pixels.checked_mul(payload.format.bytes_per_pixel()))
                .ok_or_else(|| {
                    GalError::ffi(StatusCode::InvalidArgument, "raw GUI image size overflows")
                })?;
            if payload.pixels.len() != expected {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!(
                        "raw GUI image {} has {} bytes; expected {expected}",
                        payload.asset_id,
                        payload.pixels.len()
                    ),
                ));
            }
            if images
                .insert(
                    payload.asset_id,
                    RawGuiImage {
                        format: payload.format,
                        width: payload.width,
                        height: payload.height,
                        pixels: payload.pixels,
                    },
                )
                .is_some()
            {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "duplicate raw GUI image asset id",
                ));
            }
        }
        self.destroy_dynamic_resources(gal);
        self.raw_images = images;
        self.raw_image_generation = generation;
        Ok(())
    }

    fn destroy_dynamic_resources(&mut self, gal: &mut VulkanicGal) {
        let keys: Vec<_> = self
            .resources
            .keys()
            .copied()
            .filter(|key| matches!(key.group, TextureGroupKey::Dynamic(_)))
            .collect();
        for key in keys {
            if let Some(resource) = self.resources.remove(&key) {
                for handle in resource.handles_in_destroy_order() {
                    let _ = gal.destroy(handle);
                }
            }
        }
        let mesh_rasters = std::mem::take(&mut self.mesh_rasters);
        for resources in mesh_rasters.into_values() {
            resources.destroy(gal);
        }
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

    pub fn submit_frame(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        requests: Vec<GuiSpriteRequest>,
    ) -> GalResult<GuiSubmitStats> {
        self.submit_frame_with_affine_quads(gal, generation, frame_target, requests, Vec::new())
    }

    pub fn submit_frame_with_affine_quads(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
    ) -> GalResult<GuiSubmitStats> {
        self.submit_frame_with_affine_quads_and_mesh_batches(
            gal,
            generation,
            frame_target,
            requests,
            affine_quads,
            Vec::new(),
        )
    }

    pub fn submit_frame_with_affine_quads_and_mesh_batches(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
    ) -> GalResult<GuiSubmitStats> {
        let (ops, mut stats) = self.append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
            gal,
            generation,
            frame_target,
            frame_target,
            None,
            None,
            None,
            false,
            requests,
            affine_quads,
            mesh_batches,
        )?;
        let token = gal.submit(SubmissionBatch {
            label: "minecraft.gui.frame".to_string(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "minecraft.gui.frame.commands".to_string(),
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
        requests: Vec<GuiSpriteRequest>,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        self.append_frame_ops_with_affine_quads(gal, generation, frame_target, requests, Vec::new())
    }

    pub fn append_frame_ops_with_affine_quads(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        self.append_frame_ops_with_affine_quads_to_target(
            gal,
            generation,
            frame_target,
            frame_target,
            None,
            None,
            None,
            false,
            requests,
            affine_quads,
        )
    }

    /// Records GUI work against an explicit GAL render target and its color
    /// attachment. The ordinary whole-frame route uses the acquired target for
    /// both values; source-owned shader frames provide their overlay target
    /// and view so GUI joins the final Rust composition before presentation.
    pub fn append_frame_ops_with_affine_quads_to_target(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        render_target: Handle,
        color_attachment: Handle,
        render_pass: Option<Handle>,
        depth_attachment: Option<Handle>,
        depth_format: Option<TextureFormat>,
        pre_present_y_flip: bool,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        if generation != self.generation {
            self.destroy_render_resources(gal);
            self.generation = generation;
        }
        let color_format = gal.pass_target_color_format(render_target)?;
        let frame_pass = match render_pass {
            Some(pass) => pass,
            None => self.frame_pass(gal, render_target)?,
        };
        let mut batches: Vec<GuiBatch> = Vec::new();
        let mut stats = GuiSubmitStats {
            sprite_count: requests.len() as u64,
            affine_quad_count: affine_quads.len() as u64,
            ..GuiSubmitStats::default()
        };
        let ordered = order_gui_requests(requests, affine_quads)?;
        for request in ordered {
            match request {
                GuiFrameRequest::Sprite(request) => {
                    let def = sprite_def(request.sprite_id)?;
                    if request.stratum != def.stratum {
                        return Err(GalError::ffi(
                            StatusCode::InvalidArgument,
                            format!(
                                "GUI sprite '{}' requested stratum {} but registry stratum is {}",
                                def.name, request.stratum, def.stratum
                            ),
                        ));
                    }
                    validate_request(&request, def)?;
                    let group = def.group;
                    self.ensure_resources(gal, group, color_format, depth_format, &mut stats)?;
                    let quad = self.pack_sprite(&request, def, pre_present_y_flip)?;
                    append_gui_quad(&mut batches, request.stratum, group, quad);
                }
                GuiFrameRequest::Affine(request) => {
                    let image_format = self
                        .raw_images
                        .get(&request.asset_id)
                        .ok_or_else(|| {
                            GalError::ffi(
                                StatusCode::InvalidArgument,
                                format!("unknown raw GUI image asset {}", request.asset_id),
                            )
                        })?
                        .format;
                    validate_affine_quad(&request)?;
                    let group = TextureGroup::Dynamic(request.asset_id);
                    self.ensure_resources(gal, group, color_format, depth_format, &mut stats)?;
                    let quad = PackedGuiQuad {
                        origin: [request.x0, request.y0],
                        axis_u: [request.x1 - request.x0, request.y1 - request.y0],
                        axis_v: [request.x3 - request.x0, request.y3 - request.y0],
                        viewport: [request.gui_width as f32, request.gui_height as f32],
                        clip: [
                            request.clip_left as f32,
                            request.clip_top as f32,
                            (request.clip_left + request.clip_width) as f32,
                            (request.clip_top + request.clip_height) as f32,
                        ],
                        clip_enabled: request.clip_mode == 1,
                        pre_present_y_flip,
                        uv: [
                            request.u0,
                            request.v0,
                            request.u1 - request.u0,
                            request.v1 - request.v0,
                        ],
                        color: argb_to_rgba(request.color_argb),
                        texture_mode: image_format.shader_mode(),
                        z: request.z,
                    };
                    append_gui_quad(&mut batches, request.stratum, group, quad);
                }
                GuiFrameRequest::Mesh(_) => {
                    return Err(GalError::backend(
                        "mesh GUI request reached the sprite/affine-only frame builder",
                    ));
                }
            }
        }
        stats.sprite_batch_count = batches.len() as u64;
        let mut ops = Vec::new();
        let whole_frame_vulkan = gal.capabilities().api == BackendApi::Vulkan;
        if whole_frame_vulkan {
            ops.push(CommandOp::BeginPass {
                pass: frame_pass,
                target: render_target,
                colors: vec![loaded_frame_color_attachment(color_attachment)],
                depth_stencil: depth_attachment.map(loaded_frame_depth_attachment),
            });
            ops.push(CommandOp::EndPass);
        } else if batches.is_empty() {
            ops.push(CommandOp::BeginPass {
                pass: frame_pass,
                target: render_target,
                colors: vec![loaded_frame_color_attachment(color_attachment)],
                depth_stencil: depth_attachment.map(loaded_frame_depth_attachment),
            });
            ops.push(CommandOp::EndPass);
        }
        for batch in &batches {
            let resources = self
                .resources
                .get(&ResourceKey::new(batch.group, color_format, depth_format))
                .ok_or_else(|| GalError::backend("GUI resources vanished before submit"))?;
            let uniforms = packed_uniform_bytes(batch)?;
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
                pass: frame_pass,
                target: render_target,
                colors: vec![loaded_frame_color_attachment(color_attachment)],
                depth_stencil: depth_attachment.map(loaded_frame_depth_attachment),
            });
            ops.push(CommandOp::BindGraphicsPipeline(resources.pipeline));
            ops.push(CommandOp::BindResourceSet {
                pipeline_layout: resources.pipeline_layout,
                set_index: 0,
                set: resources.resource_set,
                dynamic_offsets: Vec::new(),
            });
            ops.push(CommandOp::SetIndexBuffer {
                buffer: resources.index_buffer,
                offset: 0,
                index_type: super::resources::IndexType::U32,
            });
            ops.push(CommandOp::DrawIndexed {
                indices: 6,
                instances: batch.quads.len() as u32,
            });
            ops.push(CommandOp::EndPass);
        }
        stats.command_lists = 1;
        stats.command_ops = ops.len() as u64;
        Ok((ops, stats))
    }

    /// Ordered variant for the private standard-3D item family. Mesh items
    /// split ordinary GUI batches at their scheduler sequence so a 3D PIP
    /// composite can never jump ahead of text or sprites in the same stratum.
    pub(crate) fn append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        render_target: Handle,
        color_attachment: Handle,
        render_pass: Option<Handle>,
        depth_attachment: Option<Handle>,
        depth_format: Option<TextureFormat>,
        pre_present_y_flip: bool,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        if mesh_batches.is_empty() {
            return self.append_frame_ops_with_affine_quads_to_target(
                gal,
                generation,
                render_target,
                color_attachment,
                render_pass,
                depth_attachment,
                depth_format,
                pre_present_y_flip,
                requests,
                affine_quads,
            );
        }
        if generation != self.generation {
            self.destroy_render_resources(gal);
            self.generation = generation;
        }
        let color_format = gal.pass_target_color_format(render_target)?;
        let frame_pass = match render_pass {
            Some(pass) => pass,
            None => self.frame_pass(gal, render_target)?,
        };
        let ordered = order_gui_requests_with_mesh(requests, affine_quads, mesh_batches)?;
        self.mesh_composite_uniform_cursor = 0;
        let mut stats = GuiSubmitStats::default();
        let mut ops = Vec::new();
        if gal.capabilities().api == BackendApi::Vulkan {
            ops.push(CommandOp::BeginPass {
                pass: frame_pass,
                target: render_target,
                colors: vec![loaded_frame_color_attachment(color_attachment)],
                depth_stencil: depth_attachment.map(loaded_frame_depth_attachment),
            });
            ops.push(CommandOp::EndPass);
        }
        for request in ordered {
            match request {
                GuiFrameRequest::Sprite(request) => {
                    let def = sprite_def(request.sprite_id)?;
                    if request.stratum != def.stratum {
                        return Err(GalError::ffi(
                            StatusCode::InvalidArgument,
                            format!(
                                "GUI sprite '{}' requested stratum {} but registry stratum is {}",
                                def.name, request.stratum, def.stratum
                            ),
                        ));
                    }
                    validate_request(&request, def)?;
                    self.ensure_resources(gal, def.group, color_format, depth_format, &mut stats)?;
                    let batch = GuiBatch {
                        stratum: request.stratum,
                        group: def.group,
                        quads: vec![self.pack_sprite(&request, def, pre_present_y_flip)?],
                    };
                    append_gui_batches_ops(
                        self,
                        frame_pass,
                        render_target,
                        color_attachment,
                        depth_attachment,
                        color_format,
                        depth_format,
                        &[batch],
                        &mut ops,
                    )?;
                    stats.sprite_count = stats.sprite_count.saturating_add(1);
                    stats.sprite_batch_count = stats.sprite_batch_count.saturating_add(1);
                }
                GuiFrameRequest::Affine(request) => {
                    let image_format = self
                        .raw_images
                        .get(&request.asset_id)
                        .ok_or_else(|| {
                            GalError::ffi(
                                StatusCode::InvalidArgument,
                                format!("unknown raw GUI image asset {}", request.asset_id),
                            )
                        })?
                        .format;
                    validate_affine_quad(&request)?;
                    let group = TextureGroup::Dynamic(request.asset_id);
                    self.ensure_resources(gal, group, color_format, depth_format, &mut stats)?;
                    let batch = GuiBatch {
                        stratum: request.stratum,
                        group,
                        quads: vec![PackedGuiQuad {
                            origin: [request.x0, request.y0],
                            axis_u: [request.x1 - request.x0, request.y1 - request.y0],
                            axis_v: [request.x3 - request.x0, request.y3 - request.y0],
                            viewport: [request.gui_width as f32, request.gui_height as f32],
                            clip: [
                                request.clip_left as f32,
                                request.clip_top as f32,
                                (request.clip_left + request.clip_width) as f32,
                                (request.clip_top + request.clip_height) as f32,
                            ],
                            clip_enabled: request.clip_mode == 1,
                            pre_present_y_flip,
                            uv: [
                                request.u0,
                                request.v0,
                                request.u1 - request.u0,
                                request.v1 - request.v0,
                            ],
                            color: argb_to_rgba(request.color_argb),
                            texture_mode: image_format.shader_mode(),
                            z: request.z,
                        }],
                    };
                    append_gui_batches_ops(
                        self,
                        frame_pass,
                        render_target,
                        color_attachment,
                        depth_attachment,
                        color_format,
                        depth_format,
                        &[batch],
                        &mut ops,
                    )?;
                    stats.affine_quad_count = stats.affine_quad_count.saturating_add(1);
                    stats.sprite_batch_count = stats.sprite_batch_count.saturating_add(1);
                }
                GuiFrameRequest::Mesh(item) => {
                    let mesh_ops = self.append_mesh_items_to_target(
                        gal,
                        generation,
                        render_target,
                        color_attachment,
                        Some(frame_pass),
                        depth_attachment,
                        depth_format,
                        item.layers,
                        &mut stats,
                    )?;
                    ops.extend(mesh_ops);
                }
            }
        }
        stats.command_lists = 1;
        stats.command_ops = ops.len() as u64;
        Ok((ops, stats))
    }

    /// Appends one complete Rust-owned standard-3D GUI-item family. The caller
    /// supplies copied semantic mesh batches only; source images are resolved
    /// from the existing Rust GUI asset generation, each item is rasterized
    /// into an owned PIP target, and its result is composed through the normal
    /// GUI target. This remains private until the frame ABI and route select it.
    pub(crate) fn append_mesh_items_to_target(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        render_target: Handle,
        color_attachment: Handle,
        render_pass: Option<Handle>,
        depth_attachment: Option<Handle>,
        depth_format: Option<TextureFormat>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
        stats: &mut GuiSubmitStats,
    ) -> GalResult<Vec<CommandOp>> {
        if generation != self.generation {
            self.destroy_render_resources(gal);
            self.generation = generation;
        }
        let mut prepared = prepare_gui_mesh_draws(&mesh_batches)?;
        prepared.sort_by_key(|draw| (draw.stratum, draw.sequence, draw.layer_index));
        if prepared.is_empty() {
            return Ok(Vec::new());
        }
        let frame_pass = match render_pass {
            Some(pass) => pass,
            None => self.frame_pass(gal, render_target)?,
        };
        let color_format = gal.pass_target_color_format(render_target)?;
        let mut operations = Vec::new();
        // Buffers are persistent per image asset, but their contents must stay
        // distinct for every draw recorded in this submission. Reusing offset
        // zero made a multi-quad item rasterize each earlier face with the
        // last face's geometry once Vulkan executed the command list.
        let mut mesh_stream_ranges = BTreeMap::<GuiMeshRasterKey, GuiMeshStreamRange>::new();
        let mut cursor = 0;
        while cursor < prepared.len() {
            let first = &prepared[cursor];
            let group_key = (first.stratum, first.sequence);
            let group_end = prepared[cursor..]
                .iter()
                .position(|draw| (draw.stratum, draw.sequence) != group_key)
                .map(|offset| cursor + offset)
                .unwrap_or(prepared.len());
            let item_layers = &prepared[cursor..group_end];
            validate_mesh_item_layers(item_layers)?;
            stats.mesh_item_count = stats.mesh_item_count.saturating_add(1);
            let target = self.mesh_targets.stage(
                gal,
                generation,
                Extent3d {
                    width: first.render_extent[0],
                    height: first.render_extent[1],
                    depth: 1,
                },
            )?;
            if !stats.owned_intermediate_targets.contains(&target.target) {
                stats.owned_intermediate_targets.push(target.target);
            }
            for draw in item_layers {
                self.ensure_resources(
                    gal,
                    TextureGroup::Dynamic(draw.asset_id),
                    color_format,
                    depth_format,
                    stats,
                )?;
                let (texture_view, sampler) = {
                    let raw_resources = self
                        .resources
                        .get(&ResourceKey::new(
                            TextureGroup::Dynamic(draw.asset_id),
                            color_format,
                            depth_format,
                        ))
                        .ok_or_else(|| {
                            GalError::backend("GUI mesh image resources vanished before raster")
                        })?;
                    (raw_resources.texture_view, raw_resources.sampler)
                };
                let raster_key = gui_mesh_raster_key(draw);
                if !self.mesh_rasters.contains_key(&raster_key) {
                    let raster = GuiMeshPassResources::create(
                        gal,
                        &format!(
                            "minecraft.gui.mesh.asset{}.gen{}",
                            draw.asset_id, generation
                        ),
                        TextureFormat::Rgba8Unorm,
                        texture_view,
                        sampler,
                        draw.material_mode,
                        draw.front_face,
                    )?;
                    self.mesh_rasters.insert(raster_key, raster);
                    stats.resource_creates = stats.resource_creates.saturating_add(1);
                }
                let raster = self.mesh_rasters.get(&raster_key).ok_or_else(|| {
                    GalError::backend("GUI mesh raster resources vanished before draw")
                })?;
                let stream = *mesh_stream_ranges.entry(raster_key).or_default();
                raster.append_draw(target, draw, stream, draw.layer_index == 0, &mut operations)?;
                let next_vertex_offset = stream
                    .vertex_offset
                    .checked_add(
                        (draw.vertices.len() * super::gui_mesh_frontend::GUI_MESH_GPU_VERTEX_BYTES)
                            as u64,
                    )
                    .ok_or_else(|| {
                        GalError::ffi(
                            StatusCode::InvalidArgument,
                            "GUI mesh vertex stream cursor overflows",
                        )
                    })?;
                let next_index_offset = stream
                    .index_offset
                    .checked_add((draw.indices.len() * std::mem::size_of::<u32>()) as u64)
                    .ok_or_else(|| {
                        GalError::ffi(
                            StatusCode::InvalidArgument,
                            "GUI mesh index stream cursor overflows",
                        )
                    })?;
                mesh_stream_ranges.insert(
                    raster_key,
                    GuiMeshStreamRange {
                        vertex_offset: next_vertex_offset,
                        index_offset: next_index_offset,
                    },
                );
                stats.mesh_batch_count = stats.mesh_batch_count.saturating_add(1);
                stats.mesh_draw_count = stats.mesh_draw_count.saturating_add(1);
            }
            let composite_key = GuiMeshCompositeKey {
                width: target.extent.width,
                height: target.extent.height,
                color_format,
                depth_format,
            };
            if !self.mesh_composites.contains_key(&composite_key) {
                let composite = GuiMeshCompositeResources::create(
                    gal,
                    &format!(
                        "minecraft.gui.mesh.composite.gen{}.{}x{}",
                        generation, target.extent.width, target.extent.height
                    ),
                    color_format,
                    depth_format,
                    target.color_view,
                )?;
                self.mesh_composites.insert(composite_key, composite);
                stats.resource_creates = stats.resource_creates.saturating_add(1);
            }
            let composite = self
                .mesh_composites
                .get(&composite_key)
                .ok_or_else(|| GalError::backend("GUI mesh compositor vanished before draw"))?;
            composite.append_composite(
                target,
                frame_pass,
                render_target,
                color_attachment,
                depth_attachment,
                first,
                self.mesh_composite_uniform_cursor,
                &mut operations,
            )?;
            self.mesh_composite_uniform_cursor = self
                .mesh_composite_uniform_cursor
                .checked_add(GUI_MESH_COMPOSITE_UNIFORM_STRIDE)
                .ok_or_else(|| {
                    GalError::ffi(
                        StatusCode::InvalidArgument,
                        "GUI mesh composite uniform stream cursor overflows",
                    )
                })?;
            stats.mesh_draw_count = stats.mesh_draw_count.saturating_add(1);
            cursor = group_end;
        }
        stats.command_ops = stats.command_ops.saturating_add(operations.len() as u64);
        Ok(operations)
    }

    /// Replays GUI semantics into a frame-local diagnostic target without
    /// placing the target in the normal frame-pass cache. The caller retires
    /// the returned pass after the capture submission completes.
    pub(crate) fn append_frame_ops_to_transient_diagnostic_target(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        render_target: Handle,
        color_attachment: Handle,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        let pass = gal.create_render_pass(RenderPassDesc {
            label: "minecraft.gui.diagnostic-frame.pass".to_string(),
            target: render_target,
            color_formats: vec![gal.pass_target_color_format(render_target)?],
            depth_format: None,
        })?;
        match self.append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
            gal,
            generation,
            render_target,
            color_attachment,
            Some(pass),
            None,
            None,
            false,
            requests,
            affine_quads,
            mesh_batches,
        ) {
            Ok((ops, mut stats)) => {
                stats.transient_diagnostic_passes.push(pass);
                Ok((ops, stats))
            }
            Err(error) => {
                let _ = gal.destroy(pass);
                Err(error)
            }
        }
    }

    fn frame_pass(&mut self, gal: &mut VulkanicGal, frame_target: Handle) -> GalResult<Handle> {
        if let Some(cached) = self.cached_pass {
            if cached.frame_target == frame_target {
                return Ok(cached.pass);
            }
            gal.destroy(cached.pass)?;
            self.cached_pass = None;
        }
        let pass = gal.create_render_pass(RenderPassDesc {
            label: "minecraft.gui.frame.pass".to_string(),
            target: frame_target,
            color_formats: vec![gal.pass_target_color_format(frame_target)?],
            depth_format: None,
        })?;
        self.cached_pass = Some(CachedPass { frame_target, pass });
        Ok(pass)
    }

    fn create_resources(
        &mut self,
        gal: &mut VulkanicGal,
        group: TextureGroup,
        color_format: ColorFormat,
        depth_format: Option<TextureFormat>,
    ) -> GalResult<GuiResources> {
        let label = format!("gui-textured-{}-gen{}", group.label(), self.generation);
        let source = self.texture_source(group)?;
        let vulkan_shader_syntax = gal.capabilities().api == BackendApi::Vulkan;
        let vertex_shader_code = if vulkan_shader_syntax {
            VERTEX_SHADER_VULKAN
        } else {
            VERTEX_SHADER_OPENGL
        };
        let fragment_shader_code = if vulkan_shader_syntax {
            FRAGMENT_SHADER_VULKAN
        } else {
            FRAGMENT_SHADER_OPENGL
        };
        let mut created = Vec::new();
        let result = (|| -> GalResult<GuiResources> {
            let upload_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.texture-upload"),
                size: source.bytes.len() as u64,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::TransferSrc,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(upload_buffer);
            let index_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.index"),
                size: index_bytes().len() as u64,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Index,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(index_buffer);
            let uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.uniform"),
                size: GUI_PACKED_UNIFORM_BYTES,
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
                format: source.format.texture_format(),
                extent: Extent3d {
                    width: source.width,
                    height: source.height,
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
                comparison: None,
            })?;
            created.push(sampler);
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: vertex_shader_code.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: fragment_shader_code.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(fragment_shader);
            let texture_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.texture-view"),
                texture,
                format: source.format.texture_format(),
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
            })?;
            created.push(resource_set);
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
                cull_mode: CullMode::None,
                front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
                blend: group.blend(),
                depth_compare: None,
                depth_write: false,
                depth_bias: None,
                color_formats: vec![color_format],
                depth_format,
            })?;
            created.push(pipeline);
            let resources = GuiResources {
                upload_buffer,
                index_buffer,
                uniform_buffer,
                texture,
                sampler,
                vertex_shader,
                fragment_shader,
                texture_view,
                resource_layout,
                resource_set,
                pipeline_layout,
                pipeline,
            };
            self.upload_resources(gal, &source, group, &resources)?;
            Ok(resources)
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    fn upload_resources(
        &mut self,
        gal: &mut VulkanicGal,
        source: &GuiTextureSource,
        group: TextureGroup,
        resources: &GuiResources,
    ) -> GalResult<()> {
        gal.submit(SubmissionBatch {
            label: format!("gui-textured-{}.upload", group.label()),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: format!("gui-textured-{}.upload.commands", group.label()),
                operations: vec![
                    CommandOp::HostWriteBuffer {
                        buffer: resources.upload_buffer,
                        offset: 0,
                        data: source.bytes.clone(),
                    },
                    CommandOp::Barrier(buffer_barrier(
                        resources.upload_buffer,
                        TextureUsageState::TransferDst,
                        TextureUsageState::TransferSrc,
                    )),
                    CommandOp::HostWriteBuffer {
                        buffer: resources.index_buffer,
                        offset: 0,
                        data: index_bytes(),
                    },
                    CommandOp::Barrier(buffer_barrier(
                        resources.index_buffer,
                        TextureUsageState::TransferDst,
                        TextureUsageState::ShaderRead,
                    )),
                    CommandOp::Barrier(texture_barrier(
                        resources.texture,
                        TextureUsageState::Undefined,
                        TextureUsageState::TransferDst,
                    )),
                    CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                        buffer: resources.upload_buffer,
                        buffer_offset: 0,
                        bytes_per_row: source.width * source.format.bytes_per_pixel() as u32,
                        rows_per_image: source.height,
                        texture: resources.texture,
                        texture_mip: 0,
                        texture_layer: 0,
                        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                        extent: Extent3d {
                            width: source.width,
                            height: source.height,
                            depth: 1,
                        },
                    }),
                    CommandOp::Barrier(texture_barrier(
                        resources.texture,
                        TextureUsageState::TransferDst,
                        TextureUsageState::ShaderRead,
                    )),
                ],
            })],
        })?;
        Ok(())
    }

    fn atlas_for(&mut self, group: TextureGroup) -> GalResult<&TextureAtlas> {
        if matches!(group, TextureGroup::Dynamic(_)) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "raw GUI images do not belong to the static sprite atlas",
            ));
        }
        let key = TextureGroupKey::from(group);
        if !self.atlases.contains_key(&key) {
            let atlas = build_atlas(group, &self.asset_overrides)?;
            self.atlases.insert(key, atlas);
        }
        Ok(self.atlases.get(&key).expect("atlas was just inserted"))
    }

    fn texture_source(&mut self, group: TextureGroup) -> GalResult<GuiTextureSource> {
        match group {
            TextureGroup::Alpha | TextureGroup::Invert => {
                let atlas = self.atlas_for(group)?.clone();
                Ok(GuiTextureSource {
                    width: atlas.width,
                    height: atlas.height,
                    format: GuiRawImageFormat::Rgba8,
                    bytes: atlas.bytes,
                })
            }
            TextureGroup::Dynamic(asset_id) => {
                let image = self.raw_images.get(&asset_id).ok_or_else(|| {
                    GalError::ffi(
                        StatusCode::InvalidArgument,
                        format!("unknown raw GUI image asset {asset_id}"),
                    )
                })?;
                Ok(GuiTextureSource {
                    width: image.width,
                    height: image.height,
                    format: image.format,
                    bytes: image.pixels.clone(),
                })
            }
        }
    }

    fn ensure_resources(
        &mut self,
        gal: &mut VulkanicGal,
        group: TextureGroup,
        color_format: ColorFormat,
        depth_format: Option<TextureFormat>,
        stats: &mut GuiSubmitStats,
    ) -> GalResult<()> {
        let key = ResourceKey::new(group, color_format, depth_format);
        if self.resources.contains_key(&key) {
            stats.cache_hits += 1;
            return Ok(());
        }
        let resources = self.create_resources(gal, group, color_format, depth_format)?;
        self.resources.insert(key, resources);
        stats.cache_misses += 1;
        stats.resource_creates += 12;
        Ok(())
    }

    fn pack_sprite(
        &mut self,
        request: &GuiSpriteRequest,
        def: &SpriteDef,
        pre_present_y_flip: bool,
    ) -> GalResult<PackedGuiQuad> {
        let atlas = self.atlas_for(def.group)?;
        let region = atlas.regions.get(&def.id).ok_or_else(|| {
            GalError::backend(format!("sprite '{}' missing from atlas", def.name))
        })?;
        let source_width = request.width.min(def.width);
        let source_height = request.height.min(def.height);
        Ok(PackedGuiQuad {
            origin: [request.x as f32, request.y as f32],
            axis_u: [request.width as f32, 0.0],
            axis_v: [0.0, request.height as f32],
            viewport: [request.gui_width as f32, request.gui_height as f32],
            clip: [0.0; 4],
            clip_enabled: false,
            pre_present_y_flip,
            // GUI atlases and raw GUI images use the same semantic convention:
            // byte row zero and V=0 are the top edge. Backends upload the bytes
            // unchanged, and the shader resolves UVs with texelFetch so no
            // API-specific texture-origin conversion leaks into this frontend.
            uv: [
                region.x as f32 / atlas.width as f32,
                region.y as f32 / atlas.height as f32,
                source_width as f32 / atlas.width as f32,
                source_height as f32 / atlas.height as f32,
            ],
            color: argb_to_rgba(request.color_argb),
            texture_mode: GuiRawImageFormat::Rgba8.shader_mode(),
            z: 0.0,
        })
    }
}

fn append_gui_quad(
    batches: &mut Vec<GuiBatch>,
    stratum: u32,
    group: TextureGroup,
    quad: PackedGuiQuad,
) {
    if let Some(batch) = batches.last_mut().filter(|batch| {
        batch.stratum == stratum
            && batch.group == group
            && batch.quads.len() < GUI_MAX_PACKED_SPRITES
    }) {
        batch.quads.push(quad);
    } else {
        batches.push(GuiBatch {
            stratum,
            group,
            quads: vec![quad],
        });
    }
}

fn append_gui_batches_ops(
    frontend: &GuiFrontend,
    frame_pass: Handle,
    render_target: Handle,
    color_attachment: Handle,
    depth_attachment: Option<Handle>,
    color_format: ColorFormat,
    depth_format: Option<TextureFormat>,
    batches: &[GuiBatch],
    ops: &mut Vec<CommandOp>,
) -> GalResult<()> {
    for batch in batches {
        let resources = frontend
            .resources
            .get(&ResourceKey::new(batch.group, color_format, depth_format))
            .ok_or_else(|| GalError::backend("GUI resources vanished before ordered submit"))?;
        let uniforms = packed_uniform_bytes(batch)?;
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
            pass: frame_pass,
            target: render_target,
            colors: vec![loaded_frame_color_attachment(color_attachment)],
            depth_stencil: depth_attachment.map(loaded_frame_depth_attachment),
        });
        ops.push(CommandOp::BindGraphicsPipeline(resources.pipeline));
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout: resources.pipeline_layout,
            set_index: 0,
            set: resources.resource_set,
            dynamic_offsets: Vec::new(),
        });
        ops.push(CommandOp::SetIndexBuffer {
            buffer: resources.index_buffer,
            offset: 0,
            index_type: super::resources::IndexType::U32,
        });
        ops.push(CommandOp::DrawIndexed {
            indices: 6,
            instances: batch.quads.len() as u32,
        });
        ops.push(CommandOp::EndPass);
    }
    Ok(())
}

fn validate_mesh_item_layers(layers: &[GuiMeshPreparedDraw]) -> GalResult<()> {
    let first = layers.first().ok_or_else(|| {
        GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh item must contain one or more layers",
        )
    })?;
    for (expected_layer, layer) in layers.iter().enumerate() {
        if layer.layer_index != expected_layer as u32
            || layer.bounds != first.bounds
            || layer.gui_pose != first.gui_pose
            || layer.gui_extent != first.gui_extent
            || layer.render_extent != first.render_extent
            || layer.guard_pixels != first.guard_pixels
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh item layers must share target and composition semantics",
            ));
        }
    }
    Ok(())
}

fn order_gui_requests(
    sprites: Vec<GuiSpriteRequest>,
    affine_quads: Vec<GuiAffineQuadRequest>,
) -> GalResult<Vec<GuiFrameRequest>> {
    order_gui_requests_with_mesh(sprites, affine_quads, Vec::new())
}

fn order_gui_requests_with_mesh(
    sprites: Vec<GuiSpriteRequest>,
    affine_quads: Vec<GuiAffineQuadRequest>,
    mesh_batches: Vec<GuiMeshBatchRequest>,
) -> GalResult<Vec<GuiFrameRequest>> {
    let mesh_items = group_gui_mesh_items(mesh_batches)?;
    let mut ordered = sprites
        .into_iter()
        .map(GuiFrameRequest::Sprite)
        .chain(affine_quads.into_iter().map(GuiFrameRequest::Affine))
        .chain(mesh_items.into_iter().map(GuiFrameRequest::Mesh))
        .collect::<Vec<_>>();
    ordered.sort_by_key(|request| (request.stratum(), request.sequence()));
    validate_ordered_gui_requests(&ordered)?;
    Ok(ordered)
}

fn group_gui_mesh_items(mut batches: Vec<GuiMeshBatchRequest>) -> GalResult<Vec<GuiMeshItem>> {
    super::gui_mesh_frontend::validate_batches(&batches)?;
    batches.sort_by_key(|batch| (batch.stratum, batch.sequence, batch.layer_index));
    let mut items = Vec::new();
    let mut cursor = 0;
    while cursor < batches.len() {
        let first = &batches[cursor];
        let key = (first.stratum, first.sequence);
        let end = batches[cursor..]
            .iter()
            .position(|batch| (batch.stratum, batch.sequence) != key)
            .map(|offset| cursor + offset)
            .unwrap_or(batches.len());
        items.push(GuiMeshItem {
            stratum: key.0,
            sequence: key.1,
            layers: batches[cursor..end].to_vec(),
        });
        cursor = end;
    }
    Ok(items)
}

fn validate_ordered_gui_requests(requests: &[GuiFrameRequest]) -> GalResult<()> {
    let mut previous = None;
    for request in requests {
        if request.sequence() == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI request sequence must be non-zero",
            ));
        }
        let key = (request.stratum(), request.sequence());
        if previous.is_some_and(|previous| previous >= key) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI requests must have unique scheduler sequences in stratum order",
            ));
        }
        previous = Some(key);
    }
    Ok(())
}

fn packed_uniform_bytes(batch: &GuiBatch) -> GalResult<Vec<u8>> {
    if batch.quads.is_empty() || batch.quads.len() > GUI_MAX_PACKED_SPRITES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "packed GUI quad count must be in 1..{}: {}",
                GUI_MAX_PACKED_SPRITES,
                batch.quads.len()
            ),
        ));
    }
    let mut out = Vec::with_capacity(batch.quads.len() * GUI_UNIFORM_BYTES);
    for quad in &batch.quads {
        for value in quad.origin.into_iter().chain(quad.axis_u) {
            push_f32(&mut out, value);
        }
        for value in quad.axis_v.into_iter().chain([quad.texture_mode, quad.z]) {
            push_f32(&mut out, value);
        }
        for value in quad.viewport.into_iter().chain([
            if quad.clip_enabled { 1.0 } else { 0.0 },
            if quad.pre_present_y_flip { 1.0 } else { 0.0 },
        ]) {
            push_f32(&mut out, value);
        }
        for value in quad.clip {
            push_f32(&mut out, value);
        }
        for value in quad.uv {
            push_f32(&mut out, value);
        }
        for value in quad.color {
            push_f32(&mut out, value);
        }
    }
    Ok(out)
}

fn argb_to_rgba(color_argb: u32) -> [f32; 4] {
    [
        ((color_argb >> 16) & 0xff) as f32 / 255.0,
        ((color_argb >> 8) & 0xff) as f32 / 255.0,
        (color_argb & 0xff) as f32 / 255.0,
        ((color_argb >> 24) & 0xff) as f32 / 255.0,
    ]
}

pub(crate) fn validate_affine_quad(request: &GuiAffineQuadRequest) -> GalResult<()> {
    if request.asset_id == 0 || request.gui_width == 0 || request.gui_height == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI affine quad requires a non-zero asset and viewport",
        ));
    }
    let values = [
        request.x0, request.y0, request.x1, request.y1, request.x3, request.y3, request.z,
        request.u0, request.v0, request.u1, request.v1,
    ];
    if values.into_iter().any(|value| !value.is_finite()) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI affine quad coordinates must be finite",
        ));
    }
    if request.u0 < 0.0
        || request.v0 < 0.0
        || request.u1 > 1.0
        || request.v1 > 1.0
        || request.u1 < request.u0
        || request.v1 < request.v0
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI affine quad UV range must stay inside the semantic image",
        ));
    }
    match request.clip_mode {
        0 if request.clip_left == 0
            && request.clip_top == 0
            && request.clip_width == 0
            && request.clip_height == 0 => {}
        1 if request.clip_left >= 0
            && request.clip_top >= 0
            && request.clip_width >= 0
            && request.clip_height >= 0
            && request.clip_left.saturating_add(request.clip_width) <= request.gui_width as i32
            && request.clip_top.saturating_add(request.clip_height)
                <= request.gui_height as i32 => {}
        _ => {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI affine quad clip must be disabled or a bounded frame-local rectangle",
            ));
        }
    }
    Ok(())
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
        subresources: Some(TextureSubresourceRange {
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        }),
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

fn validate_request(request: &GuiSpriteRequest, def: &SpriteDef) -> GalResult<()> {
    if !request.progress_fraction.is_finite() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI sprite progress fraction must be finite",
        ));
    }
    if request.width == 0
        || request.height == 0
        || request.gui_width == 0
        || request.gui_height == 0
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI sprite dimensions and viewport must be non-zero",
        ));
    }
    if request.width > def.width || request.height > def.height {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "GUI sprite '{}' requested dimensions {}x{} exceed semantic sprite {}x{}",
                def.name, request.width, request.height, def.width, def.height
            ),
        ));
    }
    if request.fill_direction > 3 {
        return Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown GUI fill direction {}", request.fill_direction),
        ));
    }
    Ok(())
}

fn build_atlas(
    group: TextureGroup,
    asset_overrides: &BTreeMap<u32, Vec<u8>>,
) -> GalResult<TextureAtlas> {
    let sprites: Vec<&SpriteDef> = SPRITES
        .iter()
        .filter(|sprite| sprite.group == group)
        .collect();
    let mut width = 1u32;
    let mut height = 0u32;
    let mut row_width = 0u32;
    let mut row_height = 0u32;
    for sprite in &sprites {
        if row_width > 0 && row_width + sprite.width > 4096 {
            width = width.max(row_width);
            height += row_height;
            row_width = 0;
            row_height = 0;
        }
        row_width += sprite.width;
        row_height = row_height.max(sprite.height);
    }
    width = width.max(row_width);
    height += row_height;
    let mut bytes = vec![0u8; (width * height * 4) as usize];
    let mut regions = BTreeMap::new();
    let mut x_offset = 0u32;
    let mut y_offset = 0u32;
    row_height = 0;
    for sprite in sprites {
        if x_offset > 0 && x_offset + sprite.width > 4096 {
            x_offset = 0;
            y_offset += row_height;
            row_height = 0;
        }
        let sprite_bytes = load_sprite(sprite, asset_overrides)?;
        for y in 0..sprite.height {
            let src = (y * sprite.width * 4) as usize;
            let dst = ((y_offset + y) * width * 4 + x_offset * 4) as usize;
            bytes[dst..dst + (sprite.width * 4) as usize]
                .copy_from_slice(&sprite_bytes[src..src + (sprite.width * 4) as usize]);
        }
        regions.insert(
            sprite.id,
            AtlasRegion {
                x: x_offset,
                y: y_offset,
            },
        );
        x_offset += sprite.width;
        row_height = row_height.max(sprite.height);
    }
    Ok(TextureAtlas {
        width,
        height,
        bytes,
        regions,
    })
}

fn load_sprite(sprite: &SpriteDef, asset_overrides: &BTreeMap<u32, Vec<u8>>) -> GalResult<Vec<u8>> {
    let bytes = asset_overrides
        .get(&sprite.id)
        .map(Vec::as_slice)
        .or_else(|| bundled_sprite_bytes(sprite.path))
        .ok_or_else(|| {
            GalError::backend(format!("missing bundled GUI sprite '{}'", sprite.path))
        })?;
    decode_sprite_bytes(sprite, bytes)
}

fn decode_sprite_bytes(sprite: &SpriteDef, bytes: &[u8]) -> GalResult<Vec<u8>> {
    let mut decoder = png::Decoder::new(BufReader::new(bytes));
    decoder.set_transformations(png::Transformations::EXPAND | png::Transformations::STRIP_16);
    let mut reader = decoder.read_info().map_err(|error| {
        GalError::backend(format!(
            "failed to decode GUI sprite '{}': {error}",
            sprite.path
        ))
    })?;
    let mut buf = vec![0; reader.output_buffer_size()];
    let info = reader.next_frame(&mut buf).map_err(|error| {
        GalError::backend(format!(
            "failed to read GUI sprite '{}': {error}",
            sprite.path
        ))
    })?;
    if info.width != sprite.width || info.height != sprite.height {
        return Err(GalError::backend(format!(
            "unexpected GUI sprite dimensions for '{}': {}x{}, expected {}x{}",
            sprite.name, info.width, info.height, sprite.width, sprite.height
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
        png::ColorType::Indexed => Err(GalError::backend(format!(
            "indexed GUI sprite '{}' was not expanded by the PNG decoder",
            sprite.name
        ))),
    }
}

fn bundled_sprite_bytes(path: &str) -> Option<&'static [u8]> {
    match path {
        "/assets/minecraft/textures/gui/sprites/boss_bar/blue_background.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/blue_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/blue_progress.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/blue_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/green_background.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/green_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/green_progress.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/green_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_background.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_progress.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_background.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_progress.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_background.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_progress.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_background.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_progress.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/pink_background.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/pink_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/pink_progress.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/pink_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/purple_background.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/purple_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/purple_progress.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/purple_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/red_background.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/red_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/red_progress.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/red_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/white_background.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/white_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/yellow_background.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/yellow_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/boss_bar/yellow_progress.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/boss_bar/yellow_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/armor_empty.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/armor_empty.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/armor_full.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/armor_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/armor_half.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/armor_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/air.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/air.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/air_bursting.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/air_bursting.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/air_empty.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/air_empty.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/crosshair.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/crosshair.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_background.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_full.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_progress.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/experience_bar_background.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/experience_bar_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_full.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_full_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_half.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_half_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/container.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/container.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/container_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/container_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/full.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/full_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/half.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/half_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_full.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_full_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_half.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_half_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half_blinking.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half_blinking.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_container.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_container.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_full.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_half.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/hotbar.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/hotbar.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_background.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_background.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_progress.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_progress.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/food_empty.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/food_empty.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/food_half.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/food_half.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/food_full.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/food_full.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/food_empty_hunger.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/food_empty_hunger.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/food_half_hunger.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/food_half_hunger.png").as_slice()),
        "/assets/minecraft/textures/gui/sprites/hud/food_full_hunger.png" => Some(include_bytes!("../../../resources/assets/minecraft/textures/gui/sprites/hud/food_full_hunger.png").as_slice()),
        _ => None,
    }
}

fn index_bytes() -> Vec<u8> {
    let mut bytes = Vec::with_capacity(24);
    for value in [0u32, 1, 2, 3, 4, 5] {
        bytes.extend_from_slice(&value.to_le_bytes());
    }
    bytes
}

fn sprite_def(sprite_id: u32) -> GalResult<&'static SpriteDef> {
    SPRITES
        .iter()
        .find(|sprite| sprite.id == sprite_id)
        .ok_or_else(|| {
            GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown GUI sprite id {sprite_id}"),
            )
        })
}

const fn group(invert: bool) -> TextureGroup {
    if invert {
        TextureGroup::Invert
    } else {
        TextureGroup::Alpha
    }
}

const SPRITES: &[SpriteDef] = &[
    SpriteDef {
        id: 1,
        stratum: 200,
        name: "crosshair",
        path: "/assets/minecraft/textures/gui/sprites/hud/crosshair.png",
        width: 15,
        height: 15,
        group: group(true),
    },
    SpriteDef {
        id: 2,
        stratum: 300,
        name: "hotbar-base",
        path: "/assets/minecraft/textures/gui/sprites/hud/hotbar.png",
        width: 182,
        height: 22,
        group: group(false),
    },
    SpriteDef {
        id: 3,
        stratum: 310,
        name: "hotbar-selection",
        path: "/assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png",
        width: 24,
        height: 23,
        group: group(false),
    },
    SpriteDef {
        id: 4,
        stratum: 350,
        name: "armor-empty",
        path: "/assets/minecraft/textures/gui/sprites/hud/armor_empty.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 5,
        stratum: 350,
        name: "armor-half",
        path: "/assets/minecraft/textures/gui/sprites/hud/armor_half.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 6,
        stratum: 350,
        name: "armor-full",
        path: "/assets/minecraft/textures/gui/sprites/hud/armor_full.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 7,
        stratum: 360,
        name: "player-heart-container",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/container.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 8,
        stratum: 360,
        name: "player-heart-container",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/container_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 9,
        stratum: 360,
        name: "player-heart-container",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 10,
        stratum: 360,
        name: "player-heart-container",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 11,
        stratum: 360,
        name: "player-heart-normal",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/full.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 12,
        stratum: 360,
        name: "player-heart-normal",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/full_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 13,
        stratum: 360,
        name: "player-heart-normal",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/half.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 14,
        stratum: 360,
        name: "player-heart-normal",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/half_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 15,
        stratum: 360,
        name: "player-heart-normal",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 16,
        stratum: 360,
        name: "player-heart-normal",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 17,
        stratum: 360,
        name: "player-heart-normal",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 18,
        stratum: 360,
        name: "player-heart-normal",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 19,
        stratum: 360,
        name: "player-heart-poisoned",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 20,
        stratum: 360,
        name: "player-heart-poisoned",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 21,
        stratum: 360,
        name: "player-heart-poisoned",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 22,
        stratum: 360,
        name: "player-heart-poisoned",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 23,
        stratum: 360,
        name: "player-heart-poisoned",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 24,
        stratum: 360,
        name: "player-heart-poisoned",
        path:
            "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 25,
        stratum: 360,
        name: "player-heart-poisoned",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 26,
        stratum: 360,
        name: "player-heart-poisoned",
        path:
            "/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 27,
        stratum: 360,
        name: "player-heart-withered",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/withered_full.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 28,
        stratum: 360,
        name: "player-heart-withered",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/withered_full_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 29,
        stratum: 360,
        name: "player-heart-withered",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/withered_half.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 30,
        stratum: 360,
        name: "player-heart-withered",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/withered_half_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 31,
        stratum: 360,
        name: "player-heart-withered",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 32,
        stratum: 360,
        name: "player-heart-withered",
        path:
            "/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 33,
        stratum: 360,
        name: "player-heart-withered",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 34,
        stratum: 360,
        name: "player-heart-withered",
        path:
            "/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 35,
        stratum: 360,
        name: "player-heart-frozen",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 36,
        stratum: 360,
        name: "player-heart-frozen",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 37,
        stratum: 360,
        name: "player-heart-frozen",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 38,
        stratum: 360,
        name: "player-heart-frozen",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 39,
        stratum: 360,
        name: "player-heart-frozen",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 40,
        stratum: 360,
        name: "player-heart-frozen",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 41,
        stratum: 360,
        name: "player-heart-frozen",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 42,
        stratum: 360,
        name: "player-heart-frozen",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 43,
        stratum: 360,
        name: "absorption-heart",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 44,
        stratum: 360,
        name: "absorption-heart",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 45,
        stratum: 360,
        name: "absorption-heart",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 46,
        stratum: 360,
        name: "absorption-heart",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 47,
        stratum: 360,
        name: "absorption-heart",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_full.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 48,
        stratum: 360,
        name: "absorption-heart",
        path:
            "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_full_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 49,
        stratum: 360,
        name: "absorption-heart",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_half.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 50,
        stratum: 360,
        name: "absorption-heart",
        path:
            "/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_half_blinking.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 51,
        stratum: 400,
        name: "experience-background",
        path: "/assets/minecraft/textures/gui/sprites/hud/experience_bar_background.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 52,
        stratum: 410,
        name: "experience-progress",
        path: "/assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 53,
        stratum: 510,
        name: "attack-crosshair-full",
        path: "/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_full.png",
        width: 16,
        height: 16,
        group: group(false),
    },
    SpriteDef {
        id: 54,
        stratum: 500,
        name: "attack-crosshair-background",
        path:
            "/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_background.png",
        width: 16,
        height: 4,
        group: group(false),
    },
    SpriteDef {
        id: 55,
        stratum: 510,
        name: "attack-crosshair-progress",
        path: "/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_progress.png",
        width: 16,
        height: 4,
        group: group(false),
    },
    SpriteDef {
        id: 56,
        stratum: 520,
        name: "attack-hotbar-background",
        path: "/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_background.png",
        width: 18,
        height: 18,
        group: group(false),
    },
    SpriteDef {
        id: 57,
        stratum: 530,
        name: "attack-hotbar-progress",
        path: "/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_progress.png",
        width: 18,
        height: 18,
        group: group(false),
    },
    SpriteDef {
        id: 58,
        stratum: 600,
        name: "boss-bar-background",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/pink_background.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 59,
        stratum: 600,
        name: "boss-bar-background",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/blue_background.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 60,
        stratum: 600,
        name: "boss-bar-background",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/red_background.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 61,
        stratum: 600,
        name: "boss-bar-background",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/green_background.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 62,
        stratum: 600,
        name: "boss-bar-background",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/yellow_background.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 63,
        stratum: 600,
        name: "boss-bar-background",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/purple_background.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 64,
        stratum: 600,
        name: "boss-bar-background",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/white_background.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 65,
        stratum: 610,
        name: "boss-bar-progress",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/pink_progress.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 66,
        stratum: 610,
        name: "boss-bar-progress",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/blue_progress.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 67,
        stratum: 610,
        name: "boss-bar-progress",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/red_progress.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 68,
        stratum: 610,
        name: "boss-bar-progress",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/green_progress.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 69,
        stratum: 610,
        name: "boss-bar-progress",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/yellow_progress.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 70,
        stratum: 610,
        name: "boss-bar-progress",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/purple_progress.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 71,
        stratum: 610,
        name: "boss-bar-progress",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 72,
        stratum: 600,
        name: "boss-bar-overlay-background",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_background.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 73,
        stratum: 600,
        name: "boss-bar-overlay-background",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_background.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 74,
        stratum: 600,
        name: "boss-bar-overlay-background",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_background.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 75,
        stratum: 600,
        name: "boss-bar-overlay-background",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_background.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 76,
        stratum: 610,
        name: "boss-bar-overlay-progress",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_progress.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 77,
        stratum: 610,
        name: "boss-bar-overlay-progress",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_progress.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 78,
        stratum: 610,
        name: "boss-bar-overlay-progress",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_progress.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 79,
        stratum: 610,
        name: "boss-bar-overlay-progress",
        path: "/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_progress.png",
        width: 182,
        height: 5,
        group: group(false),
    },
    SpriteDef {
        id: 80,
        stratum: 370,
        name: "hunger-empty",
        path: "/assets/minecraft/textures/gui/sprites/hud/food_empty.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 81,
        stratum: 370,
        name: "hunger-half",
        path: "/assets/minecraft/textures/gui/sprites/hud/food_half.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 82,
        stratum: 370,
        name: "hunger-full",
        path: "/assets/minecraft/textures/gui/sprites/hud/food_full.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 83,
        stratum: 370,
        name: "hunger-effect-empty",
        path: "/assets/minecraft/textures/gui/sprites/hud/food_empty_hunger.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 84,
        stratum: 370,
        name: "hunger-effect-half",
        path: "/assets/minecraft/textures/gui/sprites/hud/food_half_hunger.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 85,
        stratum: 370,
        name: "hunger-effect-full",
        path: "/assets/minecraft/textures/gui/sprites/hud/food_full_hunger.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 86,
        stratum: 380,
        name: "air-full",
        path: "/assets/minecraft/textures/gui/sprites/hud/air.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 87,
        stratum: 380,
        name: "air-popping",
        path: "/assets/minecraft/textures/gui/sprites/hud/air_bursting.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 88,
        stratum: 380,
        name: "air-empty",
        path: "/assets/minecraft/textures/gui/sprites/hud/air_empty.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 89,
        stratum: 390,
        name: "mount-heart-container",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_container.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 90,
        stratum: 390,
        name: "mount-heart-full",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_full.png",
        width: 9,
        height: 9,
        group: group(false),
    },
    SpriteDef {
        id: 91,
        stratum: 390,
        name: "mount-heart-half",
        path: "/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_half.png",
        width: 9,
        height: 9,
        group: group(false),
    },
];

fn loaded_frame_color_attachment(frame_target: Handle) -> PassAttachment {
    PassAttachment {
        view: frame_target,
        load_op: AttachmentLoadOp::Load,
        store_op: AttachmentStoreOp::Store,
        clear_color: None,
    }
}

fn loaded_frame_depth_attachment(depth_attachment: Handle) -> PassAttachment {
    PassAttachment {
        view: depth_attachment,
        load_op: AttachmentLoadOp::Load,
        store_op: AttachmentStoreOp::Store,
        clear_color: None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::backends::{mock::MockBackend, vulkan_capabilities};
    use crate::render::vulkanic::frame::{FrameSurfaceDesc, PresentMode};
    use crate::render::vulkanic::gui_mesh_frontend::{
        GuiMeshLightingMode, GuiMeshMaterialMode, GuiMeshVertex,
    };
    use crate::render::vulkanic::resources::{Extent3d, FrameTargetDesc, TextureFormat};

    fn mock_gal() -> VulkanicGal {
        let mut capabilities = vulkan_capabilities();
        capabilities.features.presentation = true;
        VulkanicGal::new_with_backend(
            Box::new(MockBackend::with_capabilities(capabilities)),
            false,
        )
    }

    fn frame_target(gal: &mut VulkanicGal) -> Handle {
        gal.configure_frame_surface(FrameSurfaceDesc {
            label: "test-gui-frame-surface".to_owned(),
            extent: Extent3d {
                width: 320,
                height: 180,
                depth: 1,
            },
            color_format: TextureFormat::Rgba8Unorm,
            present_mode: PresentMode::Fifo,
            max_frames_in_flight: 2,
        })
        .unwrap();
        gal.create_frame_target(FrameTargetDesc {
            label: "test-gui-frame-target".to_owned(),
            frame_id: 1,
            render_target: crate::render::vulkanic::frame::FrameRenderTargetId(1),
            extent: Extent3d {
                width: 320,
                height: 180,
                depth: 1,
            },
            color_format: TextureFormat::Rgba8Unorm,
        })
        .unwrap()
    }

    fn request(sprite_id: u32) -> GuiSpriteRequest {
        let def = sprite_def(sprite_id).unwrap();
        GuiSpriteRequest {
            stratum: def.stratum,
            sprite_id,
            selected_slot: -1,
            progress_fraction: 1.0,
            fill_direction: 0,
            color_argb: 0xffffffff,
            x: 10,
            y: 10,
            width: def.width.min(16),
            height: def.height.min(16),
            gui_width: 320,
            gui_height: 180,
            sequence: 1,
        }
    }

    fn mesh_batch(layer_index: u32) -> GuiMeshBatchRequest {
        GuiMeshBatchRequest {
            stratum: 420,
            layer_index,
            sequence: 9,
            asset_id: 7,
            material_mode: GuiMeshMaterialMode::Cutout,
            lighting_mode: GuiMeshLightingMode::Block,
            alpha_cutoff: 0.5,
            model_transform: [
                16.0, 0.0, 0.0, 0.0, 0.0, -16.0, 0.0, 0.0, 0.0, 0.0, 16.0, 0.0, 17.0, 17.0, 0.0,
                1.0,
            ],
            gui_pose: [1.0, 0.0, 0.0, 1.0, 0.0, 0.0],
            bounds: [12, 34, 28, 50],
            gui_extent: [320, 180],
            render_extent: [34, 34],
            guard_pixels: 1,
            vertices: vec![
                GuiMeshVertex {
                    position: [0.0, 0.0, 0.0],
                    atlas_uv: [0.0, 0.0],
                    local_uv: [0.0, 0.0],
                    color_argb: 0xffff_ffff,
                    normal_packed: 0x007f_0000,
                },
                GuiMeshVertex {
                    position: [1.0, 0.0, 0.0],
                    atlas_uv: [1.0, 0.0],
                    local_uv: [1.0, 0.0],
                    color_argb: 0xffff_ffff,
                    normal_packed: 0x007f_0000,
                },
                GuiMeshVertex {
                    position: [0.0, 1.0, 0.0],
                    atlas_uv: [0.0, 1.0],
                    local_uv: [0.0, 1.0],
                    color_argb: 0xffff_ffff,
                    normal_packed: 0x007f_0000,
                },
            ],
            indices: vec![0, 1, 2],
        }
    }

    fn mesh_item_batches(sequence: u64) -> Vec<GuiMeshBatchRequest> {
        let mut layers = vec![mesh_batch(0), mesh_batch(1), mesh_batch(2)];
        for layer in &mut layers {
            layer.sequence = sequence;
        }
        layers
    }

    #[test]
    fn mesh_raster_cache_never_reuses_one_uniform_buffer_for_different_pip_inputs() {
        let first = prepare_gui_mesh_draws(&[mesh_batch(0)])
            .expect("prepare first GUI mesh draw")
            .pop()
            .unwrap();
        let mut changed_extent_batch = mesh_batch(0);
        changed_extent_batch.render_extent = [66, 34];
        let changed_extent = prepare_gui_mesh_draws(&[changed_extent_batch])
            .expect("prepare changed-extent GUI mesh draw")
            .pop()
            .unwrap();
        let mut changed_lighting_batch = mesh_batch(0);
        changed_lighting_batch.lighting_mode = GuiMeshLightingMode::Flat;
        let changed_lighting = prepare_gui_mesh_draws(&[changed_lighting_batch])
            .expect("prepare changed-lighting GUI mesh draw")
            .pop()
            .unwrap();

        assert_ne!(
            gui_mesh_raster_key(&first),
            gui_mesh_raster_key(&changed_extent)
        );
        assert_ne!(
            gui_mesh_raster_key(&first),
            gui_mesh_raster_key(&changed_lighting)
        );
        assert_eq!(gui_mesh_raster_key(&first), gui_mesh_raster_key(&first));
    }

    #[test]
    fn owned_mesh_items_rasterize_layers_then_compose_once_into_the_gui_target() {
        let mut gal = mock_gal();
        let target = frame_target(&mut gal);
        let mut frontend = GuiFrontend::default();
        frontend
            .apply_raw_image_update(
                &mut gal,
                1,
                vec![GuiRawImageAssetPayload {
                    asset_id: 7,
                    format: GuiRawImageFormat::Rgba8,
                    width: 1,
                    height: 1,
                    pixels: vec![255, 255, 255, 255],
                }],
            )
            .expect("stage owned mesh image");
        let affine = |sequence, x| GuiAffineQuadRequest {
            stratum: 420,
            asset_id: 7,
            x0: x,
            y0: 4.0,
            x1: x + 8.0,
            y1: 4.0,
            x3: x,
            y3: 12.0,
            z: 0.0,
            u0: 0.0,
            v0: 0.0,
            u1: 1.0,
            v1: 1.0,
            color_argb: 0xffff_ffff,
            gui_width: 320,
            gui_height: 180,
            sequence,
            clip_mode: 0,
            clip_left: 0,
            clip_top: 0,
            clip_width: 0,
            clip_height: 0,
        };
        let (ops, stats) = frontend
            .append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
                &mut gal,
                1,
                target,
                target,
                None,
                None,
                None,
                false,
                Vec::new(),
                vec![affine(8, 2.0), affine(10, 20.0)],
                vec![mesh_batch(0), mesh_batch(1), mesh_batch(2)],
            )
            .expect("append ordered GUI item mesh layers");
        assert_eq!(1, stats.mesh_item_count);
        assert_eq!(3, stats.mesh_batch_count);
        assert_eq!(4, stats.mesh_draw_count);
        assert_eq!(
            1,
            stats.owned_intermediate_targets.len(),
            "the GUI frontend must explicitly report its one Rust-owned raster target"
        );
        assert_ne!(target, stats.owned_intermediate_targets[0]);
        assert_eq!(
            3,
            ops.iter()
                .filter(|op| matches!(
                    op,
                    CommandOp::DrawIndexed {
                        indices: 3,
                        instances: 1
                    }
                ))
                .count()
        );
        let vertex_writes = ops
            .iter()
            .filter_map(|op| match op {
                CommandOp::HostWriteBuffer { offset, data, .. } if data.len() == 3 * 48 => {
                    Some((*offset, data.clone()))
                }
                _ => None,
            })
            .collect::<Vec<_>>();
        assert_eq!(
            vec![0, 3 * 48, 6 * 48],
            vertex_writes
                .iter()
                .map(|(offset, _)| *offset)
                .collect::<Vec<_>>(),
            "same-asset item layers must retain distinct stream ranges until submission"
        );
        let index_writes = ops
            .iter()
            .filter_map(|op| match op {
                CommandOp::HostWriteBuffer { offset, data, .. } if data.len() == 3 * 4 => {
                    Some((*offset, data.clone()))
                }
                _ => None,
            })
            .collect::<Vec<_>>();
        assert_eq!(
            vec![0, 3 * 4, 6 * 4],
            index_writes
                .iter()
                .map(|(offset, _)| *offset)
                .collect::<Vec<_>>()
        );
        let second_indices = index_writes[1]
            .1
            .chunks_exact(4)
            .map(|word| u32::from_le_bytes(word.try_into().unwrap()))
            .collect::<Vec<_>>();
        assert_eq!(vec![3, 4, 5], second_indices);
        let third_indices = index_writes[2]
            .1
            .chunks_exact(4)
            .map(|word| u32::from_le_bytes(word.try_into().unwrap()))
            .collect::<Vec<_>>();
        assert_eq!(vec![6, 7, 8], third_indices);
        let mesh_draw = ops
            .iter()
            .position(|op| {
                matches!(
                    op,
                    CommandOp::DrawIndexed {
                        indices: 3,
                        instances: 1
                    }
                )
            })
            .expect("mesh raster draw");
        let composite_draw = ops
            .iter()
            .position(|op| {
                matches!(
                    op,
                    CommandOp::Draw {
                        vertices: 6,
                        instances: 1
                    }
                )
            })
            .expect("mesh composite draw");
        let affine_draws = ops
            .iter()
            .enumerate()
            .filter_map(|(index, op)| {
                matches!(
                    op,
                    CommandOp::DrawIndexed {
                        indices: 6,
                        instances: 1
                    }
                )
                .then_some(index)
            })
            .collect::<Vec<_>>();
        assert_eq!(2, affine_draws.len());
        assert!(
            affine_draws[0] < mesh_draw
                && mesh_draw < composite_draw
                && composite_draw < affine_draws[1]
        );
        assert_eq!(1, frontend.mesh_targets.len());
        assert_eq!(1, frontend.mesh_composites.len());
        assert_eq!(1, frontend.mesh_rasters.len());
        gal.submit(SubmissionBatch {
            label: "gui-mesh-frontend-test".to_string(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "gui-mesh-frontend-test-commands".to_string(),
                operations: ops,
            })],
        })
        .expect("GAL validates ordered mesh raster and composite passes");
        frontend.reset(&mut gal);
    }

    #[test]
    fn mesh_items_reusing_an_extent_transition_the_raster_target_back_to_attachment_usage() {
        let mut gal = mock_gal();
        let mut frontend = GuiFrontend::default();
        let target = frame_target(&mut gal);
        frontend
            .apply_raw_image_update(
                &mut gal,
                1,
                vec![
                    GuiRawImageAssetPayload {
                        asset_id: 7,
                        format: GuiRawImageFormat::Rgba8,
                        width: 1,
                        height: 1,
                        pixels: vec![255, 255, 255, 255],
                    },
                    GuiRawImageAssetPayload {
                        asset_id: 8,
                        format: GuiRawImageFormat::Rgba8,
                        width: 1,
                        height: 1,
                        pixels: vec![255, 255, 255, 255],
                    },
                ],
            )
            .expect("raw images");
        let mut requests = mesh_item_batches(9);
        let mut next_item = mesh_item_batches(10);
        for layer in &mut next_item {
            layer.asset_id = 8;
        }
        requests.append(&mut next_item);
        let (ops, stats) = frontend
            .append_frame_ops_with_affine_quads_and_mesh_batches_to_target(
                &mut gal,
                1,
                target,
                target,
                None,
                None,
                None,
                false,
                Vec::new(),
                Vec::new(),
                requests,
            )
            .expect("two mesh items sharing an owned raster target");
        assert_eq!(stats.mesh_item_count, 2);
        assert_eq!(stats.mesh_batch_count, 6);
        assert_eq!(stats.mesh_draw_count, 8);
        let composite_uniform_buffers = frontend
            .mesh_composites
            .values()
            .map(|resources| resources.uniform_buffer)
            .collect::<Vec<_>>();
        let composite_resource_sets = frontend
            .mesh_composites
            .values()
            .map(|resources| resources.resource_set)
            .collect::<Vec<_>>();
        let composite_bind_indices = ops
            .iter()
            .enumerate()
            .filter_map(|operation| match operation {
                (
                    index,
                    CommandOp::BindResourceSet {
                        set,
                        dynamic_offsets,
                        ..
                    },
                ) if composite_resource_sets.contains(set) => {
                    Some((index, dynamic_offsets.clone()))
                }
                _ => None,
            })
            .collect::<Vec<_>>();
        assert_eq!(
            vec![vec![0], vec![GUI_MESH_COMPOSITE_UNIFORM_STRIDE]],
            composite_bind_indices
                .iter()
                .map(|(_, offsets)| offsets.clone())
                .collect::<Vec<_>>(),
            "each GUI item composite must bind its own uniform range"
        );
        let composite_uniform_offsets = composite_bind_indices
            .iter()
            .map(|(bind_index, _)| {
                ops[..*bind_index]
                    .iter()
                    .rev()
                    .find_map(|operation| match operation {
                        CommandOp::HostWriteBuffer { buffer, offset, .. }
                            if composite_uniform_buffers.contains(buffer) =>
                        {
                            Some(*offset)
                        }
                        _ => None,
                    })
                    .expect("a GUI mesh composite must write its bound UBO range")
            })
            .collect::<Vec<_>>();
        assert_eq!(
            vec![0, GUI_MESH_COMPOSITE_UNIFORM_STRIDE],
            composite_uniform_offsets,
            "same-sized GUI items must not overwrite one another's composite pose before execution"
        );
        assert_eq!(
            ops.iter()
                .filter(|operation| matches!(
                    operation,
                    CommandOp::Barrier(ResourceBarrier {
                        before: TextureUsageState::ShaderRead,
                        after: TextureUsageState::ColorAttachment,
                        ..
                    })
                ))
                .count(),
            6,
            "every raster layer establishes its target's attachment-write usage"
        );
        gal.submit(SubmissionBatch {
            label: "gui-mesh-reuse-target-test".to_string(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "gui".to_string(),
                operations: ops,
            })],
        })
        .expect("GAL validates sampled-to-attachment target reuse");
        frontend.reset(&mut gal);
    }

    #[test]
    fn sprite_registry_ids_are_stable() {
        assert_eq!(91, SPRITES.len());
        assert_eq!("crosshair", sprite_def(1).unwrap().name);
        assert_eq!("boss-bar-overlay-progress", sprite_def(79).unwrap().name);
        assert_eq!("hunger-effect-full", sprite_def(85).unwrap().name);
        assert_eq!("air-full", sprite_def(86).unwrap().name);
        assert_eq!("air-popping", sprite_def(87).unwrap().name);
        assert_eq!("air-empty", sprite_def(88).unwrap().name);
        assert_eq!("mount-heart-container", sprite_def(89).unwrap().name);
        assert_eq!("mount-heart-full", sprite_def(90).unwrap().name);
        assert_eq!("mount-heart-half", sprite_def(91).unwrap().name);
        assert!(sprite_def(92).is_err());
    }

    #[test]
    fn compatible_batches_split_on_group_stratum_and_size() {
        let requests = vec![
            GuiSpriteRequest {
                stratum: 300,
                sprite_id: 2,
                selected_slot: -1,
                progress_fraction: 1.0,
                fill_direction: 0,
                color_argb: 0xffffffff,
                x: 0,
                y: 0,
                width: 1,
                height: 1,
                gui_width: 320,
                gui_height: 180,
                sequence: 1,
            },
            GuiSpriteRequest {
                stratum: 310,
                sprite_id: 3,
                selected_slot: -1,
                progress_fraction: 1.0,
                fill_direction: 0,
                color_argb: 0xffffffff,
                x: 0,
                y: 0,
                width: 1,
                height: 1,
                gui_width: 320,
                gui_height: 180,
                sequence: 2,
            },
        ];
        let mut batches = Vec::<GuiBatch>::new();
        for request in requests {
            let group = sprite_def(request.sprite_id).unwrap().group;
            append_gui_quad(
                &mut batches,
                request.stratum,
                group,
                PackedGuiQuad {
                    origin: [0.0, 0.0],
                    axis_u: [1.0, 0.0],
                    axis_v: [0.0, 1.0],
                    viewport: [320.0, 180.0],
                    clip: [0.0; 4],
                    clip_enabled: false,
                    pre_present_y_flip: false,
                    uv: [0.0, 0.0, 1.0, 1.0],
                    color: [1.0; 4],
                    texture_mode: 1.0,
                    z: 0.0,
                },
            );
        }
        assert_eq!(2, batches.len());
    }

    #[test]
    fn mixed_gui_record_families_preserve_scheduler_order_and_reject_duplicates() {
        let mut sprite = request(1);
        sprite.stratum = 700;
        sprite.sequence = 12;
        let affine = GuiAffineQuadRequest {
            stratum: 700,
            asset_id: 41,
            x0: 0.0,
            y0: 0.0,
            x1: 8.0,
            y1: 0.0,
            x3: 0.0,
            y3: 8.0,
            z: 0.0,
            u0: 0.0,
            v0: 0.0,
            u1: 1.0,
            v1: 1.0,
            color_argb: 0xffff_ffff,
            gui_width: 320,
            gui_height: 180,
            sequence: 11,
            clip_mode: 0,
            clip_left: 0,
            clip_top: 0,
            clip_width: 0,
            clip_height: 0,
        };
        let ordered = order_gui_requests(vec![sprite.clone()], vec![affine.clone()]).unwrap();
        assert!(matches!(ordered[0], GuiFrameRequest::Affine(_)));
        assert!(matches!(ordered[1], GuiFrameRequest::Sprite(_)));

        let error = order_gui_requests(
            vec![sprite],
            vec![GuiAffineQuadRequest {
                sequence: 12,
                ..affine
            }],
        )
        .unwrap_err();
        assert_eq!(StatusCode::InvalidArgument, error.code);
    }

    #[test]
    fn affine_gui_clip_is_frame_local_and_rejects_out_of_bounds_rectangles() {
        let mut request = GuiAffineQuadRequest {
            stratum: 700,
            asset_id: 41,
            x0: 0.0,
            y0: 0.0,
            x1: 8.0,
            y1: 0.0,
            x3: 0.0,
            y3: 8.0,
            z: 0.0,
            u0: 0.0,
            v0: 0.0,
            u1: 1.0,
            v1: 1.0,
            color_argb: 0xffff_ffff,
            gui_width: 320,
            gui_height: 180,
            sequence: 1,
            clip_mode: 1,
            clip_left: 12,
            clip_top: 18,
            clip_width: 64,
            clip_height: 20,
        };
        validate_affine_quad(&request).unwrap();
        request.clip_width = 309;
        let error = validate_affine_quad(&request).unwrap_err();
        assert_eq!(StatusCode::InvalidArgument, error.code);
    }

    #[test]
    fn asset_updates_are_generation_ordered_and_copied() {
        let mut gal = mock_gal();
        let mut frontend = GuiFrontend::default();
        let sprite = sprite_def(1).unwrap();
        let mut bytes = bundled_sprite_bytes(sprite.path).unwrap().to_vec();
        frontend
            .apply_asset_update(
                &mut gal,
                2,
                vec![GuiAssetPayload {
                    sprite_id: sprite.id,
                    png_bytes: bytes.clone(),
                }],
            )
            .unwrap();
        bytes.clear();
        assert_eq!(2, frontend.asset_generation);
        assert!(!frontend.asset_overrides.get(&sprite.id).unwrap().is_empty());
        let stale = frontend
            .apply_asset_update(&mut gal, 2, Vec::new())
            .unwrap_err();
        assert_eq!(StatusCode::InvalidArgument, stale.code);
        assert_eq!(2, frontend.asset_generation);
    }

    #[test]
    fn malformed_asset_update_rolls_back_to_last_valid_assets() {
        let mut gal = mock_gal();
        let mut frontend = GuiFrontend::default();
        let sprite = sprite_def(1).unwrap();
        let valid = bundled_sprite_bytes(sprite.path).unwrap().to_vec();
        frontend
            .apply_asset_update(
                &mut gal,
                2,
                vec![GuiAssetPayload {
                    sprite_id: sprite.id,
                    png_bytes: valid.clone(),
                }],
            )
            .unwrap();
        let before = frontend.asset_overrides.get(&sprite.id).unwrap().clone();
        let error = frontend
            .apply_asset_update(
                &mut gal,
                3,
                vec![GuiAssetPayload {
                    sprite_id: sprite.id,
                    png_bytes: vec![1, 2, 3, 4],
                }],
            )
            .unwrap_err();
        assert!(error.message.contains("failed to decode GUI sprite"));
        assert_eq!(2, frontend.asset_generation);
        assert_eq!(before, *frontend.asset_overrides.get(&sprite.id).unwrap());
    }

    #[test]
    fn missing_asset_payloads_fall_back_to_vanilla() {
        let mut gal = mock_gal();
        let mut frontend = GuiFrontend::default();
        frontend
            .apply_asset_update(&mut gal, 2, Vec::new())
            .unwrap();
        assert!(frontend.asset_overrides.is_empty());
        let atlas = frontend.atlas_for(TextureGroup::Alpha).unwrap();
        assert!(atlas.regions.contains_key(&2));
    }

    #[test]
    fn static_and_raw_gui_images_share_top_origin_uv_coordinates() {
        let mut gal = mock_gal();
        let mut frontend = GuiFrontend::default();
        frontend
            .apply_asset_update(&mut gal, 2, Vec::new())
            .unwrap();

        let mut sprite_request = request(58);
        let sprite = sprite_def(sprite_request.sprite_id).unwrap();
        sprite_request.width = sprite.width;
        sprite_request.height = sprite.height;
        let packed = frontend
            .pack_sprite(&sprite_request, sprite, false)
            .unwrap();
        let atlas = frontend.atlas_for(sprite.group).unwrap();
        let region = atlas.regions.get(&sprite.id).unwrap();
        assert_eq!(
            [
                region.x as f32 / atlas.width as f32,
                region.y as f32 / atlas.height as f32,
            ],
            packed.uv[..2]
        );

        let direct_bytes = packed_uniform_bytes(&GuiBatch {
            stratum: sprite_request.stratum,
            group: sprite.group,
            quads: vec![packed],
        })
        .unwrap();
        let direct_flip = f32::from_le_bytes(direct_bytes[44..48].try_into().unwrap());
        assert_eq!(
            0.0, direct_flip,
            "ordinary GUI targets must not pre-flip their stream"
        );

        let source_packed = frontend.pack_sprite(&sprite_request, sprite, true).unwrap();
        let source_bytes = packed_uniform_bytes(&GuiBatch {
            stratum: sprite_request.stratum,
            group: sprite.group,
            quads: vec![source_packed],
        })
        .unwrap();
        let source_flip = f32::from_le_bytes(source_bytes[44..48].try_into().unwrap());
        assert_eq!(
            1.0, source_flip,
            "the source overlay must precompensate its final present copy"
        );

        // Raw font images arrive in the same row-zero-is-top convention, so
        // their top-left semantic UV must address the first uploaded texel.
        let raw = GuiAffineQuadRequest {
            asset_id: 7,
            u0: 0.0,
            v0: 0.0,
            u1: 1.0,
            v1: 1.0,
            ..GuiAffineQuadRequest {
                stratum: 700,
                asset_id: 7,
                x0: 0.0,
                y0: 0.0,
                x1: 1.0,
                y1: 0.0,
                x3: 0.0,
                y3: 1.0,
                z: 0.0,
                u0: 0.0,
                v0: 0.0,
                u1: 1.0,
                v1: 1.0,
                color_argb: 0xffff_ffff,
                gui_width: 1,
                gui_height: 1,
                sequence: 1,
                clip_mode: 0,
                clip_left: 0,
                clip_top: 0,
                clip_width: 0,
                clip_height: 0,
            }
        };
        validate_affine_quad(&raw).unwrap();
    }

    #[test]
    fn asset_reload_rebuilds_once_then_reuses_cached_resources() {
        let mut gal = mock_gal();
        let mut frontend = GuiFrontend::default();
        let target = frame_target(&mut gal);
        frontend
            .apply_asset_update(&mut gal, 2, Vec::new())
            .unwrap();

        let first = frontend
            .submit_frame(&mut gal, 10, target, vec![request(2), request(3)])
            .unwrap();
        assert!(first.resource_creates > 0);
        assert!(first.cache_misses > 0);

        let second = frontend
            .submit_frame(&mut gal, 10, target, vec![request(2), request(3)])
            .unwrap();
        assert_eq!(0, second.resource_creates);
        assert!(second.cache_hits > 0);
        assert_eq!(0, second.cache_misses);
    }

    #[test]
    fn vulkan_whole_frame_gui_submission_clears_once_before_batches() {
        let mut gal = mock_gal();
        let mut frontend = GuiFrontend::default();
        let target = frame_target(&mut gal);

        let stats = frontend
            .submit_frame(&mut gal, 10, target, vec![request(2), request(3)])
            .unwrap();

        assert_eq!(2, stats.sprite_batch_count);
        assert_eq!(1, stats.command_lists);
        assert_eq!(20, stats.command_ops);
    }

    #[test]
    fn raw_image_assets_are_copied_validated_and_rendered_as_affine_quads() {
        let mut gal = mock_gal();
        let mut frontend = GuiFrontend::default();
        let target = frame_target(&mut gal);
        let mut pixels = vec![0, 64, 128, 255];
        frontend
            .apply_raw_image_update(
                &mut gal,
                3,
                vec![GuiRawImageAssetPayload {
                    asset_id: 41,
                    format: GuiRawImageFormat::Alpha8,
                    width: 2,
                    height: 2,
                    pixels: pixels.clone(),
                }],
            )
            .unwrap();
        pixels.fill(7);
        assert_eq!(vec![0, 64, 128, 255], frontend.raw_images[&41].pixels);

        let (_, stats) = frontend
            .append_frame_ops_with_affine_quads(
                &mut gal,
                9,
                target,
                Vec::new(),
                vec![GuiAffineQuadRequest {
                    stratum: 420,
                    asset_id: 41,
                    x0: 10.0,
                    y0: 20.0,
                    x1: 18.0,
                    y1: 21.0,
                    x3: 9.0,
                    y3: 29.0,
                    z: 0.03,
                    u0: 0.0,
                    v0: 0.0,
                    u1: 1.0,
                    v1: 1.0,
                    color_argb: 0xff336699,
                    gui_width: 320,
                    gui_height: 180,
                    sequence: 1,
                    clip_mode: 0,
                    clip_left: 0,
                    clip_top: 0,
                    clip_width: 0,
                    clip_height: 0,
                }],
            )
            .unwrap();
        assert_eq!(1, stats.affine_quad_count);
        assert_eq!(1, stats.sprite_batch_count);
        assert!(frontend.resources.contains_key(&ResourceKey::new(
            TextureGroup::Dynamic(41),
            ColorFormat::Rgba8Unorm,
            None,
        )));
    }

    #[test]
    fn malformed_raw_image_update_rolls_back_without_destroying_valid_generation() {
        let mut gal = mock_gal();
        let mut frontend = GuiFrontend::default();
        frontend
            .apply_raw_image_update(
                &mut gal,
                4,
                vec![GuiRawImageAssetPayload {
                    asset_id: 7,
                    format: GuiRawImageFormat::Rgba8,
                    width: 1,
                    height: 1,
                    pixels: vec![1, 2, 3, 4],
                }],
            )
            .unwrap();
        let error = frontend
            .apply_raw_image_update(
                &mut gal,
                5,
                vec![GuiRawImageAssetPayload {
                    asset_id: 8,
                    format: GuiRawImageFormat::Alpha8,
                    width: 2,
                    height: 2,
                    pixels: vec![1, 2, 3],
                }],
            )
            .unwrap_err();
        assert_eq!(StatusCode::InvalidArgument, error.code);
        assert_eq!(4, frontend.raw_image_generation);
        assert!(frontend.raw_images.contains_key(&7));
        assert!(!frontend.raw_images.contains_key(&8));
    }

    #[test]
    fn failed_asset_generation_preserves_last_valid_atlas_bytes() {
        let mut gal = mock_gal();
        let mut frontend = GuiFrontend::default();
        let sprite = sprite_def(2).unwrap();
        frontend
            .apply_asset_update(
                &mut gal,
                2,
                vec![GuiAssetPayload {
                    sprite_id: sprite.id,
                    png_bytes: bundled_sprite_bytes(sprite.path).unwrap().to_vec(),
                }],
            )
            .unwrap();
        let atlas_before = frontend
            .atlas_for(TextureGroup::Alpha)
            .unwrap()
            .bytes
            .clone();

        let error = frontend
            .apply_asset_update(
                &mut gal,
                3,
                vec![GuiAssetPayload {
                    sprite_id: sprite.id,
                    png_bytes: vec![0, 1, 2, 3],
                }],
            )
            .unwrap_err();

        assert!(error.message.contains("failed to decode GUI sprite"));
        assert_eq!(2, frontend.asset_generation);
        assert_eq!(
            atlas_before,
            frontend.atlas_for(TextureGroup::Alpha).unwrap().bytes
        );
    }
}
