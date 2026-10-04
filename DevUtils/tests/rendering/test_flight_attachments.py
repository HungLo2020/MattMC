"""An attachment must belong to its promoted request and completed presentation."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ObserveFlightAttachments import verify_capture, verify_source_receipt


class FlightAttachmentsTest(unittest.TestCase):
    def test_matching_frame_cannot_hide_a_stage_or_uniform_from_another_submission(self):
        manifest = dict(gameplay_frame_id=12, correlation_id=14, gal_submission_id=20)
        for options, receipt in (
                ({}, dict(frame_id=12, correlation_id=14, submission_id=20)),
                ({"uniform": True}, dict(frame_id=12, correlation_id=14, gal_submission_id=20)),
                ({"execution": True}, dict(frame_id=12, submission_id=20))):
            verify_source_receipt(receipt, manifest, **options)
            for key in receipt:
                with self.subTest(options=options, key=key), self.assertRaises(RuntimeError):
                    verify_source_receipt(dict(receipt, **{key: receipt[key] + 1}), manifest, **options)
            with self.assertRaises(RuntimeError):
                verify_source_receipt({"frame_id": 12}, manifest, **options)

    def test_other_frame_or_submission_cannot_supply_the_requested_readback(self):
        manifest = dict(gameplay_frame_id=12, correlation_id=14,
                        deterministic_rendered_frame_index=12, gal_submission_id=20)
        correlation = dict(manifest, same_acquired_presented_image=True)
        properties = {key: str(manifest[key]) for key in
                      ('gameplay_frame_id', 'correlation_id', 'deterministic_rendered_frame_index')}
        verify_capture(manifest, correlation, properties, 11)
        for key in ('gameplay_frame_id', 'correlation_id', 'deterministic_rendered_frame_index',
                    'gal_submission_id'):
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                verify_capture(manifest, dict(correlation, **{key: correlation[key] + 1}), properties, 11)
        with self.assertRaises(RuntimeError):
            verify_capture(manifest, dict(correlation, same_acquired_presented_image=False), properties, 11)

    def test_unrelated_later_frame_cannot_be_relabelled_as_the_early_capture(self):
        manifest = dict(gameplay_frame_id=20, correlation_id=22,
                        deterministic_rendered_frame_index=20, gal_submission_id=30)
        correlation = dict(manifest, same_acquired_presented_image=True)
        properties = {key: str(manifest[key]) for key in
                      ('gameplay_frame_id', 'correlation_id', 'deterministic_rendered_frame_index')}
        verify_capture(manifest, correlation, properties, 12)
        for request in (11, 21):
            with self.subTest(request=request), self.assertRaises(RuntimeError):
                verify_capture(manifest, correlation, properties, request)
