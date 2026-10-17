"""Timed jcmd dumps must not stall a frame benchmark's measured window."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'Common'))
import capture_runner


class TimedDumpTest(unittest.TestCase):
    def test_dump_waits_for_a_running_frame_benchmark(self):
        for status in ('initialized', 'settled', 'warming_or_settling', 'frame-lifecycle'):
            self.assertFalse(capture_runner.timed_dump_allowed(True, status), status)

    def test_dump_runs_after_benchmark_ends_or_without_one(self):
        self.assertTrue(capture_runner.timed_dump_allowed(True, 'complete'))
        self.assertTrue(capture_runner.timed_dump_allowed(True, 'failed'))
        self.assertTrue(capture_runner.timed_dump_allowed(True, None))
        self.assertTrue(capture_runner.timed_dump_allowed(False, None))


if __name__ == '__main__':
    unittest.main()
