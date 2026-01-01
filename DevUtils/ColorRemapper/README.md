# ColorRemapper

A Python tool to darken colors in PNG images based on a configurable darkness percentage.

## Features

- **Batch Processing**: Automatically processes all PNG files in the same directory
- **Two Modes**: Target specific colors OR darken all colors with `-A` switch
- **Color Preservation**: Applies all mappings simultaneously to prevent color wipeout
- **Configurable Darkness**: Easily adjust how much darker colors should become
- **Dynamic Color List**: Supports any number of colors read from a configuration file
- **In-Place Updates**: Overwrites original files without changing filenames

## Requirements

- Python 3.x
- Pillow (PIL) library

Install Pillow:
```bash
pip install Pillow
```

## Usage

### Basic Mode (Specific Colors)

1. Place the `color_remapper.py` script in a directory with your PNG files
2. Create or edit `color_mappings.txt` in the same directory
3. Set the `DARKNESS_PERCENT` variable and list the hex colors to darken
4. Run the script:
   ```bash
   python3 color_remapper.py
   ```

### All Colors Mode

To darken **ALL** colors in your PNG files (not just specific ones), use the `-A` switch:

```bash
python3 color_remapper.py -A
```

In this mode:
- The script reads only the `DARKNESS_PERCENT` from `color_mappings.txt`
- ALL colors in the PNG files are darkened by that percentage
- Individual color listings in the file are ignored

## Color Mappings Format

Edit `color_mappings.txt` to define your darkness percentage and colors:

```
# Set how much darker to make colors (0-100)
DARKNESS_PERCENT=40

# List hex colors to darken (one per line)
# You can omit the # prefix from hex colors
8b8b8b
c6c6c6
ffffff
```

## How It Works

The script reads the darkness percentage and color list from the configuration file. For each color listed, it calculates a darkened version by reducing the RGB values by the specified percentage. All mappings are then applied **simultaneously** to each PNG image.

### Darkening Calculation

- **40% darker** means the color is reduced to 60% of its original brightness
- For example: `#ffffff` (255, 255, 255) at 40% darker becomes `#999999` (153, 153, 153)
- Formula: `new_value = original_value × (1 - darkness_percent / 100)`

### Color Preservation

The script applies all mappings simultaneously based on original pixel values to prevent color wipeout. This ensures that if multiple colors in your list would create a chain (e.g., color A darkens to B, and B is also in the list), the colors won't cascade.

## Output

The script will:
- Display the darkness percentage
- Show all loaded color mappings (original -> darkened)
- List all PNG files found in the directory
- Process each PNG file and report which ones were modified
- Overwrite the original files with the darkened versions

## Notes

- The script only processes PNG files in the same directory (not subdirectories)
- **Basic mode**: Only pixels that exactly match a listed color will be darkened
- **All colors mode (`-A`)**: Every color in the image is darkened
- Alpha channel (transparency) is preserved
- Original files are overwritten - make backups if needed!
- Darkness percentage can be any value from 0-100 (0 = no change, 100 = black)
