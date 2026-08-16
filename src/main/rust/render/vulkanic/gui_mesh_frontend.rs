//! Private semantic model for Rust-owned 3D GUI meshes.
//!
//! This module deliberately stops before GAL allocation and native lowering.
//! It gives the GUI frontend one validated, backend-neutral representation for
//! copied vanilla item meshes. Java/PIP renderer objects and backend state are
//! not part of this boundary.

use std::collections::{BTreeMap, BTreeSet};

use super::commands::{
    AttachmentLoadOp, AttachmentStoreOp, ClearColor, CommandOp, PassAttachment, ResourceBarrier,
    TextureUsageState,
};
use super::error::{GalError, GalResult, StatusCode};
use super::gal::VulkanicGal;
use super::handles::Handle;
use super::resources::{
    AccessFlags, BackendApi, BlendMode, BufferDesc, BufferUsage, ColorFormat, CompareOp, Extent3d,
    GraphicsPipelineDesc, IndexType, MemoryDomain, PipelineLayoutDesc, PipelineStageFlags,
    PrimitiveTopology, QueueClass, RenderPassDesc, RenderTargetDesc, ResourceBinding,
    ResourceBindingDesc, ResourceBindingKind, ResourceLayoutDesc, ResourceSetDesc,
    ShaderCodeFormat, ShaderModuleDesc, ShaderStage, TextureDesc, TextureDimension, TextureFormat,
    TextureUsage, TextureViewDesc,
};
use super::CullMode;

pub const GUI_MESH_MAX_BATCHES: usize = 1_024;
pub const GUI_MESH_MAX_VERTICES: usize = 65_536;
pub const GUI_MESH_MAX_INDICES: usize = 196_608;
pub const GUI_MESH_GPU_VERTEX_BYTES: usize = 3 * 4 * 4;
const GUI_MESH_FRAME_UNIFORM_BYTES: usize = 48;
const GUI_MESH_COMPOSITE_UNIFORM_BYTES: usize = 64;
/// Conservative dynamic-UBO alignment valid for both backend lowerings.
pub const GUI_MESH_COMPOSITE_UNIFORM_STRIDE: u64 = 256;
const GUI_MESH_MAX_COMPOSITE_UNIFORM_BYTES: u64 =
    GUI_MESH_MAX_BATCHES as u64 * GUI_MESH_COMPOSITE_UNIFORM_STRIDE;
const GUI_MESH_MAX_VERTEX_BYTES: u64 = (GUI_MESH_MAX_VERTICES * GUI_MESH_GPU_VERTEX_BYTES) as u64;
const GUI_MESH_MAX_INDEX_BYTES: u64 = (GUI_MESH_MAX_INDICES * std::mem::size_of::<u32>()) as u64;

const GUI_MESH_VERTEX_SHADER_OPENGL: &[u8] = br#"#version 430 core
layout(std430) readonly buffer GuiMeshVertices { vec4 vertex_words[]; };
layout(std140) uniform GuiMeshFrame { vec4 raster_extent; vec4 light0; vec4 light1; };
out vec2 v_uv;
out vec4 v_color;
out vec3 v_normal;
void main() {
    int base = gl_VertexID * 3;
    vec4 position_u = vertex_words[base];
    vec4 uv_color_rg = vertex_words[base + 1];
    vec4 color_ba_normal = vertex_words[base + 2];
    float top_left_y = 1.0 - (position_u.y / raster_extent.y) * 2.0;
    // Standard3dItemRenderer's owned orthographic projection is
    // setOrtho(0, width, height, 0, -1000, 1000, false): model-space Z is
    // negated into OpenGL clip depth. Keep that convention in the OpenGL
    // lowering so the nearest item face wins its private depth test.
    gl_Position = vec4((position_u.x / raster_extent.x) * 2.0 - 1.0, top_left_y, -position_u.z / 1000.0, 1.0);
    v_uv = vec2(position_u.w, uv_color_rg.x);
    v_color = vec4(uv_color_rg.y, uv_color_rg.z, uv_color_rg.w, color_ba_normal.x);
    v_normal = color_ba_normal.yzw;
}
"#;

const GUI_MESH_VERTEX_SHADER_VULKAN: &[u8] = br#"#version 450
layout(set = 0, binding = 0, std430) readonly buffer GuiMeshVertices { vec4 vertex_words[]; };
layout(set = 0, binding = 1, std140) uniform GuiMeshFrame { vec4 raster_extent; vec4 light0; vec4 light1; };
layout(location = 0) out vec2 v_uv;
layout(location = 1) out vec4 v_color;
layout(location = 2) out vec3 v_normal;
void main() {
    int base = gl_VertexIndex * 3;
    vec4 position_u = vertex_words[base];
    vec4 uv_color_rg = vertex_words[base + 1];
    vec4 color_ba_normal = vertex_words[base + 2];
    float top_left_y = 1.0 - (position_u.y / raster_extent.y) * 2.0;
    // The copied vanilla PIP projection above is authored in OpenGL's
    // [-1, 1] clip-depth convention. Vulkan consumes [0, 1], so convert it
    // after preserving the same negative model-space Z scale.
    float vanilla_pip_clip_depth = -position_u.z / 1000.0;
    gl_Position = vec4((position_u.x / raster_extent.x) * 2.0 - 1.0, top_left_y, vanilla_pip_clip_depth * 0.5 + 0.5, 1.0);
    v_uv = vec2(position_u.w, uv_color_rg.x);
    v_color = vec4(uv_color_rg.y, uv_color_rg.z, uv_color_rg.w, color_ba_normal.x);
    v_normal = color_ba_normal.yzw;
}
"#;

const GUI_MESH_FRAGMENT_SHADER_OPENGL: &[u8] = br#"#version 430 core
uniform sampler2D Sampler0;
layout(std140) uniform GuiMeshFrame { vec4 raster_extent; vec4 light0; vec4 light1; };
in vec2 v_uv;
in vec4 v_color;
in vec3 v_normal;
out vec4 out_color;
void main() {
    vec4 color = texture(Sampler0, v_uv) * v_color;
    if (color.a <= raster_extent.z) discard;
    if (raster_extent.w > 0.5) {
        vec2 light = max(vec2(0.0), vec2(dot(light0.xyz, normalize(v_normal)), dot(light1.xyz, normalize(v_normal))));
        color.rgb *= min(1.0, (light.x + light.y) * 0.6 + 0.4);
    }
    out_color = color;
}
"#;

const GUI_MESH_FRAGMENT_SHADER_VULKAN: &[u8] = br#"#version 450
layout(set = 0, binding = 2) uniform texture2D GuiMeshTexture;
layout(set = 0, binding = 3) uniform sampler GuiMeshSampler;
layout(set = 0, binding = 1, std140) uniform GuiMeshFrame { vec4 raster_extent; vec4 light0; vec4 light1; };
layout(location = 0) in vec2 v_uv;
layout(location = 1) in vec4 v_color;
layout(location = 2) in vec3 v_normal;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 color = texture(sampler2D(GuiMeshTexture, GuiMeshSampler), v_uv) * v_color;
    if (color.a <= raster_extent.z) discard;
    if (raster_extent.w > 0.5) {
        vec2 light = max(vec2(0.0), vec2(dot(light0.xyz, normalize(v_normal)), dot(light1.xyz, normalize(v_normal))));
        color.rgb *= min(1.0, (light.x + light.y) * 0.6 + 0.4);
    }
    out_color = color;
}
"#;

const GUI_MESH_COMPOSITE_VERTEX_SHADER_OPENGL: &[u8] = br#"#version 430 core
layout(std140) uniform GuiMeshComposite {
    vec4 pose_linear;
    vec4 pose_translation_viewport;
    vec4 bounds;
    vec4 uv_region;
};
out vec2 v_uv;
const vec2 corner[6] = vec2[6](
    vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0),
    vec2(1.0, 1.0), vec2(0.0, 1.0), vec2(0.0, 0.0)
);
void main() {
    vec2 local = mix(bounds.xy, bounds.zw, corner[gl_VertexID]);
    vec2 pixel = vec2(
        pose_linear.x * local.x + pose_linear.z * local.y + pose_translation_viewport.x,
        pose_linear.y * local.x + pose_linear.w * local.y + pose_translation_viewport.y
    );
    gl_Position = vec4(
        (pixel.x / pose_translation_viewport.z) * 2.0 - 1.0,
        1.0 - (pixel.y / pose_translation_viewport.w) * 2.0,
        0.0,
        1.0
    );
    // Standard3dItemRenderer blits its private PIP target with V increasing
    // from the top edge. This owned target already has that orientation after
    // the raster pass, so applying the generic PIP V inversion here turns the
    // completed item model upside down.
    v_uv = uv_region.xy + corner[gl_VertexID] * uv_region.zw;
}
"#;

const GUI_MESH_COMPOSITE_VERTEX_SHADER_VULKAN: &[u8] = br#"#version 450
layout(set = 0, binding = 0, std140) uniform GuiMeshComposite {
    vec4 pose_linear;
    vec4 pose_translation_viewport;
    vec4 bounds;
    vec4 uv_region;
};
layout(location = 0) out vec2 v_uv;
const vec2 corner[6] = vec2[6](
    vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0),
    vec2(1.0, 1.0), vec2(0.0, 1.0), vec2(0.0, 0.0)
);
void main() {
    vec2 local = mix(bounds.xy, bounds.zw, corner[gl_VertexIndex]);
    vec2 pixel = vec2(
        pose_linear.x * local.x + pose_linear.z * local.y + pose_translation_viewport.x,
        pose_linear.y * local.x + pose_linear.w * local.y + pose_translation_viewport.y
    );
    gl_Position = vec4(
        (pixel.x / pose_translation_viewport.z) * 2.0 - 1.0,
        1.0 - (pixel.y / pose_translation_viewport.w) * 2.0,
        0.0,
        1.0
    );
    // Keep the semantic Standard3dItemRenderer PIP orientation identical on
    // Vulkan and OpenGL. Backend coordinate conversion ends at rasterization;
    // the sampled owned image is not a generic GUI blit source.
    v_uv = uv_region.xy + corner[gl_VertexIndex] * uv_region.zw;
}
"#;

const GUI_MESH_COMPOSITE_FRAGMENT_SHADER_OPENGL: &[u8] = br#"#version 430 core
uniform sampler2D Sampler0;
in vec2 v_uv;
out vec4 out_color;
void main() {
    vec4 color = texture(Sampler0, v_uv);
    if (color.a <= 0.0) discard;
    out_color = color;
}
"#;

const GUI_MESH_COMPOSITE_FRAGMENT_SHADER_VULKAN: &[u8] = br#"#version 450
layout(set = 0, binding = 1) uniform texture2D GuiMeshColor;
layout(set = 0, binding = 2) uniform sampler GuiMeshColorSampler;
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    vec4 color = texture(sampler2D(GuiMeshColor, GuiMeshColorSampler), v_uv);
    if (color.a <= 0.0) discard;
    out_color = color;
}
"#;

#[cfg(test)]
pub(crate) fn vulkan_shader_sources_for_backend_test() -> (&'static str, &'static str) {
    (
        std::str::from_utf8(GUI_MESH_VERTEX_SHADER_VULKAN)
            .expect("GUI mesh Vulkan vertex source is UTF-8"),
        std::str::from_utf8(GUI_MESH_FRAGMENT_SHADER_VULKAN)
            .expect("GUI mesh Vulkan fragment source is UTF-8"),
    )
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum GuiMeshMaterialMode {
    Opaque,
    Cutout,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum GuiMeshLightingMode {
    Flat,
    Block,
}

/// One copied model vertex. `normal_packed` retains the producer's semantic
/// normal encoding; only the eventual Rust GUI mesh shader decodes it.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct GuiMeshVertex {
    pub position: [f32; 3],
    pub atlas_uv: [f32; 2],
    pub local_uv: [f32; 2],
    pub color_argb: u32,
    pub normal_packed: u32,
}

/// A material-homogeneous indexed mesh for one GUI item layer. `asset_id`
/// refers to a Rust-owned raw image asset, never a Minecraft atlas object.
#[derive(Clone, Debug, PartialEq)]
pub struct GuiMeshBatchRequest {
    pub stratum: u32,
    /// Ordering within one item PIP raster. Every layer of a GUI item shares
    /// its scheduler sequence and composes only after the final layer.
    pub layer_index: u32,
    pub sequence: u64,
    pub asset_id: u64,
    pub material_mode: GuiMeshMaterialMode,
    pub lighting_mode: GuiMeshLightingMode,
    pub alpha_cutoff: f32,
    /// Vanilla-resolved item-layer transform copied before FFI.
    pub model_transform: [f32; 16],
    /// GUI affine pose expressed as m00, m01, m10, m11, m20, m21.
    pub gui_pose: [f32; 6],
    /// Logical GUI placement bounds: left, top, right, bottom.
    pub bounds: [i32; 4],
    pub gui_extent: [u32; 2],
    /// Rust-owned offscreen raster extent, including vanilla's guard pixels.
    /// This is deliberately distinct from the final GUI viewport.
    pub render_extent: [u32; 2],
    /// Copied PIP guard band. Composition excludes it from the visible GUI
    /// rectangle while rasterization retains it for filtered edge safety.
    pub guard_pixels: u32,
    pub vertices: Vec<GuiMeshVertex>,
    pub indices: Vec<u32>,
}

/// Backend-neutral, owned vertex data produced after the Java-owned source
/// snapshot is validated. The future GUI mesh pass may choose its private
/// buffer layout, but it must consume these copied semantics rather than a
/// Java model or PIP object.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct GuiMeshPreparedVertex {
    pub position: [f32; 3],
    pub local_uv: [f32; 2],
    pub color: [f32; 4],
    pub normal: [f32; 3],
}

#[derive(Clone, Debug, PartialEq)]
pub struct GuiMeshPreparedDraw {
    pub stratum: u32,
    pub layer_index: u32,
    pub sequence: u64,
    pub asset_id: u64,
    pub material_mode: GuiMeshMaterialMode,
    /// The copied item transform can contain a reflection (vanilla's GUI PIP
    /// pose does). Keep the resulting winding explicit so back-face culling
    /// remains correct instead of exposing the model interior.
    pub front_face: super::resources::FrontFace,
    pub lighting_mode: GuiMeshLightingMode,
    pub alpha_cutoff: f32,
    pub gui_pose: [f32; 6],
    pub bounds: [i32; 4],
    pub gui_extent: [u32; 2],
    pub render_extent: [u32; 2],
    pub guard_pixels: u32,
    pub vertices: Vec<GuiMeshPreparedVertex>,
    pub indices: Vec<u32>,
}

/// One non-overlapping range in a persistent GUI-mesh stream. A command list
/// may rasterize several quads that use the same texture; every draw must
/// retain its own bytes until GPU execution instead of overwriting offset zero
/// before the submission reaches the backend.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct GuiMeshStreamRange {
    pub vertex_offset: u64,
    pub index_offset: u64,
}

/// Private Rust-owned pass objects for one GUI mesh material. The texture view
/// and sampler come from the Rust GUI asset cache; Java never observes these
/// handles and no backend resource crosses FFI.
#[derive(Clone, Copy, Debug)]
pub struct GuiMeshPassResources {
    pub vertex_buffer: Handle,
    pub index_buffer: Handle,
    pub uniform_buffer: Handle,
    pub vertex_shader: Handle,
    pub fragment_shader: Handle,
    pub resource_layout: Handle,
    pub resource_set: Handle,
    pub pipeline_layout: Handle,
    pub pipeline: Handle,
}

impl GuiMeshPassResources {
    pub fn create(
        gal: &mut VulkanicGal,
        label: &str,
        color_format: ColorFormat,
        texture_view: Handle,
        sampler: Handle,
        material_mode: GuiMeshMaterialMode,
        front_face: super::resources::FrontFace,
    ) -> GalResult<Self> {
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let vertex_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.vertices"),
                size: GUI_MESH_MAX_VERTEX_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Storage,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(vertex_buffer);
            let index_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.indices"),
                size: GUI_MESH_MAX_INDEX_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Index,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(index_buffer);
            let uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.frame"),
                size: GUI_MESH_FRAME_UNIFORM_BYTES as u64,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(uniform_buffer);
            let (vertex_code, fragment_code) = match gal.capabilities().api {
                BackendApi::OpenGl => (
                    GUI_MESH_VERTEX_SHADER_OPENGL,
                    GUI_MESH_FRAGMENT_SHADER_OPENGL,
                ),
                BackendApi::Vulkan | BackendApi::Mock => (
                    GUI_MESH_VERTEX_SHADER_VULKAN,
                    GUI_MESH_FRAGMENT_SHADER_VULKAN,
                ),
            };
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: vertex_code.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: fragment_code.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(fragment_shader);
            let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.layout"),
                bindings: vec![
                    resource_binding_desc(0, ResourceBindingKind::StorageBuffer),
                    resource_binding_desc(1, ResourceBindingKind::UniformBuffer),
                    resource_binding_desc(2, ResourceBindingKind::SampledTexture),
                    resource_binding_desc(3, ResourceBindingKind::Sampler),
                ],
            })?;
            created.push(resource_layout);
            let resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.set"),
                layout: resource_layout,
                bindings: vec![
                    read_binding(0, vertex_buffer, ResourceBindingKind::StorageBuffer),
                    read_binding(1, uniform_buffer, ResourceBindingKind::UniformBuffer),
                    read_binding(2, texture_view, ResourceBindingKind::SampledTexture),
                    read_binding(3, sampler, ResourceBindingKind::Sampler),
                ],
            })?;
            created.push(resource_set);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts: vec![resource_layout],
            })?;
            created.push(pipeline_layout);
            let (cull_mode, blend) = gui_mesh_raster_state(material_mode);
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode,
                front_face,
                blend,
                depth_compare: Some(CompareOp::LessOrEqual),
                depth_write: true,
                depth_bias: None,
                color_formats: vec![color_format],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(pipeline);
            Ok(Self {
                vertex_buffer,
                index_buffer,
                uniform_buffer,
                vertex_shader,
                fragment_shader,
                resource_layout,
                resource_set,
                pipeline_layout,
                pipeline,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    pub fn append_draw(
        &self,
        target: GuiMeshOffscreenTarget,
        draw: &GuiMeshPreparedDraw,
        stream: GuiMeshStreamRange,
        clear: bool,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if draw.render_extent != [target.extent.width, target.extent.height] {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh draw offscreen extent does not match its Rust-owned target",
            ));
        }
        // The raster target is sampled by the preceding item's composite pass.
        // It is cached by extent, so return it to attachment-write usage before
        // beginning another item raster. The first use is also valid: this is a
        // semantic ownership transition for the newly-created target.
        operations.push(CommandOp::Barrier(ResourceBarrier {
            resource: target.color,
            subresources: None,
            before: TextureUsageState::ShaderRead,
            after: TextureUsageState::ColorAttachment,
            src_queue: QueueClass::Graphics,
            dst_queue: QueueClass::Graphics,
        }));
        if stream.vertex_offset % GUI_MESH_GPU_VERTEX_BYTES as u64 != 0
            || stream.index_offset % std::mem::size_of::<u32>() as u64 != 0
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh stream ranges must align to their vertex and index elements",
            ));
        }
        let vertex_bytes = packed_vertices(draw);
        let vertex_base = u32::try_from(stream.vertex_offset / GUI_MESH_GPU_VERTEX_BYTES as u64)
            .map_err(|_| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    "GUI mesh vertex stream offset exceeds u32 indices",
                )
            })?;
        let index_bytes = packed_indices_with_base(&draw.indices, vertex_base)?;
        let vertex_end = stream
            .vertex_offset
            .checked_add(vertex_bytes.len() as u64)
            .ok_or_else(|| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    "GUI mesh vertex stream range overflows",
                )
            })?;
        let index_end = stream
            .index_offset
            .checked_add(index_bytes.len() as u64)
            .ok_or_else(|| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    "GUI mesh index stream range overflows",
                )
            })?;
        if vertex_end > GUI_MESH_MAX_VERTEX_BYTES || index_end > GUI_MESH_MAX_INDEX_BYTES {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh draws exceed their persistent stream capacity",
            ));
        }
        for buffer in [self.vertex_buffer, self.index_buffer, self.uniform_buffer] {
            operations.push(CommandOp::Barrier(buffer_barrier(
                buffer,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferDst,
            )));
        }
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.vertex_buffer,
            offset: stream.vertex_offset,
            data: vertex_bytes,
        });
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.index_buffer,
            offset: stream.index_offset,
            data: index_bytes,
        });
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.uniform_buffer,
            offset: 0,
            data: frame_uniform_bytes(draw.render_extent, draw.alpha_cutoff, draw.lighting_mode),
        });
        operations.push(CommandOp::Barrier(buffer_barrier(
            self.vertex_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        operations.push(CommandOp::Barrier(buffer_barrier(
            self.index_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::IndexRead,
        )));
        operations.push(CommandOp::Barrier(buffer_barrier(
            self.uniform_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        operations.push(CommandOp::BeginPass {
            pass: target.pass,
            target: target.target,
            colors: vec![PassAttachment {
                view: target.color_view,
                load_op: if clear {
                    AttachmentLoadOp::Clear
                } else {
                    AttachmentLoadOp::Load
                },
                store_op: AttachmentStoreOp::Store,
                clear_color: clear.then_some(ClearColor {
                    r: 0.0,
                    g: 0.0,
                    b: 0.0,
                    a: 0.0,
                }),
            }],
            depth_stencil: Some(PassAttachment {
                view: target.depth_view,
                load_op: if clear {
                    AttachmentLoadOp::Clear
                } else {
                    AttachmentLoadOp::Load
                },
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        operations.push(CommandOp::BindGraphicsPipeline(self.pipeline));
        operations.push(CommandOp::BindResourceSet {
            pipeline_layout: self.pipeline_layout,
            set_index: 0,
            set: self.resource_set,
            dynamic_offsets: Vec::new(),
        });
        operations.push(CommandOp::SetIndexBuffer {
            buffer: self.index_buffer,
            offset: stream.index_offset,
            index_type: IndexType::U32,
        });
        operations.push(CommandOp::DrawIndexed {
            indices: draw.indices.len() as u32,
            instances: 1,
        });
        operations.push(CommandOp::EndPass);
        Ok(())
    }

    pub fn destroy(self, gal: &mut VulkanicGal) {
        for handle in [
            self.pipeline,
            self.pipeline_layout,
            self.resource_set,
            self.resource_layout,
            self.fragment_shader,
            self.vertex_shader,
            self.uniform_buffer,
            self.index_buffer,
            self.vertex_buffer,
        ] {
            let _ = gal.destroy(handle);
        }
    }
}

/// The standard 3D item PIP route accepts only vanilla's SOLID and CUTOUT
/// item layers. Their alpha threshold is explicit per draw, while their
/// fixed-function policy is shared: opaque depth writes and back-face culling.
/// Keeping this policy here prevents a GUI texture-group blend policy from
/// silently changing copied item-model geometry.
fn gui_mesh_raster_state(material_mode: GuiMeshMaterialMode) -> (CullMode, BlendMode) {
    match material_mode {
        GuiMeshMaterialMode::Opaque | GuiMeshMaterialMode::Cutout => {
            (CullMode::Back, BlendMode::Disabled)
        }
    }
}

fn transformed_front_face(
    matrix: [f32; 16],
    vertices: &[GuiMeshPreparedVertex],
    indices: &[u32],
) -> GalResult<super::resources::FrontFace> {
    let a = matrix[0];
    let b = matrix[4];
    let c = matrix[8];
    let d = matrix[1];
    let e = matrix[5];
    let f = matrix[9];
    let g = matrix[2];
    let h = matrix[6];
    let i = matrix[10];
    let determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
    if !determinant.is_finite() || determinant.abs() <= f32::EPSILON {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh model transform has no usable winding determinant",
        ));
    }
    // The mesh vertex stage maps GUI pixels to top-left-origin clip space,
    // which contributes one final Y reflection. This must be included with
    // the copied model basis when deciding the front face. Vanilla's
    // standard PIP pose itself is reflected, so its complete transform
    // remains counter-clockwise rather than being culled as an interior.
    let mut front_face = if determinant.is_sign_negative() {
        super::resources::FrontFace::CounterClockwise
    } else {
        super::resources::FrontFace::Clockwise
    };

    // A copied GUI item batch is one baked quad. Its source winding is a
    // per-face semantic, so the item transform alone cannot choose culling
    // correctly for every model face. Reconcile the first copied triangle
    // against its transformed vertex normal before choosing raster state.
    let [first, second, third] = first_triangle_indices(indices, vertices.len())?;
    let first = vertices[first];
    let second = vertices[second];
    let third = vertices[third];
    let geometric_normal = cross(
        subtract(second.position, first.position),
        subtract(third.position, first.position),
    );
    let average_normal = normalize(add(add(first.normal, second.normal), third.normal))?;
    let alignment = dot(geometric_normal, average_normal);
    if !alignment.is_finite() || alignment.abs() <= f32::EPSILON {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh triangle winding cannot be reconciled with its copied normal",
        ));
    }
    // Under a reflected transform the geometric cross product changes handedness
    // while the inverse-transposed normal intentionally does not. A negative
    // alignment is therefore expected precisely when the model determinant is
    // negative. Only the opposite relation denotes an independently reversed
    // source quad.
    if alignment.is_sign_negative() != determinant.is_sign_negative() {
        front_face = flip_front_face(front_face);
    }
    Ok(front_face)
}

fn first_triangle_indices(indices: &[u32], vertex_count: usize) -> GalResult<[usize; 3]> {
    let [first, second, third, ..] = indices else {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh requires a first triangle to establish front-face orientation",
        ));
    };
    let indices = [*first as usize, *second as usize, *third as usize];
    if indices.iter().any(|index| *index >= vertex_count) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh front-face triangle references a missing vertex",
        ));
    }
    Ok(indices)
}

fn flip_front_face(front_face: super::resources::FrontFace) -> super::resources::FrontFace {
    match front_face {
        super::resources::FrontFace::Clockwise => super::resources::FrontFace::CounterClockwise,
        super::resources::FrontFace::CounterClockwise => super::resources::FrontFace::Clockwise,
    }
}

fn subtract(left: [f32; 3], right: [f32; 3]) -> [f32; 3] {
    [left[0] - right[0], left[1] - right[1], left[2] - right[2]]
}

fn add(left: [f32; 3], right: [f32; 3]) -> [f32; 3] {
    [left[0] + right[0], left[1] + right[1], left[2] + right[2]]
}

fn cross(left: [f32; 3], right: [f32; 3]) -> [f32; 3] {
    [
        left[1] * right[2] - left[2] * right[1],
        left[2] * right[0] - left[0] * right[2],
        left[0] * right[1] - left[1] * right[0],
    ]
}

fn dot(left: [f32; 3], right: [f32; 3]) -> f32 {
    left[0] * right[0] + left[1] * right[1] + left[2] * right[2]
}

fn normalize(vector: [f32; 3]) -> GalResult<[f32; 3]> {
    let length_squared = dot(vector, vector);
    if !length_squared.is_finite() || length_squared <= f32::EPSILON {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh normal must have a finite non-zero length",
        ));
    }
    let inverse_length = length_squared.sqrt().recip();
    Ok([
        vector[0] * inverse_length,
        vector[1] * inverse_length,
        vector[2] * inverse_length,
    ])
}

/// Private Rust-owned resources that composite one PIP raster result into the
/// ordered GUI target. This is intentionally a normal textured GUI pass: the
/// Java PIP target, program, and blit state are neither observed nor reused.
#[derive(Clone, Copy, Debug)]
pub struct GuiMeshCompositeResources {
    pub uniform_buffer: Handle,
    pub sampler: Handle,
    pub vertex_shader: Handle,
    pub fragment_shader: Handle,
    pub resource_layout: Handle,
    pub resource_set: Handle,
    pub pipeline_layout: Handle,
    pub pipeline: Handle,
}

impl GuiMeshCompositeResources {
    pub fn create(
        gal: &mut VulkanicGal,
        label: &str,
        color_format: ColorFormat,
        depth_format: Option<TextureFormat>,
        source_color_view: Handle,
    ) -> GalResult<Self> {
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.uniform"),
                size: GUI_MESH_MAX_COMPOSITE_UNIFORM_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(uniform_buffer);
            let sampler = gal.create_sampler(super::resources::SamplerDesc {
                label: format!("{label}.sampler"),
                min_filter: super::resources::SamplerFilter::Nearest,
                mag_filter: super::resources::SamplerFilter::Nearest,
                mip_filter: super::resources::SamplerFilter::Nearest,
                address_u: super::resources::SamplerAddressMode::ClampToEdge,
                address_v: super::resources::SamplerAddressMode::ClampToEdge,
                address_w: super::resources::SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(sampler);
            let (vertex_code, fragment_code) = match gal.capabilities().api {
                BackendApi::OpenGl => (
                    GUI_MESH_COMPOSITE_VERTEX_SHADER_OPENGL,
                    GUI_MESH_COMPOSITE_FRAGMENT_SHADER_OPENGL,
                ),
                BackendApi::Vulkan | BackendApi::Mock => (
                    GUI_MESH_COMPOSITE_VERTEX_SHADER_VULKAN,
                    GUI_MESH_COMPOSITE_FRAGMENT_SHADER_VULKAN,
                ),
            };
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: vertex_code.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: fragment_code.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(fragment_shader);
            let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.layout"),
                bindings: vec![
                    dynamic_resource_binding_desc(0, ResourceBindingKind::UniformBuffer),
                    resource_binding_desc(1, ResourceBindingKind::SampledTexture),
                    resource_binding_desc(2, ResourceBindingKind::Sampler),
                ],
            })?;
            created.push(resource_layout);
            let resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.set"),
                layout: resource_layout,
                bindings: vec![
                    dynamic_read_binding(
                        0,
                        uniform_buffer,
                        ResourceBindingKind::UniformBuffer,
                        GUI_MESH_COMPOSITE_UNIFORM_BYTES as u64,
                    ),
                    read_binding(1, source_color_view, ResourceBindingKind::SampledTexture),
                    read_binding(2, sampler, ResourceBindingKind::Sampler),
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
                front_face: super::resources::FrontFace::CounterClockwise,
                blend: BlendMode::Alpha,
                depth_compare: None,
                depth_write: false,
                depth_bias: None,
                color_formats: vec![color_format],
                depth_format,
            })?;
            created.push(pipeline);
            Ok(Self {
                uniform_buffer,
                sampler,
                vertex_shader,
                fragment_shader,
                resource_layout,
                resource_set,
                pipeline_layout,
                pipeline,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    pub fn append_composite(
        &self,
        source: GuiMeshOffscreenTarget,
        destination_pass: Handle,
        destination_target: Handle,
        destination_color_view: Handle,
        destination_depth_view: Option<Handle>,
        draw: &GuiMeshPreparedDraw,
        uniform_offset: u64,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if draw.render_extent != [source.extent.width, source.extent.height] {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh composite source extent does not match its prepared draw",
            ));
        }
        if uniform_offset % GUI_MESH_COMPOSITE_UNIFORM_STRIDE != 0
            || uniform_offset
                .checked_add(GUI_MESH_COMPOSITE_UNIFORM_BYTES as u64)
                .map_or(true, |end| end > GUI_MESH_MAX_COMPOSITE_UNIFORM_BYTES)
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh composite uniform stream range is invalid",
            ));
        }
        operations.push(CommandOp::Barrier(buffer_barrier(
            self.uniform_buffer,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferDst,
        )));
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.uniform_buffer,
            offset: uniform_offset,
            data: composite_uniform_bytes(draw),
        });
        operations.push(CommandOp::Barrier(buffer_barrier(
            self.uniform_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        operations.push(CommandOp::Barrier(ResourceBarrier {
            resource: source.color,
            subresources: None,
            before: TextureUsageState::ColorAttachment,
            after: TextureUsageState::ShaderRead,
            src_queue: QueueClass::Graphics,
            dst_queue: QueueClass::Graphics,
        }));
        operations.push(CommandOp::BeginPass {
            pass: destination_pass,
            target: destination_target,
            colors: vec![PassAttachment {
                view: destination_color_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            depth_stencil: destination_depth_view.map(|view| PassAttachment {
                view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        operations.push(CommandOp::BindGraphicsPipeline(self.pipeline));
        operations.push(CommandOp::BindResourceSet {
            pipeline_layout: self.pipeline_layout,
            set_index: 0,
            set: self.resource_set,
            dynamic_offsets: vec![uniform_offset],
        });
        operations.push(CommandOp::Draw {
            vertices: 6,
            instances: 1,
        });
        operations.push(CommandOp::EndPass);
        Ok(())
    }

    pub fn destroy(self, gal: &mut VulkanicGal) {
        for handle in [
            self.pipeline,
            self.pipeline_layout,
            self.resource_set,
            self.resource_layout,
            self.fragment_shader,
            self.vertex_shader,
            self.sampler,
            self.uniform_buffer,
        ] {
            let _ = gal.destroy(handle);
        }
    }
}

fn resource_binding_desc(binding: u32, kind: ResourceBindingKind) -> ResourceBindingDesc {
    ResourceBindingDesc {
        binding,
        kind,
        stages: PipelineStageFlags::DRAW,
        array_count: 1,
        optional: false,
        dynamic_offset_count: 0,
    }
}

fn dynamic_resource_binding_desc(binding: u32, kind: ResourceBindingKind) -> ResourceBindingDesc {
    ResourceBindingDesc {
        dynamic_offset_count: 1,
        ..resource_binding_desc(binding, kind)
    }
}

fn read_binding(binding: u32, resource: Handle, kind: ResourceBindingKind) -> ResourceBinding {
    ResourceBinding {
        binding,
        array_index: 0,
        resource,
        kind,
        access: AccessFlags::READ,
        dynamic_offsets: Vec::new(),
        buffer_range: None,
    }
}

fn dynamic_read_binding(
    binding: u32,
    resource: Handle,
    kind: ResourceBindingKind,
    buffer_range: u64,
) -> ResourceBinding {
    ResourceBinding {
        dynamic_offsets: vec![0],
        buffer_range: Some(buffer_range),
        ..read_binding(binding, resource, kind)
    }
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

fn packed_indices_with_base(indices: &[u32], vertex_base: u32) -> GalResult<Vec<u8>> {
    let mut bytes = Vec::with_capacity(indices.len() * std::mem::size_of::<u32>());
    for index in indices {
        let adjusted = index.checked_add(vertex_base).ok_or_else(|| {
            GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh index stream base overflows u32",
            )
        })?;
        bytes.extend_from_slice(&adjusted.to_le_bytes());
    }
    Ok(bytes)
}

fn frame_uniform_bytes(
    extent: [u32; 2],
    alpha_cutoff: f32,
    lighting_mode: GuiMeshLightingMode,
) -> Vec<u8> {
    let mut bytes = Vec::with_capacity(GUI_MESH_FRAME_UNIFORM_BYTES);
    let enabled = matches!(lighting_mode, GuiMeshLightingMode::Block) as u8 as f32;
    for value in [extent[0] as f32, extent[1] as f32, alpha_cutoff, enabled] {
        bytes.extend_from_slice(&value.to_le_bytes());
    }
    // Matches Lighting.Entry.ITEMS_3D_UPRIGHT. These are semantic light
    // directions, not a borrowed Java UBO or backend resource.
    for value in [
        -0.933_439_2_f32,
        0.262_694_72,
        -0.244_300_16,
        0.0,
        -0.103_571_37,
        0.976_606_8,
        0.188_446_42,
        0.0,
    ] {
        bytes.extend_from_slice(&value.to_le_bytes());
    }
    bytes
}

fn composite_uniform_bytes(draw: &GuiMeshPreparedDraw) -> Vec<u8> {
    let [m00, m01, m10, m11, m20, m21] = draw.gui_pose;
    let [left, top, right, bottom] = draw.bounds;
    let [width, height] = draw.render_extent;
    let guard = draw.guard_pixels as f32;
    let width = width as f32;
    let height = height as f32;
    let mut bytes = Vec::with_capacity(GUI_MESH_COMPOSITE_UNIFORM_BYTES);
    for value in [
        m00,
        m01,
        m10,
        m11,
        m20,
        m21,
        draw.gui_extent[0] as f32,
        draw.gui_extent[1] as f32,
        left as f32,
        top as f32,
        right as f32,
        bottom as f32,
        guard / width,
        guard / height,
        (width - guard * 2.0) / width,
        (height - guard * 2.0) / height,
    ] {
        bytes.extend_from_slice(&value.to_le_bytes());
    }
    bytes
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
struct OffscreenTargetKey {
    generation: u64,
    width: u32,
    height: u32,
}

/// Rust-owned color/depth target for GUI mesh rasterization. This is separate
/// from the final frame target so GUI-item depth cannot interact with terrain
/// depth; later GUI composition consumes only `color_view`.
#[derive(Clone, Copy, Debug)]
pub struct GuiMeshOffscreenTarget {
    pub color: Handle,
    pub color_view: Handle,
    pub depth: Handle,
    pub depth_view: Handle,
    pub target: Handle,
    pub pass: Handle,
    pub extent: Extent3d,
}

#[derive(Default)]
pub struct GuiMeshOffscreenTargetCache {
    targets: BTreeMap<OffscreenTargetKey, GuiMeshOffscreenTarget>,
}

impl GuiMeshOffscreenTargetCache {
    pub(crate) fn len(&self) -> usize {
        self.targets.len()
    }

    /// Returns an owned target for this GUI resource generation and logical
    /// raster extent. A new generation atomically retires old target objects
    /// before staging its replacements; no Java target or view is involved.
    pub fn stage(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        extent: Extent3d,
    ) -> GalResult<GuiMeshOffscreenTarget> {
        if generation == 0 || extent.width == 0 || extent.height == 0 || extent.depth != 1 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh offscreen target requires a non-zero generation and D2 extent",
            ));
        }
        let key = OffscreenTargetKey {
            generation,
            width: extent.width,
            height: extent.height,
        };
        if let Some(target) = self.targets.get(&key).copied() {
            return Ok(target);
        }
        self.destroy_other_generations(gal, generation);
        let label = format!(
            "minecraft.gui.mesh.gen{generation}.{}x{}",
            extent.width, extent.height
        );
        let mut created = Vec::new();
        let result = (|| -> GalResult<GuiMeshOffscreenTarget> {
            let color = gal.create_texture(TextureDesc {
                label: format!("{label}.color"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Rgba8Unorm,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::ColorAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                ],
            })?;
            created.push(color);
            let color_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.color-view"),
                texture: color,
                format: TextureFormat::Rgba8Unorm,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(color_view);
            let depth = gal.create_texture(TextureDesc {
                label: format!("{label}.depth"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Depth32Float,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::DepthStencilAttachment],
            })?;
            created.push(depth);
            let depth_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.depth-view"),
                texture: depth,
                format: TextureFormat::Depth32Float,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(depth_view);
            let target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.target"),
                color_views: vec![color_view],
                depth_stencil_view: Some(depth_view),
                extent,
            })?;
            created.push(target);
            let pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.pass"),
                target,
                color_formats: vec![TextureFormat::Rgba8Unorm],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(pass);
            Ok(GuiMeshOffscreenTarget {
                color,
                color_view,
                depth,
                depth_view,
                target,
                pass,
                extent,
            })
        })();
        match result {
            Ok(target) => {
                self.targets.insert(key, target);
                Ok(target)
            }
            Err(error) => {
                for handle in created.into_iter().rev() {
                    let _ = gal.destroy(handle);
                }
                Err(error)
            }
        }
    }

    pub fn clear(&mut self, gal: &mut VulkanicGal) {
        let targets = std::mem::take(&mut self.targets);
        for (_, target) in targets {
            destroy_target(gal, target);
        }
    }

    fn destroy_other_generations(&mut self, gal: &mut VulkanicGal, generation: u64) {
        let stale = self
            .targets
            .keys()
            .copied()
            .filter(|key| key.generation != generation)
            .collect::<Vec<_>>();
        for key in stale {
            if let Some(target) = self.targets.remove(&key) {
                destroy_target(gal, target);
            }
        }
    }
}

fn destroy_target(gal: &mut VulkanicGal, target: GuiMeshOffscreenTarget) {
    for handle in [
        target.pass,
        target.target,
        target.depth_view,
        target.depth,
        target.color_view,
        target.color,
    ] {
        let _ = gal.destroy(handle);
    }
}

pub fn validate_batches(batches: &[GuiMeshBatchRequest]) -> GalResult<()> {
    if batches.len() > GUI_MESH_MAX_BATCHES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "GUI mesh batch count {} exceeds maximum {}",
                batches.len(),
                GUI_MESH_MAX_BATCHES
            ),
        ));
    }
    let mut layer_groups = BTreeMap::<(u32, u64), BTreeSet<u32>>::new();
    for batch in batches {
        validate_batch(batch)?;
        if batch.sequence == 0 {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh batches require non-zero item frame sequences",
            ));
        }
        if !layer_groups
            .entry((batch.stratum, batch.sequence))
            .or_default()
            .insert(batch.layer_index)
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh item layers require unique layer indices",
            ));
        }
    }
    for layers in layer_groups.values() {
        if layers
            .iter()
            .copied()
            .enumerate()
            .any(|(expected, actual)| actual != expected as u32)
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh item layers must be contiguous from zero",
            ));
        }
    }
    Ok(())
}

pub fn validate_batch(batch: &GuiMeshBatchRequest) -> GalResult<()> {
    if batch.stratum == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh batch requires a non-zero GUI stratum",
        ));
    }
    if batch.asset_id == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh batch requires a non-zero semantic image asset id",
        ));
    }
    if batch.gui_extent[0] == 0 || batch.gui_extent[1] == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh batch requires a positive GUI extent",
        ));
    }
    if batch.render_extent[0] == 0 || batch.render_extent[1] == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh batch requires a positive offscreen raster extent",
        ));
    }
    if batch.bounds[0] >= batch.bounds[2] || batch.bounds[1] >= batch.bounds[3] {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh batch requires ordered non-empty logical bounds",
        ));
    }
    if !batch.alpha_cutoff.is_finite() || !(0.0..=1.0).contains(&batch.alpha_cutoff) {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh alpha cutoff must be finite and within [0, 1]",
        ));
    }
    if batch.material_mode == GuiMeshMaterialMode::Opaque && batch.alpha_cutoff != 0.0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "opaque GUI mesh batches must use a zero alpha cutoff",
        ));
    }
    if batch.vertices.len() < 3 || batch.vertices.len() > GUI_MESH_MAX_VERTICES {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "GUI mesh vertex count {} is outside 3..={}",
                batch.vertices.len(),
                GUI_MESH_MAX_VERTICES
            ),
        ));
    }
    if batch.indices.len() < 3
        || batch.indices.len() > GUI_MESH_MAX_INDICES
        || batch.indices.len() % 3 != 0
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "GUI mesh index count {} must be a bounded triangle list",
                batch.indices.len()
            ),
        ));
    }
    if !batch.model_transform.iter().all(|value| value.is_finite())
        || !batch.gui_pose.iter().all(|value| value.is_finite())
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh transforms must be finite",
        ));
    }
    if batch.guard_pixels.saturating_mul(2) >= batch.render_extent[0]
        || batch.guard_pixels.saturating_mul(2) >= batch.render_extent[1]
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh guard band must leave a non-empty offscreen raster area",
        ));
    }
    for vertex in &batch.vertices {
        if !vertex.position.iter().all(|value| value.is_finite())
            || !vertex.atlas_uv.iter().all(|value| value.is_finite())
            || !vertex.local_uv.iter().all(|value| value.is_finite())
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh vertices must contain finite positions and atlas UVs",
            ));
        }
    }
    if batch
        .indices
        .iter()
        .any(|index| *index as usize >= batch.vertices.len())
    {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh indices must reference a copied vertex in their batch",
        ));
    }
    Ok(())
}

/// Consumes the caller-independent request family into a compact render-plan
/// family. Transforming at this boundary means later GUI mesh resource and
/// command construction only sees Rust-owned data. `atlas_uv` remains in the
/// source request for parity diagnostics; local UVs are the semantic contract
/// for the Rust-owned image resource used by the eventual GUI mesh pass.
pub fn prepare_draws(batches: &[GuiMeshBatchRequest]) -> GalResult<Vec<GuiMeshPreparedDraw>> {
    validate_batches(batches)?;
    batches.iter().map(prepare_draw).collect()
}

fn prepare_draw(batch: &GuiMeshBatchRequest) -> GalResult<GuiMeshPreparedDraw> {
    let vertices = batch
        .vertices
        .iter()
        .map(|vertex| {
            let position = transform_point(batch.model_transform, vertex.position)?;
            let normal = transform_normal(
                batch.model_transform,
                unpack_normal_i8(vertex.normal_packed),
            )?;
            Ok(GuiMeshPreparedVertex {
                position,
                local_uv: vertex.local_uv,
                color: argb_to_rgba(vertex.color_argb),
                normal,
            })
        })
        .collect::<GalResult<Vec<_>>>()?;
    Ok(GuiMeshPreparedDraw {
        stratum: batch.stratum,
        layer_index: batch.layer_index,
        sequence: batch.sequence,
        asset_id: batch.asset_id,
        material_mode: batch.material_mode,
        front_face: transformed_front_face(batch.model_transform, &vertices, &batch.indices)?,
        lighting_mode: batch.lighting_mode,
        alpha_cutoff: batch.alpha_cutoff,
        gui_pose: batch.gui_pose,
        bounds: batch.bounds,
        gui_extent: batch.gui_extent,
        render_extent: batch.render_extent,
        guard_pixels: batch.guard_pixels,
        vertices,
        indices: batch.indices.clone(),
    })
}

/// Packs exactly three vec4 values per vertex: position/local-U, local-V and
/// RGBA, then normal. This is a private streaming layout, deliberately not
/// part of the FFI ABI or a Java renderer contract.
pub fn packed_vertices(draw: &GuiMeshPreparedDraw) -> Vec<u8> {
    let mut bytes = Vec::with_capacity(draw.vertices.len() * GUI_MESH_GPU_VERTEX_BYTES);
    for vertex in &draw.vertices {
        push_f32(&mut bytes, vertex.position[0]);
        push_f32(&mut bytes, vertex.position[1]);
        push_f32(&mut bytes, vertex.position[2]);
        push_f32(&mut bytes, vertex.local_uv[0]);
        push_f32(&mut bytes, vertex.local_uv[1]);
        push_f32(&mut bytes, vertex.color[0]);
        push_f32(&mut bytes, vertex.color[1]);
        push_f32(&mut bytes, vertex.color[2]);
        push_f32(&mut bytes, vertex.color[3]);
        push_f32(&mut bytes, vertex.normal[0]);
        push_f32(&mut bytes, vertex.normal[1]);
        push_f32(&mut bytes, vertex.normal[2]);
    }
    bytes
}

fn transform_point(matrix: [f32; 16], position: [f32; 3]) -> GalResult<[f32; 3]> {
    let result = [
        matrix[0] * position[0] + matrix[4] * position[1] + matrix[8] * position[2] + matrix[12],
        matrix[1] * position[0] + matrix[5] * position[1] + matrix[9] * position[2] + matrix[13],
        matrix[2] * position[0] + matrix[6] * position[1] + matrix[10] * position[2] + matrix[14],
    ];
    if result.iter().all(|value| value.is_finite()) {
        Ok(result)
    } else {
        Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh model transform produced a non-finite position",
        ))
    }
}

fn transform_normal(matrix: [f32; 16], normal: [f32; 3]) -> GalResult<[f32; 3]> {
    let a = matrix[0];
    let b = matrix[4];
    let c = matrix[8];
    let d = matrix[1];
    let e = matrix[5];
    let f = matrix[9];
    let g = matrix[2];
    let h = matrix[6];
    let i = matrix[10];
    let determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
    if !determinant.is_finite() || determinant.abs() <= f32::EPSILON {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh model transform has no usable normal matrix",
        ));
    }
    let inverse_determinant = determinant.recip();
    // Inverse-transpose of the copied model-space basis. This keeps baked
    // normals correct for vanilla item transforms with non-uniform scaling.
    let transformed = [
        ((e * i - f * h) * normal[0] + (f * g - d * i) * normal[1] + (d * h - e * g) * normal[2])
            * inverse_determinant,
        ((c * h - b * i) * normal[0] + (a * i - c * g) * normal[1] + (b * g - a * h) * normal[2])
            * inverse_determinant,
        ((b * f - c * e) * normal[0] + (c * d - a * f) * normal[1] + (a * e - b * d) * normal[2])
            * inverse_determinant,
    ];
    let length_squared = transformed.iter().map(|value| value * value).sum::<f32>();
    if !length_squared.is_finite() || length_squared <= f32::EPSILON {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "GUI mesh model transform produced a degenerate normal",
        ));
    }
    let inverse_length = length_squared.sqrt().recip();
    Ok([
        transformed[0] * inverse_length,
        transformed[1] * inverse_length,
        transformed[2] * inverse_length,
    ])
}

fn unpack_normal_i8(packed: u32) -> [f32; 3] {
    let component = |shift| {
        let value = ((packed >> shift) & 0xffu32) as u8 as i8;
        (value as f32 / 127.0).clamp(-1.0, 1.0)
    };
    [component(0), component(8), component(16)]
}

fn argb_to_rgba(color: u32) -> [f32; 4] {
    [
        ((color >> 16) & 0xff) as f32 / 255.0,
        ((color >> 8) & 0xff) as f32 / 255.0,
        (color & 0xff) as f32 / 255.0,
        ((color >> 24) & 0xff) as f32 / 255.0,
    ]
}

fn push_f32(bytes: &mut Vec<u8>, value: f32) {
    bytes.extend_from_slice(&value.to_le_bytes());
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::backends::{mock::MockBackend, vulkan_capabilities};
    use crate::render::vulkanic::commands::{CommandList, CommandListDesc, SubmissionBatch};

    fn batch() -> GuiMeshBatchRequest {
        GuiMeshBatchRequest {
            stratum: 420,
            layer_index: 0,
            sequence: 1,
            asset_id: 7,
            material_mode: GuiMeshMaterialMode::Cutout,
            lighting_mode: GuiMeshLightingMode::Block,
            alpha_cutoff: 0.5,
            model_transform: [
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ],
            gui_pose: [1.0, 0.0, 0.0, 1.0, 12.0, 34.0],
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

    #[test]
    fn mesh_batches_validate_one_coarse_item_layer() {
        validate_batches(&[batch()]).expect("bounded copied GUI mesh is valid");
    }

    #[test]
    fn mesh_batches_reject_invalid_geometry_and_sequences() {
        let mut out_of_range = batch();
        out_of_range.indices[2] = 9;
        assert!(validate_batch(&out_of_range).is_err());

        let mut invalid_cutoff = batch();
        invalid_cutoff.alpha_cutoff = f32::NAN;
        assert!(validate_batch(&invalid_cutoff).is_err());

        let mut guard_consumes_target = batch();
        guard_consumes_target.guard_pixels = 17;
        assert!(validate_batch(&guard_consumes_target).is_err());

        let mut second_layer = batch();
        second_layer.layer_index = 1;
        validate_batches(&[batch(), second_layer])
            .expect("contiguous item layers share one sequence");

        let duplicate_layer = batch();
        assert!(validate_batches(&[batch(), duplicate_layer]).is_err());

        let mut flat = batch();
        flat.lighting_mode = GuiMeshLightingMode::Flat;
        validate_batch(&flat).expect("flat item lighting remains an explicit mesh semantic");
    }

    #[test]
    fn prepared_mesh_vertices_are_owned_transformed_and_normally_packed() {
        let mut source = batch();
        source.model_transform[0] = 2.0;
        source.model_transform[12] = 4.0;
        let prepared = prepare_draws(&[source]).expect("prepare copied mesh");
        let draw = &prepared[0];
        assert_eq!([4.0, 0.0, 0.0], draw.vertices[0].position);
        assert_eq!([0.0, 0.0, 1.0], draw.vertices[0].normal);
        assert_eq!([1.0, 1.0, 1.0, 1.0], draw.vertices[0].color);
        assert_eq!(GUI_MESH_GPU_VERTEX_BYTES * 3, packed_vertices(draw).len());

        let mut degenerate = batch();
        degenerate.model_transform[0] = 0.0;
        assert!(prepare_draws(&[degenerate]).is_err());
    }

    #[test]
    fn packed_rgba_lanes_match_both_backend_shader_decoders() {
        let mut source = batch();
        source.vertices[0].color_argb = 0x8040_80c0;
        let prepared = prepare_draws(&[source]).expect("prepare copied mesh");
        let packed = packed_vertices(&prepared[0]);
        let words = packed
            .chunks_exact(std::mem::size_of::<f32>())
            .map(|word| f32::from_le_bytes(word.try_into().unwrap()))
            .collect::<Vec<_>>();
        assert_eq!(
            words[5..9],
            [64.0 / 255.0, 128.0 / 255.0, 192.0 / 255.0, 128.0 / 255.0]
        );
        let decode = "vec4(uv_color_rg.y, uv_color_rg.z, uv_color_rg.w, color_ba_normal.x)";
        assert!(std::str::from_utf8(GUI_MESH_VERTEX_SHADER_OPENGL)
            .unwrap()
            .contains(decode));
        assert!(std::str::from_utf8(GUI_MESH_VERTEX_SHADER_VULKAN)
            .unwrap()
            .contains(decode));
    }

    #[test]
    fn mesh_frame_uniform_carries_the_semantic_cutout_threshold() {
        let bytes = frame_uniform_bytes([34, 18], 0.5, GuiMeshLightingMode::Block);
        let values = bytes
            .chunks_exact(std::mem::size_of::<f32>())
            .map(|word| f32::from_le_bytes(word.try_into().unwrap()))
            .collect::<Vec<_>>();
        assert_eq!(&values[..4], &[34.0, 18.0, 0.5, 1.0]);
        assert_eq!(values.len(), 12);
        assert_eq!(&values[4..7], &[-0.933_439_2, 0.262_694_72, -0.244_300_16]);
    }

    #[test]
    fn offscreen_targets_are_rust_owned_generation_and_extent_resources() {
        let gal_capabilities = vulkan_capabilities();
        let mut gal = VulkanicGal::new_with_backend(
            Box::new(MockBackend::with_capabilities(gal_capabilities)),
            false,
        );
        let mut cache = GuiMeshOffscreenTargetCache::default();
        let extent = Extent3d {
            width: 16,
            height: 16,
            depth: 1,
        };
        let first = cache.stage(&mut gal, 1, extent).expect("stage target");
        let reused = cache.stage(&mut gal, 1, extent).expect("reuse target");
        assert_eq!(first.target, reused.target);
        assert_eq!(first.color_view, reused.color_view);

        let replacement = cache
            .stage(
                &mut gal,
                2,
                Extent3d {
                    width: 32,
                    height: 16,
                    depth: 1,
                },
            )
            .expect("replace target generation");
        assert_ne!(first.target, replacement.target);
        cache.clear(&mut gal);
    }

    #[test]
    fn owned_mesh_pass_writes_only_to_its_matching_offscreen_target() {
        let mut gal = VulkanicGal::new_with_backend(
            Box::new(MockBackend::with_capabilities(vulkan_capabilities())),
            false,
        );
        let texture = gal
            .create_texture(TextureDesc {
                label: "gui-mesh-test-image".to_string(),
                dimension: TextureDimension::D2,
                format: TextureFormat::Rgba8Unorm,
                extent: Extent3d {
                    width: 1,
                    height: 1,
                    depth: 1,
                },
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::Sampled],
            })
            .expect("create Rust-owned test image");
        let view = gal
            .create_texture_view(TextureViewDesc {
                label: "gui-mesh-test-image-view".to_string(),
                texture,
                format: TextureFormat::Rgba8Unorm,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })
            .expect("create test image view");
        let sampler = gal
            .create_sampler(super::super::resources::SamplerDesc {
                label: "gui-mesh-test-sampler".to_string(),
                min_filter: super::super::resources::SamplerFilter::Nearest,
                mag_filter: super::super::resources::SamplerFilter::Nearest,
                mip_filter: super::super::resources::SamplerFilter::Nearest,
                address_u: super::super::resources::SamplerAddressMode::ClampToEdge,
                address_v: super::super::resources::SamplerAddressMode::ClampToEdge,
                address_w: super::super::resources::SamplerAddressMode::ClampToEdge,
                comparison: None,
            })
            .expect("create test sampler");
        let mut targets = GuiMeshOffscreenTargetCache::default();
        let target = targets
            .stage(
                &mut gal,
                1,
                Extent3d {
                    width: 34,
                    height: 34,
                    depth: 1,
                },
            )
            .expect("create mesh target");
        let destination = targets
            .stage(
                &mut gal,
                1,
                Extent3d {
                    width: 320,
                    height: 180,
                    depth: 1,
                },
            )
            .expect("create final GUI target");
        let destination_pass = gal
            .create_render_pass(RenderPassDesc {
                label: "gui-mesh-test-final-pass".to_string(),
                target: destination.target,
                color_formats: vec![TextureFormat::Rgba8Unorm],
                depth_format: Some(TextureFormat::Depth32Float),
            })
            .expect("create final GUI pass");
        let resources = GuiMeshPassResources::create(
            &mut gal,
            "gui-mesh-test",
            TextureFormat::Rgba8Unorm,
            view,
            sampler,
            GuiMeshMaterialMode::Cutout,
            crate::render::vulkanic::resources::FrontFace::CounterClockwise,
        )
        .expect("create owned mesh pass resources");
        let draw = prepare_draws(&[batch()])
            .expect("prepare test mesh")
            .pop()
            .unwrap();
        let composite = GuiMeshCompositeResources::create(
            &mut gal,
            "gui-mesh-test-composite",
            TextureFormat::Rgba8Unorm,
            Some(TextureFormat::Depth32Float),
            target.color_view,
        )
        .expect("create owned GUI mesh compositor");
        let mut operations = Vec::new();
        resources
            .append_draw(
                target,
                &draw,
                GuiMeshStreamRange::default(),
                true,
                &mut operations,
            )
            .expect("append one owned mesh draw");
        composite
            .append_composite(
                target,
                destination_pass,
                destination.target,
                destination.color_view,
                Some(destination.depth_view),
                &draw,
                0,
                &mut operations,
            )
            .expect("append one owned GUI mesh composite");
        assert!(operations.iter().any(|operation| matches!(
            operation,
            CommandOp::BeginPass { target: actual, .. } if *actual == target.target
        )));
        assert!(operations.iter().any(|operation| matches!(
            operation,
            CommandOp::DrawIndexed {
                indices: 3,
                instances: 1
            }
        )));
        assert!(operations.iter().any(|operation| matches!(
            operation,
            CommandOp::BeginPass { target: actual, .. } if *actual == destination.target
        )));
        assert!(operations.iter().any(|operation| matches!(
            operation,
            CommandOp::Draw {
                vertices: 6,
                instances: 1
            }
        )));
        gal.submit(SubmissionBatch {
            label: "gui-mesh-test-submit".to_string(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "gui-mesh-test-commands".to_string(),
                operations,
            })],
        })
        .expect("GAL validates one complete owned GUI mesh draw");
        composite.destroy(&mut gal);
        resources.destroy(&mut gal);
        let _ = gal.destroy(destination_pass);
        targets.clear(&mut gal);
        let _ = gal.destroy(sampler);
        let _ = gal.destroy(view);
        let _ = gal.destroy(texture);
    }

    #[test]
    fn standard_3d_item_raster_policy_matches_vanilla_solid_and_cutout() {
        for material_mode in [GuiMeshMaterialMode::Opaque, GuiMeshMaterialMode::Cutout] {
            assert_eq!(
                gui_mesh_raster_state(material_mode),
                (CullMode::Back, BlendMode::Disabled),
            );
        }
    }

    #[test]
    fn standard_3d_item_composite_preserves_the_pip_target_v_orientation() {
        for source in [
            std::str::from_utf8(GUI_MESH_COMPOSITE_VERTEX_SHADER_OPENGL)
                .expect("OpenGL composite source is UTF-8"),
            std::str::from_utf8(GUI_MESH_COMPOSITE_VERTEX_SHADER_VULKAN)
                .expect("Vulkan composite source is UTF-8"),
        ] {
            assert!(source.contains("v_uv = uv_region.xy + corner["));
            assert!(source.contains("] * uv_region.zw;"));
            assert!(
                !source.contains("1.0 - corner["),
                "standard-3D item composition must not apply the generic PIP V inversion"
            );
        }
    }

    #[test]
    fn standard_3d_item_raster_preserves_vanilla_pip_depth_order_on_each_backend() {
        let opengl = std::str::from_utf8(GUI_MESH_VERTEX_SHADER_OPENGL)
            .expect("OpenGL mesh source is UTF-8");
        assert!(opengl.contains("-position_u.z / 1000.0"));
        assert!(
            !opengl.contains(", top_left_y, position_u.z / 1000.0, 1.0);"),
            "OpenGL PIP depth must retain vanilla's negative orthographic Z scale"
        );

        let vulkan = std::str::from_utf8(GUI_MESH_VERTEX_SHADER_VULKAN)
            .expect("Vulkan mesh source is UTF-8");
        assert!(vulkan.contains("float vanilla_pip_clip_depth = -position_u.z / 1000.0;"));
        assert!(vulkan.contains("vanilla_pip_clip_depth * 0.5 + 0.5"));
    }

    #[test]
    fn reflected_gui_item_pose_accounts_for_the_clip_space_y_reflection() {
        let mut reflected = batch();
        reflected.model_transform[5] = -1.0;
        let draw = prepare_draws(&[reflected])
            .expect("reflected vanilla GUI item transform remains valid")
            .pop()
            .unwrap();
        assert_eq!(
            draw.front_face,
            super::super::resources::FrontFace::CounterClockwise
        );
        assert_eq!(gui_mesh_raster_state(draw.material_mode).0, CullMode::Back);
    }

    #[test]
    fn non_reflected_gui_item_pose_reverses_the_complete_clip_space_front_face() {
        let draw = prepare_draws(&[batch()])
            .expect("non-reflected GUI item transform remains valid")
            .pop()
            .unwrap();
        assert_eq!(
            draw.front_face,
            super::super::resources::FrontFace::Clockwise
        );
    }

    #[test]
    fn source_quad_winding_selects_the_per_face_front_face_without_disabling_culling() {
        let mut reverse_wound = batch();
        reverse_wound.vertices = vec![
            GuiMeshVertex {
                position: [0.0, 0.0, 0.0],
                atlas_uv: [0.0, 0.0],
                local_uv: [0.0, 0.0],
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
            GuiMeshVertex {
                position: [1.0, 0.0, 0.0],
                atlas_uv: [1.0, 0.0],
                local_uv: [1.0, 0.0],
                color_argb: 0xffff_ffff,
                normal_packed: 0x007f_0000,
            },
        ];
        let draw = prepare_draws(&[reverse_wound])
            .expect("opposite baked winding remains an explicit culled draw")
            .pop()
            .unwrap();
        assert_eq!(
            draw.front_face,
            super::super::resources::FrontFace::CounterClockwise
        );
        assert_eq!(gui_mesh_raster_state(draw.material_mode).0, CullMode::Back);
    }
}
