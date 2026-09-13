import re
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'Common'))
import capture_runner as runner


class CaptureEventFilteringTest(unittest.TestCase):
    def test_optimized_events_preserve_regex_results_line_numbers_and_limits(self):
        lines = [
            'rust_gal_frames_executed=4100 ' * 1000,
            'Using shaderpack: off', 'uSiNg ShAdErPaCk: off',
            'Usİng shaderpack: unicode dotted i',
            'Type is FRAGMENT', 'Missing program abc sodium:pipeline',
            '[DH-worker] ready', 'Validation Error VUID-123',
            'vk_layer_khronos_validation', 'No active Vulkan render pass to end',
            'Sodium Vulkan chunk pipelines', 'Unexpected error',
            'LightmapInfoParity', 'shader compose into swapchain',
            'GL_INVALID', 'OpenGL debug', 'unrelated Unicode λ',
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'run.log'
            path.write_text('\n'.join(lines) + '\n')
            for pattern in (runner.SHADER_EVENT_PATTERN, runner.VALIDATION_EVENT_PATTERN,
                            runner.KEY_SUMMARY_PATTERN, r'\S+Parity'):
                expected = [f'{i}:{line}' for i, line in enumerate(lines, 1)
                            if re.search(pattern, line, re.IGNORECASE)]
                self.assertEqual(expected, runner.filtered_lines(path, pattern))
                self.assertEqual(expected[:2], runner.filtered_lines(path, pattern, limit=2))


if __name__ == '__main__':
    unittest.main()
