"""Feature-parity regression comparison against a baseline run."""
from pathlib import Path
import copy
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import RunFeatureParity as feature


class FeatureParityCompareTest(unittest.TestCase):
    def test_shader_capture_requires_native_final_output_correlation(self):
        class CaptureCommandObserved(Exception):
            pass

        for scenario, shaders in (("framed-map-shaders", True), ("chest-shaders", True),
                                  ("framed-map", False)):
            with self.subTest(scenario=scenario), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                argv = ["RunFeatureParity.py", "--label", "correlation-test", "--scenario", scenario,
                        "--repo-root", str(root), "--run-source", str(root),
                        "--shader-pack", str(root), "--frozen-repo", str(root)]
                calls = []

                def observe(command, **kwargs):
                    calls.append(command)
                    if len(calls) == 1:
                        return 0
                    raise CaptureCommandObserved()

                with patch.object(feature, "REPO", root), patch.object(sys, "argv", argv), \
                     patch.object(feature, "leftover_clients", return_value=[]), \
                     patch.object(feature.subprocess, "call", side_effect=observe):
                    with self.assertRaises(CaptureCommandObserved):
                        feature.main()
                command = calls[1]
                self.assertEqual(shaders, "--rust-selected-source-execution" in command)
                self.assertIn("--mode", command)
                self.assertIn(f"frozen-opengl-shaders-{'on' if shaders else 'off'}", command)

    def test_only_changes_from_the_baseline_count(self):
        baseline = {"scenarios": {
            "chest": {"success": True, "reports": {"cross_repository_visual_parity": "complete"}, "mean_rgb": [1.0, 1.0, 1.0]},
            "bed": {"success": False, "reports": {"cross_repository_model_crop_parity": "failed"}, "mean_rgb": [2.0, 2.0, 2.0]}}}
        same = {"scenarios": {
            "chest": {"success": True, "reports": {"cross_repository_visual_parity": "complete"}, "mean_rgb": [1.5, 1.0, 1.0]},
            "bed": {"success": False, "reports": {"cross_repository_model_crop_parity": "failed"}, "mean_rgb": [2.0, 2.0, 2.0]}}}
        self.assertEqual([], feature.compare(same, baseline), "a baseline failure that persists is not a regression")
        worse = {"scenarios": {
            "chest": {"success": False, "reports": {"cross_repository_visual_parity": "failed"}, "mean_rgb": [2.5, 1.0, 1.0]}}}
        problems = feature.compare(worse, baseline)
        self.assertEqual(4, len(problems))
        self.assertIn("bed: required baseline scenario missing", problems)

    def test_improvement_is_not_a_regression_and_explicit_subset_has_its_own_scope(self):
        base = {"scenarios": {"bed": {"success": False, "reports": {"crop": "False"}},
                              "chest": {"success": True}}}
        current = {"requested_scenarios": ["bed"], "scenarios": {
            "bed": {"success": True, "reports": {"crop": "True"}}}}
        self.assertEqual([], feature.compare(current, base))

    def test_absolute_parity_rejects_missing_failed_or_incomplete_capture_evidence(self):
        good = {"requested_scenarios": ["chest"], "scenarios": {"chest": {
            "exit": 0, "success": True, "mean_rgb": [1.0, 2.0, 3.0],
            "reports": {"cross_repository_visual_parity": "passed"}}}}
        self.assertTrue(feature.passed(good))
        for key, value in (("exit", 1), ("success", False), ("mean_rgb", []),
                           ("mean_rgb", [1, 2, float("nan")]), ("mean_rgb", [1, 2, -1]), ("reports", {})):
            with self.subTest(field=key, value=value):
                bad = copy.deepcopy(good); bad["scenarios"]["chest"][key] = value
                self.assertFalse(feature.passed(bad))
        bad = copy.deepcopy(good); bad["requested_scenarios"].append("bed")
        self.assertFalse(feature.passed(bad))
        self.assertFalse(feature.passed({"scenarios": {}}))

    def test_missing_visual_values_are_a_comparison_failure(self):
        base = {"scenarios": {"chest": {"mean_rgb": [1.0, 1.0, 1.0]}}}
        for values in (None, [], [1, 2, "bad"]):
            with self.subTest(values=values):
                self.assertTrue(feature.compare({"scenarios": {"chest": {"mean_rgb": values}}}, base))

    def test_requested_fixture_missing_from_baseline_rejects_comparison(self):
        self.assertEqual(["bed: baseline missing requested scenario"], feature.compare(
            {"requested_scenarios": ["bed"], "scenarios": {"bed": {"success": True}}}, {"scenarios": {}}))

    def test_status_reads_nested_status_fields(self):
        self.assertEqual("complete", feature.status_of({"status": "complete"}))
        self.assertEqual("True", feature.status_of({"passed": True}))
        self.assertIsNone(feature.status_of(None))


if __name__ == "__main__":
    unittest.main()
