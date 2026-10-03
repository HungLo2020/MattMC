"""Distinguish unfinished prior presents from this frame's own upload queue."""
import unittest

from AnalyzeGpuOverlap import PendingPresents, analyze


class GpuOverlapAnalysisTest(unittest.TestCase):
    def test_empty_trace_and_invalid_video_interval_do_not_become_evidence(self):
        with self.assertRaises(ValueError):
            analyze([])
        with self.assertRaises(ValueError):
            analyze([], (30, 20))

    def test_offscreen_work_with_one_prior_present_is_overlap_exposure(self):
        state = PendingPresents()
        self.assertEqual(state.sample(1, 0, 1), ('present', []))
        self.assertEqual(state.sample(2, 0, 1), ('offscreen', [1]))

    def test_upload_then_present_without_prior_present_is_not_exposure(self):
        state = PendingPresents()
        self.assertEqual(state.sample(1, 0, 0), ('offscreen', []))
        self.assertEqual(state.sample(2, 0, 1), ('present', []))
        self.assertEqual(state.sample(3, 2, 0), ('offscreen', []))

    def test_completed_current_submission_needs_no_present_classification(self):
        state = PendingPresents()
        self.assertEqual(state.sample(1, 1, 0), ('completed', []))
        self.assertEqual(state.sample(2, 1, 0), ('offscreen', []))

    def test_missing_or_failed_samples_stay_unknown_until_completion_catches_up(self):
        state = PendingPresents()
        state.sample(1, 0, 1)
        self.assertEqual(state.sample(3, 0, 1), ('unknown', []))
        self.assertEqual(state.sample(4, 3, 0), ('offscreen', []))
        self.assertEqual(state.sample(5, None, None), ('unknown', []))
        self.assertEqual(state.sample(6, 5, 1), ('present', []))

    def test_inconsistent_counts_and_regressing_completion_are_rejected(self):
        state = PendingPresents()
        state.sample(1, 0, 1)
        with self.assertRaises(ValueError):
            state.sample(2, 0, 0)
        state = PendingPresents()
        state.sample(1, 1, 0)
        with self.assertRaises(ValueError):
            state.sample(2, 0, 1)

    def test_video_interval_keeps_only_timestamped_overlap_witnesses(self):
        lines = [
            'vulkan.submission.ownership id=1 gpu_sample=ok gpu_completed=0 gpu_incomplete_present_submissions=1 gpu_sample_wall_start_ns=10 gpu_sample_wall_end_ns=11',
            'vulkan.submission.ownership id=2 gpu_sample=ok gpu_completed=0 gpu_incomplete_present_submissions=1 gpu_sample_wall_start_ns=20 gpu_sample_wall_end_ns=21',
            'vulkan.submission.ownership id=3 gpu_sample=ok gpu_completed=1 gpu_incomplete_present_submissions=0 gpu_sample_wall_start_ns=30 gpu_sample_wall_end_ns=31',
        ]
        result = analyze(lines, (19, 22))
        self.assertEqual(result['within_video_counts']['offscreen_queued_while_prior_present_incomplete'], 1)
        self.assertEqual(result['witnesses'][0]['prior_present_submissions'], [1])


if __name__ == '__main__':
    unittest.main()
