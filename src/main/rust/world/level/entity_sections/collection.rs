//! Bounded collection across sections for the stock non-spectator selector.
//! Java only projects ordinary matches into an exact ArrayList. A custom bounds,
//! spectator or section callback stops native traversal before that callback.
use super::super::ffi::CollectionStep;
use super::*;

/// Output: continuation kind, packed iterator cursor/version, 128 pairs of
/// section/member identities. Kinds: 0=end, 1=more, 2=custom section, 3=member tail,
/// 4=deferred traversal error after projecting this batch.
/// Callers must project all rows before running a returned callback, and finish
/// that section's callbacks before calling again. No lock spans those callbacks.
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_index_collect_non_spectators(
    cursor: *mut c_void,
    output: *mut u64,
) -> i32 {
    let Some(lease) = cursor.cast::<Lease>().as_mut() else {
        return -1;
    };
    let Some(bounds) = lease.bounds else {
        return -1;
    };
    if output.is_null() {
        return -1;
    }
    let out = std::slice::from_raw_parts_mut(output, 258);
    out[0] = 0;
    out[1] = 0;
    let mut count = 0;
    let mut data = lease.owner.data.lock().unwrap();
    loop {
        let member_cursor = if let Some(cursor) = lease.collection_cursor.take() {
            cursor
        } else {
            let status = advance(&mut data, lease.id, &mut lease.view);
            if status == 0 {
                return count as i32;
            }
            if status != 1 {
                out[0] = 4;
                out[1] = status as i64 as u64;
                return count as i32;
            }
            if lease.view.token == 0 {
                continue;
            }
            0
        };
        let step = data
            .members
            .get(lease.view.token as u32 as usize)
            .and_then(Option::as_ref)
            .filter(|(token, _)| *token == lease.view.token)
            .map_or(CollectionStep::Custom, |(_, context)| {
                context.collect_non_spectators(
                    bounds,
                    lease.view.token,
                    member_cursor,
                    &mut out[2..],
                    &mut count,
                )
            });
        match step {
            CollectionStep::Done => {}
            CollectionStep::Full(cursor) => {
                lease.collection_cursor = Some(cursor);
                out[0] = 1;
                return count as i32;
            }
            CollectionStep::Custom => {
                out[0] = 2;
                return count as i32;
            }
            CollectionStep::Callback { cursor, expected } => {
                out[0] = 3;
                out[1] = u64::from(cursor as u32) | (u64::from(expected as u32) << 32);
                return count as i32;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::world::level::entity_geometry::*;
    use crate::world::level::entity_sections::ffi::*;

    unsafe fn section(
        owner: *const c_void,
        key: i64,
        size: usize,
        callback: usize,
    ) -> *const c_void {
        let mut change = super::super::Change::default();
        assert_eq!(mattmc_section_index_find(owner, key, &mut change), 0);
        mattmc_section_index_add_key(owner, key);
        let binding = mattmc_section_index_allocate(owner);
        let members = mattmc_section_members_create();
        assert_eq!(
            mattmc_section_index_attach_members(owner, binding, members),
            0
        );
        assert_eq!(
            mattmc_section_index_insert(owner, key, change.slot, binding, &mut change),
            0
        );
        mattmc_section_index_finish(owner, binding);
        mattmc_section_members_visibility(members, 1);
        let world = mattmc_entity_geometry_world_create();
        let x = (key >> 42) as f64 * 16.;
        let body = mattmc_entity_geometry_create(world, x, 0., 0., x, 0., 0., x + 1., 1., 1.);
        for index in 0..size {
            let token = mattmc_section_members_allocate(members);
            assert_eq!(
                mattmc_section_members_attach_geometry(
                    members,
                    token,
                    body,
                    i32::from(index != callback)
                ),
                0
            );
            assert_eq!(
                mattmc_section_members_add(members, 0, token, std::ptr::null_mut()),
                0
            );
            mattmc_section_members_finish_binding(members, token);
        }
        mattmc_entity_geometry_release(body);
        mattmc_entity_geometry_world_release(world);
        members
    }

    #[test]
    fn collection_batches_across_sections_and_resumes_large_sections() {
        unsafe {
            let owner = mattmc_section_index_create();
            let first = section(owner, encode(0, 0, 0), 130, usize::MAX);
            let second = section(owner, encode(1, 0, 0), 3, usize::MAX);
            let bounds = [-1., -1., -1., 33., 2., 2.];
            let cursor = mattmc_section_index_spatial_cursor(owner, bounds.as_ptr());
            let mut out = [0; 258];
            assert_eq!(
                mattmc_section_index_collect_non_spectators(cursor, out.as_mut_ptr()),
                128
            );
            assert_eq!(out[0], 1);
            assert_eq!(
                mattmc_section_index_collect_non_spectators(cursor, out.as_mut_ptr()),
                5
            );
            assert_eq!(out[0], 0);
            assert_eq!(out[2], out[4]);
            assert_ne!(out[4], out[6]);
            assert_eq!(out[3] as u32, 129);
            assert_eq!(out[5] as u32, 130);
            assert_eq!(out[7] as u32, 1);
            mattmc_section_index_cursor_close(cursor);
            assert_eq!(
                mattmc_section_index_spatial_reopen(cursor, bounds.as_ptr()),
                0
            );
            assert_eq!(
                mattmc_section_index_collect_non_spectators(cursor, out.as_mut_ptr()),
                128
            );
            mattmc_section_index_cursor_release(cursor);
            mattmc_section_members_release(first);
            mattmc_section_members_release(second);
            mattmc_section_index_release(owner);
        }
    }

    #[test]
    fn callback_yields_at_original_member_and_leaves_future_columns_unopened() {
        unsafe {
            let owner = mattmc_section_index_create();
            let first = section(owner, encode(0, 0, 0), 130, 129);
            let bounds = [-1., -1., -1., 33., 2., 2.];
            let cursor = mattmc_section_index_spatial_cursor(owner, bounds.as_ptr());
            let mut out = [0; 258];
            assert_eq!(
                mattmc_section_index_collect_non_spectators(cursor, out.as_mut_ptr()),
                128
            );
            assert_eq!(
                mattmc_section_index_collect_non_spectators(cursor, out.as_mut_ptr()),
                1
            );
            assert_eq!(out[0], 3);
            assert_eq!(out[1] as u32, 129);
            assert_eq!(out[1] >> 32, 130);
            // Java resumes and finishes the original section; its callback may add this column.
            let future = section(owner, encode(1, 0, 0), 1, usize::MAX);
            assert_eq!(
                mattmc_section_index_collect_non_spectators(cursor, out.as_mut_ptr()),
                1
            );
            assert_eq!(out[0], 0);
            mattmc_section_index_cursor_release(cursor);
            mattmc_section_members_release(first);
            mattmc_section_members_release(future);
            mattmc_section_index_release(owner);
        }
    }

    #[test]
    fn range_failure_returns_collected_prefix_before_deferred_error() {
        unsafe {
            let owner = mattmc_section_index_create();
            let x = (1 << 21) - 2;
            let members = section(owner, encode(x, 0, 0), 1, usize::MAX);
            let edge = f64::from(x) * 16.;
            let bounds = [edge - 1., -1., -1., edge + 17., 2., 2.];
            let cursor = mattmc_section_index_spatial_cursor(owner, bounds.as_ptr());
            let mut out = [0; 258];
            assert_eq!(
                mattmc_section_index_collect_non_spectators(cursor, out.as_mut_ptr()),
                1
            );
            assert_eq!(out[0], 4);
            assert_eq!(out[1] as i64, -2);
            assert_eq!(out[3] as u32, 1);
            mattmc_section_index_cursor_release(cursor);
            mattmc_section_members_release(members);
            mattmc_section_index_release(owner);
        }
    }

    #[test]
    fn null_visibility_yields_before_nonempty_members_and_pool_reopen_clears_tail() {
        unsafe {
            let owner = mattmc_section_index_create();
            let members = section(owner, encode(0, 0, 0), 130, usize::MAX);
            let bounds = [-1., -1., -1., 1., 2., 2.];
            let cursor = mattmc_section_index_spatial_cursor(owner, bounds.as_ptr());
            let mut out = [0; 258];
            assert_eq!(
                mattmc_section_index_collect_non_spectators(cursor, out.as_mut_ptr()),
                128
            );
            mattmc_section_index_cursor_close(cursor);
            assert_eq!(
                mattmc_section_index_collect_non_spectators(cursor, out.as_mut_ptr()),
                0
            );
            assert_eq!(out[0], 0);
            mattmc_section_index_spatial_reopen(cursor, bounds.as_ptr());
            mattmc_section_members_visibility(members, -1);
            assert_eq!(
                mattmc_section_index_collect_non_spectators(cursor, out.as_mut_ptr()),
                0
            );
            assert_eq!(out[0], 2);
            mattmc_section_index_cursor_release(cursor);
            mattmc_section_members_release(members);
            mattmc_section_index_release(owner);
        }
    }
}
