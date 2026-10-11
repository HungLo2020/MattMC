//! Immutable CPU member views for network payloads. A lease pins tag storage,
//! independently of registry generation retirement; no member IDs are copied.

use super::Tags;
use std::sync::Arc;

#[repr(C)]
pub(super) struct MemberView {
    pub(super) members: *const u32,
    pub(super) count: u64,
}

pub(super) struct TagView {
    // Rows borrow member arrays in this owner. Both remain immutable.
    _tags: Arc<Tags>,
    pub(super) rows: Vec<MemberView>,
}

impl TagView {
    pub(super) fn new(tags: Arc<Tags>) -> Self {
        let rows = tags
            .iter()
            .map(|(_, members)| MemberView {
                members: members.as_ptr(),
                count: members.len() as u64,
            })
            .collect();
        Self { _tags: tags, rows }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::content::tags::Location;

    #[test]
    fn views_pin_original_members_without_copying() {
        let members: Arc<[u32]> = Arc::from([0, 3, u32::MAX, 3]);
        let pointer = members.as_ptr();
        let weak = Arc::downgrade(&members);
        let tags = Arc::new(vec![
            (Location::new("test", "members"), members),
            (Location::new("test", "empty"), Arc::from([])),
        ]);
        let view = TagView::new(tags.clone());
        drop(tags);
        assert_eq!(16, std::mem::size_of::<MemberView>());
        assert_eq!(pointer, view.rows[0].members);
        assert_eq!(4, view.rows[0].count);
        assert_eq!(0, view.rows[1].count);
        assert_eq!([0, 3, u32::MAX, 3], &*weak.upgrade().unwrap());
        drop(view);
        assert!(weak.upgrade().is_none(), "release retires storage when no payload keeps it");
    }
}
