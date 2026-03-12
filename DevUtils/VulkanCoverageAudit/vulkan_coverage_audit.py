#!/usr/bin/env python3
"""
VulkanCoverageAudit - static analysis of Vulkan backend completeness.

Answers the question:
  "What % of the real VulkanicAPI/GraphicsBackend contract is actually
   implemented in VulkanBackend vs just crashing the fail-hard proxy?"

Usage:
  python3 DevUtils/VulkanCoverageAudit/vulkan_coverage_audit.py [--brief] [--markdown]

Output modes:
  (default)    Verbose report with per-method status
  --brief      Summary numbers only
  --markdown   Emit GitHub-flavoured Markdown (suitable for docs/)
"""

import re
import sys
import os
from pathlib import Path
from dataclasses import dataclass, field
from typing import Optional
from collections import defaultdict

# ── paths ──────────────────────────────────────────────────────────────────
_HERE = Path(__file__).parent
_ROOT = _HERE.parent.parent
_SRC_MAIN  = _ROOT / "src" / "main" / "java"
_SRC_TEST  = _ROOT / "src" / "test" / "java"

GRAPHICS_BACKEND  = _SRC_MAIN / "net/vulkanic/GraphicsBackend.java"
VULKAN_BACKEND    = _SRC_MAIN / "net/vulkanic/backends/vulkan/VulkanBackend.java"
OPENGL_BACKEND    = _SRC_MAIN / "net/vulkanic/backends/opengl/OpenGLBackend.java"
VULKANIC_API      = _SRC_MAIN / "net/vulkanic/VulkanicAPI.java"
PRODUCTION_ROOT   = _SRC_MAIN / "net/minecraft"

# ── status enum values ─────────────────────────────────────────────────────
IMPL       = "IMPLEMENTED"   # VulkanBackend has it and it does not throw
STUB       = "STUB"          # VulkanBackend has it but body only throws UnsupportedOperationException
DEFAULT    = "DEFAULT_ONLY"  # Only the default interface impl exists; VulkanBackend has no override
MISSING    = "MISSING"       # Abstract in interface; VulkanBackend has no method of that name at all

STATUS_ORDER = [IMPL, STUB, DEFAULT, MISSING]

# ── regex helpers ─────────────────────────────────────────────────────────
# Match a method declaration line (very rough but sufficient for single-file parsing)
_METHOD_RE = re.compile(
    r'^\s{0,4}(?:(?:public|protected|private|static|default|abstract|final|synchronized|native)\s+)*'
    r'(?:<[\w\s,?<>\[\]]+>\s+)?'           # optional generic return
    r'(?:[\w.<>\[\]]+\s+)'                  # return type
    r'(\w+)\s*\('                           # ← method name capture group
    r'([^)]*)\)',                            # ← params capture group (rough)
    re.MULTILINE
)

_UNSUPPORTED_RE = re.compile(r'throw\s+new\s+UnsupportedOperationException')

# ── data model ─────────────────────────────────────────────────────────────
@dataclass
class MethodInfo:
    name: str
    has_default: bool         # GraphicsBackend has a `default` body
    is_abstract: bool         # GraphicsBackend declares without body
    vulkan_has_override: bool = False
    vulkan_only_throws: bool  = False   # override exists but only throws UnsupportedOperationException
    opengl_has_override: bool = False
    opengl_only_throws: bool  = False

    @property
    def vulkan_status(self) -> str:
        if self.vulkan_has_override and not self.vulkan_only_throws:
            return IMPL
        if self.vulkan_has_override and self.vulkan_only_throws:
            return STUB
        if self.has_default:
            return DEFAULT
        return MISSING

    @property
    def opengl_status(self) -> str:
        if self.opengl_has_override and not self.opengl_only_throws:
            return IMPL
        if self.opengl_has_override and self.opengl_only_throws:
            return STUB
        # OpenGLBackend implements GraphicsBackend — the compiler requires every
        # abstract method to be satisfied.  A `default` interface method needs no
        # override; the interface behaviour IS correct for the OpenGL path.
        # Count defaults as IMPL for OpenGL — they are not gaps.
        if self.has_default:
            return IMPL
        return MISSING


# ── parsing ────────────────────────────────────────────────────────────────
def _parse_interface_methods(src: str) -> list[MethodInfo]:
    """Extract method declarations from GraphicsBackend.java.

    Strips all comments first to avoid false positives from Javadoc text
    and parameter names being mistaken for method declarations.
    """
    methods: list[MethodInfo] = []
    seen: set[str] = set()

    # Strip block comments (Javadoc and /* ... */) before parsing
    clean = re.sub(r'/\*.*?\*/', '', src, flags=re.DOTALL)
    # Strip line comments
    clean = re.sub(r'//[^\n]*', '', clean)

    for m in _METHOD_RE.finditer(clean):
        name = m.group(1)

        # Skip keywords and uppercase tokens (types, constructors)
        if name in ('if', 'while', 'for', 'switch', 'catch', 'interface',
                    'class', 'enum', 'new', 'return', 'super', 'this',
                    'assert', 'throw', 'throws', 'extends', 'implements',
                    'instanceof', 'null', 'true', 'false'):
            continue
        if name[0].isupper():
            continue
        if name in seen:
            continue

        # Look back to the previous `}` or `{` — `default` keyword must appear
        # in the same method signature block, not a previous one.
        context_before = clean[max(0, m.start()-300) : m.start()]
        last_brace = max(context_before.rfind('}'), context_before.rfind('{'))
        relevant = context_before[last_brace+1:] if last_brace >= 0 else context_before
        has_default = bool(re.search(r'\bdefault\b', relevant))

        # Determine abstract vs concrete: first non-whitespace after `)` is `;` or `{`
        tail = clean[m.end():]
        stripped = tail.lstrip()
        is_abstract = not (stripped.startswith('{') or has_default)

        seen.add(name)
        methods.append(MethodInfo(name=name, has_default=has_default, is_abstract=is_abstract))

    return methods


def _extract_method_body(src: str, method_name: str) -> Optional[str]:
    """Return the body of the named method (first match) or None."""
    pattern = re.compile(
        r'\b' + re.escape(method_name) + r'\s*\([^)]*\)\s*(?:throws\s+[\w\s,]+)?\s*\{',
        re.MULTILINE
    )
    m = pattern.search(src)
    if not m:
        return None
    start = src.find('{', m.start())
    depth = 0
    for i, ch in enumerate(src[start:]):
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                return src[start : start + i + 1]
    return None


def _method_only_throws(body: Optional[str]) -> bool:
    """True if the method body contains only a throw new UnsupportedOperationException."""
    if not body:
        return False
    # Strip the outer braces and whitespace
    inner = body[1:-1].strip()
    # Remove single-line comments
    inner = re.sub(r'//[^\n]*', '', inner).strip()
    return bool(_UNSUPPORTED_RE.match(inner))


def _has_method(src: str, method_name: str) -> bool:
    pattern = re.compile(r'\b' + re.escape(method_name) + r'\s*\(')
    return bool(pattern.search(src))


def _parse_backend_overrides(src: str, methods: list[MethodInfo], is_opengl: bool):
    """Fill in override info on each MethodInfo from a backend source file."""
    for mi in methods:
        if _has_method(src, mi.name):
            body = _extract_method_body(src, mi.name)
            only_throws = _method_only_throws(body)
            if is_opengl:
                mi.opengl_has_override = True
                mi.opengl_only_throws = only_throws
            else:
                mi.vulkan_has_override = True
                mi.vulkan_only_throws = only_throws


# ── production callsite scanning ──────────────────────────────────────────
@dataclass
class Callsite:
    method: str
    file: str
    line: int


def _scan_production_callsites(methods: list[MethodInfo]) -> dict[str, list[Callsite]]:
    """Find every VulkanicAPI.<method>() call in src/main/java/net/minecraft."""
    callsites: dict[str, list[Callsite]] = defaultdict(list)
    method_names = {mi.name for mi in methods}

    if not PRODUCTION_ROOT.exists():
        return callsites

    for java_file in PRODUCTION_ROOT.rglob("*.java"):
        rel = str(java_file.relative_to(_SRC_MAIN))
        try:
            src = java_file.read_text(encoding="utf-8")
        except Exception:
            continue
        for i, line in enumerate(src.splitlines(), 1):
            # Look for any VulkanicAPI.<name>( usage
            for m in re.finditer(r'VulkanicAPI\.(\w+)\s*\(', line):
                nm = m.group(1)
                if nm in method_names:
                    callsites[nm].append(Callsite(nm, rel, i))

    return callsites


# ── reporting ─────────────────────────────────────────────────────────────
_STATUS_SYMBOLS = {
    IMPL:    "✅",
    STUB:    "🔶",
    DEFAULT: "🔵",
    MISSING: "❌",
}

_STATUS_COLORS_ANSI = {
    IMPL:    "\033[32m",
    STUB:    "\033[33m",
    DEFAULT: "\033[34m",
    MISSING: "\033[31m",
}
_RESET = "\033[0m"


def _pct(n, total) -> str:
    return f"{n/total*100:.1f}%" if total else "n/a"


def _bar(pct_float: float, width: int = 30) -> str:
    filled = int(pct_float / 100 * width)
    return "█" * filled + "░" * (width - filled)


def report_verbose(methods: list[MethodInfo], callsites: dict[str, list[Callsite]]):
    total = len(methods)
    by_status: dict[str, list[MethodInfo]] = defaultdict(list)
    for mi in methods:
        by_status[mi.vulkan_status].append(mi)

    print("=" * 70)
    print("  VULKANIC BACKEND COVERAGE AUDIT")
    print("=" * 70)
    print(f"  GraphicsBackend methods scanned: {total}")
    print()

    # Summary bar
    n_impl    = len(by_status[IMPL])
    n_stub    = len(by_status[STUB])
    n_default = len(by_status[DEFAULT])
    n_missing = len(by_status[MISSING])
    pct_impl  = n_impl / total * 100

    print(f"  Vulkan coverage:  {_bar(pct_impl)}  {_pct(n_impl, total)}")
    print()
    print(f"  {_STATUS_SYMBOLS[IMPL]}  IMPLEMENTED  (VulkanBackend has real impl)  : {n_impl:4d}  {_pct(n_impl, total)}")
    print(f"  {_STATUS_SYMBOLS[STUB]}  STUB         (overrides but only throws)    : {n_stub:4d}  {_pct(n_stub, total)}")
    print(f"  {_STATUS_SYMBOLS[DEFAULT]}  DEFAULT ONLY (interface default, no override): {n_default:4d}  {_pct(n_default, total)}")
    print(f"  {_STATUS_SYMBOLS[MISSING]}  MISSING      (abstract, no Vulkan override) : {n_missing:4d}  {_pct(n_missing, total)}")
    print()

    # OpenGL for comparison
    # OpenGL is a concrete class implementing GraphicsBackend.
    # Any abstract method without an override would be a compile error.
    # Interface defaults work correctly for OpenGL without an explicit override.
    # Therefore: OpenGL is always 100% -- compiler-enforced.
    n_gl_explicit = sum(1 for mi in methods if mi.opengl_has_override and not mi.opengl_only_throws)
    print(f"  OpenGL coverage: {_bar(100)}  100.0%  (compiler-verified; {n_gl_explicit}/{total} explicit overrides, rest via interface defaults)")
    print()

    # Per-status breakdowns
    for status in STATUS_ORDER:
        bucket = by_status[status]
        if not bucket:
            continue
        sym   = _STATUS_SYMBOLS[status]
        color = _STATUS_COLORS_ANSI[status]
        print(f"  {color}── {status} ({len(bucket)}) ──{_RESET}")
        for mi in sorted(bucket, key=lambda x: x.name):
            cs = callsites.get(mi.name, [])
            cs_note = f"  [{len(cs)} production callsite{'s' if len(cs)!=1 else ''}]" if cs else ""
            print(f"     {sym} {mi.name}{cs_note}")
        print()

    # Callsites with no GraphicsBackend mapping
    all_method_names = {mi.name for mi in methods}
    unmapped = set()
    for java_file in PRODUCTION_ROOT.rglob("*.java"):
        try:
            src = java_file.read_text(encoding="utf-8")
        except Exception:
            continue
        for m in re.finditer(r'VulkanicAPI\.(\w+)\s*\(', src):
            nm = m.group(1)
            if nm not in all_method_names:
                unmapped.add(nm)

    if unmapped:
        print(f"  ── VulkanicAPI callsites NOT in GraphicsBackend interface ({len(unmapped)}) ──")
        for nm in sorted(unmapped):
            print(f"     ⚠️  {nm}")
        print()

    print("=" * 70)


def report_brief(methods: list[MethodInfo]):
    total = len(methods)
    n_impl     = sum(1 for mi in methods if mi.vulkan_status == IMPL)
    n_stub     = sum(1 for mi in methods if mi.vulkan_status == STUB)
    n_default  = sum(1 for mi in methods if mi.vulkan_status == DEFAULT)
    n_missing  = sum(1 for mi in methods if mi.vulkan_status == MISSING)

    # OpenGL is a concrete class implementing GraphicsBackend.
    # If it compiles, every abstract interface method is satisfied.
    # Methods that rely on interface `default` implementations are also correct for OpenGL.
    # Therefore OpenGL is always 100% -- the compiler enforces this.
    n_gl_explicit = sum(1 for mi in methods if mi.opengl_has_override and not mi.opengl_only_throws)

    print(f"GraphicsBackend methods: {total}")
    print(f"Vulkan  IMPLEMENTED:  {n_impl:4d}  ({_pct(n_impl, total)})  explicit VulkanBackend overrides")
    print(f"Vulkan  STUB:         {n_stub:4d}  ({_pct(n_stub, total)})")
    print(f"Vulkan  DEFAULT_ONLY: {n_default:4d}  ({_pct(n_default, total)})")
    print(f"Vulkan  MISSING:      {n_missing:4d}  ({_pct(n_missing, total)})  fail-hard proxy throws on these")
    print(f"OpenGL  DECLARED:     {n_gl_explicit:4d}  ({_pct(n_gl_explicit, total)})  explicit overrides in OpenGLBackend.java")
    print(f"OpenGL  TOTAL:        {total:4d}  (100.0%)  compiler-verified (OpenGLBackend implements GraphicsBackend)")


def report_markdown(methods: list[MethodInfo], callsites: dict[str, list[Callsite]]):
    total = len(methods)
    by_status: dict[str, list[MethodInfo]] = defaultdict(list)
    for mi in methods:
        by_status[mi.vulkan_status].append(mi)

    n_impl    = len(by_status[IMPL])
    n_stub    = len(by_status[STUB])
    n_default = len(by_status[DEFAULT])
    n_missing = len(by_status[MISSING])
    n_gl_impl = sum(1 for mi in methods if mi.opengl_status == IMPL)

    pct_v = n_impl / total * 100
    pct_gl = n_gl_impl / total * 100

    print("# Vulkanic Backend Coverage Audit\n")
    print(f"| Backend | Implemented | Stub | Default Only | Missing | **Coverage** |")
    print(f"|---------|-------------|------|--------------|---------|--------------|")
    print(f"| Vulkan  | {n_impl} | {n_stub} | {n_default} | {n_missing} | **{pct_v:.1f}%** |")
    n_gl_explicit_md = sum(1 for mi in methods if mi.opengl_has_override and not mi.opengl_only_throws)
    print(f"| OpenGL  | {n_gl_explicit_md} declared + interface defaults | — | — | 0 | **100%** (compiler-verified) |")
    print()

    for status in STATUS_ORDER:
        bucket = by_status[status]
        if not bucket:
            continue
        sym = _STATUS_SYMBOLS[status]
        print(f"## {sym} {status} ({len(bucket)})\n")
        print("| Method | Production Callsites |")
        print("|--------|----------------------|")
        for mi in sorted(bucket, key=lambda x: x.name):
            cs = len(callsites.get(mi.name, []))
            cs_str = str(cs) if cs > 0 else "—"
            print(f"| `{mi.name}` | {cs_str} |")
        print()


# ── main ──────────────────────────────────────────────────────────────────
def main():
    brief    = "--brief"    in sys.argv
    markdown = "--markdown" in sys.argv

    # Load sources
    try:
        gb_src  = GRAPHICS_BACKEND.read_text(encoding="utf-8")
        vk_src  = VULKAN_BACKEND.read_text(encoding="utf-8")
        gl_src  = OPENGL_BACKEND.read_text(encoding="utf-8")
    except FileNotFoundError as e:
        print(f"ERROR: Could not read source file: {e}", file=sys.stderr)
        sys.exit(1)

    # Parse interface methods
    methods = _parse_interface_methods(gb_src)

    # Fill in backend coverage
    _parse_backend_overrides(vk_src, methods, is_opengl=False)
    _parse_backend_overrides(gl_src, methods, is_opengl=True)

    # Scan production callsites (only for interface methods — gives "called AND missing" set)
    callsites = _scan_production_callsites(methods)

    # Report
    if brief:
        report_brief(methods)
    elif markdown:
        report_markdown(methods, callsites)
    else:
        report_verbose(methods, callsites)


if __name__ == "__main__":
    main()
