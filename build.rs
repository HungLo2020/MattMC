fn main() {
    // Set up shader compilation environment
    // This build script ensures shaderc can find necessary tools
    
    println!("cargo:rerun-if-changed=src/main/rust/client/renderer/shaders.rs");
    
    // On Windows, check for Vulkan SDK
    #[cfg(target_os = "windows")]
    {
        if let Ok(vulkan_sdk) = std::env::var("VULKAN_SDK") {
            println!("cargo:rustc-env=VULKAN_SDK={}", vulkan_sdk);
            println!("cargo:warning=Using Vulkan SDK from: {}", vulkan_sdk);
        } else {
            println!("cargo:warning=VULKAN_SDK environment variable not set.");
            println!("cargo:warning=If build fails, install Vulkan SDK from https://vulkan.lunarg.com/sdk/home");
        }
    }
    
    // Provide helpful error messages if shaderc dependency fails
    println!("cargo:warning=Compiling shaders at build time using vulkano-shaders");
}
