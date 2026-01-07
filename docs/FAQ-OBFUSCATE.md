# FAQ: What is obfuscate/DontObfuscate.java used for?

## Quick Answer

**The `obfuscate/DontObfuscate.java` file is NOT actively used in MattMC.** 

It's a vestigial annotation from the original Minecraft codebase that marked code elements which should not be obfuscated. Since MattMC is a decompiled project with full source access and no obfuscation in the build process, these annotations serve only as documentation.

## Detailed Explanation

### What It Is
- An annotation (`@DontObfuscate`) that marks classes and methods
- Two versions exist:
  - `src/main/java/net/minecraft/obfuscate/DontObfuscate.java` (general)
  - `src/main/java/com/mojang/blaze3d/DontObfuscate.java` (client-side)

### What It Was For (Originally)
In Mojang's production builds of Minecraft, obfuscation tools (like ProGuard) rename classes and methods to protect intellectual property. This annotation told those tools: "Don't rename this - it's needed by mods or external tools."

### Current Status in MattMC
- ✅ Present in the code (45 files use it)
- ❌ Not used by any build process
- ❌ No obfuscation tools configured
- ✅ Kept for documentation purposes

### Why Keep It?
1. Documents which APIs have external contracts
2. Preserves fidelity to original Minecraft codebase
3. Future-proofing if obfuscation is ever added
4. Helps mod developers understand API boundaries

## For More Information
See the comprehensive guide: [OBFUSCATION-ANNOTATIONS.md](OBFUSCATION-ANNOTATIONS.md)
