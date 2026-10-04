"""Wrapper success cannot hide a native abort in the owned client's grace period."""
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "Common"))
from capture_runner import linux_core_dump_in_progress
import capture_runner
import graphics_harness as harness


class NativeTerminationTest(unittest.TestCase):
    def test_cleanup_records_core_before_forced_termination_can_erase_it(self):
        runner = object.__new__(capture_runner.CaptureRunner)
        runner.gradle_process = SimpleNamespace(pid=456)
        runner.platform_name = "linux"
        runner.find_client_pid = mock.Mock(return_value=123)
        runner.append_meta = mock.Mock()
        stat = "123 (java) " + " ".join(["S"] + ["0"] * 18 + ["42"])
        with mock.patch.object(Path, "read_text", return_value=stat), \
                mock.patch("capture_runner.safe_kill"), mock.patch("capture_runner.os.killpg"), \
                mock.patch("capture_runner.process_exists", return_value=False), \
                mock.patch("capture_runner.time.sleep"), \
                mock.patch("capture_runner.time.monotonic", side_effect=[0, 0, 1, 5]), \
                mock.patch("capture_runner.linux_core_dump_in_progress", return_value=True) as observe:
            runner.terminate_run_processes("timeout")
        observe.assert_called_once_with(123, 42)
        runner.append_meta.assert_any_call("cleanup_client_core_dumping=true")
        self.assertFalse(runner.run_client_active)

    def test_structured_artifact_rejects_native_abort_without_a_jvm_crash_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            runner = root / "DevUtils/Common/capture_runner.py"
            runner.parent.mkdir(parents=True)
            runner.touch()
            meta = root / "meta_run.txt"
            target = harness.RepoTarget("current", root, "test")
            mode = harness.ModeSpec("current-opengl-shaders-off", "current", "opengl", "off", "java", False)
            for abort in (False, True):
                meta.write_text("run_id=run\nexit_code=143\ntimeout=true\n"
                                + ("cleanup_client_core_dumping=true\n" if abort else ""))
                artifact = harness.normalize_capture_artifact(target, mode, root, "test", True, [], 0, True)
                self.assertEqual(not abort, artifact["validation"]["crash_free"])
                if abort:
                    self.assertFalse(artifact["validation"]["complete"])

    def test_core_dump_requires_the_same_process_start_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            process = root / "123"
            process.mkdir()
            (process / "stat").write_text("123 (java) " + " ".join(["S"] + ["0"] * 18 + ["42"]))
            (process / "status").write_text("Name:\tjava\nCoreDumping:\t1\n")
            self.assertTrue(linux_core_dump_in_progress(123, 42, proc_root=root))
            self.assertFalse(linux_core_dump_in_progress(123, 41, proc_root=root))
            (process / "status").write_text("Name:\tjava\nCoreDumping:\t0\n")
            self.assertFalse(linux_core_dump_in_progress(123, 42, proc_root=root))
            (process / "stat").unlink()
            self.assertFalse(linux_core_dump_in_progress(123, 42, proc_root=root))

    def test_reused_pid_during_status_read_cannot_supply_crash_evidence(self):
        with mock.patch("capture_runner.process_start_ticks", side_effect=[42, 43]), \
                mock.patch.object(Path, "read_text", return_value="CoreDumping:\t1\n"):
            self.assertFalse(linux_core_dump_in_progress(123, 42))
