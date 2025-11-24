package mattmc.client.renderer.panorama;

/**
 * Backend-agnostic interface for rendering panorama backgrounds.
 * The panorama is a rotating skybox typically used for menu screens.
 */
public interface PanoramaRenderer extends AutoCloseable {
    
    /**
     * Render the panorama skybox.
     * @param width viewport width
     * @param height viewport height
     * @param blurred if true, applies a blur effect
     */
    void render(int width, int height, boolean blurred);
    
    /**
     * Clean up resources.
     */
    @Override
    void close();
}
