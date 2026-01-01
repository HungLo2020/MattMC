# Dark Mode PNG Converter

This utility converts PNG images to dark mode by applying the same color transformations used in the game's dark mode feature.

## What It Does

The script applies these transformations to each pixel:
- **Light colors** (luminance > 50%): Darkened to 45% brightness
- **Dark colors** (luminance ≤ 50%): Lightened by adding 30 to RGB values
- **Alpha channel**: Preserved (transparency is maintained)

This matches the transformation logic in `DarkModeColorTransform.java`.

## Requirements

- Python 3.6 or higher
- Pillow (PIL) library

Install Pillow with:
```bash
pip install Pillow
```

## Usage

1. **Place your PNG files** in this directory (`DevUtils/DarkmodeConverter/`)

2. **Run the script** from the command line:
   ```bash
   cd DevUtils/DarkmodeConverter
   python convert_to_darkmode.py
   ```
   
   Or from the project root:
   ```bash
   python DevUtils/DarkmodeConverter/convert_to_darkmode.py
   ```

3. **The script will:**
   - Find all PNG files in the same directory
   - Apply dark mode transformation to each file
   - Overwrite the original files with the converted versions

## Important Notes

⚠️ **Warning**: This script **overwrites** the original PNG files. Make sure to:
- Keep backups of your original files before running
- Only place PNG files you want to convert in this directory

## Example Output

```
Found 3 PNG file(s) to convert:
--------------------------------------------------
Processing: button.png
  ✓ Converted and saved: button.png
Processing: icon.png
  ✓ Converted and saved: icon.png
Processing: background.png
  ✓ Converted and saved: background.png
--------------------------------------------------
Conversion complete! Processed 3 file(s).
```

## How It Works

The script:
1. Scans the directory for all `.png` files
2. Opens each image and converts it to RGBA mode (if needed)
3. Processes each pixel:
   - Calculates luminance using: `0.299*R + 0.587*G + 0.114*B`
   - Applies appropriate transformation based on luminance
   - Preserves alpha channel for transparency
4. Saves the transformed image, overwriting the original
