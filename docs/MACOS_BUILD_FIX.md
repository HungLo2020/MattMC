# macOS Build Fix - Cargo Not Found

## Problem

When building the project on macOS, you may encounter this error:

```
> Task :buildRustNative FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':buildRustNative'.
> A problem occurred starting process 'command 'cargo''
```

Even though `cargo`, `rustc`, and `rustup` are installed and available in your PATH.

## Root Cause

On macOS, when Gradle runs as a daemon process, it doesn't inherit the user's shell environment variables (including `PATH`). This means Gradle can't find `cargo` even if it's available in your terminal.

## Solution

The build script now automatically detects the cargo executable by checking common installation locations:

1. First tries `which cargo` (works when Gradle inherits PATH)
2. Falls back to checking these paths:
   - `~/.cargo/bin/cargo` (rustup default on Linux/macOS)
   - `/opt/homebrew/bin/cargo` (Homebrew on Apple Silicon)
   - `/usr/local/bin/cargo` (Homebrew on Intel Mac)
   - `~/.local/bin/cargo` (alternative Linux location)

When cargo is found, you'll see:
```
✓ Found cargo at: /Users/your-username/.cargo/bin/cargo
```

## Installation Verification

If the build still fails, verify Rust is properly installed:

```bash
# Check if cargo is available
which cargo

# Expected output: /Users/your-username/.cargo/bin/cargo
# If nothing is returned, Rust is not installed or not in PATH
```

## Installing Rust on macOS

If Rust is not installed:

```bash
# Install Rust using rustup (recommended)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Follow the prompts, then reload your shell configuration
source "$HOME/.cargo/env"

# Verify installation
cargo --version
```

## Alternative: Add to Shell Profile

If you want to ensure cargo is always in PATH for all processes:

Add to `~/.zshrc` (or `~/.bash_profile` if using bash):
```bash
export PATH="$HOME/.cargo/bin:$PATH"
```

Then reload:
```bash
source ~/.zshrc
```

## Building the Project

After Rust is installed, build the project:

```bash
# Clean build
./gradlew clean build

# Or just build the Rust native library
./gradlew buildRustNative
```

## Troubleshooting

### Cargo is installed but build still fails

1. Check which cargo path is being used:
   ```bash
   ./gradlew buildRustNative 2>&1 | grep "Found cargo"
   ```

2. Ensure the cargo binary is executable:
   ```bash
   ls -l ~/.cargo/bin/cargo
   # Should show: -rwxr-xr-x (executable permission)
   ```

3. Try stopping the Gradle daemon and rebuilding:
   ```bash
   ./gradlew --stop
   ./gradlew clean build
   ```

### Different Rust installation location

If you installed Rust in a non-standard location, you have two options:

1. **Symlink to a standard location:**
   ```bash
   ln -s /your/custom/path/cargo ~/.cargo/bin/cargo
   ```

2. **Add to PATH in your shell profile:**
   ```bash
   export PATH="/your/custom/path:$PATH"
   ```

## Technical Details

The fix is implemented in `build.gradle` via the `findCargoExecutable()` function, which:

1. Tries to locate cargo using `which`
2. Falls back to checking hardcoded common paths
3. Returns the first valid executable found
4. Provides helpful error messages if cargo can't be found

All Rust build tasks (`buildRustNative`, `buildRustLinux`, `buildRustMacOSx64`, etc.) now use this function to find cargo.

## Files Modified

- `build.gradle` - Added `findCargoExecutable()` helper and updated all Rust tasks
- `src/main/rust/README.md` - Added macOS troubleshooting section

## Related Issues

This fix addresses the common "Gradle can't find cargo on macOS" issue that occurs because:
- Gradle daemon doesn't inherit shell environment
- macOS GUI applications don't load `.zshrc` or `.bash_profile`
- The daemon starts before PATH is properly set

The fix makes the build work automatically without requiring users to configure their environment.
