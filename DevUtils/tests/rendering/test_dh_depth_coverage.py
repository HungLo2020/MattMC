"""Regression checks for exact-frame, unquantized DH extension evidence."""

from copy import deepcopy
from pathlib import Path
import struct
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'Common'))
from dh_depth_coverage import CoverageEvidenceError, attachment_hash, visible_extension_mask


class DhDepthCoverageTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.size = (4, 2)
        self.manifest = {
            'source': 'real-gameplay-whole-frame-submit', 'synthetic_shader_scene': False,
            'gameplay_frame_id': 21, 'correlation_id': 22, 'gal_submission_id': 23,
            'vulkan_submission_timeline_value': 23, 'deterministic_rendered_frame_index': 24,
            'extent': {'width': 4, 'height': 2}, 'world_lod_route_selected': True,
            'world_lod_instances': 2, 'same_acquired_presented_image': True,
            'acquired_swapchain_image': 25, 'presented_swapchain_image': 25,
            'png_row_origin': 'top-left', 'readback_row_origin': 'top-left',
            'capture_scope': 'source-dh-depth-coverage', 'attachment_files': [],
            'attachment_evidence': {}, 'attachment_hashes': {},
        }
        self.correlation = deepcopy(self.manifest)
        self.ack = {'renderedFrameIndex': 24, 'wholeFramePresentationCorrelation': {
            'gameplayFrameId': 21, 'correlationId': 22, 'submissionId': 23,
            'acquiredSwapchainImage': 25, 'presentedSwapchainImage': 25}}
        self.write_depth('source_main_opaque_depth', [1, 0.99999, 1, 0.5, 1, 1, 1, 1])
        self.write_depth('source_dh_opaque_depth', [0.99999, 0.99999, 1, 0.6, 1, 0.9, 1, 1])

    def write_depth(self, name, values):
        raw = struct.pack('<8f', *values)
        filename = f'attachment-{name}.raw'
        (self.root / filename).write_bytes(raw)
        self.manifest['attachment_files'].append(filename)
        self.manifest['attachment_evidence'][name] = {'kind': 'depth', 'width': 4, 'height': 2}
        self.manifest['attachment_hashes'][name] = attachment_hash(raw)

    def mask(self):
        return visible_extension_mask(self.root, self.manifest, self.correlation, self.ack, self.size)[0]

    def test_clear_and_far_geometry_remain_distinct(self):
        # Both 1 and .99999 encode as zero in the old inverse 8-bit preview.
        self.assertEqual(bytes([255, 0, 0, 0, 0, 255, 0, 0]), self.mask())

    def test_raw_bottom_origin_is_normalized_once(self):
        self.manifest['readback_row_origin'] = 'bottom-left'
        self.assertEqual(bytes([0, 255, 0, 0, 255, 0, 0, 0]), self.mask())

    def test_stale_raw_bytes_fail_even_with_matching_extent(self):
        path = self.root / 'attachment-source_dh_opaque_depth.raw'
        raw = bytearray(path.read_bytes())
        raw[0] ^= 1
        path.write_bytes(raw)
        with self.assertRaisesRegex(CoverageEvidenceError, 'hash-mismatch'):
            self.mask()

    def test_missing_and_truncated_readbacks_fail(self):
        path = self.root / 'attachment-source_dh_opaque_depth.raw'
        path.write_bytes(path.read_bytes()[:-4])
        with self.assertRaisesRegex(CoverageEvidenceError, 'byte-count-mismatch'):
            self.mask()
        path.unlink()
        with self.assertRaises(FileNotFoundError):
            self.mask()

    def test_nonfinite_and_out_of_range_depth_fail(self):
        for value in (float('nan'), float('inf'), -0.1, 1.1):
            with self.subTest(value=value):
                self.write_depth('source_dh_opaque_depth', [value] + [1] * 7)
                with self.assertRaisesRegex(CoverageEvidenceError, 'invalid-depth-sample'):
                    self.mask()

    def test_unproven_identity_extent_and_origin_fail(self):
        original = deepcopy(self.manifest)
        for key, value in [('gameplay_frame_id', 99), ('gal_submission_id', 99),
                           ('deterministic_rendered_frame_index', 99), ('correlation_id', 99),
                           ('vulkan_submission_timeline_value', 99), ('world_lod_instances', 0),
                           ('extent', {'width': 2, 'height': 4}), ('readback_row_origin', 'unknown')]:
            with self.subTest(key=key):
                self.manifest = deepcopy(original)
                self.manifest[key] = value
                with self.assertRaises(CoverageEvidenceError):
                    self.mask()
        self.manifest = original
        self.ack['wholeFramePresentationCorrelation']['presentedSwapchainImage'] = 99
        with self.assertRaisesRegex(CoverageEvidenceError, 'image-identity-mismatch'):
            self.mask()
        self.ack['wholeFramePresentationCorrelation']['presentedSwapchainImage'] = 25
        self.correlation['same_acquired_presented_image'] = False
        with self.assertRaisesRegex(CoverageEvidenceError, 'acquired-presented'):
            self.mask()

    def test_normal_route_uses_float_depth_and_verified_private_alpha(self):
        from PIL import Image
        self.manifest['capture_scope'] = 'full-attachments'
        self.write_depth('main_depth', [1, 0.99999, 1, 0.5, 1, 1, 1, 1])
        rgba = bytes([80, 60, 20, 255] * 2 + [0, 0, 0, 0] * 6)
        Image.frombytes('RGBA', self.size, rgba).save(self.root / 'attachment-dh_private_color.png')
        self.manifest['attachment_evidence']['dh_private_color'] = {'format': 'Rgba8Unorm'}
        self.manifest['attachment_hashes']['dh_private_color'] = attachment_hash(rgba)
        self.assertEqual(bytes([255] + [0] * 7), self.mask())
        self.manifest['attachment_evidence']['dh_private_color']['format'] = 'Bgra8Unorm'
        bgra = bytes([20, 60, 80, 255] * 2 + [0, 0, 0, 0] * 6)
        (self.root / 'attachment-dh_private_color.raw').write_bytes(bgra)
        self.manifest['attachment_hashes']['dh_private_color'] = attachment_hash(bgra)
        self.assertEqual(bytes([255] + [0] * 7), self.mask())

    def test_hash_matches_native_reference_vectors(self):
        # Independently generated with libxxhash's C XXH32 and Rust's seed.
        for raw, expected in [(b'', '62be9431'), (b'a', 'ce068662'), (b'abc', '3a46e0e0'),
                              (bytes(range(15)), 'f3be7e45'), (bytes(range(16)), 'f43b3d9d'),
                              (bytes(range(17)), '88ca28cc'), (bytes(range(256)), 'a1f77aea')]:
            self.assertEqual(expected, attachment_hash(raw))


if __name__ == '__main__':
    unittest.main()
