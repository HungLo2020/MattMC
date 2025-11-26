package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.texture.TextureCoordinateProvider;
import mattmc.client.settings.OptionsManager;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenGL implementation of texture coordinate provider using texture atlas.
 * Runtime texture atlas builder for block textures.
 * Packs all block textures into a single atlas at game startup,
 * enabling VBO rendering with multiple textures.
 * 
 * <p><b>Architecture:</b> The TextureAtlas is created at client/resource initialization
 * time, before a world is selected. Blocks/items and game logic use string texture paths
 * as canonical IDs (e.g. "mattmc:block/stone"). The rendering backend uses fast integer
 * texture IDs internally for performance.
 * 
 * <p>The atlas provides:
 * <ul>
 *   <li>String path → int ID mapping via {@link #getTextureId(String)}</li>
 *   <li>Int ID → UVMapping lookup via {@link #getUVMapping(int)}</li>
 *   <li>Backward-compatible string-based lookup via {@link #getUVMapping(String)}</li>
 * </ul>
 * 
 * Similar to modern MattMC's texture atlas system.
 * Implements AutoCloseable to ensure OpenGL resources are properly released.
 */
public class TextureAtlas implements TextureCoordinateProvider, AutoCloseable {
	private static final Logger logger = LoggerFactory.getLogger(TextureAtlas.class);

	private final int atlasTextureId;
	private final int atlasWidth;
	private final int atlasHeight;
	private final int textureSize = 16; // Standard MattMC texture size
	
	// Int-keyed UV mappings for fast hot path lookup
	private final Map<Integer, TextureCoordinateProvider.UVMapping> uvMappings = new HashMap<>();
	
	// String path → int ID mapping for texture ID lookup
	private final Map<String, Integer> pathToId = new HashMap<>();
	
	// Reverse lookup for debugging/tooling: int ID → string path
	private final List<String> idToPath = new ArrayList<>();
    
	/**
	 * Build the texture atlas from all registered blocks.
	 * Call this once during game initialization.
	 * 
	 * <p><b>Note:</b> This constructor builds the atlas and populates all mappings.
	 * After construction, the string→int ID mapping is fixed for the atlas lifetime.
	 */
	public TextureAtlas() {
		// logger.info("Building texture atlas...");
		
		// Collect all unique texture paths from registered blocks
		Set<String> uniqueTexturePaths = new HashSet<>();
		for (String identifier : Blocks.getRegisteredIdentifiers()) {
			// Skip AIR block - it doesn't have a texture
			if (identifier.equals("mattmc:air")) {
				continue;
			}
			
			Block block = Blocks.getBlock(identifier);
			if (block != null) {
				Map<String, String> texturePaths = block.getTexturePaths();
				if (texturePaths != null) {
					uniqueTexturePaths.addAll(texturePaths.values());
				}
			}
		}
		
		// Remove null paths
		uniqueTexturePaths.remove(null);
		
		// logger.info("Found {} unique textures", uniqueTexturePaths.size());
		
		// Calculate atlas dimensions (must be power of 2 for mipmaps)
		int textureCount = uniqueTexturePaths.size();
		int texturesPerRow = (int) Math.ceil(Math.sqrt(textureCount));
		
		// Round up to next power of 2
		int powerOf2Width = nextPowerOf2(texturesPerRow * textureSize);
		int powerOf2Height = powerOf2Width; // Keep it square
		
		atlasWidth = powerOf2Width;
		atlasHeight = powerOf2Height;
		
		// logger.info("Atlas size: {}x{} ({} textures, {} per row)", atlasWidth, atlasHeight, textureCount, texturesPerRow);
		
		// Create atlas image
		BufferedImage atlasImage = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = atlasImage.createGraphics();
		
		// Fill with neutral gray (RGB: 128,128,128) to minimize color bleeding artifacts
		// Gray is less noticeable than black or magenta when mipmaps/anisotropic filtering sample it
		g.setColor(new Color(128, 128, 128, 255));
		g.fillRect(0, 0, atlasWidth, atlasHeight);
		
		// Pack textures into atlas
		int x = 0, y = 0;
		List<String> textureList = new ArrayList<>(uniqueTexturePaths);
		
		for (String texturePath : textureList) {
			try {
				// Load texture image
				BufferedImage texture = loadTexture(texturePath);
				if (texture != null) {
					// Draw texture into atlas
					g.drawImage(texture, x, y, textureSize, textureSize, null);
					
					// Calculate UV coordinates (0.0 to 1.0)
					float u0 = (float) x / atlasWidth;
					float v0 = (float) y / atlasHeight;
					float u1 = (float) (x + textureSize) / atlasWidth;
					float v1 = (float) (y + textureSize) / atlasHeight;
					
					// Register texture with int ID mapping
					registerTexture(texturePath, new TextureCoordinateProvider.UVMapping(u0, v0, u1, v1));
					
					// logger.info("  Packed: {} at ({},{}) UV: {},{} -> {},{}", texturePath, x, y, u0, v0, u1, v1);
				} else {
					logger.error("  Failed to load: {}", texturePath);
				}
			} catch (RuntimeException e) {
				logger.error("  Error loading {}: {}", texturePath, e.getMessage());
			}
			
			// Move to next position
			x += textureSize;
			if (x >= atlasWidth) {
				x = 0;
				y += textureSize;
			}
		}
		
		g.dispose();
		
		// Upload atlas to GPU
		atlasTextureId = createGLTexture(atlasImage);
		
		// logger.info("Texture atlas built successfully! ID: {}", atlasTextureId);
	}
	
	/**
	 * Register a texture in the atlas with int ID mapping.
	 * Used during atlas building to associate texture paths with integer IDs.
	 * 
	 * @param path the texture path
	 * @param uvMapping the UV coordinates for this texture
	 * @return the assigned texture ID
	 */
	private int registerTexture(String path, UVMapping uvMapping) {
		Integer existing = pathToId.get(path);
		if (existing != null) {
			// If the same path appears again, keep the old ID and UV.
			return existing;
		}
		int id = idToPath.size();
		idToPath.add(path);
		pathToId.put(path, id);
		uvMappings.put(id, uvMapping);
		return id;
	}
    
	/**
	 * Load a texture image from resources.
	 */
	private BufferedImage loadTexture(String path) {
		try (InputStream is = mattmc.util.ResourceLoader.getResourceStreamFromClassLoader(path)) {
			if (is == null) {
				logger.error("Texture not found: {}", path);
				return null;
			}
			return ImageIO.read(is);
		} catch (IOException e) {
			logger.error("Failed to load texture: {}", path, e);
			return null;
		}
	}
	
	/**
	 * Calculate the next power of 2 greater than or equal to the given value.
	 */
	private int nextPowerOf2(int value) {
		int power = 1;
		while (power < value) {
			power *= 2;
		}
		return power;
	}
	
	/**
	 * Create an OpenGL texture from a BufferedImage.
	 */
	private int createGLTexture(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		
		// Convert image to ByteBuffer
		int[] pixels = new int[width * height];
		image.getRGB(0, 0, width, height, pixels, 0, width);
		
		ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int pixel = pixels[y * width + x];
				buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red
				buffer.put((byte) ((pixel >> 8) & 0xFF));  // Green
				buffer.put((byte) (pixel & 0xFF));         // Blue
				buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha
			}
		}
		buffer.flip();
		
		// Generate texture ID
		int textureID = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, textureID);
		
		// Upload texture data
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
		
		// Apply texture filtering including mipmaps (atlas is now power-of-2 and uses transparent fill)
		TextureManager.applyTextureFiltering(true);
		
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
		
		glBindTexture(GL_TEXTURE_2D, 0);
		
		return textureID;
	}
	
	/**
	 * Get the integer texture ID for a given texture path.
	 * 
	 * <p>IDs are stable for the lifetime of this TextureAtlas instance.
	 * Once the atlas is built during resource loading, the string→int mapping
	 * does not change. Use these IDs in hot paths instead of string lookups.
	 * 
	 * @param path the texture path (e.g., "assets/textures/block/stone.png")
	 * @return the integer texture ID, or -1 if the path is not found in the atlas
	 */
	public int getTextureId(String path) {
		Integer id = pathToId.get(path);
		return id != null ? id : -1;
	}
	
	/**
	 * Get the UV mapping for an integer texture ID.
	 * 
	 * <p>This is the fast lookup method for hot paths. Use {@link #getTextureId(String)}
	 * once to convert a texture path to an ID, then use this method for repeated UV lookups.
	 * 
	 * @param textureId the integer texture ID from {@link #getTextureId(String)}
	 * @return the UV mapping, or null if the ID is invalid (including -1)
	 */
	public UVMapping getUVMapping(int textureId) {
		return uvMappings.get(textureId);
	}
	
	/**
	 * Get the UV mapping for a texture path.
	 * Returns null if the texture is not in the atlas.
	 * 
	 * <p>This is a convenience method that delegates to {@link #getTextureId(String)}
	 * and {@link #getUVMapping(int)}. For hot paths, prefer resolving the texture ID
	 * once and using the int-based overload.
	 */
	@Override
	public UVMapping getUVMapping(String texturePath) {
		Integer id = pathToId.get(texturePath);
		return id != null ? uvMappings.get(id) : null;
	}
	
	/**
	 * Get the texture path for a given texture ID.
	 * Useful for debugging and tooling.
	 * 
	 * @param textureId the integer texture ID
	 * @return the texture path, or null if the ID is invalid
	 */
	public String getTexturePath(int textureId) {
		if (textureId >= 0 && textureId < idToPath.size()) {
			return idToPath.get(textureId);
		}
		return null;
	}
	
	/**
	 * Bind the atlas texture for rendering.
	 */
	public void bind() {
		glBindTexture(GL_TEXTURE_2D, atlasTextureId);
	}
	
	/**
	 * Get the OpenGL texture ID of the atlas.
	 */
	public int getAtlasTextureId() {
		return atlasTextureId;
	}
	
	/**
	 * Get the OpenGL texture ID of the atlas.
	 * @deprecated Use {@link #getAtlasTextureId()} for clarity.
	 */
	@Deprecated
	public int getTextureId() {
		return atlasTextureId;
	}
	
	/**
	 * Clean up GPU resources.
	 * Implements AutoCloseable.close() for proper resource management.
	 */
	@Override
	public void close() {
		glDeleteTextures(atlasTextureId);
	}
	
	/**
	 * Get the number of textures in the atlas.
	 */
	public int getTextureCount() {
		return idToPath.size();
	}
}
