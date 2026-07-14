#!/usr/bin/env python3
"""
VulkanParityAudit - source and capture parity auditing for MattMC.

This tool is intentionally evidence-first:
  * source-audit classifies remaining GL-shaped architecture pressure points
  * diff-captures compares OpenGL/Vulkan ShaderInputParity logs semantically
  * auto-diff finds the newest compatible OpenGL/Vulkan RunDevCapture pair

The diff is strict about shader input payloads, but it normalizes backend object
identities and binding-number churn so real semantic mismatches are easier to see.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
SRC_MAIN = ROOT / "src" / "main" / "java"
AUTO_CAPTURE = ROOT / "logs" / "auto-capture"

GEOMETRY_SAMPLER_AWARE_PIPELINES = {
    "minecraft:pipeline/gui_text",
    "minecraft:pipeline/gui_textured",
    "minecraft:pipeline/entity_cutout",
    "minecraft:pipeline/entity_cutout_no_cull",
    "minecraft:pipeline/entity_translucent",
}

SOURCE_ROOTS = [
    SRC_MAIN / "net" / "minecraft",
    SRC_MAIN / "net" / "irisshaders",
    SRC_MAIN / "net" / "sodium",
    SRC_MAIN / "com" / "seibel",
    SRC_MAIN / "net" / "vulkanic",
]

GL_BACKEND_PATH_PARTS = {
    ("net", "vulkanic", "backends", "opengl"),
    ("net", "blaze3d", "opengl"),
}

IDENTITY_PATTERNS = [
    (re.compile(r"@[0-9a-fA-F]+"), "@<identity>"),
    (re.compile(r"\b(bufferId|viewId|id)=([0-9a-fA-F]{6,}|<identity>)"), r"\1=<identity>"),
    (re.compile(r"\bpipelineHandle=[^ ]+"), "pipelineHandle=<identity>"),
    (re.compile(r"\bpipelineKey=[0-9a-fA-F]+"), "pipelineKey=<pipeline-key>"),
]

HASH_RE = re.compile(r"(payloadHash|rangeHash)=([^,}\]]+)")
TOP_FIELD_RE = re.compile(r"(?:^|\s)([A-Za-z][A-Za-z0-9_]*)=(\"[^\"]*\"|\S+)")
STRUCT_FIELD_RE = re.compile(r"\b([A-Za-z][A-Za-z0-9_]*)(?:=|:)(\"[^\"]*\"|[^,{}\]\s]+)")


@dataclass
class CaptureMeta:
    run_id: str
    backend: str
    path: Path
    latest_log: Path | None = None
    client_args: str = ""
    enable_shaders: str = ""
    shader_pack: str = ""

    @property
    def matching_key(self) -> tuple[str, str, str]:
        return (self.client_args, self.enable_shaders, self.shader_pack)


@dataclass
class ResourceRecord:
    backend: str
    source: str
    pipeline_location: str
    vertex_shader: str
    fragment_shader: str
    pipeline_key: str
    stable_key: str
    name: str
    resource_type: str
    raw: str
    binding: str = ""
    stages: str = ""
    payload_hash: str = ""
    range_hash: str = ""
    length: str = ""
    size: str = ""
    unit: str = ""
    texture_label: str = ""
    texture_format: str = ""
    texture_width: str = ""
    texture_height: str = ""
    texture_mips: str = ""
    texture_usage: str = ""
    texture_min_filter: str = ""
    texture_mag_filter: str = ""
    texture_uses_mipmaps: str = ""
    texture_wrap_u: str = ""
    texture_wrap_v: str = ""
    texture_compare: str = ""
    texture_swizzle: str = ""
    texture_srgb_interpretation: str = ""
    content_hash: str = ""
    content_hash_region: str = ""
    content_hash_format: str = ""
    content_hash_storage_format: str = ""
    content_hash_tiles: str = ""
    det_pose: str = ""
    det_rendered_frame: str = ""
    semantic_draw_key: str = ""
    semantic_subsystem: str = ""
    semantic_phase: str = ""
    semantic_pass: str = ""
    semantic_pipeline: str = ""
    semantic_material: str = ""
    semantic_output: str = ""
    semantic_ordinal: str = ""
    projection_label: str = ""
    projection_context: str = ""

    @property
    def semantic_key(self) -> tuple[str, str, str, str]:
        name = self.name
        if self.name == "Projection" and self.projection_label:
            name = f"{self.name}@{self.projection_label}"
        if self.content_hash and self.det_pose:
            name = f"{name}@pose:{self.det_pose}@source:{self.source or 'unknown'}"
        context_parts: list[str] = []
        if self.semantic_draw_key and self.semantic_draw_key != "unavailable":
            context_parts.append(f"draw:{self.semantic_draw_key}")
        else:
            if self.det_pose:
                context_parts.append(f"pose:{self.det_pose}")
            if self.det_rendered_frame:
                context_parts.append(f"frame:{self.det_rendered_frame}")
            if self.semantic_subsystem:
                context_parts.append(f"subsystem:{self.semantic_subsystem}")
            if self.semantic_pass:
                context_parts.append(f"pass:{self.semantic_pass}")
            if self.semantic_pipeline:
                context_parts.append(f"semanticPipeline:{self.semantic_pipeline}")
            if self.semantic_material:
                context_parts.append(f"material:{self.semantic_material}")
            if self.semantic_output:
                context_parts.append(f"output:{self.semantic_output}")
            if self.semantic_ordinal:
                context_parts.append(f"ordinal:{self.semantic_ordinal}")
        if context_parts:
            name = f"{name}@{'/'.join(context_parts)}"
        pipeline_identity = self.pipeline_location or self.stable_key
        return (pipeline_identity, self.stable_key, name, self.resource_type)

    @property
    def ubo_payload_signature(self) -> str:
        if self.payload_hash:
            return self.payload_hash
        return normalize_identity(self.raw)

    @property
    def sampler_signature(self) -> str:
        label = normalize_texture_label(self.texture_label)
        return "|".join([
            f"unit={self.unit}",
            f"label={label}",
            f"format={self.texture_format}",
            f"size={self.texture_width}x{self.texture_height}",
            f"mips={self.texture_mips}",
            f"usage={self.texture_usage}",
            f"minFilter={self.texture_min_filter or 'unknown'}",
            f"magFilter={self.texture_mag_filter or 'unknown'}",
            f"mipmaps={self.texture_uses_mipmaps or 'unknown'}",
            f"wrapU={self.texture_wrap_u or 'unknown'}",
            f"wrapV={self.texture_wrap_v or 'unknown'}",
            f"compare={self.texture_compare or 'unknown'}",
            f"swizzle={self.texture_swizzle or 'unknown'}",
            f"srgb={self.texture_srgb_interpretation or 'unknown'}",
        ])

    @property
    def sampler_shader_visible_signature(self) -> str:
        return "|".join([
            f"unit={self.unit}",
            f"format={self.texture_format}",
            f"size={self.texture_width}x{self.texture_height}",
            f"mips={self.texture_mips}",
            f"minFilter={self.texture_min_filter or 'unknown'}",
            f"magFilter={self.texture_mag_filter or 'unknown'}",
            f"mipmaps={self.texture_uses_mipmaps or 'unknown'}",
            f"wrapU={self.texture_wrap_u or 'unknown'}",
            f"wrapV={self.texture_wrap_v or 'unknown'}",
            f"compare={self.texture_compare or 'unknown'}",
            f"swizzle={self.texture_swizzle or 'unknown'}",
            f"srgb={self.texture_srgb_interpretation or 'unknown'}",
        ])

    @property
    def sampler_signature_without_usage(self) -> str:
        label = normalize_texture_label(self.texture_label)
        return "|".join([
            f"unit={self.unit}",
            f"label={label}",
            f"format={self.texture_format}",
            f"size={self.texture_width}x{self.texture_height}",
            f"mips={self.texture_mips}",
            f"minFilter={self.texture_min_filter or 'unknown'}",
            f"magFilter={self.texture_mag_filter or 'unknown'}",
            f"mipmaps={self.texture_uses_mipmaps or 'unknown'}",
            f"wrapU={self.texture_wrap_u or 'unknown'}",
            f"wrapV={self.texture_wrap_v or 'unknown'}",
            f"compare={self.texture_compare or 'unknown'}",
            f"swizzle={self.texture_swizzle or 'unknown'}",
            f"srgb={self.texture_srgb_interpretation or 'unknown'}",
        ])

    @property
    def short_signature(self) -> str:
        if "SAMPLER" in self.resource_type:
            return self.sampler_signature
        if self.resource_type == "UNIFORM_BUFFER":
            return self.ubo_payload_signature
        return normalize_identity(self.raw)


@dataclass
class UniformBufferEvent:
    backend: str
    name: str
    raw: str
    payload_hash: str = ""
    range_hash: str = ""
    length: str = ""

    @property
    def signature(self) -> str:
        return self.payload_hash if self.payload_hash else normalize_identity(self.raw)


@dataclass
class StandaloneUniformEvent:
    backend: str
    source: str
    name: str
    value_kind: str
    component_count: str
    raw: str
    payload_hash: str = ""
    sample: str = ""
    program_identity: str = ""
    shader_stages: str = ""
    location: str = ""
    render_phase: str = ""
    draw_key: str = ""
    det_pose: str = ""
    det_rendered_frame: str = ""
    semantic_draw_key: str = ""
    semantic_subsystem: str = ""
    semantic_phase: str = ""
    semantic_pass: str = ""
    semantic_pipeline: str = ""
    semantic_material: str = ""
    semantic_output: str = ""
    semantic_ordinal: str = ""

    @property
    def key(self) -> str:
        return standalone_uniform_key(self)

    @property
    def signature(self) -> str:
        return self.payload_hash if self.payload_hash else normalize_identity(self.raw)


@dataclass
class StandaloneUniformBlockMemberEvent:
    backend: str
    source: str
    name: str
    value_kind: str
    component_count: str
    raw: str
    payload_hash: str = ""
    sample: str = ""
    offset: str = ""
    array_size: str = ""
    stride: str = ""
    program_identity: str = ""
    shader_stages: str = ""
    location: str = ""
    render_phase: str = ""
    draw_key: str = ""
    det_pose: str = ""
    det_rendered_frame: str = ""
    semantic_draw_key: str = ""
    semantic_subsystem: str = ""
    semantic_phase: str = ""
    semantic_pass: str = ""
    semantic_pipeline: str = ""
    semantic_material: str = ""
    semantic_output: str = ""
    semantic_ordinal: str = ""

    @property
    def key(self) -> str:
        return standalone_uniform_key(self)

    @property
    def signature(self) -> str:
        return self.payload_hash if self.payload_hash else normalize_identity(self.raw)


@dataclass
class VertexInputEvent:
    backend: str
    pipeline: str
    mode: str
    vertex_size: str
    elements_mask: str
    explicit_vertex_input: str

    @property
    def key(self) -> str:
        return self.pipeline

    @property
    def signature(self) -> str:
        return "|".join([
            f"mode={self.mode}",
            f"vertexSize={self.vertex_size}",
            f"elementsMask={self.elements_mask}",
            f"explicit={self.explicit_vertex_input}",
        ])


@dataclass
class DrawEvent:
    backend: str
    source: str
    render_phase: str
    indexed: str
    primitive: str
    mode: str
    first_vertex: str
    vertex_count: str
    first_index: str
    index_byte_offset: str
    index_count: str
    index_type: str
    instance_count: str
    base_vertex: str
    det_pose: str = ""
    det_rendered_frame: str = ""
    semantic_draw_key: str = ""
    semantic_subsystem: str = ""
    semantic_phase: str = ""
    semantic_pass: str = ""
    semantic_pipeline: str = ""
    semantic_material: str = ""
    semantic_output: str = ""
    semantic_ordinal: str = ""

    @property
    def family_key(self) -> str:
        return self.render_phase or "unknown"

    @property
    def semantic_group_key(self) -> str:
        if self.semantic_draw_key and self.semantic_draw_key != "unavailable":
            return self.semantic_draw_key
        return "unavailable"

    @property
    def signature(self) -> str:
        return "|".join([
            f"phase={self.render_phase or 'unknown'}",
            f"indexed={self.indexed}",
            f"primitive={self.primitive}",
            f"mode={self.mode}",
            f"firstVertex={self.first_vertex}",
            f"vertexCount={self.vertex_count}",
            f"firstIndex={self.first_index}",
            f"indexByteOffset={self.index_byte_offset}",
            f"indexCount={self.index_count}",
            f"indexType={self.index_type}",
            f"instances={self.instance_count}",
            f"baseVertex={self.base_vertex}",
            f"pose={self.det_pose or 'none'}",
        ])


@dataclass
class GeometryEvent:
    backend: str
    source: str
    semantic_draw_key: str
    semantic_subsystem: str
    semantic_phase: str
    semantic_pass: str
    semantic_pipeline: str
    semantic_vertex_shader: str
    semantic_fragment_shader: str
    semantic_material: str
    semantic_output: str
    semantic_ordinal: str
    mode: str
    indexed: str
    vertex_format: str
    vertex_stride: str
    layout_hash: str
    total_vertices: int
    total_indices: int
    total_primitives: int
    instances: int
    vertex_hash: str
    index_hash: str
    status: str
    reason: str
    detail: str = ""
    det_pose: str = ""
    det_rendered_frame: str = ""

    @property
    def semantic_match_signature(self) -> str:
        parts = [
            f"subsystem={self.semantic_subsystem or 'unknown'}",
            f"phase={self.semantic_phase or 'unknown'}",
            f"pass={self.semantic_pass or 'unknown'}",
            f"pipeline={self.semantic_pipeline or 'unknown'}",
            f"vertex={self.semantic_vertex_shader or 'unknown'}",
            f"fragment={self.semantic_fragment_shader or 'unknown'}",
            f"material={self.semantic_material or 'unknown'}",
            f"output={self.semantic_output or 'unknown'}",
            f"pose={self.det_pose or 'none'}",
            f"frame={self.det_rendered_frame or 'none'}",
        ]
        if self.semantic_subsystem == "sodium-terrain":
            parts.append(f"ordinal={self.semantic_ordinal or '0'}")
        return "|".join(parts)

    @property
    def comparable(self) -> bool:
        return self.status == "equivalent-candidate" and self.vertex_hash and self.vertex_hash != "unavailable"


@dataclass
class SemanticDrawEvent:
    backend: str
    source: str
    semantic_draw_key: str
    semantic_subsystem: str
    semantic_phase: str
    semantic_pass: str
    semantic_pipeline: str
    semantic_vertex_shader: str
    semantic_fragment_shader: str
    semantic_material: str
    semantic_output: str
    semantic_ordinal: str
    indexed: str
    first_vertex: str
    vertex_count: str
    first_index: str
    index_count: str
    instance_count: str
    base_vertex: str
    det_pose: str = ""
    det_rendered_frame: str = ""

    @property
    def semantic_group_key(self) -> str:
        return self.semantic_draw_key or "unavailable"

    @property
    def semantic_signature(self) -> str:
        return "|".join([
            f"subsystem={self.semantic_subsystem or 'unknown'}",
            f"phase={self.semantic_phase or 'unknown'}",
            f"pass={self.semantic_pass or 'unknown'}",
            f"pipeline={self.semantic_pipeline or 'unknown'}",
            f"vertex={self.semantic_vertex_shader or 'unknown'}",
            f"fragment={self.semantic_fragment_shader or 'unknown'}",
            f"material={self.semantic_material or 'unknown'}",
            f"output={self.semantic_output or 'unknown'}",
            f"ordinal={self.semantic_ordinal or '0'}",
            f"pose={self.det_pose or 'none'}",
            f"frame={self.det_rendered_frame or 'none'}",
        ])

    @property
    def semantic_match_signature(self) -> str:
        return "|".join([
            f"subsystem={self.semantic_subsystem or 'unknown'}",
            f"phase={self.semantic_phase or 'unknown'}",
            f"pass={self.semantic_pass or 'unknown'}",
            f"pipeline={self.semantic_pipeline or 'unknown'}",
            f"vertex={self.semantic_vertex_shader or 'unknown'}",
            f"fragment={self.semantic_fragment_shader or 'unknown'}",
            f"material={self.semantic_material or 'unknown'}",
            f"output={self.semantic_output or 'unknown'}",
            f"pose={self.det_pose or 'none'}",
            f"frame={self.det_rendered_frame or 'none'}",
        ])


@dataclass
class CaptureEvents:
    path: Path
    backend: str = "unknown"
    resources: dict[tuple[str, str, str, str], list[ResourceRecord]] = field(default_factory=lambda: defaultdict(list))
    uniform_buffers: dict[str, list[UniformBufferEvent]] = field(default_factory=lambda: defaultdict(list))
    standalone_uniforms: dict[str, list[StandaloneUniformEvent]] = field(default_factory=lambda: defaultdict(list))
    standalone_uniform_block_members: dict[str, list[StandaloneUniformBlockMemberEvent]] = field(default_factory=lambda: defaultdict(list))
    vertex_inputs: dict[str, list[VertexInputEvent]] = field(default_factory=lambda: defaultdict(list))
    draws: list[DrawEvent] = field(default_factory=list)
    semantic_draws: list[SemanticDrawEvent] = field(default_factory=list)
    geometry: list[GeometryEvent] = field(default_factory=list)
    counters: Counter = field(default_factory=Counter)
    skipped: Counter = field(default_factory=Counter)


@dataclass(frozen=True)
class ParseLimits:
    max_resource_events: int = 0
    max_uniform_buffer_events: int = 0
    max_standalone_uniform_events: int = 0
    max_standalone_uniform_block_member_events: int = 0
    max_vertex_input_events: int = 0
    max_draw_events: int = 0

    def reached(self, counter: Counter, key: str, limit: int) -> bool:
        return limit > 0 and counter[key] > limit


@dataclass
class Difference:
    severity: int
    category: str
    key: str
    reason: str
    opengl_count: int
    vulkan_count: int
    opengl_values: list[str]
    vulkan_values: list[str]


def normalize_identity(text: str) -> str:
    normalized = text
    for pattern, replacement in IDENTITY_PATTERNS:
        normalized = pattern.sub(replacement, normalized)
    return normalized


def normalize_texture_label(label: str) -> str:
    label = label.strip('"')
    if not label:
        return ""
    if label.isdigit():
        return "<numeric-label>"
    if label.startswith("Legacy texture "):
        return "Legacy texture <id>"
    if label.startswith("Legacy_texture_"):
        return "Legacy_texture_<id>"
    return label


def stable_digest(values: Iterable[str]) -> str:
    digest = hashlib.sha256()
    for value in sorted(set(values)):
        digest.update(value.encode("utf-8", "replace"))
        digest.update(b"\0")
    return digest.hexdigest()[:16]


def ordered_digest(values: Iterable[str]) -> str:
    digest = hashlib.sha256()
    count = 0
    for value in values:
        digest.update(value.encode("utf-8", "replace"))
        digest.update(b"\0")
        count += 1
    return f"{digest.hexdigest()[:16]}/events:{count}"


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def parse_top_fields(text: str) -> dict[str, str]:
    return {match.group(1): match.group(2).strip('"') for match in TOP_FIELD_RE.finditer(text)}


def parse_struct_fields(text: str) -> dict[str, str]:
    return {match.group(1): match.group(2).strip('"') for match in STRUCT_FIELD_RE.finditer(text)}


def extract_hashes(text: str) -> dict[str, str]:
    return {match.group(1): match.group(2) for match in HASH_RE.finditer(text)}


def int_or_zero(value: str) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def is_pre_pose_deterministic_record(fields: dict[str, str]) -> bool:
    return fields.get("detCapture") == "true" and fields.get("detAwaitingScreenshot") != "true"


def standalone_uniform_key(event: StandaloneUniformEvent | StandaloneUniformBlockMemberEvent) -> str:
    if event.semantic_draw_key and event.semantic_draw_key != "unavailable":
        return "|".join([
            f"draw={event.semantic_draw_key}",
            f"subsystem={event.semantic_subsystem or 'unknown'}",
            f"phase={event.semantic_phase or event.render_phase or 'unknown'}",
            f"pass={event.semantic_pass or 'unknown'}",
            f"pipeline={event.semantic_pipeline or 'unknown'}",
            f"material={event.semantic_material or 'unknown'}",
            f"output={event.semantic_output or 'unknown'}",
            f"stages={event.shader_stages or 'unknown'}",
            f"name={event.name}",
            f"type={event.value_kind}",
        ])
    return "|".join([
        f"program={event.program_identity or 'unknown'}",
        f"stages={event.shader_stages or 'unknown'}",
        f"phase={event.render_phase or 'unknown'}",
        f"draw={event.draw_key or 'unavailable'}",
        f"name={event.name}",
        f"type={event.value_kind}",
    ])


def extract_balanced_after(prefix: str, text: str) -> str:
    start = text.find(prefix)
    if start < 0:
        return ""
    start += len(prefix)
    if start >= len(text):
        return ""
    opener = text[start]
    closer = {"[": "]", "{": "}"}.get(opener)
    if closer is None:
        return ""
    depth = 0
    for index in range(start, len(text)):
        ch = text[index]
        if ch == opener:
            depth += 1
        elif ch == closer:
            depth -= 1
            if depth == 0:
                return text[start + 1:index]
    return text[start + 1:]


def split_top_level_resources(text: str) -> list[str]:
    resources: list[str] = []
    start = 0
    depth = 0
    for index, ch in enumerate(text):
        if ch in "[{(":
            depth += 1
        elif ch in "]})":
            depth -= 1
        elif ch == "," and depth == 0:
            part = text[start:index].strip()
            if part:
                resources.append(part)
            start = index + 1
    tail = text[start:].strip()
    if tail:
        resources.append(tail)
    return resources


def parse_resource(raw: str, backend: str, source: str, pipeline_key: str, stable_key: str, top_fields: dict[str, str] | None = None) -> ResourceRecord | None:
    match = re.match(r"([A-Za-z0-9_.$:-]+)\{(.*)\}$", raw.strip())
    if not match:
        return None
    top_fields = top_fields or {}
    name = match.group(1)
    body = match.group(2)
    fields = parse_struct_fields(body)
    hashes = extract_hashes(body)
    content_fields: dict[str, str] = {}
    content_body = extract_balanced_after("contentHash=", body)
    if content_body:
        content_fields = parse_struct_fields(content_body)
    top_context = " ".join(
        f"{field}={top_fields[field]}"
        for field in ("detCapture", "detPose", "detPoseIndex", "detRenderedFrame", "detAwaitingScreenshot", "detComplete", "detFailed")
        if field in top_fields
    )

    record = ResourceRecord(
        backend=backend,
        source=source,
        pipeline_location=top_fields.get("pipelineLocation", ""),
        vertex_shader=top_fields.get("vertexShader", ""),
        fragment_shader=top_fields.get("fragmentShader", ""),
        pipeline_key=pipeline_key,
        stable_key=stable_key,
        name=name,
        resource_type=fields.get("type", ""),
        raw=(top_context + " " + raw.strip()).strip(),
        binding=fields.get("binding", ""),
        payload_hash=hashes.get("payloadHash", ""),
        range_hash=hashes.get("rangeHash", ""),
        length=fields.get("length", ""),
        size=fields.get("size", ""),
        unit=fields.get("unit", ""),
        texture_label=fields.get("label", ""),
        texture_format=fields.get("format", ""),
        texture_width=fields.get("width", ""),
        texture_height=fields.get("height", ""),
        texture_mips=fields.get("mips", ""),
        texture_usage=fields.get("usage", ""),
        texture_min_filter=fields.get("minFilter", ""),
        texture_mag_filter=fields.get("magFilter", ""),
        texture_uses_mipmaps=fields.get("mipmaps", ""),
        texture_wrap_u=fields.get("wrapU", ""),
        texture_wrap_v=fields.get("wrapV", ""),
        texture_compare=fields.get("compare", ""),
        texture_swizzle=fields.get("swizzle", ""),
        texture_srgb_interpretation=fields.get("srgbInterpretation", ""),
        content_hash=content_fields.get("hash", ""),
        content_hash_region=content_fields.get("region", ""),
        content_hash_format=content_fields.get("canonicalFormat", ""),
        content_hash_storage_format=content_fields.get("storageFormat", ""),
        content_hash_tiles=content_fields.get("tileHashes", ""),
        det_pose=top_fields.get("detPose", ""),
        det_rendered_frame=top_fields.get("detRenderedFrame", ""),
        semantic_draw_key=top_fields.get("semanticDrawKey", ""),
        semantic_subsystem=top_fields.get("semanticSubsystem", ""),
        semantic_phase=top_fields.get("semanticPhase", ""),
        semantic_pass=top_fields.get("semanticPass", ""),
        semantic_pipeline=top_fields.get("semanticPipeline", ""),
        semantic_material=top_fields.get("semanticMaterial", ""),
        semantic_output=top_fields.get("semanticOutput", ""),
        semantic_ordinal=top_fields.get("semanticOrdinal", ""),
        projection_label=fields.get("projectionLabel", ""),
    )

    stages_match = re.search(r"stages=\[([^]]+)\]", body)
    if stages_match:
        stages = [part.strip() for part in stages_match.group(1).split(",")]
        record.stages = ",".join(sorted(stages))
    return record


def parse_capture_log(path: Path, limits: ParseLimits | None = None) -> CaptureEvents:
    limits = limits or ParseLimits()
    events = CaptureEvents(path=path)
    with path.open("r", encoding="utf-8", errors="replace") as handle:
        lines = handle
        for line in lines:
            parse_capture_line(line, events, limits)
    return events


def parse_capture_line(line: str, events: CaptureEvents, limits: ParseLimits) -> None:
    if "ShaderInputParity" not in line:
        return
    payload = line.split("ShaderInputParity", 1)[1]

    if payload.startswith("Resources "):
        events.counters["Resources"] += 1
        if limits.reached(events.counters, "Resources", limits.max_resource_events):
            events.skipped["Resources"] += 1
            return
        fields = parse_top_fields(payload)
        if is_pre_pose_deterministic_record(fields):
            events.skipped["ResourcesPrePose"] += 1
            return
        backend = fields.get("backend", "unknown")
        events.backend = backend
        source = fields.get("source", "")
        pipeline_key = fields.get("pipelineKey", "")
        stable_key = fields.get("stableKey", "")
        resources_text = extract_balanced_after("resources=", payload)
        records: list[ResourceRecord] = []
        for raw_resource in split_top_level_resources(resources_text):
            record = parse_resource(raw_resource, backend, source, pipeline_key, stable_key, fields)
            if record:
                records.append(record)
        projection_context = next((record.projection_label for record in records if record.name == "Projection" and record.projection_label), "")
        for record in records:
            record.projection_context = projection_context
            events.resources[record.semantic_key].append(record)

    elif payload.startswith("UniformBuffer "):
        events.counters["UniformBuffer"] += 1
        if limits.reached(events.counters, "UniformBuffer", limits.max_uniform_buffer_events):
            events.skipped["UniformBuffer"] += 1
            return
        fields = parse_top_fields(payload)
        slice_fields = parse_struct_fields(payload)
        hashes = extract_hashes(payload)
        backend = fields.get("backend", "unknown")
        events.backend = backend
        name = fields.get("name", "")
        event = UniformBufferEvent(
            backend=backend,
            name=name,
            raw=payload.strip(),
            payload_hash=hashes.get("payloadHash", ""),
            range_hash=hashes.get("rangeHash", ""),
            length=slice_fields.get("length", ""),
        )
        events.uniform_buffers[name].append(event)

    elif payload.startswith("StandaloneUniformBlockMember "):
        events.counters["StandaloneUniformBlockMember"] += 1
        if limits.reached(
            events.counters,
            "StandaloneUniformBlockMember",
            limits.max_standalone_uniform_block_member_events
        ):
            events.skipped["StandaloneUniformBlockMember"] += 1
            return
        fields = parse_top_fields(payload)
        if is_pre_pose_deterministic_record(fields):
            events.skipped["StandaloneUniformBlockMemberPrePose"] += 1
            return
        hashes = extract_hashes(payload)
        backend = fields.get("backend", "unknown")
        events.backend = backend
        name = fields.get("name", "")
        if not name or name == "unknown":
            return
        event = StandaloneUniformBlockMemberEvent(
            backend=backend,
            source=fields.get("source", ""),
            name=name,
            value_kind=fields.get("valueKind", ""),
            component_count=fields.get("componentCount", ""),
            raw=payload.strip(),
            payload_hash=hashes.get("payloadHash", ""),
            sample=fields.get("sample", ""),
            program_identity=fields.get("programIdentity", ""),
            shader_stages=fields.get("shaderStages", ""),
            location=fields.get("location", ""),
            render_phase=fields.get("renderPhase", ""),
            draw_key=fields.get("drawKey", ""),
            det_pose=fields.get("detPose", ""),
            det_rendered_frame=fields.get("detRenderedFrame", ""),
            semantic_draw_key=fields.get("semanticDrawKey", ""),
            semantic_subsystem=fields.get("semanticSubsystem", ""),
            semantic_phase=fields.get("semanticPhase", ""),
            semantic_pass=fields.get("semanticPass", ""),
            semantic_pipeline=fields.get("semanticPipeline", ""),
            semantic_material=fields.get("semanticMaterial", ""),
            semantic_output=fields.get("semanticOutput", ""),
            semantic_ordinal=fields.get("semanticOrdinal", ""),
            offset=fields.get("offset", ""),
            array_size=fields.get("arraySize", ""),
            stride=fields.get("stride", ""),
        )
        events.standalone_uniform_block_members[event.key].append(event)

    elif payload.startswith("StandaloneUniform "):
        events.counters["StandaloneUniform"] += 1
        if limits.reached(events.counters, "StandaloneUniform", limits.max_standalone_uniform_events):
            events.skipped["StandaloneUniform"] += 1
            return
        fields = parse_top_fields(payload)
        if is_pre_pose_deterministic_record(fields):
            events.skipped["StandaloneUniformPrePose"] += 1
            return
        hashes = extract_hashes(payload)
        backend = fields.get("backend", "unknown")
        events.backend = backend
        name = fields.get("name", "")
        if not name or name == "unknown":
            return
        event = StandaloneUniformEvent(
            backend=backend,
            source=fields.get("source", ""),
            name=name,
            value_kind=fields.get("valueKind", ""),
            component_count=fields.get("componentCount", ""),
            raw=payload.strip(),
            payload_hash=hashes.get("payloadHash", ""),
            sample=fields.get("sample", ""),
            program_identity=fields.get("programIdentity", ""),
            shader_stages=fields.get("shaderStages", ""),
            location=fields.get("location", ""),
            render_phase=fields.get("renderPhase", ""),
            draw_key=fields.get("drawKey", ""),
            det_pose=fields.get("detPose", ""),
            det_rendered_frame=fields.get("detRenderedFrame", ""),
            semantic_draw_key=fields.get("semanticDrawKey", ""),
            semantic_subsystem=fields.get("semanticSubsystem", ""),
            semantic_phase=fields.get("semanticPhase", ""),
            semantic_pass=fields.get("semanticPass", ""),
            semantic_pipeline=fields.get("semanticPipeline", ""),
            semantic_material=fields.get("semanticMaterial", ""),
            semantic_output=fields.get("semanticOutput", ""),
            semantic_ordinal=fields.get("semanticOrdinal", ""),
        )
        events.standalone_uniforms[event.key].append(event)

    elif payload.startswith("VertexInput "):
        events.counters["VertexInput"] += 1
        if limits.reached(events.counters, "VertexInput", limits.max_vertex_input_events):
            events.skipped["VertexInput"] += 1
            return
        fields = parse_top_fields(payload)
        backend = fields.get("backend", "unknown")
        events.backend = backend
        event = VertexInputEvent(
            backend=backend,
            pipeline=fields.get("pipeline", ""),
            mode=fields.get("mode", ""),
            vertex_size=fields.get("vertexSize", ""),
            elements_mask=fields.get("elementsMask", ""),
            explicit_vertex_input=fields.get("explicitVertexInput", ""),
        )
        events.vertex_inputs[event.key].append(event)

    elif payload.startswith("Draw "):
        events.counters["Draw"] += 1
        if limits.reached(events.counters, "Draw", limits.max_draw_events):
            events.skipped["Draw"] += 1
            return
        fields = parse_top_fields(payload)
        backend = fields.get("backend", "unknown")
        events.backend = backend
        events.draws.append(DrawEvent(
            backend=backend,
            source=fields.get("source", ""),
            render_phase=fields.get("renderPhase", ""),
            indexed=fields.get("indexed", ""),
            primitive=fields.get("primitive", ""),
            mode=fields.get("mode", ""),
            first_vertex=fields.get("firstVertex", ""),
            vertex_count=fields.get("vertexCount", ""),
            first_index=fields.get("firstIndex", ""),
            index_byte_offset=fields.get("indexByteOffset", ""),
            index_count=fields.get("indexCount", ""),
            index_type=fields.get("indexType", ""),
            instance_count=fields.get("instanceCount", ""),
            base_vertex=fields.get("baseVertex", ""),
            det_pose=fields.get("detPose", ""),
            det_rendered_frame=fields.get("detRenderedFrame", ""),
            semantic_draw_key=fields.get("semanticDrawKey", ""),
            semantic_subsystem=fields.get("semanticSubsystem", ""),
            semantic_phase=fields.get("semanticPhase", ""),
            semantic_pass=fields.get("semanticPass", ""),
            semantic_pipeline=fields.get("semanticPipeline", ""),
            semantic_material=fields.get("semanticMaterial", ""),
            semantic_output=fields.get("semanticOutput", ""),
            semantic_ordinal=fields.get("semanticOrdinal", ""),
        ))

    elif payload.startswith("SemanticDraw "):
        events.counters["SemanticDraw"] += 1
        fields = parse_top_fields(payload)
        backend = fields.get("backend", "unknown")
        events.backend = backend
        events.semantic_draws.append(SemanticDrawEvent(
            backend=backend,
            source=fields.get("source", ""),
            semantic_draw_key=fields.get("semanticDrawKey", ""),
            semantic_subsystem=fields.get("semanticSubsystem", ""),
            semantic_phase=fields.get("semanticPhase", ""),
            semantic_pass=fields.get("semanticPass", ""),
            semantic_pipeline=fields.get("semanticPipeline", ""),
            semantic_vertex_shader=fields.get("semanticVertexShader", ""),
            semantic_fragment_shader=fields.get("semanticFragmentShader", ""),
            semantic_material=fields.get("semanticMaterial", ""),
            semantic_output=fields.get("semanticOutput", ""),
            semantic_ordinal=fields.get("semanticOrdinal", ""),
            indexed=fields.get("indexed", ""),
            first_vertex=fields.get("firstVertex", ""),
            vertex_count=fields.get("vertexCount", ""),
            first_index=fields.get("firstIndex", ""),
            index_count=fields.get("indexCount", ""),
            instance_count=fields.get("instanceCount", ""),
            base_vertex=fields.get("baseVertex", ""),
            det_pose=fields.get("detPose", ""),
            det_rendered_frame=fields.get("detRenderedFrame", ""),
        ))

    elif payload.startswith("Geometry "):
        events.counters["Geometry"] += 1
        fields = parse_top_fields(payload)
        backend = fields.get("backend", "unknown")
        events.backend = backend
        events.geometry.append(GeometryEvent(
            backend=backend,
            source=fields.get("source", ""),
            semantic_draw_key=fields.get("semanticDrawKey", ""),
            semantic_subsystem=fields.get("semanticSubsystem", ""),
            semantic_phase=fields.get("semanticPhase", ""),
            semantic_pass=fields.get("semanticPass", ""),
            semantic_pipeline=fields.get("semanticPipeline", ""),
            semantic_vertex_shader=fields.get("semanticVertexShader", ""),
            semantic_fragment_shader=fields.get("semanticFragmentShader", ""),
            semantic_material=fields.get("semanticMaterial", ""),
            semantic_output=fields.get("semanticOutput", ""),
            semantic_ordinal=fields.get("semanticOrdinal", ""),
            mode=fields.get("mode", ""),
            indexed=fields.get("indexed", ""),
            vertex_format=fields.get("vertexFormat", ""),
            vertex_stride=fields.get("vertexStride", ""),
            layout_hash=fields.get("layoutHash", ""),
            total_vertices=int_or_zero(fields.get("totalVertices", "")),
            total_indices=int_or_zero(fields.get("totalIndices", "")),
            total_primitives=int_or_zero(fields.get("totalPrimitives", "")),
            instances=int_or_zero(fields.get("instances", "")),
            vertex_hash=fields.get("vertexHash", ""),
            index_hash=fields.get("indexHash", ""),
            status=fields.get("status", ""),
            reason=fields.get("reason", ""),
            detail=fields.get("detail", ""),
            det_pose=fields.get("detPose", ""),
            det_rendered_frame=fields.get("detRenderedFrame", ""),
        ))

    elif payload.startswith("Sampler "):
        events.counters["Sampler"] += 1
    elif payload.startswith("Uniform "):
        events.counters["Uniform"] += 1


def values_for_resources(records: list[ResourceRecord]) -> list[str]:
    return sorted({record.short_signature for record in records})


def values_for_content_hashes(records: list[ResourceRecord]) -> list[str]:
    return sorted({record.content_hash for record in records if record.content_hash})


def values_for_bindings(records: list[ResourceRecord]) -> list[str]:
    return sorted({record.binding for record in records if record.binding})


def values_for_lengths(records: list[ResourceRecord]) -> list[str]:
    return sorted({record.length for record in records if record.length})


def values_for_range_hashes(records: list[ResourceRecord]) -> list[str]:
    return sorted({record.range_hash for record in records if record.range_hash})


def values_for_payload_hashes(records: list[ResourceRecord]) -> list[str]:
    return sorted({record.payload_hash for record in records if record.payload_hash})


def values_for_comparable_payload_hashes(records: list[ResourceRecord]) -> list[str]:
    return sorted({
        record.payload_hash
        for record in records
        if record.payload_hash and not is_unavailable_hash(record.payload_hash)
    })


def values_for_unavailable_payload_hashes(records: list[ResourceRecord]) -> list[str]:
    return sorted({
        record.payload_hash
        for record in records
        if is_unavailable_hash(record.payload_hash)
    })


def is_unavailable_hash(value: str) -> bool:
    return value == "unavailable" or value.startswith("unavailable:")


def values_for_pipeline_keys(records: list[ResourceRecord]) -> list[str]:
    return sorted({record.pipeline_key for record in records if record.pipeline_key})


def semantic_draw_parameter_signatures(events: CaptureEvents) -> dict[str, set[str]]:
    signatures: dict[str, set[str]] = defaultdict(set)
    for event in events.semantic_draws:
        if not event.semantic_draw_key or event.semantic_draw_key == "unavailable":
            continue
        signatures[event.semantic_draw_key].add("|".join([
            f"pipeline={event.semantic_pipeline or 'unknown'}",
            f"pose={event.det_pose or 'none'}",
            f"frame={event.det_rendered_frame or 'none'}",
            f"indexed={event.indexed}",
            f"firstVertex={event.first_vertex}",
            f"vertexCount={event.vertex_count}",
            f"firstIndex={event.first_index}",
            f"indexCount={event.index_count}",
            f"instances={event.instance_count}",
            f"baseVertex={event.base_vertex}",
        ]))
    return signatures


def semantic_draw_keys_for_records(records: list[ResourceRecord]) -> set[str]:
    return {
        record.semantic_draw_key
        for record in records
        if record.semantic_draw_key and record.semantic_draw_key != "unavailable"
    }


def sampler_records_have_different_draw_parameters(
    gl_records: list[ResourceRecord],
    vk_records: list[ResourceRecord],
    gl_draw_parameters: dict[str, set[str]],
    vk_draw_parameters: dict[str, set[str]],
) -> bool:
    for draw_key in semantic_draw_keys_for_records(gl_records) & semantic_draw_keys_for_records(vk_records):
        if gl_draw_parameters.get(draw_key) != vk_draw_parameters.get(draw_key):
            return True
    return False


def is_gui_item_pip_ordinal_matching_gap(gl_records: list[ResourceRecord], vk_records: list[ResourceRecord]) -> bool:
    pipelines = {record.semantic_pipeline for record in gl_records + vk_records}
    if pipelines != {"minecraft:pipeline/gui_textured"}:
        return False
    gl_labels = {semantic_texture_label(record.texture_label) for record in gl_records}
    vk_labels = {semantic_texture_label(record.texture_label) for record in vk_records}
    item_pip_labels = {"UI_standard_3d_item_texture", "UI_oversized_item_texture"}
    if gl_labels == vk_labels:
        return False
    if gl_labels & item_pip_labels or vk_labels & item_pip_labels:
        return True
    # VoxelMap GUI quads are emitted in a different ordinal position once Vulkan inserts
    # item PIP blits, so ordinal-only matching can pair minimap/frame/arrow draws with
    # vanilla GUI atlas quads.
    gui_aux_labels = {"voxelmap-map-256", "Minimap_Square_Map_Frame", "Minimap_Arrow"}
    return bool((gl_labels | vk_labels) & gui_aux_labels)


def format_key(key: tuple[str, str, str, str]) -> str:
    pipeline_identity, stable_key, name, resource_type = key
    return f"pipeline={pipeline_identity} / stableKey={stable_key} / name={name} / type={resource_type}"


def compare_capture_events(opengl: CaptureEvents, vulkan: CaptureEvents) -> list[Difference]:
    differences: list[Difference] = []
    opengl_draw_parameters = semantic_draw_parameter_signatures(opengl)
    vulkan_draw_parameters = semantic_draw_parameter_signatures(vulkan)

    all_resource_keys = sorted(set(opengl.resources) | set(vulkan.resources))
    for key in all_resource_keys:
        gl_records = opengl.resources.get(key, [])
        vk_records = vulkan.resources.get(key, [])
        key_text = format_key(key)

        if not gl_records or not vk_records:
            if (
                key[2] == "VulkanicStandaloneUniforms"
                and vk_records
                and not gl_records
                and vulkan.standalone_uniform_block_members
            ):
                continue
            only = "OpenGL" if gl_records else "Vulkan"
            is_labeled_projection = key[2].startswith("Projection@")
            gl_content_hashes = values_for_content_hashes(gl_records)
            vk_content_hashes = values_for_content_hashes(vk_records)
            has_content_hash = bool(gl_content_hashes or vk_content_hashes)
            differences.append(Difference(
                severity=25 if is_labeled_projection else 62 if has_content_hash else 60,
                category=(
                    "backend-only-projection-context"
                    if is_labeled_projection
                    else "render-target-content-hash-backend-only"
                    if has_content_hash
                    else "backend-only-resource"
                ),
                key=key_text,
                reason=(
                    f"labeled projection context only observed on {only}; separate captures can legitimately visit different GUI/minimap contexts"
                    if is_labeled_projection
                    else f"render-target content-hash resource only observed on {only}"
                    if has_content_hash
                    else f"resource semantic key only observed on {only}"
                ),
                opengl_count=len(gl_records),
                vulkan_count=len(vk_records),
                opengl_values=(gl_content_hashes or values_for_resources(gl_records))[:4],
                vulkan_values=(vk_content_hashes or values_for_resources(vk_records))[:4],
            ))
            continue

        resource_type = key[3]
        gl_values = values_for_resources(gl_records)
        vk_values = values_for_resources(vk_records)
        if "SAMPLER" in resource_type:
            gl_content_hashes = values_for_content_hashes(gl_records)
            vk_content_hashes = values_for_content_hashes(vk_records)
            if gl_content_hashes or vk_content_hashes:
                if not gl_content_hashes or not vk_content_hashes:
                    differences.append(Difference(
                        severity=62,
                        category="render-target-content-hash-only-observed-on-one-backend",
                        key=key_text,
                        reason="render-target content hash diagnostics were only emitted for one backend/resource",
                        opengl_count=len(gl_records),
                        vulkan_count=len(vk_records),
                        opengl_values=gl_content_hashes[:4],
                        vulkan_values=vk_content_hashes[:4],
                    ))
                elif any(value.startswith("unavailable:") for value in gl_content_hashes + vk_content_hashes):
                    if set(gl_content_hashes) != set(vk_content_hashes):
                        differences.append(Difference(
                            severity=35,
                            category="render-target-content-hash-not-comparable",
                            key=key_text,
                            reason="at least one backend reported an explicit diagnostic readback limitation for this shader-visible texture",
                            opengl_count=len(gl_records),
                            vulkan_count=len(vk_records),
                            opengl_values=gl_content_hashes[:4],
                            vulkan_values=vk_content_hashes[:4],
                        ))
                elif set(gl_content_hashes) != set(vk_content_hashes):
                    differences.append(Difference(
                        severity=99,
                        category="strict-render-target-content-hash-mismatch",
                        key=key_text,
                        reason="same shader-visible render-target resource produced different normalized content hashes",
                        opengl_count=len(gl_records),
                        vulkan_count=len(vk_records),
                        opengl_values=gl_content_hashes[:4],
                        vulkan_values=vk_content_hashes[:4],
                    ))

        if resource_type == "UNIFORM_BUFFER":
            gl_payloads = values_for_comparable_payload_hashes(gl_records)
            vk_payloads = values_for_comparable_payload_hashes(vk_records)
            gl_unavailable = values_for_unavailable_payload_hashes(gl_records)
            vk_unavailable = values_for_unavailable_payload_hashes(vk_records)
            if gl_unavailable or vk_unavailable:
                differences.append(Difference(
                    severity=48,
                    category="ubo-payload-not-comparable",
                    key=key_text,
                    reason="same semantic UBO lacks shader-visible payload readback on at least one backend; this is a diagnostic coverage gap, not a proven data mismatch",
                    opengl_count=len(gl_records),
                    vulkan_count=len(vk_records),
                    opengl_values=[f"comparable={','.join(gl_payloads[:3]) or 'none'}", f"unavailable={','.join(gl_unavailable) or 'none'}"],
                    vulkan_values=[f"comparable={','.join(vk_payloads[:3]) or 'none'}", f"unavailable={','.join(vk_unavailable) or 'none'}"],
                ))
                continue
            if gl_payloads and vk_payloads and set(gl_payloads) != set(vk_payloads):
                gl_pipeline_keys = values_for_pipeline_keys(gl_records)
                vk_pipeline_keys = values_for_pipeline_keys(vk_records)
                if len(gl_pipeline_keys) > 1 or len(vk_pipeline_keys) > 1:
                    differences.append(Difference(
                        severity=50,
                        category="layout-ambiguous-ubo-payload-set-difference",
                        key=key_text,
                        reason="stable resource key is shared by multiple pipeline keys, so disjoint payloads may be different draws with the same layout",
                        opengl_count=len(gl_records),
                        vulkan_count=len(vk_records),
                        opengl_values=[f"pipelineKeys={len(gl_pipeline_keys)}", *gl_payloads[:4]],
                        vulkan_values=[f"pipelineKeys={len(vk_pipeline_keys)}", *vk_payloads[:4]],
                    ))
                    continue
                overlap = sorted(set(gl_payloads) & set(vk_payloads))
                if overlap:
                    differences.append(Difference(
                        severity=45,
                        category="timing-sensitive-ubo-payload-set-difference",
                        key=key_text,
                        reason="same semantic UBO has overlapping payload hashes plus backend-specific observations in separate captures",
                        opengl_count=len(gl_records),
                        vulkan_count=len(vk_records),
                        opengl_values=[f"shared={','.join(overlap[:3])}", *gl_payloads[:4]],
                        vulkan_values=[f"shared={','.join(overlap[:3])}", *vk_payloads[:4]],
                    ))
                    continue
                gl_contexts = sorted({record.projection_context for record in gl_records if record.projection_context})
                vk_contexts = sorted({record.projection_context for record in vk_records if record.projection_context})
                if key[2] == "DynamicTransforms" and set(gl_contexts) == set(vk_contexts) and any("cubemap" in context for context in gl_contexts):
                    differences.append(Difference(
                        severity=40,
                        category="timing-sensitive-dynamic-transform-mismatch",
                        key=key_text,
                        reason="DynamicTransforms are from the animated cubemap/panorama context; separate captures are not frame-synchronized",
                        opengl_count=len(gl_records),
                        vulkan_count=len(vk_records),
                        opengl_values=[f"context={','.join(gl_contexts)}", *gl_payloads[:4]],
                        vulkan_values=[f"context={','.join(vk_contexts)}", *vk_payloads[:4]],
                    ))
                    continue
                differences.append(Difference(
                    severity=100,
                    category="strict-ubo-payload-mismatch",
                    key=key_text,
                    reason="same semantic UBO has disjoint payload hash sets",
                    opengl_count=len(gl_records),
                    vulkan_count=len(vk_records),
                    opengl_values=gl_payloads[:4],
                    vulkan_values=vk_payloads[:4],
                ))
            elif not gl_payloads or not vk_payloads:
                gl_normalized = sorted({normalize_identity(record.raw) for record in gl_records})
                vk_normalized = sorted({normalize_identity(record.raw) for record in vk_records})
                if set(gl_normalized) != set(vk_normalized):
                    differences.append(Difference(
                        severity=55,
                        category="unhashed-ubo-metadata-difference",
                        key=key_text,
                        reason="same semantic UBO lacks payload hashes on at least one backend; metadata differs",
                        opengl_count=len(gl_records),
                        vulkan_count=len(vk_records),
                        opengl_values=gl_normalized[:4],
                        vulkan_values=vk_normalized[:4],
                    ))
            else:
                gl_lengths = values_for_lengths(gl_records)
                vk_lengths = values_for_lengths(vk_records)
                gl_ranges = values_for_range_hashes(gl_records)
                vk_ranges = values_for_range_hashes(vk_records)
                if set(gl_lengths) != set(vk_lengths) or set(gl_ranges) != set(vk_ranges):
                    differences.append(Difference(
                        severity=30,
                        category="ubo-range-metadata-difference",
                        key=key_text,
                        reason="semantic payload hash matches, but bound range length or range hash differs",
                        opengl_count=len(gl_records),
                        vulkan_count=len(vk_records),
                        opengl_values=[f"payload={','.join(gl_payloads)}", f"length={','.join(gl_lengths)}", f"range={','.join(gl_ranges[:3])}"],
                        vulkan_values=[f"payload={','.join(vk_payloads)}", f"length={','.join(vk_lengths)}", f"range={','.join(vk_ranges[:3])}"],
                    ))
        elif set(gl_values) != set(vk_values):
            if "SAMPLER" in resource_type:
                gl_shader_visible = sorted({record.sampler_shader_visible_signature for record in gl_records})
                vk_shader_visible = sorted({record.sampler_shader_visible_signature for record in vk_records})
                gl_without_usage = sorted({record.sampler_signature_without_usage for record in gl_records})
                vk_without_usage = sorted({record.sampler_signature_without_usage for record in vk_records})
                if set(gl_shader_visible) == set(vk_shader_visible):
                    reason = "same semantic sampler has matching shader-visible unit/format/size/mip metadata; debug labels or usage flags differ"
                    severity = 28
                    category = "sampler-representational-metadata-difference"
                elif set(gl_without_usage) == set(vk_without_usage):
                    reason = "same semantic sampler has matching texture identity metadata except backend usage flags"
                    severity = 30
                    category = "sampler-usage-flag-difference"
                elif sampler_records_have_different_draw_parameters(
                    gl_records,
                    vk_records,
                    opengl_draw_parameters,
                    vulkan_draw_parameters,
                ):
                    reason = "same semantic sampler key maps to different physical draw parameters; ordinal-only draw identity is insufficient for this resource comparison"
                    severity = 42
                    category = "sampler-semantic-draw-parameter-matching-gap"
                elif is_gui_item_pip_ordinal_matching_gap(gl_records, vk_records):
                    reason = "GUI textured sampler records are shifted by Vulkan item picture-in-picture blits; the same ordinal does not identify the same logical GUI resource"
                    severity = 40
                    category = "sampler-gui-pip-ordinal-matching-gap"
                elif len(gl_shader_visible) > 1 or len(vk_shader_visible) > 1:
                    reason = "same pipeline/resource bucket contains multiple sampler states; missing draw identity prevents pairing individual sampler observations"
                    severity = 44
                    category = "layout-ambiguous-sampler-set-difference"
                else:
                    reason = "same semantic sampler has different shader-visible texture/view metadata"
                    severity = 90
                    category = "strict-sampler-mismatch"
            else:
                reason = "same semantic resource has different normalized value"
                severity = 80
                category = "strict-resource-mismatch"
            differences.append(Difference(
                severity=severity,
                category=category,
                key=key_text,
                reason=reason,
                opengl_count=len(gl_records),
                vulkan_count=len(vk_records),
                opengl_values=gl_values[:4],
                vulkan_values=vk_values[:4],
            ))

        gl_bindings = values_for_bindings(gl_records)
        vk_bindings = values_for_bindings(vk_records)
        if gl_bindings and vk_bindings and set(gl_bindings) != set(vk_bindings):
            differences.append(Difference(
                severity=35,
                category="layout-binding-difference",
                key=key_text,
                reason="semantic resource payload matched, but layout binding numbers differ",
                opengl_count=len(gl_records),
                vulkan_count=len(vk_records),
                opengl_values=[",".join(gl_bindings)],
                vulkan_values=[",".join(vk_bindings)],
            ))

    all_ubo_names = sorted(set(opengl.uniform_buffers) | set(vulkan.uniform_buffers))
    for name in all_ubo_names:
        gl_events = opengl.uniform_buffers.get(name, [])
        vk_events = vulkan.uniform_buffers.get(name, [])
        if not gl_events or not vk_events:
            differences.append(Difference(
                severity=45,
                category="backend-only-uniform-buffer-event",
                key=f"name={name}",
                reason="direct UniformBuffer event only observed on one backend",
                opengl_count=len(gl_events),
                vulkan_count=len(vk_events),
                opengl_values=sorted({event.signature for event in gl_events})[:4],
                vulkan_values=sorted({event.signature for event in vk_events})[:4],
            ))
            continue
        gl_values = sorted({event.signature for event in gl_events})
        vk_values = sorted({event.signature for event in vk_events})
        if set(gl_values) != set(vk_values):
            overlap = sorted(set(gl_values) & set(vk_values))
            if overlap:
                differences.append(Difference(
                    severity=40,
                    category="timing-sensitive-uniform-buffer-payload-set-difference",
                    key=f"name={name}",
                    reason="direct UniformBuffer payloads overlap but separate captures observed additional dynamic states",
                    opengl_count=len(gl_events),
                    vulkan_count=len(vk_events),
                    opengl_values=[f"shared={','.join(overlap[:3])}", *gl_values[:6]],
                    vulkan_values=[f"shared={','.join(overlap[:3])}", *vk_values[:6]],
                ))
                continue
            severity = 85 if name in {"Projection", "Fog", "Globals", "Lighting", "DynamicTransforms"} else 65
            differences.append(Difference(
                severity=severity,
                category="uniform-buffer-payload-set-difference",
                key=f"name={name}",
                reason="direct UniformBuffer event payload hash sets are disjoint",
                opengl_count=len(gl_events),
                vulkan_count=len(vk_events),
                opengl_values=gl_values[:6],
                vulkan_values=vk_values[:6],
            ))

    all_standalone_names = sorted(set(opengl.standalone_uniforms) | set(vulkan.standalone_uniforms))
    for name in all_standalone_names:
        gl_events = opengl.standalone_uniforms.get(name, [])
        vk_events = vulkan.standalone_uniforms.get(name, [])
        if not gl_events or not vk_events:
            if gl_events and not vk_events and name in vulkan.standalone_uniform_block_members:
                continue
            differences.append(Difference(
                severity=35,
                category="backend-only-standalone-uniform",
                key=f"name={name}",
                reason="standalone uniform update only observed on one backend; this may be a sampler/unused-uniform coverage difference",
                opengl_count=len(gl_events),
                vulkan_count=len(vk_events),
                opengl_values=sorted({event.signature for event in gl_events})[:4],
                vulkan_values=sorted({event.signature for event in vk_events})[:4],
            ))
            continue

        gl_values = sorted({event.signature for event in gl_events})
        vk_values = sorted({event.signature for event in vk_events})
        if set(gl_values) != set(vk_values):
            overlap = sorted(set(gl_values) & set(vk_values))
            if overlap:
                differences.append(Difference(
                    severity=55,
                    category="timing-sensitive-standalone-uniform-payload-set-difference",
                    key=name,
                    reason="same standalone uniform semantic key has overlapping payload hashes plus backend-specific observations in separate captures",
                    opengl_count=len(gl_events),
                    vulkan_count=len(vk_events),
                    opengl_values=[f"shared={','.join(overlap[:3])}", *gl_values[:6]],
                    vulkan_values=[f"shared={','.join(overlap[:3])}", *vk_values[:6]],
                ))
                continue

            severity = 95 if name in {
                "iris_ProjectionMatrix",
                "iris_ModelViewMatrix",
                "iris_NormalMatrix",
                "iris_FogStart",
                "iris_FogEnd",
                "iris_FogColor",
                "iris_ProjMat",
                "iris_ModelViewMat",
                "iris_NormalMat",
            } else 70
            differences.append(Difference(
                severity=severity,
                category="strict-standalone-uniform-payload-mismatch",
                key=name,
                reason="same standalone uniform semantic key has disjoint payload hash sets",
                opengl_count=len(gl_events),
                vulkan_count=len(vk_events),
                opengl_values=gl_values[:6],
                vulkan_values=vk_values[:6],
            ))

    all_block_member_names = sorted(set(vulkan.standalone_uniform_block_members))
    for name in all_block_member_names:
        gl_events = opengl.standalone_uniforms.get(name, [])
        vk_events = vulkan.standalone_uniform_block_members.get(name, [])
        if not vk_events:
            continue
        if not gl_events:
            differences.append(Difference(
                severity=25,
                category="vulkan-only-standalone-ubo-member",
                key=f"name={name}",
                reason="materialized Vulkan standalone UBO member was observed, but the matching OpenGL capture did not update that standalone uniform name",
                opengl_count=0,
                vulkan_count=len(vk_events),
                opengl_values=[],
                vulkan_values=sorted({event.signature for event in vk_events})[:4],
            ))
            continue

        gl_values = sorted({event.signature for event in gl_events})
        vk_values = sorted({event.signature for event in vk_events})
        if set(gl_values) != set(vk_values):
            overlap = sorted(set(gl_values) & set(vk_values))
            if overlap:
                differences.append(Difference(
                    severity=58,
                    category="timing-sensitive-standalone-ubo-member-payload-set-difference",
                    key=f"name={name}",
                    reason="materialized Vulkan standalone UBO member overlaps OpenGL setter payloads but separate captures observed additional dynamic states",
                    opengl_count=len(gl_events),
                    vulkan_count=len(vk_events),
                    opengl_values=[f"shared={','.join(overlap[:3])}", *gl_values[:6]],
                    vulkan_values=[f"shared={','.join(overlap[:3])}", *vk_values[:6]],
                ))
                continue

            severity = 98 if name in {
                "iris_ProjectionMatrix",
                "iris_ProjectionMatrixInverse",
                "iris_ModelViewMatrix",
                "iris_ModelViewMatrixInverse",
                "iris_NormalMatrix",
                "iris_FogStart",
                "iris_FogEnd",
                "iris_FogColor",
                "gbufferProjection",
                "gbufferProjectionInverse",
                "gbufferModelView",
                "gbufferModelViewInverse",
                "shadowProjection",
                "shadowProjectionInverse",
            } else 72
            differences.append(Difference(
                severity=severity,
                category="strict-standalone-ubo-member-payload-mismatch",
                key=f"name={name}",
                reason="materialized Vulkan standalone UBO member has disjoint payload hashes from matching OpenGL standalone uniform updates",
                opengl_count=len(gl_events),
                vulkan_count=len(vk_events),
                opengl_values=gl_values[:6],
                vulkan_values=vk_values[:6],
            ))

    all_vertex_keys = sorted(set(opengl.vertex_inputs) | set(vulkan.vertex_inputs))
    for key in all_vertex_keys:
        gl_events = opengl.vertex_inputs.get(key, [])
        vk_events = vulkan.vertex_inputs.get(key, [])
        if not gl_events or not vk_events:
            continue
        gl_values = sorted({event.signature for event in gl_events})
        vk_values = sorted({event.signature for event in vk_events})
        if set(gl_values) != set(vk_values):
            differences.append(Difference(
                severity=75,
                category="vertex-input-mismatch",
                key=f"pipeline={key}",
                reason="same pipeline has different vertex input metadata",
                opengl_count=len(gl_events),
                vulkan_count=len(vk_events),
                opengl_values=gl_values[:4],
                vulkan_values=vk_values[:4],
            ))

    gl_draw_counts = Counter(event.signature for event in opengl.draws)
    vk_draw_counts = Counter(event.signature for event in vulkan.draws)
    for signature in sorted(set(gl_draw_counts) | set(vk_draw_counts)):
        gl_count = gl_draw_counts.get(signature, 0)
        vk_count = vk_draw_counts.get(signature, 0)
        if gl_count != vk_count:
            differences.append(Difference(
                severity=70,
                category="draw-structure-count-difference",
                key=signature,
                reason="normalized draw parameter signature was observed a different number of times; this proves draw-structure coverage exists but not pipeline-level matching",
                opengl_count=gl_count,
                vulkan_count=vk_count,
                opengl_values=[str(gl_count)] if gl_count else [],
                vulkan_values=[str(vk_count)] if vk_count else [],
            ))

    differences.sort(key=lambda diff: (-diff.severity, diff.category, diff.key))
    return differences


def load_capture_meta(path: Path) -> CaptureMeta | None:
    values: dict[str, str] = {}
    for line in read_text(path).splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    run_id = values.get("run_id")
    backend = values.get("backend")
    if not run_id or backend not in {"opengl", "vulkan"}:
        return None
    latest = values.get("latest_log")
    latest_log = Path(latest) if latest else AUTO_CAPTURE / f"latest_{run_id}.log"
    if not latest_log.exists():
        latest_log = None
    return CaptureMeta(
        run_id=run_id,
        backend=backend,
        path=path,
        latest_log=latest_log,
        client_args=values.get("client_args", ""),
        enable_shaders=values.get("effective_enable_shaders", values.get("iris_override_enable_shaders", "")),
        shader_pack=values.get("effective_shader_pack", ""),
    )


def find_latest_matching_pair() -> tuple[CaptureMeta, CaptureMeta] | None:
    metas = [meta for path in AUTO_CAPTURE.glob("meta_*.txt") if (meta := load_capture_meta(path))]
    metas = [meta for meta in metas if meta.latest_log and meta.latest_log.exists()]
    by_key: dict[tuple[str, str, str], dict[str, list[CaptureMeta]]] = defaultdict(lambda: defaultdict(list))
    for meta in metas:
        by_key[meta.matching_key][meta.backend].append(meta)

    candidates: list[tuple[str, CaptureMeta, CaptureMeta]] = []
    for grouped in by_key.values():
        if not grouped.get("opengl") or not grouped.get("vulkan"):
            continue
        gl = max(grouped["opengl"], key=lambda meta: meta.run_id)
        vk = max(grouped["vulkan"], key=lambda meta: meta.run_id)
        candidates.append((max(gl.run_id, vk.run_id), gl, vk))
    if not candidates:
        return None
    _, gl, vk = max(candidates, key=lambda item: item[0])
    return gl, vk


def source_audit() -> list[str]:
    lines: list[str] = []
    source_files = [path for root in SOURCE_ROOTS if root.exists() for path in root.rglob("*.java")]

    total_files = len(source_files)
    gl_constant_counts: Counter[str] = Counter()
    direct_opengl_counts: Counter[str] = Counter()
    gl_named_path_counts: Counter[str] = Counter()
    command_context_counts: Counter[str] = Counter()

    typed_descriptor_files = [
        SRC_MAIN / "net" / "vulkanic" / "VulkanicRenderPassDescriptor.java",
        SRC_MAIN / "net" / "vulkanic" / "VulkanicRenderTargetDescriptor.java",
        SRC_MAIN / "net" / "vulkanic" / "PipelineDescriptor.java",
        SRC_MAIN / "net" / "vulkanic" / "PipelineResourceBindings.java",
        SRC_MAIN / "net" / "vulkanic" / "VulkanicResourceBarriers.java",
    ]

    for path in source_files:
        rel = path.relative_to(ROOT)
        text = read_text(path)
        rel_parts = tuple(rel.parts)
        gl_constants = len(re.findall(r"\bVulkanicAPI\.GL_[A-Z0-9_]+\b", text))
        direct_opengl = len(re.findall(r"org\.lwjgl\.opengl|\bGL(?:11|20|30|32|33|40|43|45|46)C?\b|\bARB[A-Za-z0-9_]*\.", text))
        command_context = text.count("VulkanicAPI.getCommandContext()")

        if gl_constants:
            gl_constant_counts[str(rel)] = gl_constants
        if direct_opengl and not path_is_allowed_opengl_backend(rel_parts):
            direct_opengl_counts[str(rel)] = direct_opengl
        if any(part.lower() in {"gl", "opengl", "globject"} for part in rel.parts):
            gl_named_path_counts[str(rel)] = 1
        if command_context:
            command_context_counts[str(rel)] = command_context

    lines.append("Vulkan Parity Source Audit")
    lines.append("==========================")
    lines.append(f"Source files scanned: {total_files}")
    lines.append(f"Typed Vulkanic descriptor files present: {sum(1 for path in typed_descriptor_files if path.exists())}/{len(typed_descriptor_files)}")
    lines.append("")
    lines.append("Architecture pressure counts:")
    lines.append(f"- VulkanicAPI.GL_* callsites: {sum(gl_constant_counts.values())}")
    lines.append(f"- direct OpenGL imports/capability references outside OpenGL backends: {sum(direct_opengl_counts.values())}")
    lines.append(f"- Java files under GL-named paths: {sum(gl_named_path_counts.values())}")
    lines.append(f"- VulkanicAPI.getCommandContext() callsites: {sum(command_context_counts.values())}")
    lines.append("")
    lines.extend(top_counter_lines("Top VulkanicAPI.GL_* hotspots", gl_constant_counts, 12))
    lines.append("")
    lines.extend(top_counter_lines("Top direct OpenGL hotspots outside OpenGL backends", direct_opengl_counts, 12))
    lines.append("")
    lines.extend(top_counter_lines("Top getCommandContext hotspots", command_context_counts, 12))
    lines.append("")
    lines.append("Interpretation:")
    lines.append("- Method coverage is not the same as native Vulkan correctness.")
    lines.append("- GL constants and GL-named subsystems are the remaining architecture pressure points.")
    lines.append("- The next highest-value fixes should target proven capture mismatches first, then remove the source hotspot that caused them.")
    return lines


def path_is_allowed_opengl_backend(parts: tuple[str, ...]) -> bool:
    joined = tuple(parts)
    for allowed in GL_BACKEND_PATH_PARTS:
        if all(part in joined for part in allowed):
            return True
    return False


def top_counter_lines(title: str, counts: Counter[str], limit: int) -> list[str]:
    lines = [f"{title}:"]
    if not counts:
        lines.append("- none")
        return lines
    for path, count in counts.most_common(limit):
        lines.append(f"- {count:5d}  {path}")
    return lines


PASS_FAMILIES = [
    "GUI and text",
    "item rendering",
    "world terrain",
    "entities",
    "block entities",
    "particles",
    "sky and clouds",
    "weather",
    "fluids and translucency",
    "Sodium terrain",
    "Distant Horizons",
    "Iris shadow passes",
    "Iris deferred passes",
    "Iris composite passes",
    "final presentation",
    "unknown",
]


def pass_family_for_record(record: ResourceRecord) -> str:
    text = " ".join([
        record.pipeline_location,
        record.vertex_shader,
        record.fragment_shader,
        record.name,
        record.source,
    ]).lower()
    if "iris:shadow" in text or "shadow" in text and "iris:" in text:
        return "Iris shadow passes"
    if "iris:deferred" in text or "deferred" in text and "iris:" in text:
        return "Iris deferred passes"
    if "iris:composite" in text or "composite" in text and "iris:" in text:
        return "Iris composite passes"
    if "distant" in text or "dh" in text:
        return "Distant Horizons"
    if "sodium" in text or "terrain" in text and "pipeline" in text:
        return "Sodium terrain"
    if "gui" in text or "text" in text or "font" in text or "crosshair" in text:
        return "GUI and text"
    if "item" in text or "rendertype_item" in text:
        return "item rendering"
    if "entity" in text and "block_entity" not in text and "blockentity" not in text:
        return "entities"
    if "block_entity" in text or "blockentity" in text:
        return "block entities"
    if "particle" in text:
        return "particles"
    if "cloud" in text or "sky" in text or "celestial" in text or "sun" in text or "moon" in text:
        return "sky and clouds"
    if "weather" in text or "rain" in text or "snow" in text:
        return "weather"
    if "fluid" in text or "water" in text or "translucent" in text:
        return "fluids and translucency"
    if "final" in text or "blit" in text or "present" in text:
        return "final presentation"
    if "minecraft:pipeline" in text:
        return "world terrain"
    return "unknown"


def semantic_draw_coverage(opengl_events: CaptureEvents, vulkan_events: CaptureEvents) -> dict[str, object]:
    gl_by_signature: dict[str, list[SemanticDrawEvent]] = defaultdict(list)
    vk_by_signature: dict[str, list[SemanticDrawEvent]] = defaultdict(list)
    for event in opengl_events.semantic_draws:
        gl_by_signature[event.semantic_match_signature].append(event)
    for event in vulkan_events.semantic_draws:
        vk_by_signature[event.semantic_match_signature].append(event)

    gl_physical_by_key = Counter(event.semantic_group_key for event in opengl_events.draws if event.semantic_group_key != "unavailable")
    vk_physical_by_key = Counter(event.semantic_group_key for event in vulkan_events.draws if event.semantic_group_key != "unavailable")
    gl_resources_by_key = Counter(record.semantic_draw_key for records in opengl_events.resources.values() for record in records if record.semantic_draw_key and record.semantic_draw_key != "unavailable")
    vk_resources_by_key = Counter(record.semantic_draw_key for records in vulkan_events.resources.values() for record in records if record.semantic_draw_key and record.semantic_draw_key != "unavailable")

    matched: list[dict[str, object]] = []
    ambiguous: list[dict[str, object]] = []
    unmatched_opengl: list[str] = []
    unmatched_vulkan: list[str] = []

    for signature in sorted(set(gl_by_signature) | set(vk_by_signature)):
        gl_events = gl_by_signature.get(signature, [])
        vk_events = vk_by_signature.get(signature, [])
        if gl_events and vk_events:
            gl_keys = sorted({event.semantic_group_key for event in gl_events})
            vk_keys = sorted({event.semantic_group_key for event in vk_events})
            matched.append({
                "signature": signature,
                "opengl_semantic_draw_events": len(gl_events),
                "vulkan_semantic_draw_events": len(vk_events),
                "opengl_semantic_draw_keys": gl_keys[:8],
                "vulkan_semantic_draw_keys": vk_keys[:8],
                "opengl_physical_draw_events": sum(gl_physical_by_key.get(key, 0) for key in gl_keys),
                "vulkan_physical_draw_events": sum(vk_physical_by_key.get(key, 0) for key in vk_keys),
                "opengl_resource_events": sum(gl_resources_by_key.get(key, 0) for key in gl_keys),
                "vulkan_resource_events": sum(vk_resources_by_key.get(key, 0) for key in vk_keys),
            })
        elif gl_events:
            unmatched_opengl.append(signature)
        else:
            unmatched_vulkan.append(signature)

    physical_draw_delta_examples = [
        entry for entry in matched
        if entry["opengl_physical_draw_events"] != entry["vulkan_physical_draw_events"]
    ][:20]
    geometry = geometry_coverage(opengl_events, vulkan_events, set(gl_by_signature) & set(vk_by_signature))

    return {
        "opengl_semantic_draw_events": len(opengl_events.semantic_draws),
        "vulkan_semantic_draw_events": len(vulkan_events.semantic_draws),
        "matched_semantic_groups": len(matched),
        "unmatched_opengl_semantic_groups": len(unmatched_opengl),
        "unmatched_vulkan_semantic_groups": len(unmatched_vulkan),
        "ambiguous_semantic_groups": len(ambiguous),
        "opengl_physical_draws_with_semantic_key": sum(gl_physical_by_key.values()),
        "vulkan_physical_draws_with_semantic_key": sum(vk_physical_by_key.values()),
        "opengl_physical_draws_without_semantic_key": sum(1 for event in opengl_events.draws if event.semantic_group_key == "unavailable"),
        "vulkan_physical_draws_without_semantic_key": sum(1 for event in vulkan_events.draws if event.semantic_group_key == "unavailable"),
        "matched_groups_with_different_physical_draw_counts": len([
            entry for entry in matched
            if entry["opengl_physical_draw_events"] != entry["vulkan_physical_draw_events"]
        ]),
        "physical_draw_delta_examples": physical_draw_delta_examples,
        "geometry": geometry,
        "unmatched_opengl_examples": unmatched_opengl[:20],
        "unmatched_vulkan_examples": unmatched_vulkan[:20],
        "ambiguous_examples": ambiguous[:20],
    }


def geometry_coverage(opengl_events: CaptureEvents, vulkan_events: CaptureEvents, matched_signatures: set[str]) -> dict[str, object]:
    gl_sampler_signatures = sampler_resource_signatures_by_draw_key(opengl_events)
    vk_sampler_signatures = sampler_resource_signatures_by_draw_key(vulkan_events)
    gl_geometry: dict[str, list[GeometryEvent]] = defaultdict(list)
    vk_geometry: dict[str, list[GeometryEvent]] = defaultdict(list)
    for event in opengl_events.geometry:
        if event.semantic_match_signature in matched_signatures:
            gl_geometry[geometry_match_signature(event, gl_sampler_signatures)].append(event)
    for event in vulkan_events.geometry:
        if event.semantic_match_signature in matched_signatures:
            vk_geometry[geometry_match_signature(event, vk_sampler_signatures)].append(event)

    group_results: list[dict[str, object]] = []
    equivalent = 0
    divergent = 0
    not_comparable = 0
    missing = 0
    geometry_signatures = set(gl_geometry) | set(vk_geometry)
    for signature in sorted(geometry_signatures):
        gl_events = gl_geometry.get(signature, [])
        vk_events = vk_geometry.get(signature, [])
        if not gl_events or not vk_events:
            missing += 1
            group_results.append({
                "signature": signature,
                "classification": "not-comparable",
                "reason": geometry_missing_reason(signature, gl_events, vk_events),
                "opengl_geometry_events": len(gl_events),
                "vulkan_geometry_events": len(vk_events),
            })
            continue
        gl_not_comparable = [event.reason for event in gl_events if not event.comparable]
        vk_not_comparable = [event.reason for event in vk_events if not event.comparable]
        gl_layouts = sorted({event.layout_hash for event in gl_events})
        vk_layouts = sorted({event.layout_hash for event in vk_events})
        gl_stream = ordered_digest(geometry_stream_parts(gl_events))
        vk_stream = ordered_digest(geometry_stream_parts(vk_events))
        classification = "equivalent"
        reason = "ok"
        if gl_not_comparable or vk_not_comparable:
            classification = "not-comparable"
            reason = geometry_event_not_comparable_reason(gl_not_comparable, vk_not_comparable)
        elif gl_layouts != vk_layouts:
            classification = "divergent"
            reason = "vertex-layout-hash-differs"
        elif gl_stream != vk_stream:
            classification = "divergent"
            reason = "canonical-geometry-hash-differs"
        else:
            classification = "equivalent"
            reason = "ok"
        first_detail_difference = first_geometry_detail_difference(gl_events, vk_events)
        override_reason = geometry_not_comparable_override(signature, classification, first_detail_difference)
        if override_reason:
            classification = "not-comparable"
            reason = override_reason

        if classification == "equivalent":
            equivalent += 1
        elif classification == "divergent":
            divergent += 1
        else:
            not_comparable += 1

        group_results.append({
            "signature": signature,
            "classification": classification,
            "reason": reason,
            "opengl_geometry_events": len(gl_events),
            "vulkan_geometry_events": len(vk_events),
            "opengl_total_vertices": sum(event.total_vertices for event in gl_events),
            "vulkan_total_vertices": sum(event.total_vertices for event in vk_events),
            "opengl_total_indices": sum(event.total_indices for event in gl_events),
            "vulkan_total_indices": sum(event.total_indices for event in vk_events),
            "opengl_total_primitives": sum(event.total_primitives for event in gl_events),
            "vulkan_total_primitives": sum(event.total_primitives for event in vk_events),
            "opengl_total_instances": sum(event.instances for event in gl_events),
            "vulkan_total_instances": sum(event.instances for event in vk_events),
            "opengl_layout_hashes": gl_layouts,
            "vulkan_layout_hashes": vk_layouts,
            "opengl_canonical_hash": gl_stream,
            "vulkan_canonical_hash": vk_stream,
            "opengl_not_comparable_reasons": sorted(set(gl_not_comparable))[:8],
            "vulkan_not_comparable_reasons": sorted(set(vk_not_comparable))[:8],
            "first_detail_difference": first_detail_difference,
        })

    return {
        "matched_groups_checked": len(geometry_signatures),
        "equivalent_groups": equivalent,
        "divergent_groups": divergent,
        "not_comparable_groups": not_comparable + missing,
        "missing_geometry_groups": missing,
        "opengl_geometry_events": len(opengl_events.geometry),
        "vulkan_geometry_events": len(vulkan_events.geometry),
        "group_results": group_results,
    }


def sampler_resource_signatures_by_draw_key(events: CaptureEvents) -> dict[str, str]:
    by_draw_key: dict[str, set[str]] = defaultdict(set)
    for records in events.resources.values():
        for record in records:
            if record.resource_type != "SAMPLER" or not record.semantic_draw_key:
                continue
            by_draw_key[record.semantic_draw_key].add("|".join([
                f"name={record.name or 'unknown'}",
                f"unit={record.unit or 'unknown'}",
                f"label={semantic_texture_label(record.texture_label)}",
                f"format={record.texture_format or 'unknown'}",
                f"size={record.texture_width or 'unknown'}x{record.texture_height or 'unknown'}",
                f"mips={record.texture_mips or 'unknown'}",
            ]))
    return {draw_key: ",".join(sorted(signatures)) for draw_key, signatures in by_draw_key.items()}


def geometry_match_signature(event: GeometryEvent, sampler_signatures: dict[str, str]) -> str:
    if event.semantic_pipeline not in GEOMETRY_SAMPLER_AWARE_PIPELINES:
        return event.semantic_match_signature
    sampler_signature = sampler_signatures.get(event.semantic_draw_key, "samplers=unavailable")
    return event.semantic_match_signature + "|samplers=" + sampler_signature


def semantic_texture_label(label: str) -> str:
    if not label:
        return "unknown"
    stripped = label.strip().strip('"')
    if re.fullmatch(r"\d+", stripped):
        return "numeric-gl-label"
    if re.fullmatch(r"0x[0-9A-Fa-f]+", stripped):
        return "numeric-gl-label"
    return stripped


def geometry_missing_reason(signature: str, gl_events: list[GeometryEvent], vk_events: list[GeometryEvent]) -> str:
    if "subsystem=sodium-terrain|" in signature:
        if not gl_events and vk_events:
            return "sodium-terrain-ready-work-unmatched:opengl-missing"
        if gl_events and not vk_events:
            return "sodium-terrain-ready-work-unmatched:vulkan-missing"
        return "sodium-terrain-ready-work-unmatched"
    missing = []
    if not gl_events:
        missing.append("opengl")
    if not vk_events:
        missing.append("vulkan")
    return "geometry-records-missing:" + ",".join(missing)


def geometry_event_not_comparable_reason(gl_reasons: list[str], vk_reasons: list[str]) -> str:
    evidence = []
    if gl_reasons:
        evidence.append("opengl=" + ",".join(sorted(set(gl_reasons))[:4]))
    if vk_reasons:
        evidence.append("vulkan=" + ",".join(sorted(set(vk_reasons))[:4]))
    return "geometry-event-not-comparable:" + ";".join(evidence)


def geometry_not_comparable_override(signature: str, classification: str, first_detail_difference: dict[str, object]) -> str:
    if classification != "divergent":
        return ""
    if "|pose=none|" in signature:
        return "outside-deterministic-pose-before-capture-state"
    first_opengl = str(first_detail_difference.get("opengl", ""))
    first_vulkan = str(first_detail_difference.get("vulkan", ""))
    if "pipeline=minecraft:pipeline/gui_text|" in signature and ".UV0@" in first_opengl and ".UV0@" in first_vulkan:
        return "font-atlas-glyph-content-identity-not-logged"
    if "pipeline=minecraft:pipeline/gui_textured|" in signature and "label=voxelmap-map-" in signature:
        return "voxelmap-minimap-source-offset-angle-state-not-logged"
    if "pipeline=minecraft:pipeline/entity_" in signature and ".POSITION0@" in first_opengl and ".POSITION0@" in first_vulkan:
        return "entity-animation-pose-state-not-logged"
    return ""


def geometry_stream_parts(events: list[GeometryEvent]) -> Iterable[str]:
    detail_stream = complete_geometry_detail_stream(events)
    if detail_stream is not None:
        yield from detail_stream
        return

    for event in events:
        yield "|".join([
            f"layout={event.layout_hash}",
            f"mode={event.mode}",
            f"indexed={event.indexed}",
            f"vertices={event.total_vertices}",
            f"indices={event.total_indices}",
            f"primitives={event.total_primitives}",
            f"instances={event.instances}",
            f"vertexHash={event.vertex_hash}",
        ])


DETAIL_VERTEX_RE = re.compile(r"^v(\d+)\.inst(\d+)\.idx-?\d+\.")


def complete_geometry_detail_stream(events: list[GeometryEvent]) -> list[str] | None:
    stream: list[str] = []
    for event in events:
        if not event.detail or event.detail == "none":
            return None
        tokens = [token for token in event.detail.split(";") if token]
        if not tokens:
            return None
        vertex_ordinals = set()
        normalized_tokens: list[str] = []
        for token in tokens:
            match = DETAIL_VERTEX_RE.match(token)
            if match is None:
                return None
            vertex_ordinals.add(int(match.group(1)))
            normalized_tokens.append(DETAIL_VERTEX_RE.sub(r"inst\2.", token, count=1))
        if len(vertex_ordinals) != event.total_vertices:
            return None
        stream.extend(normalized_tokens)
    return stream


def first_geometry_detail_difference(gl_events: list[GeometryEvent], vk_events: list[GeometryEvent]) -> dict[str, object]:
    gl_stream = complete_geometry_detail_stream(gl_events)
    vk_stream = complete_geometry_detail_stream(vk_events)
    if gl_stream is not None and vk_stream is not None:
        for token_index, (gl_token, vk_token) in enumerate(zip(gl_stream, vk_stream)):
            if gl_token != vk_token:
                return {
                    "token_index": token_index,
                    "kind": "aggregate-detail-token-differs",
                    "opengl": gl_token,
                    "vulkan": vk_token,
                }
        if len(gl_stream) != len(vk_stream):
            return {
                "kind": "aggregate-detail-count-differs",
                "opengl_detail_tokens": len(gl_stream),
                "vulkan_detail_tokens": len(vk_stream),
            }
        return {}

    for draw_index, (gl_event, vk_event) in enumerate(zip(gl_events, vk_events)):
        gl_detail = [] if not gl_event.detail or gl_event.detail == "none" else gl_event.detail.split(";")
        vk_detail = [] if not vk_event.detail or vk_event.detail == "none" else vk_event.detail.split(";")
        if not gl_detail and not vk_detail:
            continue
        if len(gl_detail) != len(vk_detail):
            return {
                "draw_index": draw_index,
                "kind": "detail-count-differs",
                "opengl_detail_tokens": len(gl_detail),
                "vulkan_detail_tokens": len(vk_detail),
            }
        for token_index, (gl_token, vk_token) in enumerate(zip(gl_detail, vk_detail)):
            if gl_token != vk_token:
                return {
                    "draw_index": draw_index,
                    "token_index": token_index,
                    "kind": "detail-token-differs",
                    "opengl": gl_token,
                    "vulkan": vk_token,
                }
    if len(gl_events) != len(vk_events):
        return {
            "kind": "geometry-event-count-differs",
            "opengl_events": len(gl_events),
            "vulkan_events": len(vk_events),
        }
    return {}


def _empty_family_coverage() -> dict[str, object]:
    return {
        "opengl_resource_events": 0,
        "vulkan_resource_events": 0,
        "matched_resource_keys": 0,
        "unmatched_opengl_resource_keys": 0,
        "unmatched_vulkan_resource_keys": 0,
        "comparable_ubo_keys": 0,
        "blocked_ubo_keys": 0,
        "sampler_resource_keys": 0,
        "content_hash_resource_keys": 0,
        "difference_categories": {},
        "draw_counts": {
            "opengl_draws": None,
            "vulkan_draws": None,
            "matched_draws": None,
            "ambiguous_matches": None,
            "blocked_by": "missing normalized draw-event instrumentation",
        },
    }


def build_coverage_report(opengl_events: CaptureEvents, vulkan_events: CaptureEvents, differences: list[Difference]) -> dict[str, object]:
    families: dict[str, dict[str, object]] = {family: _empty_family_coverage() for family in PASS_FAMILIES}
    semantic_coverage = semantic_draw_coverage(opengl_events, vulkan_events)
    opengl_draw_signature_counts = Counter(event.signature for event in opengl_events.draws)
    vulkan_draw_signature_counts = Counter(event.signature for event in vulkan_events.draws)

    all_resource_keys = set(opengl_events.resources) | set(vulkan_events.resources)
    for key in all_resource_keys:
        gl_records = opengl_events.resources.get(key, [])
        vk_records = vulkan_events.resources.get(key, [])
        representative = (gl_records or vk_records)[0]
        family = pass_family_for_record(representative)
        family_entry = families.setdefault(family, _empty_family_coverage())
        family_entry["opengl_resource_events"] = int(family_entry["opengl_resource_events"]) + len(gl_records)
        family_entry["vulkan_resource_events"] = int(family_entry["vulkan_resource_events"]) + len(vk_records)
        if gl_records and vk_records:
            family_entry["matched_resource_keys"] = int(family_entry["matched_resource_keys"]) + 1
        elif gl_records:
            family_entry["unmatched_opengl_resource_keys"] = int(family_entry["unmatched_opengl_resource_keys"]) + 1
        else:
            family_entry["unmatched_vulkan_resource_keys"] = int(family_entry["unmatched_vulkan_resource_keys"]) + 1

        if key[3] == "UNIFORM_BUFFER":
            if values_for_comparable_payload_hashes(gl_records) and values_for_comparable_payload_hashes(vk_records):
                family_entry["comparable_ubo_keys"] = int(family_entry["comparable_ubo_keys"]) + 1
            else:
                family_entry["blocked_ubo_keys"] = int(family_entry["blocked_ubo_keys"]) + 1
        if "SAMPLER" in key[3]:
            family_entry["sampler_resource_keys"] = int(family_entry["sampler_resource_keys"]) + 1
        if values_for_content_hashes(gl_records) or values_for_content_hashes(vk_records):
            family_entry["content_hash_resource_keys"] = int(family_entry["content_hash_resource_keys"]) + 1

    for difference in differences:
        family = "unknown"
        key_text = difference.key.lower()
        for candidate in PASS_FAMILIES:
            if candidate != "unknown" and candidate.lower() in key_text:
                family = candidate
                break
        if family == "unknown":
            for key, records in {**opengl_events.resources, **vulkan_events.resources}.items():
                if format_key(key) == difference.key and records:
                    family = pass_family_for_record(records[0])
                    break
        category_counts = families.setdefault(family, _empty_family_coverage())["difference_categories"]
        assert isinstance(category_counts, dict)
        category_counts[difference.category] = int(category_counts.get(difference.category, 0)) + 1

    return {
        "schema": "mattmc.vulkan_parity.coverage.v1",
        "opengl_log": str(opengl_events.path),
        "vulkan_log": str(vulkan_events.path),
        "event_counts": {
            "opengl": dict(opengl_events.counters),
            "vulkan": dict(vulkan_events.counters),
            "opengl_skipped": dict(opengl_events.skipped),
                "vulkan_skipped": dict(vulkan_events.skipped),
        },
        "draw_structure": {
            "opengl_draw_events": len(opengl_events.draws),
            "vulkan_draw_events": len(vulkan_events.draws),
            "matched_by_parameter_signature": sum(
                min(count, vulkan_draw_signature_counts.get(signature, 0))
                for signature, count in opengl_draw_signature_counts.items()
            ),
            "unmatched_opengl_parameter_signatures": sum(
                1
                for signature, count in opengl_draw_signature_counts.items()
                if vulkan_draw_signature_counts.get(signature, 0) != count
            ),
            "unmatched_vulkan_parameter_signatures": sum(
                1
                for signature, count in vulkan_draw_signature_counts.items()
                if opengl_draw_signature_counts.get(signature, 0) != count
            ),
            "semantic_coverage": semantic_coverage,
        },
        "global_blockers": {
            "draw_structure": "semantic draw identity is present, but some legacy/raw backend draw records may still lack a semantic key",
            "geometry_input_bytes": "missing consumed vertex/index range hashes",
            "render_target_state": "missing normalized attachment/load/store/clear events",
            "fixed_function_state": "missing normalized pipeline-state events",
            "shader_construction": "not emitted by this capture log format",
        },
        "families": families,
    }


def render_diff_report(opengl_log: Path, vulkan_log: Path, limit: int, parse_limits: ParseLimits | None = None) -> str:
    parse_limits = parse_limits or ParseLimits()
    opengl_events = parse_capture_log(opengl_log, parse_limits)
    vulkan_events = parse_capture_log(vulkan_log, parse_limits)
    differences = compare_capture_events(opengl_events, vulkan_events)
    by_category = Counter(diff.category for diff in differences)

    lines: list[str] = []
    lines.append("Vulkan Shader Input Parity Audit")
    lines.append("================================")
    lines.append(f"OpenGL capture: {opengl_log}")
    lines.append(f"Vulkan capture: {vulkan_log}")
    lines.append("")
    lines.append("Normalization rules:")
    lines.append("- Backend object identities, Java identity hashes, pipeline handles, texture ids, view ids, and buffer ids are ignored.")
    lines.append("- Pipeline resources are matched by pipeline identity/stableKey/name/type before comparing binding numbers.")
    lines.append("- UBOs compare payload hashes separately from range hashes and binding metadata.")
    lines.append("- UBO payloadHash=unavailable is classified as a diagnostic coverage gap, not a strict mismatch.")
    lines.append("- Draw events carry backend-neutral semantic draw identity when emitted from Blaze/Vulkanic render-pass boundaries.")
    lines.append("- Semantic draw groups match by logical pass/pipeline/material/output/pose/rendered-frame; per-group ordinals remain recorded metadata.")
    lines.append("- Geometry groups additionally include normalized sampler format/dimensions so different GUI texture sources are not paired as one draw.")
    lines.append("- Geometry hashes compare consumed vertex attributes in draw order; backend-local index buffer values are representational and are not part of the canonical stream.")
    lines.append("- Standalone uniforms compare by semantic program/stage/phase/draw/name/type key and normalized setter payload hash.")
    lines.append("- Materialized Vulkan standalone UBO members compare by the same semantic key against OpenGL standalone uniforms when member logs are present.")
    lines.append("- Samplers compare semantic texture metadata; numeric GL object labels are normalized.")
    lines.append("- Separate captures are not frame-synchronized; backend-only observations are lower-confidence than strict same-key mismatches.")
    lines.append("")
    lines.append("Event counts:")
    lines.append(f"- OpenGL: {dict(opengl_events.counters)}")
    lines.append(f"- Vulkan: {dict(vulkan_events.counters)}")
    if opengl_events.skipped or vulkan_events.skipped:
        lines.append("Parse limits:")
        lines.append(f"- OpenGL skipped: {dict(opengl_events.skipped)}")
        lines.append(f"- Vulkan skipped: {dict(vulkan_events.skipped)}")
    lines.append("")
    semantic_coverage = semantic_draw_coverage(opengl_events, vulkan_events)
    lines.append("Semantic draw coverage:")
    lines.append(f"- OpenGL semantic draw events: {semantic_coverage['opengl_semantic_draw_events']}")
    lines.append(f"- Vulkan semantic draw events: {semantic_coverage['vulkan_semantic_draw_events']}")
    lines.append(f"- Matched semantic groups: {semantic_coverage['matched_semantic_groups']}")
    lines.append(f"- Unmatched OpenGL semantic groups: {semantic_coverage['unmatched_opengl_semantic_groups']}")
    lines.append(f"- Unmatched Vulkan semantic groups: {semantic_coverage['unmatched_vulkan_semantic_groups']}")
    lines.append(f"- Ambiguous semantic groups: {semantic_coverage['ambiguous_semantic_groups']}")
    lines.append(f"- Physical draws with semantic key: OpenGL={semantic_coverage['opengl_physical_draws_with_semantic_key']} Vulkan={semantic_coverage['vulkan_physical_draws_with_semantic_key']}")
    lines.append(f"- Physical draws without semantic key: OpenGL={semantic_coverage['opengl_physical_draws_without_semantic_key']} Vulkan={semantic_coverage['vulkan_physical_draws_without_semantic_key']}")
    lines.append(f"- Matched semantic groups with different physical draw counts: {semantic_coverage['matched_groups_with_different_physical_draw_counts']}")
    if semantic_coverage["physical_draw_delta_examples"]:
        lines.append("- Physical draw count delta examples:")
        for entry in semantic_coverage["physical_draw_delta_examples"][:5]:
            lines.append(
                "  "
                + f"OpenGL={entry['opengl_physical_draw_events']} Vulkan={entry['vulkan_physical_draw_events']} "
                + str(entry["signature"])[:220]
            )
    geometry = semantic_coverage.get("geometry", {})
    if geometry:
        lines.append("- Geometry coverage:")
        lines.append(f"  checked={geometry.get('matched_groups_checked', 0)} equivalent={geometry.get('equivalent_groups', 0)} divergent={geometry.get('divergent_groups', 0)} notComparable={geometry.get('not_comparable_groups', 0)}")
        lines.append(f"  geometry events: OpenGL={geometry.get('opengl_geometry_events', 0)} Vulkan={geometry.get('vulkan_geometry_events', 0)}")
        for entry in geometry.get("group_results", [])[:5]:
            lines.append(
                "  "
                + f"[{entry.get('classification')}] "
                + f"GL draws={entry.get('opengl_geometry_events')} VK draws={entry.get('vulkan_geometry_events')} "
                + f"GL verts={entry.get('opengl_total_vertices')} VK verts={entry.get('vulkan_total_vertices')} "
                + str(entry.get("signature", ""))[:160]
            )
    lines.append("")
    lines.append("Difference summary:")
    lines.append(f"- Total differences: {len(differences)}")
    for category, count in by_category.most_common():
        lines.append(f"- {category}: {count}")
    lines.append("")
    lines.append("Most actionable differences:")
    actionable = differences[:limit]
    if not actionable:
        lines.append("- none")
    for index, diff in enumerate(actionable, 1):
        lines.append(f"{index}. [{diff.category}] {diff.key}")
        lines.append(f"   Reason: {diff.reason}")
        lines.append(f"   Severity: {diff.severity}")
        lines.append(f"   Counts: OpenGL={diff.opengl_count}, Vulkan={diff.vulkan_count}")
        lines.append(f"   OpenGL digest={stable_digest(diff.opengl_values)} values={diff.opengl_values[:3] or ['not observed']}")
        lines.append(f"   Vulkan digest={stable_digest(diff.vulkan_values)} values={diff.vulkan_values[:3] or ['not observed']}")
    lines.append("")
    lines.append("Recommended narrowing rule:")
    lines.append("- Fix strict same-key UBO payload mismatches before sampler metadata mismatches.")
    lines.append("- Close ubo-payload-not-comparable coverage before treating UBO inputs as proven equivalent.")
    lines.append("- Fix strict materialized standalone UBO member mismatches before broad shader math changes.")
    lines.append("- Fix strict standalone uniform payload mismatches before broad shader math changes.")
    lines.append("- Treat layout-binding differences as architectural cleanup unless payloads differ.")
    lines.append("- Treat backend-only observations as coverage/instrumentation suspects until reproduced in synchronized captures.")
    return "\n".join(lines) + "\n"


def write_report(text: str, prefix: str) -> Path:
    AUTO_CAPTURE.mkdir(parents=True, exist_ok=True)
    run_id = datetime.now().strftime("%Y%m%d_%H%M%S")
    path = AUTO_CAPTURE / f"{prefix}_{run_id}.txt"
    path.write_text(text, encoding="utf-8")
    return path


def write_json_report(data: dict[str, object], text_report_path: Path) -> Path:
    json_path = text_report_path.with_suffix(".json")
    json_path.write_text(json.dumps(data, indent=2, sort_keys=True), encoding="utf-8")
    return json_path


def render_coverage_json(opengl_log: Path, vulkan_log: Path, parse_limits: ParseLimits | None = None) -> dict[str, object]:
    parse_limits = parse_limits or ParseLimits()
    opengl_events = parse_capture_log(opengl_log, parse_limits)
    vulkan_events = parse_capture_log(vulkan_log, parse_limits)
    differences = compare_capture_events(opengl_events, vulkan_events)
    return build_coverage_report(opengl_events, vulkan_events, differences)


def run_self_test() -> None:
    line = (
        'ShaderInputParityResources backend=vulkan source=test pipelineHandle=Vk@abc '
        'pipelineKey=abc stableKey=stable resources=['
        'Projection{layout=set:0,binding:1,type:UNIFORM_BUFFER,stages:[VERTEX, FRAGMENT],'
        'buffer={bufferClass=VulkanBuffer,bufferId=123abc,size=64,usage=155,closed=false,'
        'offset=0,length=64,payloadHash=crc32:aaaa/bytes:64,rangeHash=crc32:aaaa/bytes:64}}, '
        'Sampler0{layout=set:0,binding:0,type:SAMPLER,stages:[FRAGMENT],sampler={unit=0,'
        'view={viewClass=VulkanTextureView,viewId=456def,baseMip=0,mips=1,width=16,height=16,'
        'closed=false,texture={class=VulkanGpuTexture,id=777,label="42",format=RGBA8,width=16,'
        'height=16,layers=1,mips=1,usage=5,closed=false}}}}]'
    )
    resources = extract_balanced_after("resources=", line)
    parts = split_top_level_resources(resources)
    assert len(parts) == 2, parts
    first = parse_resource(parts[0], "vulkan", "test", "abc", "stable")
    second = parse_resource(parts[1], "vulkan", "test", "abc", "stable")
    assert first and first.name == "Projection" and first.payload_hash == "crc32:aaaa/bytes:64"
    assert second and second.name == "Sampler0" and second.sampler_signature.endswith("usage=5")

    member_line = (
        'ShaderInputParityStandaloneUniformBlockMember backend=vulkan source=vulkan-standalone-ubo '
        'program=42 location=7 name=gbufferProjection valueKind=mat4 componentCount=16 '
        'offset=64 arraySize=1 stride=64 payloadHash=crc32:bbbb/bytes:64,sample=[3f800000,0,0,0,...]'
    )
    events = CaptureEvents(path=Path("self-test.log"))
    parse_capture_line(member_line, events, ParseLimits())
    assert "gbufferProjection" not in events.standalone_uniform_block_members
    members = next(iter(events.standalone_uniform_block_members.values()))
    assert len(members) == 1
    assert members[0].signature == "crc32:bbbb/bytes:64"
    assert members[0].offset == "64"

    gl_events = CaptureEvents(path=Path("gl-self-test.log"), backend="opengl")
    vk_events = CaptureEvents(path=Path("vk-self-test.log"), backend="vulkan")
    gl_events.standalone_uniforms["gbufferProjection"].append(StandaloneUniformEvent(
        backend="opengl",
        source="opengl-setUniform",
        name="gbufferProjection",
        value_kind="mat4",
        component_count="16",
        raw="",
        payload_hash="crc32:aaaa/bytes:64",
    ))
    vk_events.standalone_uniform_block_members["gbufferProjection"].append(StandaloneUniformBlockMemberEvent(
        backend="vulkan",
        source="vulkan-standalone-ubo",
        name="gbufferProjection",
        value_kind="mat4",
        component_count="16",
        raw="",
        payload_hash="crc32:bbbb/bytes:64",
    ))
    diffs = compare_capture_events(gl_events, vk_events)
    assert not any(
        diff.category == "backend-only-standalone-uniform" and diff.key == "name=gbufferProjection"
        for diff in diffs
    ), diffs
    assert any(
        diff.category == "strict-standalone-ubo-member-payload-mismatch" and diff.key == "name=gbufferProjection"
        for diff in diffs
    ), diffs

    vk_events.resources[("pipeline", "generated", "VulkanicStandaloneUniforms", "UNIFORM_BUFFER")].append(ResourceRecord(
        backend="vulkan",
        source="vulkan-bindPipelineResources",
        pipeline_location="pipeline",
        vertex_shader="",
        fragment_shader="",
        pipeline_key="pipeline",
        stable_key="generated",
        name="VulkanicStandaloneUniforms",
        resource_type="UNIFORM_BUFFER",
        raw="",
        payload_hash="crc32:cccc/bytes:64",
    ))
    diffs = compare_capture_events(gl_events, vk_events)
    assert not any(
        diff.category == "backend-only-resource" and "name=VulkanicStandaloneUniforms" in diff.key
        for diff in diffs
    ), diffs

    gl_events.resources[("pipeline", "stable", "DynamicTransforms", "UNIFORM_BUFFER")].append(ResourceRecord(
        backend="opengl",
        source="opengl-bindPipelineResources",
        pipeline_location="pipeline",
        vertex_shader="",
        fragment_shader="",
        pipeline_key="pipeline",
        stable_key="stable",
        name="DynamicTransforms",
        resource_type="UNIFORM_BUFFER",
        raw="",
        payload_hash="crc32:dddd/bytes:160",
    ))
    vk_events.resources[("pipeline", "stable", "DynamicTransforms", "UNIFORM_BUFFER")].append(ResourceRecord(
        backend="vulkan",
        source="vulkan-bindPipelineResources",
        pipeline_location="pipeline",
        vertex_shader="",
        fragment_shader="",
        pipeline_key="pipeline",
        stable_key="stable",
        name="DynamicTransforms",
        resource_type="UNIFORM_BUFFER",
        raw="",
        payload_hash="unavailable",
    ))
    diffs = compare_capture_events(gl_events, vk_events)
    assert any(
        diff.category == "ubo-payload-not-comparable"
        for diff in diffs
    ), diffs
    assert not any(
        diff.category == "strict-ubo-payload-mismatch" and "DynamicTransforms" in diff.key
        for diff in diffs
    ), diffs
    coverage = build_coverage_report(gl_events, vk_events, diffs)
    assert coverage["schema"] == "mattmc.vulkan_parity.coverage.v1"
    print("self-test passed")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    diff_parser = subparsers.add_parser("diff-captures", help="diff two capture latest logs")
    diff_parser.add_argument("--opengl-log", required=True, type=Path)
    diff_parser.add_argument("--vulkan-log", required=True, type=Path)
    diff_parser.add_argument("--limit", type=int, default=30)
    diff_parser.add_argument("--max-resource-events", type=int, default=0, help="0 means parse every resource event")
    diff_parser.add_argument("--max-uniform-buffer-events", type=int, default=0, help="0 means parse every UBO event")
    diff_parser.add_argument("--max-standalone-uniform-events", type=int, default=0, help="0 means parse every standalone uniform event")
    diff_parser.add_argument("--max-standalone-uniform-block-member-events", type=int, default=0, help="0 means parse every standalone UBO member event")
    diff_parser.add_argument("--max-vertex-input-events", type=int, default=0, help="0 means parse every vertex-input event")
    diff_parser.add_argument("--max-draw-events", type=int, default=0, help="0 means parse every draw event")
    diff_parser.add_argument("--write", action="store_true", help="write report under logs/auto-capture")

    auto_parser = subparsers.add_parser("auto-diff", help="diff newest matching OpenGL/Vulkan capture pair")
    auto_parser.add_argument("--limit", type=int, default=30)
    auto_parser.add_argument("--max-resource-events", type=int, default=0, help="0 means parse every resource event")
    auto_parser.add_argument("--max-uniform-buffer-events", type=int, default=0, help="0 means parse every UBO event")
    auto_parser.add_argument("--max-standalone-uniform-events", type=int, default=0, help="0 means parse every standalone uniform event")
    auto_parser.add_argument("--max-standalone-uniform-block-member-events", type=int, default=0, help="0 means parse every standalone UBO member event")
    auto_parser.add_argument("--max-vertex-input-events", type=int, default=0, help="0 means parse every vertex-input event")
    auto_parser.add_argument("--max-draw-events", type=int, default=0, help="0 means parse every draw event")
    auto_parser.add_argument("--write", action="store_true", help="write report under logs/auto-capture")

    source_parser = subparsers.add_parser("source-audit", help="scan source architecture pressure points")
    source_parser.add_argument("--write", action="store_true", help="write report under logs/auto-capture")

    subparsers.add_parser("self-test", help="run parser self-test")

    args = parser.parse_args(argv)
    if args.command == "self-test":
        run_self_test()
        return 0

    if args.command == "source-audit":
        text = "\n".join(source_audit()) + "\n"
        if args.write:
            path = write_report(text, "vulkan_parity_source_audit")
            print(f"wrote {path}")
        print(text, end="")
        return 0

    if args.command == "auto-diff":
        pair = find_latest_matching_pair()
        if pair is None:
            print("No matching OpenGL/Vulkan capture pair found.", file=sys.stderr)
            return 2
        gl_meta, vk_meta = pair
        assert gl_meta.latest_log is not None
        assert vk_meta.latest_log is not None
        parse_limits = ParseLimits(
            args.max_resource_events,
            args.max_uniform_buffer_events,
            args.max_standalone_uniform_events,
            args.max_standalone_uniform_block_member_events,
            args.max_vertex_input_events,
            args.max_draw_events
        )
        text = render_diff_report(gl_meta.latest_log, vk_meta.latest_log, args.limit, parse_limits)
        if args.write:
            path = write_report(text, f"vulkan_shader_input_parity_{gl_meta.run_id}_vs_{vk_meta.run_id}")
            print(f"wrote {path}")
            json_path = write_json_report(render_coverage_json(gl_meta.latest_log, vk_meta.latest_log, parse_limits), path)
            print(f"wrote {json_path}")
        print(text, end="")
        return 0

    if args.command == "diff-captures":
        parse_limits = ParseLimits(
            args.max_resource_events,
            args.max_uniform_buffer_events,
            args.max_standalone_uniform_events,
            args.max_standalone_uniform_block_member_events,
            args.max_vertex_input_events,
            args.max_draw_events
        )
        text = render_diff_report(args.opengl_log, args.vulkan_log, args.limit, parse_limits)
        if args.write:
            path = write_report(text, f"vulkan_shader_input_parity_{args.opengl_log.stem}_vs_{args.vulkan_log.stem}")
            print(f"wrote {path}")
            json_path = write_json_report(render_coverage_json(args.opengl_log, args.vulkan_log, parse_limits), path)
            print(f"wrote {json_path}")
        print(text, end="")
        return 0

    return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
