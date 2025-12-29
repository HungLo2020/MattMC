# Hook-Based System Analysis for MattMC Single-JAR Architecture

## Migration Progress Tracker

**Last Updated:** December 29, 2024

### Mixin Conversion Status

**Total Mixins:** 235 → **229 remaining** (6 removed)  
**Conversion Progress:** 2.6% complete

#### Removed Mixins (6):
1. ✅ `net.irisshaders.iris.mixin.DimensionTypeAccessor` - Record fields are public by default
2. ✅ `net.irisshaders.iris.mixin.LightTextureAccessor` - Changed `LightTexture.texture` field visibility to public
3. ✅ `com.seibel.distanthorizons.fabric.mixins.client.LightTextureAccessor` - Same as above
4. ✅ `net.irisshaders.iris.mixin.statelisteners.BooleanStateAccessor` - `GlStateManager.BooleanState.enabled` field already public
5. ✅ `net.irisshaders.iris.mixin.EndFlashAccess` - Added public setter methods to `EndFlashState`
6. ✅ `net.irisshaders.iris.mixin.GlStateManagerAccessor` - Made static fields `BLEND`, `DEPTH`, `COLOR_MASK`, `TEXTURES`, `activeTexture` public

#### Modified Files (19):
1. `net.minecraft.client.renderer.LightTexture` - Made `texture` field public
2. `net.irisshaders.iris.pipeline.CustomTextureManager` - Updated to use direct field access (2x)
3. `com.seibel.distanthorizons.fabric.hooks.DhLightTextureHook` - Updated to use direct field access
4. `net.irisshaders.iris.gl.blending.BlendModeStorage` - Updated to use direct field access (2x)
5. `net.irisshaders.iris.uniforms.CommonUniforms` - Updated to use direct field access
6. `net.minecraft.client.renderer.EndFlashState` - Added public setter methods
7. `net.irisshaders.iris.shadows.ShadowMatrices` - Removed unused import
8. `com.mojang.blaze3d.opengl.GlStateManager` - Made 5 static fields public
9. `net.irisshaders.iris.gl.program.ProgramSamplers` - Direct field access
10. `net.irisshaders.iris.gl.blending.DepthColorStorage` - Direct field access (2x)
11. `net.irisshaders.iris.gl.IrisRenderSystem` - Direct field access (8x)
12. `net.irisshaders.iris.gl.texture.DepthCopyStrategy` - Direct field access
13. `net.irisshaders.iris.pbr.TextureInfoCache` - Direct field access
14. `net.irisshaders.iris.pbr.texture.PBRTextureManager` - Direct field access
15. `net.irisshaders.iris.pipeline.CompositeRenderer` - Direct field access

---

## Executive Summary

After thorough analysis of the NEXT-STEPS.md document and the current state of the MattMC project, **I strongly recommend transitioning from mixins to a hook-based system** for integrating mods with Minecraft core. The single source set architecture creates a unique opportunity to eliminate the complexity and overhead of runtime bytecode manipulation.

## Current Architecture Overview

### Single Source Set Integration (Completed)
- ✅ All code consolidated into `src/main/java/`
- ✅ Fabric Loader, Minecraft, Sodium, Iris, and Distant Horizons compile together
- ✅ Single JAR build (380MB) with all components
- ✅ No circular dependencies between modules
- ✅ All 284 mixins currently functional

### Existing Hook System (32 Hooks Implemented)
The project has already begun the transition to a hook-based architecture:

**Hook Registry (`net.minecraft.hooks.HookRegistry`)**
- Central registration system for all hook implementations
- 32 hook interfaces already defined and integrated into Minecraft core
- Mods register hook implementations at initialization time

**Current Hook Categories:**
1. `GameHooks` - Game lifecycle events (init, tick, resource reload)
2. `RenderHooks` - Rendering system events
3. `GraphicsConfigHooks` - Graphics configuration
4. `GuiRenderHooks` - GUI and HUD rendering
5. `DebugScreenHooks` - Debug screen integration
6. `BlockRenderHooks` - Block rendering customization
7. `EntityRenderHooks` - Entity rendering
8. `FogRenderHooks` - Fog rendering
9. `SkyColorHooks` - Sky color modification
10. And 22 additional specialized hooks...

**Hook Call Sites:**
Hooks are already being called from Minecraft core code:
- `Minecraft.java` - Game initialization, tick events, resource reloading
- `LevelRenderer.java` - Rendering hooks
- `ClientLevel.java` - World/level hooks
- `AtlasManager.java` - Texture atlas hooks
- And many more...

## Current Mixin Statistics

### Total Mixin Count: 229 Files (6 removed)

**Breakdown by Type:**
- **@Accessor mixins**: 53 remaining (59 originally, 6 removed)
- **@Invoker mixins**: 1 (<1% of total)
- **@Inject annotations**: 254 (multiple per file)
- **@Redirect annotations**: 46
- **@Overwrite annotations**: 23
- **@ModifyArg/@ModifyVariable**: 15

**Distribution by Mod:**
- **Sodium**: ~50 mixins (rendering optimizations)
- **Iris**: ~144 mixins (shader system integration, 5 removed)
- **Distant Horizons**: ~23 mixins (LOD rendering, 1 removed)
- **Fabric API**: ~12 mixins (compatibility layer)

### Mixin Complexity Analysis

**Simple (Easy to Convert):**
- 53 @Accessor mixins remaining - Just need to change visibility modifiers
- 1 @Invoker mixin - Make method public/protected
- ~100 simple @Inject mixins - Direct HEAD/RETURN injections

**Moderate (Requires Refactoring):**
- ~80 complex @Inject mixins - Custom injection points
- 15 @ModifyArg/@ModifyVariable - Need method extraction
- 46 @Redirect mixins - Require method refactoring

**Complex (Careful Analysis Required):**
- 23 @Overwrite mixins - Completely replace methods

## Why Hook-Based System Makes Sense

### 1. **Shared Source Set Changes the Game**

Traditional modding requires mixins because:
- Mods cannot modify Minecraft source code directly
- Different compilation units prevent direct method calls
- No compile-time visibility into Minecraft internals

**But in MattMC's single source set:**
- ✅ All code compiles together
- ✅ Mods and Minecraft can depend on each other
- ✅ Direct method calls are possible
- ✅ Full compile-time type safety

### 2. **Performance Benefits**

**Mixin Overhead:**
- Runtime bytecode manipulation
- Method redirection through generated code
- Reflection-based access
- Refmap generation and obfuscation mapping

**Hook-Based Approach:**
- Direct method calls (JIT optimizable)
- No runtime bytecode generation
- Compile-time type checking
- Inlining opportunities

**Expected Performance Improvement:** 5-15% runtime improvement (as noted in NEXT-STEPS.md)

### 3. **Development Experience**

**Mixins:**
- ❌ String-based targeting (fragile)
- ❌ Runtime errors only
- ❌ Limited IDE support (autocomplete, refactoring)
- ❌ Difficult debugging (stack traces show mixin classes)
- ❌ Refmap generation complexity
- ❌ Obfuscation mapping issues

**Hooks:**
- ✅ Compile-time type safety
- ✅ Full IDE autocomplete and refactoring
- ✅ Clear stack traces showing actual call sites
- ✅ Easy debugging and navigation
- ✅ No mapping or refmap needed
- ✅ Standard Java interfaces

### 4. **Maintainability**

**Current Mixin Challenges:**
- 13 mixin configuration files to maintain
- 284 mixins spread across codebase
- Version-specific bytecode targeting
- Difficult to track dependencies between mixins
- Mixin plugin complexity

**Hook-Based Benefits:**
- Central hook registry
- Clear interface contracts
- Easy to see all implementations
- Versioning through interface evolution
- No configuration files needed

### 5. **Already Partially Implemented**

The project has already started this transition:
- 32 hook interfaces defined
- Hook registry implemented
- Hooks integrated into Minecraft core
- Mods (Sodium) already using hooks alongside mixins

**This proves the concept works!**

## Migration Strategy Validation

The NEXT-STEPS.md Phase 3 migration strategy is **sound and achievable**:

### Phase 3a: Accessor/Invoker Mixins (2-3 weeks)
**59 @Accessor + 1 @Invoker = 60 mixins**

**Approach: HIGHLY FEASIBLE**
- Change field/method visibility from `private` to `public` or package-private
- Update mod code to use direct access
- Remove mixin files

**Example:**
```java
// BEFORE: Mixin
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("entityRenderDispatcher")
    EntityRenderDispatcher getEntityRenderDispatcher();
}

// AFTER: Direct access (in LevelRenderer.java)
public class LevelRenderer {
    // Changed from: private EntityRenderDispatcher entityRenderDispatcher;
    public EntityRenderDispatcher entityRenderDispatcher; // Now public
    
    // Or add getter if encapsulation preferred:
    public EntityRenderDispatcher getEntityRenderDispatcher() {
        return entityRenderDispatcher;
    }
}

// Mod code changes from:
((LevelRendererAccessor) levelRenderer).getEntityRenderDispatcher()
// To:
levelRenderer.getEntityRenderDispatcher()
```

**Risk: LOW** - Straightforward visibility changes

### Phase 3b: Simple @Inject Mixins (2-3 weeks)
**~100 simple injection mixins**

**Approach: FEASIBLE with existing hook system**

Many simple injections can use existing hooks:
```java
// BEFORE: Mixin
@Mixin(Minecraft.class)
class MinecraftMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void onRunTickStart(boolean tick, CallbackInfo ci) {
        SodiumMod.beforeTick();
    }
}

// AFTER: Hook implementation
public class SodiumGameHook implements GameHooks {
    @Override
    public void beforeRunTick(Minecraft minecraft, boolean tick) {
        SodiumMod.beforeTick();
    }
}

// In Minecraft.java (already exists):
public void runTick(boolean tick) {
    for (GameHooks hook : HookRegistry.getGameHooks()) {
        hook.beforeRunTick(this, tick);
    }
    // existing code...
}
```

**Risk: LOW** - Pattern already established and working

### Phase 3c: Complex Mixins (2-4 weeks)
**46 @Redirect + 15 @ModifyArg/Variable + 80 complex @Inject = 141 mixins**

**Approach: REQUIRES REFACTORING but ACHIEVABLE**

These mixins will require extracting methods or adding new hook points:

```java
// BEFORE: @Redirect mixin
@Redirect(method = "renderChunkLayer", 
          at = @At(value = "INVOKE", 
                   target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;uploadChunkLayer"))
private void redirectUpload(ChunkRenderDispatcher dispatcher, ...) {
    SodiumChunkRenderer.customUpload(dispatcher, ...);
}

// AFTER: Extract method with hook
public class LevelRenderer {
    public void renderChunkLayer(...) {
        // ... code ...
        
        // Extract upload logic to separate method
        uploadChunkLayerWithHook(dispatcher, ...);
    }
    
    protected void uploadChunkLayerWithHook(ChunkRenderDispatcher dispatcher, ...) {
        // Check for hook implementations
        for (ChunkRenderHooks hook : HookRegistry.getChunkRenderHooks()) {
            if (hook.overrideChunkUpload(dispatcher, ...)) {
                return; // Hook handled it
            }
        }
        
        // Default vanilla behavior
        dispatcher.uploadChunkLayer(...);
    }
}
```

**Risk: MODERATE** - Requires careful refactoring to preserve behavior

### Phase 3d: @Overwrite Mixins (1-2 weeks)
**23 overwrite mixins**

**Approach: NEEDS CAREFUL ANALYSIS**

@Overwrite mixins completely replace methods, so they need special attention:

**Option 1: Convert to Hook with Full Override**
```java
public interface RenderMethodHooks {
    /**
     * @return true if hook handled rendering, false to use vanilla
     */
    boolean overrideRenderMethod(...);
}
```

**Option 2: Refactor Original Method to Use Composition**
```java
// Instead of replacing entire method, extract logic into parts
public void complexRenderMethod() {
    // Part 1: Setup (hookable)
    if (!setupRenderWithHook()) return;
    
    // Part 2: Main logic (hookable)
    performMainRenderWithHook();
    
    // Part 3: Cleanup (hookable)
    cleanupRenderWithHook();
}
```

**Risk: HIGH** - Overwrites are most complex, need case-by-case analysis

## Comprehensive Cost-Benefit Analysis

### Benefits of Migration

1. **Performance** (+++)
   - 5-15% FPS improvement from direct calls
   - Reduced memory overhead (no mixin metadata)
   - Better JIT optimization opportunities

2. **Type Safety** (+++)
   - Compile-time error detection
   - No runtime "target not found" errors
   - Full IDE support and refactoring

3. **Debugging** (++)
   - Clear stack traces
   - Easy breakpoint placement
   - Standard Java debugging flow

4. **Maintainability** (+++)
   - No refmap generation
   - No obfuscation mapping
   - Clear interface contracts
   - Easier to track changes

5. **Build Speed** (+)
   - No mixin annotation processing
   - Simpler build pipeline
   - Faster incremental compilation

6. **Code Quality** (++)
   - Forces cleaner architecture
   - Explicit hook points
   - Better separation of concerns

### Costs of Migration

1. **Time Investment** (---)
   - 8-12 weeks estimated (NEXT-STEPS.md)
   - Need to convert 235 mixins
   - Testing and validation required

2. **Risk** (--)
   - Potential behavior changes
   - Need comprehensive testing
   - Rollback complexity

3. **Visibility Changes** (-)
   - Making internal fields/methods public
   - Potential for misuse by future code
   - Loss of some encapsulation

4. **Hook Proliferation** (-)
   - May create many specialized hooks
   - Need to maintain hook interfaces
   - Risk of over-engineering

### Mitigations for Costs

**Time Investment:**
- Incremental approach (convert in phases)
- Start with high-value, low-risk conversions
- Maintain working state throughout

**Risk:**
- Comprehensive testing at each phase
- Keep mixin code commented until validated
- Create rollback branches at milestones

**Visibility Changes:**
- Use package-private where possible
- Add `@Internal` or `@ApiStatus.Internal` annotations
- Document that public APIs are for mod integration only

**Hook Proliferation:**
- Group related hooks into cohesive interfaces
- Use default methods to avoid forced implementations
- Regularly review and consolidate hooks

## Recommendation: YES, Migrate to Hooks

### Why Now is the Right Time

1. **Architecture Supports It**: Single source set enables direct integration
2. **Foundation Exists**: 32 hooks already implemented and working
3. **Proven Concept**: Sodium already using hooks successfully
4. **Clean Slate**: Better to migrate now than after more mixins are added
5. **Performance Matters**: 5-15% FPS improvement significant for rendering-heavy mods

### Why Hooks are Better than Mixins in Single Source Set

**The fundamental question:**
> "Why use runtime bytecode manipulation when you can just call a method?"

In a single source set:
- Mods can directly depend on Minecraft code
- Minecraft can directly depend on mod interfaces
- All code compiles together with full type safety

**Mixins were invented for a problem that no longer exists in this architecture.**

### Recommended Approach

Follow the NEXT-STEPS.md Phase 3 strategy with minor adjustments:

**Month 1-2: Foundation**
- Complete mixin inventory and categorization
- Expand hook system with critical missing hooks
- Convert all 60 Accessor/Invoker mixins
- Establish testing and validation procedures

**Month 2-3: Simple Injections**
- Convert 100 simple @Inject mixins
- Use existing hooks where applicable
- Create new hooks as needed (group into cohesive interfaces)
- Continuous testing and benchmarking

**Month 3-4: Complex Mixins**
- Convert @Redirect mixins through method extraction
- Convert @ModifyArg/@ModifyVariable through refactoring
- More complex @Inject mixins requiring new hook points
- Performance testing and optimization

**Month 4-5: Overwrites and Edge Cases**
- Carefully analyze 23 @Overwrite mixins
- Refactor methods to use composition where possible
- Handle edge cases and complex scenarios
- Final testing and validation

**Month 5-6: Cleanup and Optimization**
- Remove mixin library dependency
- Remove mixin configuration files
- Clean up refmap generation from build
- Performance optimization and benchmarking
- Documentation updates

### Success Criteria

Track progress with these metrics (from NEXT-STEPS.md):

- **Mixins Remaining**: 235 → 0
- **Build Time**: Current → 50% reduction
- **JAR Size**: 380MB → <350MB (after removing mixin library)
- **FPS Improvement**: Baseline → 10-15% improvement
- **Code Coverage**: Establish test coverage for converted areas

## Potential Challenges and Solutions

### Challenge 1: Complex Injection Points

**Problem**: Some mixins inject at very specific bytecode positions
```java
@Inject(method = "render", at = @At(value = "INVOKE", 
        target = "specificMethod", shift = At.Shift.AFTER))
```

**Solution**:
- Extract the code around injection point into separate method
- Add hook before/after that method
- Or inline the logic if it's small

### Challenge 2: Mixin Locals

**Problem**: Mixins can capture local variables
```java
@Inject(method = "render", at = @At("INVOKE"), locals = LocalCapture.CAPTURE_FAILHARD)
private void captureLocal(CallbackInfo ci, int x, int y, float partialTicks) {
    // Use captured locals
}
```

**Solution**:
- Refactor method to extract locals as parameters
- Pass to hook method
- Or use instance fields if appropriate

### Challenge 3: Conditional Mixins

**Problem**: Some mixins only apply under certain conditions
```java
@Mixin(value = SomeClass.class, 
       remap = false)
public class ConditionalMixin {
    // Only applies if certain mod is present
}
```

**Solution**:
- Use conditional hook registration
- Check conditions at initialization time
- Empty implementations for disabled features

### Challenge 4: Cross-Mod Interactions

**Problem**: Multiple mods might mix into the same method
```java
// Sodium mixin
@Inject(method = "render", at = @At("HEAD"))

// Iris mixin  
@Inject(method = "render", at = @At("HEAD"))
```

**Solution**:
- Hook system naturally supports multiple implementations
- HookRegistry maintains list of all hooks
- Iteration order is predictable (registration order)

## Alternative: Hybrid Approach

If full migration seems too risky, consider a **hybrid approach**:

### Keep Mixins For:
- External libraries that can't be modified
- Very complex overwrites that are hard to refactor
- Temporary compatibility during migration

### Use Hooks For:
- All new integration points
- Simple accessor/invoker patterns
- Common injection points (HEAD, RETURN, TAIL)
- High-frequency call sites (performance critical)

### Gradual Migration:
- Convert mixins to hooks incrementally
- Maintain both systems temporarily
- Eventually eliminate all mixins

**However, I believe full migration is better:**
- Cleaner architecture
- No dual-system maintenance
- Full performance benefits
- Simpler build configuration

## Conclusion

**The hook-based system makes perfect sense for MattMC's single source set architecture.**

### Key Insights:

1. **Mixins solve a problem you don't have**: Runtime bytecode manipulation is necessary when mods and Minecraft are separate compilation units. In a single source set, this complexity is unnecessary.

2. **Hooks are simpler and faster**: Direct method calls are faster, easier to debug, and provide better type safety than runtime bytecode manipulation.

3. **Foundation already exists**: 32 hooks already implemented and working proves the concept is sound.

4. **Migration is achievable**: 8-12 week timeline for 235 mixins is reasonable with the phased approach.

5. **Benefits outweigh costs**: Performance gains (5-15%), better debugging, and improved maintainability justify the migration effort.

### Final Recommendation:

**Proceed with the hook-based migration** as outlined in NEXT-STEPS.md Phase 3. The single source set architecture creates a unique opportunity to eliminate mixin complexity and achieve better performance and maintainability.

Start with the low-hanging fruit (Accessor/Invoker mixins) to build momentum and validate the approach, then systematically work through increasingly complex cases.

The end result will be a **faster, cleaner, and more maintainable codebase** that fully leverages the single-JAR architecture.

---

## Next Steps

1. **Accept this recommendation** and commit to the migration
2. **Complete mixin inventory** (NEXT-STEPS.md Step 1)
3. **Start with Accessor/Invoker conversion** (easy wins)
4. **Establish testing framework** for validation
5. **Track metrics** to measure success
6. **Document patterns** as you convert for consistency

Good luck with the migration! The architecture is well-positioned for this transition.
