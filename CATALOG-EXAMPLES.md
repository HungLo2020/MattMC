# Example: Using the Version Catalog

This file shows practical examples of how to migrate from hardcoded versions to the version catalog.

## Example 1: Simple Migration

### Before (in build.gradle):
```groovy
dependencies {
    implementation 'com.google.guava:guava:32.1.2-jre'
    implementation 'com.google.code.gson:gson:2.11.0'
}
```

### After (using catalog):
```groovy
dependencies {
    implementation libs.google.guava
    implementation libs.google.gson
}
```

## Example 2: Using Bundles

### Before (in build.gradle):
```groovy
dependencies {
    implementation 'org.slf4j:slf4j-api:2.0.9'
    implementation 'org.apache.logging.log4j:log4j-slf4j2-impl:2.22.1'
    implementation 'org.apache.logging.log4j:log4j-core:2.22.1'
    implementation 'org.apache.logging.log4j:log4j-api:2.22.1'
}
```

### After (using bundle - ONE line):
```groovy
dependencies {
    implementation libs.bundles.logging
}
```

## Example 3: Accessing Versions Directly

### Get version number in build script:
```groovy
// Access version from catalog
def lwjglVersion = libs.versions.lwjgl.get()
println "Using LWJGL version: ${lwjglVersion}"

// Use in dynamic string
runtimeOnly "org.lwjgl:lwjgl::${lwjglVersion}"
```

## Example 4: Full Real-World Migration

### Before (current state - lines 98-130 of build.gradle):
```groovy
dependencies {
    // Mojang libraries
    implementation 'com.mojang:brigadier:1.3.10'
    implementation 'com.mojang:datafixerupper:8.0.16'
    implementation 'com.mojang:jtracy:1.0.29'
    
    // Google libraries
    implementation 'com.google.guava:guava:32.1.2-jre'
    implementation 'com.google.code.gson:gson:2.11.0'
    
    // Apache Commons
    implementation 'org.apache.commons:commons-lang3:3.14.0'
    implementation 'commons-io:commons-io:2.15.1'
    implementation 'org.apache.commons:commons-compress:1.26.0'
    implementation 'org.apache.httpcomponents:httpclient:4.5.14'
    implementation 'org.apache.httpcomponents:httpcore:4.4.16'
    
    // Logging
    implementation 'org.slf4j:slf4j-api:2.0.9'
    implementation 'org.apache.logging.log4j:log4j-slf4j2-impl:2.22.1'
    implementation 'org.apache.logging.log4j:log4j-core:2.22.1'
    implementation 'org.apache.logging.log4j:log4j-api:2.22.1'
    
    // Networking
    implementation 'io.netty:netty-all:4.1.97.Final'
    
    // More dependencies...
}
```

### After (using catalog - much cleaner):
```groovy
dependencies {
    // Mojang libraries
    implementation libs.mojang.brigadier
    implementation libs.mojang.datafixerupper
    implementation libs.mojang.jtracy
    
    // Google libraries
    implementation libs.google.guava
    implementation libs.google.gson
    
    // Apache Commons - use bundle for all 5 libraries!
    implementation libs.bundles.apache.commons
    
    // Logging - use bundle for complete stack!
    implementation libs.bundles.logging
    
    // Networking
    implementation libs.netty.all
    
    // More dependencies...
}
```

**Notice:**
- Apache Commons: 5 lines → 1 line (using bundle)
- Logging: 4 lines → 1 line (using bundle)
- Everything else: cleaner, more readable
- IDE autocomplete works: type `libs.` and see all options!

## Example 5: How to Update Versions

### Scenario: Update Log4j from 2.22.1 to 2.23.0

**Step 1:** Open `gradle/libs.versions.toml`

**Step 2:** Find the version:
```toml
[versions]
log4j = "2.22.1"  # ← Change this line
```

**Step 3:** Update it:
```toml
[versions]
log4j = "2.23.0"  # ← Done!
```

**That's it!** All 3 Log4j libraries (log4j-core, log4j-api, log4j-slf4j2-impl) now use 2.23.0.

### Before (without catalog):
You'd have to find and change:
```groovy
implementation 'org.apache.logging.log4j:log4j-slf4j2-impl:2.22.1'  # ← change here
implementation 'org.apache.logging.log4j:log4j-core:2.22.1'         # ← and here
implementation 'org.apache.logging.log4j:log4j-api:2.22.1'          # ← and here
```

Three separate lines! Easy to miss one or make a typo.

## Example 6: Conditional Logic with Catalog

You can still use bundled JARs when available:

```groovy
dependencies {
    if (useBundledDeps) {
        // Use local files
        implementation files('libraries/deps/brigadier-1.3.10.jar')
    } else {
        // Use catalog reference
        implementation libs.mojang.brigadier
    }
}
```

## Example 7: Platform/BOM with Catalog

LWJGL uses a BOM (Bill of Materials):

```groovy
dependencies {
    // Import the BOM from catalog
    implementation platform(libs.lwjgl.bom)
    
    // Then use the bundle for all modules
    implementation libs.bundles.lwjgl
    
    // Natives still need manual handling (platform-specific)
    def lwjglVersion = libs.versions.lwjgl.get()
    runtimeOnly "org.lwjgl:lwjgl::${lwjglNatives}"
    runtimeOnly "org.lwjgl:lwjgl-glfw::${lwjglNatives}"
    // etc...
}
```

## Testing the Catalog

The version catalog is already active! Try it:

```bash
# See all available dependencies from catalog
./gradlew dependencies --configuration runtimeClasspath

# Build with catalog (still works exactly as before)
./gradlew build
```

## Benefits Summary

1. **Update once, change everywhere** - Change version in ONE place
2. **Type-safe** - IDE autocomplete shows available libraries
3. **No typos** - IDE knows what exists
4. **Bundle related deps** - One line for entire logging stack
5. **Easy auditing** - See all versions in one file
6. **Future-proof** - Ready for multi-module projects

## Learn More

- **Official Docs**: https://docs.gradle.org/current/userguide/platforms.html
- **Your Catalog**: `gradle/libs.versions.toml`
- **This Demo**: `VERSION-CATALOG-DEMO.md`
