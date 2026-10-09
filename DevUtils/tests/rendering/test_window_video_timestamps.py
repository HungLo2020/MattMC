"""Preserve closely spaced capture timestamps rather than weakening validation."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import CaptureWindowVideo as observer


@unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'requires ffmpeg/ffprobe')
class WindowVideoTimestampTest(unittest.TestCase):
    def record(self, root, suffix, delta_us):
        video = root / f'gap-{delta_us}.{suffix}'
        # Two real frames, separated by less than a Matroska clock tick.
        command = ['ffmpeg', '-nostdin', '-v', 'error', '-n', '-f', 'lavfi',
                   '-i', 'testsrc2=size=32x32:rate=60:duration=0.04',
                   '-vf', rf'settb=1/1000000,setpts=if(eq(N\,1)\,{delta_us}\,PTS)',
                   '-frames:v', '2', *observer.lossless_encoder_args(), str(video)]
        subprocess.run(command, check=True, capture_output=True, timeout=15)
        probe = subprocess.run(['ffprobe', '-v', 'error', '-show_entries',
                                'frame=best_effort_timestamp_time', '-of', 'json', str(video)],
                               check=True, capture_output=True, text=True, timeout=15)
        frames = json.loads(probe.stdout)['frames']
        self.assertEqual(2, len(frames))
        return [float(frame['best_effort_timestamp_time']) for frame in frames]

    def test_nut_keeps_distinct_microsecond_times(self):
        with tempfile.TemporaryDirectory() as directory:
            for gap in (1, 100, 400, 1000, 16667):
                with self.subTest(gap_us=gap):
                    times = self.record(Path(directory), 'nut', gap)
                    self.assertEqual(0.0, times[0])
                    self.assertAlmostEqual(gap / 1_000_000, times[1], places=6)
                    self.assertGreater(times[1], times[0])

    def test_original_container_rounding_reproduces_rejected_duplicate(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual([0.0, 0.0], self.record(Path(directory), 'mkv', 400))


if __name__ == '__main__':
    unittest.main()
