"""Shared resource-only shield animation witnesses, not capability admission."""

SHIELD_ANIMATION_SPRITES = {
    "plain": "minecraft:entity/shield_base_nopattern",
    "cross": "minecraft:entity/shield/cross",
    "body": "minecraft:entity/shield_base",
    "dye": "minecraft:entity/shield/base",
    "border": "minecraft:entity/shield/border",
}

SHIELD_ANIMATION_SCENARIOS = {
    prefix + suffix: material
    for prefix, material in (
        ("shield-animation", "plain"),
        ("shield-pattern-animation", "cross"),
        ("shield-body-animation", "body"),
        ("shield-dye-animation", "dye"),
        ("shield-border-animation", "border"),
        ("shield-combined-animation", "combined"),
        ("shield-mixed-animation", "mixed"),
        ("shield-periods-animation", "periods"),
    )
    for suffix in ("", "-interpolated")
}


def shield_animation_materials(material):
    return ("dye", "border", "cross", "body") if material in ("combined", "mixed", "periods") else (material,)


# Shared authored playback order, including repeated sheet 2 and unequal durations.
SHIELD_ANIMATION_FRAMES = ((2, 3), (0, 5), (2, 2), (1, 7))


# Distinct per-material ordering and frame durations; equal total period keeps
# the capture witness bounded without modifying either renderer's clocks.
SHIELD_MIXED_ANIMATION_FRAMES = {
    "dye": SHIELD_ANIMATION_FRAMES,
    "border": ((1, 5), (2, 2), (0, 7), (1, 3)),
    "cross": ((0, 2), (1, 7), (0, 3), (2, 5)),
    "body": ((1, 6), (0, 4), (2, 7)),
}


SHIELD_PERIOD_ANIMATION_FRAMES = {
    "dye": SHIELD_ANIMATION_FRAMES,
    "border": ((1, 10), (2, 4), (0, 14), (1, 6)),
    "cross": ((0, 6), (1, 21), (0, 9), (2, 15)),
    "body": SHIELD_MIXED_ANIMATION_FRAMES["body"],
}


def shield_animation_frames(material, role):
    if role not in shield_animation_materials(material) or role not in SHIELD_ANIMATION_SPRITES:
        raise ValueError("animation schedule requires a selected material")
    if material == "periods": return SHIELD_PERIOD_ANIMATION_FRAMES[role]
    return SHIELD_MIXED_ANIMATION_FRAMES[role] if material == "mixed" else SHIELD_ANIMATION_FRAMES


def shield_animation_phase(frame, subframe, frames=SHIELD_ANIMATION_FRAMES):
    if type(frame) is not int or type(subframe) is not int:
        raise ValueError("Frozen animation phase requires integer observations")
    if not 0 <= frame < len(frames):
        raise ValueError("Frozen animation frame is outside the authored schedule")
    if not 0 <= subframe < frames[frame][1]:
        raise ValueError("Frozen animation subframe is outside its authored duration")
    return sum(duration for _, duration in frames[:frame]) + subframe
