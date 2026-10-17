"""Exact-frame DH coverage from bounded float depth readbacks, never PNG depth."""

from array import array
from pathlib import Path
import math
import struct
import sys
from typing import Mapping


class CoverageEvidenceError(ValueError):
    """The retained attachments cannot prove the requested presented frame."""


def attachment_hash(data: bytes) -> str:
    """XXH32 with the same seed as Rust's gameplay attachment manifest."""
    mask = 0xffffffff
    p1, p2, p3, p4, p5 = 0x9e3779b1, 0x85ebca77, 0xc2b2ae3d, 0x27d4eb2f, 0x165667b1

    def rotate(value, count):
        value &= mask
        return ((value << count) | (value >> (32 - count))) & mask

    seed = 0x47414d45
    offset = 0
    if len(data) >= 16:
        lanes = [(seed + p1 + p2) & mask, (seed + p2) & mask, seed, (seed - p1) & mask]
        while offset + 16 <= len(data):
            for lane, value in enumerate(struct.unpack_from('<4I', data, offset)):
                lanes[lane] = (rotate(lanes[lane] + value * p2, 13) * p1) & mask
            offset += 16
        result = sum(rotate(value, shift) for value, shift in zip(lanes, (1, 7, 12, 18)))
    else:
        result = seed + p5
    result = (result + len(data)) & mask
    while offset + 4 <= len(data):
        result = (rotate(result + struct.unpack_from('<I', data, offset)[0] * p3, 17) * p4) & mask
        offset += 4
    for value in data[offset:]:
        result = (rotate(result + value * p5, 11) * p1) & mask
    result ^= result >> 15
    result = (result * p2) & mask
    result ^= result >> 13
    result = (result * p3) & mask
    result ^= result >> 16
    return f'{result:08x}'


def _verify_frame(manifest: Mapping, correlation: Mapping, acknowledgement: Mapping,
                  size: tuple[int, int]) -> str:
    if manifest.get('source') != 'real-gameplay-whole-frame-submit' or manifest.get('synthetic_shader_scene') is not False:
        raise CoverageEvidenceError('not-real-gameplay-attachments')
    presentation = acknowledgement.get('wholeFramePresentationCorrelation', {})
    if not isinstance(presentation, Mapping):
        raise CoverageEvidenceError('missing-screenshot-presentation')
    expected = {
        'gameplay_frame_id': presentation.get('gameplayFrameId'),
        'correlation_id': presentation.get('correlationId'),
        'gal_submission_id': presentation.get('submissionId'),
        'deterministic_rendered_frame_index': acknowledgement.get('renderedFrameIndex'),
    }
    for field, value in expected.items():
        if type(value) is not int or value <= 0 or manifest.get(field) != value or correlation.get(field) != value:
            raise CoverageEvidenceError(f'screenshot-identity-mismatch:{field}')
    submission = expected['gal_submission_id']
    if any(doc.get('vulkan_submission_timeline_value') != submission for doc in (manifest, correlation)):
        raise CoverageEvidenceError('submission-timeline-mismatch')
    if correlation.get('same_acquired_presented_image') is not True:
        raise CoverageEvidenceError('unproven-acquired-presented-identity')
    acquired = correlation.get('acquired_swapchain_image')
    if (type(acquired) is not int or acquired <= 0
            or correlation.get('presented_swapchain_image') != acquired
            or presentation.get('acquiredSwapchainImage') != acquired
            or presentation.get('presentedSwapchainImage') != acquired):
        raise CoverageEvidenceError('screenshot-image-identity-mismatch')
    for doc in (manifest, correlation):
        if doc.get('world_lod_route_selected') is not True or not isinstance(doc.get('world_lod_instances'), int) or doc['world_lod_instances'] <= 0:
            raise CoverageEvidenceError('missing-submitted-dh-work')
        if doc.get('extent') != {'width': size[0], 'height': size[1]}:
            raise CoverageEvidenceError('attachment-extent-mismatch')
    if min(size) <= 0 or max(size) > 8192 or size[0] * size[1] > 8_388_608:
        raise CoverageEvidenceError('attachment-extent-out-of-bounds')
    origin = manifest.get('readback_row_origin')
    if origin not in ('top-left', 'bottom-left') or manifest.get('png_row_origin') != 'top-left':
        raise CoverageEvidenceError('unknown-attachment-row-origin')
    return origin


def _verify_hash(manifest: Mapping, name: str, raw: bytes) -> None:
    hashes = manifest.get('attachment_hashes', {})
    if not isinstance(hashes, Mapping) or hashes.get(name) != attachment_hash(raw):
        raise CoverageEvidenceError(f'attachment-hash-mismatch:{name}')


def _read_depth(root: Path, manifest: Mapping, name: str, size: tuple[int, int]) -> array:
    evidence = manifest.get('attachment_evidence', {})
    receipt = evidence.get(name, {}) if isinstance(evidence, Mapping) else {}
    if not isinstance(receipt, Mapping) or receipt.get('kind') != 'depth' or (receipt.get('width'), receipt.get('height')) != size:
        raise CoverageEvidenceError(f'missing-depth-receipt:{name}')
    filename = f'attachment-{name}.raw'
    files = manifest.get('attachment_files', [])
    if not isinstance(files, list) or filename not in files:
        raise CoverageEvidenceError(f'missing-depth-file-receipt:{name}')
    byte_count = size[0] * size[1] * 4
    with (root / filename).open('rb') as handle:
        raw = handle.read(byte_count + 1)
    if len(raw) != byte_count:
        raise CoverageEvidenceError(f'depth-byte-count-mismatch:{name}')
    _verify_hash(manifest, name, raw)
    depth = array('f')
    depth.frombytes(raw)
    if sys.byteorder != 'little':
        depth.byteswap()
    if any(not math.isfinite(value) or not 0.0 <= value <= 1.0 for value in depth):
        raise CoverageEvidenceError(f'invalid-depth-sample:{name}')
    return depth


def visible_extension_mask(root: Path, manifest: Mapping, correlation: Mapping,
                           acknowledgement: Mapping, size: tuple[int, int]) -> tuple[bytes, str]:
    """Return a top-left byte mask and its source, rejecting stale evidence."""
    origin = _verify_frame(manifest, correlation, acknowledgement, size)
    source = manifest.get('capture_scope') == 'source-dh-depth-coverage'
    main = _read_depth(root, manifest, 'source_main_opaque_depth' if source else 'main_depth', size)
    if source:
        distant = _read_depth(root, manifest, 'source_dh_opaque_depth', size)
        values = bytes(255 if near == 1.0 and far < 1.0 else 0 for near, far in zip(main, distant))
        definition = 'source DH opaque depth < 1 while source main opaque depth equals clear 1'
    else:
        from PIL import Image
        attachments = manifest.get('attachment_evidence', {})
        evidence = attachments.get('dh_private_color', {}) if isinstance(attachments, Mapping) else {}
        if not isinstance(evidence, Mapping) or evidence.get('format') not in ('Rgba8Unorm', 'Bgra8Unorm'):
            raise CoverageEvidenceError('unsupported-private-color-format')
        if evidence.get('format') == 'Bgra8Unorm':
            byte_count = size[0] * size[1] * 4
            with (root / 'attachment-dh_private_color.raw').open('rb') as handle:
                raw = handle.read(byte_count + 1)
            if len(raw) != byte_count:
                raise CoverageEvidenceError('private-color-byte-count-mismatch')
        else:
            with Image.open(root / 'attachment-dh_private_color.png') as image:
                if image.size != size:
                    raise CoverageEvidenceError('private-color-extent-mismatch')
                rgba = image.convert('RGBA')
            if origin == 'bottom-left':
                rgba = rgba.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
            raw = rgba.tobytes()
        _verify_hash(manifest, 'dh_private_color', raw)
        values = bytes(255 if depth == 1.0 and alpha > 0 else 0 for depth, alpha in zip(main, raw[3::4]))
        definition = 'DH private alpha > 0 while float main depth equals clear 1'
    if origin == 'bottom-left':
        width, height = size
        values = b''.join(values[row * width:(row + 1) * width] for row in range(height - 1, -1, -1))
    return values, definition
