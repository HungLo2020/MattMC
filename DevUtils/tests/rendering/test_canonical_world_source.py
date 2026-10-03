"""Explicit copied-world inputs must apply to every renderer configuration."""
import argparse
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'Common'))
import graphics_harness as harness


class CanonicalWorldSourceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.current = self.root / 'current'
        self.source = self.root / 'explicit-source'
        for run, content in ((self.current / 'run', b'default-world'),
                             (self.source, b'explicit-world')):
            region = run / 'saves/Origin/region'
            region.mkdir(parents=True)
            (region / 'r.0.0.mca').write_bytes(content)
            (run / 'options.txt').write_text('graphicsMode:1\n')
        self.args = argparse.Namespace(world='Origin', world_profile='migration-gate',
                                       world_distant_horizons_real_world=False)
        self.targets = {'current': harness.RepoTarget('current', self.current, 'test')}

    def materialize(self):
        return harness.materialize_canonical_fixture(self.args, self.targets, self.root / 'artifacts')

    def test_explicit_source_is_copied_for_vanilla_and_shader_worlds_without_dh(self):
        with mock.patch.dict(harness.os.environ, {'MATTMC_CAPTURE_RUN_SOURCE': str(self.source),
                                                'MATTMC_CAPTURE_SHADER_PACK_SOURCE': ''}):
            run = self.materialize()
        self.assertEqual((run / 'saves/Origin/region/r.0.0.mca').read_bytes(), b'explicit-world')
        manifest = json.loads((run.parent / 'fixture_manifest.json').read_text())
        self.assertEqual(manifest['source_run'], str(self.source))
        self.assertEqual(manifest['source_save_hash']['hash'], manifest['canonical_save_hash']['hash'])
        self.assertEqual((self.current / 'run/saves/Origin/region/r.0.0.mca').read_bytes(), b'default-world')
        self.assertEqual((self.source / 'saves/Origin/region/r.0.0.mca').read_bytes(), b'explicit-world')

    def test_non_dh_fixture_identity_separates_different_explicit_sources(self):
        ids = []
        for source in (self.source, self.current / 'run'):
            with mock.patch.dict(harness.os.environ, {'MATTMC_CAPTURE_RUN_SOURCE': str(source),
                                                    'MATTMC_CAPTURE_SHADER_PACK_SOURCE': ''}):
                ids.append(harness.canonical_fixture_id(self.args))
        self.assertNotEqual(ids[0], ids[1])

    def test_missing_explicit_source_does_not_silently_use_default_world(self):
        with mock.patch.dict(harness.os.environ, {'MATTMC_CAPTURE_RUN_SOURCE': str(self.root / 'missing'),
                                                'MATTMC_CAPTURE_SHADER_PACK_SOURCE': ''}):
            with self.assertRaisesRegex(SystemExit, 'Canonical fixture source world is missing'):
                self.materialize()

    def test_absent_override_retains_default_current_source(self):
        with mock.patch.dict(harness.os.environ, {'MATTMC_CAPTURE_RUN_SOURCE': '',
                                                'MATTMC_CAPTURE_SHADER_PACK_SOURCE': ''}):
            run = self.materialize()
        self.assertEqual((run / 'saves/Origin/region/r.0.0.mca').read_bytes(), b'default-world')


if __name__ == '__main__':
    unittest.main()
