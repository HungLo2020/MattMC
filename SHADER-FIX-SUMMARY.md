# Distant Horizons Shader Compilation Fix - Summary

## Problem Description

When enabling shaders with Distant Horizons, the game would fail to load shaders with errors about undefined DH-specific uniforms:

**Initial error:**
```
error C1503: undefined variable "dhProjection"
error C1503: undefined variable "dhProjectionInverse"
```

**After initial fix:**
```
error C1503: undefined variable "dhDepthTex1"
```

## Root Cause Analysis

The issue occurred because:

1. **Shader Pack Expectations**: The Complementary shader pack (and likely other DH-compatible shaders) expects several DH-specific uniforms to be available. These are defined in the shader pack's `lib/uniforms.glsl`:

```glsl
#ifdef DISTANT_HORIZONS
    uniform int dhRenderDistance;
    
    uniform mat4 dhProjection;
    uniform mat4 dhProjectionInverse;
    
    uniform sampler2D dhDepthTex;
    uniform sampler2D dhDepthTex1;
#endif
```

2. **Iris Implementation**: Iris was only injecting `iris_ProjectionMatrix` and `iris_ProjectionMatrixInverse` uniforms during shader transformation, but not the DH-specific uniforms (`dhProjection`, `dhProjectionInverse`, `dhDepthTex`, `dhDepthTex1`, `dhRenderDistance`) that shader packs expect.

3. **Compilation from Source**: This issue manifested specifically in this project because all mods (including Iris) are compiled completely from source, which differs from using pre-compiled mod jars where this issue might have been already addressed or worked around.

## Solution Implemented

The fix involved adding support for all DH-specific uniforms at multiple levels:

### 1. Shader Transformation Layer (DHTerrainTransformer.java & DHGenericTransformer.java)

Added injection of all DH-specific uniform declarations:

```java
// Add DH-specific uniforms for shader pack compatibility
Iris.logger.info("[DH-SHADER-TRANSFORM] Injecting DH-specific uniforms for shader: " + parameters.type);
tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
    "uniform mat4 dhProjection;");

tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
    "uniform mat4 dhProjectionInverse;");

tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
    "uniform sampler2D dhDepthTex;");

tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
    "uniform sampler2D dhDepthTex1;");

tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
    "uniform int dhRenderDistance;");
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

### 4. Sampler and Uniform Values

**Note**: The depth texture samplers (`dhDepthTex`, `dhDepthTex1`) and render distance uniform (`dhRenderDistance`) are already being bound/set by Iris:
- `dhDepthTex` and `dhDepthTex1` are bound in `IrisSamplers.addRenderTargetSamplers()` 
- `dhRenderDistance` is set in `CommonUniforms.java`

The issue was only that shader transformers weren't injecting the uniform declarations, causing compilation failures.

### 5. Debug Logging

Added comprehensive logging to help diagnose issues:

- Shader transformation logging to confirm uniforms are being injected
- Uniform location logging to verify they're being found in compiled shaders
- Uniform value logging (if needed) to verify they're being set correctly

## Expected Behavior After Fix

1. **Shader Compilation**: DH terrain and water shaders will compile successfully because all required DH-specific uniforms are now declared:
   - `dhProjection` and `dhProjectionInverse` (matrices)
   - `dhDepthTex` and `dhDepthTex1` (samplers)
   - `dhRenderDistance` (integer)

2. **Uniform Values**: 
   - Projection uniforms will be populated with the same values as `iris_ProjectionMatrix` and `iris_ProjectionMatrixInverse`
   - Depth texture samplers will be bound to the appropriate DH depth buffers
   - Render distance will be set to the DH chunk render distance

3. **Logging Output**: You should see log messages like:
```
[DH-SHADER-TRANSFORM] Injecting DH-specific uniforms for shader: VERTEX
[DH-SHADER-TRANSFORM] Injecting DH-specific uniforms for shader: FRAGMENT
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
   - Added all DH-specific uniform declarations: `dhProjection`, `dhProjectionInverse`, `dhDepthTex`, `dhDepthTex1`, `dhRenderDistance`
   - Added logging for debugging

2. `modules/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/pipeline/transform/transformer/DHGenericTransformer.java`
   - Added all DH-specific uniform declarations: `dhProjection`, `dhProjectionInverse`, `dhDepthTex`, `dhDepthTex1`, `dhRenderDistance`
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
   - Fixed pre-existing bug: projectionInverseUniform now uses dhProjectionMatrix instead of dhModelViewMatrix
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
