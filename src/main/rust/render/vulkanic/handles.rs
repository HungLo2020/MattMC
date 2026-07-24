use super::error::{GalError, GalResult, StatusCode};

const KIND_SHIFT: u64 = 56;
const GENERATION_SHIFT: u64 = 32;
const KIND_MASK: u64 = 0xff;
const GENERATION_MASK: u64 = 0x00ff_ffff;
const INDEX_MASK: u64 = 0xffff_ffff;
pub const MAX_GENERATION: u32 = GENERATION_MASK as u32;

#[repr(u8)]
#[derive(Clone, Copy, Debug, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub enum HandleKind {
    Buffer = 1,
    Texture = 2,
    TextureView = 3,
    Sampler = 4,
    ShaderModule = 5,
    ResourceLayout = 6,
    ResourceSet = 7,
    PipelineLayout = 8,
    GraphicsPipeline = 9,
    ComputePipeline = 10,
    RenderTarget = 11,
    RenderPass = 12,
}

impl HandleKind {
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
            _ => None,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct Handle {
    raw: u64,
}

impl Handle {
    pub const NULL: Self = Self { raw: 0 };

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

    pub fn from_raw(raw: u64) -> Self {
        Self { raw }
    }

    pub fn raw(self) -> u64 {
        self.raw
    }

    pub fn is_null(self) -> bool {
        self.raw == 0
    }

    pub fn kind(self) -> Option<HandleKind> {
        HandleKind::from_raw(((self.raw >> KIND_SHIFT) & KIND_MASK) as u8)
    }

    pub fn index(self) -> u32 {
        (self.raw & INDEX_MASK) as u32
    }

    pub fn generation(self) -> u32 {
        ((self.raw >> GENERATION_SHIFT) & GENERATION_MASK) as u32
    }

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
