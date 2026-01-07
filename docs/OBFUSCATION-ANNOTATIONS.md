# Obfuscation Annotations in MattMC

## Overview

The `net.minecraft.obfuscate` package and `com.mojang.blaze3d` package both contain a `@DontObfuscate` annotation. This document explains what these annotations are for and whether they are actively used in this project.

## What is @DontObfuscate?

The `@DontObfuscate` annotation is a Java annotation that was originally designed to **mark classes and methods that should NOT be obfuscated** when code obfuscation tools are applied to the compiled bytecode.

### Location in Project

Two versions of this annotation exist in the codebase:

1. **`src/main/java/net/minecraft/obfuscate/DontObfuscate.java`** - General-purpose annotation
2. **`src/main/java/com/mojang/blaze3d/DontObfuscate.java`** - Client-side specific annotation (has `@Environment(EnvType.CLIENT)`)

Both are functionally similar but exist in different packages, likely due to the decompiled nature of Minecraft's source code where different components had their own obfuscation markers.

### Annotation Definition

```java
@TypeQualifierDefault({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface DontObfuscate {
}
```

Key characteristics:
- **Retention Policy**: `CLASS` - The annotation is retained in the compiled `.class` files but not available at runtime
- **Target**: Can be applied to both types (classes, interfaces) and methods
- **Purpose**: Signals to obfuscation tools (like ProGuard or R8) that marked elements should keep their original names

## Usage in MattMC

The `@DontObfuscate` annotation is used throughout the codebase on **44 files**, primarily on:

1. **Main entry point classes**:
   - `net.minecraft.server.Main`
   - `net.minecraft.client.main.Main`
   - `net.minecraft.data.Main`
   - `net.minecraft.gametest.Main`

2. **Methods called by external systems**:
   - `ClientBrandRetriever.getClientModName()` - Called by Fabric/mod loaders
   - `MinecraftServer.getServerModName()` - Called by server management tools

3. **Low-level graphics/rendering classes** (in `com.mojang.blaze3d`):
   - OpenGL state management classes
   - GPU resource classes
   - Rendering pipeline classes
   - Shader system classes

4. **JFR (Java Flight Recorder) event classes**:
   - Profiling event classes that may be accessed reflectively

## Is Obfuscation Actually Used in MattMC?

**No, obfuscation is NOT currently used in this project.**

### Evidence:

1. **No obfuscation tools in build.gradle**: The `build.gradle` file contains no references to:
   - ProGuard
   - R8 (Android obfuscator)
   - Any other obfuscation tool

2. **No obfuscation configuration files**: The project has no:
   - `proguard-rules.pro`
   - `proguard.txt`
   - R8 configuration files

3. **This is a decompiled codebase**: As stated in the README, MattMC is a "decompiled version of Minecraft Java Edition 1.21.10" with full source code access. The purpose is educational and development use with **transparent, readable code** - the opposite of obfuscation.

4. **Project philosophy**: The README explicitly states "No Bullshit" and "Full Source Access" as core principles, which contradicts the use of obfuscation.

## Why Does the Annotation Still Exist?

The `@DontObfuscate` annotation exists in the codebase because:

1. **Decompilation artifact**: When Minecraft's compiled and obfuscated code was decompiled, these annotations were part of the original source. They were preserved during decompilation to maintain code structure.

2. **Original Mojang usage**: Mojang/Microsoft compiles and distributes Minecraft in an obfuscated form to protect intellectual property. These annotations marked which classes and methods needed to remain un-obfuscated for:
   - Mod loader compatibility (Fabric, Forge, etc.)
   - External tool integration
   - Reflection-based access
   - JNI (Java Native Interface) calls
   - Java Flight Recorder integration

3. **Code preservation**: Keeping these annotations maintains the original code structure and provides documentation about which elements have external dependencies or contracts.

## Should the Annotations Be Removed?

**No, they should be kept** for the following reasons:

1. **Documentation value**: They document which methods and classes have external contracts or are accessed reflectively
2. **Mod compatibility**: If someone wants to build modified versions or add obfuscation for distribution, these annotations provide valuable guidance
3. **Future-proofing**: If the project later adds an obfuscation step for certain builds, the annotations are already in place
4. **Historical accuracy**: Preserving the decompiled structure maintains fidelity to the original codebase
5. **No harm**: The annotations have `RetentionPolicy.CLASS`, meaning they don't impact runtime performance or behavior when obfuscation isn't used

## Summary

The `obfuscate/DontObfuscate.java` file defines an annotation that **marks code elements that should not be renamed during obfuscation**. While this annotation appears throughout the codebase on 44+ files, **MattMC does not currently use any obfuscation tools**.

The annotations are **vestigial remnants from the original Minecraft codebase** where they served an important purpose. In MattMC, they primarily serve as:
- Documentation of external contracts
- Historical preservation of code structure  
- Future-proofing for potential obfuscation needs

**In short**: The `DontObfuscate` annotation is present but not actively used by any build process. It can safely be ignored for development purposes, but provides useful documentation about which APIs have external dependencies.

## Related Files

- `src/main/java/net/minecraft/obfuscate/DontObfuscate.java` - General annotation
- `src/main/java/com/mojang/blaze3d/DontObfuscate.java` - Client-side annotation
- `build.gradle` - Build configuration (no obfuscation configured)
- `docs/PROJECT-STRUCTURE.md` - Mentions the obfuscate package

## Further Reading

- [ProGuard Documentation](https://www.guardsquare.com/proguard)
- [Understanding Java Annotations](https://docs.oracle.com/javase/tutorial/java/annotations/)
- [Minecraft Obfuscation and Deobfuscation](https://fabricmc.net/wiki/tutorial:mappings)
