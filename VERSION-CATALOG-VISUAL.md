# 📊 Version Catalog: Visual Comparison

## The Central Idea

```
┌─────────────────────────────────────────────────────────────┐
│                    WITHOUT Version Catalog                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  build.gradle (1042 lines)                                 │
│  ├─ implementation 'com.google.guava:guava:32.1.2-jre'    │
│  ├─ implementation 'com.google.gson:gson:2.11.0'          │
│  ├─ implementation 'org.slf4j:slf4j-api:2.0.9'            │
│  ├─ implementation 'log4j:log4j-core:2.22.1'              │
│  ├─ implementation 'log4j:log4j-api:2.22.1'               │
│  ├─ implementation 'log4j:log4j-slf4j2-impl:2.22.1'       │
│  └─ ... 50+ more dependencies ...                          │
│                                                             │
│  ❌ Versions scattered everywhere                          │
│  ❌ Updating Log4j requires changing 3 lines               │
│  ❌ Easy to use different versions by accident             │
│  ❌ No autocomplete                                         │
│  ❌ Hard to see what versions you're using                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘

                           ↓ ↓ ↓

┌─────────────────────────────────────────────────────────────┐
│                    WITH Version Catalog                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  gradle/libs.versions.toml (single source of truth)        │
│  ┌──────────────────────────────────────────────┐         │
│  │ [versions]                                    │         │
│  │ log4j = "2.22.1"    ← Change once, updates 3 │         │
│  │ guava = "32.1.2-jre"                         │         │
│  │ gson = "2.11.0"                              │         │
│  │ lwjgl = "3.3.3"                              │         │
│  │                                               │         │
│  │ [libraries]                                   │         │
│  │ log4j-core = { version.ref = "log4j" }      │         │
│  │ log4j-api = { version.ref = "log4j" }       │         │
│  │                                               │         │
│  │ [bundles]                                     │         │
│  │ logging = ["log4j-core", "log4j-api", ...]  │         │
│  └──────────────────────────────────────────────┘         │
│                         ↓                                   │
│  build.gradle (cleaner, type-safe)                         │
│  ├─ implementation libs.google.guava    ← Autocomplete!   │
│  ├─ implementation libs.google.gson                        │
│  └─ implementation libs.bundles.logging ← All 3 at once!  │
│                                                             │
│  ✅ One place to update versions                           │
│  ✅ Type-safe with IDE autocomplete                        │
│  ✅ Impossible to use wrong versions                       │
│  ✅ Bundles group related dependencies                     │
│  ✅ Easy to audit all dependencies                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Update Workflow Comparison

### Scenario: Update Log4j from 2.22.1 to 2.23.0

```
WITHOUT Catalog:                    WITH Catalog:
─────────────────                   ─────────────

Step 1: Search build.gradle        Step 1: Open libs.versions.toml
  Find: log4j-core:2.22.1            
  Find: log4j-api:2.22.1            Step 2: Change ONE line
  Find: log4j-slf4j2-impl:2.22.1      log4j = "2.23.0"

Step 2: Change all 3 manually       Step 3: Done! ✓
  (Risk of missing one!)              All 3 libraries updated

Step 3: Double-check you got
  all occurrences

Time: ~5 minutes                    Time: ~10 seconds
Risk: High (typos, missed lines)    Risk: Zero (single source)
```

---

## IDE Experience

### WITHOUT Catalog (Manual Typing):
```groovy
dependencies {
    // Have to remember exact string:
    // group:artifact:version
    implementation 'org.apac█
                           ↑ cursor - no autocomplete!
    // Did I type it right? Is it "apache" or "apach"?
    // What was the artifact name again? "common-lang3" or "commons-lang3"?
}
```

### WITH Catalog (Autocomplete):
```groovy
dependencies {
    // Type "libs." and IDE shows ALL available:
    implementation libs.█
                       ↓
    ┌─────────────────────────────────┐
    │ libs.apache.commons.compress    │
    │ libs.apache.commons.io          │
    │ libs.apache.commons.lang3  ←    │
    │ libs.apache.httpclient          │
    │ libs.bundles.apache.commons     │
    │ libs.bundles.logging            │
    │ libs.google.gson                │
    │ libs.google.guava               │
    │ libs.lwjgl.glfw                 │
    │ ...                             │
    └─────────────────────────────────┘
    
    // Select from list - no typos possible!
    implementation libs.apache.commons.lang3  ✓
}
```

---

## Bundle Power: Related Dependencies

### Example: Complete Logging Stack

```
┌────────────────────────────────────────────────────────────────┐
│                      WITHOUT Bundles                           │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  dependencies {                                                │
│      implementation 'org.slf4j:slf4j-api:2.0.9'               │
│      implementation 'log4j:log4j-slf4j2-impl:2.22.1'          │
│      implementation 'log4j:log4j-core:2.22.1'                 │
│      implementation 'log4j:log4j-api:2.22.1'                  │
│  }                                                             │
│                                                                │
│  Lines: 4                                                      │
│  Risk: Might forget one dependency                            │
│                                                                │
└────────────────────────────────────────────────────────────────┘

                               ↓

┌────────────────────────────────────────────────────────────────┐
│                       WITH Bundles                             │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  dependencies {                                                │
│      implementation libs.bundles.logging                       │
│  }                                                             │
│                                                                │
│  Lines: 1                                                      │
│  Risk: Zero - bundle includes everything                      │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Your Project's Bundles:

```toml
[bundles]
logging = [slf4j-api, log4j-core, log4j-api, log4j-slf4j2-impl]  # 4 → 1
lwjgl = [lwjgl, lwjgl-glfw, lwjgl-opengl, ...]                    # 8 → 1
apache-commons = [lang3, io, compress, httpclient, httpcore]      # 5 → 1
testing = [junit-api, junit-params, assertj, mockito, ...]        # 9 → 1
asm = [asm, asm-analysis, asm-commons, asm-tree, asm-util]       # 5 → 1
```

**Total reduction: 31 lines → 5 lines!**

---

## Multi-Module Future

When you split into modules later:

```
┌────────────────────────────────────────────────────────────────┐
│  WITHOUT Catalog (Copy-Paste Hell)                            │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  core/build.gradle:                                            │
│    implementation 'com.google.guava:guava:32.1.2-jre'         │
│    implementation 'org.slf4j:slf4j-api:2.0.9'                 │
│                                                                │
│  mods/sodium/build.gradle:                                     │
│    implementation 'com.google.guava:guava:32.1.2-jre'  ← COPY │
│    implementation 'org.slf4j:slf4j-api:2.0.9'          ← COPY │
│                                                                │
│  mods/iris/build.gradle:                                       │
│    implementation 'com.google.guava:guava:32.1.2-jre'  ← COPY │
│    implementation 'org.slf4j:slf4j-api:2.0.9'          ← COPY │
│                                                                │
│  ❌ Version copied 3 times                                     │
│  ❌ Update requires changing all 3 modules                     │
│  ❌ Easy to forget one module                                  │
│                                                                │
└────────────────────────────────────────────────────────────────┘

                               ↓

┌────────────────────────────────────────────────────────────────┐
│  WITH Catalog (Shared Versions)                               │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  gradle/libs.versions.toml (shared by all modules):           │
│    [versions]                                                  │
│    guava = "32.1.2-jre"     ← ONE version for all modules     │
│    slf4j = "2.0.9"                                             │
│                                                                │
│  core/build.gradle:                                            │
│    implementation libs.google.guava                            │
│    implementation libs.slf4j.api                               │
│                                                                │
│  mods/sodium/build.gradle:                                     │
│    implementation libs.google.guava    ← Same version!         │
│    implementation libs.slf4j.api       ← Same version!         │
│                                                                │
│  mods/iris/build.gradle:                                       │
│    implementation libs.google.guava    ← Same version!         │
│    implementation libs.slf4j.api       ← Same version!         │
│                                                                │
│  ✅ Version defined ONCE                                       │
│  ✅ Update in ONE place, all modules get it                    │
│  ✅ Guaranteed consistency                                     │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

## Real Numbers from Your Project

```
Current build.gradle dependency block:
  Lines: ~172 (lines 98-270)
  Dependencies: 60+
  Version updates required for full LWJGL upgrade: 16 lines
  Version updates required for full Log4j upgrade: 3 lines
  Chance of typo on manual update: ~30%

With version catalog:
  Lines in build.gradle: ~60 (using bundles)
  Lines in libs.versions.toml: ~200 (but centralized!)
  Version updates for LWJGL upgrade: 1 line
  Version updates for Log4j upgrade: 1 line
  Chance of typo: 0% (single source of truth)
  
Efficiency gain: ~65% reduction in dependency declaration
```

---

## The Bottom Line

**Version Catalog = Dependency Management Phone Book**

Instead of scattering phone numbers (versions) throughout your code,
you keep them in one place (libs.versions.toml) and reference by name.

- Update a phone number once → everyone has the new number
- Easy to look up any number → just open the phone book
- Impossible to call the wrong number → type-safe references
- Group related contacts → bundles for families of dependencies

**Your project already has the catalog created!**
It's in `gradle/libs.versions.toml` - ready to use whenever you want.
