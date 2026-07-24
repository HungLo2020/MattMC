use super::handles::Handle;

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum MemoryDomain {
    DeviceLocal = 1,
    Upload = 2,
    Readback = 3,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BufferUsage {
    Vertex = 1,
    Index = 2,
    Uniform = 3,
    Storage = 4,
    TransferSrc = 5,
    TransferDst = 6,
    Indirect = 7,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct BufferDesc {
    pub label: String,
    pub size: u64,
    pub memory: MemoryDomain,
    pub usages: Vec<BufferUsage>,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TextureDimension {
    D1 = 1,
    D2 = 2,
    D3 = 3,
    Cube = 4,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TextureFormat {
    Rgba8Unorm = 1,
    Bgra8Unorm = 2,
    Rgba16Float = 3,
    Depth24Stencil8 = 4,
    Depth32Float = 5,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TextureUsage {
    Sampled = 1,
    Storage = 2,
    ColorAttachment = 3,
    DepthStencilAttachment = 4,
    TransferSrc = 5,
    TransferDst = 6,
    Present = 7,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct Extent3d {
    pub width: u32,
    pub height: u32,
    pub depth: u32,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TextureDesc {
    pub label: String,
    pub dimension: TextureDimension,
    pub format: TextureFormat,
    pub extent: Extent3d,
    pub mip_levels: u32,
    pub array_layers: u32,
    pub usages: Vec<TextureUsage>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TextureViewDesc {
    pub label: String,
    pub texture: Handle,
    pub format: TextureFormat,
    pub base_mip: u32,
    pub mip_count: u32,
    pub base_layer: u32,
    pub layer_count: u32,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SamplerFilter {
    Nearest = 1,
    Linear = 2,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SamplerAddressMode {
    ClampToEdge = 1,
    Repeat = 2,
    MirroredRepeat = 3,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SamplerDesc {
    pub label: String,
    pub min_filter: SamplerFilter,
    pub mag_filter: SamplerFilter,
    pub mip_filter: SamplerFilter,
    pub address_u: SamplerAddressMode,
    pub address_v: SamplerAddressMode,
    pub address_w: SamplerAddressMode,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ShaderStage {
    Vertex = 1,
    Fragment = 2,
    Compute = 3,
    Geometry = 4,
    TessControl = 5,
    TessEvaluation = 6,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderModuleDesc {
    pub label: String,
    pub stage: ShaderStage,
    pub code_format: ShaderCodeFormat,
    pub code: Vec<u8>,
    pub entry_point: String,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ShaderCodeFormat {
    Spirv = 1,
    BackendPortableIr = 2,
}

#[repr(transparent)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct PipelineStageFlags(pub u32);

impl PipelineStageFlags {
    pub const NONE: Self = Self(0);
    pub const DRAW: Self = Self(1 << 0);
    pub const COMPUTE: Self = Self(1 << 1);
    pub const TRANSFER: Self = Self(1 << 2);
    pub const PRESENT: Self = Self(1 << 3);
}

#[repr(transparent)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct AccessFlags(pub u32);

impl AccessFlags {
    pub const NONE: Self = Self(0);
    pub const READ: Self = Self(1 << 0);
    pub const WRITE: Self = Self(1 << 1);
    pub const COLOR_ATTACHMENT: Self = Self(1 << 2);
    pub const DEPTH_STENCIL: Self = Self(1 << 3);
    pub const TRANSFER: Self = Self(1 << 4);

    pub fn reads(self) -> bool {
        self.0 & Self::READ.0 != 0
    }

    pub fn writes(self) -> bool {
        self.0
            & (Self::WRITE.0 | Self::COLOR_ATTACHMENT.0 | Self::DEPTH_STENCIL.0 | Self::TRANSFER.0)
            != 0
    }
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ResourceBindingKind {
    UniformBuffer = 1,
    StorageBuffer = 2,
    SampledTexture = 3,
    StorageTexture = 4,
    Sampler = 5,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ResourceBindingDesc {
    pub binding: u32,
    pub kind: ResourceBindingKind,
    pub stages: PipelineStageFlags,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ResourceLayoutDesc {
    pub label: String,
    pub bindings: Vec<ResourceBindingDesc>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ResourceSetDesc {
    pub label: String,
    pub layout: Handle,
    pub bindings: Vec<ResourceBinding>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ResourceBinding {
    pub binding: u32,
    pub resource: Handle,
    pub kind: ResourceBindingKind,
    pub access: AccessFlags,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PipelineLayoutDesc {
    pub label: String,
    pub resource_layouts: Vec<Handle>,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum PrimitiveTopology {
    Points = 1,
    Lines = 2,
    Triangles = 3,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum CullMode {
    None = 1,
    Front = 2,
    Back = 3,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BlendMode {
    Disabled = 1,
    Alpha = 2,
    Additive = 3,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum CompareOp {
    Always = 1,
    Less = 2,
    LessOrEqual = 3,
    Equal = 4,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum IndexType {
    U16 = 1,
    U32 = 2,
}

pub type ColorFormat = TextureFormat;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GraphicsPipelineDesc {
    pub label: String,
    pub layout: Handle,
    pub vertex_shader: Handle,
    pub fragment_shader: Handle,
    pub topology: PrimitiveTopology,
    pub cull_mode: CullMode,
    pub blend: BlendMode,
    pub depth_compare: Option<CompareOp>,
    pub color_formats: Vec<ColorFormat>,
    pub depth_format: Option<TextureFormat>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ComputePipelineDesc {
    pub label: String,
    pub layout: Handle,
    pub shader: Handle,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct RenderTargetDesc {
    pub label: String,
    pub color_views: Vec<Handle>,
    pub depth_stencil_view: Option<Handle>,
    pub extent: Extent3d,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct RenderPassDesc {
    pub label: String,
    pub target: Handle,
    pub color_formats: Vec<ColorFormat>,
    pub depth_format: Option<TextureFormat>,
}
