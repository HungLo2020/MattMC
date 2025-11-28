package mattmc.client.renderer.chunk;

import mattmc.util.ColorUtils;
import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.renderer.texture.TextureCoordinateProvider;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.block.LeavesBlock;

/**
 * Handles UV mapping and color extraction for mesh building.
 * Maps block textures to texture atlas coordinates and applies color tints.
 * 
 * <p><b>Performance:</b> For hot paths, use the int-based texture ID API:
 * call {@link #resolveTextureId(String)} once to get an ID, then
 * use {@link #resolveUV(int)} for repeated UV lookups. This avoids
 * string hashing in inner loops.
 * 
 * Extracted from MeshBuilder as part of refactoring to single-purpose classes.
 */
public class UVMapper {
	
	private final TextureCoordinateProvider textureAtlas;
	
	/**
	 * Create a UV mapper with optional texture atlas support.
	 * 
	 * @param textureAtlas Texture atlas for UV mapping, or null to use fallback colors
	 */
	public UVMapper(TextureCoordinateProvider textureAtlas) {
		this.textureAtlas = textureAtlas;
	}
	
	/**
	 * Get the texture atlas.
	 * @return The texture atlas, or null if not available
	 */
	public TextureCoordinateProvider getTextureCoordinateProvider() {
		return textureAtlas;
	}
	
	/**
	 * Resolve a texture path to an integer texture ID.
	 * Use this once per texture path, then use {@link #resolveUV(int)} for repeated lookups.
	 * 
	 * @param texturePath the texture path
	 * @return the integer texture ID, or -1 if not found
	 */
	public int resolveTextureId(String texturePath) {
		if (textureAtlas == null || texturePath == null) {
			return -1;
		}
		return textureAtlas.getTextureId(texturePath);
	}
	
	/**
	 * Get UV mapping for an integer texture ID.
	 * This is the fast lookup method for hot paths.
	 * 
	 * @param textureId the integer texture ID from {@link #resolveTextureId(String)}
	 * @return the UV mapping, or null if the ID is invalid
	 */
	public TextureCoordinateProvider.UVMapping resolveUV(int textureId) {
		if (textureAtlas == null || textureId < 0) {
			return null;
		}
		return textureAtlas.getUVMapping(textureId);
	}
	
	/**
	 * Get UV mapping from texture atlas for a face.
	 * Returns null if no atlas or texture not found.
	 * 
	 * <p>Resolves the texture path to an int ID, then uses int-based UV mapping lookup.
	 * For repeated lookups of the same texture, prefer using 
	 * {@link #resolveTextureId(String)} once, then {@link #resolveUV(int)} for better performance.
	 */
	public TextureCoordinateProvider.UVMapping getUVMapping(BlockFaceCollector.FaceData face) {
		if (textureAtlas == null) {
			return null;
		}
		
		String texturePath = face.block.getTexturePath(face.faceType);
		if (texturePath == null) {
			return null;
		}
		
		// Resolve to int ID, then use int-based UV lookup
		int textureId = textureAtlas.getTextureId(texturePath);
		return (textureId >= 0) ? textureAtlas.getUVMapping(textureId) : null;
	}
	
	/**
	 * Get UV mapping from texture atlas for a specific texture path.
	 * Returns null if no atlas or texture not found.
	 * Used by ModelElementRenderer for data-driven geometry rendering.
	 * 
	 * <p>Resolves the texture path to an int ID, then uses int-based UV mapping lookup.
	 * For repeated lookups of the same texture, prefer using 
	 * {@link #resolveTextureId(String)} once, then {@link #resolveUV(int)} to avoid
	 * repeated string→int conversion.
	 */
	public TextureCoordinateProvider.UVMapping getUVMappingForTexture(String texturePath) {
		if (textureAtlas == null || texturePath == null) {
			return null;
		}
		
		// Resolve to int ID, then use int-based UV lookup
		int textureId = textureAtlas.getTextureId(texturePath);
		return (textureId >= 0) ? textureAtlas.getUVMapping(textureId) : null;
	}
	
	/**
	 * Extract RGBA color from face data.
	 * Uses white color with directional brightness when texture atlas is available,
	 * otherwise uses fallback colors with directional brightness.
	 * 
	 * The colorBrightness field contains Minecraft-style directional shading:
	 * - TOP: 1.0 (brightest)
	 * - BOTTOM: 0.5 (darkest)
	 * - NORTH/SOUTH: 0.8
	 * - WEST/EAST: 0.6
	 */
	public float[] extractColor(BlockFaceCollector.FaceData face) {
		int r, g, b;
		
		if (textureAtlas != null && face.block.hasTexture()) {
			// Use white color for texture modulation, with directional brightness applied
			int renderColor = 0xFFFFFF;
			
			// Apply grass green tint for grass_block top face (vanilla MattMC-like)
			if (face.block == Blocks.GRASS_BLOCK && face.faceType != null && "top".equals(face.faceType)) {
				renderColor = 0x5BB53B; // Grass green
			}
			
			// Apply tinting for leaves blocks
			if (face.block instanceof LeavesBlock) {
				LeavesBlock leavesBlock = (LeavesBlock) face.block;
				if (leavesBlock.hasTinting()) {
					renderColor = leavesBlock.getTintColor();
				}
			}
			
			// Extract RGB and apply directional brightness
			float directionalBrightness = face.colorBrightness;
			r = (int)(((renderColor >> 16) & 0xFF) * directionalBrightness);
			g = (int)(((renderColor >> 8) & 0xFF) * directionalBrightness);
			b = (int)((renderColor & 0xFF) * directionalBrightness);
		} else {
			// Use fallback color when no texture atlas
			// Directional brightness is baked into the color via adjustColorBrightness
			int renderColor = ColorUtils.adjustColorBrightness(
				face.block.getFallbackColor(), 
				face.colorBrightness
			);
			
			// Apply grass green tint for grass_block top face
			if (face.block == Blocks.GRASS_BLOCK && face.faceType != null && "top".equals(face.faceType)) {
				renderColor = ColorUtils.applyTint(renderColor, 0x5BB53B, face.colorBrightness);
			}
			
			// Apply tinting for leaves blocks
			if (face.block instanceof LeavesBlock) {
				LeavesBlock leavesBlock = (LeavesBlock) face.block;
				if (leavesBlock.hasTinting()) {
					renderColor = ColorUtils.applyTint(renderColor, leavesBlock.getTintColor(), face.colorBrightness);
				}
			}
			
			r = (renderColor >> 16) & 0xFF;
			g = (renderColor >> 8) & 0xFF;
			b = renderColor & 0xFF;
		}
		
		return new float[] {
			r / 255.0f,
			g / 255.0f,
			b / 255.0f,
			1.0f // alpha
		};
	}
}
