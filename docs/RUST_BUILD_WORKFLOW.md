# Rust Build and Release Workflow

This repository includes a GitHub Actions workflow for building and releasing the MattMC Rust/Vulkan project.

## Overview

The workflow builds the Rust project for three platforms:
- **Linux x86_64** (`x86_64-unknown-linux-gnu`)
- **Windows x86_64** (`x86_64-pc-windows-msvc`)
- **macOS Apple Silicon** (`aarch64-apple-darwin`)

## How to Use

### Manually Triggering a Build

1. Go to the **Actions** tab in the GitHub repository
2. Select the **"Build and Release Rust Project"** workflow from the left sidebar
3. Click the **"Run workflow"** button
4. Choose whether to create a GitHub release (default: yes)
5. Click **"Run workflow"** to start the build

### Release Creation

When the workflow completes successfully, it will:

1. **Extract the version** from `Cargo.toml` (e.g., `0.1.0`)
2. **Generate a timestamp** in the format `YYYY-MM-DD-HHMM` (e.g., `2024-02-02-1430`)
3. **Create a release tag** combining both: `v{VERSION}-{TIMESTAMP}` (e.g., `v0.1.0-2024-02-02-1430`)
4. **Upload binaries** for all three platforms as release assets

This naming scheme ensures that:
- Multiple releases can be created on the same day
- Multiple releases can be created for the same version
- Releases are sortable by date and time

### Release Assets

Each release includes three binary files:
- `mattmc-rust-linux-x86_64` - Linux executable
- `mattmc-rust-windows-x86_64.exe` - Windows executable
- `mattmc-rust-macos-aarch64` - macOS Apple Silicon executable

### Updating the Version

To change the version number for future releases:

1. Edit `Cargo.toml` in the repository root
2. Update the `version` field under `[package]`
3. Commit and push the change
4. The next workflow run will use the updated version

## Workflow Details

### Build Process

The workflow:
1. Checks out the repository
2. Installs the Rust toolchain with the appropriate target
3. Installs platform-specific dependencies (e.g., Vulkan libraries on Linux)
4. Caches cargo registry, git, and target directories for faster builds
5. Builds the project in release mode with optimizations
6. Uploads the compiled binaries as artifacts

### Dependencies

The workflow automatically installs required dependencies:

- **Linux**: `libvulkan-dev`, `vulkan-tools`, `libxkbcommon-dev`, `libwayland-dev`
- **Windows**: No additional dependencies (uses MSVC toolchain)
- **macOS**: No additional dependencies (MoltenVK included in macOS SDK)

### Caching

The workflow uses GitHub Actions caching to speed up builds:
- Cargo registry cache
- Cargo git cache
- Target directory cache

This significantly reduces build times for subsequent runs.

## Troubleshooting

### Build Failures

If a build fails for a specific platform:
1. Check the workflow logs in the Actions tab
2. Look for platform-specific errors
3. Ensure all dependencies are correctly specified in `Cargo.toml`

### Missing Binaries

If binaries are not uploaded:
1. Verify the binary name in `Cargo.toml` matches `mattmc-rust`
2. Check that the build completed successfully
3. Review the "Prepare binary for upload" step logs

### Release Creation Issues

If the release is not created:
1. Ensure the workflow has write permissions (Settings → Actions → General → Workflow permissions)
2. Check that `create_release` input is set to `true`
3. Verify the version format in `Cargo.toml` is correct (e.g., `version = "0.1.0"`)

## Advanced Usage

### Building Without Creating a Release

You can run the workflow without creating a release:
1. When triggering the workflow, uncheck "Create a GitHub release"
2. The binaries will still be built and available as workflow artifacts
3. Download artifacts from the workflow run page (valid for 90 days)

### Modifying Build Targets

To add or modify build targets, edit `.github/workflows/rust-release.yml`:
1. Locate the `matrix.platform` section
2. Add or modify platform configurations
3. Ensure the target is supported by Rust and the required dependencies are available

## Requirements for End Users

Users downloading and running the binaries will need:

### Linux
- Vulkan-compatible GPU and drivers
- Required libraries: `libvulkan1`, `libxkbcommon0`, `libwayland-client0`
- Install with: `sudo apt-get install libvulkan1 libxkbcommon0 libwayland-client0`

### Windows
- Vulkan-compatible GPU
- Latest GPU drivers with Vulkan support
- Download from GPU manufacturer (NVIDIA, AMD, Intel)

### macOS
- Apple Silicon Mac
- macOS with MoltenVK support (included in recent versions)
- May require granting execution permissions: `chmod +x mattmc-rust-macos-aarch64`
