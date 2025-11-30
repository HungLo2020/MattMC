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
	 * Represents the texture coordinates in normalized space (0.0 to 1.0).
	 * 
	 * <p><b>UV Shrinking for Mipmaps:</b> When using mipmaps and anisotropic filtering,
	 * texture bleeding can occur at the edges of texture tiles in an atlas. The
	 * {@link #uvShrinkRatio()} method returns a factor used to inset UV coordinates
	 * slightly toward the center of the texture tile, preventing sampling from
	 * neighboring tiles. This follows Minecraft's FaceBakery approach.
	 * 
	 * <p>To apply shrinking, use {@link #shrinkU(float, float)} and {@link #shrinkV(float, float)}
	 * which linearly interpolate the given UV coordinate toward the center by the shrink ratio.
	 */
	class UVMapping {
		public final float u0, v0, u1, v1;
		private final float uvShrinkRatio;
		
		/**
		 * Create a UV mapping with default shrink ratio (no shrinking).
		 */
		public UVMapping(float u0, float v0, float u1, float v1) {
			this(u0, v0, u1, v1, 0.0f);
		}
		
		/**
		 * Create a UV mapping with a specific shrink ratio.
		 * 
		 * @param u0 minimum U coordinate
		 * @param v0 minimum V coordinate
		 * @param u1 maximum U coordinate
		 * @param v1 maximum V coordinate
		 * @param uvShrinkRatio the shrink ratio (typically 4.0f / atlasSize)
		 */
		public UVMapping(float u0, float v0, float u1, float v1, float uvShrinkRatio) {
			this.u0 = u0;
			this.v0 = v0;
			this.u1 = u1;
			this.v1 = v1;
			this.uvShrinkRatio = uvShrinkRatio;
		}
		
		/**
		 * Get the UV shrink ratio for this texture.
		 * 
		 * <p>This ratio is used to prevent texture bleeding when mipmaps are enabled.
		 * UV coordinates should be linearly interpolated toward the center of the texture
		 * by this amount. Formula follows Minecraft's TextureAtlasSprite.uvShrinkRatio(): 
		 * 4.0f / atlasSize
		 * 
		 * @return the shrink ratio (0.0 = no shrinking, larger values = more shrinking)
		 */
		public float uvShrinkRatio() {
			return uvShrinkRatio;
		}
		
		/**
		 * Apply UV shrinking to a U coordinate.
		 * Linearly interpolates the coordinate toward the center U by the shrink ratio.
		 * 
		 * @param u the U coordinate to shrink
		 * @param centerU the center U coordinate to shrink toward
		 * @return the shrunk U coordinate
		 */
		public float shrinkU(float u, float centerU) {
			return lerp(uvShrinkRatio, u, centerU);
		}
		
		/**
		 * Apply UV shrinking to a V coordinate.
		 * Linearly interpolates the coordinate toward the center V by the shrink ratio.
		 * 
		 * @param v the V coordinate to shrink
		 * @param centerV the center V coordinate to shrink toward
		 * @return the shrunk V coordinate
		 */
		public float shrinkV(float v, float centerV) {
			return lerp(uvShrinkRatio, v, centerV);
		}
		
		/**
		 * Get the shrunk u0 coordinate (minimum U).
		 * Convenience method that applies shrinking toward the center.
		 */
		public float getShrunkU0() {
			float centerU = (u0 + u1) * 0.5f;
			return shrinkU(u0, centerU);
		}
		
		/**
		 * Get the shrunk u1 coordinate (maximum U).
		 * Convenience method that applies shrinking toward the center.
		 */
		public float getShrunkU1() {
			float centerU = (u0 + u1) * 0.5f;
			return shrinkU(u1, centerU);
		}
		
		/**
		 * Get the shrunk v0 coordinate (minimum V).
		 * Convenience method that applies shrinking toward the center.
		 */
		public float getShrunkV0() {
			float centerV = (v0 + v1) * 0.5f;
			return shrinkV(v0, centerV);
		}
		
		/**
		 * Get the shrunk v1 coordinate (maximum V).
		 * Convenience method that applies shrinking toward the center.
		 */
		public float getShrunkV1() {
			float centerV = (v0 + v1) * 0.5f;
			return shrinkV(v1, centerV);
		}
		
		/**
		 * Linear interpolation between two values.
		 * 
		 * @param t interpolation factor (0.0 = returns a, 1.0 = returns b)
		 * @param a start value
		 * @param b end value
		 * @return the linearly interpolated result: a + t * (b - a)
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
