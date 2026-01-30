#!/usr/bin/env python3
"""
Minecraft Spawn Egg Texture Generator

Generates a 16x16 spawn egg texture from two hex colors.
Based on the classic Minecraft two-color spawn egg system.

Usage:
    python generate_egg.py <base_color> <overlay_color>
    
Example:
    python generate_egg.py F6B201 FFF87E
    python generate_egg.py #0DA70B #000000
"""

from PIL import Image
import sys
import os


def hex_to_rgb(hex_color):
    """Convert hex color string to RGB tuple."""
    hex_color = hex_color.lstrip('#')
    if len(hex_color) != 6:
        raise ValueError(f"Invalid hex color: {hex_color}")
    return tuple(int(hex_color[i:i+2], 16) for i in (0, 2, 4))


def create_base_template():
    """Create the grayscale base template for spawn egg body."""
    img = Image.new('L', (16, 16), 0)
    pixels = img.load()
    
    # Egg shape pattern (grayscale intensity: 0=black, 255=white)
    pattern = [
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0],
        [0,   0,   0,   0,   0, 150, 180, 200, 200, 180, 150,   0,   0,   0,   0,   0],
        [0,   0,   0,   0, 180, 200, 220, 240, 240, 220, 200, 180,   0,   0,   0,   0],
        [0,   0,   0, 180, 200, 220, 240, 255, 255, 240, 220, 200, 180,   0,   0,   0],
        [0,   0, 150, 200, 220, 240, 255, 255, 255, 255, 240, 220, 200, 150,   0,   0],
        [0,   0, 180, 220, 240, 255, 255, 255, 255, 255, 255, 240, 220, 180,   0,   0],
        [0,   0, 200, 240, 255, 255, 255, 255, 255, 255, 255, 255, 240, 200,   0,   0],
        [0,   0, 200, 240, 255, 255, 255, 255, 255, 255, 255, 255, 240, 200,   0,   0],
        [0,   0, 180, 220, 240, 255, 255, 255, 255, 255, 255, 240, 220, 180,   0,   0],
        [0,   0, 150, 200, 220, 240, 255, 255, 255, 255, 240, 220, 200, 150,   0,   0],
        [0,   0,   0, 180, 200, 220, 240, 255, 255, 240, 220, 200, 180,   0,   0,   0],
        [0,   0,   0,   0, 150, 200, 220, 240, 240, 220, 200, 150,   0,   0,   0,   0],
        [0,   0,   0,   0,   0, 150, 180, 200, 200, 180, 150,   0,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0],
    ]
    
    for y in range(16):
        for x in range(16):
            pixels[x, y] = pattern[y][x]
    
    return img


def create_overlay_template():
    """Create the grayscale overlay template for spawn egg spots."""
    img = Image.new('L', (16, 16), 0)
    pixels = img.load()
    
    # Spots pattern
    pattern = [
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0, 200, 255,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0, 255, 255, 200,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0, 200, 255,   0,   0,   0,   0,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0, 255, 255, 200,   0,   0,   0,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0, 200,   0,   0,   0,   0,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0, 200, 255,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0, 255, 255, 200,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0, 200,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0],
        [0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0,   0],
    ]
    
    for y in range(16):
        for x in range(16):
            pixels[x, y] = pattern[y][x]
    
    return img


def apply_color_tint(grayscale_img, color_rgb):
    """Apply color tint to grayscale image."""
    rgba_img = Image.new('RGBA', grayscale_img.size, (0, 0, 0, 0))
    pixels_in = grayscale_img.load()
    pixels_out = rgba_img.load()
    
    r_tint, g_tint, b_tint = color_rgb
    
    for y in range(grayscale_img.size[1]):
        for x in range(grayscale_img.size[0]):
            gray_value = pixels_in[x, y]
            
            if gray_value > 0:
                intensity = gray_value / 255.0
                r = int(r_tint * intensity)
                g = int(g_tint * intensity)
                b = int(b_tint * intensity)
                a = gray_value
                
                pixels_out[x, y] = (r, g, b, a)
    
    return rgba_img


def generate_egg(base_color, overlay_color, output_file='spawn_egg.png'):
    """Generate spawn egg texture from two colors."""
    base_rgb = hex_to_rgb(base_color)
    overlay_rgb = hex_to_rgb(overlay_color)
    
    base_template = create_base_template()
    overlay_template = create_overlay_template()
    
    base_colored = apply_color_tint(base_template, base_rgb)
    overlay_colored = apply_color_tint(overlay_template, overlay_rgb)
    
    final_image = Image.alpha_composite(base_colored, overlay_colored)
    final_image.save(output_file, 'PNG')
    
    return output_file


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print("Usage: python generate_egg.py <base_color> <overlay_color>")
        print()
        print("Example:")
        print("  python generate_egg.py F6B201 FFF87E")
        print("  python generate_egg.py #0DA70B #000000")
        print()
        print("Common spawn egg colors:")
        print("  Blaze:   F6B201 FFF87E")
        print("  Creeper: 0DA70B 000000")
        print("  Spider:  342D27 A80E0E")
        sys.exit(1)
    
    base_color = sys.argv[1]
    overlay_color = sys.argv[2]
    
    try:
        output_file = generate_egg(base_color, overlay_color)
        print(f"✓ Generated spawn_egg.png")
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
