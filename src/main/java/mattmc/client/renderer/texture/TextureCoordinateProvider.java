package mattmc.client.renderer.texture;

/**
 * Backend-agnostic interface for texture coordinate lookup.
 * 
 * <p>This interface abstracts away the details of how textures are stored and managed
 * (e.g., texture atlases, individual textures), allowing game logic to query texture
 * coordinates without depending on OpenGL-specific implementations.
 * 
 * <p><b>Architecture:</b> This allows mesh building code to be defined outside the
 * backend/ directory while the actual texture management remains in the backend.
 * 
 * @see mattmc.client.renderer.backend.opengl.TextureAtlas OpenGL implementation
 */
public interface TextureCoordinateProvider {
    
    /**
     * UV coordinates for a texture.
     * Represents the texture coordinates in normalized space (0.0 to 1.0).
     */
    class UVMapping {
        public final float u0, v0, u1, v1;
        
        public UVMapping(float u0, float v0, float u1, float v1) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
        }
    }
    
    /**
     * Get UV mapping for a texture by its resource path.
     * 
     * @param texturePath the resource path to the texture (e.g., "/assets/textures/block/stone.png")
     * @return the UV mapping, or null if the texture is not found
     */
    UVMapping getUVMapping(String texturePath);
}
