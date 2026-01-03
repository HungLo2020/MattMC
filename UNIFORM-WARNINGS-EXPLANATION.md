# Explanation: "Found unknown and unsupported uniform" Warnings

## Summary
You are seeing **285 warnings** about "unknown and unsupported uniforms" because of a mismatch between how **Iris Shaders** (specifically the Iris-prefixed uniforms) and **vanilla Minecraft shaders** are being handled in the shader program initialization code.

## The Warnings

From your ERROR-LOG.txt, the warnings appear as:
```
[14:45:34] [Render thread/WARN]: Found unknown and unsupported uniform iris_DynamicTransforms in basic
[14:45:34] [Render thread/WARN]: Found unknown and unsupported uniform iris_Fog in basic
[14:45:34] [Render thread/WARN]: Found unknown and unsupported uniform iris_Globals in basic
[14:45:34] [Render thread/WARN]: Found unknown and unsupported uniform iris_Projection in basic
```

### Warning Breakdown:
- **Total warnings:** 285
- **Affected programs:** 71 shader programs (both regular and shadow variants)
- **Specific uniforms reported:**
  - `iris_DynamicTransforms` - 71 occurrences
  - `iris_Fog` - 71 occurrences  
  - `iris_Globals` - 71 occurrences
  - `iris_Projection` - 71 occurrences
  - `iris_CloudInfo` - 1 occurrence

## Root Cause Analysis

### Where the Warning is Generated

The warning comes from `GlProgram.java` at line 150:

```java
for (int p = 0; p < o; p++) {
    String string = GL31.glGetActiveUniformBlockName(this.programId, p);
    if (!this.uniformsByName.containsKey(string)) {
        if (!list2.contains(string) && BUILT_IN_UNIFORMS.contains(string)) {
            int n = i++;
            GL31.glUniformBlockBinding(this.programId, p, n);
            this.uniformsByName.put(string, new Uniform.Ubo(n));
        } else {
            LOGGER.warn("Found unknown and unsupported uniform {} in {}", string, this.debugLabel);
        }
    }
}
```

### The Mechanism

1. **Shader Compilation:** When shaders are compiled and linked, OpenGL reports all uniform blocks that exist in the compiled program.

2. **Uniform Discovery Loop:** The code iterates through all active uniform blocks in the compiled shader program (line 142-153).

3. **Name Checking:** For each uniform, it checks:
   - Is it already registered in `uniformsByName`? (line 144)
   - Is it in the sampler list (`list2`)? (line 145)
   - Is it in the `BUILT_IN_UNIFORMS` set? (line 145)

4. **The Problem:** The `BUILT_IN_UNIFORMS` set only contains vanilla Minecraft uniform names:
   ```java
   public static Set<String> BUILT_IN_UNIFORMS = Sets.<String>newHashSet("Projection", "Lighting", "Fog", "Globals");
   ```

5. **Iris Uniforms:** When using the Iris shader mod with your "ComplementaryHungLoIfied.zip" shaderpack, the shaders reference Iris-specific uniform blocks that are **prefixed with `iris_`**:
   - `iris_Projection`
   - `iris_Fog`
   - `iris_Globals`
   - `iris_DynamicTransforms`
   - `iris_CloudInfo`

### Why This Happens

The issue occurs because of **two different shader systems coexisting**:

1. **Iris-aware shaders** (`ExtendedShader` and `FallbackShader`):
   - These properly register Iris uniforms by implementing the `IrisProgram` interface
   - They override `iris$getBlockIndex()` to add the "iris_" prefix
   - Lines 104-109 in `ExtendedShader.java` show they register these uniforms:
     ```java
     uniformList.add(new RenderPipeline.UniformDescription("DynamicTransforms", UniformType.UNIFORM_BUFFER));
     uniformList.add(new RenderPipeline.UniformDescription("CloudInfo", UniformType.UNIFORM_BUFFER));
     uniformList.add(new RenderPipeline.UniformDescription("Projection", UniformType.UNIFORM_BUFFER));
     uniformList.add(new RenderPipeline.UniformDescription("Fog", UniformType.UNIFORM_BUFFER));
     uniformList.add(new RenderPipeline.UniformDescription("Globals", UniformType.UNIFORM_BUFFER));
     ```

2. **Vanilla/compatibility shaders** (regular `GlProgram` instances):
   - These are vanilla Minecraft shader programs (like "basic", "textured", "terrain_solid", etc.)
   - When Iris shaderpacks are active, these vanilla shaders get the Iris uniform blocks injected into them by the shader preprocessor/patcher
   - However, these vanilla shader instances don't know about the `iris_` prefixed uniforms
   - The uniforms aren't in their registration list, so they trigger the warning

### The Actual Flow

1. Your shaderpack "ComplementaryHungLoIfied.zip" is loaded (line 130 of ERROR-LOG.txt)
2. Iris patches/injects its uniform blocks into shader code during compilation
3. When vanilla Minecraft shader programs (basic, textured, terrain_solid, etc.) are compiled:
   - The GLSL compiler sees the injected `iris_*` uniform blocks
   - They get registered in the OpenGL program
4. When `GlProgram.setupUniforms()` runs for these vanilla programs:
   - It finds these `iris_*` uniform blocks via `glGetActiveUniformBlockName()`
   - They're not in `BUILT_IN_UNIFORMS` (which only has non-prefixed names)
   - They're not in the explicit registration lists for vanilla shaders
   - **Warning is triggered**

### Why There's a Silencing Check

Notice in the code at lines 111-113 and 131-133:
```java
if (!isKnownShader()) {
    LOGGER.warn(...);
}
```

The `isKnownShader()` method returns true for `ExtendedShader` and `FallbackShader` instances - these are Iris-aware shaders that properly handle the iris_ uniforms, so warnings are suppressed for them.

However, vanilla shader programs (basic, textured, etc.) are **not** instances of these Iris-aware classes, so they get the warning.

## Why You See So Many

You see 285 warnings because:
1. **71 different shader programs** are being used (vanilla Minecraft has many shader programs for different rendering passes)
2. Each program finds **4 main Iris uniform blocks** it doesn't recognize:
   - `iris_DynamicTransforms`
   - `iris_Fog`
   - `iris_Globals`
   - `iris_Projection`
3. Math: 71 programs × 4 uniforms = 284 warnings
4. Plus 1 for `iris_CloudInfo` in the "clouds" shader = **285 total warnings**

## Impact

**These warnings are mostly harmless:**
- The uniforms ARE being used by the shaders (when Iris is active)
- Iris handles them separately through its own pipeline
- The warnings are just noise from the vanilla shader registration code not recognizing them
- The game functions properly despite the warnings

## The Design Trade-off

This appears to be an architectural decision where:
- Iris injects its uniforms into vanilla shaders for compatibility
- But vanilla shader programs don't explicitly register them
- Rather than modifying every vanilla shader class, the warnings just indicate "we found something unexpected"
- For actual Iris shaders (`ExtendedShader`/`FallbackShader`), the warnings are suppressed

## Potential Solutions (Not Implemented)

To eliminate these warnings, you would need to either:

1. **Add iris_* uniforms to BUILT_IN_UNIFORMS:**
   ```java
   public static Set<String> BUILT_IN_UNIFORMS = Sets.<String>newHashSet(
       "Projection", "Lighting", "Fog", "Globals",
       "iris_Projection", "iris_Fog", "iris_Globals", "iris_DynamicTransforms", "iris_CloudInfo"
   );
   ```

2. **Make the warning check smarter** to detect Iris prefixes:
   ```java
   if (!list2.contains(string) && BUILT_IN_UNIFORMS.contains(string)) {
       // register it
   } else if (string.startsWith("iris_")) {
       // silently ignore Iris uniforms when Iris is active
   } else {
       LOGGER.warn("Found unknown and unsupported uniform {} in {}", string, this.debugLabel);
   }
   ```

3. **Check if Iris shaderpack is active** before warning about iris_* uniforms

## Conclusion

The warnings are a **cosmetic issue** resulting from the integration between Iris Shaders and vanilla Minecraft's shader system. The Iris-prefixed uniform blocks are injected into vanilla shaders but not explicitly registered in the vanilla shader program's uniform list, causing the unknown uniform detection code to trigger warnings. The actual rendering works fine because Iris manages these uniforms through its own separate pipeline.
