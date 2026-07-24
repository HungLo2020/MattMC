#!/usr/bin/env python3
"""Repository-neutral chunk meshing corpus helpers.

This module deliberately stores section-corpus metadata outside Java object
serialization.  The first schema is a compact binary envelope around the
production real-replay fixture summaries already produced by MattMC.  It is
designed as an interchange artifact between the current Rust-migration repo and
the frozen Java baseline while the replay adapters mature.
"""

from __future__ import annotations

import argparse
import contextlib
import hashlib
import json
import math
import os
import platform
import struct
import subprocess
import sys
import time
import zlib
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Sequence


MAGIC = b"MMCMCORP"
SCHEMA_VERSION = 2
SUPPORTED_SCHEMA_VERSIONS = {1, 2}
HEADER = struct.Struct("<8sII32s")
SECTION_HEADER = struct.Struct("<IIIQQQQ")
DEFAULT_REPLAY_TEST = "net.sodium.client.perf.MeshingCorpusReplayTest"
SUPPORTED_PERF_CATEGORIES = {
    "dense-terrain",
    "fluid-heavy",
    "foliage",
    "modded-static",
    "ordinary-terrain",
    "translucent-heavy",
    "waterlogged",
    "weighted-multipart",
}
MODEL_HEAVY_CATEGORIES = {"dense-terrain", "foliage", "modded-static", "ordinary-terrain", "weighted-multipart"}
FLUID_HEAVY_CATEGORIES = {"fluid-heavy", "waterlogged"}
COMPACT_VERTEX_BYTES = 20
QUAD_INDEX_BYTES = 6 * 4


@dataclass(frozen=True)
class CorpusSection:
    name: str
    category: str
    classification: str
    non_air_blocks: int
    fluid_blocks: int
    emitted_quads: int
    fallback_blocks: int
    fallback_quads: int
    canonical_hash: str
    payload: dict[str, object]


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256_bytes(data: bytes) -> bytes:
    return hashlib.sha256(data).digest()


def encode_string(value: str) -> bytes:
    return value.encode("utf-8")


def read_string(data: bytes, offset: int, length: int, label: str) -> str:
    if length < 0 or offset < 0 or offset + length > len(data):
        raise ValueError(f"Invalid {label} string bounds")
    return data[offset : offset + length].decode("utf-8")


def load_real_replay_json(path: Path) -> dict[str, object]:
    doc = json.loads(path.read_text(encoding="utf-8"))
    if doc.get("status") != "ok":
        raise ValueError(f"Real replay output did not complete successfully: {path}")
    if not isinstance(doc.get("fixtures"), list):
        raise ValueError(f"Real replay output has no fixture list: {path}")
    return doc


def section_from_fixture(fixture: dict[str, object]) -> CorpusSection:
    summary = fixture.get("summary") if isinstance(fixture.get("summary"), dict) else {}
    corpus_input = fixture.get("corpus_input") if isinstance(fixture.get("corpus_input"), dict) else None
    name = str(fixture.get("name", "unknown"))
    fallback_blocks = int(summary.get("fallback_blocks", 0) or 0)
    fallback_quads = int(summary.get("fallback_quads", 0) or 0)
    total_vertices = int(summary.get("total_vertices", 0) or 0)
    emitted_quads = total_vertices // 4
    if fallback_blocks or fallback_quads:
        classification = "fallback-contaminated"
    elif corpus_input:
        unsupported = corpus_input.get("unsupported")
        if isinstance(unsupported, list) and unsupported:
            classification = "raw-input-captured-with-unsupported-records"
        else:
            classification = "replayable-raw-input-v2"
    else:
        classification = "summary-only-not-replayable"
    category = classify_name(name)
    non_air_blocks, fluid_blocks = emitted_block_counts(summary)
    canonical_hash = str(summary.get("canonical_hash", "0"))
    payload = {
        "fixture": fixture,
        "capture_kind": "production-real-replay-raw-input-v2" if corpus_input else "production-real-replay-fixture-summary-v1",
        "raw_input": corpus_input,
        "input_limitations": input_limitations(corpus_input),
    }
    return CorpusSection(
        name=name,
        category=category,
        classification=classification,
        non_air_blocks=non_air_blocks,
        fluid_blocks=fluid_blocks,
        emitted_quads=emitted_quads,
        fallback_blocks=fallback_blocks,
        fallback_quads=fallback_quads,
        canonical_hash=canonical_hash,
        payload=payload,
    )


def input_limitations(corpus_input: dict[str, object] | None) -> list[str]:
    if corpus_input:
        return [
            "Stores stable section tables, compact padded snapshot inputs, selected static model bundle data, and semantic evidence.",
            "Does not store Java object identities, live ClientLevel/Minecraft handles, graphics objects, or final quads as replay inputs.",
            "Unsupported model/fluid/entity data is listed explicitly in the unsupported records.",
        ]
    return [
        "This legacy corpus record stores deterministic section identity, classification, and semantic summary data only.",
        "Raw Java object graphs and final mesh dumps are intentionally not serialized.",
        "Replay adapters need schema-v2 raw_input data before this record is replayable.",
    ]


def emitted_block_counts(summary: dict[str, object]) -> tuple[int, int]:
    model_blocks: set[tuple[int, int, int]] = set()
    fluid_blocks: set[tuple[int, int, int]] = set()
    passes = summary.get("passes", [])
    if not isinstance(passes, list):
        return 0, 0
    for pass_doc in passes:
        if not isinstance(pass_doc, dict):
            continue
        quads = pass_doc.get("canonical_quads", [])
        if not isinstance(quads, list):
            continue
        for quad in quads:
            if not isinstance(quad, dict):
                continue
            block = quad.get("block")
            if not isinstance(block, list) or len(block) != 3:
                continue
            try:
                key = (int(block[0]), int(block[1]), int(block[2]))
            except (TypeError, ValueError):
                continue
            producer = str(quad.get("producer", ""))
            if producer == "fluid":
                fluid_blocks.add(key)
            else:
                model_blocks.add(key)
    return len(model_blocks | fluid_blocks), len(fluid_blocks)


def classify_name(name: str) -> str:
    lowered = name.lower()
    if "empty" in lowered:
        return "empty"
    if "dense" in lowered:
        return "dense-terrain"
    if "foliage" in lowered:
        return "foliage"
    if "weighted" in lowered or "multipart" in lowered:
        return "weighted-multipart"
    if "waterlogged" in lowered:
        return "waterlogged"
    if "fluid" in lowered:
        return "fluid-heavy"
    if "translucent" in lowered:
        return "translucent-heavy"
    if "complex" in lowered or "modded" in lowered:
        return "modded-static"
    return "ordinary-terrain"


def write_corpus_from_real_replay(real_replay_json: Path, output: Path) -> dict[str, object]:
    doc = load_real_replay_json(real_replay_json)
    fixtures = [fixture for fixture in doc.get("fixtures", []) if isinstance(fixture, dict)]
    sections = [section_from_fixture(fixture) for fixture in fixtures]
    metadata = {
        "schema": "mattmc-real-section-corpus-v2",
        "created_at": utc_now(),
        "source": str(real_replay_json),
        "source_runner": doc.get("runner"),
        "source_timestamp": doc.get("timestamp"),
        "section_count": len(sections),
        "format": {
            "magic": MAGIC.decode("ascii"),
            "version": SCHEMA_VERSION,
            "endianness": "little",
            "compression": "zlib-per-section-json",
            "checksums": "sha256 per compressed section payload plus sha256 header digest",
        },
    }
    metadata_bytes = json.dumps(metadata, sort_keys=True, separators=(",", ":")).encode("utf-8")
    body = bytearray()
    body.extend(HEADER.pack(MAGIC, SCHEMA_VERSION, len(sections), sha256_bytes(metadata_bytes)))
    body.extend(struct.pack("<I", len(metadata_bytes)))
    body.extend(metadata_bytes)
    for section in sections:
        name = encode_string(section.name)
        category = encode_string(section.category)
        classification = encode_string(section.classification)
        payload_doc = {
            "name": section.name,
            "category": section.category,
            "classification": section.classification,
            "non_air_blocks": section.non_air_blocks,
            "fluid_blocks": section.fluid_blocks,
            "emitted_quads": section.emitted_quads,
            "fallback_blocks": section.fallback_blocks,
            "fallback_quads": section.fallback_quads,
            "canonical_hash": section.canonical_hash,
            "payload": section.payload,
        }
        payload = zlib.compress(json.dumps(payload_doc, sort_keys=True).encode("utf-8"), level=9)
        body.extend(
            SECTION_HEADER.pack(
                len(name),
                len(category),
                len(classification),
                section.non_air_blocks,
                section.fluid_blocks,
                section.emitted_quads,
                len(payload),
            )
        )
        body.extend(sha256_bytes(payload))
        body.extend(name)
        body.extend(category)
        body.extend(classification)
        body.extend(payload)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(body)
    return summarize_corpus(output)


def summarize_corpus(path: Path) -> dict[str, object]:
    data = path.read_bytes()
    if len(data) < HEADER.size + 4:
        raise ValueError(f"Corpus is too small: {path}")
    magic, version, section_count, metadata_digest = HEADER.unpack_from(data, 0)
    if magic != MAGIC:
        raise ValueError(f"Invalid corpus magic in {path}: {magic!r}")
    if version not in SUPPORTED_SCHEMA_VERSIONS:
        raise ValueError(f"Unsupported corpus schema version {version}; expected one of {sorted(SUPPORTED_SCHEMA_VERSIONS)}")
    offset = HEADER.size
    (metadata_len,) = struct.unpack_from("<I", data, offset)
    offset += 4
    metadata_bytes = data[offset : offset + metadata_len]
    if sha256_bytes(metadata_bytes) != metadata_digest:
        raise ValueError("Corpus metadata checksum mismatch")
    offset += metadata_len
    metadata = json.loads(metadata_bytes.decode("utf-8"))
    sections: list[dict[str, object]] = []
    categories: dict[str, int] = {}
    classifications: dict[str, int] = {}
    for _ in range(section_count):
        if offset + SECTION_HEADER.size + 32 > len(data):
            raise ValueError("Truncated corpus section header")
        name_len, category_len, classification_len, non_air, fluids, quads, payload_len = SECTION_HEADER.unpack_from(data, offset)
        offset += SECTION_HEADER.size
        payload_digest = data[offset : offset + 32]
        offset += 32
        name = read_string(data, offset, name_len, "name")
        offset += name_len
        category = read_string(data, offset, category_len, "category")
        offset += category_len
        classification = read_string(data, offset, classification_len, "classification")
        offset += classification_len
        payload = data[offset : offset + payload_len]
        if len(payload) != payload_len:
            raise ValueError("Truncated corpus section payload")
        if sha256_bytes(payload) != payload_digest:
            raise ValueError(f"Corpus section payload checksum mismatch: {name}")
        offset += payload_len
        categories[category] = categories.get(category, 0) + 1
        classifications[classification] = classifications.get(classification, 0) + 1
        sections.append(
            {
                "name": name,
                "category": category,
                "classification": classification,
                "non_air_blocks": non_air,
                "fluid_blocks": fluids,
                "emitted_quads": quads,
                "payload": json.loads(zlib.decompress(payload).decode("utf-8")),
            }
        )
    if offset != len(data):
        raise ValueError("Corpus has trailing bytes")
    return {
        "schema": f"mattmc-real-section-corpus-summary-v{version}",
        "schema_version": version,
        "path": str(path),
        "bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "metadata": metadata,
        "section_count": section_count,
        "categories": categories,
        "classifications": classifications,
        "sections": sections,
    }


def section_payload(section: dict[str, object]) -> dict[str, object]:
    payload = section.get("payload")
    return payload if isinstance(payload, dict) else {}


def section_matches(section: dict[str, object], fixture: str = "", category: str = "", replayable_only: bool = False) -> bool:
    if fixture and section.get("name") != fixture:
        return False
    if category and section.get("category") != category:
        return False
    if replayable_only and section.get("classification") != "replayable-raw-input-v2":
        return False
    return True


def filtered_summary(summary: dict[str, object], fixture: str = "", category: str = "", replayable_only: bool = False) -> dict[str, object]:
    sections = [
        section
        for section in summary.get("sections", [])
        if isinstance(section, dict) and section_matches(section, fixture, category, replayable_only)
    ]
    result = dict(summary)
    result["sections"] = sections
    result["section_count"] = len(sections)
    result["categories"] = count_by(sections, "category")
    result["classifications"] = count_by(sections, "classification")
    return result


def count_by(sections: Sequence[dict[str, object]], key: str) -> dict[str, int]:
    counts: dict[str, int] = {}
    for section in sections:
        value = str(section.get(key, "unknown"))
        counts[value] = counts.get(value, 0) + 1
    return counts


def replay_fixtures(doc: dict[str, object]) -> dict[str, dict[str, object]]:
    fixtures = doc.get("fixtures")
    if isinstance(fixtures, list):
        return {
            str(fixture.get("fixture") or fixture.get("name")): fixture
            for fixture in fixtures
            if isinstance(fixture, dict)
        }
    fixture = doc.get("fixture")
    if isinstance(fixture, str):
        return {fixture: doc}
    return {}


def semantic_fingerprint(fixture: dict[str, object]) -> dict[str, object]:
    value = fixture.get("semantic_fingerprint")
    if isinstance(value, dict):
        return value
    raw_vertex = fixture.get("raw_vertex_hash")
    raw_index = fixture.get("raw_index_hash", "absent")
    return {
        "raw_vertex_hash": raw_vertex,
        "raw_index_hash": raw_index,
        "ordered_semantic_hash": fixture.get("ordered_semantic_hash", raw_vertex),
        "canonical_semantic_hash": fixture.get("canonical_semantic_hash", raw_vertex),
        "normalized_semantic_hash": fixture.get("normalized_semantic_hash", raw_vertex),
        "translucent_metadata_hash": fixture.get("translucent_metadata_hash", raw_index),
    }


def semantic_classification(current: dict[str, object] | None, frozen: dict[str, object] | None) -> str:
    if current is None or frozen is None:
        return "missing-output"
    if current.get("status") == "unsupported" and frozen.get("status") == "unsupported":
        return "unsupported"
    if current.get("status") == "unsupported" or frozen.get("status") == "unsupported":
        return "unsupported-mismatch"
    current_semantic = semantic_fingerprint(current)
    frozen_semantic = semantic_fingerprint(frozen)
    if current_semantic.get("raw_vertex_hash") == frozen_semantic.get("raw_vertex_hash") and (
        current_semantic.get("raw_index_hash") == frozen_semantic.get("raw_index_hash")
    ):
        return "byte-identical"
    semantic_fields = ("canonical_semantic_hash", "normalized_semantic_hash", "translucent_metadata_hash")
    if all(current_semantic.get(field) == frozen_semantic.get(field) for field in semantic_fields):
        if current_semantic.get("ordered_semantic_hash") == frozen_semantic.get("ordered_semantic_hash"):
            return "semantic-identical-encoding-different"
        return "semantic-identical-order-different"
    return "semantic-mismatch"


def first_mismatch(current: dict[str, object] | None, frozen: dict[str, object] | None) -> dict[str, object] | None:
    if current is None or frozen is None:
        return {"kind": "missing-output", "current_present": current is not None, "frozen_present": frozen is not None}
    current_semantic = semantic_fingerprint(current)
    frozen_semantic = semantic_fingerprint(frozen)
    for field in (
        "raw_vertex_hash",
        "raw_index_hash",
        "ordered_semantic_hash",
        "canonical_semantic_hash",
        "normalized_semantic_hash",
        "translucent_metadata_hash",
    ):
        if current_semantic.get(field) != frozen_semantic.get(field):
            mismatch = {
                "kind": field,
                "current": current_semantic.get(field),
                "frozen": frozen_semantic.get(field),
                "current_counts": pass_counts(current),
                "frozen_counts": pass_counts(frozen),
                "decoded_report_limit": "semantic field hashes are reported; full vertex dumps intentionally omitted from CLI output",
            }
            current_fields = current_semantic.get("field_hashes")
            frozen_fields = frozen_semantic.get("field_hashes")
            if isinstance(current_fields, dict) and isinstance(frozen_fields, dict):
                field_mismatches = []
                for name in ("position", "color", "texture", "light_material"):
                    if current_fields.get(name) != frozen_fields.get(name):
                        field_mismatches.append(
                            {
                                "field": name,
                                "current": current_fields.get(name),
                                "frozen": frozen_fields.get(name),
                            }
                        )
                mismatch["field_mismatches"] = field_mismatches
            return mismatch
    return None


def pass_counts(fixture: dict[str, object]) -> dict[str, object]:
    value = fixture.get("pass_material_counts")
    if isinstance(value, dict):
        return value
    return {
        "solid_quads": fixture.get("solid_quads", 0),
        "cutout_quads": fixture.get("cutout_quads", 0),
        "translucent_quads": fixture.get("translucent_quads", 0),
    }


def raw_input_for_section(section: dict[str, object] | None) -> dict[str, object] | None:
    if not isinstance(section, dict):
        return None
    payload = section.get("payload")
    if not isinstance(payload, dict):
        return None
    inner = payload.get("payload")
    if not isinstance(inner, dict):
        return None
    raw = inner.get("raw_input")
    return raw if isinstance(raw, dict) else None


def f32(value: float) -> float:
    return struct.unpack("<f", struct.pack("<f", float(value)))[0]


def f32_bits(value: float) -> str:
    return f"0x{struct.unpack('<I', struct.pack('<f', f32(value)))[0]:08x}"


def int_bits_to_f32(value: object) -> float:
    return struct.unpack("<f", struct.pack("<I", int(value) & 0xffff_ffff))[0]


def quantize_position(value: float) -> int:
    normalized = f32(f32(8.0 + f32(value)) / 32.0)
    return int(f32(normalized * 1_048_576.0)) & 0x0f_ffff


def pack_position(values: tuple[float, float, float]) -> dict[str, object]:
    x = quantize_position(values[0])
    y = quantize_position(values[1])
    z = quantize_position(values[2])
    return {
        "x20": x,
        "y20": y,
        "z20": z,
        "hi": ((x >> 10) & 0x3ff) | (((y >> 10) & 0x3ff) << 10) | (((z >> 10) & 0x3ff) << 20),
        "lo": (x & 0x3ff) | ((y & 0x3ff) << 10) | ((z & 0x3ff) << 20),
    }


def model_offset_diagnostics(raw: dict[str, object] | None, limit: int = 16) -> dict[str, object] | None:
    if raw is None:
        return None
    state_table = {
        int(entry.get("index", entry.get("id", i))): str(entry.get("state", entry.get("key", entry)))
        for i, entry in enumerate(raw.get("state_table", []))
        if isinstance(entry, dict)
    }
    models = {
        int(model.get("state_table")): model
        for model in raw.get("model_bundle", [])
        if isinstance(model, dict) and "state_table" in model
    }
    records: list[dict[str, object]] = []
    missing_offset_metadata = []
    for block in raw.get("active_blocks", []):
        if not isinstance(block, dict):
            continue
        state_table_id = int(block.get("state_table", -1))
        model = models.get(state_table_id)
        if not model:
            continue
        offset_type = model.get("offset_type")
        if offset_type is None:
            if model.get("state_key") not in missing_offset_metadata:
                missing_offset_metadata.append(model.get("state_key"))
            continue
        if int(offset_type) == 0:
            continue
        offset = (
            f32(float(block.get("model_offset_x", 0.0))),
            f32(float(block.get("model_offset_y", 0.0))),
            f32(float(block.get("model_offset_z", 0.0))),
        )
        if offset == (0.0, 0.0, 0.0):
            continue
        local = (int(block.get("x", 0)), int(block.get("y", 0)), int(block.get("z", 0)))
        world = (
            int(raw.get("origin_x", 0)) + local[0],
            int(raw.get("origin_y", 0)) + local[1],
            int(raw.get("origin_z", 0)) + local[2],
        )
        quads = model.get("quads", [])
        if not isinstance(quads, list):
            continue
        for quad_index, quad in enumerate(quads):
            if not isinstance(quad, dict):
                continue
            words = quad.get("vertices", [])
            if not isinstance(words, list) or len(words) < 32:
                continue
            quad_deltas: list[tuple[float, float, float]] = []
            for vertex in range(4):
                word_offset = vertex * 8
                base = (
                    int_bits_to_f32(words[word_offset]),
                    int_bits_to_f32(words[word_offset + 1]),
                    int_bits_to_f32(words[word_offset + 2]),
                )
                rust_before = (
                    f32(f32(float(local[0])) + base[0]),
                    f32(f32(float(local[1])) + base[1]),
                    f32(f32(float(local[2])) + base[2]),
                )
                java_live = (
                    f32(f32(f32(float(local[0])) + offset[0]) + base[0]),
                    f32(f32(f32(float(local[1])) + offset[1]) + base[1]),
                    f32(f32(f32(float(local[2])) + offset[2]) + base[2]),
                )
                delta = (
                    f32(java_live[0] - rust_before[0]),
                    f32(java_live[1] - rust_before[1]),
                    f32(java_live[2] - rust_before[2]),
                )
                quad_deltas.append(delta)
                records.append(
                    {
                        "block_local": {"x": local[0], "y": local[1], "z": local[2]},
                        "block_world": {"x": world[0], "y": world[1], "z": world[2]},
                        "block_state": model.get("state_key", state_table.get(state_table_id, state_table_id)),
                        "native_state_id": model.get("native_state_id"),
                        "model_id": model.get("model_id"),
                        "selector_id": model.get("selector_id"),
                        "selector_kind": model.get("selector_kind"),
                        "selected_child": model.get("model_id"),
                        "render_pass": model.get("pass"),
                        "material_bits": quad.get("material_bits", model.get("material_bits")),
                        "cull_face": quad.get("cull_face"),
                        "normal_face": quad.get("normal_face"),
                        "quad_index": quad_index,
                        "vertex_index": vertex,
                        "captured_base_model_position": {
                            "x": base[0],
                            "y": base[1],
                            "z": base[2],
                            "x_bits": f32_bits(base[0]),
                            "y_bits": f32_bits(base[1]),
                            "z_bits": f32_bits(base[2]),
                        },
                        "captured_model_offset": {
                            "type": offset_type,
                            "type_name": model.get("offset_type_name"),
                            "x": offset[0],
                            "y": offset[1],
                            "z": offset[2],
                            "max_horizontal": model.get("max_horizontal_offset"),
                            "max_vertical": model.get("max_vertical_offset"),
                        },
                        "java_live_or_frozen_replay_position": {
                            "float": {"x": java_live[0], "y": java_live[1], "z": java_live[2]},
                            "bits": {"x": f32_bits(java_live[0]), "y": f32_bits(java_live[1]), "z": f32_bits(java_live[2])},
                            "packed": pack_position(java_live),
                        },
                        "rust_replay_before_fix_position": {
                            "float": {"x": rust_before[0], "y": rust_before[1], "z": rust_before[2]},
                            "bits": {"x": f32_bits(rust_before[0]), "y": f32_bits(rust_before[1]), "z": f32_bits(rust_before[2])},
                            "packed": pack_position(rust_before),
                        },
                        "absolute_delta": {"x": abs(delta[0]), "y": abs(delta[1]), "z": abs(delta[2])},
                    }
                )
                if len(records) >= limit:
                    break
            uniform_delta = len(set(quad_deltas)) == 1 if quad_deltas else False
            for record in records[-len(quad_deltas):]:
                record["uniform_translation_delta_for_quad"] = uniform_delta
            if len(records) >= limit:
                break
        if len(records) >= limit:
            break
    return {
        "schema": "mattmc-position-offset-diagnostics-v1",
        "fixture": raw.get("fixture"),
        "records_are_bounded_to": limit,
        "classification": "missing-state-offset-metadata" if missing_offset_metadata and not records else "state-model-offset-translation",
        "missing_offset_metadata_state_keys": missing_offset_metadata,
        "records": records,
    }


def compare_replay_outputs(current_path: Path, frozen_path: Path, corpus_path: Path | None = None) -> dict[str, object]:
    current_doc = json.loads(current_path.read_text(encoding="utf-8"))
    frozen_doc = json.loads(frozen_path.read_text(encoding="utf-8"))
    current = replay_fixtures(current_doc)
    frozen = replay_fixtures(frozen_doc)
    corpus_sections = {}
    if corpus_path is not None:
        corpus_sections = {
            str(section.get("name")): section
            for section in summarize_corpus(corpus_path).get("sections", [])
            if isinstance(section, dict)
        }
    names = sorted(set(current) | set(frozen) | set(corpus_sections))
    rows: list[dict[str, object]] = []
    failures = 0
    for name in names:
        current_fixture = current.get(name)
        frozen_fixture = frozen.get(name)
        classification = semantic_classification(current_fixture, frozen_fixture)
        if classification not in {
            "byte-identical",
            "semantic-identical-encoding-different",
            "semantic-identical-order-different",
            "unsupported",
        }:
            failures += 1
        row = {
            "fixture": name,
            "category": corpus_sections.get(name, {}).get("category", classify_name(name)),
            "corpus_classification": corpus_sections.get(name, {}).get("classification"),
            "comparison": classification,
            "current_timing": timing_summary(current_fixture),
            "frozen_timing": timing_summary(frozen_fixture),
            "current_pass_counts": pass_counts(current_fixture or {}),
            "frozen_pass_counts": pass_counts(frozen_fixture or {}),
            "current_semantic_fingerprint": semantic_fingerprint(current_fixture or {}),
            "frozen_semantic_fingerprint": semantic_fingerprint(frozen_fixture or {}),
            "first_mismatch": first_mismatch(current_fixture, frozen_fixture),
        }
        mismatch = row["first_mismatch"]
        if (
            isinstance(mismatch, dict)
            and any(
                field.get("field") == "position"
                for field in mismatch.get("field_mismatches", [])
                if isinstance(field, dict)
            )
        ):
            row["position_mismatch_diagnostics"] = model_offset_diagnostics(
                raw_input_for_section(corpus_sections.get(name))
            )
        rows.append(row)
    return {
        "schema": "mattmc-meshing-corpus-compare-v1",
        "created_at": utc_now(),
        "current_output": str(current_path),
        "frozen_output": str(frozen_path),
        "corpus": str(corpus_path) if corpus_path else None,
        "fixture_count": len(rows),
        "failure_count": failures,
        "rows": rows,
    }


def timing_summary(fixture: dict[str, object] | None) -> dict[str, object]:
    if not fixture:
        return {}
    return {
        "cold_full_nanos": fixture.get("cold_full_nanos"),
        "cold_core_nanos": fixture.get("cold_core_nanos"),
        "warm_full_median_nanos": fixture.get("full_nanos", fixture.get("median_ns")),
        "warm_core_median_nanos": fixture.get("core_nanos"),
        "prepare_nanos": fixture.get("prepare_nanos"),
        "samples": fixture.get("measurement_iterations", fixture.get("iterations")),
    }


def command_output(command: Sequence[str], cwd: Path, timeout_seconds: int = 30) -> str:
    completed = subprocess.run(
        command,
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=timeout_seconds,
        check=False,
    )
    return completed.stdout.strip()


def file_identity(path: Path) -> dict[str, object] | None:
    if not path.is_file():
        return None
    data = path.read_bytes()
    stat = path.stat()
    return {
        "path": str(path),
        "sha256": hashlib.sha256(data).hexdigest(),
        "bytes": len(data),
        "modified_utc": datetime.fromtimestamp(stat.st_mtime, timezone.utc).isoformat(),
    }


def native_library_identity(root: Path) -> dict[str, object] | None:
    native_dir = root / "build" / "rust" / "native"
    if not native_dir.is_dir():
        return None
    candidates = sorted(
        [
            path
            for path in native_dir.iterdir()
            if path.suffix.lower() in {".dll", ".so", ".dylib"}
        ],
        key=lambda path: path.stat().st_mtime,
        reverse=True,
    )
    return file_identity(candidates[0]) if candidates else None


def repository_identity(root: Path, target: str) -> dict[str, object]:
    return {
        "target": target,
        "path": str(root),
        "commit": command_output(["git", "rev-parse", "HEAD"], root),
        "status_short": command_output(["git", "status", "--short"], root),
        "java_version": command_output(["java", "-version"], root),
        "gradle_version": command_output([*gradle_wrapper(root), "-v"], root, timeout_seconds=120),
        "native_library": native_library_identity(root),
    }


def corpus_identity(corpus: Path, expected_sha256: str) -> dict[str, object]:
    summary = summarize_corpus(corpus)
    actual_sha256 = str(summary["sha256"])
    if expected_sha256 and actual_sha256.lower() != expected_sha256.lower():
        raise ValueError(f"Corpus SHA-256 mismatch: expected {expected_sha256}, got {actual_sha256}")
    if summary.get("schema_version") != SCHEMA_VERSION:
        raise ValueError(f"Expected corpus schema version {SCHEMA_VERSION}, got {summary.get('schema_version')}")
    supported = [
        section
        for section in summary.get("sections", [])
        if isinstance(section, dict) and section.get("category") in SUPPORTED_PERF_CATEGORIES
    ]
    unsupported_perf = [
        section.get("name")
        for section in summary.get("sections", [])
        if isinstance(section, dict) and section.get("category") == "empty"
    ]
    return {
        "path": str(corpus),
        "sha256": actual_sha256,
        "schema_version": summary.get("schema_version"),
        "section_count": summary.get("section_count"),
        "categories": summary.get("categories"),
        "included_fixtures": [section.get("name") for section in supported],
        "excluded_from_performance": unsupported_perf,
        "summary": summary,
    }


def replay_fixture_map(doc: dict[str, object]) -> dict[str, dict[str, object]]:
    return {
        name: fixture
        for name, fixture in replay_fixtures(doc).items()
        if fixture.get("status") == "ok"
    }


def fixture_samples(fixture: dict[str, object], key: str) -> list[float]:
    value = fixture.get(key)
    if not isinstance(value, list):
        return []
    return [float(sample) for sample in value if isinstance(sample, (int, float))]


def median_float(values: Sequence[float]) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    middle = len(ordered) // 2
    if len(ordered) % 2:
        return ordered[middle]
    return (ordered[middle - 1] + ordered[middle]) / 2.0


def percentile(values: Sequence[float], pct: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = (len(ordered) - 1) * pct
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[int(position)]
    weight = position - lower
    return ordered[lower] * (1.0 - weight) + ordered[upper] * weight


def sample_stats(values: Sequence[float]) -> dict[str, object]:
    if not values:
        return {"samples": 0}
    mean = sum(values) / len(values)
    variance = sum((value - mean) ** 2 for value in values) / len(values)
    stddev = math.sqrt(variance)
    return {
        "samples": len(values),
        "min_nanos": min(values),
        "max_nanos": max(values),
        "mean_nanos": mean,
        "stddev_nanos": stddev,
        "coefficient_of_variation": stddev / mean if mean else None,
        "p50_nanos": percentile(values, 0.50),
        "p75_nanos": percentile(values, 0.75),
        "p90_nanos": percentile(values, 0.90),
        "p95_nanos": percentile(values, 0.95),
        "p99_nanos": percentile(values, 0.99),
    }


def ratio(numerator: float | None, denominator: float | None) -> float | None:
    if numerator is None or denominator in (None, 0):
        return None
    return numerator / denominator


def drift(warmup: Sequence[float]) -> dict[str, object]:
    if len(warmup) < 6:
        return {"samples": len(warmup), "status": "insufficient-warmup-samples"}
    fifth = max(1, len(warmup) // 5)
    middle_start = max(0, (len(warmup) // 2) - (fifth // 2))
    early = median_float(warmup[:fifth])
    middle = median_float(warmup[middle_start:middle_start + fifth])
    late = median_float(warmup[-fifth:])
    return {
        "samples": len(warmup),
        "first_20_percent_median_nanos": early,
        "middle_20_percent_median_nanos": middle,
        "final_20_percent_median_nanos": late,
        "late_over_early_ratio": ratio(late, early),
        "drift_percent": ((late - early) / early * 100.0) if early else None,
    }


def classify_fixture(full_ratio: float | None, core_ratio: float | None, fork_consistency: dict[str, int]) -> str:
    if full_ratio is None or core_ratio is None:
        return "statistically inconclusive"
    stable = max(fork_consistency.values(), default=0) >= 4
    full_faster = full_ratio < 0.95
    full_slower = full_ratio > 1.05
    core_faster = core_ratio < 0.95
    core_slower = core_ratio > 1.05
    if not stable or (not full_faster and not full_slower and not core_faster and not core_slower):
        return "statistically inconclusive"
    if full_faster and core_faster:
        return "Rust faster in full and core"
    if full_slower and core_faster:
        return "Rust faster in core but slower in full"
    if full_faster and core_slower:
        return "Rust slower in core but faster in full"
    if full_slower and core_slower:
        return "Rust slower in both"
    return "statistically inconclusive"


def fixture_weights(section: dict[str, object]) -> dict[str, float]:
    return {
        "equal_section": 1.0,
        "non_air_blocks": float(section.get("non_air_blocks") or 0),
        "emitted_quads": float(section.get("emitted_quads") or 0),
        "fluid_blocks": float(section.get("fluid_blocks") or 0),
    }


def weighted_ratio(rows: Sequence[dict[str, object]], key: str, timing_key: str) -> float | None:
    current_total = 0.0
    frozen_total = 0.0
    for row in rows:
        weight = float(row.get("weights", {}).get(key, 0.0))
        current = row.get("current", {}).get(timing_key, {}).get("p50_nanos")
        frozen = row.get("frozen", {}).get(timing_key, {}).get("p50_nanos")
        if not weight or current is None or frozen is None:
            continue
        current_total += weight * float(current)
        frozen_total += weight * float(frozen)
    return ratio(current_total, frozen_total)


def aggregate_rows(rows: Sequence[dict[str, object]], categories: set[str] | None = None) -> dict[str, object]:
    selected = [row for row in rows if categories is None or row.get("category") in categories]
    return {
        "fixtures": [row.get("fixture") for row in selected],
        "fixture_count": len(selected),
        "equal_section_full_current_over_frozen": weighted_ratio(selected, "equal_section", "full"),
        "equal_section_core_current_over_frozen": weighted_ratio(selected, "equal_section", "core"),
        "non_air_weighted_full_current_over_frozen": weighted_ratio(selected, "non_air_blocks", "full"),
        "quad_weighted_full_current_over_frozen": weighted_ratio(selected, "emitted_quads", "full"),
        "fluid_block_weighted_full_current_over_frozen": weighted_ratio(selected, "fluid_blocks", "full"),
    }


def build_fixture_rows(
    corpus_summary: dict[str, object],
    comparisons: dict[str, dict[str, object]],
    fork_docs: Sequence[dict[str, dict[str, object]]],
) -> list[dict[str, object]]:
    sections = {
        str(section.get("name")): section
        for section in corpus_summary.get("sections", [])
        if isinstance(section, dict)
    }
    rows: list[dict[str, object]] = []
    for name, section in sorted(sections.items()):
        if section.get("category") not in SUPPORTED_PERF_CATEGORIES:
            continue
        current_full: list[float] = []
        current_core: list[float] = []
        current_warm_full: list[float] = []
        frozen_full: list[float] = []
        frozen_core: list[float] = []
        frozen_warm_full: list[float] = []
        fork_consistency = {
            "rust_full_faster": 0,
            "rust_core_faster": 0,
            "rust_full_slower": 0,
            "rust_core_slower": 0,
        }
        cold = {"current_full_nanos": [], "frozen_full_nanos": [], "current_core_nanos": [], "frozen_core_nanos": []}
        prepare = {"current_nanos": [], "frozen_nanos": []}
        actual_samples = {"current": [], "frozen": []}
        for fork in fork_docs:
            current = replay_fixture_map(fork["current"]).get(name)
            frozen = replay_fixture_map(fork["frozen"]).get(name)
            if not current or not frozen:
                continue
            current_full.extend(fixture_samples(current, "raw_full_times_ns"))
            current_core.extend(fixture_samples(current, "raw_core_times_ns"))
            current_warm_full.extend(fixture_samples(current, "warmup_raw_full_times_ns"))
            frozen_full.extend(fixture_samples(frozen, "raw_full_times_ns"))
            frozen_core.extend(fixture_samples(frozen, "raw_core_times_ns"))
            frozen_warm_full.extend(fixture_samples(frozen, "warmup_raw_full_times_ns"))
            cold["current_full_nanos"].append(float(current.get("cold_full_nanos", 0)))
            cold["frozen_full_nanos"].append(float(frozen.get("cold_full_nanos", 0)))
            cold["current_core_nanos"].append(float(current.get("cold_core_nanos", 0)))
            cold["frozen_core_nanos"].append(float(frozen.get("cold_core_nanos", 0)))
            prepare["current_nanos"].append(float(current.get("prepare_nanos", 0)))
            prepare["frozen_nanos"].append(float(frozen.get("prepare_nanos", 0)))
            actual_samples["current"].append(int(current.get("actual_measurement_iterations",
                    current.get("measurement_iterations", 0))))
            actual_samples["frozen"].append(int(frozen.get("actual_measurement_iterations",
                    frozen.get("measurement_iterations", 0))))
            current_full_median = median_float(fixture_samples(current, "raw_full_times_ns"))
            frozen_full_median = median_float(fixture_samples(frozen, "raw_full_times_ns"))
            current_core_median = median_float(fixture_samples(current, "raw_core_times_ns"))
            frozen_core_median = median_float(fixture_samples(frozen, "raw_core_times_ns"))
            if current_full_median is not None and frozen_full_median is not None:
                fork_consistency["rust_full_faster" if current_full_median < frozen_full_median else "rust_full_slower"] += 1
            if current_core_median is not None and frozen_core_median is not None:
                fork_consistency["rust_core_faster" if current_core_median < frozen_core_median else "rust_core_slower"] += 1
        current_full_stats = sample_stats(current_full)
        frozen_full_stats = sample_stats(frozen_full)
        current_core_stats = sample_stats(current_core)
        frozen_core_stats = sample_stats(frozen_core)
        full_ratio = ratio(current_full_stats.get("p50_nanos"), frozen_full_stats.get("p50_nanos"))
        core_ratio = ratio(current_core_stats.get("p50_nanos"), frozen_core_stats.get("p50_nanos"))
        non_air_blocks = float(section.get("non_air_blocks") or 0)
        emitted_quads = float(section.get("emitted_quads") or 0)
        fluid_blocks = float(section.get("fluid_blocks") or 0)
        comparison = comparisons.get(name, {})
        rows.append(
            {
                "fixture": name,
                "category": section.get("category"),
                "semantic_classification": comparison.get("comparison"),
                "classification": classify_fixture(full_ratio, core_ratio, fork_consistency),
                "current_over_frozen_full_ratio": full_ratio,
                "current_over_frozen_core_ratio": core_ratio,
                "frozen_over_current_full_speedup": ratio(frozen_full_stats.get("p50_nanos"), current_full_stats.get("p50_nanos")),
                "frozen_over_current_core_speedup": ratio(frozen_core_stats.get("p50_nanos"), current_core_stats.get("p50_nanos")),
                "current": {
                    "full": current_full_stats,
                    "core": current_core_stats,
                    "warmup_full": drift(current_warm_full),
                    "measurement_full_drift": drift(current_full),
                    "measurement_core_drift": drift(current_core),
                },
                "frozen": {
                    "full": frozen_full_stats,
                    "core": frozen_core_stats,
                    "warmup_full": drift(frozen_warm_full),
                    "measurement_full_drift": drift(frozen_full),
                    "measurement_core_drift": drift(frozen_core),
                },
                "cold": {key: sample_stats(values) for key, values in cold.items()},
                "prepare": {key: sample_stats(values) for key, values in prepare.items()},
                "actual_measurement_samples_by_fork": actual_samples,
                "fork_consistency": fork_consistency,
                "weights": fixture_weights(section),
                "non_air_blocks": section.get("non_air_blocks"),
                "model_blocks": int(section.get("non_air_blocks") or 0) - int(section.get("fluid_blocks") or 0),
                "fluid_blocks": section.get("fluid_blocks"),
                "emitted_quads": section.get("emitted_quads"),
                "estimated_output_vertex_bytes": int(section.get("emitted_quads") or 0) * 4 * COMPACT_VERTEX_BYTES,
                "estimated_output_index_bytes": int(section.get("emitted_quads") or 0) * QUAD_INDEX_BYTES,
                "estimated_output_total_bytes": int(section.get("emitted_quads") or 0) * ((4 * COMPACT_VERTEX_BYTES) + QUAD_INDEX_BYTES),
                "full_nanos_per_non_air_block": ratio(current_full_stats.get("p50_nanos"), non_air_blocks),
                "full_nanos_per_emitted_quad": ratio(current_full_stats.get("p50_nanos"), emitted_quads),
                "full_nanos_per_fluid_block": ratio(current_full_stats.get("p50_nanos"), fluid_blocks),
            }
        )
    return rows


def validate_semantics_for_benchmark(args: argparse.Namespace, artifact_dir: Path) -> dict[str, object]:
    validation_dir = artifact_dir / "validation"
    current_output = validation_dir / "current.json"
    frozen_output = validation_dir / "frozen.json"
    compare_output = validation_dir / "compare.json"
    current_doc = run_replay(
        repo_root(),
        args.input.resolve(),
        current_output,
        warmup=1,
        measure=1,
        fixture="",
        timeout_seconds=args.timeout_seconds,
        gradle_args=args.gradle_arg,
        log_path=validation_dir / "current.log",
    )
    frozen_doc = run_replay(
        args.repo.resolve(),
        args.input.resolve(),
        frozen_output,
        warmup=1,
        measure=1,
        fixture="",
        timeout_seconds=args.timeout_seconds,
        gradle_args=args.frozen_gradle_arg,
        log_path=validation_dir / "frozen.log",
    )
    comparison = compare_replay_outputs(current_output, frozen_output, args.input.resolve())
    write_summary(comparison, compare_output)
    measured_failures = [
        row
        for row in comparison.get("rows", [])
        if isinstance(row, dict)
        and row.get("category") in SUPPORTED_PERF_CATEGORIES
        and row.get("comparison") not in {"byte-identical", "semantic-identical-encoding-different", "semantic-identical-order-different"}
    ]
    if measured_failures:
        raise RuntimeError(f"Semantic validation failed for measured fixtures: {[row.get('fixture') for row in measured_failures]}")
    return {
        "current_output": str(current_output),
        "frozen_output": str(frozen_output),
        "compare_output": str(compare_output),
        "failure_count": comparison.get("failure_count"),
        "measured_failure_count": len(measured_failures),
        "comparison_counts": count_values(row.get("comparison") for row in comparison.get("rows", []) if isinstance(row, dict)),
        "current_doc": current_doc,
        "frozen_doc": frozen_doc,
        "comparison": comparison,
    }


def count_values(values: Iterable[object]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for value in values:
        key = str(value)
        counts[key] = counts.get(key, 0) + 1
    return counts


def run_benchmark(args: argparse.Namespace) -> dict[str, object]:
    artifact_dir = args.artifact_dir or (
        repo_root() / "logs" / "perf-audit" / "meshing-corpus" / ("benchmark-" + datetime.now().strftime("%Y%m%d-%H%M%S"))
    )
    artifact_dir.mkdir(parents=True, exist_ok=True)
    corpus = corpus_identity(args.input.resolve(), args.expected_sha256)
    preflight = {
        "current_release_native_rebuild": run_logged_simple(
            [*gradle_wrapper(repo_root()), "-PmattmcRustProfile=release", "buildRustNative", "--no-daemon"],
            repo_root(),
            args.timeout_seconds,
            artifact_dir / "preflight" / "current-buildRustNative.log",
        ),
        "frozen_test_prepare": run_logged_simple(
            [*gradle_wrapper(args.repo.resolve()), "test", "--tests", DEFAULT_REPLAY_TEST, "--no-daemon"],
            args.repo.resolve(),
            args.timeout_seconds,
            artifact_dir / "preflight" / "frozen-test-prepare.log",
        ),
    }
    semantic = validate_semantics_for_benchmark(args, artifact_dir)
    identities = {
        "host": {
            "platform": platform.platform(),
            "processor": platform.processor(),
            "python": sys.version,
        },
        "current": repository_identity(repo_root(), "current"),
        "frozen": repository_identity(args.repo.resolve(), "frozen"),
    }
    fork_runs: list[dict[str, object]] = []
    fork_docs: list[dict[str, dict[str, object]]] = []
    for fork in range(1, args.forks + 1):
        order = ["current", "frozen"] if fork % 2 else ["frozen", "current"]
        docs: dict[str, dict[str, object]] = {}
        run_record: dict[str, object] = {
            "fork": fork,
            "order": order,
            "started_at": utc_now(),
            "runs": [],
        }
        for target in order:
            target_dir = artifact_dir / f"fork-{fork:02d}" / target
            repo = repo_root() if target == "current" else args.repo.resolve()
            output = target_dir / "replay.json"
            gradle_args = args.gradle_arg if target == "current" else args.frozen_gradle_arg
            doc = run_replay(
                repo,
                args.input.resolve(),
                output,
                args.warmup,
                args.measure,
                "",
                args.timeout_seconds,
                gradle_args,
                args.warmup_seconds,
                args.measure_seconds,
                target_dir / "gradle.log",
            )
            docs[target] = doc
            run_record["runs"].append(
                {
                    "target": target,
                    "output": str(output),
                    "log": str(target_dir / "gradle.log"),
                    "status": doc.get("status"),
                    "fixture_count": len(replay_fixtures(doc)),
                }
            )
        run_record["ended_at"] = utc_now()
        fork_runs.append(run_record)
        fork_docs.append(docs)
    diagnostic_output = artifact_dir / "diagnostics" / "current.json"
    diagnostic = run_replay(
        repo_root(),
        args.input.resolve(),
        diagnostic_output,
        warmup=0,
        measure=1,
        fixture="",
        timeout_seconds=args.timeout_seconds,
        gradle_args=[*args.gradle_arg, "-PmattmcMeshingCorpusReplayDiagnostics=true"],
        log_path=artifact_dir / "diagnostics" / "current.log",
    )
    comparison_rows = {
        str(row.get("fixture")): row
        for row in semantic["comparison"].get("rows", [])
        if isinstance(row, dict)
    }
    rows = build_fixture_rows(corpus["summary"], comparison_rows, fork_docs)
    summary = {
        "schema": "mattmc-production-equivalent-meshing-benchmark-v1",
        "created_at": utc_now(),
        "artifact_dir": str(artifact_dir),
        "settings": {
            "forks": args.forks,
            "warmup_min_invocations_per_fixture": args.warmup,
            "measurement_min_invocations_per_fixture": args.measure,
            "warmup_seconds_per_fixture": args.warmup_seconds,
            "measurement_seconds_per_fixture": args.measure_seconds,
            "timeout_seconds_per_replay": args.timeout_seconds,
            "excluded_categories": ["empty"],
            "production_full_scope": "corpus section already decoded in memory -> rebuild repository-native per-section input (Java LevelSlice view or Rust compact snapshot) -> production meshing -> final CPU mesh",
            "production_core_scope": "prepared repository-native per-section input -> production meshing -> final CPU mesh",
            "excluded_from_timed_scopes": "corpus disk I/O, decompression, JSON/schema parsing, JVM startup, class loading, native DLL loading, semantic hashing, and report generation",
            "timed_scope_note": "Global fixture model/state tables and native static model cache registration are prepared outside timing; full samples rebuild per-section view/snapshot each invocation.",
        },
        "corpus": {key: value for key, value in corpus.items() if key != "summary"},
        "preflight": preflight,
        "identities": identities,
        "semantic_validation": {key: value for key, value in semantic.items() if key not in {"current_doc", "frozen_doc", "comparison"}},
        "fork_runs": fork_runs,
        "fixture_results": rows,
        "aggregates": {
            "all_valid": aggregate_rows(rows),
            "model_heavy": aggregate_rows(rows, MODEL_HEAVY_CATEGORIES),
            "fluid_translucent": aggregate_rows(rows, FLUID_HEAVY_CATEGORIES | {"translucent-heavy"}),
        },
        "diagnostics": {
            "current_output": str(diagnostic_output),
            "current_log": str(artifact_dir / "diagnostics" / "current.log"),
            "rust_native_profiles_present": any("native_profile" in fixture for fixture in replay_fixtures(diagnostic).values()),
            "frozen_java_stage_profiles": "not available in the current frozen replay adapter",
        },
        "outliers_and_exclusions": {
            "excluded_fixtures": corpus["excluded_from_performance"],
            "outliers_discarded": 0,
            "policy": "No timing sample is discarded by the benchmark harness.",
        },
    }
    write_summary(summary, artifact_dir / "benchmark-summary.json")
    return summary


def repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def gradle_wrapper(root: Path) -> list[str]:
    if os.name == "nt":
        return [str(root / "gradlew.bat")]
    return [str(root / "gradlew")]


def popen_kwargs() -> dict[str, object]:
    if os.name == "nt":
        return {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP}
    return {"start_new_session": True}


def terminate_process_tree(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    if os.name == "nt":
        subprocess.run(
            ["taskkill", "/PID", str(process.pid), "/T", "/F"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
        return
    try:
        os.killpg(process.pid, 15)
        time.sleep(2)
        if process.poll() is None:
            os.killpg(process.pid, 9)
    except ProcessLookupError:
        pass


def run_command(command: Sequence[str], cwd: Path, timeout_seconds: int, env: dict[str, str] | None = None) -> None:
    started = time.time()
    completed = subprocess.run(command, cwd=cwd, text=True, timeout=timeout_seconds, env=env, check=False)
    if completed.returncode != 0:
        raise RuntimeError(f"Command failed with exit code {completed.returncode}: {' '.join(command)}")
    if time.time() - started > timeout_seconds:
        raise RuntimeError(f"Command exceeded timeout: {' '.join(command)}")


def run_logged_simple(command: Sequence[str], cwd: Path, timeout_seconds: int, log_path: Path) -> dict[str, object]:
    log_path.parent.mkdir(parents=True, exist_ok=True)
    started = time.time()
    with log_path.open("w", encoding="utf-8") as log:
        log.write("cwd: " + str(cwd) + "\ncommand: " + " ".join(command) + "\n\n")
        log.flush()
        process = subprocess.Popen(
            command,
            cwd=cwd,
            text=True,
            stdout=log,
            stderr=subprocess.STDOUT,
            **popen_kwargs(),
        )
        timed_out = False
        try:
            exit_code = process.wait(timeout=timeout_seconds)
        except subprocess.TimeoutExpired:
            timed_out = True
            terminate_process_tree(process)
            exit_code = process.wait(timeout=30)
    ended = time.time()
    if timed_out:
        raise RuntimeError(f"Command timed out after {timeout_seconds}s: {' '.join(command)}")
    if exit_code != 0:
        raise RuntimeError(f"Command failed with exit code {exit_code}: {' '.join(command)}")
    return {
        "command": list(command),
        "cwd": str(cwd),
        "log": str(log_path),
        "exit_code": exit_code,
        "started_at": datetime.fromtimestamp(started, timezone.utc).isoformat(),
        "ended_at": datetime.fromtimestamp(ended, timezone.utc).isoformat(),
        "duration_seconds": ended - started,
    }


def run_replay(repo: Path, corpus: Path, output: Path, warmup: int, measure: int, fixture: str, timeout_seconds: int,
        gradle_args: Sequence[str], warmup_seconds: float = 0.0, measure_seconds: float = 0.0,
        log_path: Path | None = None) -> dict[str, object]:
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()
    command = [
        *gradle_wrapper(repo),
        f"-PmattmcMeshingCorpusReplayInput={corpus}",
        f"-PmattmcMeshingCorpusReplayOutput={output}",
        f"-PmattmcMeshingCorpusReplayWarmup={warmup}",
        f"-PmattmcMeshingCorpusReplayMeasure={measure}",
        f"-PmattmcMeshingCorpusReplayWarmupSeconds={warmup_seconds}",
        f"-PmattmcMeshingCorpusReplayMeasureSeconds={measure_seconds}",
    ]
    if fixture:
        command.append(f"-PmattmcMeshingCorpusReplayFixture={fixture}")
    if (repo / "src" / "main" / "rust" / "Cargo.toml").is_file():
        command.append("-PmattmcRustProfile=release")
    command.extend(["test", "--tests", DEFAULT_REPLAY_TEST, "--no-build-cache", "--no-daemon", *gradle_args])
    started = time.time()
    if log_path:
        log_path.parent.mkdir(parents=True, exist_ok=True)
    with (log_path.open("w", encoding="utf-8") if log_path else contextlib.nullcontext(subprocess.DEVNULL)) as log:
        if log_path:
            log.write("cwd: " + str(repo) + "\ncommand: " + " ".join(command) + "\n\n")
            log.flush()
        process = subprocess.Popen(
            command,
            cwd=repo,
            text=True,
            stdout=log,
            stderr=subprocess.STDOUT,
            **popen_kwargs(),
        )
        try:
            exit_code = process.wait(timeout=timeout_seconds)
            timed_out = False
        except subprocess.TimeoutExpired:
            terminate_process_tree(process)
            exit_code = process.wait(timeout=30)
            timed_out = True
    if timed_out:
        raise RuntimeError(f"Replay command timed out after {timeout_seconds}s: {' '.join(command)}")
    if exit_code != 0:
        raise RuntimeError(f"Replay command failed with exit code {exit_code}: {' '.join(command)}")
    if not output.is_file():
        raise RuntimeError(f"Replay command succeeded but did not create output: {output}")
    if output.stat().st_mtime < started:
        raise RuntimeError(f"Replay output was not refreshed; Gradle may have considered stale output up-to-date: {output}")
    return json.loads(output.read_text(encoding="utf-8"))


def write_summary(summary: dict[str, object], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def display_summary(summary: dict[str, object]) -> dict[str, object]:
    visible = dict(summary)
    sections = []
    for section in summary.get("sections", []):
        if not isinstance(section, dict):
            continue
        payload = section.get("payload")
        payload_doc = payload if isinstance(payload, dict) else {}
        raw_input = section_payload(payload_doc).get("raw_input") if isinstance(section_payload(payload_doc), dict) else None
        unsupported = raw_input.get("unsupported") if isinstance(raw_input, dict) else None
        sections.append(
            {
                "name": section.get("name"),
                "category": section.get("category"),
                "classification": section.get("classification"),
                "non_air_blocks": section.get("non_air_blocks"),
                "fluid_blocks": section.get("fluid_blocks"),
                "emitted_quads": section.get("emitted_quads"),
                "fallback_blocks": payload_doc.get("fallback_blocks"),
                "fallback_quads": payload_doc.get("fallback_quads"),
                "canonical_hash": payload_doc.get("canonical_hash"),
                "unsupported_count": len(unsupported) if isinstance(unsupported, list) else 0,
            }
        )
    visible["sections"] = sections
    return visible


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Create, replay, validate, and compare MattMC chunk meshing corpus files.")
    sub = parser.add_subparsers(dest="command", required=True)
    create = sub.add_parser("from-real-replay", help="Convert RealChunkMeshingReplayRunner JSON to a corpus file")
    create.add_argument("--input", required=True, type=Path)
    create.add_argument("--output", required=True, type=Path)
    create.add_argument("--summary", type=Path)
    capture = sub.add_parser("capture", help="Capture a corpus through the internal graphics capture engine and convert it to .mmcm")
    capture.add_argument("--output", required=True, type=Path)
    capture.add_argument("--summary", type=Path)
    capture.add_argument("--fixture", default="", help="fixture name; empty captures the runner's full fixture set")
    capture.add_argument("--warmup", type=int, default=2)
    capture.add_argument("--measure", type=int, default=3)
    capture.add_argument("--timeout-seconds", type=int, default=300)
    capture.add_argument("--artifact-dir", type=Path)
    capture.add_argument("--keep-replay-json", type=Path)
    for name in ("inspect", "validate"):
        command = sub.add_parser(name, help="Validate, filter, and summarize a corpus file")
        command.add_argument("--input", required=True, type=Path)
        command.add_argument("--summary", type=Path)
        command.add_argument("--fixture", default="")
        command.add_argument("--category", default="")
        command.add_argument("--replayable-only", action="store_true")
    replay_current = sub.add_parser("replay-current", help="Replay a corpus through this checkout's headless replay test")
    replay_current.add_argument("--input", required=True, type=Path)
    replay_current.add_argument("--output", required=True, type=Path)
    replay_current.add_argument("--fixture", default="")
    replay_current.add_argument("--warmup", type=int, default=8)
    replay_current.add_argument("--measure", type=int, default=25)
    replay_current.add_argument("--warmup-seconds", type=float, default=0.0)
    replay_current.add_argument("--measure-seconds", type=float, default=0.0)
    replay_current.add_argument("--timeout-seconds", type=int, default=900)
    replay_current.add_argument("--gradle-arg", action="append", default=[])
    replay_frozen = sub.add_parser("replay-frozen", help="Replay a corpus through the frozen Java checkout")
    replay_frozen.add_argument("--input", required=True, type=Path)
    replay_frozen.add_argument("--output", required=True, type=Path)
    replay_frozen.add_argument("--repo", type=Path, required=True)
    replay_frozen.add_argument("--fixture", default="")
    replay_frozen.add_argument("--warmup", type=int, default=8)
    replay_frozen.add_argument("--measure", type=int, default=25)
    replay_frozen.add_argument("--warmup-seconds", type=float, default=0.0)
    replay_frozen.add_argument("--measure-seconds", type=float, default=0.0)
    replay_frozen.add_argument("--timeout-seconds", type=int, default=900)
    replay_frozen.add_argument("--gradle-arg", action="append", default=[])
    compare = sub.add_parser("compare", help="Compare current and frozen corpus replay outputs")
    compare.add_argument("--current", required=True, type=Path)
    compare.add_argument("--frozen", required=True, type=Path)
    compare.add_argument("--corpus", type=Path)
    compare.add_argument("--output", type=Path)
    compare.add_argument("--allow-mismatch", action="store_true")
    benchmark = sub.add_parser("benchmark", help="Validate and run alternating current-vs-frozen corpus replay forks")
    benchmark.add_argument("--input", required=True, type=Path)
    benchmark.add_argument("--repo", type=Path, required=True, help="frozen Java comparison repository")
    benchmark.add_argument("--artifact-dir", type=Path)
    benchmark.add_argument("--expected-sha256", default="")
    benchmark.add_argument("--forks", type=int, default=5)
    benchmark.add_argument("--warmup", type=int, default=20)
    benchmark.add_argument("--measure", type=int, default=30)
    benchmark.add_argument("--warmup-seconds", type=float, default=10.0)
    benchmark.add_argument("--measure-seconds", type=float, default=15.0)
    benchmark.add_argument("--timeout-seconds", type=int, default=3600)
    benchmark.add_argument("--gradle-arg", action="append", default=[])
    benchmark.add_argument("--frozen-gradle-arg", action="append", default=[])
    args = parser.parse_args(list(argv) if argv is not None else None)

    try:
        if args.command == "from-real-replay":
            summary = write_corpus_from_real_replay(args.input, args.output)
            if args.summary:
                write_summary(summary, args.summary)
            print(json.dumps(display_summary(summary), indent=2, sort_keys=True))
        elif args.command == "capture":
            replay_json = args.keep_replay_json or args.output.with_suffix(".real_replay.json")
            command = [
                sys.executable,
                str(repo_root() / "DevUtils" / "Common" / "capture_runner.py"),
                "--backend",
                "opengl",
                "--shaders",
                "off",
                "--max-secs",
                str(args.timeout_seconds),
                "--skip-tests",
                "--capture-meshing-corpus",
                "--meshing-corpus-output",
                str(replay_json),
                "--meshing-corpus-warmup",
                str(args.warmup),
                "--meshing-corpus-measure",
                str(args.measure),
            ]
            if args.fixture:
                command.extend(["--meshing-corpus-fixture", args.fixture])
            if args.artifact_dir:
                command.extend(["--artifact-dir", str(args.artifact_dir)])
            env = os.environ.copy()
            env["MATTMC_GRAPHICS_TOOL_INTERNAL"] = "1"
            run_command(command, repo_root(), args.timeout_seconds + 120, env=env)
            summary = write_corpus_from_real_replay(replay_json, args.output)
            if args.summary:
                write_summary(summary, args.summary)
            print(json.dumps(display_summary(summary), indent=2, sort_keys=True))
        elif args.command in {"inspect", "validate"}:
            summary = filtered_summary(summarize_corpus(args.input), args.fixture, args.category, args.replayable_only)
            if args.summary:
                write_summary(summary, args.summary)
            print(json.dumps(display_summary(summary), indent=2, sort_keys=True))
        elif args.command == "replay-current":
            doc = run_replay(repo_root(), args.input, args.output, args.warmup, args.measure, args.fixture,
                    args.timeout_seconds, args.gradle_arg, args.warmup_seconds, args.measure_seconds)
            print(json.dumps(doc, indent=2, sort_keys=True))
        elif args.command == "replay-frozen":
            doc = run_replay(args.repo.resolve(), args.input.resolve(), args.output.resolve(), args.warmup, args.measure,
                    args.fixture, args.timeout_seconds, args.gradle_arg, args.warmup_seconds, args.measure_seconds)
            print(json.dumps(doc, indent=2, sort_keys=True))
        elif args.command == "compare":
            doc = compare_replay_outputs(args.current, args.frozen, args.corpus)
            if args.output:
                write_summary(doc, args.output)
            print(json.dumps(doc, indent=2, sort_keys=True))
            if doc["failure_count"] and not args.allow_mismatch:
                return 2
        elif args.command == "benchmark":
            if args.forks < 1:
                raise ValueError("--forks must be at least 1")
            doc = run_benchmark(args)
            print(json.dumps({key: value for key, value in doc.items() if key != "fixture_results"}, indent=2, sort_keys=True))
        return 0
    except Exception as exc:
        print(f"meshing corpus error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
