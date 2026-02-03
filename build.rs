use std::process::Command;

fn main() {
    // Set up shader compilation environment
    println!("cargo:rerun-if-changed=src/main/rust/client/renderer/shaders.rs");
    
    // Check for cmake availability (required by shaderc-sys if building from source)
    let cmake_available = Command::new("cmake")
        .arg("--version")
        .output()
        .is_ok();
    
    // Platform-specific guidance
    if cfg!(target_os = "windows") {
        // Windows-specific checks
        if let Ok(vulkan_sdk) = std::env::var("VULKAN_SDK") {
            println!("cargo:rustc-env=VULKAN_SDK={}", vulkan_sdk);
            println!("cargo:warning=✓ Using Vulkan SDK from: {}", vulkan_sdk);
        } else if !cmake_available {
            println!("cargo:warning=");
            println!("cargo:warning=════════════════════════════════════════════════════════════");
            println!("cargo:warning= BUILD REQUIREMENTS NOT MET");
            println!("cargo:warning=════════════════════════════════════════════════════════════");
            println!("cargo:warning=");
            println!("cargo:warning=The Rust build requires shader compilation at build time.");
            println!("cargo:warning=This needs either:");
            println!("cargo:warning=");
            println!("cargo:warning=OPTION 1 (Recommended): Install Vulkan SDK");
            println!("cargo:warning=  • Download from: https://vulkan.lunarg.com/sdk/home");
            println!("cargo:warning=  • The SDK includes pre-built shaderc libraries");
            println!("cargo:warning=  • After install, restart your terminal/IDE");
            println!("cargo:warning=");
            println!("cargo:warning=OPTION 2: Install cmake");
            println!("cargo:warning=  • Download from: https://cmake.org/download/");
            println!("cargo:warning=  • Or use: choco install cmake (if you have Chocolatey)");
            println!("cargo:warning=  • Add cmake to your PATH");
            println!("cargo:warning=  • Restart your terminal/IDE after installation");
            println!("cargo:warning=");
            println!("cargo:warning=After installing, run: cargo clean && cargo build");
            println!("cargo:warning=");
            println!("cargo:warning=See WINDOWS_BUILD.md for detailed instructions.");
            println!("cargo:warning=════════════════════════════════════════════════════════════");
        } else {
            println!("cargo:warning=✓ cmake found - shaderc can build from source if needed");
        }
    } else if cfg!(target_os = "macos") {
        // macOS-specific checks
        if !cmake_available {
            println!("cargo:warning=");
            println!("cargo:warning=════════════════════════════════════════════════════════════");
            println!("cargo:warning= CMAKE NOT FOUND");
            println!("cargo:warning=════════════════════════════════════════════════════════════");
            println!("cargo:warning=");
            println!("cargo:warning=Install cmake or Vulkan SDK:");
            println!("cargo:warning=  brew install cmake");
            println!("cargo:warning=  OR");
            println!("cargo:warning=  Download Vulkan SDK: https://vulkan.lunarg.com/sdk/home");
            println!("cargo:warning=");
            println!("cargo:warning=════════════════════════════════════════════════════════════");
        } else {
            println!("cargo:warning=✓ cmake found");
        }
    } else {
        // Linux and other Unix-like systems
        if !cmake_available {
            println!("cargo:warning=");
            println!("cargo:warning=════════════════════════════════════════════════════════════");
            println!("cargo:warning= CMAKE NOT FOUND");
            println!("cargo:warning=════════════════════════════════════════════════════════════");
            println!("cargo:warning=");
            println!("cargo:warning=Install cmake or libshaderc-dev:");
            println!("cargo:warning=  sudo apt install cmake");
            println!("cargo:warning=  OR");
            println!("cargo:warning=  sudo apt install libshaderc-dev");
            println!("cargo:warning=");
            println!("cargo:warning=════════════════════════════════════════════════════════════");
        } else {
            println!("cargo:warning=✓ cmake found");
        }
    }
    
    println!("cargo:warning=Compiling shaders at build time using vulkano-shaders");
}
