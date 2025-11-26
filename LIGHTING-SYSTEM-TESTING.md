# MattMC Lighting System Testing Documentation

This document describes the comprehensive testing infrastructure for MattMC's lighting system, including existing tests, the new bug detection tests, and guidance for creating additional tests.

---

## Table of Contents

1. [Overview](#overview)
2. [Test Categories](#test-categories)
3. [Existing Test Files](#existing-test-files)
4. [New Bug Detection Tests](#new-bug-detection-tests)
5. [How to Run Tests](#how-to-run-tests)
6. [Creating New Tests](#creating-new-tests)
7. [Common Light Propagation Bugs](#common-light-propagation-bugs)
8. [Test Patterns](#test-patterns)
9. [Debugging Light Issues](#debugging-light-issues)

---

## Overview

MattMC's lighting system uses a BFS (Breadth-First Search) flood-fill algorithm for light propagation. The system supports:

- **Sky Light**: 15 levels (0-15), propagates from sky downward
- **Block Light RGBI**: 4-channel colored lighting with Red, Green, Blue, and Intensity channels
- **Cross-Chunk Propagation**: Light propagates seamlessly across chunk boundaries
- **Deferred Updates**: Light updates for unloaded chunks are queued and applied when chunks load

### Key Classes Being Tested

| Class | Purpose |
|-------|---------|
| `LightPropagator` | Main BFS propagation algorithm for block light |
| `CrossChunkLightPropagator` | Handles light crossing chunk boundaries |
| `SkylightEngine` | Skylight propagation logic |
| `SkylightInitializer` | Initial skylight calculation for new chunks |
| `WorldLightManager` | Coordinates all lighting operations |
| `LightStorage` | Per-section storage of light values |

---

## Test Categories

### 1. Unit Tests (No world required)
Tests that validate individual methods and classes in isolation.

- `LightPropagatorTest` - BFS queue operations
- `SkylightEngineTest` - Skylight algorithms
- `SkylightInitializerTest` - Initial skylight setup

### 2. Integration Tests (Single chunk)
Tests that require a chunk but not a full world.

- `TorchRemovalTest` - Torch placement and removal
- `EnclosedRoomTorchTest` - Lighting in enclosed spaces
- `RGBLightRemovalTest` - Colored light handling

### 3. Cross-Chunk Tests (Multiple chunks)
Tests that verify light propagates across chunk boundaries.

- `CrossChunkLightTest` - Basic cross-chunk propagation
- `CrossChunkLightRemovalTest` - Light removal across boundaries
- `CrossChunkVertexLightTest` - Vertex lighting at boundaries

### 4. Bug Detection Tests
Targeted tests designed to detect common bugs.

- `LightPropagationBugDetectionTest` - Comprehensive bug detection suite

---

## Existing Test Files

The lighting system has **25+ test files** in `src/test/java/mattmc/world/level/lighting/`:

| Test File | Description |
|-----------|-------------|
| `LightPropagatorTest.java` | Core BFS propagation tests |
| `BlockLightPropagationTest.java` | Manual block light test (main method) |
| `BlockLightRegistryChangeTest.java` | Light updates when block registry changes |
| `CrossChunkLightTest.java` | Light crossing chunk boundaries |
| `CrossChunkLightRemovalTest.java` | Removal across boundaries |
| `CrossChunkLightManualTest.java` | Manual cross-chunk test |
| `CrossChunkVertexLightTest.java` | Vertex sampling across chunks |
| `DebugLightTest.java` | Debug-oriented light tests |
| `DebugAttenuationTest.java` | Attenuation debugging |
| `DiggingHoleLightTest.java` | Light behavior when digging |
| `EnclosedRoomTorchTest.java` | Light in enclosed spaces |
| `EnclosedRoomExpansionTest.java` | Expanding enclosed rooms |
| `EnclosedRoomLightRemovalTest.java` | Removal in enclosed spaces |
| `InteriorCornerLightingTest.java` | Corner case lighting |
| `LightPersistenceTest.java` | Light save/load |
| `LightPropagationIntegrationTest.java` | Full integration tests |
| `RGBLightRemovalTest.java` | Colored light removal |
| `ShaderLightingTest.java` | Shader light value tests |
| `SkylightEngineTest.java` | Skylight engine tests |
| `SkylightInitializerTest.java` | Skylight initialization |
| `SkylightPropagationTest.java` | Skylight propagation |
| `TorchRemovalTest.java` | Torch removal tests |
| `VertexLightSamplingTest.java` | Vertex light sampling |
| `VerticalShaftReopenTest.java` | Reopening vertical shafts |
| `WorldLightManagerAssignmentTest.java` | Manager assignment tests |

---

## New Bug Detection Tests

The `LightPropagationBugDetectionTest.java` provides comprehensive tests designed to detect common light propagation bugs:

### Test Categories

#### Basic Propagation
```java
@Test
void testLightPropagationDistance()     // Light reaches exactly N-1 blocks
@Test
void testSixDirectionalPropagation()    // Equal light in all 6 directions
@Test
void testDiagonalPropagation()          // Manhattan distance, not Euclidean
```

#### RGBI Color Handling
```java
@Test
void testRGBColorPropagation()          // RGB values propagate correctly
```

#### Light Removal
```java
@Test
void testCompleteLightRemoval()         // All light removed when source destroyed
@Test
void testPartialLightRemoval()          // Nearby torch light preserved
```

#### Opacity Blocking
```java
@Test
void testOpaqueBlocksStopLight()        // Stone blocks light
@Test
void testLightAroundSingleBlock()       // Light routes around obstacles
@Test
void testEnclosedRoomNoLight()          // Fully enclosed = 0 light
```

#### Edge Cases
```java
@Test
void testLightAtChunkEdgeX()            // x=15 edge
@Test
void testLightAtChunkEdgeZ()            // z=0 edge
@Test
void testLightAtYExtremes()             // Near y=0 and y=383
```

#### Multi-Source Interaction
```java
@Test
void testTwoTorchesMaxLight()           // Brightest value wins
@Test
void testSequentialTorchPlacement()     // Updates correctly
```

#### Regression Tests
```java
@Test
void testLightThroughAirGap()           // Light goes through gaps
@Test
void testNoLightLeakAfterRemoval()      // No residual light
@Test
void testCorrectAttenuationInCorners()  // Consistent attenuation
```

---

## How to Run Tests

### Run All Tests
```bash
./gradlew test
```

### Run Only Lighting Tests
```bash
./gradlew test --tests "mattmc.world.level.lighting.*"
```

### Run Specific Test File
```bash
./gradlew test --tests "mattmc.world.level.lighting.LightPropagatorTest"
```

### Run Specific Test Method
```bash
./gradlew test --tests "mattmc.world.level.lighting.LightPropagatorTest.testAddBlockLightFromTorch"
```

### Run with Verbose Output
```bash
./gradlew test --info --tests "mattmc.world.level.lighting.*"
```

### View Test Report
After running tests, open:
```
build/reports/tests/test/index.html
```

---

## Creating New Tests

### Template for Light Propagation Test

```java
package mattmc.world.level.lighting;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

public class MyNewLightTest {
    
    private WorldLightManager worldLightManager;
    private LevelChunk chunk;
    
    @BeforeEach
    public void setup() {
        worldLightManager = new WorldLightManager();
        chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
    }
    
    @Test
    @DisplayName("Descriptive test name for documentation")
    public void testMyScenario() {
        int y = LevelChunk.worldYToChunkY(64);  // Convert world Y to chunk Y
        
        // Arrange: Set up the test scenario
        chunk.setBlock(8, y, 8, Blocks.TORCH);
        
        // Act: The action happens automatically via setBlock hook
        
        // Assert: Verify expected light values
        assertEquals(14, chunk.getBlockLightI(8, y, 8), "Torch should have full light");
        assertEquals(13, chunk.getBlockLightI(9, y, 8), "Neighbor should have -1 light");
    }
}
```

### Template for Cross-Chunk Test

```java
package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MyCrossChunkTest {
    
    private Level level;
    private Path tempDir;
    
    @BeforeEach
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("mattmc-test-");
        level = new Level();
        level.setWorldDirectory(tempDir);
        level.setSeed(12345L);
    }
    
    @Test
    public void testLightCrossesChunk() {
        LevelChunk chunk0 = level.getChunk(0, 0);
        LevelChunk chunk1 = level.getChunk(1, 0);  // Adjacent chunk
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place torch at edge of chunk 0
        chunk0.setBlock(15, y, 8, Blocks.TORCH);
        
        // Light should cross into chunk 1
        int lightInChunk1 = chunk1.getBlockLightI(0, y, 8);
        assertEquals(13, lightInChunk1, "Light should cross boundary");
    }
}
```

---

## Common Light Propagation Bugs

### 1. Light Not Reaching All Blocks
**Symptom**: Some blocks don't receive light they should.

**Test Strategy**:
```java
// Check all blocks within expected radius
for (int dx = -14; dx <= 14; dx++) {
    for (int dz = -14; dz <= 14; dz++) {
        int distance = Math.abs(dx) + Math.abs(dz);
        if (distance <= 14) {
            int expectedLight = Math.max(0, 14 - distance);
            assertEquals(expectedLight, chunk.getBlockLightI(8+dx, y, 8+dz));
        }
    }
}
```

### 2. Incorrect Attenuation
**Symptom**: Light values don't decrease by 1 per block.

**Test Strategy**:
```java
// Verify exact attenuation along a line
for (int i = 0; i <= 14; i++) {
    assertEquals(14 - i, chunk.getBlockLightI(8 + i, y, 8), "Distance " + i);
}
```

### 3. Light Leaking Through Opaque Blocks
**Symptom**: Light passes through solid blocks.

**Test Strategy**:
```java
// Create enclosed room
for (int dx = -1; dx <= 1; dx++) {
    for (int dy = -1; dy <= 1; dy++) {
        for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            chunk.setBlock(8+dx, y+dy, 8+dz, Blocks.STONE);
        }
    }
}
// Place torch outside
chunk.setBlock(5, y, 8, Blocks.TORCH);
// Inside should be dark
assertEquals(0, chunk.getBlockLightI(8, y, 8), "Enclosed room should be dark");
```

### 4. Residual Light After Removal
**Symptom**: Light remains after source is destroyed.

**Test Strategy**:
```java
// Place and remove torch multiple times
for (int i = 0; i < 5; i++) {
    chunk.setBlock(8, y, 8, Blocks.TORCH);
    chunk.setBlock(8, y, 8, Blocks.AIR);
}
// Should have no residual light
assertEquals(0, chunk.getBlockLightI(8, y, 8));
assertEquals(0, chunk.getBlockLightI(9, y, 8));
```

### 5. Cross-Chunk Light Seams
**Symptom**: Visible light discontinuity at chunk boundaries.

**Test Strategy**:
```java
// Place torch at boundary
chunk0.setBlock(15, y, 8, Blocks.TORCH);

// Light should differ by exactly 1 across boundary
int lightAtBoundary = chunk0.getBlockLightI(15, y, 8);
int lightAcrossBoundary = chunk1.getBlockLightI(0, y, 8);
assertEquals(lightAtBoundary - 1, lightAcrossBoundary);
```

### 6. RGB Color Mixing Issues
**Symptom**: Colors not preserved during propagation.

**Test Strategy**:
```java
// Torch has RGB (14, 11, 0)
chunk.setBlock(8, y, 8, Blocks.TORCH);

// At neighbor, colors should be same, only intensity decreases
assertEquals(14, chunk.getBlockLightR(9, y, 8), "Red preserved");
assertEquals(11, chunk.getBlockLightG(9, y, 8), "Green preserved");
assertEquals(0, chunk.getBlockLightB(9, y, 8), "Blue preserved");
assertEquals(13, chunk.getBlockLightI(9, y, 8), "Intensity decreases");
```

---

## Test Patterns

### Pattern 1: Symmetry Test
Light should propagate equally in all directions from a central source.

```java
@Test
public void testSymmetricPropagation() {
    chunk.setBlock(8, y, 8, Blocks.TORCH);
    
    // All 6 neighbors should have equal light
    int expected = 13;
    assertEquals(expected, chunk.getBlockLightI(9, y, 8));
    assertEquals(expected, chunk.getBlockLightI(7, y, 8));
    assertEquals(expected, chunk.getBlockLightI(8, y+1, 8));
    assertEquals(expected, chunk.getBlockLightI(8, y-1, 8));
    assertEquals(expected, chunk.getBlockLightI(8, y, 9));
    assertEquals(expected, chunk.getBlockLightI(8, y, 7));
}
```

### Pattern 2: Obstacle Test
Light should route around obstacles.

```java
@Test
public void testLightAroundObstacle() {
    // Place obstacle
    chunk.setBlock(9, y, 8, Blocks.STONE);
    
    // Place torch
    chunk.setBlock(8, y, 8, Blocks.TORCH);
    
    // Light behind obstacle should come via alternate path
    int lightBehind = chunk.getBlockLightI(10, y, 8);
    assertTrue(lightBehind > 0, "Light should route around");
    assertTrue(lightBehind < 12, "Should be less than direct path");
}
```

### Pattern 3: Removal/Repropagation Test
Removing one light source should not affect another's light.

```java
@Test
public void testIndependentSources() {
    // Two torches far apart
    chunk.setBlock(2, y, 8, Blocks.TORCH);
    chunk.setBlock(14, y, 8, Blocks.TORCH);
    
    // Remove first torch
    chunk.setBlock(2, y, 8, Blocks.AIR);
    
    // Second torch's light should be unaffected
    assertEquals(14, chunk.getBlockLightI(14, y, 8));
    assertEquals(13, chunk.getBlockLightI(13, y, 8));
}
```

---

## Debugging Light Issues

### Print Light Values in Area
```java
private void printLightGrid(LevelChunk chunk, int centerX, int y, int centerZ, int radius) {
    System.out.println("Light values around (" + centerX + "," + y + "," + centerZ + "):");
    for (int dz = -radius; dz <= radius; dz++) {
        StringBuilder row = new StringBuilder();
        for (int dx = -radius; dx <= radius; dx++) {
            int light = chunk.getBlockLightI(centerX + dx, y, centerZ + dz);
            row.append(String.format("%2d ", light));
        }
        System.out.println(row);
    }
}
```

### Trace Light Path
```java
private void traceLightPath(LevelChunk chunk, int x1, int y1, int z1, int x2, int y2, int z2) {
    System.out.println("Tracing light from (" + x1 + "," + y1 + "," + z1 + 
                       ") to (" + x2 + "," + y2 + "," + z2 + "):");
    
    int dx = Integer.compare(x2 - x1, 0);
    int dy = Integer.compare(y2 - y1, 0);
    int dz = Integer.compare(z2 - z1, 0);
    
    int x = x1, y = y1, z = z1;
    while (x != x2 || y != y2 || z != z2) {
        int light = chunk.getBlockLightI(x, y, z);
        String block = chunk.getBlock(x, y, z).getIdentifier();
        System.out.println("  (" + x + "," + y + "," + z + ") light=" + light + " block=" + block);
        
        if (x != x2) x += dx;
        else if (y != y2) y += dy;
        else if (z != z2) z += dz;
    }
}
```

---

## Summary

The MattMC lighting system has comprehensive test coverage with 25+ existing test files plus new bug detection tests. Key areas covered:

1. **Basic BFS propagation** - Light reaches correct distances
2. **RGBI color system** - Colored light propagates correctly
3. **Light removal** - Sources can be removed cleanly
4. **Cross-chunk** - No seams at boundaries
5. **Opacity** - Blocks properly stop light
6. **Edge cases** - Chunk edges, Y extremes, corners

To create effective new tests:
1. Use the templates provided above
2. Focus on one specific behavior per test
3. Use `@DisplayName` for clear documentation
4. Assert specific values, not just "non-zero"
5. Consider edge cases and boundaries

---

*Document generated: November 2024*
*Based on MattMC lighting system analysis*
