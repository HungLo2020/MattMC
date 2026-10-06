"""Terrain-queue readiness properties for Current frame benchmark rows."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'Common'))
import graphics_harness as harness

PREFIX = '-Dmattmc.dev.graphicsFrameBenchmark.'


class BenchmarkTerrainQueueOptionsTest(unittest.TestCase):
    def test_static_rows_restart_on_any_build_during_measurement(self):
        self.assertEqual(harness.benchmark_terrain_queue_options('rust-vulkan', 'settled-static', []),
                         [f'{PREFIX}requireTerrainQueueDrain=true'])

    def test_moving_rows_start_drained_and_count_later_streaming(self):
        self.assertEqual(harness.benchmark_terrain_queue_options('rust-vulkan', 'moving-camera', []),
                         [f'{PREFIX}requireTerrainQueueDrain=true',
                          f'{PREFIX}terrainQueueDrainDuringMeasurement=false'])

    def test_explicit_jvm_arguments_win(self):
        self.assertEqual(harness.benchmark_terrain_queue_options(
            'rust-vulkan', 'moving-camera', [f'{PREFIX}requireTerrainQueueDrain=false']), [])
        self.assertEqual(harness.benchmark_terrain_queue_options(
            'rust-vulkan', 'moving-camera', [f'{PREFIX}terrainQueueDrainDuringMeasurement=true']),
            [f'{PREFIX}requireTerrainQueueDrain=true'])

    def test_frozen_and_other_profiles_get_no_current_gate(self):
        self.assertEqual(harness.benchmark_terrain_queue_options('opengl', 'moving-camera', []), [])
        self.assertEqual(harness.benchmark_terrain_queue_options('rust-vulkan', 'startup', []), [])


if __name__ == '__main__':
    unittest.main()
