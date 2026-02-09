# Migration Guide: CommandContext Pattern

**Status:** ✅ FOUNDATION COMPLETE  
**Date:** 2026-02-09  
**Purpose:** Document the first step in Vulkan migration - CommandContext abstraction

---

## What Was Implemented

### 1. CommandContext Abstraction

**Files Created:**
- `src/main/java/net/vulkanic/CommandContext.java` - Interface for command recording
- `src/main/java/net/vulkanic/backends/opengl/OpenGLCommandContext.java` - OpenGL implementation

**Purpose:**  
Provides a unified way to record graphics commands that works with both OpenGL (immediate) and Vulkan (deferred/command buffers).

**Key Features:**
- `isImmediate()` - Identifies if context is immediate-mode (OpenGL) or deferred (Vulkan)
- `getHandle()` - Returns backend-specific handle (0 for OpenGL, VkCommandBuffer for Vulkan)
- `getDebugName()` - Human-readable name for debugging

### 2. Enhanced Existing Non-Deprecated Methods

**Methods Updated:**
- `setDynamicViewport()` - Now has CommandContext variant
- `setDynamicScissor()` - Now has CommandContext variant

**Changes:**
- Added new overloads that take `CommandContext` parameter
- Deprecated old overloads (but kept for backward compatibility)
- Old overloads delegate to new CommandContext variants

### 3. VulkanicAPI Additions

**New Method:**
- `VulkanicAPI.getImmediateContext()` - Returns immediate-mode context for convenience

**Purpose:**  
Allows gradual migration to CommandContext pattern without breaking existing code.

---

## How To Use

### For New Code (Recommended)

```java
// Get the immediate context
CommandContext ctx = VulkanicAPI.getImmediateContext();

// Use CommandContext-aware methods
VulkanicAPI.setDynamicViewport(ctx, 0, 0, 1920, 1080);
VulkanicAPI.setDynamicScissor(ctx, 0, 0, 1920, 1080);
```

### For Existing Code (Still Works)

```java
// Old method still works (but deprecated)
VulkanicAPI.setDynamicViewport(0, 0, 1920, 1080);
VulkanicAPI.setDynamicScissor(0, 0, 1920, 1080);
```

The old methods automatically delegate to the new CommandContext variants using `OpenGLCommandContext.IMMEDIATE`.

---

## Migration Example: setDynamicViewport()

### Before (Still Works, But Deprecated)
```java
public void render() {
    VulkanicAPI.setDynamicViewport(0, 0, width, height);
    // ... rendering ...
}
```

### After (Recommended - Future-Proof)
```java
public void render() {
    CommandContext ctx = VulkanicAPI.getImmediateContext();
    VulkanicAPI.setDynamicViewport(ctx, 0, 0, width, height);
    // ... rendering ...
}
```

### Future Vulkan Code (When Implemented)
```java
public void render() {
    CommandContext ctx = VulkanicAPI.beginCommandBuffer();
    VulkanicAPI.setDynamicViewport(ctx, 0, 0, width, height);
    // ... more rendering commands ...
    VulkanicAPI.submitCommandBuffer(ctx);
}
```

---

## Why This Matters

### Problem Solved
Before this change, methods didn't have a way to specify which command buffer to record into. This is fine for OpenGL (immediate mode) but incompatible with Vulkan (deferred recording).

### Solution
By adding CommandContext parameters NOW:
1. ✅ API is Vulkan-compatible from the start
2. ✅ No future breaking changes needed
3. ✅ Game code can migrate gradually
4. ✅ OpenGL backend works the same (immediate context is just a marker)

---

## Pattern For Future Methods

When adding new non-deprecated methods, follow this pattern:

```java
// 1. In GraphicsBackend interface:
void newMethod(CommandContext ctx, /* other params */);

@Deprecated
void newMethod(/* other params */);  // Legacy variant

// 2. In OpenGLBackend:
@Override
public void newMethod(CommandContext ctx, /* other params */) {
    if (!ctx.isImmediate()) {
        throw new IllegalArgumentException("OpenGL requires immediate context");
    }
    // OpenGL implementation
}

@Deprecated
@Override
public void newMethod(/* other params */) {
    newMethod(OpenGLCommandContext.IMMEDIATE, /* other params */);
}

// 3. In VulkanicAPI:
public static void newMethod(CommandContext ctx, /* other params */) {
    getBackend().newMethod(ctx, /* other params */);
}

@Deprecated
public static void newMethod(/* other params */) {
    getBackend().newMethod(/* other params */);
}
```

---

## Next Steps

Now that CommandContext foundation is in place:

1. **Migrate More Methods:**
   - Add CommandContext to other non-deprecated methods
   - Start migrating high-frequency deprecated methods

2. **Test Migration:**
   - Convert some existing call sites to use CommandContext
   - Verify no performance regression

3. **Document Patterns:**
   - Create migration guides for common patterns
   - Add examples for game developers

4. **Prepare for Vulkan:**
   - Design command buffer management system
   - Plan descriptor set abstractions
   - Consider pipeline state objects

---

## Testing

**Test File:** `src/test/java/net/vulkanic/CommandContextTest.java`

**Tests Verify:**
- ✅ CommandContext interface works
- ✅ OpenGL context is immediate mode
- ✅ OpenGL context is singleton
- ✅ VulkanicAPI.getImmediateContext() works
- ✅ New CommandContext-aware methods exist
- ✅ Legacy methods delegate to new variants

**All tests passing:** ✅ 7/7 tests pass

---

## Benefits

### Immediate Benefits
1. ✅ Sets pattern for future method migrations
2. ✅ Makes API truly backend-agnostic
3. ✅ No breaking changes to existing code
4. ✅ Clear deprecation path

### Future Benefits
1. ✅ Vulkan backend can be added without API changes
2. ✅ Command buffer recording model supported
3. ✅ Multi-threaded rendering possible (Vulkan)
4. ✅ Deferred command execution supported

---

## Validation Checklist

- [x] CommandContext interface created
- [x] OpenGLCommandContext implementation created
- [x] GraphicsBackend methods updated
- [x] OpenGLBackend implements new methods
- [x] VulkanicAPI exposes new methods
- [x] getImmediateContext() convenience method added
- [x] Unit tests created and passing
- [x] Old methods deprecated but still functional
- [x] Documentation written (this file)

---

## Questions & Answers

**Q: Do I have to use CommandContext now?**  
A: No, old methods still work. But new code should use CommandContext for future compatibility.

**Q: Will this make my code slower?**  
A: No. The CommandContext parameter is just a marker in OpenGL. No performance impact.

**Q: When do I need to worry about command buffers?**  
A: Not until Vulkan backend is implemented. For now, `getImmediateContext()` is all you need.

**Q: What happens if I pass the wrong context type?**  
A: OpenGL backend validates and throws `IllegalArgumentException` if you pass a non-immediate context.

**Q: Can I create my own CommandContext?**  
A: No, use `VulkanicAPI.getImmediateContext()` for OpenGL. Vulkan will provide its own command buffer creation methods.

---

**Conclusion:**  
CommandContext pattern is now the foundation for all future API development. This is the first concrete step toward Vulkan support while maintaining full OpenGL compatibility.
