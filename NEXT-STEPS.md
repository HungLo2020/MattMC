# Next Steps for MattMC Single-JAR Integration

## Overview

Now that all mods (Sodium, Iris, Distant Horizons) and Fabric Loader are successfully integrated into a single JAR with Minecraft, this document outlines the next steps to further optimize, clean up, and improve the codebase.

## Current State

✅ **Completed:**
- All source code consolidated into `src/main/java/`
- Single JAR build (380MB) with all components
- All 284 mixins functional
- Runtime verified - all mods load and initialize correctly
- Build system simplified from 1509 to 1145 lines

📊 **Current Statistics:**
- **Sodium**: ~50 mixins
- **Iris**: ~149 mixins  
- **Distant Horizons**: ~24 mixins
- **Hook System**: ~32 hook implementations in Minecraft core
- **Mixin Configs**: 13 configuration files

---

## Phase 3: Mixin to Direct Integration Migration

**Priority: HIGH**  
**Estimated Time: 8-12 weeks**  
**Impact: Major performance improvement, simplified maintenance**

### Goal
Replace mixin-based bytecode manipulation with direct code integration since all code is now in the same source set.

### Benefits
1. **Performance**: Direct method calls instead of bytecode injection (5-15% runtime improvement)
2. **Maintainability**: No refmap generation, no obfuscation mapping issues
3. **Type Safety**: Compile-time checking instead of runtime string-based targeting
4. **Debugging**: Stack traces show actual call sites, not mixin classes
5. **IDE Support**: Full autocomplete, refactoring, and navigation

### Migration Strategy

#### Step 1: Inventory and Categorization (1 week)
- [ ] Catalog all 284 mixins by type:
  - **Accessor mixins** (field/method access) - easiest to convert
  - **Invoker mixins** (private method calls) - easy to convert
  - **Injection mixins** (@Inject, @ModifyArg, etc.) - moderate complexity
  - **Redirect mixins** (@Redirect) - moderate to high complexity
  - **Overwrite mixins** - highest complexity, requires careful analysis
- [ ] Identify mixins that target the same class
- [ ] Group by mod (Sodium, Iris, DH) and functional area

#### Step 2: Expand Hook System (2-3 weeks)
- [ ] Analyze remaining mixin injection points
- [ ] Design hook interfaces for common patterns:
  - Rendering hooks (before/after render calls)
  - Event hooks (entity spawn, chunk load, etc.)
  - Data modification hooks (transform data before use)
- [ ] Add hook call sites to Minecraft code at strategic points
- [ ] Document hook system architecture in `HOOK-SYSTEM.md`

#### Step 3: Convert Simple Mixins First (2-3 weeks)
Start with low-risk, high-value conversions:

**Phase 3a: Accessor/Invoker Mixins**
- [ ] Convert `@Accessor` mixins to direct field access
- [ ] Convert `@Invoker` mixins to direct method calls
- [ ] Change field/method visibility from `private` to `public` or `package-private`
- [ ] Update mod code to use direct access
- [ ] Test thoroughly, then remove mixin

**Phase 3b: Simple @Inject Mixins**
- [ ] Convert `@Inject(at = @At("HEAD"))` to hook calls at method start
- [ ] Convert `@Inject(at = @At("RETURN"))` to hook calls before return
- [ ] Convert `@Inject(at = @At("TAIL"))` to hook calls at method end
- [ ] Verify behavior matches original mixin

**Example Conversion:**
```java
// BEFORE: Mixin
@Mixin(LevelRenderer.class)
class LevelRendererMixin {
    @Inject(method = "renderChunkLayer", at = @At("HEAD"))
    private void onRenderChunkLayer(RenderType renderType, ...) {
        SodiumHook.beforeRenderChunkLayer(renderType);
    }
}

// AFTER: Direct Integration
// In LevelRenderer.java:
public void renderChunkLayer(RenderType renderType, ...) {
    RenderHooks.beforeChunkLayer(renderType); // Direct call
    // existing code...
}
```

#### Step 4: Convert Complex Mixins (2-4 weeks)
- [ ] Convert `@ModifyArg` and `@ModifyVariable` mixins
  - Refactor to extract methods that can be overridden or hooked
- [ ] Convert `@Redirect` mixins
  - Extract redirect target to separate method
  - Replace with hook or direct override
- [ ] Convert `@Overwrite` mixins carefully
  - These completely replace methods - highest risk
  - Consider refactoring original code to use composition instead
  - Document why overwrite was necessary

#### Step 5: Testing and Validation (1-2 weeks)
- [ ] Create comprehensive test suite for each converted area
- [ ] Performance benchmarking: compare before/after metrics
- [ ] Visual regression testing (screenshots, shader output)
- [ ] Ensure parity with mixin behavior

#### Step 6: Cleanup (1 week)
- [ ] Remove unused mixin configuration files
- [ ] Remove mixin plugin classes
- [ ] Remove Mixin library dependency (once all mixins converted)
- [ ] Update `fabric.mod.json` to remove mixin declarations
- [ ] Clean up refmap generation from build

---

## Phase 4: Code Organization and Structure Optimization

**Priority: MEDIUM**  
**Estimated Time: 3-4 weeks**  
**Impact: Improved maintainability**

### Reorganize Package Structure
Currently all mods maintain their original package structure. Consider reorganizing:

- [ ] **Option A: Keep Separate Packages** (current state)
  - Pros: Clear ownership, easier to track mod-specific code
  - Cons: Some duplication, unclear integration points
  
- [ ] **Option B: Unified by Functionality**
  - Reorganize into `net.mattmc.rendering`, `net.mattmc.world`, etc.
  - Merge duplicate functionality from different mods
  - Pros: Better organization, easier to find related code
  - Cons: Major refactoring, harder to track original mod code

- [ ] **Recommendation**: Stay with Option A for now, gradually merge duplicates

### Deduplication Opportunities
- [ ] Identify and merge duplicate utility classes
- [ ] Consolidate configuration systems
- [ ] Merge overlapping rendering code (Sodium + Iris)
- [ ] Unify chunk management (DH + Sodium)

---

## Phase 5: Build System Further Simplification

**Priority: MEDIUM**  
**Estimated Time: 1-2 weeks**  
**Impact: Faster builds, simpler maintenance**

### Current Build Improvements Needed
- [ ] Remove unused tasks from modules
- [ ] Clean up dependency declarations (many may be duplicated)
- [ ] Optimize resource processing (currently using EXCLUDE strategy)
- [ ] Consider splitting test source sets for different mods
- [ ] Add parallel compilation flags for faster builds

### Gradle Optimization
- [ ] Enable Gradle build cache
- [ ] Use configuration cache where possible
- [ ] Profile build to find bottlenecks
- [ ] Consider using Gradle's composite builds for development

---

## Phase 6: Resource Consolidation and Optimization

**Priority: LOW-MEDIUM**  
**Estimated Time: 1-2 weeks**  
**Impact: Smaller JAR, faster resource loading**

### Resource Deduplication
- [ ] Audit all resources for duplicates across mods
- [ ] Merge duplicate textures, translations, shaders
- [ ] Consolidate language files (currently 3 separate mod translations)
- [ ] Optimize textures (compress, resize if oversized)

### Asset Pipeline
- [ ] Create unified asset directory structure
- [ ] Document asset organization in `ASSETS.md`
- [ ] Set up automated texture optimization in build
- [ ] Consider texture atlasing for mod-added textures

---

## Phase 7: Configuration System Unification

**Priority: MEDIUM**  
**Estimated Time: 2-3 weeks**  
**Impact: Better user experience**

### Current State
Each mod has its own configuration system:
- Sodium: Custom config with GUI
- Iris: Shader pack-based config
- Distant Horizons: Custom config system

### Unified Config Goals
- [ ] Design unified configuration API
- [ ] Create single configuration GUI that covers all mods
- [ ] Merge config files where logical
- [ ] Provide migration for existing user configs
- [ ] Document configuration system in `CONFIG.md`

---

## Phase 8: Documentation and Developer Experience

**Priority: MEDIUM-HIGH**  
**Estimated Time: 2-3 weeks**  
**Impact: Easier contribution, better onboarding**

### Documentation Needs
- [ ] Create `ARCHITECTURE.md` - overall system design
- [ ] Create `BUILDING.md` - how to build the project
- [ ] Create `CONTRIBUTING.md` - guidelines for contributions
- [ ] Create `HOOK-SYSTEM.md` - how the hook system works
- [ ] Update README.md with new architecture
- [ ] Document major classes and subsystems with Javadoc

### Developer Tools
- [ ] Set up consistent code formatting (EditorConfig/Checkstyle)
- [ ] Create IntelliJ/Eclipse project files
- [ ] Add useful run configurations for debugging
- [ ] Create scripts for common development tasks

---

## Phase 9: Performance Optimization

**Priority: HIGH (after mixin conversion)**  
**Estimated Time: 3-4 weeks**  
**Impact: Better runtime performance**

### Optimization Opportunities
- [ ] Profile runtime to find hotspots
- [ ] Optimize mod initialization (currently sequential)
- [ ] Consider lazy loading for rarely-used features
- [ ] Optimize resource loading (parallel where possible)
- [ ] Review and optimize hook call overhead

### Benchmarking
- [ ] Create performance test suite
- [ ] Establish baseline metrics (FPS, load time, memory)
- [ ] Track performance over time
- [ ] Document performance characteristics

---

## Phase 10: Testing Infrastructure

**Priority: HIGH**  
**Estimated Time: 3-4 weeks**  
**Impact: Stability, regression prevention**

### Test Coverage
- [ ] Add unit tests for core functionality
- [ ] Add integration tests for mod interactions
- [ ] Add regression tests for known issues
- [ ] Create visual regression test suite (screenshot comparison)
- [ ] Set up CI/CD for automated testing

### Test Organization
- [ ] Separate test source sets by mod
- [ ] Create test utilities and helpers
- [ ] Document testing guidelines

---

## Phase 11: Feature Integration

**Priority: LOW-MEDIUM**  
**Estimated Time: Ongoing**  
**Impact: Enhanced functionality**

### Cross-Mod Feature Opportunities
Since all mods are now integrated, new features become possible:

- [ ] **Sodium + Iris Integration**
  - Unified chunk rendering pipeline
  - Shared shader uniform system
  - Combined performance optimizations

- [ ] **Sodium + DH Integration**  
  - Seamless LOD transition
  - Shared chunk data structures
  - Unified culling system

- [ ] **Iris + DH Integration**
  - Shader support for LOD chunks
  - Unified shadow system
  - Custom shader effects on distant terrain

### New Capabilities
- [ ] Direct access between mods (no more reflection/mixins)
- [ ] Shared data structures (less memory overhead)
- [ ] Coordinated feature development
- [ ] Unified debugging and profiling

---

## Migration Priority Roadmap

### Immediate (Next 2-4 weeks)
1. **Mixin Inventory** - Complete categorization of all mixins
2. **Hook System Expansion** - Add critical hooks needed for conversions
3. **Simple Mixin Conversion** - Start with accessor/invoker mixins

### Short Term (1-3 months)
1. **Core Mixin Conversion** - Focus on high-value, frequently-used mixins
2. **Build Optimization** - Improve build speed and reliability
3. **Documentation** - Create essential architecture docs

### Medium Term (3-6 months)
1. **Complete Mixin Conversion** - Finish converting all mixins to direct integration
2. **Performance Optimization** - Profile and optimize based on real-world usage
3. **Testing Infrastructure** - Establish comprehensive test coverage

### Long Term (6-12 months)
1. **Feature Integration** - Develop cross-mod features
2. **Code Reorganization** - Refactor into more unified structure
3. **Community Release** - Prepare for public release with documentation

---

## Success Metrics

Track progress with these metrics:

- **Mixins Remaining**: 284 → 0 (target)
- **Build Time**: Current → 50% reduction (target)
- **JAR Size**: 380MB → <350MB (target, after deduplication)
- **FPS Improvement**: Baseline → 10-15% improvement (target, after mixin removal)
- **Code Coverage**: 0% → 60%+ (target)

---

## Risk Mitigation

### Risks During Migration
1. **Behavioral Changes** - Converted code doesn't match mixin behavior
   - Mitigation: Comprehensive testing, gradual rollout
   
2. **Performance Regression** - Direct integration slower than expected
   - Mitigation: Continuous benchmarking, profile-guided optimization
   
3. **Compatibility Issues** - Changes break mod interactions
   - Mitigation: Integration tests, staged conversion

4. **Development Velocity** - Migration takes longer than estimated
   - Mitigation: Prioritize high-value conversions, maintain working state

### Rollback Strategy
- Keep mixin code commented until fully validated
- Tag releases at major milestones
- Maintain branches for each phase
- Document all changes thoroughly

---

## Getting Started

To begin Phase 3 (Mixin Migration):

1. Run this command to generate a mixin inventory:
   ```bash
   ./gradlew listMixins  # TODO: Create this task
   ```

2. Review the generated inventory in `build/reports/mixins.txt`

3. Start with Sodium accessor mixins (lowest risk, immediate benefit)

4. Follow the conversion template in `docs/MIXIN-CONVERSION-TEMPLATE.md` (TODO: Create)

5. Test each conversion thoroughly before proceeding

---

## Questions or Concerns?

This is an ambitious but achievable roadmap. The key is incremental progress:
- Small, tested changes
- Maintain working state
- Benchmark regularly
- Document decisions

The end result will be a **faster, more maintainable, and more integrated** codebase that fully leverages the single-JAR architecture.
