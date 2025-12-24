# Comprehensive Mod Integration Plan

## Goal
**Fully integrate Iris, Sodium, Distant Horizons, and Fabric Loader into the main Minecraft jar with all functionality intact.**

---

## Current Architecture

### Build Structure
```
MattMC/
├── src/main/java/          # Main Minecraft jar (vanilla + custom code)
├── modules/
│   ├── Iris-1.21.9/        # Iris shader mod
│   ├── sodium-1.21.9/      # Sodium rendering optimization
│   ├── distant-horizons/   # Distant Horizons LOD renderer
│   └── fabric-loader-0.18.2/ # Fabric mod loader
```

### Current Compilation Model
- **`src/`** → Compiles into main Minecraft jar
- **`modules/`** → Each module compiles into separate mod jars
- **Dependencies**: Mods depend on Minecraft jar (one-way dependency)

---

## Challenges & Blockers

### 1. **Circular Dependencies**
- **Problem**: Mods reference each other at runtime (e.g., Distant Horizons ↔ Iris integration)
- **Current State**: Using reflection in some cases (e.g., `IrisAccessor`)
- **Impact**: Cannot merge mods into single jar without resolving cross-module references

### 2. **Mixin Framework Dependency**
- **Problem**: All mods heavily use Fabric's Mixin framework to inject code into Minecraft
- **Mixins Required**:
  - Iris: ~150+ mixin classes
  - Sodium: ~80+ mixin classes  
  - Distant Horizons: ~40+ mixin classes
- **Challenge**: Mixins expect separate mod jars loaded by Fabric Loader
- **Impact**: Mixins won't work correctly if code is in the main jar

### 3. **Fabric Loader Integration**
- **Problem**: Mods rely on Fabric Loader's:
  - Mod discovery and loading mechanism
  - Entrypoint system (`ModInitializer`, `ClientModInitializer`)
  - Configuration system
  - Event system
- **Challenge**: Fabric Loader itself is designed to load external mods
- **Impact**: Cannot remove Fabric Loader without rewriting mod initialization

### 4. **Resource Loading**
- **Problem**: Mods have their own resource packs, shaders, and assets
- **Locations**:
  - Iris: Shader pack system with custom format
  - Sodium: Custom config screens and textures
  - Distant Horizons: Custom UI assets
- **Challenge**: Resources are loaded from mod jar paths
- **Impact**: Resource paths hardcoded to expect mod structure

### 5. **Mod Metadata & APIs**
- **Problem**: Mods expose public APIs for inter-mod compatibility
- **Examples**:
  - Iris API (`net.irisshaders.iris.api.v0.IrisApi`)
  - Distant Horizons API (`com.seibel.distanthorizons.api.*`)
- **Challenge**: Other mods/plugins expect these APIs at specific package paths
- **Impact**: Breaking API contracts breaks third-party integrations

### 6. **Package Name Conflicts**
- **Problem**: Classes have overlapping names across mods
- **Examples**:
  - Multiple `MixinOption` classes
  - Similar utility classes (`StringUtil`, `ColorUtil`, etc.)
- **Challenge**: Merging into single jar causes naming collisions
- **Impact**: Requires renaming hundreds of classes

### 7. **Build Configuration Complexity**
- **Problem**: Each mod has custom Gradle build logic
- **Includes**:
  - Custom source sets
  - Annotation processors
  - Shadow/repackaging
  - Multi-version support
- **Challenge**: Merging build scripts is non-trivial
- **Impact**: May break mod-specific build requirements

---

## Integration Strategy

### Phase 1: Infrastructure Setup (Week 1-2)

#### 1.1 Fabric Loader Embedding
**Goal**: Embed Fabric Loader into main jar instead of loading it as mod

**Tasks**:
- [ ] Move Fabric Loader source to `src/main/java/net/fabricmc/loader/`
- [ ] Modify Fabric Loader to discover mods from classpath instead of jar files
- [ ] Update mod loading to support in-memory mod registration
- [ ] Test: Ensure Fabric Loader initializes in embedded mode

**Risk**: Medium - Fabric Loader expects filesystem-based mod discovery

#### 1.2 Mixin Infrastructure
**Goal**: Ensure Mixin framework works with embedded mods

**Tasks**:
- [ ] Configure Mixin to load configs from classpath resources
- [ ] Update mixin config paths to embedded resource locations
- [ ] Modify mixin application to support single-jar architecture
- [ ] Test: Verify mixins apply correctly from main jar

**Risk**: High - Mixins may expect separate mod jars

---

### Phase 2: Sodium Integration (Week 3-4)

**Rationale**: Start with Sodium as it has the fewest external dependencies

#### 2.1 Source Migration
**Tasks**:
- [ ] Copy Sodium sources to `src/main/java/net/caffeinemc/mods/sodium/`
- [ ] Keep original package names (no renaming)
- [ ] Move Sodium resources to `src/main/resources/assets/sodium/`
- [ ] Update resource loading paths

#### 2.2 Dependency Resolution
**Tasks**:
- [ ] Add Sodium's dependencies to main `build.gradle`
- [ ] Resolve any dependency version conflicts with vanilla Minecraft
- [ ] Update imports if package structure changes

#### 2.3 Initialization
**Tasks**:
- [ ] Register Sodium as embedded mod with Fabric Loader
- [ ] Ensure `SodiumClientMod` initializer runs
- [ ] Verify config system works
- [ ] Test: Launch Minecraft and verify Sodium features work

**Risk**: Medium - Config and rendering changes need validation

---

### Phase 3: Iris Integration (Week 5-7)

**Rationale**: Iris depends on Sodium, so must come after

#### 3.1 Source Migration
**Tasks**:
- [ ] Copy Iris sources to `src/main/java/net/irisshaders/iris/`
- [ ] Keep original package names
- [ ] Move shader pack system to `src/main/resources/`
- [ ] Update shader loading to use embedded resources

#### 3.2 Sodium Compatibility
**Tasks**:
- [ ] Verify Iris mixins into Sodium work correctly
- [ ] Test Iris-Sodium rendering pipeline integration
- [ ] Ensure shader pack loading works

#### 3.3 API Preservation
**Tasks**:
- [ ] Keep Iris API at `net.irisshaders.iris.api.v0.*`
- [ ] Ensure other mods can still find and use Iris API
- [ ] Document any API changes

#### 3.4 Testing
**Tasks**:
- [ ] Test shader pack loading
- [ ] Test rendering with various shader packs
- [ ] Verify config GUI works
- [ ] Test: Performance benchmarks vs standalone Iris

**Risk**: High - Complex rendering pipeline interactions

---

### Phase 4: Distant Horizons Integration (Week 8-10)

**Rationale**: Most complex due to heavy inter-mod dependencies

#### 4.1 Source Migration
**Tasks**:
- [ ] Copy DH API to `src/main/java/com/seibel/distanthorizons/api/`
- [ ] Copy DH core to `src/main/java/com/seibel/distanthorizons/core/`
- [ ] Copy platform-specific code
- [ ] Move resources and configs

#### 4.2 Iris Integration
**Tasks**:
- [ ] Remove reflection-based `IrisAccessor` (no longer needed)
- [ ] Replace with direct imports to Iris classes
- [ ] Update DH-Iris rendering bridge
- [ ] Test shadow rendering with Iris shaders

#### 4.3 Sodium Integration
**Tasks**:
- [ ] Verify DH mixins into Sodium work
- [ ] Test chunk rendering integration
- [ ] Ensure LOD system works with Sodium optimizations

#### 4.4 Testing
**Tasks**:
- [ ] Test LOD generation and rendering
- [ ] Test multiplayer data synchronization
- [ ] Verify world save/load
- [ ] Performance testing with all three mods enabled

**Risk**: Very High - Most complex integration

---

### Phase 5: Optimization & Cleanup (Week 11-12)

#### 5.1 Remove Duplication
**Tasks**:
- [ ] Identify duplicate utility classes
- [ ] Consolidate into single implementation
- [ ] Update all references
- [ ] Remove unused code

#### 5.2 Package Reorganization
**Tasks**:
- [ ] Consider flattening package structure
- [ ] Move all to `net.mattmc.` parent package (optional)
- [ ] Update all imports and references
- [ ] Update mixin configs

**Risk**: Low if done after everything works

#### 5.3 Build Optimization
**Tasks**:
- [ ] Remove module-specific source sets
- [ ] Simplify Gradle build script
- [ ] Optimize compilation time
- [ ] Configure proper dependency scoping

---

### Phase 6: Testing & Validation (Week 13-14)

#### 6.1 Functional Testing
**Test Cases**:
- [ ] Launch Minecraft successfully
- [ ] Load world with all features working
- [ ] Apply shader packs (Iris)
- [ ] Verify rendering optimizations (Sodium)
- [ ] Test LOD rendering (Distant Horizons)
- [ ] Test multiplayer functionality
- [ ] Config GUI for all mods
- [ ] Mod compatibility with external mods

#### 6.2 Performance Testing
**Benchmarks**:
- [ ] FPS comparison: integrated vs separate mods
- [ ] Memory usage comparison
- [ ] Startup time comparison
- [ ] World loading time comparison

#### 6.3 Stability Testing
**Tests**:
- [ ] Extended play sessions (4+ hours)
- [ ] Stress testing (large render distances)
- [ ] Dimension switching
- [ ] Resource pack switching
- [ ] Server connection/disconnection

---

## Alternative Approaches

### Option A: Keep Mods Separate (Current State)
**Pros**: 
- No integration work needed
- Maintains mod independence
- Easy to update individual mods

**Cons**:
- Separate jars to manage
- Can't share code efficiently
- Potential version conflicts

### Option B: Partial Integration
**Approach**: Integrate only utility libraries and shared code into main jar, keep mod logic separate

**Pros**:
- Reduces duplication
- Maintains mod isolation
- Lower risk

**Cons**:
- Still have multiple jars
- Limited benefits

### Option C: Full Fork & Refactor
**Approach**: Fork all mods, remove Mixin framework, directly integrate into Minecraft codebase

**Pros**:
- True integration
- Maximum optimization potential
- No mod loader overhead

**Cons**:
- Massive development effort (6+ months)
- Breaks compatibility with mod updates
- Difficult to maintain
- Loses upstream bug fixes

---

## Recommended Path Forward

### SHORT TERM (Recommended)
**Keep current modular architecture** but improve integration:

1. **Shared Library Layer**
   - Create `src/main/java/net/mattmc/common/` for shared utilities
   - Move duplicate utility classes here
   - Have mods depend on this

2. **Improve Build Integration**
   - Use Gradle's `implementation` dependencies properly
   - Share version constants
   - Unified dependency management

3. **API Unification**
   - Create unified API layer for cross-mod communication
   - Replace reflection with direct API calls where safe

### LONG TERM (If Full Integration Needed)
Follow the phased approach above:
- **Phase 1-2**: 4-6 weeks (Fabric + Sodium)
- **Phase 3**: 3 weeks (Iris)
- **Phase 4**: 3 weeks (Distant Horizons)
- **Phase 5-6**: 4 weeks (Optimization + Testing)

**Total Estimated Time**: 14-16 weeks (3-4 months) of dedicated development

---

## Technical Prerequisites

### Required Knowledge
- Deep understanding of Minecraft rendering pipeline
- Fabric Loader internals
- Mixin framework architecture
- Gradle build system
- Java 17+ features

### Tools Needed
- IntelliJ IDEA Ultimate (for refactoring)
- Automated testing framework
- Performance profiling tools (JProfiler, VisualVM)

### Team Size
- **Minimum**: 1 senior developer (4 months full-time)
- **Optimal**: 2-3 developers (2 months)

---

## Risk Assessment

| Phase | Risk Level | Mitigation |
|-------|-----------|------------|
| Fabric Embedding | Medium | Thorough testing, rollback plan |
| Sodium Integration | Medium | Start with minimal features |
| Iris Integration | High | Extensive shader testing |
| DH Integration | Very High | Phased approach, feature flags |
| Optimization | Low | Do last, after stability |

---

## Success Criteria

### Must Have
- ✅ Minecraft launches successfully
- ✅ All mod features functional
- ✅ No performance regression
- ✅ Stable for extended play

### Nice to Have
- 🎯 Improved startup time
- 🎯 Reduced memory usage
- 🎯 Simplified build process
- 🎯 Better cross-mod integration

---

## Conclusion

**Full integration is technically feasible but requires significant effort.** The main challenges are:

1. Mixin framework expects separate jars
2. Circular dependencies between mods
3. Complex rendering pipeline interactions
4. Resource loading from embedded locations

**Recommendation**: Unless there's a compelling business reason, **keep mods separate** and focus on improving integration points instead. If full integration is required, allocate 3-4 months and follow the phased approach.

The current modular architecture is actually a strength, not a weakness - it allows independent updates, easier maintenance, and better separation of concerns.

---

## Next Steps

If proceeding with integration:

1. **Decision Point**: Choose integration strategy (Short Term vs Long Term)
2. **Prototype**: Attempt Phase 1 (Fabric embedding) as proof-of-concept
3. **Evaluate**: Assess technical feasibility and effort required
4. **Commit**: If POC successful, proceed with full plan
5. **Fallback**: If too complex, revert to short-term approach

---

**Document Version**: 1.0  
**Last Updated**: 2025-12-24  
**Author**: GitHub Copilot  
**Status**: Draft - Awaiting Review
