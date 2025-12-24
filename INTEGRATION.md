# MattMC Mod Integration Plan - Hook-Based Integration

## Goal

**Remove the need for mixins entirely** while keeping mods in separate modules.

## The Problem

- Mods currently use **mixins** (runtime bytecode modification) to inject functionality into Minecraft
- Mixins are complex, fragile, and make the codebase harder to understand and maintain
- We want to eliminate mixins but keep mods as separate modules
- **Constraint**: Minecraft cannot depend on mod packages at compile time (would create circular dependencies)

## The Solution: Interface-Based Hook System

Instead of using mixins to inject code at runtime, we add **hook interfaces** directly into Minecraft source code. Mods implement these interfaces and register themselves at runtime.

### How It Works

1. **Add hook interfaces in Minecraft** - Define interfaces like `RenderHooks`, `GameHooks`, etc. in Minecraft packages
2. **Add hook calls in Minecraft** - At points where mixins would inject, call the registered hooks instead
3. **Mods implement interfaces** - Each mod implements the hook interfaces in their own separate modules
4. **Runtime registration** - Mods register their hook implementations when the game starts

### Why This Works

- ✅ **No mixins needed** - Direct method calls instead of bytecode modification
- ✅ **Mods stay separate** - No need to move mod code into Minecraft packages
- ✅ **No compile-time dependencies** - Minecraft only knows about its own interfaces
- ✅ **Clean and maintainable** - Easy to understand what hooks exist and where they're called
- ✅ **Type-safe** - Compiler checks interface implementations

## Example: Replacing a Mixin with Hooks

### Before (Using Mixins)

```java
// In Sodium mod (net.caffeinemc.mods.sodium.mixin.MinecraftMixin)
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "buildInitialScreens", at = @At("TAIL"))
    private void postInit(CallbackInfoReturnable<Runnable> cir) {
        ResourcePackScanner.checkIfCoreShaderLoaded(this.resourceManager);
    }
}
```

### After (Using Hooks)

```java
// In Minecraft (net.minecraft.hooks.GameHooks.java)
public interface GameHooks {
    void onGameInitialized(Minecraft minecraft);
}

// In Minecraft (net.minecraft.client.Minecraft.java)
public class Minecraft {
    private Runnable buildInitialScreens() {
        // ... original code ...
        
        // Call registered hooks
        for (GameHooks hook : HookRegistry.getGameHooks()) {
            hook.onGameInitialized(this);
        }
        
        return screenTask;
    }
}

// In Sodium mod (still in separate module!)
public class SodiumGameHook implements GameHooks {
    @Override
    public void onGameInitialized(Minecraft minecraft) {
        ResourcePackScanner.checkIfCoreShaderLoaded(minecraft.getResourceManager());
    }
}

// Sodium registers itself at startup
HookRegistry.registerGameHook(new SodiumGameHook());
```

## Implementation Strategy

### Phase 1: Infrastructure Setup
1. Create hook registry system in Minecraft (`net.minecraft.hooks.HookRegistry`)
2. Create initial hook interfaces (`GameHooks`, `RenderHooks`, etc.)
3. Test the registration system works

### Phase 2: Convert Mixins to Hooks (Incremental)
For each mixin:
1. Analyze what it does and where it injects
2. Create or use existing hook interface method
3. Add hook call in Minecraft at injection point
4. Implement hook in mod
5. Remove mixin class and config entry
6. Test that functionality still works

### Phase 3: Remove Mixin System
Once all mixins are converted:
1. Remove SpongePowered Mixin dependency
2. Remove Fabric Loader's mixin initialization
3. Simplify build system

## Benefits

- **Simpler codebase** - No runtime bytecode modification
- **Better IDE support** - Can navigate from hook calls to implementations
- **Easier debugging** - Stack traces show actual method calls, not mixin magic
- **Better performance** - No runtime bytecode transformation overhead
- **Maintainable** - Clear contracts defined by interfaces

## Proof of Concept

A working proof of concept has been implemented for Sodium's resource pack checking functionality:
- Hook interface: `net.minecraft.hooks.GameHooks`
- Hook registry: `net.minecraft.hooks.HookRegistry`
- Minecraft integration: Added hook call in `Minecraft.buildInitialScreens()`
- Sodium implementation: `SodiumGameHook` implements the interface
- Original mixin removed from `MinecraftMixin.java`
