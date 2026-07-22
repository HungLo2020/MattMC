import tempfile
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import visual_capture_matrix as matrix


class VisualCaptureMatrixTest(unittest.TestCase):
    def test_rejects_incomparable_common_fingerprint_fields(self) -> None:
        rows = [
            {
                "repository": "current",
                "backend": "opengl",
                "shaders": "off",
                "status": "complete",
                "fingerprint": self.fingerprint(world="Origin"),
            },
            {
                "repository": "current",
                "backend": "vulkan",
                "shaders": "off",
                "status": "complete",
                "fingerprint": self.fingerprint(world="Other"),
            },
        ]
        with self.assertRaisesRegex(RuntimeError, "world"):
            matrix.validate_matrix(rows)

    def test_loads_complete_row_and_parses_perf_metrics(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata = {
                "status": "complete",
                "gitCommit": "abc",
                "benchmarkFingerprintHash": "hash",
                "benchmarkFingerprint": self.fingerprint(),
            }
            (root / "deterministic_camera_capture_1.json").write_text(
                matrix.json.dumps(metadata),
                encoding="utf-8",
            )
            audit_dir = root / "performance_audit_1"
            audit_dir.mkdir()
            (audit_dir / "vulkan-perf-audit-1.txt").write_text(
                "\n".join(
                    [
                        "timestamp_utc=now",
                        "deterministic_measured_frame_median_ms=1.5",
                        "deterministic_measured_frame_p95_ms=2.5",
                        "deterministic_measured_frame_p99_ms=3.5",
                        "deterministic_measured_frame_worst_ms=4.5",
                        "deterministic_measured_frame_total_ms=5.5",
                        "graphics_draw_count=7",
                        "gal_v2_graphics_draw_count=6",
                        "gal_v2_legacy_fallback_draw_count=1",
                    ]
                ),
                encoding="utf-8",
            )

            row = matrix.load_row(matrix.MatrixRowSpec("current", "opengl", "off", root))

            self.assertEqual("complete", row["status"])
            self.assertEqual(1.5, row["metrics"]["medianMs"])
            self.assertEqual(7, row["metrics"]["draws"])

    @staticmethod
    def fingerprint(world: str = "Origin") -> dict[str, object]:
        data = {field: "same" for field in matrix.COMMON_FINGERPRINT_FIELDS}
        data.update(
            {
                "schemaVersion": "2",
                "repositoryIdentity": "current",
                "repositoryCommit": "abc",
                "backend": "opengl",
                "shaderEnabled": "false",
                "shaderPack": "ComplementaryHungLoIfied.zip",
                "world": world,
            }
        )
        return data


if __name__ == "__main__":
    unittest.main()
