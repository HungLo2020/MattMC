# Distant Horizons Shader Compilation Fix - Summary

## Problem Description

When enabling shaders with Distant Horizons, the game would fail to load shaders with the following error:

```
[16:41:05] [Render thread/WARN]: Shader compilation log for dh_terrain.fsh: 0(366) : error C1503: undefined variable "dhProjection"
0(367) : error C1503: undefined variable "dhProjectionInverse"
[16:41:05] [Render thread/ERROR]: Failed to create shader rendering pipeline, disabling shaders!
```

## Root Cause Analysis

The issue occurred because:

1. **Shader Pack Expectations**: The Complementary shader pack (and likely other DH-compatible shaders) expects `dhProjection` and `dhProjectionInverse` uniforms to be available in DH terrain shaders. These are defined in the shader pack's `lib/uniforms.glsl`:

```glsl
#ifdef DISTANT_HORIZONS
    uniform int dhRenderDistance;
    
    uniform mat4 dhProjection;
    uniform mat4 dhProjectionInverse;
    
    uniform sampler2D dhDepthTex;
    uniform sampler2D dhDepthTex1;
#endif
```

2. **Iris Implementation**: Iris was only injecting `iris_ProjectionMatrix` and `iris_ProjectionMatrixInverse` uniforms during shader transformation, but not the DH-specific `dhProjection` and `dhProjectionInverse` uniforms that shader packs expect.

3. **Compilation from Source**: This issue manifested specifically in this project because all mods (including Iris) are compiled completely from source, which differs from using pre-compiled mod jars where this issue might have been already addressed or worked around.

## Solution Implemented

The fix involved adding support for DH-specific projection uniforms at multiple levels:

### 1. Shader Transformation Layer (DHTerrainTransformer.java & DHGenericTransformer.java)

Added injection of `dhProjection` and `dhProjectionInverse` uniform declarations:

```java
// Add DH-specific projection matrix uniforms for shader pack compatibility
Iris.logger.info("[DH-SHADER-TRANSFORM] Injecting dhProjection and dhProjectionInverse uniforms for shader: " + parameters.type);
tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
    "uniform mat4 dhProjection;");

tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
    "uniform mat4 dhProjectionInverse;");
```

### 2. LOD Render Program (IrisLodRenderProgram.java)

- **Added uniform location fields**:
```java
// DH-specific projection uniforms
public final int dhProjectionUniform;
public final int dhProjectionInverseUniform;
```

- **Added uniform location lookups**:
```java
// DH-specific projection uniforms
dhProjectionUniform = tryGetUniformLocation2("dhProjection");
dhProjectionInverseUniform = tryGetUniformLocation2("dhProjectionInverse");
```

- **Added uniform value setting**:
```java
// Set DH-specific projection uniforms (these are the same as the iris ones)
setUniform(dhProjectionUniform, projection);
setUniform(dhProjectionInverseUniform, projection.invert(new Matrix4f()));
```

### 3. Generic Render Program (IrisGenericRenderProgram.java)

Applied the same changes as IrisLodRenderProgram for consistency and to support both rendering paths.

### 4. Debug Logging

Added comprehensive logging to help diagnose issues:

- Shader transformation logging to confirm uniforms are being injected
- Uniform location logging to verify they're being found in compiled shaders
- Uniform value logging (if needed) to verify they're being set correctly

## Expected Behavior After Fix

1. **Shader Compilation**: DH terrain shaders will compile successfully because `dhProjection` and `dhProjectionInverse` uniforms are now declared.

2. **Uniform Values**: These uniforms will be populated with the same values as `iris_ProjectionMatrix` and `iris_ProjectionMatrixInverse`, ensuring correct rendering.

3. **Logging Output**: You should see log messages like:
```
[DH-SHADER-TRANSFORM] Injecting dhProjection and dhProjectionInverse uniforms for shader: VERTEX
[DH-SHADER-TRANSFORM] Injecting dhProjection and dhProjectionInverse uniforms for shader: FRAGMENT
[DH-SHADER-UNIFORMS] Program: dh_terrain
[DH-SHADER-UNIFORMS] dhProjection uniform location: <non-negative number>
[DH-SHADER-UNIFORMS] dhProjectionInverse uniform location: <non-negative number>
```

## Testing Instructions

1. **Build the project**:
```bash
./gradlew build
```

2. **Run the client**:
```bash
./gradlew runClient
```

3. **Enable shaders**:
   - Press `O` (or your configured shader menu key)
   - Select a shader pack (e.g., Complementary)
   - Apply the shader

4. **Verify the fix**:
   - Shaders should load without errors
   - Check logs for `[DH-SHADER-TRANSFORM]` and `[DH-SHADER-UNIFORMS]` messages
   - No shader compilation errors should appear
   - Distant Horizons LODs should render with shaders enabled

## Files Modified

1. `modules/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/pipeline/transform/transformer/DHTerrainTransformer.java`
   - Added dhProjection/dhProjectionInverse uniform declarations
   - Added logging for debugging

2. `modules/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/pipeline/transform/transformer/DHGenericTransformer.java`
   - Added dhProjection/dhProjectionInverse uniform declarations
   - Added logging for debugging

3. `modules/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/compat/dh/IrisLodRenderProgram.java`
   - Added dhProjectionUniform and dhProjectionInverseUniform fields
   - Added uniform location lookups
   - Added uniform value setting in fillUniformData()
   - Added logging for debugging

4. `modules/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java`
   - Added dhProjectionUniform and dhProjectionInverseUniform fields
   - Added uniform location lookups
   - Added uniform value setting in bind()
   - Added logging for debugging

## Build Verification

The changes have been successfully compiled and verified:
- ✅ Iris module compiled without errors
- ✅ dhProjectionUniform and dhProjectionInverseUniform fields present in compiled classes
- ✅ Uniform declarations injected in transformer
- ✅ Logging statements present in compiled code

## Additional Notes

- The fix is minimal and surgical, only adding what's necessary to support DH-specific uniforms
- The implementation follows the existing patterns in Iris for uniform handling
- Logging can be disabled by removing the Iris.logger statements if desired
- The fix applies to both DH terrain and DH generic rendering paths
- No changes to the shader pack itself are required
