use std::collections::BTreeMap;
use std::io::BufReader;

use super::commands::{
    AttachmentLoadOp, AttachmentStoreOp, CommandList, CommandOp, PassAttachment, ResourceBarrier,
    SubmissionBatch, TextureOrigin3d, TextureUsageState,
};
use super::error::{GalError, GalResult, StatusCode};
use super::gal::VulkanicGal;
use super::handles::Handle;
use super::resources::{
    AccessFlags, BlendMode, BufferDesc, BufferUsage, ColorFormat, Extent3d, GraphicsPipelineDesc,
    MemoryDomain, PipelineLayoutDesc, PipelineStageFlags, PrimitiveTopology, QueueClass,
    RenderPassDesc, ResourceBinding, ResourceBindingDesc, ResourceBindingKind, ResourceLayoutDesc,
    ResourceSetDesc, SamplerAddressMode, SamplerDesc, SamplerFilter, ShaderCodeFormat,
    ShaderModuleDesc, ShaderStage, TextureDesc, TextureDimension, TextureFormat,
    TextureSubresourceRange, TextureUsage, TextureViewDesc,
};
use super::{BufferImageCopyRegion, CommandListDesc, CullMode};

pub const GUI_MAX_PACKED_SPRITES: usize = 256;
const GUI_UNIFORM_BYTES: usize = 64;
const GUI_PACKED_UNIFORM_BYTES: u64 = (GUI_MAX_PACKED_SPRITES * GUI_UNIFORM_BYTES) as u64;

const VERTEX_SHADER_OPENGL: &[u8] = br#"#version 330 core
struct PackedGuiSprite {
    vec4 rect;
    vec4 viewport;
    vec4 uv_region;
    vec4 color;
};
layout(std140) uniform GuiSpriteBatch {
    PackedGuiSprite sprites[256];
};
out vec2 v_uv;
out vec2 v_sprite_corner;
out vec4 v_color;
flat out vec4 v_uv_region;
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
    PackedGuiSprite sprite = sprites[gl_InstanceID];
    vec2 pixel = sprite.rect.xy + corner[vertex] * sprite.rect.zw;
    vec2 ndc = vec2((pixel.x / sprite.viewport.x) * 2.0 - 1.0, 1.0 - (pixel.y / sprite.viewport.y) * 2.0);
    gl_Position = vec4(ndc, 0.0, 1.0);
    v_uv_region = sprite.uv_region;
    v_sprite_corner = corner[vertex];
    v_uv = vec2(
        sprite.uv_region.x + corner[vertex].x * sprite.uv_region.z,
        sprite.uv_region.y + (1.0 - corner[vertex].y) * sprite.uv_region.w
    );
    v_color = sprite.color;
}
"#;

const VERTEX_SHADER_VULKAN: &[u8] = br#"#version 450
struct PackedGuiSprite {
    vec4 rect;
    vec4 viewport;
    vec4 uv_region;
    vec4 color;
};
layout(set = 0, binding = 0, std140) uniform GuiSpriteBatch {
    PackedGuiSprite sprites[256];
};
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec2 v_sprite_corner;
layout(location = 2) out vec4 v_color;
layout(location = 3) flat out vec4 v_uv_region;
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
    PackedGuiSprite sprite = sprites[gl_InstanceIndex];
    vec2 pixel = sprite.rect.xy + corner[vertex] * sprite.rect.zw;
    vec2 ndc = vec2((pixel.x / sprite.viewport.x) * 2.0 - 1.0, 1.0 - (pixel.y / sprite.viewport.y) * 2.0);
    gl_Position = vec4(ndc, 0.0, 1.0);
    v_uv_region = sprite.uv_region;
    v_sprite_corner = corner[vertex];
    v_uv = vec2(
        sprite.uv_region.x + corner[vertex].x * sprite.uv_region.z,
        sprite.uv_region.y + (1.0 - corner[vertex].y) * sprite.uv_region.w
    );
    v_color = sprite.color;
}
"#;

const FRAGMENT_SHADER_OPENGL: &[u8] = br#"#version 330 core
uniform sampler2D Sampler0;
in vec2 v_uv;
in vec2 v_sprite_corner;
in vec4 v_color;
flat in vec4 v_uv_region;
out vec4 out_color;
void main() {
    ivec2 texture_size = textureSize(Sampler0, 0);
    ivec2 origin = ivec2(round(v_uv_region.xy * vec2(texture_size)));
    ivec2 extent = max(ivec2(round(v_uv_region.zw * vec2(texture_size))), ivec2(1));
    ivec2 local = clamp(ivec2(floor(v_sprite_corner * vec2(extent))), ivec2(0), extent - ivec2(1));
    ivec2 texel = ivec2(origin.x + local.x, origin.y + extent.y - 1 - local.y);
    texel = clamp(texel, ivec2(0), texture_size - ivec2(1));
    vec4 color = texelFetch(Sampler0, texel, 0) * v_color;
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
layout(location = 2) in vec4 v_color;
layout(location = 3) flat in vec4 v_uv_region;
layout(location = 0) out vec4 out_color;
void main() {
    ivec2 texture_size = textureSize(sampler2D(Tex0, Samp0), 0);
    ivec2 origin = ivec2(round(v_uv_region.xy * vec2(texture_size)));
    ivec2 extent = max(ivec2(round(v_uv_region.zw * vec2(texture_size))), ivec2(1));
    ivec2 local = clamp(ivec2(floor(v_sprite_corner * vec2(extent))), ivec2(0), extent - ivec2(1));
    ivec2 texel = ivec2(origin.x + local.x, origin.y + extent.y - 1 - local.y);
    texel = clamp(texel, ivec2(0), texture_size - ivec2(1));
    vec4 color = texelFetch(sampler2D(Tex0, Samp0), texel, 0) * v_color;
    if (color.a <= 0.0) {
        discard;
    }
    out_color = color;
}
"#;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum TextureGroup {
    Alpha,
    Invert,
}

impl TextureGroup {
    fn label(self) -> &'static str {
        match self {
            Self::Alpha => "gui-alpha",
            Self::Invert => "gui-invert",
        }
    }

    fn blend(self) -> BlendMode {
        match self {
            Self::Alpha => BlendMode::Alpha,
            Self::Invert => BlendMode::Invert,
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

#[derive(Default)]
pub struct GuiFrontend {
    generation: u64,
    asset_generation: u64,
    asset_overrides: BTreeMap<u32, Vec<u8>>,
    atlases: BTreeMap<TextureGroupKey, TextureAtlas>,
    resources: BTreeMap<ResourceKey, GuiResources>,
    cached_pass: Option<CachedPass>,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
enum TextureGroupKey {
    Alpha,
    Invert,
}

impl From<TextureGroup> for TextureGroupKey {
    fn from(value: TextureGroup) -> Self {
        match value {
            TextureGroup::Alpha => Self::Alpha,
            TextureGroup::Invert => Self::Invert,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
struct ResourceKey {
    group: TextureGroupKey,
    color_format: ColorFormat,
}

impl ResourceKey {
    fn new(group: TextureGroup, color_format: ColorFormat) -> Self {
        Self {
            group: TextureGroupKey::from(group),
            color_format,
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
}

#[derive(Clone, Debug, Default)]
pub struct GuiSubmitStats {
    pub submission_id: u64,
    pub sprite_count: u64,
    pub sprite_batch_count: u64,
    pub cache_hits: u64,
    pub cache_misses: u64,
    pub resource_creates: u64,
    pub command_lists: u64,
    pub command_ops: u64,
}

#[derive(Clone, Debug)]
pub struct GuiAssetPayload {
    pub sprite_id: u32,
    pub png_bytes: Vec<u8>,
}

struct PackedSprite {
    request: GuiSpriteRequest,
    def: &'static SpriteDef,
}

struct SpriteBatch {
    stratum: u32,
    group: TextureGroup,
    sprites: Vec<PackedSprite>,
}

impl GuiFrontend {
    pub fn reset(&mut self, gal: &mut VulkanicGal) {
        self.destroy_render_resources(gal);
        self.asset_overrides.clear();
        self.asset_generation = 0;
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
        let (ops, mut stats) = self.append_frame_ops(gal, generation, frame_target, requests)?;
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
        if generation != self.generation {
            self.destroy_render_resources(gal);
            self.generation = generation;
        }
        let color_format = gal.frame_target_color_format(frame_target)?;
        let frame_pass = self.frame_pass(gal, frame_target)?;
        let mut batches: Vec<SpriteBatch> = Vec::new();
        let mut stats = GuiSubmitStats {
            sprite_count: requests.len() as u64,
            ..GuiSubmitStats::default()
        };
        for request in requests {
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
            let key = ResourceKey::new(group, color_format);
            if self.resources.contains_key(&key) {
                stats.cache_hits += 1;
            } else {
                let resources = self.create_resources(gal, group, color_format)?;
                self.resources.insert(key, resources);
                stats.cache_misses += 1;
                stats.resource_creates += 12;
            }
            let stratum = request.stratum;
            let packed = PackedSprite { request, def };
            let append = batches.last_mut().filter(|batch| {
                batch.stratum == stratum
                    && batch.group == group
                    && batch.sprites.len() < GUI_MAX_PACKED_SPRITES
            });
            if let Some(batch) = append {
                batch.sprites.push(packed);
            } else {
                batches.push(SpriteBatch {
                    stratum,
                    group,
                    sprites: vec![packed],
                });
            }
        }
        stats.sprite_batch_count = batches.len() as u64;
        let mut ops = Vec::new();
        let whole_frame_vulkan = gal
            .capabilities()
            .name
            .to_ascii_lowercase()
            .contains("vulkan");
        if whole_frame_vulkan {
            ops.push(CommandOp::BeginPass {
                pass: frame_pass,
                target: frame_target,
                colors: vec![loaded_frame_color_attachment(frame_target)],
                depth_stencil: None,
            });
            ops.push(CommandOp::EndPass);
        } else if batches.is_empty() {
            ops.push(CommandOp::BeginPass {
                pass: frame_pass,
                target: frame_target,
                colors: vec![loaded_frame_color_attachment(frame_target)],
                depth_stencil: None,
            });
            ops.push(CommandOp::EndPass);
        }
        for batch in &batches {
            let resources = self
                .resources
                .get(&ResourceKey::new(batch.group, color_format))
                .ok_or_else(|| GalError::backend("GUI resources vanished before submit"))?;
            let uniforms = self.packed_uniform_bytes(batch)?;
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
                target: frame_target,
                colors: vec![loaded_frame_color_attachment(frame_target)],
                depth_stencil: None,
            });
            ops.push(CommandOp::BindGraphicsPipeline(resources.pipeline));
            ops.push(CommandOp::BindResourceSet {
                pipeline_layout: resources.pipeline_layout,
                set_index: 0,
                set: resources.resource_set,
            });
            ops.push(CommandOp::SetIndexBuffer {
                buffer: resources.index_buffer,
                offset: 0,
            });
            ops.push(CommandOp::DrawIndexed {
                indices: 6,
                instances: batch.sprites.len() as u32,
            });
            ops.push(CommandOp::EndPass);
        }
        stats.command_lists = 1;
        stats.command_ops = ops.len() as u64;
        Ok((ops, stats))
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
            color_formats: vec![gal.frame_target_color_format(frame_target)?],
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
    ) -> GalResult<GuiResources> {
        let label = format!("gui-textured-{}-gen{}", group.label(), self.generation);
        let atlas = self.atlas_for(group)?.clone();
        let vulkan_shader_syntax = gal
            .capabilities()
            .name
            .to_ascii_lowercase()
            .contains("vulkan");
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
                size: atlas.bytes.len() as u64,
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
                format: TextureFormat::Rgba8Unorm,
                extent: Extent3d {
                    width: atlas.width,
                    height: atlas.height,
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
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::None,
                blend: group.blend(),
                depth_compare: None,
                depth_write: false,
                color_formats: vec![color_format],
                depth_format: None,
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
            self.upload_resources(gal, group, &resources)?;
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
        group: TextureGroup,
        resources: &GuiResources,
    ) -> GalResult<()> {
        let atlas = self.atlas_for(group)?;
        gal.submit(SubmissionBatch {
            label: format!("gui-textured-{}.upload", group.label()),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: format!("gui-textured-{}.upload.commands", group.label()),
                operations: vec![
                    CommandOp::HostWriteBuffer {
                        buffer: resources.upload_buffer,
                        offset: 0,
                        data: atlas.bytes.clone(),
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
                        bytes_per_row: atlas.width * 4,
                        rows_per_image: atlas.height,
                        texture: resources.texture,
                        texture_mip: 0,
                        texture_layer: 0,
                        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                        extent: Extent3d {
                            width: atlas.width,
                            height: atlas.height,
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
        let key = TextureGroupKey::from(group);
        if !self.atlases.contains_key(&key) {
            let atlas = build_atlas(group, &self.asset_overrides)?;
            self.atlases.insert(key, atlas);
        }
        Ok(self.atlases.get(&key).expect("atlas was just inserted"))
    }

    fn packed_uniform_bytes(&self, batch: &SpriteBatch) -> GalResult<Vec<u8>> {
        if batch.sprites.is_empty() || batch.sprites.len() > GUI_MAX_PACKED_SPRITES {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                format!(
                    "packed GUI sprite count must be in 1..{}: {}",
                    GUI_MAX_PACKED_SPRITES,
                    batch.sprites.len()
                ),
            ));
        }
        let atlas = self
            .atlases
            .get(&TextureGroupKey::from(batch.group))
            .ok_or_else(|| GalError::backend("GUI atlas missing for packed uniforms"))?;
        let mut out = Vec::with_capacity(batch.sprites.len() * GUI_UNIFORM_BYTES);
        for sprite in &batch.sprites {
            let region = atlas.regions.get(&sprite.def.id).ok_or_else(|| {
                GalError::backend(format!("sprite '{}' missing from atlas", sprite.def.name))
            })?;
            let source_width = sprite.request.width.min(sprite.def.width);
            let source_height = sprite.request.height.min(sprite.def.height);
            push_f32(&mut out, sprite.request.x as f32);
            push_f32(&mut out, sprite.request.y as f32);
            push_f32(&mut out, sprite.request.width as f32);
            push_f32(&mut out, sprite.request.height as f32);
            push_f32(&mut out, sprite.request.gui_width as f32);
            push_f32(&mut out, sprite.request.gui_height as f32);
            push_f32(&mut out, sprite.request.progress_fraction);
            push_f32(&mut out, sprite.request.fill_direction as f32);
            push_f32(&mut out, region.x as f32 / atlas.width as f32);
            push_f32(
                &mut out,
                (atlas.height - (region.y + source_height)) as f32 / atlas.height as f32,
            );
            push_f32(&mut out, source_width as f32 / atlas.width as f32);
            push_f32(&mut out, source_height as f32 / atlas.height as f32);
            push_f32(
                &mut out,
                ((sprite.request.color_argb >> 16) & 0xff) as f32 / 255.0,
            );
            push_f32(
                &mut out,
                ((sprite.request.color_argb >> 8) & 0xff) as f32 / 255.0,
            );
            push_f32(&mut out, (sprite.request.color_argb & 0xff) as f32 / 255.0);
            push_f32(
                &mut out,
                ((sprite.request.color_argb >> 24) & 0xff) as f32 / 255.0,
            );
        }
        Ok(out)
    }
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
        access: AccessFlags::TRANSFER,
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
        stages: PipelineStageFlags(PipelineStageFlags::DRAW.0 | PipelineStageFlags::TRANSFER.0),
        access: AccessFlags::TRANSFER,
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::backends::{mock::MockBackend, vulkan_capabilities};
    use crate::render::vulkanic::frame::{FrameSurfaceDesc, PresentMode};
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
        }
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
            },
        ];
        let batches = requests
            .into_iter()
            .map(|request| PackedSprite {
                def: sprite_def(request.sprite_id).unwrap(),
                request,
            })
            .fold(Vec::<SpriteBatch>::new(), |mut batches, packed| {
                let group = packed.def.group;
                if let Some(batch) = batches.last_mut().filter(|batch| {
                    batch.stratum == packed.request.stratum
                        && batch.group == group
                        && batch.sprites.len() < GUI_MAX_PACKED_SPRITES
                }) {
                    batch.sprites.push(packed);
                } else {
                    batches.push(SpriteBatch {
                        stratum: packed.request.stratum,
                        group,
                        sprites: vec![packed],
                    });
                }
                batches
            });
        assert_eq!(2, batches.len());
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
