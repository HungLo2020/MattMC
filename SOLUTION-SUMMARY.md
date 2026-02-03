# Solution Summary: cmake Build Requirement

## The Problem

Users building the Rust portion of MattMC on Windows (and other platforms) encounter:

```
error: failed to run custom build command for `shaderc-sys v0.8.3`
...
couldn't find required command: "cmake"
```

## Why This Happens

1. The Rust project uses **compile-time shader compilation** via `vulkano_shaders::shader!` macro
2. This depends on `shaderc` → `shaderc-sys`
3. `shaderc-sys` looks for system shaderc libraries
4. If not found, it falls back to **building from source**
5. Building from source requires **cmake**
6. If cmake isn't installed → **BUILD FAILS**

## Why We Can't "Fix" This Automatically

**Technical Limitation:** Cargo builds dependencies BEFORE the main crate.
- Our `build.rs` runs after `shaderc-sys` has already tried to build
- We can't intercept or prevent the error
- We can only show helpful messages AFTER dependencies succeed

**This is a fundamental Cargo limitation**, not something we can work around.

## Our Solution: Make Requirements Crystal Clear

Since we can't prevent the error, we make it **impossible to miss the requirements**:

### 1. Prominent Warning in README.md

Added a ⚠️ **IMPORTANT: Before Building** section at the top of the Rust documentation with:
- Platform-specific quick install commands
- Links to detailed guides
- Explanation of why it's needed

### 2. Comprehensive Error Guide (ERROR-CMAKE.md)

Created a dedicated quick-fix guide that shows up when users search for the error:
- Quick copy-paste commands for each platform
- Two options: Vulkan SDK (recommended) or cmake
- Step-by-step instructions

### 3. Detailed Platform Guides

- **WINDOWS_BUILD.md**: Complete Windows setup guide with screenshots
- **CMAKE_SOLUTION.md**: Technical background and troubleshooting

### 4. Simplified build.rs

Removed the early-failure logic (didn't work anyway) and replaced with:
- Success messages when build works
- Cleaner, more maintainable code
- Comments explaining the limitation

## What Users See Now

### Scenario 1: Read README First (Ideal)
1. See ⚠️ WARNING about cmake requirement
2. Install Vulkan SDK or cmake
3. Restart terminal
4. Build succeeds ✓

### Scenario 2: Build Without Reading (Reality)
1. Run `cargo build`
2. See shaderc-sys cmake error
3. Search for "couldn't find required command cmake mattmc"
4. Find ERROR-CMAKE.md
5. Install Vulkan SDK or cmake
6. Run `cargo clean && cargo build`
7. Build succeeds ✓

## Documentation Hierarchy

```
README.md
├─ ⚠️ WARNING: Install deps before building
├─ Quick install commands per platform
└─ Link to WINDOWS_BUILD.md

ERROR-CMAKE.md
├─ Quick fix for the error
├─ Platform-specific commands
└─ Links to detailed guides

WINDOWS_BUILD.md
├─ Step-by-step Windows setup
├─ Two options: Vulkan SDK or cmake
├─ Troubleshooting section
└─ All download links

CMAKE_SOLUTION.md
├─ Complete technical explanation
├─ Why the requirement exists
└─ Before/after comparison
```

## What We CANNOT Do

❌ Make cmake optional (shader compilation requires it)
❌ Bundle cmake with the repo (licensing/size issues)
❌ Fail build.rs early (runs after dependencies)
❌ Auto-install cmake (security/permissions issues)
❌ Use pre-compiled shaders (rejected in favor of compile-time)

## What We DID Do

✅ Make requirements **impossible to miss**
✅ Provide **quick-fix guides** for all platforms
✅ Offer **two options**: Vulkan SDK (easier) or cmake
✅ Create **searchable error documentation**
✅ Link to **detailed setup guides**
✅ Explain **why it's needed**

## Installation Options

### Option 1: Vulkan SDK (Recommended)

**Pros:**
- Includes pre-built shaderc libraries
- No compilation needed
- Faster builds
- Useful for Vulkan development anyway

**Cons:**
- Larger download (~200-500 MB)

### Option 2: cmake

**Pros:**
- Smaller download
- More targeted solution

**Cons:**
- First build takes 1-2 minutes (compiling shaderc from source)
- Requires C++ compiler

## Success Metrics

Users now have:
1. **Multiple touchpoints** for learning about the requirement
2. **Clear, actionable** installation instructions
3. **Platform-specific** guidance
4. **Quick recovery** path if they build without installing

## Future Considerations

If the compile-time requirement becomes too problematic, we could:
1. Switch to runtime shader compilation (different trade-offs)
2. Provide pre-built binaries with compiled shaders
3. Add GitHub Actions to build for users

But for now, the current approach (compile-time with clear documentation) is the best balance.

## Bottom Line

**We can't prevent the error, but we've made the solution obvious and easy to find.**

Users who read the README will install dependencies first. Users who don't will see a clear error with an immediate solution.
