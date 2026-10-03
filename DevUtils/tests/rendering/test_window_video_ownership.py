"""An explicit X11 ID must not bypass ownership, visibility or live PID checks."""
import contextlib
import io
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import CaptureWindowVideo as observer


class WindowVideoOwnershipTest(unittest.TestCase):
    def test_logical_client_extent_is_not_inferred_from_a_desktop_clipped_screenshot(self):
        result = mock.Mock(stdout='  Width: 3840\n  Height: 2160\n')
        with mock.patch.object(observer.subprocess, 'run', return_value=result):
            self.assertEqual(observer.window_extent('0x123'), (3840, 2160))

    def test_missing_or_invalid_x11_geometry_is_rejected(self):
        for text in ('Width: 3840\n', 'Width: 0\nHeight: 2160\n', 'bad window'):
            with self.subTest(text=text), mock.patch.object(observer.subprocess, 'run',
                    return_value=mock.Mock(stdout=text)):
                with self.assertRaises(RuntimeError):
                    observer.window_extent('0x123')

    def reject(self, provenance, *, viewable=True, process_error=None):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)/'video'
            args = ['CaptureWindowVideo.py', '--pid', '42', '--window-id', '0x123',
                    '--output', str(output), '--region', '0,0,640,240']
            with mock.patch.object(sys, 'argv', args), \
                    mock.patch.dict(os.environ, {'DISPLAY': ':0'}), \
                    mock.patch.object(observer.shutil, 'which', return_value='/usr/bin/tool'), \
                    mock.patch.object(observer, 'find_linux_client_window_id') as discovery, \
                    mock.patch.object(observer, 'window_capture_provenance', return_value=provenance) as proof, \
                    mock.patch.object(observer, 'linux_x11_window_is_viewable', return_value=viewable), \
                    mock.patch.object(observer, 'process_start', side_effect=process_error), \
                    mock.patch.object(observer.subprocess, 'run') as capture, \
                    contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as error:
                    observer.main()
                self.assertEqual(error.exception.code, 2)
                discovery.assert_not_called()
                proof.assert_called_once_with('linux', '0x123', 42)
                capture.assert_not_called()
                self.assertFalse(output.exists())

    def test_foreign_window_id_is_rejected_before_capture(self):
        self.reject({'status': 'unverified', 'observedWindowPid': 43})

    def test_owned_but_unviewable_window_is_rejected_before_capture(self):
        self.reject({'status': 'verified', 'observedWindowPid': 42}, viewable=False)

    def test_stale_pid_is_rejected_even_with_matching_window_properties(self):
        self.reject({'status': 'verified', 'observedWindowPid': 42},
                    process_error=FileNotFoundError('client exited'))


if __name__ == '__main__':
    unittest.main()
