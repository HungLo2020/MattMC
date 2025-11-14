# Step 8: Voxel Ambient Occlusion (AO)

## Implementation

Added Minecraft-style voxel ambient occlusion using the two-sides + corner rule.

### AO Calculation Algorithm

For each vertex, sample 3 positions: 2 side neighbors + 1 corner diagonal.

**Rules:**
- **Both sides solid** → Strongest AO (value 3)
- **One side + corner solid** → Medium AO (value 2)
- **Only one neighbor solid** → Weak AO (value 1)
- **No neighbors solid** → No AO (value 0)

### Implementation Details

**MeshBuilder.java:**
- `calculateVertexAO()` - Implements Minecraft AO rules
- `getAOSampleOffsets()` - Returns 2 sides + 1 corner offset for each face/corner combination
- `isBlockOpaque()` - Checks if block has opacity >= 15 (blocks light)
- `sampleVertexLight()` - Now calculates AO alongside light sampling

**Shader (voxel_lit.fs):**
- `aoToBrightness()` - Maps AO value (0-3) to brightness multiplier
- AO 0 → 100% brightness (no darkening)
- AO 1 → 73% brightness (weak darkening)
- AO 2 → 47% brightness (medium darkening)
- AO 3 → 20% brightness (strong darkening)
- Configurable strength via `uAOStrength` uniform (0.0-1.0)

**VoxelLitShader.java:**
- Added `setAOStrength(float)` method (default 0.8)
- Added `getAOStrength()` getter
- Updated `applyDefaultLighting()` to set AO strength

### Vertex Format

Each vertex stores 15 floats:
```
[0-2]:   position (x, y, z)
[3-4]:   texcoord (u, v)
[5-8]:   color (r, g, b, a)
[9-11]:  normal (nx, ny, nz)
[12-14]: light (skyLight, blockLight, ao) ← AO NOW POPULATED
```

### Configuration

**Default Values:**
- AO Strength: 0.8 (80% darkening at maximum)

**Runtime Adjustment:**
```java
VoxelLitShader shader = renderer.getShader();
shader.setAOStrength(1.0f);  // Full strength (100% darkening)
shader.setAOStrength(0.5f);  // Half strength (50% darkening)
shader.setAOStrength(0.0f);  // Disabled (no darkening)
```

### Visual Examples

**Concave Corners:**
- Inside corners where 2 walls meet: Strong AO (darkest)
- Creates depth perception in caves and indoor spaces

**Caves:**
- Ceiling meets walls: Medium to strong AO
- Floor meets walls: Medium to strong AO
- Enhances spatial awareness

**Block Edges:**
- Vertices touching solid neighbors: Subtle AO gradient
- Creates definition between blocks

### Testing

**Build:**
```bash
./gradlew installDist
```

**Run:**
```bash
./build/install/MattMC/bin/MattMC
```

**Manual Test Steps:**

1. **Build concave corners:**
   - Create an L-shaped structure
   - Notice darkening at inside corners

2. **Create a cave:**
   - Dig underground room
   - Notice darkening where ceiling/floor meet walls

3. **Toggle AO on/off (via code):**
   ```java
   shader.setAOStrength(0.0f); // Off
   shader.setAOStrength(0.8f); // Default
   shader.setAOStrength(1.0f); // Maximum
   ```

4. **Compare:**
   - With AO: Corners and edges are darker, creating depth
   - Without AO: Flat lighting, less depth perception

### Performance

- AO calculated at mesh bake time (not per-frame)
- Negligible runtime cost (just vertex attribute interpolation)
- 3 block samples per vertex (minimal overhead)

### Files Changed

1. `MeshBuilder.java` - Added AO calculation methods
2. `voxel_lit.fs` - Added AO application in fragment shader
3. `VoxelLitShader.java` - Added AO strength configuration
4. `STEP8-AMBIENT-OCCLUSION.md` - Documentation

### Future Enhancements

Possible improvements:
- Cross-chunk AO sampling for seamless edges
- Per-block AO override (e.g., disable for transparent blocks)
- Smooth AO (more samples for higher quality)
- Directional AO based on sun position
