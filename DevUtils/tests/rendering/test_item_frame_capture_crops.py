"""Frame-correlated item-frame evidence must include real viewport crop pixels."""
from pathlib import Path
import sys
import tempfile
import unittest
import copy

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'Common'))
import graphics_harness as harness


class ItemFrameCaptureCropsTest(unittest.TestCase):
    def document(self, root):
        identity = 'minecraft:item_frame'
        doc = {
            'rustGalWorldModelMeshSetup': {'status': 'spawned', 'blockId': identity,
                'serverEntityPresent': True, 'clientEntityPresent': True, 'clientEntityId': 7},
            'itemFrameFixture': {'requested': True, 'complete': True, 'entityId': 7,
                'clientInvisible': False, 'clientGlowFrame': False, 'clientItemEmpty': False,
                'clientItemIdentity': 'minecraft:filled_map', 'clientRotation': 0, 'clientMapId': 0},
            'rustGalWorldBlockModels': {'semantic': [], 'execution': []},
            'rustGalWorldItemFrameMaps': [], 'rustGalWorldItemFrameMapExecutions': [], 'captures': [],
        }
        for index, frame in enumerate((100, 110, 120, 130, 140)):
            screenshot = root / f'frame-{index}.png'
            image = Image.new('RGB', (96, 96), (255, 0, 0))
            image.paste((0, 0, 255), (0, 48, 96, 96))
            image.save(screenshot)
            doc['captures'].append({'renderedFrameIndex': frame, 'screenshot': str(screenshot)})
            common = {'route': 'rust-vulkan-whole-frame', 'semanticIdentity': identity,
                      'meshKey': 900 + index, 'meshGeneration': 1}
            doc['rustGalWorldBlockModels']['semantic'].append({**common, 'frameIndex': frame,
                'projected': True, 'sectionCount': 1, 'lightCoords': 0,
                'screenBounds': {'left': 30, 'top': 20, 'right': 50, 'bottom': 40}})
            doc['rustGalWorldBlockModels']['execution'].append({**common,
                'deterministicFrameIndex': frame, 'instances': 1, 'submissionId': 800 + index})
            map_common = {'route': 'rust-vulkan-whole-frame', 'entityId': 7, 'mapId': 0,
                'rotation': 0, 'invisibleFrame': False, 'contentOffset': 0.4375,
                'textureIdentity': 'minecraft:map/0', 'textureId': 9}
            doc['rustGalWorldItemFrameMaps'].append({**map_common, 'frameIndex': frame, 'projected': True})
            doc['rustGalWorldItemFrameMapExecutions'].append({**map_common,
                'deterministicFrameIndex': frame, 'quads': 1, 'submissionId': 800 + index})
        return doc

    def test_correlated_frames_save_actual_pixels_and_shared_viewport_bounds(self):
        with tempfile.TemporaryDirectory() as directory:
            doc = self.document(Path(directory))
            evidence = harness.deterministic_item_frame_backing_capture_evidence(doc, 'item-frame-map')
            self.assertEqual('structural_present', evidence['status'])
            self.assertTrue(harness.completed_item_frame_capture_evidence(evidence))
            self.assertNotIn('rustGalWorldModelMeshRouteDecisions', doc)
            self.assertEqual(5, len(evidence['crops']))
            for crop in evidence['crops']:
                self.assertEqual([20, 46, 60, 86], crop.get('crop_box'))
                self.assertTrue(Path(crop.get('crop_path', '')).is_file())
                with Image.open(crop['crop_path']) as image:
                    self.assertEqual((40, 40), image.size)
                    self.assertEqual((255, 0, 0), image.getpixel((20, 0)))
                    self.assertEqual((0, 0, 255), image.getpixel((20, 20)))

    def test_missing_image_or_stale_execution_cannot_report_a_present_crop(self):
        for defect in ('missing-image', 'stale-map', 'duplicate-map'):
            with self.subTest(defect=defect), tempfile.TemporaryDirectory() as directory:
                doc = self.document(Path(directory))
                if defect == 'missing-image':
                    Path(doc['captures'][-1]['screenshot']).unlink()
                elif defect == 'stale-map':
                    doc['rustGalWorldItemFrameMapExecutions'][-1]['deterministicFrameIndex'] += 10
                else:
                    doc['rustGalWorldItemFrameMapExecutions'][-1]['quads'] = 2
                evidence = harness.deterministic_item_frame_backing_capture_evidence(doc, 'item-frame-map')
                self.assertEqual('incomplete_item_frame_backing_correlation', evidence['status'])
                self.assertNotEqual('present', evidence['crops'][-1]['status'])
                self.assertNotIn('crop_path', evidence['crops'][-1])
                self.assertFalse(harness.completed_item_frame_capture_evidence(evidence))

    def test_typed_proof_rejects_wrong_generation_submission_and_map_identity(self):
        for defect in ('generation', 'submission', 'map-identity'):
            with self.subTest(defect=defect), tempfile.TemporaryDirectory() as directory:
                doc = self.document(Path(directory))
                if defect == 'generation':
                    doc['rustGalWorldBlockModels']['execution'][-1]['meshGeneration'] += 1
                elif defect == 'submission':
                    doc['rustGalWorldItemFrameMapExecutions'][-1]['submissionId'] += 1
                else:
                    doc['rustGalWorldItemFrameMapExecutions'][-1]['mapId'] += 1
                evidence = harness.deterministic_item_frame_backing_capture_evidence(doc, 'item-frame-map')
                self.assertFalse(harness.completed_item_frame_capture_evidence(evidence))

    def test_generic_or_incomplete_evidence_cannot_replace_model_part_receipts(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence = harness.deterministic_item_frame_backing_capture_evidence(
                self.document(Path(directory)), 'item-frame-map')
            for field in ('producer_family', 'checked', 'status', 'route_status',
                          'execution_status', 'frame_sequence_status', 'game_window_status'):
                with self.subTest(field=field):
                    incomplete = {key: value for key, value in evidence.items() if key != field}
                    self.assertFalse(harness.completed_item_frame_capture_evidence(incomplete))

    def test_unreadable_image_and_offscreen_bounds_cannot_report_a_present_crop(self):
        for defect in ('corrupt-image', 'offscreen'):
            with self.subTest(defect=defect), tempfile.TemporaryDirectory() as directory:
                doc = self.document(Path(directory))
                if defect == 'corrupt-image':
                    Path(doc['captures'][-1]['screenshot']).write_bytes(b'invalid PNG')
                else:
                    doc['rustGalWorldBlockModels']['semantic'][-1]['screenBounds'] = {
                        'left': 200, 'right': 220, 'top': 20, 'bottom': 40}
                evidence = harness.deterministic_item_frame_backing_capture_evidence(doc, 'item-frame-map')
                self.assertNotEqual('structural_present', evidence['status'])
                self.assertNotEqual('present', evidence['crops'][-1]['status'])
                self.assertNotIn('crop_path', evidence['crops'][-1])


class FrozenItemFrameAdmissionTest(unittest.TestCase):
    def artifact(self, scenario, defect=None):
        identity = 'minecraft:item_frame'
        fixture = {'requested': True, 'complete': True, 'entityId': 7,
            'clientInvisible': False, 'clientGlowFrame': False, 'clientItemEmpty': False,
            'clientItemIdentity': 'minecraft:filled_map', 'clientMapId': 0,
            'clientRotation': 3 if scenario.endswith('-rotated') else 0,
            'clientDecorationReady': True, 'backingLightCoords': 0,
            'backingEmission': {'stage': 'model-render-to-buffer-returned', 'frameIndex': 100,
                'fixtureEntityId': 7, 'textureId': identity, 'entityType': identity, 'position': [1, 2, 3]},
            'mapEmission': {'stage': 'map-render-returned', 'frameIndex': 100,
                'fixtureEntityId': 7, 'mapId': 0, 'textureIdentity': 'minecraft:map/0',
                'lightCoords': 0, 'invisibleFrame': False, 'contentOffset': 0.4375},
            'mapDecorationEmission': {'stage': 'map-render-returned', 'frameIndex': 100,
                'fixtureEntityId': 7, 'mapId': 0, 'decorationIdentity': 'minecraft:red_x',
                'x': 24, 'y': -16, 'rotation': 5,
                'atlasIdentity': 'minecraft:textures/atlas/map_decorations.png'}}
        if defect == 'missing-map':
            fixture['mapEmission'] = None
        elif defect == 'wrong-rotation':
            fixture['clientRotation'] = 0
        elif defect == 'wrong-decoration':
            fixture['mapDecorationEmission']['decorationIdentity'] = 'minecraft:blue_marker'
        observed = harness.frozen_item_frame_backing_evidence(
            {'backend': 'opengl', 'itemFrameFixture': fixture}, scenario)
        return {'benchmark_fingerprint': {'workload_signature': {
            'camera': {'status': 'complete', 'poses': [{'yaw': 105, 'pitch': 10}]},
            'world_save_state': {'hash': 'saved-world'},
            'shaderpack': {'name': 'pack.zip', 'sha256': 'pack-hash'},
            'parity_config': {'hash': 'config-hash', 'missing_parity_critical_values': [],
                              'fixture': {'scenario': scenario}}}},
            'metrics': {'rust_gal_slice': {'frozen_item_frame_backing_evidence': observed,
                'world_mesh_model_capture_evidence': {'status': 'missing_real_setup'}}}}

    def test_existing_frozen_map_producers_admit_all_requested_variants(self):
        for scenario in ('item-frame-map', 'item-frame-map-rotated', 'item-frame-map-decorated'):
            with self.subTest(scenario=scenario):
                baseline = self.artifact(scenario)
                current = copy.deepcopy(baseline)
                self.assertTrue(baseline['metrics']['rust_gal_slice']['frozen_item_frame_backing_evidence']['passed'])
                self.assertEqual([], harness.parity_evidence_failures(baseline, current))

    def test_variant_admission_still_requires_valid_typed_frozen_emissions(self):
        for scenario, defect in (('item-frame-map', 'missing-map'),
                                ('item-frame-map-rotated', 'wrong-rotation'),
                                ('item-frame-map-decorated', 'wrong-decoration')):
            with self.subTest(scenario=scenario, defect=defect):
                baseline = self.artifact(scenario, defect)
                failures = harness.parity_evidence_failures(baseline, copy.deepcopy(baseline))
                self.assertEqual(['baseline_model_producer_evidence_missing:missing_real_setup'], failures)


if __name__ == '__main__':
    unittest.main()
