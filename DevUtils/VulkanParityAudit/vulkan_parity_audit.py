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
import struct
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
RESOURCE_START_RE = re.compile(r"(?:^|,\s*)([A-Za-z0-9_.$:-]+)\{layout=")


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
    det_awaiting_screenshot: str = ""
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
        name = normalize_resource_name(self.name)
        if self.semantic_subsystem == "sodium-terrain" and self.resource_type in {"SAMPLER", "COMPARISON_SAMPLER"}:
            pipeline_identity = self.semantic_pipeline or self.pipeline_location or self.stable_key
            return (pipeline_identity, "semantic-sodium-terrain", name, self.resource_type)
        if self.name == "Projection" and self.projection_label:
            name = f"{self.name}@{self.projection_label}"
        if self.content_hash and self.det_pose:
            name = f"{name}@pose:{self.det_pose}@source:{self.source or 'unknown'}"
        context_parts: list[str] = []
        if is_stable_semantic_draw_key(self.semantic_draw_key):
            context_parts.append(f"draw:{self.semantic_draw_key}")
        else:
            if self.det_pose:
                context_parts.append(f"pose:{self.det_pose}")
            if self.det_rendered_frame:
                context_parts.append(f"frame:{self.det_rendered_frame}")
            if self.semantic_subsystem:
                context_parts.append(f"subsystem:{self.semantic_subsystem}")
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
        stable_label = label if is_semantic_texture_label(label) else "weak-label"
        return "|".join([
            f"label={stable_label}",
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
    decoded: str = ""
    program_identity: str = ""
    shader_stages: str = ""
    location: str = ""
    render_phase: str = ""
    draw_key: str = ""
    det_pose: str = ""
    det_rendered_frame: str = ""
    det_awaiting_screenshot: str = ""
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
    decoded: str = ""
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
    det_awaiting_screenshot: str = ""
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
    det_awaiting_screenshot: str = ""
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
    det_awaiting_screenshot: str = ""

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
        return "|".join(parts)

    @property
    def comparable(self) -> bool:
        return self.status == "equivalent-candidate" and self.vertex_hash and self.vertex_hash != "unavailable"


@dataclass
class DrawStateEvent:
    backend: str
    raw: str
    det_pose: str = ""
    det_rendered_frame: str = ""
    det_awaiting_screenshot: str = ""
    semantic_draw_key: str = ""
    semantic_subsystem: str = ""
    semantic_phase: str = ""
    semantic_pass: str = ""
    semantic_pipeline: str = ""
    semantic_material: str = ""
    semantic_output: str = ""
    semantic_ordinal: str = ""
    path: str = ""
    pipeline: str = ""
    framebuffer: str = ""
    render_target: str = ""
    color_attachments: str = ""
    has_depth: str = ""
    requested: str = ""
    translated: str = ""
    viewport: str = ""
    scissor: str = ""
    draw: str = ""
    resources: str = ""

    @property
    def semantic_key(self) -> str:
        if self.semantic_draw_key and self.semantic_draw_key != "unavailable":
            return self.semantic_draw_key
        return "|".join([
            f"pose={self.det_pose or 'none'}",
            f"frame={self.det_rendered_frame or 'none'}",
            f"pipeline={self.semantic_pipeline or self.pipeline or 'unknown'}",
            f"output={self.semantic_output or 'unknown'}",
            f"ordinal={self.semantic_ordinal or '0'}",
            f"draw={self.draw}",
        ])

    @property
    def logical_target_signature(self) -> str:
        return "|".join([
            f"output={self.semantic_output or 'unknown'}",
            f"target={normalize_render_target_signature(self.render_target)}",
            f"colors={self.color_attachments}",
            f"hasDepth={self.has_depth}",
        ])

    @property
    def fixed_function_signature(self) -> str:
        requested = parse_struct_fields(self.requested)
        translated = parse_struct_fields(self.translated)
        depth_test = requested.get('depthTest', '')
        depth_compare = "disabled" if depth_test == "NO_DEPTH_TEST" else normalize_depth_compare(
            translated.get('depthCompare', depth_test)
        )
        cull = requested.get('cull', '')
        front_face = "cull-disabled" if cull == "false" else normalize_front_face(translated.get('frontFace', ''))
        return "|".join([
            self.logical_target_signature,
            f"viewport={normalize_rect_signature(self.viewport, include_known=True)}",
            f"scissor={normalize_rect_signature(self.scissor, include_known=False)}",
            f"depthTest={depth_test}",
            f"depthWrite={requested.get('depthWrite', '')}",
            f"depthCompare={depth_compare}",
            f"stencilTest={translated.get('stencilTest', 'false')}",
            f"cull={cull}",
            f"frontFace={front_face}",
            f"polygonMode={normalize_polygon_mode(translated.get('polygonMode', requested.get('polygonMode', '')))}",
            f"topology={normalize_topology(requested.get('topology', translated.get('topology', '')))}",
            f"blend={requested.get('blend', '')}",
            f"blendState={requested.get('blendState', '')}",
            f"colorWriteMask={normalize_color_write_mask(translated.get('colorWriteMask', ''))}",
            f"logic={requested.get('logic', '')}",
            f"depthBias={translated.get('depthBias', '')}",
            f"depthBiasConstant={translated.get('depthBiasConstant', '')}",
            f"depthBiasSlope={translated.get('depthBiasSlope', '')}",
            f"multisampling={translated.get('multisampling', requested.get('multisampling', ''))}",
            f"lineWidth={translated.get('lineWidth', requested.get('lineWidth', ''))}",
        ])


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
    det_awaiting_screenshot: str = ""

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
class OrderingEvent:
    backend: str
    operation: str
    source: str
    order_ordinal: int
    order_key: str
    detail: str
    det_pose: str = ""
    det_rendered_frame: str = ""
    det_awaiting_screenshot: str = ""
    semantic_draw_key: str = ""
    semantic_subsystem: str = ""
    semantic_phase: str = ""
    semantic_pass: str = ""
    semantic_pipeline: str = ""
    semantic_material: str = ""
    semantic_output: str = ""
    semantic_ordinal: str = ""

    @property
    def pose_key(self) -> str:
        return f"pose={self.det_pose or 'none'}|frame={self.det_rendered_frame or 'none'}"

    @property
    def semantic_signature(self) -> str:
        return "|".join([
            f"operation={self.operation or 'unknown'}",
            f"subsystem={self.semantic_subsystem or 'unknown'}",
            f"phase={self.semantic_phase or 'unknown'}",
            f"pass={self.semantic_pass or 'unknown'}",
            f"pipeline={self.semantic_pipeline or 'unknown'}",
            f"material={self.semantic_material or 'unknown'}",
            f"output={self.semantic_output or 'unknown'}",
            self.pose_key,
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
    draw_states: dict[str, list[DrawStateEvent]] = field(default_factory=lambda: defaultdict(list))
    ordering: list[OrderingEvent] = field(default_factory=list)
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


def normalize_resource_name(name: str) -> str:
    # Draw fingerprints are useful evidence, but shader-enabled backends can
    # batch the same logical resource behind different physical draw hashes.
    return re.sub(r"@draw:crc32:[0-9a-fA-F]+/bytes:\d+", "", name)


def is_stable_semantic_draw_key(key: str) -> bool:
    if not key or key == "unavailable":
        return False
    # crc32 draw keys are physical draw fingerprints, not shared producer
    # identity. They differ when OpenGL and Vulkan batch equivalent work
    # differently.
    return not key.startswith("crc32:")


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


def is_semantic_texture_label(label: str) -> bool:
    if not label:
        return False
    if label in {"<numeric-label>", "PNG_Texture"}:
        return False
    if label.startswith("Legacy_texture_"):
        return False
    return True


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


def normalize_render_target_signature(text: str) -> str:
    if not text:
        return "unknown"
    normalized = normalize_identity(text)
    if normalized in {"framebuffer", "framebuffer-or-texture-view"}:
        return "legacy-framebuffer"
    normalized = re.sub(r"\btex=\d+", "tex=<id>", normalized)
    return normalized


def normalize_semantic_output(text: str) -> str:
    if not text:
        return ""
    normalized = normalize_identity(text)
    projection_suffix = ""
    if "|projection:" in normalized:
        normalized, projection = normalized.split("|projection:", 1)
        projection_suffix = f"|projection:{projection}"
    if (
        normalized in {"framebuffer", "framebuffer-or-texture-view"}
        or normalized.startswith("framebuffer:")
        or normalized.startswith("extent=")
    ):
        normalized = "legacy-framebuffer"
    else:
        normalized = re.sub(r"\btex=\d+", "tex=<id>", normalized)
    return normalized + projection_suffix


def normalize_semantic_pass(text: str) -> str:
    if not text:
        return ""
    normalized = normalize_identity(text)
    if normalized.startswith("extent="):
        return "legacy-renderpass"
    return normalized


def normalize_rect_signature(text: str, include_known: bool) -> str:
    fields = parse_struct_fields(text)
    if not fields:
        return "unknown"
    if include_known and fields.get("known") == "false":
        return "known=false"
    keys = ["x", "y", "width", "height"]
    if include_known:
        keys.insert(0, "known")
    else:
        keys.insert(0, "enabled")
    return ",".join(f"{key}={fields.get(key, 'unknown')}" for key in keys)


def normalize_depth_compare(value: str) -> str:
    normalized = value.replace("VK_COMPARE_OP_", "").replace("_DEPTH_TEST", "")
    if normalized == "LESS_OR_EQUAL":
        return "LEQUAL"
    return normalized


def normalize_front_face(value: str) -> str:
    if value in {"OPENGL_CURRENT", "VK_FRONT_FACE_COUNTER_CLOCKWISE", "COUNTER_CLOCKWISE", "unknown", ""}:
        return "semantic-default"
    return value.replace("VK_FRONT_FACE_", "")


def normalize_polygon_mode(value: str) -> str:
    return value.replace("VK_POLYGON_MODE_", "")


def normalize_topology(value: str) -> str:
    normalized = value.replace("VK_PRIMITIVE_TOPOLOGY_", "")
    if normalized == "TRIANGLE_LIST":
        return "TRIANGLES"
    return normalized


def normalize_color_write_mask(value: str) -> str:
    if not value:
        return "unknown"
    return "".join(sorted(value))


FLOAT_RE = re.compile(r"[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?")
SEMANTIC_BLOCK_RE = re.compile(r"semantic=\{([^}]*)\}")
SEMANTIC_FLOAT_ABS_TOLERANCE = 1.0e-4
SEMANTIC_FLOAT_REL_TOLERANCE = 1.0e-4


def extract_semantic_float_values(text: str) -> list[float]:
    match = SEMANTIC_BLOCK_RE.search(text)
    if not match:
        return []
    return [float(value) for value in FLOAT_RE.findall(match.group(1))]


def parse_decoded_float_values(text: str) -> list[float]:
    if not text or text in {"unavailable", "unknown"}:
        return []
    return [float(value) for value in FLOAT_RE.findall(text)]


def decoded_float_values(event: StandaloneUniformEvent | StandaloneUniformBlockMemberEvent) -> list[float]:
    decoded = parse_decoded_float_values(event.decoded)
    if decoded:
        return decoded
    return parse_float_sample_values(event)


def parse_float_sample_values(event: StandaloneUniformEvent | StandaloneUniformBlockMemberEvent) -> list[float]:
    if "float" not in event.value_kind and "vec" not in event.value_kind and "mat" not in event.value_kind:
        return []
    if not event.sample.startswith("[") or not event.sample.endswith("]") or "..." in event.sample:
        return []
    try:
        expected_count = int(event.component_count)
    except (TypeError, ValueError):
        return []
    parts = [part.strip() for part in event.sample[1:-1].split(",") if part.strip()]
    if not parts or len(parts) != expected_count:
        return []
    values: list[float] = []
    for part in parts:
        try:
            raw = int(part, 16) & 0xFFFFFFFF
        except ValueError:
            return []
        values.append(struct.unpack(">f", struct.pack(">I", raw))[0])
    return values


def semantic_float_tolerance_for(left: float, right: float) -> float:
    return SEMANTIC_FLOAT_ABS_TOLERANCE + SEMANTIC_FLOAT_REL_TOLERANCE * max(abs(left), abs(right))


def float_sequences_equivalent(left: list[float], right: list[float]) -> tuple[bool, float]:
    if len(left) != len(right):
        return False, float("inf")
    max_delta = 0.0
    for left_value, right_value in zip(left, right):
        delta = abs(left_value - right_value)
        max_delta = max(max_delta, delta)
        if delta > semantic_float_tolerance_for(left_value, right_value):
            return False, max_delta
    return True, max_delta


def decoded_float_sets_equivalent(
    gl_events: list[StandaloneUniformEvent] | list[StandaloneUniformBlockMemberEvent],
    vk_events: list[StandaloneUniformEvent] | list[StandaloneUniformBlockMemberEvent],
) -> tuple[str, str]:
    gl_values = unique_float_sequences(decoded_float_values(event) for event in gl_events)
    vk_values = unique_float_sequences(decoded_float_values(event) for event in vk_events)
    if not gl_values or not vk_values or not all(gl_values) or not all(vk_values):
        return "unavailable", ""

    unmatched_vk = list(range(len(vk_values)))
    max_delta = 0.0
    for gl_value in gl_values:
        match_index = -1
        match_delta = float("inf")
        for candidate_index in unmatched_vk:
            equivalent, delta = float_sequences_equivalent(gl_value, vk_values[candidate_index])
            if equivalent and delta < match_delta:
                match_index = candidate_index
                match_delta = delta
        if match_index < 0:
            return "different", ""
        unmatched_vk.remove(match_index)
        max_delta = max(max_delta, match_delta)

    if unmatched_vk:
        return "different", ""
    return "equivalent", f"maxDelta={max_delta:.9g};absTolerance={SEMANTIC_FLOAT_ABS_TOLERANCE:.1e};relTolerance={SEMANTIC_FLOAT_REL_TOLERANCE:.1e};decodedValues={len(gl_values)}"


def decoded_float_sets_overlap(
    gl_events: list[StandaloneUniformEvent] | list[StandaloneUniformBlockMemberEvent],
    vk_events: list[StandaloneUniformEvent] | list[StandaloneUniformBlockMemberEvent],
) -> tuple[bool, str]:
    gl_values = unique_float_sequences(decoded_float_values(event) for event in gl_events)
    vk_values = unique_float_sequences(decoded_float_values(event) for event in vk_events)
    if not gl_values or not vk_values or not all(gl_values) or not all(vk_values):
        return False, ""

    shared = 0
    max_delta = 0.0
    for gl_value in gl_values:
        for vk_value in vk_values:
            equivalent, delta = float_sequences_equivalent(gl_value, vk_value)
            if equivalent:
                shared += 1
                max_delta = max(max_delta, delta)
                break
    if shared <= 0:
        return False, ""
    return True, (
        f"sharedDecodedValues={shared};openglDecodedValues={len(gl_values)};"
        f"vulkanDecodedValues={len(vk_values)};maxSharedDelta={max_delta:.9g};"
        f"absTolerance={SEMANTIC_FLOAT_ABS_TOLERANCE:.1e};relTolerance={SEMANTIC_FLOAT_REL_TOLERANCE:.1e}"
    )


def unique_float_sequences(values: Iterable[list[float]]) -> list[list[float]]:
    unique: list[list[float]] = []
    for value in values:
        if not value:
            unique.append(value)
            continue
        if not any(float_sequences_equivalent(value, existing)[0] for existing in unique if existing):
            unique.append(value)
    return unique


def semantic_float_values_equivalent(gl_records: list[ResourceRecord], vk_records: list[ResourceRecord]) -> tuple[bool, str]:
    gl_values = [extract_semantic_float_values(record.raw) for record in gl_records]
    vk_values = [extract_semantic_float_values(record.raw) for record in vk_records]
    if len(gl_values) != len(vk_values) or not gl_values or not all(gl_values) or not all(vk_values):
        return False, ""
    max_delta = 0.0
    for gl_record_values, vk_record_values in zip(gl_values, vk_values):
        if len(gl_record_values) != len(vk_record_values):
            return False, ""
        for gl_value, vk_value in zip(gl_record_values, vk_record_values):
            max_delta = max(max_delta, abs(gl_value - vk_value))
            if max_delta > semantic_float_tolerance_for(gl_value, vk_value):
                return False, f"maxDelta={max_delta:.9g}"
    return True, f"maxDelta={max_delta:.9g};absTolerance={SEMANTIC_FLOAT_ABS_TOLERANCE:.1e};relTolerance={SEMANTIC_FLOAT_REL_TOLERANCE:.1e}"


def without_signature_field(signature: str, field_name: str) -> str:
    prefix = f"{field_name}="
    return "|".join(part for part in signature.split("|") if not part.startswith(prefix))


def without_signature_fields(signature: str, field_names: set[str]) -> str:
    prefixes = tuple(f"{field_name}=" for field_name in field_names)
    return "|".join(part for part in signature.split("|") if not part.startswith(prefixes))


def is_gui_item_atlas_pip_state_gap(gl_events: list["DrawStateEvent"], vk_events: list["DrawStateEvent"], gl_sigs: list[str], vk_sigs: list[str]) -> bool:
    pipelines = {event.semantic_pipeline for event in gl_events + vk_events}
    if not pipelines or not all(
        pipeline in {
            "minecraft:pipeline/entity_cutout",
            "minecraft:pipeline/entity_cutout_no_cull",
            "minecraft:pipeline/item_entity_translucent_cull",
        }
        for pipeline in pipelines
    ):
        return False
    gl_without_viewport_scissor = sorted({without_signature_fields(signature, {"viewport", "scissor"}) for signature in gl_sigs})
    vk_without_viewport_scissor = sorted({without_signature_fields(signature, {"viewport", "scissor"}) for signature in vk_sigs})
    if gl_without_viewport_scissor != vk_without_viewport_scissor:
        return False
    gl_uses_item_atlas_tile = any(
        "viewport=known=true,x=0,y=0,width=512,height=512" in signature
        and "scissor=enabled=true" in signature
        and "width=48,height=48" in signature
        for signature in gl_sigs
    )
    vk_uses_pip_full_target = any(
        "viewport=known=true,x=0,y=0,width=1280,height=720" in signature
        and "scissor=enabled=false" in signature
        for signature in vk_sigs
    )
    return gl_uses_item_atlas_tile and vk_uses_pip_full_target


def extract_hashes(text: str) -> dict[str, str]:
    return {match.group(1): match.group(2) for match in HASH_RE.finditer(text)}


def int_or_zero(value: str) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def is_pre_pose_deterministic_record(fields: dict[str, str]) -> bool:
    return fields.get("detCapture") == "true" and fields.get("detPose", "none") == "none"


def standalone_uniform_key(event: StandaloneUniformEvent | StandaloneUniformBlockMemberEvent) -> str:
    if is_stable_semantic_draw_key(event.semantic_draw_key):
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
    draw_key = event.draw_key if is_stable_semantic_draw_key(event.draw_key) else "unavailable"
    return "|".join([
        f"program={event.program_identity or 'unknown'}",
        f"stages={event.shader_stages or 'unknown'}",
        f"phase={event.render_phase or 'unknown'}",
        f"draw={draw_key}",
        f"name={event.name}",
        f"type={event.value_kind}",
    ])


def standalone_uniform_key_has_semantic_identity(key: str) -> bool:
    return key.startswith("draw=") and "draw=unavailable" not in key


def semantic_field_or_fallback(value: str, fallback: str = "unknown") -> str:
    if not value or value == "unknown" or value == "unavailable":
        return fallback or "unknown"
    return value


def standalone_uniform_program_level_key(event: StandaloneUniformEvent | StandaloneUniformBlockMemberEvent) -> str:
    return "|".join([
        f"program={semantic_field_or_fallback(event.program_identity)}",
        f"stages={semantic_field_or_fallback(event.shader_stages)}",
        f"phase={semantic_field_or_fallback(event.semantic_phase, event.render_phase)}",
        f"name={event.name}",
        f"type={event.value_kind}",
        f"pose={semantic_field_or_fallback(event.det_pose)}",
    ])


def standalone_uniform_program_level_index(
    events: dict[str, list[StandaloneUniformEvent]] | dict[str, list[StandaloneUniformBlockMemberEvent]],
) -> dict[str, list[StandaloneUniformEvent | StandaloneUniformBlockMemberEvent]]:
    indexed: dict[str, list[StandaloneUniformEvent | StandaloneUniformBlockMemberEvent]] = defaultdict(list)
    for values in events.values():
        for event in values:
            indexed[standalone_uniform_program_level_key(event)].append(event)
    return indexed


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
    starts = [match.start(1) for match in RESOURCE_START_RE.finditer(text)]
    if len(starts) > 1:
        return [
            text[starts[index]:starts[index + 1]].strip().rstrip(",").strip()
            for index in range(len(starts) - 1)
        ] + [text[starts[-1]:].strip().rstrip(",").strip()]

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
        semantic_pass=normalize_semantic_pass(top_fields.get("semanticPass", "")),
        semantic_pipeline=top_fields.get("semanticPipeline", ""),
        semantic_material=top_fields.get("semanticMaterial", ""),
        det_awaiting_screenshot=top_fields.get("detAwaitingScreenshot", ""),
        semantic_output=normalize_semantic_output(top_fields.get("semanticOutput", "")),
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


def event_pose(event) -> str:
    return getattr(event, "det_pose", "") or "none"


def filter_capture_events_by_pose(
    events: CaptureEvents,
    pose: str | None,
    stable_pose_only: bool = False,
    stable_window_frames: int = 1,
) -> CaptureEvents:
    if not pose:
        return events

    filtered = CaptureEvents(path=events.path, backend=events.backend)
    filtered.counters = events.counters.copy()
    filtered.skipped = events.skipped.copy()
    filtered.skipped[f"PoseFilter:{pose}"] = 0
    if stable_pose_only:
        filtered.skipped[f"StablePoseFilter:{pose}"] = 0

    def all_pose_scoped_events():
        for records in events.resources.values():
            yield from records
        for records in events.standalone_uniforms.values():
            yield from records
        for records in events.standalone_uniform_block_members.values():
            yield from records
        yield from events.draws
        yield from events.semantic_draws
        yield from events.geometry
        for records in events.draw_states.values():
            yield from records
        yield from events.ordering

    stable_frame = ""
    stable_window_start = ""
    if stable_pose_only:
        stable_frames = [
            int(getattr(event, "det_rendered_frame", ""))
            for event in all_pose_scoped_events()
            if event_pose(event) == pose
            and getattr(event, "det_awaiting_screenshot", "") == "true"
            and str(getattr(event, "det_rendered_frame", "")).isdigit()
        ]
        if stable_frames:
            selected_stable_frame = min(stable_frames)
            stable_frame = str(selected_stable_frame)
            stable_window_start = str(max(0, selected_stable_frame - max(1, stable_window_frames) + 1))

    def keep_event(event) -> bool:
        keep = event_pose(event) == pose
        if not keep:
            filtered.skipped[f"PoseFilter:{pose}"] += 1
            return False
        if stable_pose_only and (
            not stable_frame
            or not str(getattr(event, "det_rendered_frame", "")).isdigit()
            or int(getattr(event, "det_rendered_frame", "")) < int(stable_window_start)
            or int(getattr(event, "det_rendered_frame", "")) > int(stable_frame)
        ):
            filtered.skipped[f"StablePoseFilter:{pose}"] += 1
            return False
        return keep

    for key, records in events.resources.items():
        kept = [record for record in records if keep_event(record)]
        if kept:
            filtered.resources[key].extend(kept)
    for key, records in events.uniform_buffers.items():
        filtered.uniform_buffers[key].extend(records)
    for key, records in events.standalone_uniforms.items():
        kept = [record for record in records if keep_event(record)]
        if kept:
            filtered.standalone_uniforms[key].extend(kept)
    for key, records in events.standalone_uniform_block_members.items():
        kept = [record for record in records if keep_event(record)]
        if kept:
            filtered.standalone_uniform_block_members[key].extend(kept)
    for key, records in events.vertex_inputs.items():
        filtered.vertex_inputs[key].extend(records)
    filtered.draws = [event for event in events.draws if keep_event(event)]
    filtered.semantic_draws = [event for event in events.semantic_draws if keep_event(event)]
    filtered.geometry = [event for event in events.geometry if keep_event(event)]
    for key, records in events.draw_states.items():
        kept = [record for record in records if keep_event(record)]
        if kept:
            filtered.draw_states[key].extend(kept)
    filtered.ordering = [event for event in events.ordering if keep_event(event)]
    return filtered


def parse_capture_line(line: str, events: CaptureEvents, limits: ParseLimits) -> None:
    if "VulkanicDrawStateParity" in line:
        payload = line.split("VulkanicDrawStateParity", 1)[1]
        events.counters["DrawState"] += 1
        fields = parse_top_fields(payload)
        backend = fields.get("backend", "unknown")
        events.backend = backend
        event = DrawStateEvent(
            backend=backend,
            raw=payload.strip(),
            det_pose=fields.get("detPose", ""),
            det_rendered_frame=fields.get("detRenderedFrame", ""),
            det_awaiting_screenshot=fields.get("detAwaitingScreenshot", ""),
            semantic_draw_key=fields.get("semanticDrawKey", ""),
            semantic_subsystem=fields.get("semanticSubsystem", ""),
            semantic_phase=fields.get("semanticPhase", ""),
            semantic_pass=normalize_semantic_pass(fields.get("semanticPass", "")),
            semantic_pipeline=fields.get("semanticPipeline", ""),
            semantic_material=fields.get("semanticMaterial", ""),
            semantic_output=normalize_semantic_output(fields.get("semanticOutput", "")),
            semantic_ordinal=fields.get("semanticOrdinal", ""),
            path=fields.get("path", ""),
            pipeline=fields.get("pipeline", ""),
            framebuffer=fields.get("framebuffer", ""),
            render_target=fields.get("renderTarget", ""),
            color_attachments=fields.get("colorAttachments", ""),
            has_depth=fields.get("hasDepth", ""),
            requested=extract_balanced_after("requested", payload),
            translated=extract_balanced_after("translated", payload),
            viewport=extract_balanced_after("viewport", payload),
            scissor=extract_balanced_after("scissor", payload),
            draw=extract_balanced_after("draw", payload),
            resources=extract_balanced_after("resources", payload),
        )
        events.draw_states[event.semantic_key].append(event)
        return

    if "ShaderInputParity" not in line:
        return
    payload = line.split("ShaderInputParity", 1)[1]

    if payload.startswith("Ordering "):
        events.counters["Ordering"] += 1
        fields = parse_top_fields(payload)
        if is_pre_pose_deterministic_record(fields):
            events.skipped["OrderingPrePose"] += 1
            return
        backend = fields.get("backend", "unknown")
        events.backend = backend
        try:
            order_ordinal = int(fields.get("orderOrdinal", "0"))
        except ValueError:
            order_ordinal = 0
        events.ordering.append(OrderingEvent(
            backend=backend,
            operation=fields.get("operation", ""),
            source=fields.get("source", ""),
            order_ordinal=order_ordinal,
            order_key=fields.get("orderKey", ""),
            detail=fields.get("detail", ""),
            det_pose=fields.get("detPose", ""),
            det_rendered_frame=fields.get("detRenderedFrame", ""),
            det_awaiting_screenshot=fields.get("detAwaitingScreenshot", ""),
            semantic_draw_key=fields.get("semanticDrawKey", ""),
            semantic_subsystem=fields.get("semanticSubsystem", ""),
            semantic_phase=fields.get("semanticPhase", ""),
            semantic_pass=normalize_semantic_pass(fields.get("semanticPass", "")),
            semantic_pipeline=fields.get("semanticPipeline", ""),
            semantic_material=fields.get("semanticMaterial", ""),
            semantic_output=normalize_semantic_output(fields.get("semanticOutput", "")),
            semantic_ordinal=fields.get("semanticOrdinal", ""),
        ))

    elif payload.startswith("Resources "):
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
            decoded=fields.get("decoded", ""),
            program_identity=fields.get("programIdentity", ""),
            shader_stages=fields.get("shaderStages", ""),
            location=fields.get("location", ""),
            render_phase=fields.get("renderPhase", ""),
            draw_key=fields.get("drawKey", ""),
            det_pose=fields.get("detPose", ""),
            det_rendered_frame=fields.get("detRenderedFrame", ""),
            det_awaiting_screenshot=fields.get("detAwaitingScreenshot", ""),
            semantic_draw_key=fields.get("semanticDrawKey", ""),
            semantic_subsystem=fields.get("semanticSubsystem", ""),
            semantic_phase=fields.get("semanticPhase", ""),
            semantic_pass=normalize_semantic_pass(fields.get("semanticPass", "")),
            semantic_pipeline=fields.get("semanticPipeline", ""),
            semantic_material=fields.get("semanticMaterial", ""),
            semantic_output=normalize_semantic_output(fields.get("semanticOutput", "")),
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
            decoded=fields.get("decoded", ""),
            program_identity=fields.get("programIdentity", ""),
            shader_stages=fields.get("shaderStages", ""),
            location=fields.get("location", ""),
            render_phase=fields.get("renderPhase", ""),
            draw_key=fields.get("drawKey", ""),
            det_pose=fields.get("detPose", ""),
            det_rendered_frame=fields.get("detRenderedFrame", ""),
            det_awaiting_screenshot=fields.get("detAwaitingScreenshot", ""),
            semantic_draw_key=fields.get("semanticDrawKey", ""),
            semantic_subsystem=fields.get("semanticSubsystem", ""),
            semantic_phase=fields.get("semanticPhase", ""),
            semantic_pass=normalize_semantic_pass(fields.get("semanticPass", "")),
            semantic_pipeline=fields.get("semanticPipeline", ""),
            semantic_material=fields.get("semanticMaterial", ""),
            semantic_output=normalize_semantic_output(fields.get("semanticOutput", "")),
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
            det_awaiting_screenshot=fields.get("detAwaitingScreenshot", ""),
            semantic_draw_key=fields.get("semanticDrawKey", ""),
            semantic_subsystem=fields.get("semanticSubsystem", ""),
            semantic_phase=fields.get("semanticPhase", ""),
            semantic_pass=normalize_semantic_pass(fields.get("semanticPass", "")),
            semantic_pipeline=fields.get("semanticPipeline", ""),
            semantic_material=fields.get("semanticMaterial", ""),
            semantic_output=normalize_semantic_output(fields.get("semanticOutput", "")),
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
            semantic_pass=normalize_semantic_pass(fields.get("semanticPass", "")),
            semantic_pipeline=fields.get("semanticPipeline", ""),
            semantic_vertex_shader=fields.get("semanticVertexShader", ""),
            semantic_fragment_shader=fields.get("semanticFragmentShader", ""),
            semantic_material=fields.get("semanticMaterial", ""),
            semantic_output=normalize_semantic_output(fields.get("semanticOutput", "")),
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
            det_awaiting_screenshot=fields.get("detAwaitingScreenshot", ""),
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
            semantic_pass=normalize_semantic_pass(fields.get("semanticPass", "")),
            semantic_pipeline=fields.get("semanticPipeline", ""),
            semantic_vertex_shader=fields.get("semanticVertexShader", ""),
            semantic_fragment_shader=fields.get("semanticFragmentShader", ""),
            semantic_material=fields.get("semanticMaterial", ""),
            semantic_output=normalize_semantic_output(fields.get("semanticOutput", "")),
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
            det_awaiting_screenshot=fields.get("detAwaitingScreenshot", ""),
        ))

    elif payload.startswith("Sampler "):
        events.counters["Sampler"] += 1
    elif payload.startswith("Uniform "):
        events.counters["Uniform"] += 1


def values_for_resources(records: list[ResourceRecord]) -> list[str]:
    return sorted({record.short_signature for record in records})


def sampler_metadata_incomplete(records: list[ResourceRecord]) -> bool:
    return any(
        not record.texture_format
        or not record.texture_width
        or not record.texture_height
        or not record.texture_mips
        for record in records
    )


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


def resource_key_without_rendered_frame(key: tuple[str, str, str, str]) -> tuple[str, str, str, str]:
    pipeline_identity, stable_key, name, resource_type = key
    return (pipeline_identity, stable_key, re.sub(r"/frame:\d+", "", name), resource_type)


def screenshot_ready_payloads_for_key(events: CaptureEvents, key: tuple[str, str, str, str]) -> set[str]:
    normalized_key = resource_key_without_rendered_frame(key)
    payloads: set[str] = set()
    for candidate_key, records in events.resources.items():
        if resource_key_without_rendered_frame(candidate_key) != normalized_key:
            continue
        for record in records:
            if record.det_awaiting_screenshot != "true":
                continue
            if record.resource_type != "UNIFORM_BUFFER":
                continue
            if record.payload_hash and not record.payload_hash.startswith("unavailable:"):
                payloads.add(record.payload_hash)
    return payloads


def rendered_frame_from_resource_key(key: tuple[str, str, str, str]) -> int | None:
    match = re.search(r"/frame:(\d+)", key[2])
    if not match:
        return None
    return int(match.group(1))


def later_payloads_for_key(events: CaptureEvents, key: tuple[str, str, str, str], after_frame: int) -> set[str]:
    normalized_key = resource_key_without_rendered_frame(key)
    payloads: set[str] = set()
    for candidate_key, records in events.resources.items():
        if resource_key_without_rendered_frame(candidate_key) != normalized_key:
            continue
        candidate_frame = rendered_frame_from_resource_key(candidate_key)
        if candidate_frame is None or candidate_frame <= after_frame:
            continue
        for record in records:
            if record.resource_type != "UNIFORM_BUFFER":
                continue
            if record.payload_hash and not record.payload_hash.startswith("unavailable:"):
                payloads.add(record.payload_hash)
    return payloads


def mismatch_is_transient_window_warmup_with_later_match(
    opengl: CaptureEvents,
    vulkan: CaptureEvents,
    key: tuple[str, str, str, str],
    gl_records: list[ResourceRecord],
    vk_records: list[ResourceRecord],
) -> bool:
    if not gl_records or not vk_records:
        return False
    frame = rendered_frame_from_resource_key(key)
    if frame is None:
        return False
    gl_final = screenshot_ready_payloads_for_key(opengl, key)
    vk_final = screenshot_ready_payloads_for_key(vulkan, key)
    if gl_final and vk_final and gl_final & vk_final:
        return True
    gl_later = later_payloads_for_key(opengl, key, frame)
    vk_later = later_payloads_for_key(vulkan, key, frame)
    return bool(gl_later and vk_later and gl_later & vk_later)


def compare_capture_events(opengl: CaptureEvents, vulkan: CaptureEvents) -> list[Difference]:
    differences: list[Difference] = []
    opengl_draw_parameters = semantic_draw_parameter_signatures(opengl)
    vulkan_draw_parameters = semantic_draw_parameter_signatures(vulkan)

    all_draw_state_keys = sorted(set(opengl.draw_states) | set(vulkan.draw_states))
    for key in all_draw_state_keys:
        gl_events = opengl.draw_states.get(key, [])
        vk_events = vulkan.draw_states.get(key, [])
        key_text = f"semanticDrawKey={key}"
        if not gl_events or not vk_events:
            differences.append(Difference(
                severity=35,
                category="backend-only-draw-state",
                key=key_text,
                reason="fixed-function draw-state snapshot only observed on one backend",
                opengl_count=len(gl_events),
                vulkan_count=len(vk_events),
                opengl_values=[event.fixed_function_signature for event in gl_events[:4]],
                vulkan_values=[event.fixed_function_signature for event in vk_events[:4]],
            ))
            continue
        gl_sigs = sorted({event.fixed_function_signature for event in gl_events})
        vk_sigs = sorted({event.fixed_function_signature for event in vk_events})
        if set(gl_sigs) == set(vk_sigs):
            continue
        gl_without_viewport = sorted({without_signature_field(signature, "viewport") for signature in gl_sigs})
        vk_without_viewport = sorted({without_signature_field(signature, "viewport") for signature in vk_sigs})
        gl_targets = sorted({event.logical_target_signature for event in gl_events})
        vk_targets = sorted({event.logical_target_signature for event in vk_events})
        gl_draws = sorted({event.draw for event in gl_events})
        vk_draws = sorted({event.draw for event in vk_events})
        if gl_draws != vk_draws:
            category = "draw-state-semantic-draw-parameter-matching-gap"
            severity = 38
            reason = "same semantic draw key has different physical draw parameters; state records should not be paired one-to-one"
        elif gl_targets != vk_targets:
            category = "strict-render-target-state-mismatch"
            severity = 92
            reason = "matched semantic draw has different logical output target or attachment behavior"
        elif gl_without_viewport == vk_without_viewport:
            category = "draw-state-viewport-coverage-gap"
            severity = 36
            reason = "matched semantic draw differs only because one backend did not report a comparable viewport snapshot"
        elif is_gui_item_atlas_pip_state_gap(gl_events, vk_events, gl_sigs, vk_sigs):
            category = "draw-state-gui-item-atlas-pip-implementation-gap"
            severity = 35
            reason = "OpenGL renders this GUI item draw through the shared item atlas tile while Vulkan renders the equivalent item through a per-item PIP target; viewport/scissor are intentionally not one-to-one"
        else:
            category = "strict-fixed-function-state-mismatch"
            severity = 90
            reason = "matched semantic draw has different normalized fixed-function state"
        differences.append(Difference(
            severity=severity,
            category=category,
            key=key_text,
            reason=reason,
            opengl_count=len(gl_events),
            vulkan_count=len(vk_events),
            opengl_values=gl_sigs[:4],
            vulkan_values=vk_sigs[:4],
        ))

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
                semantic_equivalent, semantic_detail = semantic_float_values_equivalent(gl_records, vk_records)
                if semantic_equivalent:
                    differences.append(Difference(
                        severity=20,
                        category="ubo-byte-different-semantic-float-equivalent",
                        key=key_text,
                        reason="same semantic UBO has different raw payload hashes, but decoded semantic float values are equal within tolerance",
                        opengl_count=len(gl_records),
                        vulkan_count=len(vk_records),
                        opengl_values=[semantic_detail, *gl_payloads[:3]],
                        vulkan_values=[semantic_detail, *vk_payloads[:3]],
                    ))
                    continue
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
                if mismatch_is_transient_window_warmup_with_later_match(opengl, vulkan, key, gl_records, vk_records):
                    differences.append(Difference(
                        severity=35,
                        category="transient-window-ubo-warmup-mismatch-later-frame-matches",
                        key=key_text,
                        reason="same semantic UBO differs in an earlier stable-window frame, but a later observed frame for the same semantic input has a shared payload hash",
                        opengl_count=len(gl_records),
                        vulkan_count=len(vk_records),
                        opengl_values=gl_payloads[:4],
                        vulkan_values=vk_payloads[:4],
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
                elif sampler_metadata_incomplete(gl_records) or sampler_metadata_incomplete(vk_records):
                    reason = "same semantic sampler was observed on both backends, but at least one side lacks comparable texture view metadata"
                    severity = 42
                    category = "sampler-metadata-not-comparable"
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
            decoded_status, decoded_reason = decoded_float_sets_equivalent(gl_events, vk_events)
            if decoded_status == "equivalent":
                differences.append(Difference(
                    severity=18,
                    category="standalone-uniform-byte-different-semantic-float-equivalent",
                    key=name,
                    reason="same standalone uniform semantic key has disjoint payload hashes, but decoded float values match within tolerance; raw byte/hash difference is representational",
                    opengl_count=len(gl_events),
                    vulkan_count=len(vk_events),
                    opengl_values=[decoded_reason, *gl_values[:5]],
                    vulkan_values=[decoded_reason, *vk_values[:5]],
                ))
                continue
            if decoded_status == "different":
                differences.append(Difference(
                    severity=96,
                    category="strict-standalone-uniform-decoded-float-mismatch",
                    key=name,
                    reason="same standalone uniform semantic key has decoded float values that differ beyond tolerance",
                    opengl_count=len(gl_events),
                    vulkan_count=len(vk_events),
                    opengl_values=[event.decoded for event in gl_events[:4]],
                    vulkan_values=[event.decoded for event in vk_events[:4]],
                ))
                continue

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

    gl_program_level_standalone = standalone_uniform_program_level_index(opengl.standalone_uniforms)
    vk_program_level_block_members = standalone_uniform_program_level_index(vulkan.standalone_uniform_block_members)

    all_standalone_names = sorted(set(opengl.standalone_uniforms) | set(vulkan.standalone_uniforms))
    for name in all_standalone_names:
        gl_events = opengl.standalone_uniforms.get(name, [])
        vk_events = vulkan.standalone_uniforms.get(name, [])
        if not gl_events or not vk_events:
            if gl_events and not vk_events and name in vulkan.standalone_uniform_block_members:
                continue
            if gl_events and not vk_events:
                paired_block_members: list[StandaloneUniformEvent | StandaloneUniformBlockMemberEvent] = []
                for event in gl_events:
                    paired_block_members.extend(vk_program_level_block_members.get(standalone_uniform_program_level_key(event), []))
                if paired_block_members:
                    decoded_status, decoded_reason = decoded_float_sets_equivalent(gl_events, paired_block_members)  # type: ignore[arg-type]
                    if decoded_status == "equivalent":
                        differences.append(Difference(
                            severity=12,
                            category="standalone-vs-ubo-program-level-equivalent",
                            key=f"name={name}",
                            reason="OpenGL updated this standalone uniform at program scope and Vulkan materialized the same value as per-draw UBO members; decoded values match within tolerance",
                            opengl_count=len(gl_events),
                            vulkan_count=len(paired_block_members),
                            opengl_values=[decoded_reason],
                            vulkan_values=[decoded_reason],
                        ))
                        continue
                    if decoded_status == "different":
                        overlap, overlap_reason = decoded_float_sets_overlap(gl_events, paired_block_members)  # type: ignore[arg-type]
                        if overlap:
                            differences.append(Difference(
                                severity=38,
                                category="timing-sensitive-standalone-vs-ubo-program-level-decoded-set-difference",
                                key=f"name={name}",
                                reason="OpenGL standalone uniform and Vulkan materialized UBO members share decoded values but separate captures observed extra dynamic states",
                                opengl_count=len(gl_events),
                                vulkan_count=len(paired_block_members),
                                opengl_values=[overlap_reason, *[event.decoded or event.sample for event in gl_events[:3]]],
                                vulkan_values=[overlap_reason, *[event.decoded or event.sample for event in paired_block_members[:3]]],
                            ))
                            continue
                        differences.append(Difference(
                            severity=98,
                            category="strict-standalone-vs-ubo-program-level-decoded-float-mismatch",
                            key=f"name={name}",
                            reason="OpenGL standalone uniform and Vulkan materialized UBO members match by program/phase/name/type/pose but decoded values differ beyond tolerance",
                            opengl_count=len(gl_events),
                            vulkan_count=len(paired_block_members),
                            opengl_values=[event.decoded or event.sample for event in gl_events[:4]],
                            vulkan_values=[event.decoded or event.sample for event in paired_block_members[:4]],
                        ))
                        continue
            has_semantic_identity = standalone_uniform_key_has_semantic_identity(name)
            category = (
                "backend-only-standalone-uniform"
                if has_semantic_identity
                else "standalone-uniform-missing-semantic-identity"
            )
            differences.append(Difference(
                severity=35 if has_semantic_identity else 28,
                category=category,
                key=f"name={name}",
                reason=(
                    "standalone uniform update only observed on one backend despite a stable semantic draw identity; classify as missing input unless paired with a Vulkan UBO member"
                    if has_semantic_identity
                    else "standalone uniform update lacks stable semantic draw identity, so the audit cannot determine whether the opposite backend has the equivalent input"
                ),
                opengl_count=len(gl_events),
                vulkan_count=len(vk_events),
                opengl_values=sorted({event.signature for event in gl_events})[:4],
                vulkan_values=sorted({event.signature for event in vk_events})[:4],
            ))
            continue

        gl_values = sorted({event.signature for event in gl_events})
        vk_values = sorted({event.signature for event in vk_events})
        if set(gl_values) != set(vk_values):
            decoded_status, decoded_reason = decoded_float_sets_equivalent(gl_events, vk_events)
            if decoded_status == "equivalent":
                differences.append(Difference(
                    severity=18,
                    category="standalone-ubo-member-byte-different-semantic-float-equivalent",
                    key=f"name={name}",
                    reason="OpenGL standalone uniform and Vulkan materialized UBO member have disjoint payload hashes, but decoded float values match within tolerance; raw byte/hash difference is representational",
                    opengl_count=len(gl_events),
                    vulkan_count=len(vk_events),
                    opengl_values=[decoded_reason, *gl_values[:5]],
                    vulkan_values=[decoded_reason, *vk_values[:5]],
                ))
                continue
            if decoded_status == "different":
                overlap, overlap_reason = decoded_float_sets_overlap(gl_events, vk_events)
                if overlap:
                    differences.append(Difference(
                        severity=35,
                        category="timing-sensitive-standalone-ubo-member-decoded-set-difference",
                        key=f"name={name}",
                        reason="OpenGL standalone uniform and Vulkan materialized UBO member share decoded values but separate captures observed extra dynamic states",
                        opengl_count=len(gl_events),
                        vulkan_count=len(vk_events),
                        opengl_values=[overlap_reason, *[event.decoded for event in gl_events[:3]]],
                        vulkan_values=[overlap_reason, *[event.decoded for event in vk_events[:3]]],
                    ))
                    continue
                differences.append(Difference(
                    severity=98,
                    category="strict-standalone-ubo-member-decoded-float-mismatch",
                    key=f"name={name}",
                    reason="OpenGL standalone uniform and Vulkan materialized UBO member decoded float values differ beyond tolerance",
                    opengl_count=len(gl_events),
                    vulkan_count=len(vk_events),
                    opengl_values=[event.decoded for event in gl_events[:4]],
                    vulkan_values=[event.decoded for event in vk_events[:4]],
                ))
                continue

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

            if not any(event.sample for event in gl_events + vk_events):
                differences.append(Difference(
                    severity=60,
                    category="standalone-uniform-hash-only-not-comparable",
                    key=name,
                    reason="same standalone uniform semantic key has disjoint payload hashes, but no decoded sample/semantic values were logged; this is a comparison blocker, not a proven semantic mismatch",
                    opengl_count=len(gl_events),
                    vulkan_count=len(vk_events),
                    opengl_values=gl_values[:6],
                    vulkan_values=vk_values[:6],
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
            paired_gl_events = gl_program_level_standalone.get(standalone_uniform_program_level_key(vk_events[0]), [])
            if paired_gl_events:
                decoded_status, decoded_reason = decoded_float_sets_equivalent(paired_gl_events, vk_events)  # type: ignore[arg-type]
                if decoded_status == "equivalent":
                    differences.append(Difference(
                        severity=12,
                        category="standalone-vs-ubo-program-level-equivalent",
                        key=f"name={name}",
                        reason="Vulkan materialized this standalone uniform as per-draw UBO members and the matching OpenGL program-scope standalone setter has equivalent decoded values",
                        opengl_count=len(paired_gl_events),
                        vulkan_count=len(vk_events),
                        opengl_values=[decoded_reason],
                        vulkan_values=[decoded_reason],
                    ))
                    continue
                if decoded_status == "different":
                    overlap, overlap_reason = decoded_float_sets_overlap(paired_gl_events, vk_events)  # type: ignore[arg-type]
                    if overlap:
                        differences.append(Difference(
                            severity=38,
                            category="timing-sensitive-standalone-vs-ubo-program-level-decoded-set-difference",
                            key=f"name={name}",
                            reason="Vulkan materialized UBO member and OpenGL program-scope standalone uniform share decoded values but separate captures observed extra dynamic states",
                            opengl_count=len(paired_gl_events),
                            vulkan_count=len(vk_events),
                            opengl_values=[overlap_reason, *[event.decoded or event.sample for event in paired_gl_events[:3]]],
                            vulkan_values=[overlap_reason, *[event.decoded or event.sample for event in vk_events[:3]]],
                        ))
                        continue
                    differences.append(Difference(
                        severity=98,
                        category="strict-standalone-vs-ubo-program-level-decoded-float-mismatch",
                        key=f"name={name}",
                        reason="Vulkan materialized UBO member and OpenGL program-scope standalone uniform match by program/phase/name/type/pose but decoded values differ beyond tolerance",
                        opengl_count=len(paired_gl_events),
                        vulkan_count=len(vk_events),
                        opengl_values=[event.decoded or event.sample for event in paired_gl_events[:4]],
                        vulkan_values=[event.decoded or event.sample for event in vk_events[:4]],
                    ))
                    continue
            has_semantic_identity = standalone_uniform_key_has_semantic_identity(name)
            differences.append(Difference(
                severity=25 if has_semantic_identity else 22,
                category=(
                    "standalone-vs-ubo-representation-unpaired"
                    if has_semantic_identity
                    else "standalone-ubo-member-missing-semantic-identity"
                ),
                key=f"name={name}",
                reason=(
                    "Vulkan materialized this standalone uniform as a UBO member at a stable semantic draw, but the matching OpenGL standalone setter was not observed"
                    if has_semantic_identity
                    else "Vulkan materialized this standalone uniform as a UBO member without a stable semantic draw identity, so it cannot be paired with OpenGL yet"
                ),
                opengl_count=0,
                vulkan_count=len(vk_events),
                opengl_values=[],
                vulkan_values=sorted({event.signature for event in vk_events})[:4],
            ))
            continue

        gl_values = sorted({event.signature for event in gl_events})
        vk_values = sorted({event.signature for event in vk_events})
        if set(gl_values) != set(vk_values):
            decoded_status, decoded_reason = decoded_float_sets_equivalent(gl_events, vk_events)
            if decoded_status == "equivalent":
                differences.append(Difference(
                    severity=12,
                    category="standalone-vs-ubo-decoded-float-equivalent",
                    key=f"name={name}",
                    reason="OpenGL standalone uniform and Vulkan materialized UBO member have different raw payload hashes, but decoded float values match within tolerance",
                    opengl_count=len(gl_events),
                    vulkan_count=len(vk_events),
                    opengl_values=[decoded_reason, *gl_values[:5]],
                    vulkan_values=[decoded_reason, *vk_values[:5]],
                ))
                continue
            if decoded_status == "different":
                overlap, overlap_reason = decoded_float_sets_overlap(gl_events, vk_events)
                if overlap:
                    differences.append(Difference(
                        severity=35,
                        category="timing-sensitive-standalone-ubo-member-decoded-set-difference",
                        key=f"name={name}",
                        reason="OpenGL standalone uniform and Vulkan materialized UBO member share decoded values but separate captures observed extra dynamic states",
                        opengl_count=len(gl_events),
                        vulkan_count=len(vk_events),
                        opengl_values=[overlap_reason, *[event.decoded or event.sample for event in gl_events[:3]]],
                        vulkan_values=[overlap_reason, *[event.decoded or event.sample for event in vk_events[:3]]],
                    ))
                    continue
                differences.append(Difference(
                    severity=98,
                    category="strict-standalone-ubo-member-decoded-float-mismatch",
                    key=f"name={name}",
                    reason="OpenGL standalone uniform and Vulkan materialized UBO member decoded values differ beyond tolerance",
                    opengl_count=len(gl_events),
                    vulkan_count=len(vk_events),
                    opengl_values=[event.decoded or event.sample for event in gl_events[:4]],
                    vulkan_values=[event.decoded or event.sample for event in vk_events[:4]],
                ))
                continue
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
        record.semantic_subsystem,
        record.semantic_phase,
        record.semantic_pass,
        record.semantic_pipeline,
        record.semantic_material,
        record.semantic_output,
        record.projection_label,
    ]).lower()
    if record.semantic_subsystem == "sodium-terrain" or "sodium-terrain" in text:
        return "Sodium terrain"
    if (
        record.semantic_subsystem == "distant-horizons"
        or "distant-horizons" in text
        or "distant horizons" in text
        or "dhdepth" in text
    ):
        return "Distant Horizons"
    if "iris:shadow" in text or "shadow" in text and "iris:" in text:
        return "Iris shadow passes"
    if "iris:deferred" in text or "deferred" in text and "iris:" in text:
        return "Iris deferred passes"
    if "iris:composite" in text or "composite" in text and "iris:" in text:
        return "Iris composite passes"
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


def semantic_draw_coverage(opengl_events: CaptureEvents, vulkan_events: CaptureEvents, settled_ready_frames: int = 0) -> dict[str, object]:
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
    geometry = geometry_coverage(opengl_events, vulkan_events, set(gl_by_signature) & set(vk_by_signature), settled_ready_frames)

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


SETTLED_READY_GEOMETRY_SUBSYSTEMS = {"sodium-terrain", "distant-horizons"}


def geometry_coverage(
    opengl_events: CaptureEvents,
    vulkan_events: CaptureEvents,
    matched_signatures: set[str],
    settled_ready_frames: int = 0,
) -> dict[str, object]:
    gl_sampler_signatures = sampler_resource_signatures_by_draw_key(opengl_events)
    vk_sampler_signatures = sampler_resource_signatures_by_draw_key(vulkan_events)
    settled_gate = settled_ready_geometry_gate(
        opengl_events,
        vulkan_events,
        gl_sampler_signatures,
        vk_sampler_signatures,
        settled_ready_frames,
    )
    gl_geometry: dict[str, list[GeometryEvent]] = defaultdict(list)
    vk_geometry: dict[str, list[GeometryEvent]] = defaultdict(list)
    for event in opengl_events.geometry:
        if (
            event.semantic_match_signature in matched_signatures
            or (
                settled_gate.get("enabled")
                and event.semantic_subsystem in SETTLED_READY_GEOMETRY_SUBSYSTEMS
            )
        ):
            signature = settled_geometry_match_signature(event, gl_sampler_signatures, settled_gate, "opengl")
            if signature:
                gl_geometry[signature].append(event)
    for event in vulkan_events.geometry:
        if (
            event.semantic_match_signature in matched_signatures
            or (
                settled_gate.get("enabled")
                and event.semantic_subsystem in SETTLED_READY_GEOMETRY_SUBSYSTEMS
            )
        ):
            signature = settled_geometry_match_signature(event, vk_sampler_signatures, settled_gate, "vulkan")
            if signature:
                vk_geometry[signature].append(event)

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
        if generated_geometry_equivalent(gl_events, vk_events, gl_not_comparable, vk_not_comparable):
            classification = "equivalent"
            reason = "generated-fullscreen-geometry"
        elif gl_not_comparable or vk_not_comparable:
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
        "settled_ready_gate": settled_ready_gate_for_report(settled_gate),
        "group_results": group_results,
    }


def settled_ready_gate_for_report(gate: dict[str, object]) -> dict[str, object]:
    if not gate.get("enabled"):
        return {"enabled": False}

    def clean_family(entry: dict[str, object]) -> dict[str, object]:
        cleaned = dict(entry)
        if "work" in cleaned:
            cleaned["work"] = sorted(cleaned["work"])
        return cleaned

    return {
        "enabled": True,
        "settled_ready_frames": gate.get("settled_ready_frames", 0),
        "summary": gate.get("summary", {}),
        "opengl": {
            family: clean_family(entry)
            for family, entry in gate.get("opengl", {}).items()
        },
        "vulkan": {
            family: clean_family(entry)
            for family, entry in gate.get("vulkan", {}).items()
        },
        "intersections": {
            family: sorted(values)
            for family, values in gate.get("intersections", {}).items()
        },
    }


def settled_geometry_match_signature(
    event: GeometryEvent,
    sampler_signatures: dict[str, str],
    settled_gate: dict[str, object],
    backend: str,
) -> str:
    if settled_gate.get("enabled") and event.semantic_subsystem in SETTLED_READY_GEOMETRY_SUBSYSTEMS:
        family = event.semantic_subsystem
        backend_gate = settled_gate.get(backend, {})
        family_gate = backend_gate.get(family, {}) if isinstance(backend_gate, dict) else {}
        frame = family_gate.get("selected_frame")
        if frame is None or str(event.det_rendered_frame) != str(frame):
            return ""
        signature = geometry_logical_work_signature(event, sampler_signatures)
        intersection = settled_gate.get("intersections", {}).get(family, set())
        if signature not in intersection:
            return ""
        return signature
    return geometry_match_signature(event, sampler_signatures)


def geometry_logical_work_signature(event: GeometryEvent, sampler_signatures: dict[str, str]) -> str:
    signature = geometry_match_signature(event, sampler_signatures)
    signature = re.sub(r"\|frame=[^|]+", "|frame=settled", signature)
    if event.semantic_subsystem == "sodium-terrain":
        signature = re.sub(r";vf=[^;:|]+", ";vf=settled", signature)
        signature = re.sub(r";h=[^:|]+", ";h=settled", signature)
        signature = re.sub(r":draws=[^|]+", "", signature)
    return signature


def settled_ready_geometry_gate(
    opengl_events: CaptureEvents,
    vulkan_events: CaptureEvents,
    gl_sampler_signatures: dict[str, str],
    vk_sampler_signatures: dict[str, str],
    settled_ready_frames: int,
) -> dict[str, object]:
    if settled_ready_frames <= 0:
        return {"enabled": False}

    gl = settled_ready_backend_geometry(opengl_events, gl_sampler_signatures, settled_ready_frames)
    vk = settled_ready_backend_geometry(vulkan_events, vk_sampler_signatures, settled_ready_frames)
    intersections: dict[str, set[str]] = {}
    families = sorted(SETTLED_READY_GEOMETRY_SUBSYSTEMS)
    for family in families:
        gl_set = set(gl.get(family, {}).get("work", set()))
        vk_set = set(vk.get(family, {}).get("work", set()))
        intersections[family] = gl_set & vk_set

    return {
        "enabled": True,
        "settled_ready_frames": settled_ready_frames,
        "opengl": gl,
        "vulkan": vk,
        "intersections": intersections,
        "summary": {
            family: {
                "opengl_work": len(gl.get(family, {}).get("work", set())),
                "vulkan_work": len(vk.get(family, {}).get("work", set())),
                "intersection_work": len(intersections.get(family, set())),
                "opengl_selected_frame": gl.get(family, {}).get("selected_frame"),
                "vulkan_selected_frame": vk.get(family, {}).get("selected_frame"),
                "opengl_status": gl.get(family, {}).get("status", "missing"),
                "vulkan_status": vk.get(family, {}).get("status", "missing"),
            }
            for family in families
        },
    }


def settled_ready_backend_geometry(
    events: CaptureEvents,
    sampler_signatures: dict[str, str],
    settled_ready_frames: int,
) -> dict[str, dict[str, object]]:
    by_family_frame: dict[str, dict[int, set[str]]] = {
        family: defaultdict(set) for family in SETTLED_READY_GEOMETRY_SUBSYSTEMS
    }
    for event in events.geometry:
        if event.semantic_subsystem not in SETTLED_READY_GEOMETRY_SUBSYSTEMS:
            continue
        if not str(event.det_rendered_frame).isdigit():
            continue
        by_family_frame[event.semantic_subsystem][int(event.det_rendered_frame)].add(
            geometry_logical_work_signature(event, sampler_signatures)
        )

    result: dict[str, dict[str, object]] = {}
    for family, frames in by_family_frame.items():
        result[family] = find_settled_ready_window(frames, settled_ready_frames)
    return result


def find_settled_ready_window(frames: dict[int, set[str]], settled_ready_frames: int) -> dict[str, object]:
    if not frames:
        return {"status": "no-work-observed", "selected_frame": None, "work": set()}
    sorted_frames = sorted(frames)
    selected_frame = None
    selected_work: set[str] = set()
    selected_latest_work_count = 0
    frame_set = set(sorted_frames)
    for frame in sorted_frames:
        start = frame - settled_ready_frames + 1
        if start < 0:
            continue
        window = list(range(start, frame + 1))
        if any(candidate not in frame_set for candidate in window):
            continue
        stable_work = set(frames[window[0]])
        if not stable_work:
            continue
        for candidate in window[1:]:
            stable_work &= frames[candidate]
        if stable_work:
            selected_frame = frame
            selected_work = stable_work
            selected_latest_work_count = len(frames[frame])
    if selected_frame is None:
        last_frame = sorted_frames[-1]
        return {
            "status": "no-stable-intersection",
            "selected_frame": None,
            "observed_frames": sorted_frames,
            "latest_frame": last_frame,
            "latest_work_count": len(frames[last_frame]),
            "work": set(),
        }
    return {
        "status": "stable-intersection",
        "selected_frame": selected_frame,
        "window_start": selected_frame - settled_ready_frames + 1,
        "work_count": len(selected_work),
        "latest_work_count": selected_latest_work_count,
        "excluded_work_count": max(0, selected_latest_work_count - len(selected_work)),
        "work": selected_work,
    }


def generated_geometry_equivalent(
    gl_events: list[GeometryEvent],
    vk_events: list[GeometryEvent],
    gl_not_comparable: list[str],
    vk_not_comparable: list[str],
) -> bool:
    if not gl_events or not vk_events:
        return False
    if set(gl_not_comparable) != {"vertex-buffer-missing-or-closed"}:
        return False
    if set(vk_not_comparable) != {"vertex-buffer-missing-or-closed"}:
        return False
    if len(gl_events) != len(vk_events):
        return False

    generated_vertex_shaders = {"minecraft:core/screenquad"}
    generated_pipelines = {
        "minecraft:pipeline/blit",
        "minecraft:pipeline/blit_screen",
        "minecraft:pipeline/entity_outline_blit",
        "minecraft:pipeline/lightmap",
    }
    for gl_event, vk_event in zip(gl_events, vk_events):
        if gl_event.semantic_vertex_shader not in generated_vertex_shaders:
            return False
        if vk_event.semantic_vertex_shader != gl_event.semantic_vertex_shader:
            return False
        if gl_event.semantic_pipeline not in generated_pipelines and vk_event.semantic_pipeline not in generated_pipelines:
            return False
        if (
            gl_event.mode,
            gl_event.indexed,
            gl_event.total_vertices,
            gl_event.total_indices,
            gl_event.total_primitives,
            gl_event.instances,
        ) != (
            vk_event.mode,
            vk_event.indexed,
            vk_event.total_vertices,
            vk_event.total_indices,
            vk_event.total_primitives,
            vk_event.instances,
        ):
            return False
    return True


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
    if "ctx=gui:gui-item:" in event.semantic_pass:
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
    if "samplers=" in signature and "numeric-gl-label" in signature:
        if gl_events and not vk_events:
            return "opengl-legacy-texture-context-not-resolved"
        if not gl_events and vk_events:
            return "vulkan-texture-context-not-paired-with-opengl-legacy-label"
    if (
        "pipeline=minecraft:pipeline/gui_textured|" in signature
        and "samplers=" in signature
        and not gl_events
        and vk_events
    ):
        return "vulkan-gui-texture-context-not-paired-with-opengl-label"
    if (
        "pipeline=minecraft:pipeline/entity_" in signature
        and "samplers=" in signature
        and not gl_events
        and vk_events
    ):
        return "vulkan-entity-texture-context-not-paired-with-opengl-label"
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
    if (
        "pipeline=minecraft:pipeline/gui_textured|" in signature
        and "ctx=gui:gui-item:" in signature
        and ".UV0@" in first_opengl
        and ".UV0@" in first_vulkan
    ):
        return "gui-item-atlas-vs-pip-uv-representation"
    if (
        "pipeline=minecraft:pipeline/gui_textured|" in signature
        and "ctx=gui:gui-item:" in signature
    ):
        return "gui-item-atlas-vs-pip-geometry-representation"
    if "pipeline=minecraft:pipeline/gui_textured|" in signature and "label=voxelmap-map-" in signature:
        return "voxelmap-minimap-source-offset-angle-state-not-logged"
    if "pipeline=minecraft:pipeline/entity_" in signature and ".POSITION0@" in first_opengl and ".POSITION0@" in first_vulkan:
        return "entity-animation-pose-state-not-logged"
    if "pipeline=minecraft:pipeline/armor_" in signature and ".POSITION0@" in first_opengl and ".POSITION0@" in first_vulkan:
        return "entity-armor-animation-pose-state-not-logged"
    if (
        "pipeline=minecraft:pipeline/armor_" in signature
        and "|projection:perspective:Iris_shadow_map_projection|" in signature
    ):
        return "entity-armor-animation-pose-state-not-logged"
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


def ordering_visibility_coverage(
    opengl_events: CaptureEvents,
    vulkan_events: CaptureEvents,
    differences: list[Difference] | None = None,
) -> dict[str, object]:
    def operation_counts(events: list[OrderingEvent]) -> dict[str, int]:
        return dict(Counter(event.operation or "unknown" for event in events))

    def pose_operation_counts(events: list[OrderingEvent]) -> dict[str, dict[str, int]]:
        grouped: dict[str, Counter] = defaultdict(Counter)
        for event in events:
            grouped[event.pose_key][event.operation or "unknown"] += 1
        return {pose: dict(counter) for pose, counter in sorted(grouped.items())}

    def ordering_draw_match_signature(event: SemanticDrawEvent) -> str:
        return "|".join([
            f"subsystem={event.semantic_subsystem or 'unknown'}",
            f"phase={event.semantic_phase or 'unknown'}",
            f"pass={event.semantic_pass or 'unknown'}",
            f"pipeline={event.semantic_pipeline or 'unknown'}",
            f"vertex={event.semantic_vertex_shader or 'unknown'}",
            f"fragment={event.semantic_fragment_shader or 'unknown'}",
            f"material={event.semantic_material or 'unknown'}",
            f"output={event.semantic_output or 'unknown'}",
            f"pose={event.det_pose or 'none'}",
        ])

    def semantic_draw_index(events: CaptureEvents) -> tuple[dict[str, list[SemanticDrawEvent]], dict[str, str]]:
        by_signature: dict[str, list[SemanticDrawEvent]] = defaultdict(list)
        key_to_signature: dict[str, str] = {}
        for event in events.semantic_draws:
            signature = ordering_draw_match_signature(event)
            by_signature[signature].append(event)
            if event.semantic_group_key != "unavailable":
                key_to_signature[event.semantic_group_key] = signature
        return by_signature, key_to_signature

    def keys_for_signature(by_signature: dict[str, list[SemanticDrawEvent]], signature: str) -> set[str]:
        return {
            event.semantic_group_key for event in by_signature.get(signature, [])
            if event.semantic_group_key != "unavailable"
        }

    def resource_keys(events: CaptureEvents, keys: set[str]) -> set[str]:
        observed: set[str] = set()
        for records in events.resources.values():
            for record in records:
                if record.semantic_draw_key in keys:
                    observed.add("|".join([
                        f"name={record.name}",
                        f"type={record.resource_type}",
                        f"pipeline={record.semantic_pipeline or 'unknown'}",
                        f"material={record.semantic_material or 'unknown'}",
                    ]))
        return observed

    def uniform_keys(events: CaptureEvents, keys: set[str]) -> set[str]:
        observed: set[str] = set()
        for event_list in events.standalone_uniforms.values():
            for event in event_list:
                if event.semantic_draw_key in keys:
                    observed.add(f"name={event.name}|type={event.value_kind}|stages={event.shader_stages}")
        for event_list in events.standalone_uniform_block_members.values():
            for event in event_list:
                if event.semantic_draw_key in keys:
                    observed.add(f"name={event.name}|type={event.value_kind}|stages={event.shader_stages}")
        return observed

    def target_state_signatures(events: CaptureEvents, keys: set[str]) -> set[str]:
        observed: set[str] = set()
        for key in keys:
            for state in events.draw_states.get(key, []):
                observed.add(state.logical_target_signature)
        return observed

    def classify_observed_data(
        name: str,
        gl_by_signature: dict[str, list[SemanticDrawEvent]],
        vk_by_signature: dict[str, list[SemanticDrawEvent]],
        matched_signatures: set[str],
        extractor,
    ) -> dict[str, object]:
        equivalent = 0
        representational_or_empty = 0
        gap = 0
        divergent = 0
        examples: list[dict[str, object]] = []
        for signature in sorted(matched_signatures):
            gl_values = extractor(opengl_events, keys_for_signature(gl_by_signature, signature))
            vk_values = extractor(vulkan_events, keys_for_signature(vk_by_signature, signature))
            if gl_values and vk_values and gl_values == vk_values:
                equivalent += 1
            elif not gl_values and not vk_values:
                representational_or_empty += 1
            elif not gl_values or not vk_values:
                gap += 1
                if len(examples) < 8:
                    examples.append({
                        "signature": signature,
                        "reason": f"{name}-visibility-records-only-on-one-backend",
                        "opengl_records": len(gl_values),
                        "vulkan_records": len(vk_values),
                    })
            else:
                divergent += 1
                if len(examples) < 8:
                    examples.append({
                        "signature": signature,
                        "reason": f"{name}-semantic-key-set-differs",
                        "opengl_examples": sorted(gl_values)[:4],
                        "vulkan_examples": sorted(vk_values)[:4],
                    })
        return {
            "equivalent_groups": equivalent,
            "both_unobserved_or_not_applicable_groups": representational_or_empty,
            "coverage_gap_groups": gap,
            "semantic_key_set_divergent_groups": divergent,
            "examples": examples,
        }

    def normalize_clear_load_events(events: list[OrderingEvent]) -> Counter:
        clear_loads: Counter = Counter()
        for event in events:
            detail = parse_struct_fields(event.detail.replace("|", " "))
            pose = event.pose_key
            if event.operation == "clear":
                mask = detail.get("mask", event.detail)
                if "COLOR" in event.detail or mask in {"0x4000", "0x4100", "0x4500"}:
                    clear_loads[f"{pose}|color-clear"] += 1
                if "DEPTH" in event.detail or mask in {"0x100", "0x4100", "0x4500"}:
                    clear_loads[f"{pose}|depth-clear"] += 1
                continue
            if event.operation != "pass-begin":
                continue
            if detail.get("clearColor") == "true" or detail.get("colorClear") == "true" or detail.get("colorLoad") == "CLEAR":
                clear_loads[f"{pose}|color-clear-or-loadop"] += 1
            if detail.get("clearDepth") == "true" or detail.get("depthClear") == "true" or detail.get("depthLoad") == "CLEAR":
                clear_loads[f"{pose}|depth-clear-or-loadop"] += 1
            if detail.get("colorLoad") == "LOAD":
                clear_loads[f"{pose}|color-load"] += 1
            if detail.get("depthLoad") == "LOAD":
                clear_loads[f"{pose}|depth-load"] += 1
        return clear_loads

    def diff_category_counts() -> Counter:
        counts: Counter = Counter()
        for diff in differences or []:
            counts[diff.category] += 1
        return counts

    def strict_ordering_relevant_categories(counts: Counter) -> dict[str, int]:
        relevant_prefixes = (
            "strict-render-target",
            "strict-fixed-function",
            "strict-resource",
            "strict-sampler",
            "strict-ubo",
            "strict-standalone",
        )
        return {
            category: count for category, count in counts.items()
            if category.startswith(relevant_prefixes)
        }

    gl_operation_counts = operation_counts(opengl_events.ordering)
    vk_operation_counts = operation_counts(vulkan_events.ordering)
    all_operations = sorted(set(gl_operation_counts) | set(vk_operation_counts))
    operation_deltas = {
        operation: {
            "opengl": gl_operation_counts.get(operation, 0),
            "vulkan": vk_operation_counts.get(operation, 0),
        }
        for operation in all_operations
        if gl_operation_counts.get(operation, 0) != vk_operation_counts.get(operation, 0)
    }

    gl_by_signature, _ = semantic_draw_index(opengl_events)
    vk_by_signature, _ = semantic_draw_index(vulkan_events)
    matched_signatures = set(gl_by_signature) & set(vk_by_signature)
    resource_visibility = classify_observed_data(
        "resource",
        gl_by_signature,
        vk_by_signature,
        matched_signatures,
        resource_keys,
    )
    uniform_visibility = classify_observed_data(
        "uniform",
        gl_by_signature,
        vk_by_signature,
        matched_signatures,
        uniform_keys,
    )
    target_visibility = classify_observed_data(
        "target-state",
        gl_by_signature,
        vk_by_signature,
        matched_signatures,
        target_state_signatures,
    )
    gl_clear_loads = normalize_clear_load_events(opengl_events.ordering)
    vk_clear_loads = normalize_clear_load_events(vulkan_events.ordering)
    diff_counts = diff_category_counts()
    strict_categories = strict_ordering_relevant_categories(diff_counts)
    producer_consumer_status = "coverage-gap:no-scoped-content-history-for-no-shader-targets"
    if not strict_categories:
        producer_consumer_status = "no-strict-input-or-target-divergence-observed-for-stable-matched-groups"

    return {
        "schema": "mattmc.vulkan_parity.ordering_visibility.v2",
        "opengl_ordering_events": len(opengl_events.ordering),
        "vulkan_ordering_events": len(vulkan_events.ordering),
        "stable_matched_semantic_groups": len(matched_signatures),
        "raw_operation_counts": {
            "opengl": gl_operation_counts,
            "vulkan": vk_operation_counts,
        },
        "raw_operation_count_deltas_backend_internal": operation_deltas,
        "pose_operation_counts": {
            "opengl": pose_operation_counts(opengl_events.ordering),
            "vulkan": pose_operation_counts(vulkan_events.ordering),
        },
        "draw_observed_resource_visibility": resource_visibility,
        "draw_observed_uniform_visibility": uniform_visibility,
        "draw_observed_target_state_visibility": target_visibility,
        "logical_clear_load_visibility": {
            "opengl": dict(sorted(gl_clear_loads.items())),
            "vulkan": dict(sorted(vk_clear_loads.items())),
            "classification": (
                "representational-or-backend-internal"
                if not any(category.startswith("strict-render-target") for category in strict_categories)
                else "strict-render-target-divergence-present"
            ),
            "reason": (
                "clear/load operation counts are not compared directly; matched draw target state is the semantic visibility proof"
            ),
        },
        "uniform_resource_update_before_draw": {
            "classification": "covered-by-draw-attached-resource-and-uniform-records",
            "resource_groups": resource_visibility,
            "uniform_groups": uniform_visibility,
        },
        "producer_consumer_visibility": {
            "classification": producer_consumer_status,
            "reason": "no-shader ordering currently proves draw-observed inputs/state; full prior attachment content history needs scoped target hashes",
        },
        "strict_ordering_relevant_difference_categories": strict_categories,
        "backend_internal_differences": {
            "buffer_uploads": {
                "opengl": gl_operation_counts.get("buffer-upload", 0),
                "vulkan": vk_operation_counts.get("buffer-upload", 0),
                "classification": "backend-internal",
                "reason": "buffer writes are staged/sliced differently; geometry, UBO, and uniform draw-observed records are the semantic visibility proof",
            },
            "pass_lifetime": {
                "opengl_pass_begin": gl_operation_counts.get("pass-begin", 0),
                "opengl_pass_end": gl_operation_counts.get("pass-end", 0),
                "vulkan_pass_begin": vk_operation_counts.get("pass-begin", 0),
                "vulkan_pass_end": vk_operation_counts.get("pass-end", 0),
                "classification": "backend-internal",
                "reason": "native Vulkan render-pass boundaries and OpenGL compatibility pass lifetimes are not one-to-one; logical target state is compared per semantic draw",
            },
            "uniform_updates": {
                "opengl": gl_operation_counts.get("uniform-update", 0),
                "vulkan": vk_operation_counts.get("uniform-update", 0),
                "classification": "backend-internal",
                "reason": "raw setter/update counts differ because Vulkan materializes standalone uniforms into UBO-backed state; decoded uniform and UBO records are compared semantically",
            },
            "resource_binds": {
                "opengl": gl_operation_counts.get("resource-bind", 0),
                "vulkan": vk_operation_counts.get("resource-bind", 0),
                "classification": "backend-internal",
                "reason": "descriptor/resource binding counts differ by backend; draw-attached semantic resource metadata is compared instead",
            },
        },
    }


def build_coverage_report(
    opengl_events: CaptureEvents,
    vulkan_events: CaptureEvents,
    differences: list[Difference],
    pose_filter: str | None = None,
    stable_pose_only: bool = False,
    stable_window_frames: int = 1,
    settled_ready_frames: int = 0,
) -> dict[str, object]:
    families: dict[str, dict[str, object]] = {family: _empty_family_coverage() for family in PASS_FAMILIES}
    semantic_coverage = semantic_draw_coverage(opengl_events, vulkan_events, settled_ready_frames)
    ordering_coverage = ordering_visibility_coverage(opengl_events, vulkan_events, differences)
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
        "pose_filter": pose_filter or "all",
        "stable_pose_only": stable_pose_only,
        "stable_window_frames": max(1, stable_window_frames) if stable_pose_only else 0,
        "settled_ready_frames": max(0, settled_ready_frames),
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
        "ordering_visibility": ordering_coverage,
        "global_blockers": {
            "draw_structure": "semantic draw identity is present, but some legacy/raw backend draw records may still lack a semantic key",
            "geometry_input_bytes": "missing consumed vertex/index range hashes",
            "render_target_state": "normalized semantic ordering events are present for pass begin/end and clears; attachment load/store parity still depends on descriptor coverage",
            "fixed_function_state": "missing normalized pipeline-state events",
            "shader_construction": "not emitted by this capture log format",
        },
        "families": families,
    }


def render_diff_report(
    opengl_log: Path,
    vulkan_log: Path,
    limit: int,
    parse_limits: ParseLimits | None = None,
    pose_filter: str | None = None,
    stable_pose_only: bool = False,
    stable_window_frames: int = 1,
    settled_ready_frames: int = 0,
) -> str:
    parse_limits = parse_limits or ParseLimits()
    opengl_events = filter_capture_events_by_pose(
        parse_capture_log(opengl_log, parse_limits),
        pose_filter,
        stable_pose_only,
        stable_window_frames,
    )
    vulkan_events = filter_capture_events_by_pose(
        parse_capture_log(vulkan_log, parse_limits),
        pose_filter,
        stable_pose_only,
        stable_window_frames,
    )
    differences = compare_capture_events(opengl_events, vulkan_events)
    by_category = Counter(diff.category for diff in differences)

    lines: list[str] = []
    lines.append("Vulkan Shader Input Parity Audit")
    lines.append("================================")
    lines.append(f"OpenGL capture: {opengl_log}")
    lines.append(f"Vulkan capture: {vulkan_log}")
    if pose_filter:
        lines.append(f"Pose filter: {pose_filter}")
    if stable_pose_only:
        lines.append(f"Stable pose only: true (windowFrames={max(1, stable_window_frames)}, ends at first detAwaitingScreenshot=true frame)")
    if settled_ready_frames > 0:
        lines.append(f"Settled ready geometry gate: true (frames={settled_ready_frames})")
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
    semantic_coverage = semantic_draw_coverage(opengl_events, vulkan_events, settled_ready_frames)
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
        gate = geometry.get("settled_ready_gate", {})
        if gate.get("enabled"):
            lines.append("  settled ready intersections:")
            for family, summary in gate.get("summary", {}).items():
                lines.append(
                    "    "
                    + f"{family}: GL frame={summary.get('opengl_selected_frame')} work={summary.get('opengl_work')} "
                    + f"VK frame={summary.get('vulkan_selected_frame')} work={summary.get('vulkan_work')} "
                    + f"intersection={summary.get('intersection_work')} "
                    + f"status={summary.get('opengl_status')}/{summary.get('vulkan_status')}"
                )
        for entry in geometry.get("group_results", [])[:5]:
            lines.append(
                "  "
                + f"[{entry.get('classification')}] "
                + f"GL draws={entry.get('opengl_geometry_events')} VK draws={entry.get('vulkan_geometry_events')} "
                + f"GL verts={entry.get('opengl_total_vertices')} VK verts={entry.get('vulkan_total_vertices')} "
                + str(entry.get("signature", ""))[:160]
            )
    ordering = ordering_visibility_coverage(opengl_events, vulkan_events, differences)
    lines.append("- Semantic ordering / resource visibility:")
    lines.append(f"  schema={ordering.get('schema', 'unknown')}")
    lines.append(f"  stable matched semantic groups={ordering.get('stable_matched_semantic_groups', 0)}")
    resource_visibility = ordering.get("draw_observed_resource_visibility", {})
    uniform_visibility = ordering.get("draw_observed_uniform_visibility", {})
    target_visibility = ordering.get("draw_observed_target_state_visibility", {})
    lines.append(
        "  draw-observed resources: "
        + f"equivalent={resource_visibility.get('equivalent_groups', 0)} "
        + f"bothUnobserved={resource_visibility.get('both_unobserved_or_not_applicable_groups', 0)} "
        + f"gaps={resource_visibility.get('coverage_gap_groups', 0)} "
        + f"keySetDivergent={resource_visibility.get('semantic_key_set_divergent_groups', 0)}"
    )
    lines.append(
        "  draw-observed uniforms: "
        + f"equivalent={uniform_visibility.get('equivalent_groups', 0)} "
        + f"bothUnobserved={uniform_visibility.get('both_unobserved_or_not_applicable_groups', 0)} "
        + f"gaps={uniform_visibility.get('coverage_gap_groups', 0)} "
        + f"keySetDivergent={uniform_visibility.get('semantic_key_set_divergent_groups', 0)}"
    )
    lines.append(
        "  draw-observed target state: "
        + f"equivalent={target_visibility.get('equivalent_groups', 0)} "
        + f"bothUnobserved={target_visibility.get('both_unobserved_or_not_applicable_groups', 0)} "
        + f"gaps={target_visibility.get('coverage_gap_groups', 0)} "
        + f"keySetDivergent={target_visibility.get('semantic_key_set_divergent_groups', 0)}"
    )
    clear_load = ordering.get("logical_clear_load_visibility", {})
    producer_consumer = ordering.get("producer_consumer_visibility", {})
    lines.append(f"  clear/load classification={clear_load.get('classification', 'unknown')}: {clear_load.get('reason', '')}")
    lines.append(f"  producer/consumer classification={producer_consumer.get('classification', 'unknown')}: {producer_consumer.get('reason', '')}")
    strict_ordering = ordering.get("strict_ordering_relevant_difference_categories", {})
    lines.append(f"  strict ordering-relevant categories={strict_ordering}")
    backend_internal = ordering.get("backend_internal_differences", {})
    for name, entry in backend_internal.items():
        lines.append(f"  backend-internal {name}: {entry}")
    for label, coverage in (
        ("resource", resource_visibility),
        ("uniform", uniform_visibility),
        ("target", target_visibility),
    ):
        examples = coverage.get("examples", [])
        if examples:
            lines.append(f"  {label} visibility examples:")
            for example in examples[:3]:
                lines.append("    " + str(example)[:220])
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
    lines.append("- If any strict same-key uniform, UBO, sampler, geometry, or state mismatch exists, fix that before ordering work.")
    lines.append("- If no strict input/state mismatch exists for stable matched groups, move to semantic ordering and resource-update visibility.")
    lines.append("- Close ubo-payload-not-comparable coverage before treating newly matched UBO inputs as proven equivalent.")
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


def render_coverage_json(
    opengl_log: Path,
    vulkan_log: Path,
    parse_limits: ParseLimits | None = None,
    pose_filter: str | None = None,
    stable_pose_only: bool = False,
    stable_window_frames: int = 1,
    settled_ready_frames: int = 0,
) -> dict[str, object]:
    parse_limits = parse_limits or ParseLimits()
    opengl_events = filter_capture_events_by_pose(
        parse_capture_log(opengl_log, parse_limits),
        pose_filter,
        stable_pose_only,
        stable_window_frames,
    )
    vulkan_events = filter_capture_events_by_pose(
        parse_capture_log(vulkan_log, parse_limits),
        pose_filter,
        stable_pose_only,
        stable_window_frames,
    )
    differences = compare_capture_events(opengl_events, vulkan_events)
    return build_coverage_report(opengl_events, vulkan_events, differences, pose_filter, stable_pose_only, stable_window_frames, settled_ready_frames)


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
    assert second and second.name == "Sampler0" and "usage=5" in second.sampler_signature

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
    diff_parser.add_argument("--pose-filter", choices=["initial", "right", "left", "return", "none"], help="only compare records from one deterministic pose")
    diff_parser.add_argument("--stable-pose-only", action="store_true", help="with --pose-filter, compare only deterministic records emitted once the pose is screenshot-ready")
    diff_parser.add_argument("--stable-window-frames", type=int, default=1, help="with --stable-pose-only, compare this many rendered frames ending at the first screenshot-ready frame")
    diff_parser.add_argument("--settled-ready-frames", type=int, default=0, help="compare Sodium/DH geometry only after this many identical submitted-work frames")
    diff_parser.add_argument("--write", action="store_true", help="write report under logs/auto-capture")

    auto_parser = subparsers.add_parser("auto-diff", help="diff newest matching OpenGL/Vulkan capture pair")
    auto_parser.add_argument("--limit", type=int, default=30)
    auto_parser.add_argument("--max-resource-events", type=int, default=0, help="0 means parse every resource event")
    auto_parser.add_argument("--max-uniform-buffer-events", type=int, default=0, help="0 means parse every UBO event")
    auto_parser.add_argument("--max-standalone-uniform-events", type=int, default=0, help="0 means parse every standalone uniform event")
    auto_parser.add_argument("--max-standalone-uniform-block-member-events", type=int, default=0, help="0 means parse every standalone UBO member event")
    auto_parser.add_argument("--max-vertex-input-events", type=int, default=0, help="0 means parse every vertex-input event")
    auto_parser.add_argument("--max-draw-events", type=int, default=0, help="0 means parse every draw event")
    auto_parser.add_argument("--pose-filter", choices=["initial", "right", "left", "return", "none"], help="only compare records from one deterministic pose")
    auto_parser.add_argument("--stable-pose-only", action="store_true", help="with --pose-filter, compare only deterministic records emitted once the pose is screenshot-ready")
    auto_parser.add_argument("--stable-window-frames", type=int, default=1, help="with --stable-pose-only, compare this many rendered frames ending at the first screenshot-ready frame")
    auto_parser.add_argument("--settled-ready-frames", type=int, default=0, help="compare Sodium/DH geometry only after this many identical submitted-work frames")
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
        text = render_diff_report(
            gl_meta.latest_log,
            vk_meta.latest_log,
            args.limit,
            parse_limits,
            args.pose_filter,
            args.stable_pose_only,
            args.stable_window_frames,
            args.settled_ready_frames,
        )
        if args.write:
            pose_suffix = f"_{args.pose_filter}" if args.pose_filter else ""
            if args.stable_pose_only:
                pose_suffix += "_stable"
                if args.stable_window_frames > 1:
                    pose_suffix += f"_window{args.stable_window_frames}"
            if args.settled_ready_frames > 0:
                pose_suffix += f"_settled{args.settled_ready_frames}"
            path = write_report(text, f"vulkan_shader_input_parity{pose_suffix}_{gl_meta.run_id}_vs_{vk_meta.run_id}")
            print(f"wrote {path}")
            json_path = write_json_report(
                render_coverage_json(
                    gl_meta.latest_log,
                    vk_meta.latest_log,
                    parse_limits,
                    args.pose_filter,
                    args.stable_pose_only,
                    args.stable_window_frames,
                    args.settled_ready_frames,
                ),
                path,
            )
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
        text = render_diff_report(
            args.opengl_log,
            args.vulkan_log,
            args.limit,
            parse_limits,
            args.pose_filter,
            args.stable_pose_only,
            args.stable_window_frames,
            args.settled_ready_frames,
        )
        if args.write:
            pose_suffix = f"_{args.pose_filter}" if args.pose_filter else ""
            if args.stable_pose_only:
                pose_suffix += "_stable"
                if args.stable_window_frames > 1:
                    pose_suffix += f"_window{args.stable_window_frames}"
            if args.settled_ready_frames > 0:
                pose_suffix += f"_settled{args.settled_ready_frames}"
            path = write_report(text, f"vulkan_shader_input_parity{pose_suffix}_{args.opengl_log.stem}_vs_{args.vulkan_log.stem}")
            print(f"wrote {path}")
            json_path = write_json_report(
                render_coverage_json(
                    args.opengl_log,
                    args.vulkan_log,
                    parse_limits,
                    args.pose_filter,
                    args.stable_pose_only,
                    args.stable_window_frames,
                    args.settled_ready_frames,
                ),
                path,
            )
            print(f"wrote {json_path}")
        print(text, end="")
        return 0

    return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
