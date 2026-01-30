#!/usr/bin/env python3
"""
Generate spawn egg textures for AlexsCaves entities.

This script contains the actual color codes used in AlexsCaves for their spawn eggs.
Run this to generate all the AlexsCaves spawn egg textures at once.
"""

from generate_egg_texture import generate_spawn_egg
import os

# AlexsCaves spawn egg colors from ACItemRegistry.java
alexscaves_eggs = [
    ("teletor_spawn_egg.png", "433B4A", "0060EF", "Teletor"),
    ("magnetron_spawn_egg.png", "FF002A", "203070", "Magnetron"),
    ("boundroid_spawn_egg.png", "BB1919", "FFFFFF", "Boundroid"),
    ("ferrouslime_spawn_egg.png", "26272D", "53556C", "Ferrouslime"),
    ("notor_spawn_egg.png", "5F5369", "C6C6C6", "Notor"),
    ("subterranodon_spawn_egg.png", "00B1B2", "FFF11C", "Subterranodon"),
    ("vallumraptor_spawn_egg.png", "22389A", "EEE5AB", "Vallumraptor"),
    ("grottoceratops_spawn_egg.png", "AC3B03", "D39B4E", "Grottoceratops"),
    ("trilocaris_spawn_egg.png", "713E0D", "8B2010", "Trilocaris"),
    ("tremorsaurus_spawn_egg.png", "53780E", "DFA211", "Tremorsaurus"),
    ("relicheirus_spawn_egg.png", "6AE4F9", "5B2152", "Relicheirus"),
    ("luxtructosaurus_spawn_egg.png", "1F0E15", "B30C03", "Luxtructosaurus"),
    ("atlatitan_spawn_egg.png", "B67000", "BFBAA4", "Atlatitan"),
    ("nucleeper_spawn_egg.png", "95A1A5", "00FF00", "Nucleeper"),
    ("radgill_spawn_egg.png", "43302C", "E8E400", "Radgill"),
    ("brainiac_spawn_egg.png", "3E5136", "E87C9E", "Brainiac"),
    ("gammaroach_spawn_egg.png", "56682A", "2A2B19", "Gammaroach"),
    ("raycat_spawn_egg.png", "67FF00", "030A00", "Raycat"),
    ("tremorzilla_spawn_egg.png", "574D2F", "8CFF08", "Tremorzilla"),
    ("lanternfish_spawn_egg.png", "182538", "ECA500", "Lanternfish"),
    ("sea_pig_spawn_egg.png", "FFA3B9", "F88672", "Sea Pig"),
    ("hullbreaker_spawn_egg.png", "182538", "76FFFD", "Hullbreaker"),
    ("gossamer_worm_spawn_egg.png", "C8F1FF", "96DEF6", "Gossamer Worm"),
    ("tripodfish_spawn_egg.png", "34529D", "81A1CF", "Tripodfish"),
    ("deep_one_spawn_egg.png", "0D2547", "0A843B", "Deep One"),
    ("deep_one_knight_spawn_egg.png", "472C3B", "D4CCC3", "Deep One Knight"),
    ("deep_one_mage_spawn_egg.png", "96DEF6", "D1FF00", "Deep One Mage"),
    ("mine_guardian_spawn_egg.png", "404253", "E62008", "Mine Guardian"),
    ("gloomoth_spawn_egg.png", "5E463D", "EBD3BE", "Gloomoth"),
    ("underzealot_spawn_egg.png", "291C17", "F27C68", "Underzealot"),
    ("watcher_spawn_egg.png", "291C17", "EC1900", "Watcher"),
    ("corrodent_spawn_egg.png", "351A14", "593B33", "Corrodent"),
    ("vesper_spawn_egg.png", "884E2A", "A54A6B", "Vesper"),
    ("forsaken_spawn_egg.png", "000000", "110909", "Forsaken"),
    ("sweetish_fish_spawn_egg.png", "E9132C", "FF364D", "Sweetish Fish"),
    ("caniac_spawn_egg.png", "F9F0FF", "FF3F56", "Caniac"),
    ("gumbeeper_spawn_egg.png", "FF2B44", "E7BAFF", "Gumbeeper"),
    ("candicorn_spawn_egg.png", "E86B00", "FFEF57", "Candicorn"),
    ("gum_worm_spawn_egg.png", "92FFD9", "FFA1DC", "Gum Worm"),
    ("caramel_cube_spawn_egg.png", "CC8015", "B86A0D", "Caramel Cube"),
    ("gummy_bear_spawn_egg.png", "FF463F", "FDA09E", "Gummy Bear"),
    ("licowitch_spawn_egg.png", "681182", "FF6CD7", "Licowitch"),
    ("gingerbread_man_spawn_egg.png", "BB581D", "FFFFFF", "Gingerbread Man"),
]

def main():
    print("=" * 70)
    print("  Generating AlexsCaves Spawn Egg Textures")
    print("=" * 70)
    print()
    print(f"Total eggs to generate: {len(alexscaves_eggs)}")
    print()
    
    # Create output directory
    output_dir = "alexscaves_eggs"
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)
        print(f"Created output directory: {output_dir}/")
        print()
    
    success_count = 0
    
    for filename, base_color, overlay_color, name in alexscaves_eggs:
        try:
            print(f"Generating {name}...")
            output_path = os.path.join(output_dir, filename)
            generate_spawn_egg(base_color, overlay_color, output_path)
            success_count += 1
            print()
        except Exception as e:
            print(f"  ERROR: Failed to generate {name}: {e}")
            print()
    
    print("=" * 70)
    print(f"Successfully generated {success_count} out of {len(alexscaves_eggs)} spawn egg textures!")
    print()
    print(f"All textures saved to: {output_dir}/")
    print()
    print("You can now copy these textures to your mod's resource pack.")
    print("=" * 70)


if __name__ == '__main__':
    main()
