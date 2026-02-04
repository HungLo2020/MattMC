package net.vulkanic.examples;

import net.vulkanic.BackendType;
import net.vulkanic.Vulkanic;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicCommandBuffer;
import net.vulkanic.VulkanicDevice;
import net.vulkanic.VulkanicFramebuffer;
import net.vulkanic.VulkanicShader;
import net.vulkanic.VulkanicTexture;

/**
 * Example demonstrating basic Vulkanic API usage.
 * 
 * This class shows how game code should interact with the Vulkanic rendering API.
 * Note: This is a conceptual example - the actual implementation will be completed
 * in future milestones.
 */
public class BasicRenderingExample {
    
    /**
     * Example: Initialize Vulkanic and get device information.
     */
    public static void exampleInitialization() {
        // Initialize with default backend (OpenGL)
        Vulkanic.initialize();
        
        // Or specify a backend explicitly
        // Vulkanic.initialize(BackendType.VULKAN);  // Future: when Vulkan is implemented
        
        // Get the device
        VulkanicDevice device = Vulkanic.getDevice();
        
        // Query device capabilities
        System.out.println("Backend: " + device.getBackendName());
        System.out.println("Vendor: " + device.getVendor());
        System.out.println("Renderer: " + device.getRenderer());
        System.out.println("Max Texture Size: " + device.getMaxTextureSize());
        System.out.println("Backend Type: " + device.getBackendType().getDisplayName());
    }
    
    /**
     * Example: Create rendering resources.
     */
    public static void exampleResourceCreation() {
        VulkanicDevice device = Vulkanic.getDevice();
        
        // Create a shader program
        String vertexShader = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            void main() {
                gl_Position = vec4(aPos, 1.0);
            }
            """;
        
        String fragmentShader = """
            #version 330 core
            out vec4 FragColor;
            void main() {
                FragColor = vec4(1.0, 0.5, 0.2, 1.0);
            }
            """;
        
        VulkanicShader shader = device.createShader(vertexShader, fragmentShader);
        
        // Create a vertex buffer (1024 bytes)
        VulkanicBuffer vertexBuffer = device.createBuffer(1024);
        
        // Create a texture (512x512)
        VulkanicTexture texture = device.createTexture(512, 512);
        
        // Create a framebuffer (800x600)
        VulkanicFramebuffer framebuffer = device.createFramebuffer(800, 600);
        
        // Clean up when done
        shader.close();
        vertexBuffer.close();
        texture.close();
        framebuffer.close();
    }
    
    /**
     * Example: Record and execute rendering commands.
     */
    public static void exampleRendering() {
        VulkanicDevice device = Vulkanic.getDevice();
        
        // Create resources
        VulkanicShader shader = device.createShader("...", "...");
        VulkanicBuffer vertexBuffer = device.createBuffer(1024);
        VulkanicFramebuffer framebuffer = device.createFramebuffer(800, 600);
        
        // Create a command buffer
        VulkanicCommandBuffer cmd = device.createCommandBuffer();
        
        // Begin rendering to framebuffer
        cmd.beginRenderPass(framebuffer);
        
        // Set viewport
        cmd.setViewport(0, 0, 800, 600);
        
        // Clear the screen
        cmd.clear(0.0f, 0.0f, 0.0f, 1.0f);
        
        // Bind shader and vertex buffer
        cmd.bindShader(shader);
        cmd.bindVertexBuffer(vertexBuffer);
        
        // Draw
        cmd.draw(3); // Draw 3 vertices (a triangle)
        
        // End rendering
        cmd.endRenderPass();
        
        // Submit commands for execution
        cmd.submit();
        
        // Clean up
        shader.close();
        vertexBuffer.close();
        framebuffer.close();
    }
    
    /**
     * Example: Complete lifecycle from initialization to shutdown.
     */
    public static void exampleCompleteLifecycle() {
        // Initialize
        Vulkanic.initialize(BackendType.OPENGL);
        
        try {
            VulkanicDevice device = Vulkanic.getDevice();
            
            // Do rendering work...
            System.out.println("Rendering with " + device.getBackendName());
            
            // Create and use resources...
            exampleRendering();
            
        } finally {
            // Always shut down
            Vulkanic.shutdown();
        }
    }
    
    /**
     * Example: Wrong usage - this will throw exceptions!
     */
    public static void exampleWrongUsage() {
        try {
            // Wrong: Getting device before initialization
            VulkanicDevice device = Vulkanic.getDevice();
            // This throws IllegalStateException
        } catch (IllegalStateException e) {
            System.err.println("Error: " + e.getMessage());
        }
        
        try {
            // Wrong: Double initialization
            Vulkanic.initialize();
            Vulkanic.initialize(); // This throws IllegalStateException
        } catch (IllegalStateException e) {
            System.err.println("Error: " + e.getMessage());
        }
        
        try {
            // Wrong: Using unsupported backend
            Vulkanic.initialize(BackendType.VULKAN);
            // This throws UnsupportedOperationException (until Vulkan is implemented)
        } catch (UnsupportedOperationException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    /**
     * Example: Checking if Vulkanic is initialized.
     */
    public static void exampleCheckInitialization() {
        if (!Vulkanic.isInitialized()) {
            System.out.println("Vulkanic not initialized yet");
            Vulkanic.initialize();
        }
        
        VulkanicDevice device = Vulkanic.getDevice();
        System.out.println("Using: " + device.getBackendName());
        
        BackendType currentBackend = Vulkanic.getCurrentBackend();
        System.out.println("Current backend: " + currentBackend.getDisplayName());
    }
}
