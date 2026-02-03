# Build Error Solution - cmake Requirement

## The Problem

Users on Windows (and other platforms without cmake) encountered this error when building:

```
error: failed to run custom build command for `shaderc-sys v0.8.3`
Caused by:
  thread 'main' panicked at ...:
  couldn't find required command: "cmake"
```

This happens because:
1. The Rust project uses compile-time shader compilation (`vulkano_shaders::shader!` macro)
2. This depends on `shaderc` → `shaderc-sys`
3. `shaderc-sys` tries to find system shaderc libraries
4. If not found, it falls back to building from source
5. Building from source requires `cmake`

## The Solution

We've implemented a **proactive detection and guidance system** that helps users fix the issue before the build fails.

### What We Added

1. **Enhanced build.rs Script**
   - Detects cmake availability at build time
   - Provides platform-specific installation guidance
   - Shows clear success indicators when dependencies are met

2. **Comprehensive Windows Build Guide** (WINDOWS_BUILD.md)
   - Step-by-step installation instructions
   - Two clear options: Vulkan SDK (recommended) or cmake
   - Troubleshooting for common issues
   - Direct links to all downloads

3. **Updated Documentation**
   - README.md now links to platform-specific guides
   - Clear prerequisites listed for each platform
   - Vulkan SDK recommended as the easiest solution

### How It Works

**When cmake IS available:**
```bash
$ cargo build
warning: mattmc-rust@0.1.0: ✓ cmake found
warning: mattmc-rust@0.1.0: Compiling shaders at build time using vulkano-shaders
   Compiling shaderc-sys v0.8.3
   ...
    Finished `dev` profile [optimized + debuginfo] target(s) in 1m 28s
```

**When cmake is NOT available (Windows):**
```bash
$ cargo build
warning: mattmc-rust@0.1.0:
warning: mattmc-rust@0.1.0: ════════════════════════════════════════════════════════════
warning: mattmc-rust@0.1.0:  BUILD REQUIREMENTS NOT MET
warning: mattmc-rust@0.1.0: ════════════════════════════════════════════════════════════
warning: mattmc-rust@0.1.0:
warning: mattmc-rust@0.1.0: The Rust build requires shader compilation at build time.
warning: mattmc-rust@0.1.0: This needs either:
warning: mattmc-rust@0.1.0:
warning: mattmc-rust@0.1.0: OPTION 1 (Recommended): Install Vulkan SDK
warning: mattmc-rust@0.1.0:   • Download from: https://vulkan.lunarg.com/sdk/home
warning: mattmc-rust@0.1.0:   • The SDK includes pre-built shaderc libraries
warning: mattmc-rust@0.1.0:   • After install, restart your terminal/IDE
warning: mattmc-rust@0.1.0:
warning: mattmc-rust@0.1.0: OPTION 2: Install cmake
warning: mattmc-rust@0.1.0:   • Download from: https://cmake.org/download/
warning: mattmc-rust@0.1.0:   • Or use: choco install cmake (if you have Chocolatey)
warning: mattmc-rust@0.1.0:   • Add cmake to your PATH
warning: mattmc-rust@0.1.0:   • Restart your terminal/IDE after installation
warning: mattmc-rust@0.1.0:
warning: mattmc-rust@0.1.0: After installing, run: cargo clean && cargo build
warning: mattmc-rust@0.1.0:
warning: mattmc-rust@0.1.0: See WINDOWS_BUILD.md for detailed instructions.
warning: mattmc-rust@0.1.0: ════════════════════════════════════════════════════════════

   Compiling shaderc-sys v0.8.3
error: failed to run custom build command for `shaderc-sys v0.8.3`
...
couldn't find required command: "cmake"
```

Users now see **clear guidance BEFORE the cryptic error**, telling them exactly what to do.

### Why This Approach?

We can't eliminate the cmake requirement because:
- Compile-time shader compilation is a core requirement
- `vulkano_shaders::shader!` macro provides type safety and validation
- Pre-compiled shaders were rejected in favor of compile-time compilation
- Shaders must be in `src/main/rust/` (achieved via inline GLSL)

Instead, we make the requirement **crystal clear** and provide **easy solutions**:
1. Install Vulkan SDK (easiest - includes pre-built shaderc)
2. Install cmake (allows building shaderc from source)

## User Experience Improvements

### Before This Fix
❌ Cryptic cmake error after long build time
❌ No guidance on what to install
❌ No indication of where to get it
❌ Platform-specific differences unclear

### After This Fix
✅ Proactive detection at start of build
✅ Clear, actionable installation instructions
✅ Platform-specific guidance (Windows/macOS/Linux)
✅ Links to all required downloads
✅ Comprehensive troubleshooting guide
✅ Success indicators when properly configured

## For Developers

If you're setting up a new Windows development machine:

1. **Install Rust**
   ```powershell
   # Download from https://rustup.rs/
   rustup install stable
   ```

2. **Install Vulkan SDK** (Recommended)
   ```
   Download from: https://vulkan.lunarg.com/sdk/home
   Run installer with default options
   Restart terminal
   ```

3. **Build**
   ```powershell
   cargo build
   ```

That's it! The Vulkan SDK includes everything needed for shader compilation.

## Alternative: cmake Only

If you prefer to install just cmake:

1. Download from https://cmake.org/download/
2. During install, select "Add CMake to system PATH"
3. Restart terminal
4. `cargo build`

cmake allows shaderc-sys to build from source (slower first build, but works).

## Technical Details

**build.rs** runs before the main crate build and:
1. Checks if `cmake --version` succeeds
2. Checks for `VULKAN_SDK` environment variable
3. Prints appropriate warnings based on findings
4. Does NOT fail the build (lets shaderc-sys fail with its error)

This means users see:
1. Our helpful warning first
2. Then the shaderc-sys error (if cmake missing)

The double messaging ensures users understand both:
- What's wrong (our message)
- Where it failed (shaderc-sys message)

## Files Changed

- **build.rs**: cmake detection and platform-specific warnings
- **WINDOWS_BUILD.md**: Comprehensive Windows setup guide
- **README.md**: Links to platform guides, clear prerequisites
- **COMPILE_TIME_SHADERS.md**: Already documented the approach
- **src/main/rust/SHADERS.md**: Technical shader documentation

## Testing

Tested on:
- ✅ Linux with cmake (builds successfully)
- ✅ Linux without cmake (shows helpful error message)
- ✅ Incremental builds (fast, warnings still show)
- ✅ Clean builds (warnings show early)

## Conclusion

While we can't eliminate the cmake requirement, we've made it:
- **Obvious**: Detected and reported early
- **Actionable**: Clear installation instructions
- **Platform-aware**: Specific guidance for Windows/macOS/Linux
- **User-friendly**: Links, options, and troubleshooting included

Users who encounter this error now have a clear path to resolution rather than being stuck with a cryptic error message.
