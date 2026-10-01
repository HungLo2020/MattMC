//! Generational resource handles.

use super::error::{GalError, GalResult, StatusCode};

const KIND_SHIFT: u64 = 56;
const GENERATION_SHIFT: u64 = 32;
const KIND_MASK: u64 = 0xff;
const GENERATION_MASK: u64 = 0x00ff_ffff;
const INDEX_MASK: u64 = 0xffff_ffff;
/// The largest generation a handle slot can reach before it is retired.
pub const MAX_GENERATION: u32 = GENERATION_MASK as u32;

/// The kind of resource a handle names; values are part of the C ABI.
#[repr(u8)]
#[derive(Clone, Copy, Debug, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub enum HandleKind {
    /// A buffer.
    Buffer = 1,
    /// A texture.
    Texture = 2,
    /// A view of a texture's mips and layers.
    TextureView = 3,
    /// A sampler.
    Sampler = 4,
    /// A shader module.
    ShaderModule = 5,
    /// A resource layout.
    ResourceLayout = 6,
    /// A resource set.
    ResourceSet = 7,
    /// A pipeline layout.
    PipelineLayout = 8,
    /// A graphics pipeline.
    GraphicsPipeline = 9,
    /// A compute pipeline.
    ComputePipeline = 10,
    /// A render target.
    RenderTarget = 11,
    /// A render pass.
    RenderPass = 12,
    /// A frame target bound to a swapchain image.
    FrameTarget = 13,
    /// A texture view paired with a sampler.
    CombinedTextureSampler = 14,
}

impl HandleKind {
    /// The kind for a raw wire value, if it names one.
    pub fn from_raw(value: u8) -> Option<Self> {
        match value {
            1 => Some(Self::Buffer),
            2 => Some(Self::Texture),
            3 => Some(Self::TextureView),
            4 => Some(Self::Sampler),
            5 => Some(Self::ShaderModule),
            6 => Some(Self::ResourceLayout),
            7 => Some(Self::ResourceSet),
            8 => Some(Self::PipelineLayout),
            9 => Some(Self::GraphicsPipeline),
            10 => Some(Self::ComputePipeline),
            11 => Some(Self::RenderTarget),
            12 => Some(Self::RenderPass),
            13 => Some(Self::FrameTarget),
            14 => Some(Self::CombinedTextureSampler),
            _ => None,
        }
    }
}

/// A generational resource handle: kind, slot index and generation packed
/// into 64 bits. A destroyed handle's slot is reused with a newer
/// generation, so stale handles are rejected rather than aliased.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct Handle {
    raw: u64,
}

impl Handle {
    /// The null handle; never valid for an operation.
    pub const NULL: Self = Self { raw: 0 };

    /// Packs a handle. Fails for generation 0 or above `MAX_GENERATION`.
    pub fn new(kind: HandleKind, index: u32, generation: u32) -> GalResult<Self> {
        if generation == 0 || generation > MAX_GENERATION {
            return Err(GalError::handle(
                StatusCode::GenerationExhausted,
                format!("invalid handle generation {generation}"),
            ));
        }
        Ok(Self {
            raw: ((kind as u64) << KIND_SHIFT)
                | ((generation as u64) << GENERATION_SHIFT)
                | index as u64,
        })
    }

    /// A handle from its raw 64-bit value (for example from the bridge).
    pub fn from_raw(raw: u64) -> Self {
        Self { raw }
    }

    /// The raw 64-bit value.
    pub fn raw(self) -> u64 {
        self.raw
    }

    /// Whether this is `Handle::NULL`.
    pub fn is_null(self) -> bool {
        self.raw == 0
    }

    /// The kind encoded in the handle, if valid.
    pub fn kind(self) -> Option<HandleKind> {
        HandleKind::from_raw(((self.raw >> KIND_SHIFT) & KIND_MASK) as u8)
    }

    /// The slot index.
    pub fn index(self) -> u32 {
        (self.raw & INDEX_MASK) as u32
    }

    /// The slot generation.
    pub fn generation(self) -> u32 {
        ((self.raw >> GENERATION_SHIFT) & GENERATION_MASK) as u32
    }

    /// The slot index and generation, or an error if the handle is null or
    /// of another kind.
    pub fn require_kind(self, expected: HandleKind) -> GalResult<(usize, u32)> {
        if self.is_null() {
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                "null handle is not valid",
            ));
        }
        match self.kind() {
            Some(kind) if kind == expected => Ok((self.index() as usize, self.generation())),
            Some(kind) => Err(GalError::handle(
                StatusCode::WrongHandleType,
                format!("expected {expected:?} handle, got {kind:?}"),
            )),
            None => Err(GalError::handle(
                StatusCode::WrongHandleType,
                format!("unknown handle kind in 0x{:016x}", self.raw),
            )),
        }
    }
}
