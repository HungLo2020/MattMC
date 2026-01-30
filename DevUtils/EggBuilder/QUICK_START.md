# Quick Start Guide

## Installation

1. Install the required dependency:
   ```bash
   pip install -r requirements.txt
   ```

## Usage Examples

### Generate a Custom Spawn Egg

Run the interactive tool:
```bash
python generate_egg_texture.py
```

You will be prompted for two colors:
- **Base color**: The main body color (e.g., `F6B201`)
- **Overlay color**: The spots color (e.g., `FFF87E`)

The tool will generate `spawn_egg.png` in the current directory.

### Generate All AlexsMobs Spawn Eggs

```bash
python generate_alexsmobs_eggs.py
```

Output: `alexsmobs_eggs/` directory with 30 PNG files

### Generate All AlexsCaves Spawn Eggs

```bash
python generate_alexscaves_eggs.py
```

Output: `alexscaves_eggs/` directory with 43 PNG files

### Generate Vanilla Examples

```bash
python generate_examples.py
```

Generates example spawn eggs for Blaze, Creeper, Spider, etc.

## Finding Color Codes

### From Java Code

Look for spawn egg registrations like:
```java
new DeferredSpawnEggItem(entityType, 0xF6B201, 0xFFF87E, properties);
                                     ^^^^^^^^  ^^^^^^^^
                                     Base      Overlay
```

### From Minecraft Wiki

Visit the [Spawn Egg colors page](https://minecraft.wiki/w/Spawn_Egg_colors) for vanilla mob colors.

## Next Steps

1. Generate the textures you need
2. Rename them to match your entity names
3. Copy them to your mod's resource pack:
   ```
   src/main/resources/assets/yourmod/textures/item/
   ```
4. Reference them in your item JSON or code

## Troubleshooting

**Error: ModuleNotFoundError: No module named 'PIL'**
- Run: `pip install -r requirements.txt`

**Colors look wrong**
- Verify you're using the correct hex values (6 characters)
- Check if base and overlay are swapped

**Need different patterns**
- The egg shape and spots pattern are based on vanilla Minecraft
- For custom patterns, you'll need to modify the template generation in `generate_egg_texture.py`
