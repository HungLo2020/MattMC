import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import RunDevCapture as capture


class RunDevCaptureWorkloadSignatureTest(unittest.TestCase):
    def test_settings_fingerprint_ignores_comments_and_order(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = root / "iris-a.properties"
            second = root / "iris-b.properties"
            first.write_text(
                "#Wed Jul 22 00:03:49 PDT 2026\n"
                "enableShaders=false\n"
                "shaderPack=ComplementaryHungLoIfied.zip\n",
                encoding="utf-8",
            )
            second.write_text(
                "#Wed Jul 22 00:04:12 PDT 2026\n"
                "shaderPack=ComplementaryHungLoIfied.zip\n"
                "\n"
                "enableShaders=false\n",
                encoding="utf-8",
            )

            self.assertEqual(capture.canonical_settings_bytes(first), capture.canonical_settings_bytes(second))

    def test_rejects_same_fingerprint_with_different_observed_workload(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reference = root / "reference.json"
            metadata = root / "metadata.json"
            status = root / "status.json"
            audit_dir = root / "audit"
            audit_dir.mkdir()
            (audit_dir / "vulkan-perf-audit-1.txt").write_text("timestamp_utc=now\n", encoding="utf-8")

            reference.write_text(
                json.dumps(
                    {
                        "status": "complete",
                        "performanceMode": True,
                        "benchmarkFingerprintHash": "same-config",
                        "observedWorkloadSignatureHash": "workload-a",
                    }
                ),
                encoding="utf-8",
            )
            metadata.write_text(
                json.dumps(
                    {
                        "status": "complete",
                        "performanceMode": True,
                        "benchmarkFingerprintHash": "same-config",
                        "observedWorkloadSignatureHash": "workload-b",
                    }
                ),
                encoding="utf-8",
            )
            status.write_text(
                json.dumps(
                    {
                        "status": "complete",
                        "measureFramesRecorded": 2,
                        "observedWorkloadSignatureHash": "workload-b",
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(RuntimeError, "observed workload signature mismatch"):
                capture.validate_deterministic_performance_metadata(
                    metadata,
                    status,
                    audit_dir,
                    expected_measure_frames=2,
                    compare_fingerprint_path=str(reference),
                )


if __name__ == "__main__":
    unittest.main()
