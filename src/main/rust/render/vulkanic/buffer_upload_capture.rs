//! Bounded diagnostic history of accepted host uploads. No GPU readback or
//! renderer policy. A later overlapping non-host/partial write loses proof.
use super::sync::SubmissionId;
use super::{GalError, GalResult, Handle};
use std::collections::BTreeMap;

// Persistent allocations span animation poses, not just one frame of draws.
// Bound metadata by the owned mesh residency and exact retained bytes separately.
const MAX_RANGES: usize = 2 * super::world_primitive_frontend::WORLD_MESH_GEOMETRY_RESIDENCY;
const MAX_TOTAL_BYTES: usize = 5 * 1024 * 1024;
const MAX_BYTES: usize = 512 * 80;
type Range = (Handle, u64, usize);

#[derive(Default)]
pub(super) struct BufferUploadCapture {
    accepted: BTreeMap<Range, Option<(Vec<u8>, SubmissionId)>>,
    pending: BTreeMap<Range, Option<Vec<u8>>>,
}

impl BufferUploadCapture {
    pub(super) fn watch(&mut self, buffer: Handle, offset: u64, size: usize) -> GalResult<()> {
        if size == 0 || size > MAX_BYTES || offset.checked_add(size as u64).is_none() {
            return Err(GalError::invalid_argument(
                "buffer upload capture range limit",
            ));
        }
        let key = (buffer, offset, size);
        // Registering a new allocation must never inherit old arena contents.
        self.accepted
            .retain(|k, _| !overlaps(*k, buffer, offset, size as u64));
        self.pending
            .retain(|k, _| !overlaps(*k, buffer, offset, size as u64));
        let retained_bytes = self.accepted.keys().map(|k| k.2).sum::<usize>();
        if self.accepted.len() >= MAX_RANGES
            || retained_bytes
                .checked_add(size)
                .is_none_or(|n| n > MAX_TOTAL_BYTES)
        {
            return Err(GalError::invalid_argument(
                "buffer upload capture residency limit",
            ));
        }
        self.accepted.insert(key, None);
        Ok(())
    }
    pub(super) fn begin(&mut self) {
        // A failed encode/submit may already have touched mapped memory.
        // Never retain older proof for ranges touched by an unaccepted batch.
        for key in self.pending.keys() {
            if let Some(value) = self.accepted.get_mut(key) {
                *value = None;
            }
        }
        self.pending.clear();
    }
    pub(super) fn write(&mut self, buffer: Handle, offset: u64, size: u64) {
        for key in self.accepted.keys() {
            if overlaps(*key, buffer, offset, size) {
                self.pending.insert(*key, None);
            }
        }
    }
    pub(super) fn host_write(&mut self, buffer: Handle, offset: u64, bytes: &[u8]) {
        self.write(buffer, offset, bytes.len() as u64);
        let Some(end) = offset.checked_add(bytes.len() as u64) else {
            return;
        };
        for key in self.accepted.keys() {
            let (watched, start, size) = *key;
            if watched == buffer && offset <= start && start + size as u64 <= end {
                let first = (start - offset) as usize;
                self.pending
                    .insert(*key, Some(bytes[first..first + size].to_vec()));
            }
        }
    }
    pub(super) fn accept(&mut self, id: SubmissionId) {
        for (key, value) in std::mem::take(&mut self.pending) {
            if let Some(entry) = self.accepted.get_mut(&key) {
                *entry = value.map(|bytes| (bytes, id));
            }
        }
    }
    pub(super) fn forget(&mut self, buffer: Handle) {
        self.accepted.retain(|k, _| k.0 != buffer);
        self.pending.retain(|k, _| k.0 != buffer);
    }
    pub(super) fn unwatch(&mut self, buffer: Handle, offset: u64, size: usize) {
        self.accepted.remove(&(buffer, offset, size));
        self.pending.remove(&(buffer, offset, size));
    }
    pub(super) fn get(
        &self,
        buffer: Handle,
        offset: u64,
        size: usize,
    ) -> Option<(&[u8], SubmissionId)> {
        if self.pending.contains_key(&(buffer, offset, size)) {
            return None;
        }
        self.accepted
            .get(&(buffer, offset, size))?
            .as_ref()
            .map(|(v, id)| (v.as_slice(), *id))
    }
}

fn overlaps(key: Range, buffer: Handle, start: u64, size: u64) -> bool {
    key.0 == buffer
        && size != 0
        && key.1 < start.saturating_add(size)
        && start < key.1 + key.2 as u64
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn vulkan_capture_tracks_accepted_uploads_across_submissions_and_invalidates_gpu_writes() {
        use super::super::resources::{BufferDesc, BufferUsage, MemoryDomain};
        use super::super::{CommandList, CommandListDesc, CommandOp, SubmissionBatch, VulkanicGal};
        let backend =
            super::super::backends::vulkan::VulkanBackend::new("upload history conformance")
                .unwrap();
        let mut gal = VulkanicGal::new_with_backend(Box::new(backend), false);
        let desc = |label: &str| BufferDesc {
            label: label.into(),
            size: 16,
            memory: MemoryDomain::Upload,
            usages: vec![
                BufferUsage::HostWrite,
                BufferUsage::TransferSrc,
                BufferUsage::TransferDst,
            ],
        };
        let a = gal.create_buffer(desc("watched")).unwrap();
        let b = gal.create_buffer(desc("other")).unwrap();
        gal.watch_buffer_upload_for_capture(a, 4, 4).unwrap();
        assert!(gal.watch_buffer_upload_for_capture(a, 15, 4).is_err());
        let submit = |gal: &mut VulkanicGal, ops: Vec<CommandOp>| {
            gal.submit(SubmissionBatch {
                label: "capture-test".into(),
                command_lists: vec![CommandList::from(CommandListDesc {
                    label: "capture-test-commands".into(),
                    operations: ops,
                })],
            })
            .unwrap()
        };
        submit(
            &mut gal,
            vec![CommandOp::HostWriteBuffer {
                buffer: a,
                offset: 0,
                data: vec![7; 16],
            }],
        );
        let original = gal.buffer_upload_for_capture(a, 4, 4).unwrap().1;
        assert_eq!(gal.buffer_upload_for_capture(a, 4, 4).unwrap().0, &[7; 4]);
        submit(
            &mut gal,
            vec![CommandOp::HostWriteBuffer {
                buffer: b,
                offset: 0,
                data: vec![9; 16],
            }],
        );
        assert_eq!(gal.buffer_upload_for_capture(a, 4, 4).unwrap().1, original);
        submit(
            &mut gal,
            vec![CommandOp::CopyBuffer {
                src: b,
                dst: a,
                size: 16,
            }],
        );
        assert!(gal.buffer_upload_for_capture(a, 4, 4).is_err());
        submit(
            &mut gal,
            vec![CommandOp::HostWriteBuffer {
                buffer: a,
                offset: 4,
                data: vec![3; 4],
            }],
        );
        assert_eq!(gal.buffer_upload_for_capture(a, 4, 4).unwrap().0, &[3; 4]);
        submit(
            &mut gal,
            vec![CommandOp::HostWriteBuffer {
                buffer: a,
                offset: 5,
                data: vec![2],
            }],
        );
        assert!(gal.buffer_upload_for_capture(a, 4, 4).is_err());
        gal.unwatch_buffer_upload_for_capture(a, 4, 4);
        submit(
            &mut gal,
            vec![CommandOp::HostWriteBuffer {
                buffer: a,
                offset: 4,
                data: vec![1; 4],
            }],
        );
        assert!(gal.buffer_upload_for_capture(a, 4, 4).is_err());
        gal.destroy(a).unwrap();
        gal.destroy(b).unwrap();
    }
    #[test]
    fn accepted_upload_survives_frames_but_not_overwrites_or_reuse() {
        let b = Handle::NULL;
        let mut c = BufferUploadCapture::default();
        c.watch(b, 4, 4).unwrap();
        c.begin();
        c.host_write(b, 0, &[0, 1, 2, 3, 4, 5, 6, 7, 8]);
        assert!(c.get(b, 4, 4).is_none());
        c.accept(SubmissionId(1));
        assert_eq!(c.get(b, 4, 4), Some((&[4, 5, 6, 7][..], SubmissionId(1))));
        c.begin();
        c.write(b, 8, 4);
        c.accept(SubmissionId(2));
        assert_eq!(c.get(b, 4, 4).unwrap().1, SubmissionId(1));
        c.begin();
        c.host_write(b, 5, &[9]);
        c.accept(SubmissionId(3));
        assert!(c.get(b, 4, 4).is_none());
        c.begin();
        c.host_write(b, 4, &[4, 5, 6, 7]);
        c.write(b, 6, 1);
        c.accept(SubmissionId(4));
        assert!(c.get(b, 4, 4).is_none());
        c.begin();
        c.write(b, 6, 1);
        c.host_write(b, 4, &[4, 5, 6, 7]);
        c.accept(SubmissionId(5));
        assert!(c.get(b, 4, 4).is_some());
        c.begin();
        c.host_write(b, 4, &[0; 4]);
        c.begin();
        c.accept(SubmissionId(6)); // rejected batch
        assert!(c.get(b, 4, 4).is_none());
        c.watch(b, 4, 4).unwrap();
        assert!(c.get(b, 4, 4).is_none());
        c.forget(b);
        assert!(c.accepted.is_empty());
        assert!(c.pending.is_empty());
    }
    #[test]
    fn small_animated_allocations_share_a_fixed_total_byte_budget() {
        let b = Handle::NULL;
        let mut c = BufferUploadCapture::default();
        // More than the former 128-range ceiling, without increasing byte residency.
        for i in 0..256 {
            let offset = (i * 16) as u64;
            c.watch(b, offset, 8).unwrap();
            c.begin();
            c.host_write(b, offset, &[i as u8; 8]);
            c.accept(SubmissionId(i as u64 + 1));
        }
        for i in 0..256 {
            assert_eq!(c.get(b, (i * 16) as u64, 8).unwrap().0, &[i as u8; 8]);
        }
        c.forget(b);
        let count = MAX_TOTAL_BYTES / MAX_BYTES;
        for i in 0..count {
            c.watch(b, (i * MAX_BYTES) as u64, MAX_BYTES).unwrap();
        }
        assert!(c.watch(b, MAX_TOTAL_BYTES as u64, 1).is_err());
        c.unwatch(b, 0, MAX_BYTES);
        c.watch(b, MAX_TOTAL_BYTES as u64, MAX_BYTES).unwrap();
        assert_eq!(
            c.accepted.keys().map(|k| k.2).sum::<usize>(),
            MAX_TOTAL_BYTES
        );
    }
    #[test]
    fn capture_bounds_and_overlapping_allocation_replacement() {
        let mut c = BufferUploadCapture::default();
        let b = Handle::NULL;
        assert!(c.watch(b, 0, 0).is_err());
        assert!(c.watch(b, 0, MAX_BYTES + 1).is_err());
        assert!(c.watch(b, u64::MAX, 4).is_err());
        for i in 0..MAX_RANGES {
            c.accepted.insert((b, (i * 8) as u64, 4), None);
        }
        assert!(c.watch(b, (MAX_RANGES * 8) as u64, 4).is_err());
        c.watch(b, 1, 4).unwrap();
        assert_eq!(c.accepted.len(), MAX_RANGES);
        assert!(!c.accepted.contains_key(&(b, 0, 4)));
    }
}
