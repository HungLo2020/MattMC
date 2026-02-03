use std::process::Command;

fn main() {
    // NOTE: This build script runs AFTER dependencies are built.
    // If shaderc-sys fails due to missing cmake, this won't run.
    // However, it provides helpful messages when the build succeeds.
    
    println!("cargo:rerun-if-changed=src/main/rust/client/renderer/shaders.rs");
    println!("cargo:rerun-if-changed=src/main/rust/shaders/vertex.glsl");
    println!("cargo:rerun-if-changed=src/main/rust/shaders/fragment.glsl");
    
    // Check for cmake availability (required by shaderc-sys if building from source)
    let cmake_available = Command::new("cmake")
        .arg("--version")
        .output()
        .is_ok();
    
    // Check if Vulkan SDK is installed
    let vulkan_sdk_available = std::env::var("VULKAN_SDK").is_ok();
    
    // Platform-specific success messages
    if cfg!(target_os = "windows") {
        if let Ok(vulkan_sdk) = std::env::var("VULKAN_SDK") {
            println!("cargo:rustc-env=VULKAN_SDK={}", vulkan_sdk);
            println!("cargo:warning=✓ Using Vulkan SDK from: {}", vulkan_sdk);
        } else if cmake_available {
            println!("cargo:warning=✓ cmake found - using for shader compilation");
        }
    } else if cmake_available || vulkan_sdk_available {
        println!("cargo:warning=✓ Build dependencies satisfied");
    }
    
    println!("cargo:warning=Compiling shaders at build time using vulkano-shaders");
}
