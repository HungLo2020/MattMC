# Vulkanic Graphics Abstraction Layer

## Goal

Vulkanic is a graphics abstraction layer that sits between the Minecraft game/mod code and the underlying graphics APIs (OpenGL and Vulkan). The primary objective is to provide a unified frontend API that allows the codebase to be graphics-API-agnostic, enabling future support for Vulkan while maintaining backward compatibility with OpenGL.

## Architecture

### Directory Structure

```
vulkanic/
├── README.md (this file)
├── [Frontend API classes - public interface]
└── backends/
    ├── opengl/
    │   └── [OpenGL-specific implementation]
    └── vulkan/
        └── [Vulkan-specific implementation - future]
```

### Design Principles

1. **Strict API Boundaries**:
   - Code outside `vulkanic/` can ONLY access the frontend API in `vulkanic/`
   - Code outside `vulkanic/` CANNOT access anything within `backends/`
   - Only backend implementations can call their respective graphics API functions (OpenGL/Vulkan)

2. **Frontend Delegation**:
   - The frontend API delegates to the appropriate backend (OpenGL or Vulkan) based on runtime configuration
   - Initially, only OpenGL backend will be implemented
   - Vulkan backend is planned for future implementation

3. **API Design Philosophy**:
   - Initial OpenGL calls can be 1:1 mappings even if not directly compatible with future Vulkan implementation
   - Focus is on establishing the abstraction layer architecture first
   - Refinement for Vulkan compatibility will occur during Vulkan backend development

## Implementation Strategy

### Phase 1: Blaze3D Integration (Current Focus)

Minecraft already has a graphics abstraction layer called Blaze3D (`net.blaze3d`). To minimize code changes:

1. **Port Blaze3D functionality** into Vulkanic frontend API
2. **Redirect Blaze3D calls** to use Vulkanic instead of directly calling OpenGL
3. This approach minimizes required changes to core Minecraft code

### Phase 2: Mod Integration

Mods like Iris Shaders and Distant Horizons will need to route their graphics calls through Vulkanic:

- **Iris Shaders**: Extensive OpenGL usage for shader-based rendering
- **Distant Horizons**: Custom rendering for level-of-detail terrain
- These integrations will be more complex than core Minecraft changes

### Phase 3: Vulkan Backend

Once the abstraction layer is stable with OpenGL:

1. Implement Vulkan backend in `backends/vulkan/`
2. Refine frontend API to ensure compatibility with both backends
3. Add runtime configuration to select backend

## OpenGL Call Migration Status

### Current Statistics

**Total OpenGL References**: ~2,025 occurrences across the codebase

**Breakdown by Component**:
- **Blaze3D** (current abstraction layer): 37 direct OpenGL imports, 123 Java files
- **Sodium**: 45 OpenGL imports (performance optimization mod)
- **Iris Shaders**: 168 OpenGL imports, 445 Java files (shader support)
- **Distant Horizons**: 1 Java file (minimal direct usage)
- **Core Minecraft**: 0 direct OpenGL imports (uses Blaze3D)

**Unique OpenGL Methods**: ~161 distinct GL function calls identified

### Migration Progress

| Component | Total Calls | Migrated | Percentage |
|-----------|-------------|----------|------------|
| Blaze3D | ~37 imports | 0 | 0% |
| Sodium | ~45 imports | 0 | 0% |
| Iris Shaders | ~168 imports | 0 | 0% |
| Distant Horizons | ~1 file | 0 | 0% |
| **TOTAL** | **~316 import sites** | **0** | **0%** |

*Note: Import sites represent files importing OpenGL APIs. Actual function call count is higher (~2,025 occurrences).*

## Development Guidelines

### For Frontend API Development

1. Define interfaces that are backend-agnostic
2. Use descriptive names that reflect intent, not implementation
3. Document which Blaze3D methods map to which Vulkanic methods
4. Ensure thread-safety where applicable

### For Backend Implementation

1. OpenGL backend should implement the frontend interface
2. Keep OpenGL-specific code isolated in `backends/opengl/`
3. Use appropriate error handling and validation
4. Document any OpenGL-specific limitations or behaviors

### For Consumers (Game/Mod Code)

1. Import only from `net.vulkanic.*` (not from `net.vulkanic.backends.*`)
2. Never directly import `org.lwjgl.opengl.*` after migration
3. Use Vulkanic API instead of direct OpenGL calls
4. Report any missing API functionality to extend the frontend

## Next Steps

1. ✅ Create directory structure
2. ✅ Document architecture and strategy
3. ⏳ Design frontend API interfaces based on Blaze3D analysis
4. ⏳ Implement OpenGL backend for core rendering operations
5. ⏳ Migrate Blaze3D to use Vulkanic
6. ⏳ Migrate Sodium to use Vulkanic
7. ⏳ Migrate Iris Shaders to use Vulkanic
8. ⏳ Add runtime configuration system
9. ⏳ Begin Vulkan backend development

## References

- **Blaze3D**: `src/main/java/net/blaze3d/` - Minecraft's existing graphics abstraction
- **LWJGL OpenGL**: `org.lwjgl.opengl.*` - OpenGL bindings being abstracted
- **Vulkan**: Future target for cross-platform high-performance rendering

---

*This abstraction layer is a critical step toward modernizing MattMC's rendering pipeline and enabling future Vulkan support.*
