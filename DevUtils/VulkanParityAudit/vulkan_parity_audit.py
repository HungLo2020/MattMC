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
    projection_label: str = ""
    projection_context: str = ""

    @property
    def semantic_key(self) -> tuple[str, str, str, str]:
        name = self.name
        if self.name == "Projection" and self.projection_label:
            name = f"{self.name}@{self.projection_label}"
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

    @property
    def key(self) -> str:
        return self.name

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

    @property
    def key(self) -> str:
        return self.name

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
class CaptureEvents:
    path: Path
    backend: str = "unknown"
    resources: dict[tuple[str, str, str, str], list[ResourceRecord]] = field(default_factory=lambda: defaultdict(list))
    uniform_buffers: dict[str, list[UniformBufferEvent]] = field(default_factory=lambda: defaultdict(list))
    standalone_uniforms: dict[str, list[StandaloneUniformEvent]] = field(default_factory=lambda: defaultdict(list))
    standalone_uniform_block_members: dict[str, list[StandaloneUniformBlockMemberEvent]] = field(default_factory=lambda: defaultdict(list))
    vertex_inputs: dict[str, list[VertexInputEvent]] = field(default_factory=lambda: defaultdict(list))
    counters: Counter = field(default_factory=Counter)
    skipped: Counter = field(default_factory=Counter)


@dataclass(frozen=True)
class ParseLimits:
    max_resource_events: int = 0
    max_uniform_buffer_events: int = 0
    max_standalone_uniform_events: int = 0
    max_standalone_uniform_block_member_events: int = 0
    max_vertex_input_events: int = 0

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
    return label


def stable_digest(values: Iterable[str]) -> str:
    digest = hashlib.sha256()
    for value in sorted(set(values)):
        digest.update(value.encode("utf-8", "replace"))
        digest.update(b"\0")
    return digest.hexdigest()[:16]


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def parse_top_fields(text: str) -> dict[str, str]:
    return {match.group(1): match.group(2).strip('"') for match in TOP_FIELD_RE.finditer(text)}


def parse_struct_fields(text: str) -> dict[str, str]:
    return {match.group(1): match.group(2).strip('"') for match in STRUCT_FIELD_RE.finditer(text)}


def extract_hashes(text: str) -> dict[str, str]:
    return {match.group(1): match.group(2) for match in HASH_RE.finditer(text)}


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

    elif payload.startswith("Sampler "):
        events.counters["Sampler"] += 1
    elif payload.startswith("Uniform "):
        events.counters["Uniform"] += 1


def values_for_resources(records: list[ResourceRecord]) -> list[str]:
    return sorted({record.short_signature for record in records})


def values_for_bindings(records: list[ResourceRecord]) -> list[str]:
    return sorted({record.binding for record in records if record.binding})


def values_for_lengths(records: list[ResourceRecord]) -> list[str]:
    return sorted({record.length for record in records if record.length})


def values_for_range_hashes(records: list[ResourceRecord]) -> list[str]:
    return sorted({record.range_hash for record in records if record.range_hash})


def values_for_payload_hashes(records: list[ResourceRecord]) -> list[str]:
    return sorted({record.payload_hash for record in records if record.payload_hash})


def values_for_pipeline_keys(records: list[ResourceRecord]) -> list[str]:
    return sorted({record.pipeline_key for record in records if record.pipeline_key})


def format_key(key: tuple[str, str, str, str]) -> str:
    pipeline_identity, stable_key, name, resource_type = key
    return f"pipeline={pipeline_identity} / stableKey={stable_key} / name={name} / type={resource_type}"


def compare_capture_events(opengl: CaptureEvents, vulkan: CaptureEvents) -> list[Difference]:
    differences: list[Difference] = []

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
            differences.append(Difference(
                severity=25 if is_labeled_projection else 60,
                category="backend-only-projection-context" if is_labeled_projection else "backend-only-resource",
                key=key_text,
                reason=(
                    f"labeled projection context only observed on {only}; separate captures can legitimately visit different GUI/minimap contexts"
                    if is_labeled_projection
                    else f"resource semantic key only observed on {only}"
                ),
                opengl_count=len(gl_records),
                vulkan_count=len(vk_records),
                opengl_values=values_for_resources(gl_records)[:4],
                vulkan_values=values_for_resources(vk_records)[:4],
            ))
            continue

        resource_type = key[3]
        gl_values = values_for_resources(gl_records)
        vk_values = values_for_resources(vk_records)
        if resource_type == "UNIFORM_BUFFER":
            gl_payloads = values_for_payload_hashes(gl_records)
            vk_payloads = values_for_payload_hashes(vk_records)
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
                reason = "same semantic sampler has different texture/view metadata"
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
                    key=f"name={name}",
                    reason="same standalone uniform has overlapping payload hashes plus backend-specific observations in separate captures",
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
                key=f"name={name}",
                reason="same standalone uniform name has disjoint payload hash sets",
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
    lines.append("- Standalone uniforms compare by semantic uniform name and normalized setter payload hash.")
    lines.append("- Materialized Vulkan standalone UBO members compare by semantic uniform name against OpenGL standalone uniforms when member logs are present.")
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
    members = events.standalone_uniform_block_members["gbufferProjection"]
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
    diff_parser.add_argument("--write", action="store_true", help="write report under logs/auto-capture")

    auto_parser = subparsers.add_parser("auto-diff", help="diff newest matching OpenGL/Vulkan capture pair")
    auto_parser.add_argument("--limit", type=int, default=30)
    auto_parser.add_argument("--max-resource-events", type=int, default=0, help="0 means parse every resource event")
    auto_parser.add_argument("--max-uniform-buffer-events", type=int, default=0, help="0 means parse every UBO event")
    auto_parser.add_argument("--max-standalone-uniform-events", type=int, default=0, help="0 means parse every standalone uniform event")
    auto_parser.add_argument("--max-standalone-uniform-block-member-events", type=int, default=0, help="0 means parse every standalone UBO member event")
    auto_parser.add_argument("--max-vertex-input-events", type=int, default=0, help="0 means parse every vertex-input event")
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
            args.max_vertex_input_events
        )
        text = render_diff_report(gl_meta.latest_log, vk_meta.latest_log, args.limit, parse_limits)
        if args.write:
            path = write_report(text, f"vulkan_shader_input_parity_{gl_meta.run_id}_vs_{vk_meta.run_id}")
            print(f"wrote {path}")
        print(text, end="")
        return 0

    if args.command == "diff-captures":
        parse_limits = ParseLimits(
            args.max_resource_events,
            args.max_uniform_buffer_events,
            args.max_standalone_uniform_events,
            args.max_standalone_uniform_block_member_events,
            args.max_vertex_input_events
        )
        text = render_diff_report(args.opengl_log, args.vulkan_log, args.limit, parse_limits)
        if args.write:
            path = write_report(text, f"vulkan_shader_input_parity_{args.opengl_log.stem}_vs_{args.vulkan_log.stem}")
            print(f"wrote {path}")
        print(text, end="")
        return 0

    return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
