#!/usr/bin/env python3
"""
Generate spawn egg textures for AlexsMobs entities.

This script contains the actual color codes used in AlexsMobs for their spawn eggs.
Run this to generate all the AlexsMobs spawn egg textures at once.
"""

from generate_egg_texture import generate_spawn_egg
import os

# AlexsMobs spawn egg colors from AMItemRegistry.java
alexsmobs_eggs = [
    ("grizzly_bear_spawn_egg.png", "693A2C", "976144", "Grizzly Bear"),
    ("roadrunner_spawn_egg.png", "3A2E26", "FBE9CE", "Roadrunner"),
    ("bone_serpent_spawn_egg.png", "E5D9C4", "FF6038", "Bone Serpent"),
    ("gazelle_spawn_egg.png", "DDA675", "2C2925", "Gazelle"),
    ("crocodile_spawn_egg.png", "738940", "A6A15E", "Crocodile"),
    ("fly_spawn_egg.png", "464241", "892E2E", "Fly"),
    ("hummingbird_spawn_egg.png", "325E7F", "44A75F", "Hummingbird"),
    ("orca_spawn_egg.png", "2C2C2C", "D6D8E4", "Orca"),
    ("sunbird_spawn_egg.png", "F6694F", "FFDDA0", "Sunbird"),
    ("gorilla_spawn_egg.png", "595B5D", "1C1C21", "Gorilla"),
    ("crimson_mosquito_spawn_egg.png", "53403F", "C11A1A", "Crimson Mosquito"),
    ("rattlesnake_spawn_egg.png", "CEB994", "937A5B", "Rattlesnake"),
    ("endergrade_spawn_egg.png", "7862B3", "81BDEB", "Endergrade"),
    ("hammerhead_shark_spawn_egg.png", "8A92B5", "B9BED8", "Hammerhead Shark"),
    ("lobster_spawn_egg.png", "C43123", "DD5F38", "Lobster"),
    ("komodo_dragon_spawn_egg.png", "746C4F", "564231", "Komodo Dragon"),
    ("capuchin_monkey_spawn_egg.png", "25211F", "F1DAB3", "Capuchin Monkey"),
    ("centipede_spawn_egg.png", "342B2E", "733449", "Centipede"),
    ("warped_toad_spawn_egg.png", "1F968E", "FEAC6D", "Warped Toad"),
    ("moose_spawn_egg.png", "36302A", "D4B183", "Moose"),
    ("mimicube_spawn_egg.png", "8A80C1", "5E4F6F", "Mimicube"),
    ("raccoon_spawn_egg.png", "85827E", "2A2726", "Raccoon"),
    ("blobfish_spawn_egg.png", "DBC6BD", "9E7A7F", "Blobfish"),
    ("seal_spawn_egg.png", "483C32", "66594C", "Seal"),
    ("cockroach_spawn_egg.png", "0D0909", "42241E", "Cockroach"),
    ("shoebill_spawn_egg.png", "828282", "D5B48A", "Shoebill"),
    ("elephant_spawn_egg.png", "8D8987", "EDE5D1", "Elephant"),
    ("soul_vulture_spawn_egg.png", "23262D", "57F4FF", "Soul Vulture"),
    ("snow_leopard_spawn_egg.png", "ACA293", "26201D", "Snow Leopard"),
    ("spectre_spawn_egg.png", "C8D0EF", "8791EF", "Spectre"),
]

def main():
    print("=" * 70)
    print("  Generating AlexsMobs Spawn Egg Textures")
    print("=" * 70)
    print()
    print(f"Total eggs to generate: {len(alexsmobs_eggs)}")
    print()
    
    # Create output directory
    output_dir = "alexsmobs_eggs"
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)
        print(f"Created output directory: {output_dir}/")
        print()
    
    success_count = 0
    
    for filename, base_color, overlay_color, name in alexsmobs_eggs:
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
    print(f"Successfully generated {success_count} out of {len(alexsmobs_eggs)} spawn egg textures!")
    print()
    print(f"All textures saved to: {output_dir}/")
    print()
    print("You can now copy these textures to your mod's resource pack.")
    print("=" * 70)


if __name__ == '__main__':
    main()
