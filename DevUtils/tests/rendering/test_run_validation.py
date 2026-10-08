"""The validation driver's commands, ordering and artifact readers."""
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import RunValidation as validation


class RunValidationTest(unittest.TestCase):
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
            artifact.write_text(json.dumps({"metrics": {
                "frame_time_ms": {"count": 1000, "total": 2000.0, "median": 1.9, "p99": 4.0},
                "validation_findings": {"concrete_vuid_count": 0}}}))
            (run / "capture" / "runClient_1.log").write_text("ready failureCount=0\n")
            row = validation.read_fps_artifact(artifact)
            self.assertEqual(500.0, row["fps"])
            self.assertTrue(row["clean"])
            (run / "capture" / "runClient_1.log").write_text("java.lang.IllegalStateException\n")
            self.assertFalse(validation.read_fps_artifact(artifact)["clean"])

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
            self.assertFalse(validation.read_parity(root / "missing")["passed"])

    def test_leftover_clients_ignore_this_process(self):
        self.assertNotIn(__import__("os").getpid(), validation.leftover_clients())


if __name__ == "__main__":
    unittest.main()
