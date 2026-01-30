#!/usr/bin/env python3
"""
Example script showing how to generate multiple spawn egg textures programmatically.

This demonstrates using the generate_spawn_egg function directly without interactive prompts.
"""

from generate_egg_texture import generate_spawn_egg

# Common vanilla Minecraft spawn egg colors
examples = [
    ("blaze_spawn_egg.png", "F6B201", "FFF87E", "Blaze"),
    ("creeper_spawn_egg.png", "0DA70B", "000000", "Creeper"),
    ("spider_spawn_egg.png", "342D27", "A80E0E", "Spider"),
    ("zombie_spawn_egg.png", "00AFAF", "799C65", "Zombie"),
    ("skeleton_spawn_egg.png", "C1C1C1", "494949", "Skeleton"),
    ("enderman_spawn_egg.png", "161616", "000000", "Enderman"),
]

print("Generating example spawn egg textures...")
print("=" * 60)
print()

for filename, base_color, overlay_color, name in examples:
    print(f"Generating {name} spawn egg...")
    generate_spawn_egg(base_color, overlay_color, filename)
    print()

print("=" * 60)
print("All example textures generated!")
print()
print("Files created:")
for filename, _, _, name in examples:
    print(f"  - {filename} ({name})")
