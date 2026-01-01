# ColorRemapper

A Python tool to remap colors in PNG images based on hex color mappings.

## Features

- **Batch Processing**: Automatically processes all PNG files in the same directory
- **Color Preservation**: Applies all mappings simultaneously to prevent color wipeout
- **Dynamic Mappings**: Supports any number of color mappings read from a configuration file
- **In-Place Updates**: Overwrites original files without changing filenames

## Requirements

- Python 3.x
- Pillow (PIL) library

Install Pillow:
```bash
pip install Pillow
```

## Usage

1. Place the `color_remapper.py` script in a directory with your PNG files
2. Create or edit `color_mappings.txt` in the same directory
3. Add color mappings in the format: `source_hex -> target_hex`
4. Run the script:
   ```bash
   python3 color_remapper.py
   ```

## Color Mappings Format

Edit `color_mappings.txt` to define your color mappings:

```
# Comments start with #
# Format: source_hex -> target_hex
# You can omit the # prefix from hex colors

8b8b8b -> 555555
ff0000 -> 00ff00
123456 -> abcdef
```

## How It Works

The script reads all color mappings from the configuration file and applies them **simultaneously** to each PNG image. This ensures that chained mappings (e.g., A→B, B→C) work correctly without causing color wipeout where A would incorrectly become C.

### Example

If you have mappings:
- `8b8b8b -> 555555`
- `555555 -> 333333`

The script will:
- Convert all `#8b8b8b` pixels to `#555555`
- Convert all original `#555555` pixels to `#333333`
- **NOT** convert the newly created `#555555` pixels (from step 1) to `#333333`

This is achieved by reading the original pixel values once and applying all transformations based on those original values.

## Output

The script will:
- Display all loaded color mappings
- List all PNG files found in the directory
- Process each PNG file and report which ones were modified
- Overwrite the original files with the remapped versions

## Notes

- The script only processes PNG files in the same directory (not subdirectories)
- Only pixels that exactly match a source color will be remapped
- Alpha channel (transparency) is preserved
- Original files are overwritten - make backups if needed!
