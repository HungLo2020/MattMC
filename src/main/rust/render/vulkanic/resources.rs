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
    HostRead = 8,
    HostWrite = 9,
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
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
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
    HostRead = 8,
    HostWrite = 9,
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

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TextureSubresourceRange {
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
    Glsl = 3,
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
    pub array_count: u32,
    pub optional: bool,
    pub dynamic_offset_count: u32,
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
    pub array_index: u32,
    pub resource: Handle,
    pub kind: ResourceBindingKind,
    pub access: AccessFlags,
    pub dynamic_offsets: Vec<u64>,
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
    Invert = 4,
    Multiply = 5,
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

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BackendFeature {
    Graphics = 1,
    Compute = 2,
    DescriptorArrays = 3,
    OptionalBindings = 4,
    DynamicBufferOffsets = 5,
    UniformBuffers = 6,
    StorageBuffers = 7,
    StorageTextures = 8,
    IndirectDraw = 9,
    IndirectDispatch = 10,
    MultipleColorAttachments = 11,
    DepthOnlyPass = 12,
    BlendedPass = 13,
    TextureSubresourceCopies = 14,
    TextureMipLevels = 15,
    TextureArrayLayers = 16,
    HostBufferAccess = 17,
    Presentation = 18,
    RenderDocCapture = 19,
    TracyZones = 20,
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct BackendFeatureFlags {
    pub graphics: bool,
    pub compute: bool,
    pub descriptor_arrays: bool,
    pub optional_bindings: bool,
    pub dynamic_buffer_offsets: bool,
    pub uniform_buffers: bool,
    pub storage_buffers: bool,
    pub storage_textures: bool,
    pub indirect_draw: bool,
    pub indirect_dispatch: bool,
    pub multiple_color_attachments: bool,
    pub depth_only_pass: bool,
    pub blended_pass: bool,
    pub texture_subresource_copies: bool,
    pub texture_mip_levels: bool,
    pub texture_array_layers: bool,
    pub host_buffer_access: bool,
    pub presentation: bool,
    pub renderdoc_capture: bool,
    pub tracy_zones: bool,
}

impl BackendFeatureFlags {
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
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct BackendLimits {
    pub max_buffer_size: u64,
    pub max_texture_extent_2d: u32,
    pub max_texture_mip_levels: u32,
    pub max_texture_array_layers: u32,
    pub max_resource_layout_bindings: u32,
    pub max_binding_array_count: u32,
    pub max_color_attachments: u32,
    pub max_dynamic_offsets_per_binding: u32,
    pub max_command_lists_per_submission: u32,
    pub max_commands_per_list: u32,
    pub max_draw_count: u32,
    pub max_dispatch_groups_per_axis: u32,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct BackendCapabilities {
    pub name: &'static str,
    pub features: BackendFeatureFlags,
    pub limits: BackendLimits,
}

impl BackendCapabilities {
    pub fn supports(self, feature: BackendFeature) -> bool {
        self.features.supports(feature)
    }

    pub fn fingerprint_json(self) -> String {
        format!(
            "{{\"name\":\"{}\",\"features\":{{\"graphics\":{},\"compute\":{},\"descriptor_arrays\":{},\"optional_bindings\":{},\"dynamic_buffer_offsets\":{},\"uniform_buffers\":{},\"storage_buffers\":{},\"storage_textures\":{},\"indirect_draw\":{},\"indirect_dispatch\":{},\"multiple_color_attachments\":{},\"depth_only_pass\":{},\"blended_pass\":{},\"texture_subresource_copies\":{},\"texture_mip_levels\":{},\"texture_array_layers\":{},\"host_buffer_access\":{},\"presentation\":{},\"renderdoc_capture\":{},\"tracy_zones\":{}}},\"limits\":{{\"max_buffer_size\":{},\"max_texture_extent_2d\":{},\"max_texture_mip_levels\":{},\"max_texture_array_layers\":{},\"max_resource_layout_bindings\":{},\"max_binding_array_count\":{},\"max_color_attachments\":{},\"max_dynamic_offsets_per_binding\":{},\"max_command_lists_per_submission\":{},\"max_commands_per_list\":{},\"max_draw_count\":{},\"max_dispatch_groups_per_axis\":{}}}}}",
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
            self.limits.max_buffer_size,
            self.limits.max_texture_extent_2d,
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
pub struct FrameTargetDesc {
    pub label: String,
    pub frame_id: u64,
    pub extent: Extent3d,
    pub color_format: TextureFormat,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct RenderPassDesc {
    pub label: String,
    pub target: Handle,
    pub color_formats: Vec<ColorFormat>,
    pub depth_format: Option<TextureFormat>,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum QueueClass {
    Graphics = 1,
    Compute = 2,
    Transfer = 3,
    Present = 4,
    External = 5,
}
