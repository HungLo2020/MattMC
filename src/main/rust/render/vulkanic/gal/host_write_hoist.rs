//! Moves host buffer writes ahead of a command list's GPU work.
//!
//! A `HostWriteBuffer` wrapped in its own transitions (into `TransferDst`
//! immediately before, out of it immediately after) can run at the start of
//! its command list when no earlier command in that list references the
//! buffer: nothing in the list can observe the difference, and earlier
//! submissions stay ordered by the same barriers. Each write lowers to a
//! transfer between barriers, so leaving them between passes drains the GPU
//! once per write; gathered at the start they cost one dependency.

use super::*;

/// One hoistable write: the indices of its pre-barrier, write and post-barrier.
struct Unit {
    start: usize,
    buffer: Handle,
}

/// Hoists eligible writes to the start of `list`, keeping their relative
/// order, and returns how many moved. `set_binds` says whether a resource
/// set binds a buffer; unknown sets must answer `true`.
pub(super) fn hoist_host_writes(
    list: &mut CommandList,
    mut set_binds: impl FnMut(Handle, Handle) -> bool,
) -> usize {
    let ops = &list.operations;
    let candidates: Vec<Unit> = (1..ops.len().saturating_sub(1))
        .filter_map(|index| {
            let CommandOp::HostWriteBuffer { buffer, .. } = &ops[index] else {
                return None;
            };
            let enters = matches!(&ops[index - 1], CommandOp::Barrier(barrier)
                if barrier.resource == *buffer
                    && barrier.after == TextureUsageState::TransferDst
                    && barrier.src_queue == barrier.dst_queue);
            let leaves = matches!(&ops[index + 1], CommandOp::Barrier(barrier)
                if barrier.resource == *buffer
                    && barrier.before == TextureUsageState::TransferDst
                    && barrier.src_queue == barrier.dst_queue);
            (enters && leaves).then_some(Unit { start: index - 1, buffer: *buffer })
        })
        .collect();
    if candidates.is_empty() {
        return 0;
    }
    let tracked: HashSet<Handle> = candidates.iter().map(|unit| unit.buffer).collect();
    let mut referenced = HashSet::new();
    let mut set_cache = HashMap::<Handle, Vec<Handle>>::new();
    let mut hoisted = Vec::new();
    let mut next_candidate = 0;
    let mut index = 0;
    while index < ops.len() {
        if let Some(unit) = candidates.get(next_candidate).filter(|unit| unit.start == index) {
            next_candidate += 1;
            if referenced.insert(unit.buffer) {
                hoisted.push(unit.start);
                index += 3;
                continue;
            }
        }
        for_each_buffer_reference(&ops[index], |handle| {
            if tracked.contains(&handle) {
                referenced.insert(handle);
            }
        });
        if let CommandOp::BindResourceSet { set, .. } = &ops[index] {
            let bound = set_cache.entry(*set).or_insert_with(|| {
                tracked.iter().copied().filter(|buffer| set_binds(*set, *buffer)).collect()
            });
            referenced.extend(bound.iter().copied());
        }
        index += 1;
    }
    if hoisted.is_empty() {
        return 0;
    }
    let mut remaining = std::mem::take(&mut list.operations).into_iter().map(Some).collect::<Vec<_>>();
    let mut take = |at: usize| remaining[at].take().expect("hoisted ops are taken once");
    let mut entries = Vec::with_capacity(hoisted.len());
    let mut writes = Vec::with_capacity(hoisted.len());
    let mut exits = Vec::with_capacity(hoisted.len());
    for start in &hoisted {
        entries.push(take(*start));
        writes.push(take(*start + 1));
        exits.push(take(*start + 2));
    }
    let mut operations = Vec::with_capacity(remaining.len());
    operations.extend(entries);
    operations.extend(writes);
    operations.extend(exits);
    operations.extend(remaining.into_iter().flatten());
    list.operations = operations;
    hoisted.len()
}

/// Calls `visit` for every buffer an op names directly (not through sets).
fn for_each_buffer_reference(op: &CommandOp, mut visit: impl FnMut(Handle)) {
    match op {
        CommandOp::DrawIndirect { buffer, .. }
        | CommandOp::DrawIndexedIndirect { buffer, .. }
        | CommandOp::DispatchIndirect { buffer, .. }
        | CommandOp::SetIndexBuffer { buffer, .. }
        | CommandOp::SetVertexBuffer { buffer, .. }
        | CommandOp::HostWriteBuffer { buffer, .. }
        | CommandOp::HostReadBuffer { buffer, .. } => visit(*buffer),
        CommandOp::Barrier(barrier) => visit(barrier.resource),
        CommandOp::CopyBufferToTexture(region) | CommandOp::CopyTextureToBuffer(region) => {
            visit(region.buffer)
        }
        CommandOp::CopyBuffer { src, dst, .. } | CommandOp::CopyBufferRegion { src, dst, .. } => {
            visit(*src);
            visit(*dst);
        }
        CommandOp::TrackSubmission(_)
        | CommandOp::BeginPass { .. }
        | CommandOp::EndPass
        | CommandOp::BindGraphicsPipeline(_)
        | CommandOp::BindComputePipeline(_)
        | CommandOp::BindResourceSet { .. }
        | CommandOp::Draw { .. }
        | CommandOp::DrawIndexed { .. }
        | CommandOp::Dispatch { .. }
        | CommandOp::CopyTexture(_)
        | CommandOp::CopyFrameTargetToTexture { .. }
        | CommandOp::CopyTextureToFrameTarget { .. }
        | CommandOp::GenerateMipmaps { .. }
        | CommandOp::Present { .. } => {}
    }
}

#[cfg(test)]
mod tests;
