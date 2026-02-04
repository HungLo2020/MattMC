package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLDevice;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Vulkanic rendering abstraction layer.
 * 
 * This class manages backend selection and device initialization.
 * Game code should use this class to obtain a VulkanicDevice instance.
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Initialize with default backend
 * Vulkanic.initialize();
 * 
 * // Get the device
 * VulkanicDevice device = Vulkanic.getDevice();
 * 
 * // Create a command buffer
 * VulkanicCommandBuffer cmd = device.createCommandBuffer();
 * }</pre>
 */
public class Vulkanic {
    private static final Logger LOGGER = LoggerFactory.getLogger(Vulkanic.class);
    
    @Nullable
    private static VulkanicDevice device;
    
    private static BackendType currentBackend = BackendType.getDefault();
    
    /**
     * Initializes Vulkanic with the default backend.
     * 
     * @throws IllegalStateException if already initialized
     */
    public static void initialize() {
        initialize(BackendType.getDefault());
    }
    
    /**
     * Initializes Vulkanic with the specified backend.
     * 
     * @param backendType the backend to use
     * @throws IllegalStateException if already initialized
     * @throws UnsupportedOperationException if the backend is not supported
     */
    public static void initialize(BackendType backendType) {
        if (device != null) {
            throw new IllegalStateException("Vulkanic is already initialized");
        }
        
        LOGGER.info("Initializing Vulkanic with {} backend", backendType.getDisplayName());
        currentBackend = backendType;
        
        switch (backendType) {
            case OPENGL:
                device = new OpenGLDevice();
                break;
            case VULKAN:
                throw new UnsupportedOperationException("Vulkan backend is not yet implemented");
            default:
                throw new UnsupportedOperationException("Unknown backend: " + backendType);
        }
        
        LOGGER.info("Vulkanic initialized: {} / {} / {}", 
            device.getBackendName(), 
            device.getVendor(), 
            device.getRenderer());
    }
    
    /**
     * Gets the current Vulkanic device instance.
     * 
     * @return the device instance
     * @throws IllegalStateException if not initialized
     */
    public static VulkanicDevice getDevice() {
        if (device == null) {
            throw new IllegalStateException("Vulkanic is not initialized. Call initialize() first.");
        }
        return device;
    }
    
    /**
     * Gets the currently active backend type.
     * 
     * @return the current backend type
     */
    public static BackendType getCurrentBackend() {
        return currentBackend;
    }
    
    /**
     * Checks if Vulkanic has been initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        return device != null;
    }
    
    /**
     * Shuts down Vulkanic and releases all resources.
     */
    public static void shutdown() {
        if (device != null) {
            LOGGER.info("Shutting down Vulkanic");
            device.close();
            device = null;
        }
    }
    
    private Vulkanic() {
        // Prevent instantiation
    }
}
