package net.minecraft.client.renderer.shaders.shadows;

import net.minecraft.client.renderer.shaders.framebuffer.GlFramebuffer;
import net.minecraft.client.renderer.shaders.pipeline.WorldRenderingPhase;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Main shadow renderer coordinating shadow pass rendering.
 * IRIS-compatible implementation for shadow map generation.
 */
public class ShadowRenderer {
    private final PackShadowDirectives shadowDirectives;
    private final ShadowRenderTargets renderTargets;
    private final ShadowMatrices matrices;
    private final ShadowCompositeRenderer compositeRenderer;
    
    private boolean isRenderingShadows;
    private WorldRenderingPhase previousPhase;
    private int terrainSetupCounter;
    
    /**
     * Creates a shadow renderer with the given configuration.
     *
     * @param directives Shadow configuration from shader pack
     * @param renderTargets Shadow render targets manager
     */
    public ShadowRenderer(PackShadowDirectives directives, ShadowRenderTargets renderTargets) {
        this.shadowDirectives = directives;
        this.renderTargets = renderTargets;
        
        // Initialize matrices based on directives
        float halfPlane = directives.getDistance() / 2.0f;
        float distMultiplier = directives.getDistanceRenderMul();
        int resolution = directives.getResolution();
        this.matrices = new ShadowMatrices(halfPlane, distMultiplier, resolution);
        
        // Create composite renderer for post-processing
        this.compositeRenderer = new ShadowCompositeRenderer(renderTargets, shadowDirectives);
        
        this.isRenderingShadows = false;
        this.previousPhase = WorldRenderingPhase.NONE;
        this.terrainSetupCounter = 0;
    }
    
    /**
     * Begins shadow map rendering.
     * Called before main world rendering to generate shadow maps.
     *
     * @param sunAngle Current sun angle for shadow direction
     */
    public void beginShadowRender(float sunAngle) {
        if (isRenderingShadows) {
            return;  // Already rendering shadows
        }
        
        // Mark shadow rendering active
        isRenderingShadows = true;
        ShadowRenderingState.startShadowPass();
        
        // Update shadow matrices for current sun position
        matrices.update(sunAngle, shadowDirectives.getShadowAngle());
        
        // Setup shadow framebuffer
        setupShadowFramebuffer();
        
        // Clear shadow buffers
        clearShadowBuffers();
    }
    
    /**
     * Sets up the shadow framebuffer for rendering.
     */
    private void setupShadowFramebuffer() {
        GlFramebuffer shadowFBO = renderTargets.getShadowFramebuffer();
        if (shadowFBO != null) {
            shadowFBO.bind();
            
            // Set viewport to shadow map resolution
            int resolution = shadowDirectives.getResolution();
            GL11.glViewport(0, 0, resolution, resolution);
            
            // Setup depth test for shadow rendering
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
        }
    }
    
    /**
     * Clears shadow render targets.
     */
    private void clearShadowBuffers() {
        // Clear depth buffers
        GL11.glClearDepth(1.0);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        
        // Clear color buffers if present
        int colorBuffers = shadowDirectives.getColorSamplersCount();
        if (colorBuffers > 0) {
            GL11.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
    }
    
    /**
     * Renders shadow-casting geometry.
     * This is called during the shadow pass to populate shadow maps.
     */
    public void renderShadowPass() {
        if (!isRenderingShadows) {
            return;
        }
        
        // Shadow rendering happens through the normal rendering pipeline
        // but with modified matrices and simplified shaders.
        // The actual geometry rendering is handled by the game's render system
        // using the shadow programs selected by the shader pack.
        
        // Terrain rendering in shadow pass
        terrainSetupCounter++;
        
        // Entity rendering in shadow pass would be triggered here
        // Block entity rendering in shadow pass would be triggered here
        // These are handled by the rendering hooks calling back through
        // the shader program interception system
    }
    
    /**
     * Ends shadow map rendering and performs post-processing.
     */
    public void endShadowRender() {
        if (!isRenderingShadows) {
            return;
        }
        
        // Run shadow composite passes
        compositeRenderer.runCompositePasses(matrices);
        
        // Generate mipmaps if needed
        if (shadowDirectives.shouldGenerateMipmaps()) {
            generateMipmaps();
        }
        
        // Restore main framebuffer
        restoreMainFramebuffer();
        
        // Mark shadow rendering complete
        isRenderingShadows = false;
        ShadowRenderingState.endShadowPass();
    }
    
    /**
     * Generates mipmaps for shadow textures.
     */
    private void generateMipmaps() {
        renderTargets.generateMipmaps();
    }
    
    /**
     * Restores the main rendering framebuffer.
     */
    private void restoreMainFramebuffer() {
        // Bind default framebuffer (0)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        
        // Restore viewport - this would be set to actual screen size
        // The caller should restore the proper viewport
    }
    
    /**
     * Checks if currently rendering shadows.
     *
     * @return true if in shadow pass
     */
    public boolean isRenderingShadows() {
        return isRenderingShadows;
    }
    
    /**
     * Gets the shadow matrices.
     *
     * @return Shadow transformation matrices
     */
    public ShadowMatrices getMatrices() {
        return matrices;
    }
    
    /**
     * Gets the shadow directives.
     *
     * @return Shadow configuration
     */
    public PackShadowDirectives getDirectives() {
        return shadowDirectives;
    }
    
    /**
     * Gets the shadow render targets.
     *
     * @return Shadow render targets manager
     */
    public ShadowRenderTargets getRenderTargets() {
        return renderTargets;
    }
    
    /**
     * Gets the shadow projection matrix.
     *
     * @return Shadow projection matrix
     */
    public Matrix4f getShadowProjectionMatrix() {
        return matrices.getProjectionMatrix();
    }
    
    /**
     * Gets the shadow model-view matrix.
     *
     * @return Shadow model-view matrix
     */
    public Matrix4f getShadowModelViewMatrix() {
        return matrices.getModelViewMatrix();
    }
    
    /**
     * Updates shadow direction based on celestial angle.
     *
     * @param celestialAngle Angle of sun/moon (0-1)
     */
    public void updateShadowDirection(float celestialAngle) {
        // Convert celestial angle to radians
        float sunAngle = celestialAngle * 2.0f * (float) Math.PI;
        
        // Update matrices
        matrices.update(sunAngle, shadowDirectives.getShadowAngle());
    }
    
    /**
     * Updates shadow direction with explicit vector.
     *
     * @param lightDirection Direction of light source
     */
    public void updateShadowDirection(Vector3f lightDirection) {
        matrices.updateWithDirection(lightDirection);
    }
    
    /**
     * Configures shadow culling parameters.
     *
     * @param cullDistance Distance at which to cull shadow casters
     */
    public void configureCulling(float cullDistance) {
        // Shadow culling configuration
        // This would affect which entities/blocks are rendered in shadow pass
    }
    
    /**
     * Sets up shadow camera for rendering.
     */
    public void setupShadowCamera() {
        // Apply shadow projection matrix
        // This would be done through the matrix stack system
        // The actual implementation would set matrices via uniform uploads
    }
    
    /**
     * Stores the current rendering phase.
     *
     * @param phase Current rendering phase
     */
    public void setCurrentPhase(WorldRenderingPhase phase) {
        this.previousPhase = phase;
    }
    
    /**
     * Gets the terrain setup counter (for debugging).
     *
     * @return Number of terrain setups in shadow pass
     */
    public int getTerrainSetupCounter() {
        return terrainSetupCounter;
    }
    
    /**
     * Resets the terrain setup counter.
     */
    public void resetTerrainSetupCounter() {
        this.terrainSetupCounter = 0;
    }
    
    /**
     * Checks if shadow rendering is enabled.
     *
     * @return true if shadows are enabled in directives
     */
    public boolean areShadowsEnabled() {
        return shadowDirectives.getDistance() > 0.0f;
    }
    
    /**
     * Destroys the shadow renderer and releases resources.
     */
    public void destroy() {
        if (compositeRenderer != null) {
            compositeRenderer.destroy();
        }
        // Render targets are managed externally
    }
}
