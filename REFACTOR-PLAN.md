# MattMC Refactoring Plan

This document outlines the top 10 refactoring priorities for the MattMC project. These are **safe, non-breaking refactorings** focused on improving code quality, maintainability, and performance without changing external behavior.

## Priority 1: Extract Magic Numbers to Named Constants

**Location**: Throughout the codebase, particularly in:
- `net/minecraft/network/protocol/` (packet size limits, compression thresholds)
- `net/minecraft/commands/` (retry counts, timeout values)
- `net/minecraft/world/entity/Entity.java` (physics constants, tick rates)

**Issue**: Hardcoded numeric literals (e.g., `10`, `256`, `2097152`, `20`) scattered throughout the code make it difficult to understand their meaning and maintain consistency.

**Refactoring**: Extract magic numbers to well-named static final constants with descriptive names.

**Example**:
```java
// Before
if (stringReader.getRemainingLength() > 256) {

// After
private static final int MAX_MESSAGE_LENGTH = 256;
if (stringReader.getRemainingLength() > MAX_MESSAGE_LENGTH) {
```

**Benefits**: Improved readability, easier maintenance, single source of truth for configuration values.

---

## Priority 2: Consolidate Duplicated Map Constants

**Location**: `net/minecraft/util/datafix/fixes/BlockStateData.java` (9,174 lines)

**Issue**: This massive file contains hundreds of nearly identical `Map.of()` constant declarations for block state properties (AGE_0, AGE_1, AGE_2, etc., FACING_NORTH, FACING_SOUTH, etc.). This creates significant code duplication and maintenance burden.

**Refactoring**: Replace individual constant maps with a builder pattern or factory methods that generate these maps on demand.

**Example**:
```java
// Before
private static final Map<String, String> AGE_0 = Map.of("age", "0");
private static final Map<String, String> AGE_1 = Map.of("age", "1");
// ... repeated 15+ times

// After
private static Map<String, String> age(int value) {
    return Map.of("age", String.valueOf(value));
}
```

**Benefits**: Dramatically reduces file size, eliminates duplication, easier to extend with new states.

---

## Priority 3: Replace Multiple Boolean Parameters with Enum or Builder Pattern

**Location**: Throughout the codebase, particularly in:
- `net/minecraft/server/MinecraftServer.java` (`saveAllChunks`, `saveEverything`)
- `net/minecraft/network/protocol/game/` (various packet constructors)
- `net/minecraft/world/entity/` (teleport and movement methods)

**Issue**: Methods with multiple boolean parameters (e.g., `saveAllChunks(boolean bl, boolean bl2, boolean bl3)`) are difficult to understand at call sites and error-prone.

**Refactoring**: Replace boolean flag parameters with enums or use the builder pattern for complex configurations.

**Example**:
```java
// Before
server.saveAllChunks(true, false, true);

// After
server.saveAllChunks(SaveOptions.builder()
    .suppressLogs(true)
    .flushCache(false)
    .force(true)
    .build());
```

**Benefits**: Self-documenting code, type safety, easier to extend with new options.

---

## Priority 4: Reduce God Class Complexity

**Location**: 
- `net/minecraft/world/entity/Entity.java` (4,054 lines, 250+ conditionals)
- `net/minecraft/world/entity/LivingEntity.java` (3,742 lines)
- `net/minecraft/client/Minecraft.java` (2,895 lines, 115+ public methods)

**Issue**: These classes have too many responsibilities and are difficult to understand, test, and modify. They violate the Single Responsibility Principle.

**Refactoring**: Extract cohesive functionality into smaller, focused classes:
- Entity physics → `EntityPhysics` helper class
- Entity collision → `EntityCollisionHandler`
- Entity rendering → Move to appropriate renderer classes

**Benefits**: Improved testability, easier navigation, reduced coupling, clearer separation of concerns.

---

## Priority 5: Simplify Complex Conditional Logic

**Location**: Throughout the codebase, particularly in:
- `net/minecraft/server/network/ServerGamePacketListenerImpl.java`
- `net/minecraft/world/entity/Entity.java`
- Movement and collision detection code

**Issue**: Deeply nested conditionals and complex boolean expressions (e.g., `if (a && b && c && d)`) are hard to understand and maintain.

**Refactoring**: Extract complex conditions into well-named predicate methods and use early returns.

**Example**:
```java
// Before
if (this.clientIsFloating && !this.player.isSleeping() && !this.player.isPassenger() && !this.player.isDeadOrDying()) {

// After
private boolean shouldCheckFloating() {
    return this.clientIsFloating 
        && !this.player.isSleeping() 
        && !this.player.isPassenger() 
        && !this.player.isDeadOrDying();
}

if (shouldCheckFloating()) {
```

**Benefits**: Improved readability, self-documenting code, easier to test individual conditions.

---

## Priority 6: Improve Encapsulation of Public Mutable Fields

**Location**:
- `net/minecraft/server/level/ServerPlayer.java` (`connection`, `seenCredits`, `wonGame`)
- `net/minecraft/server/level/ServerLevel.java` (`noSave`)
- `net/minecraft/server/network/ServerConnectionListener.java` (`running`)

**Issue**: Public mutable fields expose internal state and prevent adding validation or side effects when values change.

**Refactoring**: Convert public fields to private with getter/setter methods (or use records where appropriate).

**Example**:
```java
// Before
public boolean seenCredits = false;

// After
private boolean seenCredits = false;

public boolean hasSeenCredits() {
    return this.seenCredits;
}

public void setSeenCredits(boolean seen) {
    this.seenCredits = seen;
}
```

**Benefits**: Better encapsulation, allows validation/logging, maintains API flexibility.

---

## Priority 7: Eliminate Mutable Static State

**Location**:
- `net/minecraft/Util.java` (`timeSource`)
- `net/minecraft/server/packs/VanillaPackResourcesBuilder.java` (`developmentConfig`)
- `net/minecraft/SharedConstants.java` (`CHECK_DATA_FIXER_SCHEMA`)

**Issue**: Mutable static fields create hidden dependencies, make testing difficult, and can cause race conditions in concurrent environments.

**Refactoring**: Convert to final static fields with immutable values, or inject through constructor/method parameters.

**Example**:
```java
// Before
public static TimeSource.NanoTimeSource timeSource = System::nanoTime;

// After - inject through constructor
private final TimeSource.NanoTimeSource timeSource;

public ClassName(TimeSource.NanoTimeSource timeSource) {
    this.timeSource = timeSource;
}
```

**Benefits**: Eliminates global mutable state, improves testability, prevents unexpected side effects.

---

## Priority 8: Optimize String Concatenation in Loops

**Location**: Throughout the codebase where strings are built incrementally:
- `net/minecraft/commands/functions/StringTemplate.java`
- `net/minecraft/commands/SharedSuggestionProvider.java`
- Various formatting and display name generation code

**Issue**: String concatenation with `+` operator in loops creates many intermediate String objects, causing unnecessary allocations and GC pressure.

**Refactoring**: Replace string concatenation in loops with `StringBuilder`.

**Example**:
```java
// Before
for (int i = 0; i < items.size(); i++) {
    result = result + items.get(i) + " ";
}

// After
StringBuilder sb = new StringBuilder();
for (int i = 0; i < items.size(); i++) {
    sb.append(items.get(i)).append(" ");
}
String result = sb.toString();
```

**Benefits**: Reduced memory allocations, improved performance, lower GC overhead.

---

## Priority 9: Standardize Exception Handling

**Location**: Throughout the codebase, particularly in:
- `net/minecraft/Util.java` (multiple catch blocks with only logging)
- Network protocol handling
- File I/O operations

**Issue**: Inconsistent exception handling patterns - some exceptions are logged, some are silently swallowed, and error messages vary in quality.

**Refactoring**: Establish consistent exception handling patterns:
- Always log exceptions with context
- Use specific exception types rather than catching `Exception`
- Provide actionable error messages

**Example**:
```java
// Before
} catch (IOException var2) {
    Util.LOGGER.error("Failed to rename", (Throwable)var2);
}

// After
} catch (IOException e) {
    LOGGER.error("Failed to rename file from {} to {}: {}", 
        sourcePath, targetPath, e.getMessage(), e);
}
```

**Benefits**: Better debugging, consistent logging, more actionable error messages.

---

## Priority 10: Extract Large Registration Methods into Configuration Classes

**Location**:
- `net/minecraft/world/level/block/Blocks.java` (6,854 lines of block registrations)
- `net/minecraft/world/item/Items.java` (2,480 lines of item registrations)
- `net/minecraft/sounds/SoundEvents.java` (1,794 lines of sound registrations)

**Issue**: Massive static initializer classes with thousands of lines of registration code are difficult to navigate and maintain.

**Refactoring**: Split registrations into logical groups using nested classes or separate configuration classes:
- `Blocks.Stone` → all stone-related blocks
- `Blocks.Wood` → all wood-related blocks
- Similar grouping for Items and SoundEvents

**Example**:
```java
// Before
public class Blocks {
    public static final Block STONE = register(...);
    public static final Block GRANITE = register(...);
    // ... 500+ more blocks
}

// After
public class Blocks {
    public static class Stone {
        public static final Block STONE = register(...);
        public static final Block GRANITE = register(...);
        // ... stone variants
    }
    
    public static class Wood {
        public static final Block OAK_PLANKS = register(...);
        public static final Block SPRUCE_PLANKS = register(...);
        // ... wood variants
    }
}
```

**Benefits**: Better organization, easier navigation, logical grouping of related items.

---

## Implementation Guidelines

### Safe Refactoring Practices

1. **One change at a time**: Each refactoring should be done independently
2. **Maintain backward compatibility**: All public APIs must remain unchanged
3. **Preserve behavior**: No functional changes, only structural improvements
4. **Test after each change**: Ensure builds succeed and existing tests pass
5. **Use IDE refactoring tools**: Leverage automated refactoring when possible

### Non-Breaking Principles

- Keep all public method signatures unchanged
- Maintain existing class names and package structure
- Preserve serialization compatibility where relevant
- Don't change any gameplay mechanics or logic
- Ensure all existing code paths continue to work identically

### Validation Steps

After each refactoring:
1. Run `./gradlew build` to ensure compilation succeeds
2. Run `./gradlew test` if tests exist
3. Verify that both client and server can start successfully
4. Check that basic gameplay functionality remains intact

---

## Estimated Impact

These refactorings will:
- **Improve maintainability** by reducing code complexity and duplication
- **Enhance readability** through better naming and organization
- **Increase performance** by optimizing string operations and reducing allocations
- **Strengthen code quality** through better encapsulation and error handling
- **Facilitate future development** by establishing cleaner architectural patterns

All changes are **safe, incremental, and non-breaking**, preserving the existing functionality while improving the codebase quality.
