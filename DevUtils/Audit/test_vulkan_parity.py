import importlib.util
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "DevUtils" / "Audit" / "VulkanParity.py"


def load_module():
    spec = importlib.util.spec_from_file_location("mattmc_vulkan_parity", MODULE_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError("unable to load VulkanParity.py")
    module = importlib.util.module_from_spec(spec)
    # dataclasses resolve the module while decorating classes on Python 3.13.
    import sys
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class VulkanParityDiscoveryTest(unittest.TestCase):
    def test_managed_rust_vulkan_metadata_is_normalized_and_resolved_locally(self):
        parity = load_module()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            metadata = directory / "meta_20260827_000000_000000.txt"
            latest = directory / "latest_20260827_000000_000000.log"
            latest.write_text("", encoding="utf-8")
            metadata.write_text(
                "run_id=20260827_000000_000000\n"
                "backend=rust-vulkan\n",
                encoding="utf-8",
            )
            loaded = parity.load_capture_meta(metadata)
            self.assertIsNotNone(loaded)
            self.assertEqual(loaded.backend, "vulkan")
            self.assertEqual(loaded.latest_log, latest)

    def test_matching_key_includes_fixture_and_world_identity(self):
        parity = load_module()
        base = dict(
            run_id="a", backend="vulkan", path=Path("a"),
            client_args="same", enable_shaders="true", shader_pack="pack",
            parity_fixture_schema="fixture-v1", parity_fixture_id="scene-a",
            parity_fixture_source_save_hash="source-a", world_save_state_hash="world-a",
        )
        left = parity.CaptureMeta(**base)
        right = parity.CaptureMeta(**{**base, "world_save_state_hash": "world-b"})
        self.assertNotEqual(left.matching_key, right.matching_key)


if __name__ == "__main__":
    unittest.main()
