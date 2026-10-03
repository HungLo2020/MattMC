//! Resource descriptions and capabilities: buffer, texture, view, sampler,
//! shader, layout, set, pipeline, render-target and pass descriptors; formats,
//! usages and fixed-function state; and `BackendCapabilities`, the features,
//! limits and shader conventions renderers branch on.

use super::frame::FrameRenderTargetId;
use super::handles::Handle;

/// Where a buffer's memory lives; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum MemoryDomain {
    /// GPU memory; filled by uploads or GPU writes.
    DeviceLocal = 1,
    /// Host-visible memory the CPU writes for the GPU to read.
    Upload = 2,
    /// Host-visible memory the GPU writes for the CPU to read.
    Readback = 3,
}

/// How a buffer may be used; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BufferUsage {
    /// Vertex buffer.
    Vertex = 1,
    /// Index buffer.
    Index = 2,
    /// Uniform buffer binding.
    Uniform = 3,
    /// Storage buffer binding.
    Storage = 4,
    /// Copy source.
    TransferSrc = 5,
    /// Copy destination.
    TransferDst = 6,
    /// Indirect draw or dispatch records.
    Indirect = 7,
    /// Read back by `CommandOp::HostReadBuffer`.
    HostRead = 8,
    /// Written by `CommandOp::HostWriteBuffer`.
    HostWrite = 9,
}

/// Describes a buffer for `VulkanicGal::create_buffer`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct BufferDesc {
    /// A debug label.
    pub label: String,
    /// Size in bytes (non-zero, within `BackendLimits::max_buffer_size`).
    pub size: u64,
    /// Where its memory lives.
    pub memory: MemoryDomain,
    /// Every way it will be used (non-empty).
    pub usages: Vec<BufferUsage>,
}

/// A texture's dimensionality; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TextureDimension {
    /// One-dimensional; not supported, creation rejects it.
    D1 = 1,
    /// Two-dimensional, optionally an array.
    D2 = 2,
    /// Three-dimensional (requires `Texture3d`; no array layers).
    D3 = 3,
    /// Cube map; not supported, creation rejects it.
    Cube = 4,
}

/// Texel formats; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd, Hash)]
pub enum TextureFormat {
    /// Four 8-bit normalized channels.
    Rgba8Unorm = 1,
    /// Four 8-bit normalized channels, blue first.
    Bgra8Unorm = 2,
    /// Four 16-bit float channels.
    Rgba16Float = 3,
    /// 24-bit depth with 8-bit stencil.
    Depth24Stencil8 = 4,
    /// 32-bit float depth.
    Depth32Float = 5,
    /// Single-channel unsigned-integer resource data. This is not
    /// a color attachment format; it is valid for sampled/storage textures.
    R8Uint = 6,
    /// Packed unsigned floating-point RGB color data.
    R11fG11fB10f = 7,
    /// Single-channel floating-point color/resource data.
    R32Float = 8,
    /// Three-channel half-float color data.
    Rgb16Float = 9,
    /// Single-channel normalized color/resource data.
    R8Unorm = 10,
    /// Four-channel signed-normalized color data.
    Rgba8Snorm = 11,
    /// Single-channel half-float color/resource data.
    R16Float = 12,
}

impl TextureFormat {
    /// The tightly packed byte width used by buffer-to-texture copy validation.
    /// Depth/stencil formats deliberately remain unsupported for host copies until
    /// their aspect-specific copy contract is modeled.
    pub const fn copy_bytes_per_texel(self) -> Option<u32> {
        match self {
            Self::Rgba8Unorm
            | Self::Bgra8Unorm
            | Self::Depth32Float
            | Self::R11fG11fB10f
            | Self::R32Float
            | Self::Rgba8Snorm => Some(4),
            Self::Rgba16Float => Some(8),
            Self::Rgb16Float => Some(6),
            Self::R16Float => Some(2),
            Self::R8Uint | Self::R8Unorm => Some(1),
            Self::Depth24Stencil8 => None,
        }
    }
}

/// How a texture may be used; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TextureUsage {
    /// Sampled by shaders.
    Sampled = 1,
    /// Storage image binding.
    Storage = 2,
    /// Color attachment of a render target.
    ColorAttachment = 3,
    /// Depth-stencil attachment of a render target.
    DepthStencilAttachment = 4,
    /// Copy source.
    TransferSrc = 5,
    /// Copy destination.
    TransferDst = 6,
    /// Presented to a window.
    Present = 7,
    /// Copied to a buffer for host readback (implies copy source).
    HostRead = 8,
    /// Filled from a host upload buffer (implies copy destination).
    HostWrite = 9,
}

/// A size in texels.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct Extent3d {
    /// Width.
    pub width: u32,
    /// Height.
    pub height: u32,
    /// Depth (1 for 2D textures and surfaces).
    pub depth: u32,
}

/// Describes a texture for `VulkanicGal::create_texture`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TextureDesc {
    /// A debug label.
    pub label: String,
    /// 2D or 3D.
    pub dimension: TextureDimension,
    /// The texel format.
    pub format: TextureFormat,
    /// The size of mip 0.
    pub extent: Extent3d,
    /// Mip levels (at least 1, no more than the extent allows).
    pub mip_levels: u32,
    /// Array layers (1 for 3D textures).
    pub array_layers: u32,
    /// Every way it will be used (non-empty).
    pub usages: Vec<TextureUsage>,
}

/// Describes a texture view for `VulkanicGal::create_texture_view`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TextureViewDesc {
    /// A debug label.
    pub label: String,
    /// The texture viewed.
    pub texture: Handle,
    /// The view format; must equal the texture's.
    pub format: TextureFormat,
    /// First mip level.
    pub base_mip: u32,
    /// Mip levels covered.
    pub mip_count: u32,
    /// First array layer.
    pub base_layer: u32,
    /// Array layers covered.
    pub layer_count: u32,
}

/// A range of mips and array layers of a texture.
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub struct TextureSubresourceRange {
    /// First mip level.
    pub base_mip: u32,
    /// Mip levels covered.
    pub mip_count: u32,
    /// First array layer.
    pub base_layer: u32,
    /// Array layers covered.
    pub layer_count: u32,
}

/// Texel filtering; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SamplerFilter {
    /// Nearest texel.
    Nearest = 1,
    /// Linear interpolation.
    Linear = 2,
}

/// Addressing outside [0, 1]; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SamplerAddressMode {
    /// Clamp to the edge texel.
    ClampToEdge = 1,
    /// Wrap around.
    Repeat = 2,
    /// Wrap around, mirrored on every repeat.
    MirroredRepeat = 3,
}

/// Describes a sampler for `VulkanicGal::create_sampler`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SamplerDesc {
    /// A debug label.
    pub label: String,
    /// Minification filter.
    pub min_filter: SamplerFilter,
    /// Magnification filter.
    pub mag_filter: SamplerFilter,
    /// Filtering between mip levels.
    pub mip_filter: SamplerFilter,
    /// Addressing along u.
    pub address_u: SamplerAddressMode,
    /// Addressing along v.
    pub address_v: SamplerAddressMode,
    /// Addressing along w.
    pub address_w: SamplerAddressMode,
    /// Optional semantic depth comparison. `None` is ordinary sampling;
    /// `Some` may only be paired with a depth texture view.
    pub comparison: Option<CompareOp>,
}

/// One explicit sampled image and sampler pairing. It is backend-neutral:
/// frontends never receive descriptor, texture-unit, or native-handle state.
/// Backends may lower this as a combined descriptor or a private texture-unit
/// pairing.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CombinedTextureSamplerDesc {
    /// A debug label.
    pub label: String,
    /// The sampled texture view (needs sampled usage).
    pub texture_view: Handle,
    /// The sampler. Integer formats need nearest filtering; comparison
    /// samplers need a depth view.
    pub sampler: Handle,
}

/// A shader stage; values are part of the C ABI. Graphics pipelines use
/// vertex and fragment shaders only.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub enum ShaderStage {
    /// Vertex shader.
    Vertex = 1,
    /// Fragment shader.
    Fragment = 2,
    /// Compute shader.
    Compute = 3,
    /// Geometry shader (no pipeline accepts this stage yet).
    Geometry = 4,
    /// Tessellation control shader (no pipeline accepts this stage yet).
    TessControl = 5,
    /// Tessellation evaluation shader (no pipeline accepts this stage yet).
    TessEvaluation = 6,
}

/// Describes a shader module for `VulkanicGal::create_shader_module`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderModuleDesc {
    /// A debug label.
    pub label: String,
    /// The stage it runs in.
    pub stage: ShaderStage,
    /// The format of `code`.
    pub code_format: ShaderCodeFormat,
    /// The shader code.
    pub code: Vec<u8>,
    /// The entry point name.
    pub entry_point: String,
}

/// Shader code formats; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ShaderCodeFormat {
    /// SPIR-V binary.
    Spirv = 1,
    /// The backend-portable intermediate form.
    BackendPortableIr = 2,
    /// GLSL source in the backend's `GlslDialect`.
    Glsl = 3,
}

/// Shader stages a binding is visible to, as bits.
#[repr(transparent)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct PipelineStageFlags(pub u32);

impl PipelineStageFlags {
    /// No stage.
    pub const NONE: Self = Self(0);
    /// Vertex and fragment stages.
    pub const DRAW: Self = Self(1 << 0);
    /// Compute stage.
    pub const COMPUTE: Self = Self(1 << 1);
    /// Transfer operations.
    pub const TRANSFER: Self = Self(1 << 2);
    /// Presentation.
    pub const PRESENT: Self = Self(1 << 3);
}

/// How a bound resource is accessed, as bits; hazard analysis uses it.
#[repr(transparent)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct AccessFlags(pub u32);

impl AccessFlags {
    /// No access.
    pub const NONE: Self = Self(0);
    /// Read.
    pub const READ: Self = Self(1 << 0);
    /// Written by shaders.
    pub const WRITE: Self = Self(1 << 1);
    /// Written as a color attachment.
    pub const COLOR_ATTACHMENT: Self = Self(1 << 2);
    /// Written as a depth-stencil attachment.
    pub const DEPTH_STENCIL: Self = Self(1 << 3);
    /// Written by a transfer.
    pub const TRANSFER: Self = Self(1 << 4);

    pub(in crate::render::vulkanic) fn reads(self) -> bool {
        self.0 & Self::READ.0 != 0
    }

    /// Whether any write bit is set.
    pub fn writes(self) -> bool {
        self.0
            & (Self::WRITE.0 | Self::COLOR_ATTACHMENT.0 | Self::DEPTH_STENCIL.0 | Self::TRANSFER.0)
            != 0
    }
}

/// The kind of resource a binding holds; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ResourceBindingKind {
    /// A uniform buffer range.
    UniformBuffer = 1,
    /// A storage buffer range.
    StorageBuffer = 2,
    /// A sampled texture view.
    SampledTexture = 3,
    /// A storage texture view.
    StorageTexture = 4,
    /// A sampler.
    Sampler = 5,
    /// A combined texture sampler.
    CombinedTextureSampler = 6,
}

/// One binding of a resource layout.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ResourceBindingDesc {
    /// The binding number.
    pub binding: u32,
    /// What it holds.
    pub kind: ResourceBindingKind,
    /// The shader stages that see it.
    pub stages: PipelineStageFlags,
    /// Array elements (1 for a single resource).
    pub array_count: u32,
    /// Whether a set may leave it empty (requires `OptionalBindings`).
    pub optional: bool,
    /// Dynamic offsets it takes (buffer bindings; requires
    /// `DynamicBufferOffsets`).
    pub dynamic_offset_count: u32,
}

/// Describes a resource layout for `VulkanicGal::create_resource_layout`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ResourceLayoutDesc {
    /// A debug label.
    pub label: String,
    /// The bindings.
    pub bindings: Vec<ResourceBindingDesc>,
}

/// Describes a resource set for `VulkanicGal::create_resource_set`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ResourceSetDesc {
    /// A debug label.
    pub label: String,
    /// The resource layout it fills.
    pub layout: Handle,
    /// The bound resources.
    pub bindings: Vec<ResourceBinding>,
}

/// One resource bound in a resource set.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ResourceBinding {
    /// The layout binding it fills.
    pub binding: u32,
    /// The array element it fills.
    pub array_index: u32,
    /// The buffer, texture view, sampler or combined texture sampler.
    pub resource: Handle,
    /// What it is; must match the layout binding.
    pub kind: ResourceBindingKind,
    /// How shaders access it.
    pub access: AccessFlags,
    /// Default dynamic offsets, one per offset the layout binding declares;
    /// used when a `BindResourceSet` supplies none.
    pub dynamic_offsets: Vec<u64>,
    /// Bytes visible from each offset, for buffer bindings. `None` means up
    /// to the end of the buffer from the largest default offset.
    pub buffer_range: Option<u64>,
}

/// Describes a pipeline layout for `VulkanicGal::create_pipeline_layout`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PipelineLayoutDesc {
    /// A debug label.
    pub label: String,
    /// The resource layout of each set index, in order.
    pub resource_layouts: Vec<Handle>,
}

/// How vertices assemble into primitives; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum PrimitiveTopology {
    /// Points.
    Points = 1,
    /// Line list.
    Lines = 2,
    /// Triangle list.
    Triangles = 3,
    /// Ordered fan sharing vertex zero, preserving the producer's primitive
    /// assembly through clipping and interpolation.  This is distinct from
    /// an application-side rewrite to a triangle list.
    TriangleFan = 4,
}

/// Which faces are culled; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum CullMode {
    /// None.
    None = 1,
    /// Front faces.
    Front = 2,
    /// Back faces.
    Back = 3,
}

/// Semantic winding convention for front-face classification.
///
/// The backend owns how its viewport convention realizes this declaration.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum FrontFace {
    /// Counter-clockwise winding is front-facing.
    CounterClockwise = 1,
    /// Clockwise winding is front-facing.
    Clockwise = 2,
}

/// Vertex supplying flat-shaded outputs for each assembled primitive.
/// This is explicit pipeline state, never the backend's implicit default.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ProvokingVertex {
    /// The first vertex of each primitive.
    First = 1,
    /// The last vertex of each primitive.
    Last = 2,
}

/// Explicit raster depth bias in backend-neutral units.
///
/// `slope_factor` scales the maximum depth slope and `constant_factor` applies
/// a constant depth-unit offset. Backends own their native realization.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct DepthBias {
    /// Constant depth offset, in depth units.
    pub constant_factor: f32,
    /// Factor applied to the primitive's maximum depth slope.
    pub slope_factor: f32,
}

impl DepthBias {
    /// A depth bias from its two factors.
    pub const fn new(constant_factor: f32, slope_factor: f32) -> Self {
        Self {
            constant_factor,
            slope_factor,
        }
    }

    /// Whether both factors are finite (required by pipelines).
    pub const fn is_finite(self) -> bool {
        self.constant_factor.is_finite() && self.slope_factor.is_finite()
    }
}

impl Eq for DepthBias {}

/// Color blending; values are part of the C ABI. Equations below are
/// per color attachment.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BlendMode {
    /// No blending; the source replaces the destination.
    Disabled = 1,
    /// `rgb = src.rgb * src.a + dst.rgb * (1 - src.a)`,
    /// `a = src.a + dst.a * (1 - src.a)`.
    Alpha = 2,
    /// `out = src + dst`.
    Additive = 3,
    /// `rgb = src.rgb * (1 - dst.rgb) + dst.rgb * (1 - src.rgb)`, `a = src.a`.
    Invert = 4,
    /// `rgb = src.rgb * dst.rgb`, `a = src.a`.
    Multiply = 5,
    /// Additive overlay tint: `out.rgb = src.rgb * src.a + dst.rgb`, `out.a = src.a`.
    /// This is the backend-neutral semantic used by forcefield-style world overlays.
    Overlay = 6,
    /// `out.rgb = src.rgb * src.rgb + dst.rgb`, `out.a = dst.a` (glint).
    SrcColorAdditive = 7,
    /// `out = dst * (1 - src.rgb)` (vignette).
    InverseSrcColorModulate = 8,
    /// Premultiplied-alpha compositing: `out.rgb = src.rgb + dst.rgb * (1-src.a)`.
    Premultiplied = 9,
    /// Alpha blending on attachment zero while the remaining attachments are
    /// replacement writes (translucent terrain's auxiliary outputs). Backends
    /// with a single blend state treat it as ordinary alpha.
    AlphaFirstAttachmentOnly = 10,
    /// Source-alpha RGB composition with destination alpha preserved:
    /// RGB factors SrcAlpha/OneMinusSrcAlpha, alpha factors Zero/One.
    AlphaPreserveAlpha = 11,
    /// `out.rgb = 2 * src.rgb * dst.rgb` (block-breaking cracks).
    DoubleModulate = 12,
    /// Source-alpha RGB composition with source alpha replacing the
    /// destination alpha. This matches DH's vanilla water state, whose
    /// transparent blend setup preserves RGB alpha-over while leaving the
    /// alpha attachment equal to the current source.
    AlphaSource = 13,
    /// Keep a color attachment bound while writing only depth, as in vanilla's boat water mask.
    DepthMask = 14,
}

/// Depth, stencil and sampler comparisons; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum CompareOp {
    /// Always passes.
    Always = 1,
    /// Passes if the new value is less.
    Less = 2,
    /// Passes if the new value is less or equal.
    LessOrEqual = 3,
    /// Passes if equal.
    Equal = 4,
    /// Passes if the new value is greater.
    Greater = 5,
}

/// Explicit stencil operation used by a graphics pipeline's front/back face
/// state. The GAL keeps this deliberately small: optical masks need only
/// preserve or replace a bounded stencil value, while unsupported operations
/// remain unavailable instead of being reconstructed from Java state.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum StencilOp {
    /// Keep the stencil value.
    Keep = 1,
    /// Write the reference value.
    Replace = 2,
}

/// Stencil test and operations for one face.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct StencilFaceState {
    /// The stencil comparison.
    pub compare: CompareOp,
    /// The reference value.
    pub reference: u32,
    /// Bits compared.
    pub read_mask: u32,
    /// Bits written.
    pub write_mask: u32,
    /// On stencil failure.
    pub fail_op: StencilOp,
    /// On stencil pass, depth failure.
    pub depth_fail_op: StencilOp,
    /// On stencil and depth pass.
    pub pass_op: StencilOp,
}

impl StencilFaceState {
    /// Tests against `reference` and never writes.
    pub const fn keep(compare: CompareOp, reference: u32, read_mask: u32) -> Self {
        Self {
            compare,
            reference,
            read_mask,
            write_mask: 0,
            fail_op: StencilOp::Keep,
            depth_fail_op: StencilOp::Keep,
            pass_op: StencilOp::Keep,
        }
    }

    /// Always passes and writes `reference` where depth also passes.
    pub const fn replace(reference: u32, read_mask: u32, write_mask: u32) -> Self {
        Self {
            compare: CompareOp::Always,
            reference,
            read_mask,
            write_mask,
            fail_op: StencilOp::Keep,
            depth_fail_op: StencilOp::Keep,
            pass_op: StencilOp::Replace,
        }
    }
}

/// Stencil state of a graphics pipeline (requires a `Depth24Stencil8`
/// attachment).
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct StencilState {
    /// Front faces.
    pub front: StencilFaceState,
    /// Back faces.
    pub back: StencilFaceState,
}

/// Index size; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum IndexType {
    /// 16-bit indices.
    U16 = 1,
    /// 32-bit indices.
    U32 = 2,
}

/// Optional capabilities a backend may report; values are part of the C
/// ABI. Query with `BackendCapabilities::supports`.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BackendFeature {
    /// Graphics pipelines and passes.
    Graphics = 1,
    /// Compute pipelines and dispatch.
    Compute = 2,
    /// Bindings with more than one array element.
    DescriptorArrays = 3,
    /// Bindings a set may leave empty.
    OptionalBindings = 4,
    /// Dynamic offsets on buffer bindings.
    DynamicBufferOffsets = 5,
    /// Uniform buffer bindings.
    UniformBuffers = 6,
    /// Storage buffers.
    StorageBuffers = 7,
    /// Storage textures.
    StorageTextures = 8,
    /// Indirect draws.
    IndirectDraw = 9,
    /// Indirect dispatch.
    IndirectDispatch = 10,
    /// More than one color attachment per pass.
    MultipleColorAttachments = 11,
    /// Render targets with depth and no color.
    DepthOnlyPass = 12,
    /// Blend modes other than `Disabled`.
    BlendedPass = 13,
    /// Copies addressing individual mips and layers.
    TextureSubresourceCopies = 14,
    /// Textures with more than one mip level.
    TextureMipLevels = 15,
    /// Textures with more than one array layer.
    TextureArrayLayers = 16,
    /// Host reads and writes of buffers in command lists.
    HostBufferAccess = 17,
    /// A presentation surface (frames and swapchain).
    Presentation = 18,
    /// RenderDoc frame captures.
    RenderDocCapture = 19,
    /// Tracy timing zones.
    TracyZones = 20,
    /// Three-dimensional textures.
    Texture3d = 21,
    /// Texture copies that reverse row order.
    TextureRowReversal = 22,
    /// Device-local memory is distinct from host-visible memory: frequently
    /// read resources belong there, behind staged (and batched) uploads.
    DeviceLocalMemory = 23,
    /// Three-dimensional textures also accept packed formats (BGRA8 and
    /// packed depth/stencil), not only the common color formats.
    Texture3dPackedFormats = 24,
}

/// The features a backend supports, one flag per `BackendFeature`.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct BackendFeatureFlags {
    /// See `BackendFeature::Graphics`.
    pub graphics: bool,
    /// See `BackendFeature::Compute`.
    pub compute: bool,
    /// See `BackendFeature::DescriptorArrays`.
    pub descriptor_arrays: bool,
    /// See `BackendFeature::OptionalBindings`.
    pub optional_bindings: bool,
    /// See `BackendFeature::DynamicBufferOffsets`.
    pub dynamic_buffer_offsets: bool,
    /// See `BackendFeature::UniformBuffers`.
    pub uniform_buffers: bool,
    /// See `BackendFeature::StorageBuffers`.
    pub storage_buffers: bool,
    /// See `BackendFeature::StorageTextures`.
    pub storage_textures: bool,
    /// See `BackendFeature::IndirectDraw`.
    pub indirect_draw: bool,
    /// See `BackendFeature::IndirectDispatch`.
    pub indirect_dispatch: bool,
    /// See `BackendFeature::MultipleColorAttachments`.
    pub multiple_color_attachments: bool,
    /// See `BackendFeature::DepthOnlyPass`.
    pub depth_only_pass: bool,
    /// See `BackendFeature::BlendedPass`.
    pub blended_pass: bool,
    /// See `BackendFeature::TextureSubresourceCopies`.
    pub texture_subresource_copies: bool,
    /// See `BackendFeature::TextureMipLevels`.
    pub texture_mip_levels: bool,
    /// See `BackendFeature::TextureArrayLayers`.
    pub texture_array_layers: bool,
    /// See `BackendFeature::HostBufferAccess`.
    pub host_buffer_access: bool,
    /// See `BackendFeature::Presentation`.
    pub presentation: bool,
    /// See `BackendFeature::RenderDocCapture`.
    pub renderdoc_capture: bool,
    /// See `BackendFeature::TracyZones`.
    pub tracy_zones: bool,
    /// See `BackendFeature::Texture3d`.
    pub texture_3d: bool,
    /// See `BackendFeature::TextureRowReversal`.
    pub texture_row_reversal: bool,
    /// See `BackendFeature::DeviceLocalMemory`.
    pub device_local_memory: bool,
    /// See `BackendFeature::Texture3dPackedFormats`.
    pub texture_3d_packed_formats: bool,
}

impl BackendFeatureFlags {
    /// Whether one feature is supported.
    pub fn supports(self, feature: BackendFeature) -> bool {
        match feature {
            BackendFeature::Graphics => self.graphics,
            BackendFeature::Compute => self.compute,
            BackendFeature::DescriptorArrays => self.descriptor_arrays,
            BackendFeature::OptionalBindings => self.optional_bindings,
            BackendFeature::DynamicBufferOffsets => self.dynamic_buffer_offsets,
            BackendFeature::UniformBuffers => self.uniform_buffers,
            BackendFeature::StorageBuffers => self.storage_buffers,
            BackendFeature::StorageTextures => self.storage_textures,
            BackendFeature::IndirectDraw => self.indirect_draw,
            BackendFeature::IndirectDispatch => self.indirect_dispatch,
            BackendFeature::MultipleColorAttachments => self.multiple_color_attachments,
            BackendFeature::DepthOnlyPass => self.depth_only_pass,
            BackendFeature::BlendedPass => self.blended_pass,
            BackendFeature::TextureSubresourceCopies => self.texture_subresource_copies,
            BackendFeature::TextureMipLevels => self.texture_mip_levels,
            BackendFeature::TextureArrayLayers => self.texture_array_layers,
            BackendFeature::HostBufferAccess => self.host_buffer_access,
            BackendFeature::Presentation => self.presentation,
            BackendFeature::RenderDocCapture => self.renderdoc_capture,
            BackendFeature::TracyZones => self.tracy_zones,
            BackendFeature::Texture3d => self.texture_3d,
            BackendFeature::TextureRowReversal => self.texture_row_reversal,
            BackendFeature::DeviceLocalMemory => self.device_local_memory,
            BackendFeature::Texture3dPackedFormats => self.texture_3d_packed_formats,
        }
    }
}

/// Numeric limits a backend enforces; creation and submission reject
/// requests beyond them.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct BackendLimits {
    /// Largest buffer, in bytes.
    pub max_buffer_size: u64,
    /// Required alignment for a uniform-buffer dynamic offset in bytes.
    pub uniform_buffer_offset_alignment: u64,
    /// Largest 2D texture width or height.
    pub max_texture_extent_2d: u32,
    /// Largest 3D texture extent on any axis.
    pub max_texture_extent_3d: u32,
    /// Most mip levels per texture.
    pub max_texture_mip_levels: u32,
    /// Most array layers per texture.
    pub max_texture_array_layers: u32,
    /// Most bindings per resource layout.
    pub max_resource_layout_bindings: u32,
    /// Most array elements per binding.
    pub max_binding_array_count: u32,
    /// Most color attachments per pass or pipeline.
    pub max_color_attachments: u32,
    /// Most dynamic offsets per binding.
    pub max_dynamic_offsets_per_binding: u32,
    /// Most command lists per submission batch.
    pub max_command_lists_per_submission: u32,
    /// Most ops per command list.
    pub max_commands_per_list: u32,
    /// Most records per indirect draw.
    pub max_draw_count: u32,
    /// Most work groups per dispatch axis.
    pub max_dispatch_groups_per_axis: u32,
}

/// Number of GPU profiling scopes a backend can time or count per frame.
pub const GPU_PROFILE_SCOPE_COUNT: usize = 12;

/// Profiling classification a frontend assigns to a render pass or graphics
/// pipeline. Scope indices are opaque to the GAL and its backends: they time
/// and count work per index, and the frontend that assigned them names them.
#[derive(Clone, Copy, Debug, Default, Eq, Hash, PartialEq)]
pub struct GpuProfileTag {
    /// Scope (< `GPU_PROFILE_SCOPE_COUNT`) whose GPU time includes this work.
    pub timing_scope: Option<u8>,
    /// Scope (< `GPU_PROFILE_SCOPE_COUNT`) whose pipeline statistics include
    /// this pipeline's work.
    pub statistics_scope: Option<u8>,
    /// A timing span begun by this pipeline ends when a pipeline without a
    /// timing scope binds, instead of lasting until the pass ends.
    pub timing_ends_at_untimed_pipeline: bool,
}

/// The kinds of object a `GpuProfileClassifier` tags.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum GpuProfiledObject {
    /// A render pass.
    RenderPass,
    /// A graphics pipeline.
    GraphicsPipeline,
}

/// Frontend policy installed on the GAL: tags created passes/pipelines from
/// their labels and names statistics scopes for diagnostics.
#[derive(Clone, Copy)]
pub struct GpuProfileClassifier {
    /// Tags a newly created pass or pipeline from its kind and label.
    pub classify: fn(GpuProfiledObject, &str) -> GpuProfileTag,
    /// Names a statistics scope index, for diagnostics.
    pub statistics_scope_name: fn(u8) -> &'static str,
}

/// GLSL flavour a backend compiles. Frontends that ship several source
/// variants choose by dialect, never by which backend is running.
#[derive(Clone, Copy, Debug, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub enum GlslDialect {
    /// GLSL 4.50 with explicit `set`/`binding`/`location` decorations and
    /// SPIR-V built-ins (`gl_VertexIndex`).
    ExplicitBindings,
    /// Desktop core-profile GLSL with implicit locations and GL built-ins.
    CoreProfile,
}

/// Native coordinate conventions a backend imposes on shaders and readback.
/// Frontends adapt shared shader text through these facts (see
/// `render::shaderpack::programs::model::shader_stage_code`), never through
/// backend identity.
#[derive(Clone, Copy, Debug, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct ShaderConventions {
    /// The GLSL flavour the backend compiles.
    pub glsl_dialect: GlslDialect,
    /// Clip-space depth maps to [0, 1] rather than [-1, 1].
    pub zero_to_one_clip_depth: bool,
    /// Sampling a render target in a fullscreen pass needs V flipped to keep
    /// the pass graph's top-left image origin.
    pub flip_fullscreen_uv_y: bool,
    /// Framebuffer readback returns the bottom row first.
    pub readback_rows_bottom_up: bool,
}

/// Everything a renderer may ask about a backend: features, limits and
/// shader conventions. Renderers branch on these facts, never on `name`.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct BackendCapabilities {
    /// Coordinate and GLSL conventions shaders must follow.
    pub shader_conventions: ShaderConventions,
    /// A display name for logs and error messages only.
    pub name: &'static str,
    /// Supported optional features.
    pub features: BackendFeatureFlags,
    /// Enforced limits.
    pub limits: BackendLimits,
}

impl BackendCapabilities {
    /// Whether one feature is supported.
    pub fn supports(self, feature: BackendFeature) -> bool {
        self.features.supports(feature)
    }

    /// Reports whether this backend can create a three-dimensional texture for
    /// one semantic format/use pair. This keeps format support explicit for
    /// frontends without exposing a native image, texture target, or descriptor.
    pub fn supports_texture_3d_usage(self, format: TextureFormat, usage: TextureUsage) -> bool {
        if !self.supports(BackendFeature::Texture3d) {
            return false;
        }

        let format_supported = self.supports(BackendFeature::Texture3dPackedFormats)
            || !matches!(
                format,
                TextureFormat::Bgra8Unorm | TextureFormat::Depth24Stencil8
            );
        if !format_supported {
            return false;
        }

        match usage {
            TextureUsage::Sampled => true,
            TextureUsage::Storage => {
                self.supports(BackendFeature::StorageTextures) && !is_depth_format(format)
            }
            TextureUsage::TransferSrc | TextureUsage::TransferDst => {
                self.supports(BackendFeature::TextureSubresourceCopies)
                    && format.copy_bytes_per_texel().is_some()
            }
            TextureUsage::ColorAttachment
            | TextureUsage::DepthStencilAttachment
            | TextureUsage::Present
            | TextureUsage::HostRead
            | TextureUsage::HostWrite => false,
        }
    }

    #[cfg(test)]
    pub(in crate::render::vulkanic) fn fingerprint_json(self) -> String {
        format!(
            "{{\"name\":\"{}\",\"features\":{{\"graphics\":{},\"compute\":{},\"descriptor_arrays\":{},\"optional_bindings\":{},\"dynamic_buffer_offsets\":{},\"uniform_buffers\":{},\"storage_buffers\":{},\"storage_textures\":{},\"indirect_draw\":{},\"indirect_dispatch\":{},\"multiple_color_attachments\":{},\"depth_only_pass\":{},\"blended_pass\":{},\"texture_subresource_copies\":{},\"texture_mip_levels\":{},\"texture_array_layers\":{},\"host_buffer_access\":{},\"presentation\":{},\"renderdoc_capture\":{},\"tracy_zones\":{},\"texture_3d\":{}}},\"limits\":{{\"max_buffer_size\":{},\"uniform_buffer_offset_alignment\":{},\"max_texture_extent_2d\":{},\"max_texture_extent_3d\":{},\"max_texture_mip_levels\":{},\"max_texture_array_layers\":{},\"max_resource_layout_bindings\":{},\"max_binding_array_count\":{},\"max_color_attachments\":{},\"max_dynamic_offsets_per_binding\":{},\"max_command_lists_per_submission\":{},\"max_commands_per_list\":{},\"max_draw_count\":{},\"max_dispatch_groups_per_axis\":{}}}}}",
            self.name,
            self.features.graphics,
            self.features.compute,
            self.features.descriptor_arrays,
            self.features.optional_bindings,
            self.features.dynamic_buffer_offsets,
            self.features.uniform_buffers,
            self.features.storage_buffers,
            self.features.storage_textures,
            self.features.indirect_draw,
            self.features.indirect_dispatch,
            self.features.multiple_color_attachments,
            self.features.depth_only_pass,
            self.features.blended_pass,
            self.features.texture_subresource_copies,
            self.features.texture_mip_levels,
            self.features.texture_array_layers,
            self.features.host_buffer_access,
            self.features.presentation,
            self.features.renderdoc_capture,
            self.features.tracy_zones,
            self.features.texture_3d,
            self.limits.max_buffer_size,
            self.limits.uniform_buffer_offset_alignment,
            self.limits.max_texture_extent_2d,
            self.limits.max_texture_extent_3d,
            self.limits.max_texture_mip_levels,
            self.limits.max_texture_array_layers,
            self.limits.max_resource_layout_bindings,
            self.limits.max_binding_array_count,
            self.limits.max_color_attachments,
            self.limits.max_dynamic_offsets_per_binding,
            self.limits.max_command_lists_per_submission,
            self.limits.max_commands_per_list,
            self.limits.max_draw_count,
            self.limits.max_dispatch_groups_per_axis
        )
    }
}

const fn is_depth_format(format: TextureFormat) -> bool {
    matches!(
        format,
        TextureFormat::Depth24Stencil8 | TextureFormat::Depth32Float
    )
}

/// A texture format used for color attachments.
pub type ColorFormat = TextureFormat;

/// Direction of positive clip-space Y in the logical target image. This is
/// explicit raster state, not a shader rewrite or an image-copy convention.
/// FrontFace retains its existing clip-space winding meaning in both modes.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub enum RasterYDirection {
    /// Positive clip-space Y points up the image.
    Up = 0,
    /// Positive clip-space Y points down the image.
    Down = 1,
}

/// Describes a graphics pipeline for `VulkanicGal::create_graphics_pipeline`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GraphicsPipelineDesc {
    /// A debug label; also what GPU profile classification sees.
    pub label: String,
    /// The pipeline layout.
    pub layout: Handle,
    /// The vertex shader module.
    pub vertex_shader: Handle,
    /// The fragment shader module.
    pub fragment_shader: Handle,
    /// Primitive assembly.
    pub topology: PrimitiveTopology,
    /// Face culling.
    pub cull_mode: CullMode,
    /// Which winding is front-facing.
    pub front_face: FrontFace,
    /// The vertex supplying flat-shaded outputs.
    pub provoking_vertex: ProvokingVertex,
    /// Clip-space Y direction in the target image.
    pub raster_y_direction: RasterYDirection,
    /// Color blending.
    pub blend: BlendMode,
    /// The depth test; `None` disables it.
    pub depth_compare: Option<CompareOp>,
    /// Whether depth is written.
    pub depth_write: bool,
    /// Optional explicit raster depth bias. It is only meaningful with an
    /// enabled depth test and a depth attachment.
    pub depth_bias: Option<DepthBias>,
    /// The formats of the color attachments it renders to.
    pub color_formats: Vec<ColorFormat>,
    /// The depth attachment format, if any.
    pub depth_format: Option<TextureFormat>,
    /// Stencil state (requires a `Depth24Stencil8` attachment).
    pub stencil: Option<StencilState>,
}

/// Describes a compute pipeline for `VulkanicGal::create_compute_pipeline`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ComputePipelineDesc {
    /// A debug label.
    pub label: String,
    /// The pipeline layout.
    pub layout: Handle,
    /// The compute shader module.
    pub shader: Handle,
}

/// Describes a render target for `VulkanicGal::create_render_target`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct RenderTargetDesc {
    /// A debug label.
    pub label: String,
    /// Color attachment views, in attachment order.
    pub color_views: Vec<Handle>,
    /// The depth-stencil attachment view, if any.
    pub depth_stencil_view: Option<Handle>,
    /// The size every attachment must have.
    pub extent: Extent3d,
}

/// Describes a frame target for `VulkanicGal::create_frame_target`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FrameTargetDesc {
    /// A debug label.
    pub label: String,
    /// The acquired `FrameId`'s raw value.
    pub frame_id: u64,
    /// The acquired swapchain image (`AcquiredFrame::render_target`).
    pub render_target: FrameRenderTargetId,
    /// The image size.
    pub extent: Extent3d,
    /// The image format.
    pub color_format: TextureFormat,
}

/// Describes a render pass for `VulkanicGal::create_render_pass`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct RenderPassDesc {
    /// A debug label; also what GPU profile classification sees.
    pub label: String,
    /// The render target or frame target it renders to.
    pub target: Handle,
    /// The color attachment formats.
    pub color_formats: Vec<ColorFormat>,
    /// The depth attachment format, if any.
    pub depth_format: Option<TextureFormat>,
}

/// Queue families a barrier transfers ownership between; values are part
/// of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum QueueClass {
    /// Graphics queue.
    Graphics = 1,
    /// Compute queue.
    Compute = 2,
    /// Transfer queue.
    Transfer = 3,
    /// Presentation.
    Present = 4,
    /// Outside the GAL.
    External = 5,
}
