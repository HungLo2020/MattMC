use super::handles::Handle;
use super::resources::{AccessFlags, PipelineStageFlags};

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TextureUsageState {
    Undefined = 1,
    ShaderRead = 2,
    ShaderWrite = 3,
    ColorAttachment = 4,
    DepthStencilAttachment = 5,
    TransferSrc = 6,
    TransferDst = 7,
    Present = 8,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ResourceBarrier {
    pub resource: Handle,
    pub before: TextureUsageState,
    pub after: TextureUsageState,
    pub stages: PipelineStageFlags,
    pub access: AccessFlags,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ClearColor {
    pub r: f32,
    pub g: f32,
    pub b: f32,
    pub a: f32,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum AttachmentLoadOp {
    Load = 1,
    Clear = 2,
    DontCare = 3,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum AttachmentStoreOp {
    Store = 1,
    DontCare = 2,
}

#[derive(Clone, Debug, PartialEq)]
pub struct PassAttachment {
    pub view: Handle,
    pub load_op: AttachmentLoadOp,
    pub store_op: AttachmentStoreOp,
    pub clear_color: Option<ClearColor>,
}

#[derive(Clone, Debug, PartialEq)]
pub enum CommandOp {
    BeginPass {
        pass: Handle,
        target: Handle,
        colors: Vec<PassAttachment>,
        depth_stencil: Option<PassAttachment>,
    },
    BindGraphicsPipeline(Handle),
    BindComputePipeline(Handle),
    BindResourceSet {
        pipeline_layout: Handle,
        set_index: u32,
        set: Handle,
    },
    SetVertexBuffer {
        slot: u32,
        buffer: Handle,
        offset: u64,
    },
    SetIndexBuffer {
        buffer: Handle,
        offset: u64,
    },
    Draw {
        vertices: u32,
        instances: u32,
    },
    DrawIndexed {
        indices: u32,
        instances: u32,
    },
    Dispatch {
        groups_x: u32,
        groups_y: u32,
        groups_z: u32,
    },
    CopyBuffer {
        src: Handle,
        dst: Handle,
        size: u64,
    },
    Barrier(ResourceBarrier),
    EndPass,
}

#[derive(Clone, Debug, PartialEq)]
pub struct CommandListDesc {
    pub label: String,
    pub operations: Vec<CommandOp>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct CommandList {
    pub label: String,
    pub operations: Vec<CommandOp>,
}

impl From<CommandListDesc> for CommandList {
    fn from(desc: CommandListDesc) -> Self {
        Self {
            label: desc.label,
            operations: desc.operations,
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct SubmissionBatch {
    pub label: String,
    pub command_lists: Vec<CommandList>,
}
