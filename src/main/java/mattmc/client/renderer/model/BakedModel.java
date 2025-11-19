package mattmc.client.renderer.model;

import mattmc.client.resources.model.ModelDisplay;

import java.util.List;
import java.util.Map;

/**
 * Represents a baked (pre-processed) model ready for rendering.
 * Similar to Minecraft's BakedModel interface.
 * 
 * Contains pre-computed quads that can be directly rendered without
 * runtime model element processing.
 */
public class BakedModel {
    private final List<BakedQuad> quads;
    private final Map<String, ModelDisplay.Transform> displayTransforms;
    private final String particleTexture;
    private final boolean hasAmbientOcclusion;
    
    /**
     * Create a baked model.
     * 
     * @param quads List of pre-processed quads
     * @param displayTransforms Display transforms for different view modes (gui, firstperson, etc.)
     * @param particleTexture Particle texture path
     * @param hasAmbientOcclusion Whether this model should use ambient occlusion
     */
    public BakedModel(List<BakedQuad> quads, Map<String, ModelDisplay.Transform> displayTransforms, 
                      String particleTexture, boolean hasAmbientOcclusion) {
        this.quads = quads;
        this.displayTransforms = displayTransforms;
        this.particleTexture = particleTexture;
        this.hasAmbientOcclusion = hasAmbientOcclusion;
    }
    
    /**
     * Get all quads for this model.
     * In Minecraft, this would be filtered by face direction and block state,
     * but we simplify by returning all quads.
     */
    public List<BakedQuad> getQuads() {
        return quads;
    }
    
    /**
     * Get display transform for a specific view mode.
     * 
     * @param mode Display mode (gui, firstperson_righthand, etc.)
     * @return Transform for that mode, or null if not defined
     */
    public ModelDisplay.Transform getTransform(String mode) {
        return displayTransforms != null ? displayTransforms.get(mode) : null;
    }
    
    /**
     * Get all display transforms.
     */
    public Map<String, ModelDisplay.Transform> getDisplayTransforms() {
        return displayTransforms;
    }
    
    /**
     * Get the particle texture path.
     */
    public String getParticleTexture() {
        return particleTexture;
    }
    
    /**
     * Check if this model uses ambient occlusion.
     */
    public boolean usesAmbientOcclusion() {
        return hasAmbientOcclusion;
    }
    
    /**
     * Check if this model is a GUI 3D model (has 3D geometry).
     * Returns true if the model has quads.
     */
    public boolean isGui3d() {
        return quads != null && !quads.isEmpty();
    }
}
