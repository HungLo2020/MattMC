package net.vulkanic;

/**
 * Service Provider Interface for graphics backend implementations.
 * Backend modules implement this interface and register via ServiceLoader.
 */
public interface GraphicsBackendProvider {
    /**
     * Gets the backend type this provider supports.
     */
    VulkanicAPI.BackendType getBackendType();
    
    /**
     * Creates a new instance of the graphics backend.
     */
    GraphicsBackend createBackend();
}
