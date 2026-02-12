# 📚 Gradle Version Catalog - Practical Demonstration

## What is a Gradle Version Catalog?

A **Gradle version catalog** is a centralized way to manage all your dependency versions. Instead of scattering version numbers throughout your `build.gradle`, you define them once in a special file: `gradle/libs.versions.toml`.

Think of it like a **dictionary** or **phone book** for dependencies:
- Instead of remembering phone numbers (version numbers), you just remember names
- Update a phone number once, and everyone has the new number
- Easy to see all your contacts (dependencies) in one place

---

## 🔍 The Problem (Current State)

**In your current `build.gradle` (lines 98-270):**

```groovy
dependencies {
    // Version numbers are scattered everywhere!
    implementation 'com.google.guava:guava:32.1.2-jre'
    implementation 'com.google.code.gson:gson:2.11.0'
    
    implementation 'org.apache.commons:commons-lang3:3.14.0'
    implementation 'commons-io:commons-io:2.15.1'
    implementation 'org.apache.commons:commons-compress:1.26.0'
    
    implementation 'org.slf4j:slf4j-api:2.0.9'
    implementation 'org.apache.logging.log4j:log4j-slf4j2-impl:2.22.1'
    implementation 'org.apache.logging.log4j:log4j-core:2.22.1'
    implementation 'org.apache.logging.log4j:log4j-api:2.22.1'
    
    // What if you want to update all Log4j to 2.23.0?
    // You'd have to find and change 3 separate lines!
    
    // LWJGL repeated 8 times with natives
    runtimeOnly "org.lwjgl:lwjgl::${lwjglNatives}"
    runtimeOnly "org.lwjgl:lwjgl-glfw::${lwjglNatives}"
    runtimeOnly "org.lwjgl:lwjgl-opengl::${lwjglNatives}"
    // ... 5 more times
    
    // Testing dependencies spread across many lines
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.1'
    testImplementation 'org.junit.jupiter:junit-jupiter-params:5.10.1'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.10.1'
    testImplementation 'org.assertj:assertj-core:3.25.1'
    testImplementation 'org.mockito:mockito-core:5.8.0'
    testImplementation 'org.mockito:mockito-junit-jupiter:5.8.0'
}
```

**Problems:**
1. ❌ **Version numbers duplicated** - Log4j version appears 3 times
2. ❌ **Hard to update** - Upgrading LWJGL requires changing 8+ lines
3. ❌ **No overview** - Can't see all versions at a glance
4. ❌ **Typo-prone** - Easy to accidentally use different versions
5. ❌ **No autocomplete** - Have to remember exact group:artifact:version strings

---

## ✅ The Solution (With Version Catalog)

### Step 1: Define versions in `gradle/libs.versions.toml`

```toml
[versions]
log4j = "2.22.1"
lwjgl = "3.3.3"
junit = "5.10.1"
mockito = "5.8.0"

[libraries]
# Define each library once
log4j-slf4j2-impl = { module = "org.apache.logging.log4j:log4j-slf4j2-impl", version.ref = "log4j" }
log4j-core = { module = "org.apache.logging.log4j:log4j-core", version.ref = "log4j" }
log4j-api = { module = "org.apache.logging.log4j:log4j-api", version.ref = "log4j" }

[bundles]
# Group related dependencies
logging = ["log4j-slf4j2-impl", "log4j-core", "log4j-api"]
```

### Step 2: Use in `build.gradle` with clean references

```groovy
dependencies {
    // ✅ Clean, readable, type-safe!
    implementation libs.bundles.logging  // All 3 Log4j libraries at once!
    
    // ✅ IDE autocomplete: type "libs." and see all available dependencies
    implementation libs.google.guava
    implementation libs.google.gson
    
    // ✅ Update Log4j? Change ONE number in libs.versions.toml:
    //    log4j = "2.23.0"
    //    All 3 libraries update automatically!
}
```

---

## 🎯 Real Examples from Your Project

### Example 1: Apache Commons (5 dependencies)

**Before (current):**
```groovy
implementation 'org.apache.commons:commons-lang3:3.14.0'
implementation 'commons-io:commons-io:2.15.1'
implementation 'org.apache.commons:commons-compress:1.26.0'
implementation 'org.apache.httpcomponents:httpclient:4.5.14'
implementation 'org.apache.httpcomponents:httpcore:4.4.16'
```

**After (with catalog):**
```groovy
// Single line adds all 5 libraries!
implementation libs.bundles.apache.commons
```

Or individually with autocomplete:
```groovy
implementation libs.apache.commons.lang3
implementation libs.apache.commons.io
implementation libs.apache.commons.compress
```

---

### Example 2: LWJGL (8 modules + natives)

**Before (current):**
```groovy
def lwjglVersion = '3.3.3'

implementation platform("org.lwjgl:lwjgl-bom:${lwjglVersion}")
implementation 'org.lwjgl:lwjgl'
implementation 'org.lwjgl:lwjgl-glfw'
implementation 'org.lwjgl:lwjgl-opengl'
implementation 'org.lwjgl:lwjgl-openal'
implementation 'org.lwjgl:lwjgl-stb'
implementation 'org.lwjgl:lwjgl-tinyfd'
implementation 'org.lwjgl:lwjgl-freetype'
implementation 'org.lwjgl:lwjgl-jemalloc'

runtimeOnly "org.lwjgl:lwjgl::${lwjglNatives}"
runtimeOnly "org.lwjgl:lwjgl-glfw::${lwjglNatives}"
runtimeOnly "org.lwjgl:lwjgl-opengl::${lwjglNatives}"
runtimeOnly "org.lwjgl:lwjgl-openal::${lwjglNatives}"
runtimeOnly "org.lwjgl:lwjgl-stb::${lwjglNatives}"
runtimeOnly "org.lwjgl:lwjgl-tinyfd::${lwjglNatives}"
runtimeOnly "org.lwjgl:lwjgl-freetype::${lwjglNatives}"
runtimeOnly "org.lwjgl:lwjgl-jemalloc::${lwjglNatives}"
```

**After (with catalog):**
```groovy
// BOM still needed for platform
implementation platform(libs.lwjgl.bom)

// All 8 modules in one line!
implementation libs.bundles.lwjgl

// Natives still need custom handling (platform-specific)
runtimeOnly "org.lwjgl:lwjgl::${lwjglNatives}"
// ... (natives handling stays the same)
```

**To update LWJGL 3.3.3 → 3.4.0:**
- Before: Find and change 2+ places
- After: Change ONE line in `gradle/libs.versions.toml`:
  ```toml
  lwjgl = "3.4.0"  # Done! All references update automatically
  ```

---

### Example 3: Testing Stack (9 dependencies)

**Before (current):**
```groovy
testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.1'
testImplementation 'org.junit.jupiter:junit-jupiter-params:5.10.1'
testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.10.1'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.1'
testImplementation 'org.assertj:assertj-core:3.25.1'
testImplementation 'org.mockito:mockito-core:5.8.0'
testImplementation 'org.mockito:mockito-junit-jupiter:5.8.0'
testImplementation 'org.openjdk.jmh:jmh-core:1.37'
testImplementation 'org.awaitility:awaitility:4.2.0'
```

**After (with catalog):**
```groovy
// All testing dependencies in one bundle!
testImplementation libs.bundles.testing

// Runtime dependencies
testRuntimeOnly libs.junit.engine
testRuntimeOnly libs.junit.platform.launcher

// Performance testing
testImplementation libs.jmh.core
```

---

## 💡 Key Benefits

### 1. **Single Source of Truth**
```toml
# Update Log4j from 2.22.1 to 2.23.0 in ONE place
[versions]
log4j = "2.23.0"  # ← Change here, everywhere updates!
```

All 3 Log4j libraries update automatically.

### 2. **Type-Safe with Autocomplete**
When you type `libs.` in your IDE:
```
libs.
├─ apache.commons.lang3     ← Autocomplete shows all libraries!
├─ apache.commons.io
├─ google.guava
├─ google.gson
├─ lwjgl.glfw
├─ bundles.
│  ├─ logging               ← Bundles group related deps
│  ├─ apache.commons
│  └─ lwjgl
```

**No more typos!** IDE knows what's available.

### 3. **Reusable Across Modules**
When you split into multi-module project:
```
core/build.gradle    ← uses libs.bundles.logging
mods/build.gradle    ← uses libs.bundles.logging
server/build.gradle  ← uses libs.bundles.logging
```

All modules share the same versions automatically!

### 4. **Easy to See What You're Using**
Open `gradle/libs.versions.toml` and see ALL dependencies at a glance:
```toml
[versions]
lwjgl = "3.3.3"           # Graphics library
junit = "5.10.1"          # Testing
log4j = "2.22.1"          # Logging
netty = "4.1.97.Final"    # Networking
```

Want to check what version of Netty you're using? **One glance!**

### 5. **Prevents Version Conflicts**
```toml
# All Log4j libraries MUST use the same version
log4j = "2.22.1"

[libraries]
log4j-core = { version.ref = "log4j" }      # ← Same version
log4j-api = { version.ref = "log4j" }       # ← Same version
log4j-slf4j2-impl = { version.ref = "log4j" } # ← Same version
```

**Impossible** to accidentally use `log4j-core:2.22.1` and `log4j-api:2.23.0`.

---

## 🔧 How to Use the Catalog I Created

I've created `gradle/libs.versions.toml` with:
- ✅ All 60+ dependencies from your project
- ✅ Logical grouping (Mojang, Google, Apache, LWJGL, etc.)
- ✅ Bundles for related dependencies (logging, testing, etc.)
- ✅ Comprehensive comments explaining everything

### To adopt it in your build.gradle:

**Replace this:**
```groovy
implementation 'com.google.guava:guava:32.1.2-jre'
implementation 'com.google.code.gson:gson:2.11.0'
```

**With this:**
```groovy
implementation libs.google.guava
implementation libs.google.gson
```

**Or use bundles:**
```groovy
implementation libs.bundles.logging      // All Log4j + SLF4J
implementation libs.bundles.apache.commons // All Apache Commons
testImplementation libs.bundles.testing  // All test frameworks
```

---

## 📝 Practical Scenarios

### Scenario 1: Upgrade All ASM Libraries
**Before (find 5 separate lines):**
```groovy
implementation 'org.ow2.asm:asm:9.9'
implementation 'org.ow2.asm:asm-analysis:9.9'
implementation 'org.ow2.asm:asm-commons:9.9'
implementation 'org.ow2.asm:asm-tree:9.9'
implementation 'org.ow2.asm:asm-util:9.9'
```

**After (change one number):**
```toml
# In gradle/libs.versions.toml
asm = "9.10"  # ← Done! All 5 update
```

### Scenario 2: Add New Module
When you create `client/build.gradle`:
```groovy
dependencies {
    // Same catalog available immediately!
    implementation libs.bundles.lwjgl
    implementation libs.bundles.logging
}
```

No copy-pasting version numbers between modules.

### Scenario 3: Audit All Versions
```bash
# Just open gradle/libs.versions.toml
# See every dependency version in 1 file
```

vs. searching through 1000+ line `build.gradle`

---

## 🚀 Next Steps

1. **Test the catalog** - Build works with existing `build.gradle`
2. **Gradually migrate** - Replace hardcoded versions with `libs.*` references
3. **Enjoy benefits** - Easier updates, better IDE support, cleaner builds

The catalog is already created and ready to use! It's backward compatible - your current `build.gradle` still works, but you can now optionally use the cleaner syntax.

---

## 📚 Additional Resources

- **Gradle Official Docs**: https://docs.gradle.org/current/userguide/platforms.html
- **Your Catalog**: `gradle/libs.versions.toml` (already created!)
- **IDE Support**: IntelliJ IDEA and VS Code both support version catalogs with full autocomplete

---

**TL;DR:** Version catalogs are like a centralized "dependency phonebook" - instead of remembering version numbers scattered everywhere, you define them once and reference them by name. Update once, change everywhere. Type-safe, IDE-friendly, and makes your build.gradle much cleaner.
