"""The validation driver's commands, ordering and artifact readers."""
import json
import copy
import gzip
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parent))
import RunValidation as validation


class RunValidationTest(unittest.TestCase):
    def test_only_exact_orderly_shutdown_disconnect_is_classified_separately(self):
        stopping = "[23:08:08] [Render thread/INFO]: Stopping!\n"
        disconnect = ("[23:08:08] [Server thread/INFO]: Player lost connection: "
                      "Internal Exception: java.nio.channels.ClosedChannelException\n")
        with tempfile.TemporaryDirectory() as temporary:
            run = Path(temporary)
            (run / "capture").mkdir()
            path = run / "capture/runClient_1.log"
            for suffix in (".log", ".log.gz"):
                path = path.with_suffix(suffix)
                data = (stopping + disconnect).encode()
                path.write_bytes(gzip.compress(data) if suffix.endswith("gz") else data)
                health = validation.read_client_health(run)
                self.assertEqual(0, health["exceptions"])
                self.assertEqual(2, health["raw_exception_mentions"])
                self.assertEqual(1, health["shutdown_disconnects"])
                path.unlink()

    def test_runtime_disconnects_stack_traces_and_other_shutdown_errors_still_fail(self):
        stopping = "[23:08:08] [Render thread/INFO]: Stopping!\n"
        disconnect = ("[23:08:08] [Server thread/INFO]: Player lost connection: "
                      "Internal Exception: java.nio.channels.ClosedChannelException\n")
        cases = {
            "before shutdown": disconnect + stopping,
            "error level": stopping + disconnect.replace("thread/INFO", "thread/ERROR"),
            "invalid marker": stopping.replace("thread/INFO", "thread/ERROR") + disconnect,
            "different error": stopping + disconnect.replace("ClosedChannelException", "IOException"),
            "stack trace": stopping + disconnect + "java.nio.channels.ClosedChannelException\n",
            "runtime error": "java.lang.IllegalStateException\n" + stopping + disconnect,
            "native panic": stopping + "panicked at render/frame.rs\n",
        }
        with tempfile.TemporaryDirectory() as temporary:
            run = Path(temporary)
            (run / "capture").mkdir()
            for name, text in cases.items():
                with self.subTest(case=name):
                    (run / "capture/runClient_1.log").write_text(text)
                    self.assertGreater(validation.read_client_health(run)["exceptions"], 0)

    def test_java_tests_skip_the_serial_rust_rerun_and_use_the_release_library(self):
        command = validation.gradle_java_tests_command(["net.vulkanic.*"])
        self.assertIn("-x", command)
        self.assertEqual("testRustNative", command[command.index("-x") + 1])
        self.assertIn("-PmattmcRustProfile=release", command)
        self.assertEqual(["--tests", "net.vulkanic.*"], command[-2:])
        self.assertNotIn("--tests", validation.gradle_java_tests_command(None))

    def test_perf_protocol_interleaves_current_first(self):
        self.assertEqual(["current", "frozen", "current", "frozen"], validation.perf_order(2))
        self.assertEqual("frozen-opengl-shaders-off", validation.fps_mode("frozen", False))
        self.assertEqual("current-rust-vulkan-shaders-on", validation.fps_mode("current", True))

    def test_fps_command_adds_dh_only_for_dh_modes(self):
        root = Path("/tmp/x")
        self.assertIn("--world-distant-horizons-real-world",
                      validation.fps_command(root, "m", False, True, 1800))
        command = validation.fps_command(root, "m", True, False, 6000)
        self.assertNotIn("--world-distant-horizons-real-world", command)
        self.assertEqual("6000", command[command.index("--measure-frames") + 1])
        self.assertEqual("enableShaders=true", command[command.index("--client-args") + 1])

    def test_failed_rust_tests_reads_the_trailing_failure_list(self):
        output = ("failures:\n\n---- a::b stdout ----\npanicked\n\n"
                  "failures:\n    a::b\n    c::d\n\ntest result: FAILED. 1 passed; 2 failed\n")
        self.assertEqual(["a::b", "c::d"], validation.failed_rust_tests(output))
        self.assertEqual([], validation.failed_rust_tests("test result: ok. 3 passed\n"))

    def test_fps_artifact_is_clean_only_without_exceptions_failures_or_vuids(self):
        with tempfile.TemporaryDirectory() as temporary:
            run = Path(temporary)
            (run / "capture").mkdir()
            artifact = run / "graphics_audit_artifact.json"
            document = {"capture": {"exit_code": 0}, "validation": {
                "complete": True, "crash_free": True, "device_loss_free": True,
                "frame_sampler_validity_passed": True, "performance_publishable": True,
                "orphan_process_detected": False, "rss_guard_triggered": False}, "metrics": {
                "frame_time_ms": {"count": 1000, "total": 2000.0, "median": 1.9, "p99": 4.0},
                "validation_findings": {"concrete_vuid_count": 0}}}
            artifact.write_text(json.dumps(document))
            (run / "capture" / "runClient_1.log").write_text("ready failureCount=0\n")
            row = validation.read_fps_artifact(artifact)
            self.assertEqual(500.0, row["fps"])
            self.assertTrue(row["clean"])
            self.assertFalse(validation.read_fps_artifact(artifact, 6000)["clean"])
            for key, value in document["validation"].items():
                with self.subTest(flag=key):
                    invalid = copy.deepcopy(document)
                    invalid["validation"][key] = not value
                    artifact.write_text(json.dumps(invalid))
                    self.assertFalse(validation.read_fps_artifact(artifact)["clean"])
                    del invalid["validation"][key]
                    artifact.write_text(json.dumps(invalid))
                    self.assertFalse(validation.read_fps_artifact(artifact)["clean"])
            for section, key, value in (("capture", "exit_code", False),
                                        ("capture", "exit_code", 143),
                                        ("frame_time_ms", "total", -1),
                                        ("frame_time_ms", "total", float("nan")),
                                        ("frame_time_ms", "count", 0)):
                with self.subTest(section=section, field=key, value=value):
                    invalid = copy.deepcopy(document)
                    (invalid["capture"] if section == "capture" else invalid["metrics"][section])[key] = value
                    artifact.write_text(json.dumps(invalid))
                    self.assertFalse(validation.read_fps_artifact(artifact)["clean"])
            invalid = copy.deepcopy(document)
            del invalid["metrics"]["validation_findings"]["concrete_vuid_count"]
            artifact.write_text(json.dumps(invalid))
            self.assertFalse(validation.read_fps_artifact(artifact)["clean"])
            artifact.write_text(json.dumps(document))
            (run / "capture" / "runClient_1.log").write_text("java.lang.IllegalStateException\n")
            self.assertFalse(validation.read_fps_artifact(artifact)["clean"])

    def test_performance_requires_all_modes_repeat_health_fps_and_tail(self):
        modes = {name: {side: {"fps": [101.0, 103.0] if side == "current" else [100.0, 100.0],
                              "p99_ms": [4.0, 4.0], "clean": True}
                       for side in ("current", "frozen")} for name, _, _ in validation.FPS_MODES}
        self.assertEqual([], validation.performance_comparison(modes))
        for change in ("missing mode", "unhealthy", "too few", "nan", "slower fps", "worse tail"):
            bad = copy.deepcopy(modes)
            side = bad["vanilla"]["current"]
            if change == "missing mode": del bad["shaders-dh"]
            elif change == "unhealthy": side["clean"] = False
            elif change == "too few": side["fps"] = [102.0]
            elif change == "nan": side["fps"][0] = float("nan")
            elif change == "slower fps": side["fps"] = [95.0, 99.0]
            elif change == "worse tail": side["p99_ms"] = [4.1, 4.1]
            with self.subTest(change=change):
                self.assertTrue(validation.performance_comparison(bad))

    def test_background_exception_fails_aggregate_and_still_runs_wiki(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            argv = ["validation", "--label", "cpu-failure", "--run-source", str(root),
                    "--vanilla-run-source", str(root), "--shader-pack", str(root),
                    "--skip", "java-tests", "--skip", "gate", "--skip", "parity", "--skip", "fps"]
            with patch.object(validation, "REPO", root), patch.object(validation, "FROZEN_REPO", root), \
                 patch.object(sys, "argv", argv), patch.object(validation, "leftover_clients", return_value=[]), \
                 patch.object(validation.subprocess, "run", return_value=SimpleNamespace(stdout="test-head")), \
                 patch.object(validation, "run_logged", return_value=0), \
                 patch.object(validation, "rust_tests", side_effect=OSError("test launch failed")):
                self.assertEqual(1, validation.main())
            result = json.loads((root / "artifacts/graphics-captures/validation/cpu-failure/summary.json").read_text())
            self.assertFalse(result["passed"])
            self.assertFalse(result["steps"]["rust-tests"]["passed"])
            self.assertTrue(result["steps"]["wiki"]["passed"])

    def test_missing_requested_step_and_empty_aggregate_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            validation.ensure_marker(root)
            for steps, requested in (({}, []), ({"build": {"passed": True}}, ["build", "wiki"])):
                result = {"schema": "mattmc-validation-v2", "label": "missing", "head": "test-head",
                          "steps": steps, "requested_steps": requested, "timings_s": {}}
                self.assertEqual(1, validation.finish(root, result, validation.time.monotonic()))
                self.assertFalse(result["passed"])

    def test_parity_fails_on_a_failed_dh_extension_or_vuids(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            report_dir = root / "capture" / "1" / "paired_visual_static_terrain"
            report_dir.mkdir(parents=True)
            report = report_dir / "visual_parity_report.json"
            audit = root / "capture" / "1" / "current-rust-vulkan-shaders-on" / "graphics_audit_artifact.json"
            audit.parent.mkdir(parents=True)

            def write(passed, dh, vuids):
                report.write_text(json.dumps({"passed": passed, "pairs": [
                    {"diff": {"mean_rgb_abs": [1.0, 2.0, 3.0]}, "dh_visible_extension": {"passed": dh}}]}))
                audit.write_text(json.dumps({"metrics": {"validation_findings": {"concrete_vuid_count": vuids}}}))

            write(True, True, 0)
            self.assertTrue(validation.read_parity(root)["passed"])
            write(True, False, 0)
            self.assertFalse(validation.read_parity(root)["passed"])
            write(True, None, 2)
            self.assertFalse(validation.read_parity(root)["passed"])
            write(True, None, 0)
            self.assertFalse(validation.read_parity(root, expect_dh=True)["passed"])
            write(True, True, None)
            self.assertFalse(validation.read_parity(root)["passed"])
            write(True, True, 0)
            audit.unlink()
            self.assertFalse(validation.read_parity(root)["passed"])
            self.assertFalse(validation.read_parity(root / "missing")["passed"])

    def test_leftover_clients_ignore_this_process(self):
        self.assertNotIn(__import__("os").getpid(), [pid for pid, _ in validation.leftover_clients()])

    def test_cleanup_identifies_only_owned_java_clients_and_checks_pid_reuse(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            owned = root / "invocation/run"; owned.mkdir(parents=True)
            unrelated = root / "other-run"; unrelated.mkdir()
            proc_root = root / "proc"; proc_root.mkdir()
            for pid, comm, cwd in ((101, "java", owned), (102, "java", unrelated), (103, "python3", owned)):
                proc = proc_root / str(pid); proc.mkdir()
                (proc / "comm").write_text(comm)
                (proc / "cmdline").write_bytes(b"java\0KnotClient\0")
                (proc / "cwd").symlink_to(cwd, target_is_directory=True)
                (proc / "stat").write_text(f"{pid} (java client) S" + " 0" * 18 + " 999 0")
            self.assertEqual([(101, 999)], validation.leftover_clients(root / "invocation", proc_root))
            with patch.object(validation, "leftover_clients", return_value=[(101, 999)]), \
                 patch.object(validation, "client_start_ticks", return_value=1000), \
                 patch.object(validation.os, "pidfd_open", return_value=77, create=True), \
                 patch.object(validation.signal, "pidfd_send_signal", create=True) as send, \
                 patch.object(validation.os, "kill") as kill, patch.object(validation.os, "close") as close:
                self.assertEqual(0, validation.stop_leftover_clients(root / "invocation"))
                send.assert_not_called(); kill.assert_not_called(); close.assert_called_once_with(77)
            with patch.object(validation, "leftover_clients", return_value=[(101, 999)]), \
                 patch.object(validation, "client_start_ticks", return_value=999), \
                 patch.object(validation.os, "pidfd_open", return_value=77, create=True), \
                 patch.object(validation.signal, "pidfd_send_signal", create=True) as send, \
                 patch.object(validation.os, "kill") as kill, patch.object(validation.os, "close"):
                self.assertEqual(1, validation.stop_leftover_clients(root / "invocation"))
                send.assert_called_once_with(77, validation.signal.SIGKILL)
                kill.assert_not_called()


if __name__ == "__main__":
    unittest.main()
