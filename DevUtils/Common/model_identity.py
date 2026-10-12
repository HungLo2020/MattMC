"""Match a semantic model family to its exact canonical per-part assets."""
import re
import math

_PART = re.compile(r"(?:root|(?:[0-9a-f]{2})+)\Z")


def matches_model_identity(actual: object, expected: str) -> bool:
    if not isinstance(actual, str):
        return False
    if actual == expected:
        return True
    prefix = expected + "/part/"
    if not actual.startswith(prefix):
        return False
    part = actual[len(prefix):]
    if not _PART.fullmatch(part):
        return False
    if part == "root":
        return True
    try:
        bytes.fromhex(part).decode("utf-8")
    except (ValueError, UnicodeDecodeError):
        return False
    return True


def combine_model_parts(models: list[dict], expected: str, inventory: set[str]) -> dict | None:
    """One frame's complete, unique part set forms one projected model crop."""
    if len(models) == 1 and models[0].get("semanticModelIdentity") == expected:
        return models[0]
    identities = [m.get("semanticModelIdentity") for m in models]
    if (not models or len(set(identities)) != len(models) or set(identities) != inventory
            or any(identity == expected or not matches_model_identity(identity, expected)
                   for identity in identities)):
        return None
    first = models[0]
    common = ("frameIndex", "route", "entityId", "textureId", "viewport", "vertexLayoutVersion", "indexType", "sectionCullPolicy")
    if any(any(m.get(key) != first.get(key) for key in common) for m in models):
        return None
    bounds = []
    for model in models:
        box = model.get("screenBounds")
        if not model.get("projected") or not isinstance(box, dict):
            return None
        values = [box.get(key) for key in ("left", "top", "right", "bottom")]
        if any(not isinstance(v, (int, float)) or not math.isfinite(v) for v in values):
            return None
        if values[0] > values[2] or values[1] > values[3]:
            return None
        bounds.append(values)
    return {**first, "semanticModelIdentity": expected,
            "screenBounds": dict(zip(("left", "top", "right", "bottom"),
                                     (min(b[0] for b in bounds), min(b[1] for b in bounds),
                                      max(b[2] for b in bounds), max(b[3] for b in bounds)))),
            "vertexCount": sum(m.get("vertexCount", 0) for m in models),
            "sectionCount": sum(m.get("sectionCount", 0) for m in models),
            "parts": [{key: m.get(key) for key in ("meshKey", "meshGeneration", "semanticModelIdentity")}
                      for m in models]}
