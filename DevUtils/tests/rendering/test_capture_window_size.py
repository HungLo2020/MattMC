"""Shared launch dimensions must survive both launcher paths and stay bounded."""
import os
from pathlib import Path
import shlex
import sys
from types import SimpleNamespace
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'Common'))
import capture_window
import capture_runner


class CaptureWindowSizeTest(unittest.TestCase):
    def test_default_world_and_menu_stay_1280_by_720(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            self.assertEqual(capture_window.capture_window_size(False), (1280, 720))
            self.assertEqual(capture_window.capture_window_size(True), (1280, 720))

    def test_world_override_does_not_resize_a_menu_fixture(self):
        with mock.patch.dict(os.environ, {'MATTMC_CAPTURE_WORLD_WIDTH': '3840',
                                         'MATTMC_CAPTURE_WORLD_HEIGHT': '2160'}, clear=True):
            self.assertEqual(capture_window.capture_window_size(False), (3840, 2160))
            self.assertEqual(capture_window.capture_window_size(True), (1280, 720))

    def test_world_dimensions_reject_malformed_and_out_of_bounds_inputs(self):
        for value in ('0', '319', '3841', '-1', 'nan', '３８４０'):
            with self.subTest(value=value), mock.patch.dict(os.environ,
                    {'MATTMC_CAPTURE_WORLD_WIDTH': value}, clear=True):
                with self.assertRaises(ValueError):
                    capture_window.capture_window_size(False)
        for value in ('239', '2161'):
            with self.subTest(value=value), mock.patch.dict(os.environ,
                    {'MATTMC_CAPTURE_WORLD_HEIGHT': value}, clear=True):
                with self.assertRaises(ValueError):
                    capture_window.capture_window_size(False)

    def test_menu_override_cannot_silently_resize_a_world(self):
        with mock.patch.dict(os.environ, {'MATTMC_CAPTURE_MENU_WIDTH': '1600'}, clear=True):
            with self.assertRaisesRegex(ValueError, 'title/menu fixture'):
                capture_window.capture_window_size(False)

    def test_python_launcher_keeps_shared_resolution_and_removes_stale_dimensions(self):
        runner = object.__new__(capture_runner.CaptureRunner)
        runner.config = SimpleNamespace(shaders='off', world='Origin', title_screen_capture=False,
            deterministic_camera_capture=False,
            client_args='--width 640 --height=480 --quickPlaySingleplayer=Old enableShaders=true')
        runner.append_meta = mock.Mock()
        with mock.patch.dict(os.environ, {'MATTMC_CAPTURE_WORLD_WIDTH': '3840',
                                         'MATTMC_CAPTURE_WORLD_HEIGHT': '2160'}, clear=True):
            expected = capture_window.capture_window_size(False)
            runner.configure_client_args()
        args = shlex.split(runner.config.client_args)
        self.assertEqual(args.count('--width'), 1)
        self.assertEqual(args.count('--height'), 1)
        self.assertEqual((int(args[args.index('--width') + 1]),
                          int(args[args.index('--height') + 1])), expected)
        self.assertIn('--quickPlaySingleplayer=Origin', args)
        self.assertIn('enableShaders=false', args)


if __name__ == '__main__':
    unittest.main()
