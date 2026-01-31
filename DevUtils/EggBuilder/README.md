# Spawn Egg Texture Generator

Generates Minecraft spawn egg textures from two hex colors.

## Usage

```bash
python generate_egg.py <base_color> <overlay_color>
```

## Examples

```bash
# Blaze spawn egg
python generate_egg.py F6B201 FFF87E

# Creeper spawn egg
python generate_egg.py 0DA70B 000000

# Spider spawn egg
python generate_egg.py 342D27 A80E0E
```

## Requirements

```bash
pip install -r requirements.txt
```

## Output

Generates `spawn_egg.png` (16x16) in the current directory.

## Color Reference

### Vanilla Minecraft
- Blaze: F6B201 / FFF87E
- Creeper: 0DA70B / 000000
- Spider: 342D27 / A80E0E

### AlexsMobs (from AMItemRegistry.java)
- Grizzly Bear: 693A2C / 976144
- Roadrunner: 3A2E26 / FBE9CE
- Crocodile: 738940 / A6A15E
- Hummingbird: 325E7F / 44A75F

### AlexsCaves (from ACItemRegistry.java)
- Tremorsaurus: 53780E / DFA211
- Nucleeper: 95A1A5 / 00FF00
- Gummy Bear: FF463F / FDA09E
