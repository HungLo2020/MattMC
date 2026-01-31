# Spawn Egg Textures

This directory contains all 132 spawn egg textures for mobs from Alex's Mobs and Alex's Caves.

## Summary

- **Alex's Mobs**: 90 spawn egg textures
- **Alex's Caves**: 42 spawn egg textures (note: one mob listed in MOB_EGGS.md didn't generate, likely Devil's Hole Pupfish with apostrophe in filename)
- **Total**: 132 PNG files

## Generation

These textures were generated using the `DevUtils/EggBuilder/generate_egg.py` script, which creates authentic vanilla Minecraft-style spawn egg textures from two hex color values (base color and overlay color).

The colors were extracted from the original source code of Alex's Mobs and Alex's Caves, as documented in `MOB_EGGS.md`.

## Naming Convention

All files follow the pattern: `{mob_name}_spawn_egg.png`

Where `{mob_name}` is:
- Lowercase
- Spaces replaced with underscores
- Special characters removed
- Example: "Grizzly Bear" → `grizzly_bear_spawn_egg.png`

## File Specifications

- **Format**: PNG with transparency
- **Dimensions**: 16x16 pixels
- **Color Mode**: RGBA
- **Pattern**: Vanilla Minecraft spawn egg shape (pill/capsule with two-color gradient system)

## Usage

These textures can be directly used as item textures for spawn eggs in Minecraft 1.21.x resource packs or mods.
