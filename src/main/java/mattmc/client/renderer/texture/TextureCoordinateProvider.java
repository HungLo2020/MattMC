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
 * <p><b>Performance:</b> For hot paths (mesh building, rendering), prefer the int-based
 * API: call {@link #getTextureId(String)} once to convert a texture path to an integer ID,
 * then use {@link #getUVMapping(int)} for repeated UV lookups. This avoids string hashing
 * in inner loops.
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
	 * Get the integer texture ID for a given texture path.
	 * 
	 * <p>IDs are stable for the lifetime of this provider instance.
	 * Once built, the string→int mapping does not change.
	 * Use these IDs in hot paths instead of string lookups.
	 * 
	 * @param texturePath the resource path to the texture (e.g., "assets/textures/block/stone.png")
	 * @return the integer texture ID, or -1 if the path is not found
	 */
	int getTextureId(String texturePath);
	
	/**
	 * Get UV mapping for a texture by its integer ID.
	 * 
	 * <p>This is the fast lookup method for hot paths. Use {@link #getTextureId(String)}
	 * once to convert a texture path to an ID, then use this method for repeated UV lookups.
	 * 
	 * @param textureId the integer texture ID from {@link #getTextureId(String)}
	 * @return the UV mapping, or null if the ID is invalid (including -1)
	 */
	UVMapping getUVMapping(int textureId);
	
	/**
	 * Get UV mapping for a texture by its resource path.
	 * 
	 * <p>This is a convenience method. For hot paths, prefer resolving the texture ID
	 * once with {@link #getTextureId(String)} and using {@link #getUVMapping(int)}.
	 * 
	 * @param texturePath the resource path to the texture (e.g., "assets/textures/block/stone.png")
	 * @return the UV mapping, or null if the texture is not found
	 */
	UVMapping getUVMapping(String texturePath);
}
