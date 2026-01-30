#!/usr/bin/env python3
"""
Minecraft Spawn Egg Texture Generator

This script generates spawn egg textures using the classic Minecraft two-color system.
In older versions of Minecraft (1.12-1.16), spawn eggs were generated at runtime by 
tinting grayscale base and overlay textures with two specified colors.

Usage:
    python generate_egg_texture.py

The script will prompt for:
    - Base color (hex, e.g., #F6B201 or F6B201)
    - Overlay color (hex, e.g., #FFF87E or FFF87E)

Output:
    Generates a 16x16 PNG file named 'spawn_egg.png' in the current directory.
"""

from PIL import Image, ImageDraw
import sys
import os


def hex_to_rgb(hex_color):
    """
    Convert a hex color string to RGB tuple.
    
    Args:
        hex_color: String like '#F6B201' or 'F6B201'
    
    Returns:
        Tuple of (r, g, b) values (0-255)
    """
    # Remove '#' if present
    hex_color = hex_color.lstrip('#')
    
    # Validate hex color
    if len(hex_color) != 6:
        raise ValueError(f"Invalid hex color: {hex_color}. Must be 6 characters.")
    
    try:
        r = int(hex_color[0:2], 16)
        g = int(hex_color[2:4], 16)
        b = int(hex_color[4:6], 16)
        return (r, g, b)
    except ValueError:
        raise ValueError(f"Invalid hex color: {hex_color}. Must contain only hex digits (0-9, A-F).")


def create_base_template():
    """
    Create the grayscale base template for a spawn egg.
    This represents the main body of the egg.
    
    Returns:
        PIL Image object (16x16 grayscale)
    """
    # Create a 16x16 grayscale image
    img = Image.new('L', (16, 16), 0)
    pixels = img.load()
    
    # Classic Minecraft spawn egg base shape
    # This creates an egg-like oval shape with shading
    # Based on the vanilla spawn egg template
    
    # Egg shape pattern (values represent grayscale intensity: 0=black, 255=white)
    # Higher values will be more affected by the color tint
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
    """
    Create the grayscale overlay template for a spawn egg.
    This represents the spots/dots pattern on the egg.
    
    Returns:
        PIL Image object (16x16 grayscale)
    """
    # Create a 16x16 grayscale image
    img = Image.new('L', (16, 16), 0)
    pixels = img.load()
    
    # Classic Minecraft spawn egg spots pattern
    # These are the characteristic dots/spots on spawn eggs
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
    """
    Apply a color tint to a grayscale image.
    This simulates how Minecraft applies colors to spawn egg templates.
    
    Args:
        grayscale_img: PIL Image object in grayscale mode
        color_rgb: Tuple of (r, g, b) color values
    
    Returns:
        PIL Image object in RGBA mode with the color tint applied
    """
    # Convert grayscale to RGBA
    rgba_img = Image.new('RGBA', grayscale_img.size, (0, 0, 0, 0))
    pixels_in = grayscale_img.load()
    pixels_out = rgba_img.load()
    
    r_tint, g_tint, b_tint = color_rgb
    
    for y in range(grayscale_img.size[1]):
        for x in range(grayscale_img.size[0]):
            # Get the grayscale value (0-255)
            gray_value = pixels_in[x, y]
            
            # If the pixel is not completely black, apply the color tint
            if gray_value > 0:
                # Calculate the tinted color
                # The grayscale value acts as both the intensity and alpha
                intensity = gray_value / 255.0
                
                r = int(r_tint * intensity)
                g = int(g_tint * intensity)
                b = int(b_tint * intensity)
                a = gray_value  # Use gray value as alpha
                
                pixels_out[x, y] = (r, g, b, a)
    
    return rgba_img


def generate_spawn_egg(base_color_hex, overlay_color_hex, output_filename='spawn_egg.png'):
    """
    Generate a spawn egg texture using two colors.
    
    Args:
        base_color_hex: Hex color string for the base (e.g., '#F6B201')
        overlay_color_hex: Hex color string for the overlay/spots (e.g., '#FFF87E')
        output_filename: Output filename for the generated PNG
    
    Returns:
        Path to the generated file
    """
    try:
        # Convert hex to RGB
        base_rgb = hex_to_rgb(base_color_hex)
        overlay_rgb = hex_to_rgb(overlay_color_hex)
        
        print(f"Base color: {base_color_hex} -> RGB{base_rgb}")
        print(f"Overlay color: {overlay_color_hex} -> RGB{overlay_rgb}")
        
        # Create templates
        print("Creating base template...")
        base_template = create_base_template()
        
        print("Creating overlay template...")
        overlay_template = create_overlay_template()
        
        # Apply color tints
        print("Applying base color tint...")
        base_colored = apply_color_tint(base_template, base_rgb)
        
        print("Applying overlay color tint...")
        overlay_colored = apply_color_tint(overlay_template, overlay_rgb)
        
        # Composite the images (overlay on top of base)
        print("Compositing final image...")
        final_image = Image.alpha_composite(base_colored, overlay_colored)
        
        # Save the result
        output_path = os.path.join(os.path.dirname(__file__), output_filename)
        final_image.save(output_path, 'PNG')
        
        print(f"\n✓ Spawn egg texture generated successfully!")
        print(f"  Output: {output_path}")
        print(f"  Size: 16x16 pixels")
        
        return output_path
        
    except ValueError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Unexpected error: {e}", file=sys.stderr)
        sys.exit(1)


def main():
    """Main entry point for the script."""
    print("=" * 60)
    print("  Minecraft Spawn Egg Texture Generator")
    print("=" * 60)
    print()
    print("This tool generates spawn egg textures using the classic")
    print("two-color system from older Minecraft versions.")
    print()
    print("Examples of color combinations:")
    print("  Blaze:   Base=#F6B201  Overlay=#FFF87E")
    print("  Creeper: Base=#0DA70B  Overlay=#000000")
    print("  Spider:  Base=#342D27  Overlay=#A80E0E")
    print()
    print("-" * 60)
    print()
    
    # Get base color
    while True:
        base_color = input("Enter base color (hex, e.g., F6B201 or #F6B201): ").strip()
        try:
            hex_to_rgb(base_color)  # Validate
            break
        except ValueError as e:
            print(f"Invalid input: {e}")
            print("Please try again.\n")
    
    # Get overlay color
    while True:
        overlay_color = input("Enter overlay/spots color (hex, e.g., FFF87E or #FFF87E): ").strip()
        try:
            hex_to_rgb(overlay_color)  # Validate
            break
        except ValueError as e:
            print(f"Invalid input: {e}")
            print("Please try again.\n")
    
    print()
    print("-" * 60)
    print()
    
    # Generate the texture
    generate_spawn_egg(base_color, overlay_color)
    
    print()
    print("You can now use this texture in your Minecraft resource pack!")
    print()


if __name__ == '__main__':
    main()
