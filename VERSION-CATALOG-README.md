# 📖 Version Catalog Documentation

## Quick Links

- **[VERSION-CATALOG-DEMO.md](VERSION-CATALOG-DEMO.md)** - Comprehensive explanation with real examples
- **[CATALOG-EXAMPLES.md](CATALOG-EXAMPLES.md)** - Practical how-to guide for migration
- **[VERSION-CATALOG-VISUAL.md](VERSION-CATALOG-VISUAL.md)** - Visual diagrams and comparisons
- **[gradle/libs.versions.toml](gradle/libs.versions.toml)** - The actual catalog (ready to use!)

## What is a Gradle Version Catalog?

A **Gradle version catalog** is a modern way to manage dependencies. Instead of scattering version numbers throughout your `build.gradle`, you define them once in `gradle/libs.versions.toml` and reference them by name.

### The Problem It Solves

**Current approach (hardcoded versions):**
```groovy
implementation 'org.apache.logging.log4j:log4j-core:2.22.1'
implementation 'org.apache.logging.log4j:log4j-api:2.22.1'
implementation 'org.apache.logging.log4j:log4j-slf4j2-impl:2.22.1'
```

**Issues:**
- Version `2.22.1` repeated 3 times
- Updating requires changing 3 lines manually
- Easy to accidentally use different versions
- No IDE autocomplete
- Hard to see all versions at a glance

**With version catalog:**
```groovy
implementation libs.bundles.logging  // All 3 libraries, one line!
```

**To update Log4j 2.22.1 → 2.23.0:**
- Open `gradle/libs.versions.toml`
- Change ONE line: `log4j = "2.23.0"`
- Done! All 3 libraries update automatically

## Key Benefits

✅ **Single Source of Truth** - Update a version in ONE place  
✅ **Type-Safe** - IDE autocomplete for all dependencies  
✅ **Bundles** - Group related dependencies together  
✅ **Reusable** - Share versions across multiple modules  
✅ **Easy Auditing** - See all versions at a glance  
✅ **Prevents Conflicts** - Impossible to use mismatched versions  

## Available Bundles

Your catalog includes these pre-made bundles:

| Bundle | Includes | Lines Saved |
|--------|----------|-------------|
| `libs.bundles.logging` | SLF4J + all Log4j | 4 → 1 |
| `libs.bundles.lwjgl` | All 8 LWJGL modules | 8 → 1 |
| `libs.bundles.apache.commons` | All Apache Commons libs | 5 → 1 |
| `libs.bundles.testing` | JUnit + Mockito + AssertJ | 9 → 1 |
| `libs.bundles.asm` | All 5 ASM modules | 5 → 1 |
| `libs.bundles.fabric` | Fabric Loader deps | 3 → 1 |
| `libs.bundles.iris` | Iris Shaders deps | 3 → 1 |
| `libs.bundles.distant.horizons` | Distant Horizons deps | 7 → 1 |

**Total reduction: 44 lines → 8 lines!**

## Quick Start

### Option 1: Use Bundles (Easiest)

Replace this:
```groovy
implementation 'org.slf4j:slf4j-api:2.0.9'
implementation 'org.apache.logging.log4j:log4j-slf4j2-impl:2.22.1'
implementation 'org.apache.logging.log4j:log4j-core:2.22.1'
implementation 'org.apache.logging.log4j:log4j-api:2.22.1'
```

With this:
```groovy
implementation libs.bundles.logging
```

### Option 2: Use Individual References

Replace this:
```groovy
implementation 'com.google.guava:guava:32.1.2-jre'
implementation 'com.google.code.gson:gson:2.11.0'
```

With this:
```groovy
implementation libs.google.guava
implementation libs.google.gson
```

### Option 3: Mix and Match

```groovy
dependencies {
    // Use bundles for groups
    implementation libs.bundles.logging
    implementation libs.bundles.apache.commons
    
    // Use individual references for standalone deps
    implementation libs.google.guava
    implementation libs.netty.all
    implementation libs.fastutil
}
```

## How to Update a Dependency

**Example: Upgrade LWJGL from 3.3.3 to 3.4.0**

1. Open `gradle/libs.versions.toml`
2. Find the version line:
   ```toml
   lwjgl = "3.3.3"
   ```
3. Change it:
   ```toml
   lwjgl = "3.4.0"
   ```
4. Done! All 8 LWJGL modules now use version 3.4.0

## IDE Support

When you type `libs.` in your build.gradle, your IDE will show autocomplete:

```
libs.
├─ apache.commons.lang3
├─ apache.commons.io
├─ google.guava
├─ google.gson
├─ lwjgl.glfw
├─ bundles.
│  ├─ logging
│  ├─ apache.commons
│  └─ lwjgl
└─ versions.
   ├─ lwjgl
   ├─ log4j
   └─ junit
```

**No more typos!** Your IDE knows what exists.

## Migration Guide

The catalog is **backward compatible** - your current build.gradle still works.

You can migrate gradually:

1. Start with bundles (biggest wins):
   - Replace 4 logging deps → `libs.bundles.logging`
   - Replace 5 Apache Commons → `libs.bundles.apache.commons`
   - Replace 9 testing deps → `libs.bundles.testing`

2. Then migrate individual dependencies:
   - Replace `'com.google.guava:guava:32.1.2-jre'` → `libs.google.guava`

3. Test after each change:
   ```bash
   ./gradlew build
   ```

See **[CATALOG-EXAMPLES.md](CATALOG-EXAMPLES.md)** for step-by-step examples.

## What's Included

### 1. gradle/libs.versions.toml (9.5 KB)
The actual catalog with:
- All 60+ dependencies from your project
- 10 pre-made dependency bundles
- Organized into logical groups (Mojang, Google, Apache, LWJGL, etc.)
- Comprehensive inline documentation

### 2. VERSION-CATALOG-DEMO.md (11 KB)
Complete explanation covering:
- What is a version catalog and why it matters
- Real examples from your project
- Before/after comparisons
- Step-by-step usage guide
- Common scenarios and solutions

### 3. CATALOG-EXAMPLES.md (5.6 KB)
Practical how-to guide:
- Simple migration examples
- Using bundles effectively
- Accessing versions directly
- Conditional logic patterns
- Testing the catalog

### 4. VERSION-CATALOG-VISUAL.md (12 KB)
Visual learning:
- ASCII diagrams showing architecture
- Before/after visual comparisons
- Update workflow comparisons
- IDE experience demonstrations
- Bundle power visualizations
- Multi-module benefits

## Next Steps

1. **Read the documentation** - Start with [VERSION-CATALOG-DEMO.md](VERSION-CATALOG-DEMO.md)
2. **Try a bundle** - Replace your logging dependencies with `libs.bundles.logging`
3. **Test the build** - Run `./gradlew build` to verify
4. **Gradually migrate** - Move more dependencies over time

The catalog is ready to use whenever you want! It's there to make your life easier. 🚀

## Learn More

- **Gradle Official Docs**: https://docs.gradle.org/current/userguide/platforms.html
- **Version Catalogs Guide**: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
- **TOML Spec**: https://toml.io/

---

**Questions?** Check the documentation files above - they have detailed examples for every scenario.
