#!/usr/bin/env python3
"""
ColorRemapper - A tool to darken colors in PNG images based on a configurable percentage.

This script reads hex colors from a text file and darkens them by a specified percentage,
then applies these transformations to all PNG files in the same directory. It ensures 
that colors are not wiped out by applying all mappings simultaneously based on the 
original pixel values.
"""

import os
from pathlib import Path
from PIL import Image


def hex_to_rgb(hex_color):
    """Convert hex color string to RGB tuple."""
    hex_color = hex_color.strip().lstrip('#')
    if len(hex_color) != 6:
        raise ValueError(f"Invalid hex color: {hex_color}")
    return tuple(int(hex_color[i:i+2], 16) for i in (0, 2, 4))


def darken_color(rgb, darkness_percent):
    """
    Darken an RGB color by a given percentage.
    
    Args:
        rgb: Tuple of (r, g, b) values (0-255)
        darkness_percent: Percentage to darken (0-100)
    
    Returns:
        Tuple of (r, g, b) values for the darkened color
    """
    factor = 1.0 - (darkness_percent / 100.0)
    return tuple(int(max(0, min(255, c * factor))) for c in rgb)


def read_color_mappings(mapping_file):
    """
    Read color mappings from a text file.
    Expected format: 
    - DARKNESS_PERCENT=40 (at the top)
    - Individual hex colors (one per line)
    Each color will be darkened by the specified percentage.
    """
    mappings = {}
    darkness_percent = 40  # Default value
    
    if not os.path.exists(mapping_file):
        print(f"Warning: Mapping file '{mapping_file}' not found.")
        return mappings
    
    with open(mapping_file, 'r') as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()
            
            # Skip empty lines and comments
            if not line or line.startswith('#'):
                continue
            
            # Check for darkness percentage setting
            if line.startswith('DARKNESS_PERCENT='):
                try:
                    darkness_percent = float(line.split('=')[1].strip())
                    print(f"Darkness percentage set to: {darkness_percent}%")
                    continue
                except (ValueError, IndexError) as e:
                    print(f"Warning: Line {line_num} invalid DARKNESS_PERCENT format: {line}")
                    continue
            
            # Parse hex color
            try:
                source_rgb = hex_to_rgb(line)
                target_rgb = darken_color(source_rgb, darkness_percent)
                mappings[source_rgb] = target_rgb
                print(f"Loaded mapping: #{line.lstrip('#')} -> #{target_rgb[0]:02x}{target_rgb[1]:02x}{target_rgb[2]:02x} ({darkness_percent}% darker)")
            except ValueError as e:
                print(f"Warning: Line {line_num} error: {e}")
                continue
    
    return mappings


def remap_image_colors(image_path, color_mappings):
    """
    Remap colors in an image based on the provided color mappings.
    
    This function applies ALL mappings simultaneously based on the original
    pixel values to avoid the color wipeout problem where sequential mappings
    would cause colors to cascade (e.g., A->B, B->C would make A become C).
    """
    if not color_mappings:
        print(f"No color mappings to apply for {image_path}")
        return False
    
    try:
        # Open image and convert to RGBA to handle transparency
        img = Image.open(image_path)
        img = img.convert('RGBA')
        
        # Get pixel data
        pixels = img.load()
        width, height = img.size
        
        modified = False
        
        # Apply all mappings simultaneously based on original pixel values
        for y in range(height):
            for x in range(width):
                r, g, b, a = pixels[x, y]
                original_color = (r, g, b)
                
                # Check if this color needs to be remapped
                if original_color in color_mappings:
                    new_color = color_mappings[original_color]
                    pixels[x, y] = (new_color[0], new_color[1], new_color[2], a)
                    modified = True
        
        if modified:
            # Save back to the same file
            img.save(image_path, 'PNG')
            print(f"✓ Remapped colors in {os.path.basename(image_path)}")
            return True
        else:
            print(f"  No matching colors found in {os.path.basename(image_path)}")
            return False
            
    except Exception as e:
        print(f"Error processing {image_path}: {e}")
        return False


def main():
    """Main function to process all PNG files in the current directory."""
    # Get the directory where this script is located
    script_dir = Path(__file__).parent.absolute()
    
    print(f"ColorRemapper - Processing directory: {script_dir}")
    print("=" * 60)
    
    # Read color mappings from text file
    mapping_file = script_dir / 'color_mappings.txt'
    color_mappings = read_color_mappings(mapping_file)
    
    if not color_mappings:
        print("\nNo valid color mappings found. Please create a 'color_mappings.txt' file")
        print("with hex colors (one per line) and DARKNESS_PERCENT setting")
        print("Example:")
        print("  DARKNESS_PERCENT=40")
        print("  8b8b8b")
        print("  c6c6c6")
        return
    
    print(f"\nTotal mappings loaded: {len(color_mappings)}")
    print("=" * 60)
    
    # Find all PNG files in the directory (excluding subdirectories)
    png_files = list(script_dir.glob('*.png'))
    
    if not png_files:
        print("\nNo PNG files found in the directory.")
        return
    
    print(f"\nFound {len(png_files)} PNG file(s) to process:")
    for png_file in png_files:
        print(f"  - {png_file.name}")
    
    print("\n" + "=" * 60)
    print("Processing images...")
    print("=" * 60 + "\n")
    
    # Process each PNG file
    processed_count = 0
    for png_file in png_files:
        if remap_image_colors(png_file, color_mappings):
            processed_count += 1
    
    print("\n" + "=" * 60)
    print(f"Complete! Modified {processed_count} of {len(png_files)} image(s).")
    print("=" * 60)


if __name__ == '__main__':
    main()
