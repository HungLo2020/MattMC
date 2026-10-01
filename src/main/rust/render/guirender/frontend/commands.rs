//! Small command-stream helpers: barriers, uniform writes and loaded attachments.

use super::*;

pub(super) fn gui_index_upload_ops(index_buffer: Handle) -> Vec<CommandOp> {
    vec![
        CommandOp::HostWriteBuffer {
            buffer: index_buffer,
            offset: 0,
            data: index_bytes(),
        },
        CommandOp::Barrier(buffer_barrier(
            index_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::IndexRead,
        )),
    ]
}

pub(super) fn push_f32(out: &mut Vec<u8>, value: f32) {
    out.extend_from_slice(&value.to_ne_bytes());
}

pub(super) fn buffer_barrier(
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

pub(super) fn push_uniform_write(operations: &mut Vec<CommandOp>, buffer: Handle, data: Vec<u8>) {
    operations.push(CommandOp::HostWriteBuffer {
        buffer,
        offset: 0,
        data,
    });
    operations.push(CommandOp::Barrier(buffer_barrier(
        buffer,
        TextureUsageState::TransferDst,
        TextureUsageState::ShaderRead,
    )));
}

pub(super) fn texture_barrier(
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

pub(super) fn index_bytes() -> Vec<u8> {
    let mut bytes = Vec::with_capacity(24);
    for value in [0u32, 1, 2, 3, 4, 5] {
        bytes.extend_from_slice(&value.to_le_bytes());
    }
    bytes
}

pub(super) fn loaded_frame_color_attachment(frame_target: Handle) -> PassAttachment {
    PassAttachment {
        view: frame_target,
        load_op: AttachmentLoadOp::Load,
        store_op: AttachmentStoreOp::Store,
        clear_color: None,
    }
}

pub(super) fn loaded_frame_depth_attachment(depth_attachment: Handle) -> PassAttachment {
    PassAttachment {
        view: depth_attachment,
        load_op: AttachmentLoadOp::Load,
        store_op: AttachmentStoreOp::Store,
        clear_color: None,
    }
}
