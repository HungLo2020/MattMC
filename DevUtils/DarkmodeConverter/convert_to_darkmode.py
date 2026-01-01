#!/usr/bin/env python3
"""
Dark Mode PNG Converter

This script applies dark mode color transformations to PNG files.
It processes all PNG files in the same directory and overwrites them
with the dark mode converted versions.

The transformation logic matches the Java implementation in
DarkModeColorTransform.java:
- Light colors (luminance > 0.5): Darkened to 45% brightness
- Dark colors (luminance <= 0.5): Lightened by adding 30 to RGB values
- Alpha channel is preserved

Usage:
    python convert_to_darkmode.py
"""

import os
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("Error: PIL (Pillow) library is required.")
    print("Install it with: pip install Pillow")
    sys.exit(1)


# Dark mode parameters (matching Java constants)
BRIGHTNESS_REDUCTION = 0.45  # Reduce brightness to 45%
LIGHTEN_AMOUNT = 30          # Amount to lighten dark colors


def calculate_luminance(r, g, b):
    """
    Calculate luminance of a color using the same formula as Java.
    Returns a value between 0.0 and 1.0
    """
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0


def transform_color(r, g, b, a):
    """
    Transform a single color according to dark mode rules.
    
    Args:
        r, g, b: RGB color values (0-255)
        a: Alpha value (0-255)
    
    Returns:
        Tuple of (r, g, b, a) with transformed values
    """
    # Calculate luminance to determine if color is light or dark
    luminance = calculate_luminance(r, g, b)
    
    if luminance > 0.5:
        # Light colors: darken significantly
        r = int(r * BRIGHTNESS_REDUCTION)
        g = int(g * BRIGHTNESS_REDUCTION)
        b = int(b * BRIGHTNESS_REDUCTION)
    else:
        # Dark colors: lighten slightly for visibility
        r = min(255, r + LIGHTEN_AMOUNT)
        g = min(255, g + LIGHTEN_AMOUNT)
        b = min(255, b + LIGHTEN_AMOUNT)
    
    return (r, g, b, a)


def convert_png_to_darkmode(image_path):
    """
    Convert a PNG file to dark mode and overwrite the original.
    
    Args:
        image_path: Path to the PNG file
    """
    print(f"Processing: {image_path.name}")
    
    try:
        # Open the image
        img = Image.open(image_path)
        
        # Convert to RGBA if not already (to handle alpha channel)
        if img.mode != 'RGBA':
            img = img.convert('RGBA')
        
        # Get pixel data
        pixels = img.load()
        width, height = img.size
        
        # Process each pixel
        for y in range(height):
            for x in range(width):
                r, g, b, a = pixels[x, y]
                
                # Only transform non-transparent pixels
                if a > 0:
                    r, g, b, a = transform_color(r, g, b, a)
                    pixels[x, y] = (r, g, b, a)
        
        # Save the image, overwriting the original
        img.save(image_path, 'PNG')
        print(f"  ✓ Converted and saved: {image_path.name}")
        
    except Exception as e:
        print(f"  ✗ Error processing {image_path.name}: {e}")


def main():
    """Main function to process all PNG files in the current directory."""
    # Get the directory where the script is located
    script_dir = Path(__file__).parent
    
    # Find all PNG files in the same directory
    png_files = list(script_dir.glob("*.png"))
    
    if not png_files:
        print("No PNG files found in the current directory.")
        return
    
    print(f"Found {len(png_files)} PNG file(s) to convert:")
    print("-" * 50)
    
    # Process each PNG file
    for png_file in png_files:
        convert_png_to_darkmode(png_file)
    
    print("-" * 50)
    print(f"Conversion complete! Processed {len(png_files)} file(s).")


if __name__ == "__main__":
    main()
