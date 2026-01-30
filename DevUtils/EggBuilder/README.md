# Spawn Egg Texture Generator

This tool generates Minecraft spawn egg textures using the classic two-color system from older versions of Minecraft (1.12-1.16).

## Background

In older versions of Minecraft and Minecraft Forge, spawn egg textures were generated programmatically at runtime. Each spawn egg was defined in Java code with two hex color values:
- **Base color**: The main body/background color of the egg
- **Overlay color**: The color of the spots/dots pattern on the egg

Example from older Minecraft mods:
```java
new ForgeSpawnEggItem(
    entityType,
    0xF6B201,  // Base color (yellow)
    0xFFF87E,  // Overlay color (light yellow)
    itemProperties
);
```

In newer versions (1.21+), spawn eggs require actual texture files. This tool helps convert those two-color definitions into proper texture files.

## Requirements

- Python 3.6 or later
- PIL/Pillow library

## Installation

Install the required dependency:
```bash
pip install Pillow
```

## Usage

Run the script:
```bash
cd DevUtils/EggBuilder
python generate_egg_texture.py
```

The script will prompt you for two hex colors:
1. **Base color**: The main color of the egg body
2. **Overlay color**: The color of the spots/dots

### Input Format

Colors can be entered with or without the `#` prefix:
- `F6B201` ✓
- `#F6B201` ✓

### Examples

#### Vanilla Minecraft spawn egg colors:

| Mob | Base Color | Overlay Color |
|-----|------------|---------------|
| Blaze | `#F6B201` | `#FFF87E` |
| Creeper | `#0DA70B` | `#000000` |
| Spider | `#342D27` | `#A80E0E` |
| Zombie | `#00AFAF` | `#799C65` |
| Skeleton | `#C1C1C1` | `#494949` |
| Enderman | `#161616` | `#000000` |

#### AlexsMobs spawn egg colors (first 10):

| Mob | Base Color | Overlay Color |
|-----|------------|---------------|
| Grizzly Bear | `#693A2C` | `#976144` |
| Roadrunner | `#3A2E26` | `#FBE9CE` |
| Bone Serpent | `#E5D9C4` | `#FF6038` |
| Gazelle | `#DDA675` | `#2C2925` |
| Crocodile | `#738940` | `#A6A15E` |
| Fly | `#464241` | `#892E2E` |
| Hummingbird | `#325E7F` | `#44A75F` |
| Orca | `#2C2C2C` | `#D6D8E4` |
| Sunbird | `#F6694F` | `#FFDDA0` |
| Gorilla | `#595B5D` | `#1C1C21` |

See `generate_alexsmobs_eggs.py` for the complete list of 30+ AlexsMobs spawn eggs.

### Output

The script generates a file named `spawn_egg.png` in the current directory (DevUtils/EggBuilder/).

The output is a 16x16 pixel PNG texture that can be used directly in your Minecraft resource pack or mod.

## Batch Generation Scripts

Two batch generation scripts are provided for convenience:

### generate_examples.py
Generates a few common vanilla Minecraft spawn eggs as examples.

```bash
python generate_examples.py
```

### generate_alexsmobs_eggs.py
Generates all 30 AlexsMobs spawn egg textures using the exact color codes from the mod's source code.

```bash
python generate_alexsmobs_eggs.py
```

This will create an `alexsmobs_eggs/` directory with all the textures.

### generate_alexscaves_eggs.py
Generates all 43 AlexsCaves spawn egg textures using the exact color codes from the mod's source code.

```bash
python generate_alexscaves_eggs.py
```

This will create an `alexscaves_eggs/` directory with all the textures.

## Using the Generated Textures

1. Run the script with your desired colors
2. The generated `spawn_egg.png` will be created in the EggBuilder directory
3. Rename it to match your entity (e.g., `custom_mob_spawn_egg.png`)
4. Place it in the appropriate location in your mod's resources

## How It Works

The script:
1. Creates a grayscale base template (egg body shape)
2. Creates a grayscale overlay template (spots pattern)
3. Tints the base with the base color
4. Tints the overlay with the overlay color
5. Composites the two layers together
6. Outputs a 16x16 PNG texture

This mimics how Minecraft historically generated spawn egg textures at runtime.

## Troubleshooting

### "ModuleNotFoundError: No module named 'PIL'"
Install Pillow: `pip install Pillow`

### Invalid hex color error
Make sure your color is exactly 6 hex digits (0-9, A-F). Examples:
- ✓ `F6B201`
- ✓ `#0DA70B`
- ✗ `F6B` (too short)
- ✗ `GGGGGG` (invalid characters)

## Credits

Based on the spawn egg texture system used in Minecraft 1.12-1.16 and Minecraft Forge.
