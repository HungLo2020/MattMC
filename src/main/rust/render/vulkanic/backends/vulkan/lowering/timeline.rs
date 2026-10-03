//! Diagnostic-only GPU overlap from a freshly sampled native timeline.
//! Retained queue entries and acquired-image receipts are not completion proof.

use std::collections::BTreeSet;

/// Host clock for correlation with external window observations, not timing.
pub(super) fn wall_time_ns() -> String {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|elapsed| elapsed.as_nanos().to_string())
        .unwrap_or_else(|_| "unknown".to_owned())
}

#[derive(Debug, PartialEq, Eq)]
pub(super) struct GpuOverlapSample {
    completed: u64,
    incomplete_submissions: usize,
    incomplete_present_submissions: usize,
    incomplete_images: usize,
}

impl GpuOverlapSample {
    pub(super) fn from_submissions(
        completed: u64,
        submissions: impl IntoIterator<Item = (u64, Option<u32>)>,
    ) -> Self {
        let mut incomplete_submissions = 0;
        let mut incomplete_present_submissions = 0;
        let mut images = BTreeSet::new();
        for (submission, image) in submissions {
            if submission <= completed { continue; }
            incomplete_submissions += 1;
            if let Some(image) = image {
                incomplete_present_submissions += 1;
                images.insert(image);
            }
        }
        Self { completed, incomplete_submissions, incomplete_present_submissions,
            incomplete_images: images.len() }
    }

    pub(super) fn fields(&self) -> String {
        format!("gpu_sample=ok gpu_completed={} gpu_incomplete_submissions={} gpu_incomplete_present_submissions={} gpu_incomplete_images={}",
            self.completed, self.incomplete_submissions, self.incomplete_present_submissions, self.incomplete_images)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn gpu_overlap_sample_excludes_completed_entries_and_counts_distinct_present_images() {
        let sample = GpuOverlapSample::from_submissions(2,
            [(1, Some(0)), (2, Some(2)), (3, None), (4, Some(1)), (5, Some(1)), (6, Some(2))]);
        assert_eq!(sample, GpuOverlapSample { completed: 2, incomplete_submissions: 4,
            incomplete_present_submissions: 3, incomplete_images: 2 });
    }

    #[test]
    fn gpu_overlap_sample_does_not_treat_retained_or_offscreen_work_as_present_overlap() {
        let retained = GpuOverlapSample::from_submissions(7, [(4, Some(0)), (5, Some(1))]);
        assert_eq!(retained.incomplete_images, 0);
        let updates = GpuOverlapSample::from_submissions(7, [(8, None), (9, None)]);
        assert_eq!(updates.incomplete_submissions, 2);
        assert_eq!(updates.incomplete_images, 0);
    }
}
