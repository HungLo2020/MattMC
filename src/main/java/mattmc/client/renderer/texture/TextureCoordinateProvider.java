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
	 * UV coordinates for a texture in a texture atlas.
	 * 
	 * <p>Contains both raw UV coordinates and shrunk (inset) coordinates that
	 * prevent texture bleeding at lower mipmap levels. The shrunk coordinates
	 * are computed by lerping toward the texture center by a ratio based on
	 * the atlas size (matching Minecraft's algorithm in FaceBakery).
	 * 
	 * <p><b>Usage:</b> For rendering, use the shrunk coordinates (u0Shrunk, etc.)
	 * to avoid gray artifacts on distant terrain. The raw coordinates (u0, etc.)
	 * are provided for cases where exact atlas positions are needed.
	 */
	class UVMapping {
		/** Raw UV coordinates (exact atlas position) */
		public final float u0, v0, u1, v1;
		
		/** Shrunk UV coordinates (inset to prevent mipmap bleeding) */
		public final float u0Shrunk, v0Shrunk, u1Shrunk, v1Shrunk;
		
		/**
		 * Create a UV mapping with no UV shrinking.
		 * Use this for non-atlas textures or when shrinking is not needed.
		 */
		public UVMapping(float u0, float v0, float u1, float v1) {
			this(u0, v0, u1, v1, 0.0f);
		}
		
		/**
		 * Create a UV mapping with UV shrinking for texture atlas anti-bleeding.
		 * 
		 * <p>The shrink ratio is typically computed as {@code 4.0f / atlasSize},
		 * matching Minecraft's TextureAtlasSprite.uvShrinkRatio() method.
		 * 
		 * @param u0 left edge U coordinate (0.0-1.0)
		 * @param v0 top edge V coordinate (0.0-1.0)
		 * @param u1 right edge U coordinate (0.0-1.0)
		 * @param v1 bottom edge V coordinate (0.0-1.0)
		 * @param shrinkRatio ratio to lerp UV coordinates toward center (0.0 = no shrinking)
		 */
		public UVMapping(float u0, float v0, float u1, float v1, float shrinkRatio) {
			this.u0 = u0;
			this.v0 = v0;
			this.u1 = u1;
			this.v1 = v1;
			
			if (shrinkRatio > 0.0f) {
				// Compute center of the texture region
				float uCenter = (u0 + u1) * 0.5f;
				float vCenter = (v0 + v1) * 0.5f;
				
				// Lerp UV coordinates toward center by shrinkRatio
				// This matches Minecraft's FaceBakery.bakeQuad() algorithm
				this.u0Shrunk = lerp(shrinkRatio, u0, uCenter);
				this.v0Shrunk = lerp(shrinkRatio, v0, vCenter);
				this.u1Shrunk = lerp(shrinkRatio, u1, uCenter);
				this.v1Shrunk = lerp(shrinkRatio, v1, vCenter);
			} else {
				// No shrinking - use raw coordinates
				this.u0Shrunk = u0;
				this.v0Shrunk = v0;
				this.u1Shrunk = u1;
				this.v1Shrunk = v1;
			}
		}
		
		/**
		 * Linear interpolation between two values.
		 * @param t interpolation factor (0.0 = a, 1.0 = b)
		 * @param a start value
		 * @param b end value
		 * @return interpolated value
		 */
		private static float lerp(float t, float a, float b) {
			return a + t * (b - a);
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
