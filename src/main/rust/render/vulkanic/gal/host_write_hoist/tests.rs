use super::*;
use crate::render::vulkanic::commands::ResourceBarrier;
use crate::render::vulkanic::resources::QueueClass;

fn handle(kind: HandleKind, index: u32) -> Handle {
    Handle::new(kind, index, 1).unwrap()
}

fn barrier(resource: Handle, before: TextureUsageState, after: TextureUsageState) -> CommandOp {
    CommandOp::Barrier(ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    })
}

fn write(buffer: Handle) -> Vec<CommandOp> {
    vec![
        barrier(buffer, TextureUsageState::ShaderRead, TextureUsageState::TransferDst),
        CommandOp::HostWriteBuffer { buffer, offset: 0, data: vec![1, 2, 3, 4] },
        barrier(buffer, TextureUsageState::TransferDst, TextureUsageState::ShaderRead),
    ]
}

fn bind(set: Handle) -> CommandOp {
    CommandOp::BindResourceSet {
        pipeline_layout: handle(HandleKind::PipelineLayout, 1),
        set_index: 0,
        set,
        dynamic_offsets: Vec::new(),
    }
}

fn list(operations: Vec<CommandOp>) -> CommandList {
    CommandList { label: "test".into(), operations }
}

fn kinds(list: &CommandList) -> Vec<String> {
    list.operations
        .iter()
        .map(|op| match op {
            CommandOp::Barrier(barrier) => format!("barrier{}", barrier.resource.index()),
            CommandOp::HostWriteBuffer { buffer, .. } => format!("write{}", buffer.index()),
            CommandOp::BindResourceSet { set, .. } => format!("bind{}", set.index()),
            CommandOp::Draw { .. } => "draw".into(),
            _ => "other".into(),
        })
        .collect()
}

#[test]
fn unreferenced_writes_move_to_the_start_with_their_barriers_grouped() {
    let a = handle(HandleKind::Buffer, 1);
    let b = handle(HandleKind::Buffer, 2);
    let set_a = handle(HandleKind::ResourceSet, 3);
    let set_b = handle(HandleKind::ResourceSet, 4);
    let draw = CommandOp::Draw { vertices: 3, instances: 1 };
    let mut ops = vec![bind(set_a), draw.clone()];
    ops.extend(write(a));
    ops.push(draw.clone());
    ops.extend(write(b));
    ops.extend([bind(set_b), draw.clone()]);
    let mut list = list(ops);
    let moved = hoist_host_writes(&mut list, |set, buffer| (set, buffer) == (set_b, b));
    assert_eq!(moved, 2);
    assert_eq!(
        kinds(&list),
        ["barrier1", "barrier2", "write1", "write2", "barrier1", "barrier2", "bind3", "draw", "draw", "bind4", "draw"]
    );
}

#[test]
fn writes_after_a_reference_stay_in_place() {
    let a = handle(HandleKind::Buffer, 1);
    let set = handle(HandleKind::ResourceSet, 3);
    let draw = CommandOp::Draw { vertices: 3, instances: 1 };
    let mut ops = write(a);
    ops.extend([bind(set), draw.clone()]);
    ops.extend(write(a));
    ops.extend([bind(set), draw.clone()]);
    let mut list = list(ops);
    let before = kinds(&list);
    let moved = hoist_host_writes(&mut list, |_, buffer| buffer == a);
    // The first write is already at the start; the second follows a read.
    assert_eq!(moved, 1);
    assert_eq!(kinds(&list), before);
}

#[test]
fn unknown_sets_and_unwrapped_writes_are_conservative() {
    let a = handle(HandleKind::Buffer, 1);
    let set = handle(HandleKind::ResourceSet, 3);
    let draw = CommandOp::Draw { vertices: 3, instances: 1 };
    let mut ops = vec![bind(set), draw.clone()];
    ops.extend(write(a));
    let mut unknown = list(ops.clone());
    assert_eq!(hoist_host_writes(&mut unknown, |_, _| true), 0);
    let mut bare = list(vec![draw.clone(), CommandOp::HostWriteBuffer { buffer: a, offset: 0, data: vec![0; 4] }, draw]);
    assert_eq!(hoist_host_writes(&mut bare, |_, _| false), 0);
}
