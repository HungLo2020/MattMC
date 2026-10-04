"""DH fog isolation must reach both launchers with equivalent settings."""
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'Common'))
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'Audit'))
import graphics_harness as harness
from harness_test_support import fake_repo


class DhCaptureFogTest(unittest.TestCase):
    def check_fog_environment(self, disable, expected):
        with tempfile.TemporaryDirectory() as temporary, mock.patch.dict(
                os.environ, {'MATTMC_CAPTURE_DH_DISABLE_FOG': disable}, clear=True):
            root = Path(temporary)
            for stream in ('opaque', 'water'):
                for name in ('current-rust-vulkan-shaders-off', 'frozen-opengl-shaders-off'):
                    with self.subTest(stream=stream, mode=name):
                        mode = next(m for m in harness.MATRIX_MODES if m.name == name)
                        argv = ['capture', '--world-distant-horizons-real-world',
                            '--world-distant-horizons-opaque', '--dh-composition-mode', 'DOUBLE_PASS']
                        if stream == 'water':
                            argv.append('--world-distant-horizons-water')
                        args = harness.parse_args(argv)
                        args._canonical_fixture_run_source = root / 'fixture'
                        _, env = harness.build_capture_command(fake_repo(root, mode.target), mode,
                            root / name / 'capture', 'settled-static', args, 'capture')
                        self.assertEqual(disable, env['MATTMC_CAPTURE_DH_DISABLE_FOG'])
                        self.assertEqual(expected, env['MATTMC_CAPTURE_DH_KEEP_FOG'])

    def test_disabled_fog_cannot_be_reenabled_by_dedicated_dh_capture(self):
        self.check_fog_environment('true', 'false')

    def test_ordinary_dh_capture_preserves_enabled_fog(self):
        self.check_fog_environment('false', 'true')


if __name__ == '__main__':
    unittest.main()
