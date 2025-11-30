package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.texture.AnimatedTextureData;
import mattmc.client.renderer.texture.TextureCoordinateProvider;
import mattmc.client.resources.metadata.animation.AnimationMetadataSection;
import mattmc.client.resources.metadata.animation.FrameSize;
import mattmc.client.settings.OptionsManager;
import mattmc.world.level.block.Block;
import mattmc.registries.Blocks;
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
	
	/**
	 * Border padding around each texture tile in pixels.
	 * This padding duplicates edge pixels to prevent mipmap bleeding when
	 * mipmaps sample across texture tile boundaries. A value of 2 is sufficient
	 * for most mipmap levels and anisotropic filtering.
	 */
	private static final int BORDER_PADDING = 2;
	
	/**
	 * Total size of each texture cell in the atlas (texture + padding on both sides).
	 */
	private final int cellSize = textureSize + BORDER_PADDING * 2;
	
	// Int-keyed UV mappings for fast hot path lookup
	private final Map<Integer, TextureCoordinateProvider.UVMapping> uvMappings = new HashMap<>();
	
	// String path → int ID mapping for texture ID lookup
	private final Map<String, Integer> pathToId = new HashMap<>();
	
	// Reverse lookup for debugging/tooling: int ID → string path
	private final List<String> idToPath = new ArrayList<>();
	
	// Animated texture data for textures with .mcmeta files
	private final Map<String, AnimatedTextureData> animatedTextures = new HashMap<>();
	
	// Atlas position data for animated texture updates
	private final Map<String, int[]> textureAtlasPositions = new HashMap<>();
    
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
		// Each texture cell includes the texture plus border padding on all sides
		int textureCount = uniqueTexturePaths.size();
		int texturesPerRow = (int) Math.ceil(Math.sqrt(textureCount));
		
		// Round up to next power of 2 (using cellSize which includes padding)
		int powerOf2Width = nextPowerOf2(texturesPerRow * cellSize);
		int powerOf2Height = powerOf2Width; // Keep it square
		
		atlasWidth = powerOf2Width;
		atlasHeight = powerOf2Height;
		
		// logger.info("Atlas size: {}x{} ({} textures, {} per row)", atlasWidth, atlasHeight, textureCount, texturesPerRow);
		
		// Create atlas image
		BufferedImage atlasImage = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = atlasImage.createGraphics();
		
		// Fill with transparent black to ensure proper alpha for textures with transparency
		// This is important for leaves and other blocks with transparent pixels
		g.setComposite(java.awt.AlphaComposite.Clear);
		g.fillRect(0, 0, atlasWidth, atlasHeight);
		g.setComposite(java.awt.AlphaComposite.SrcOver);
		
		// Pack textures into atlas with border padding
		int cellX = 0, cellY = 0;
		List<String> textureList = new ArrayList<>(uniqueTexturePaths);
		
		// Use Src composite to completely replace destination pixels with source pixels
		// This preserves the texture's alpha channel instead of blending with the background
		g.setComposite(java.awt.AlphaComposite.Src);
		
		for (String texturePath : textureList) {
			try {
				// Load texture image (handles animated textures with .mcmeta files)
				BufferedImage texture = loadTextureForAtlas(texturePath);
				if (texture != null) {
					// Calculate the position where the actual texture goes (inside the padding)
					int textureX = cellX + BORDER_PADDING;
					int textureY = cellY + BORDER_PADDING;
					
					// Draw the main texture into the atlas at the padded position
					g.drawImage(texture, textureX, textureY, textureSize, textureSize, null);
					
					// Draw border padding by duplicating edge pixels
					// This prevents mipmap bleeding when the GPU generates lower mip levels
					drawBorderPadding(g, texture, cellX, cellY);
					
					// Store atlas position for animated texture updates (the inner texture position)
					textureAtlasPositions.put(texturePath, new int[]{textureX, textureY});
					
					// Calculate UV coordinates (0.0 to 1.0) for the inner texture area only
					// The padding is not included in UV coordinates
					float u0 = (float) textureX / atlasWidth;
					float v0 = (float) textureY / atlasHeight;
					float u1 = (float) (textureX + textureSize) / atlasWidth;
					float v1 = (float) (textureY + textureSize) / atlasHeight;
					
					// Calculate UV shrink ratio following Minecraft's formula: 4.0f / atlasSize
					// This provides additional protection against texture bleeding when mipmaps are enabled.
					// With border padding, a smaller shrink ratio may be sufficient.
					float atlasSize = Math.max(atlasWidth, atlasHeight);
					float uvShrinkRatio = 4.0f / atlasSize;
					
					// Register texture with int ID mapping including shrink ratio
					registerTexture(texturePath, new TextureCoordinateProvider.UVMapping(u0, v0, u1, v1, uvShrinkRatio));
					
					// logger.info("  Packed: {} at ({},{}) UV: {},{} -> {},{}", texturePath, textureX, textureY, u0, v0, u1, v1);
				} else {
					logger.error("  Failed to load: {}", texturePath);
				}
			} catch (RuntimeException e) {
				logger.error("  Error loading {}: {}", texturePath, e.getMessage());
			}
			
			// Move to next cell position (each cell includes texture + padding)
			cellX += cellSize;
			if (cellX + cellSize > atlasWidth) {
				cellX = 0;
				cellY += cellSize;
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
	 * <p>If the same path is registered multiple times, the existing ID and UV
	 * mapping are kept (since the same texture should always have the same UVs).
	 * 
	 * @param path the texture path
	 * @param uvMapping the UV coordinates for this texture
	 * @return the assigned texture ID (existing or newly created)
	 */
	private int registerTexture(String path, UVMapping uvMapping) {
		Integer existing = pathToId.get(path);
		if (existing != null) {
			// Same path registered again - return existing ID (UVs should be identical)
			return existing;
		}
		int id = idToPath.size();
		idToPath.add(path);
		pathToId.put(path, id);
		uvMappings.put(id, uvMapping);
		return id;
	}
    
	/**
	 * Load a texture image from resources for atlas packing.
	 * Handles animated textures by checking for .mcmeta files and extracting the first frame.
	 * 
	 * @param path the texture path (e.g., "assets/textures/block/crimson_stem.png")
	 * @return the texture image (first frame for animated textures), or null if not found
	 */
	private BufferedImage loadTextureForAtlas(String path) {
		// First, load the full texture image
		BufferedImage fullImage = loadTexture(path);
		if (fullImage == null) {
			return null;
		}
		
		// Check for animation metadata (.mcmeta file)
		String mcmetaPath = path + ".mcmeta";
		try (InputStream mcmetaStream = mattmc.util.ResourceLoader.getOptionalResourceStreamFromClassLoader(mcmetaPath)) {
			if (mcmetaStream != null) {
				// Parse the .mcmeta file
				AnimationMetadataSection metadata = AnimationMetadataSection.load(mcmetaStream);
				
				if (metadata != AnimationMetadataSection.EMPTY) {
					// This is an animated texture
					// Calculate frame size
					FrameSize frameSize = metadata.calculateFrameSize(fullImage.getWidth(), fullImage.getHeight());
					
					// Check if the image is actually taller than it is wide (animated strip)
					if (fullImage.getHeight() > fullImage.getWidth() || 
						fullImage.getHeight() > frameSize.height()) {
						
						// Create animated texture data for later animation updates
						AnimatedTextureData animData = new AnimatedTextureData(path, fullImage, metadata);
						animatedTextures.put(path, animData);
						
						// Return just the first frame for atlas packing
						return animData.getFirstFrame();
					}
				}
			}
		} catch (IOException e) {
			// No .mcmeta file or error reading it - treat as static texture
			logger.debug("No .mcmeta file for {}: {}", path, e.getMessage());
		}
		
		// Return the full image for static textures
		return fullImage;
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
	 * Draw border padding around a texture tile by duplicating edge pixels.
	 * This prevents mipmap bleeding when the GPU generates lower mip levels,
	 * as the padding ensures that edge samples blend with the correct color
	 * rather than neighboring tiles in the atlas.
	 * 
	 * @param g the graphics context to draw into
	 * @param texture the source texture image
	 * @param cellX the X coordinate of the cell (top-left corner including padding)
	 * @param cellY the Y coordinate of the cell (top-left corner including padding)
	 */
	private void drawBorderPadding(Graphics2D g, BufferedImage texture, int cellX, int cellY) {
		int texWidth = Math.min(texture.getWidth(), textureSize);
		int texHeight = Math.min(texture.getHeight(), textureSize);
		
		// Calculate inner texture position
		int textureX = cellX + BORDER_PADDING;
		int textureY = cellY + BORDER_PADDING;
		
		// Draw top padding: duplicate the top row of pixels
		for (int p = 1; p <= BORDER_PADDING; p++) {
			for (int i = 0; i < texWidth; i++) {
				int pixel = texture.getRGB(i, 0);
				g.setColor(new Color(pixel, true));
				g.fillRect(textureX + i, textureY - p, 1, 1);
			}
		}
		
		// Draw bottom padding: duplicate the bottom row of pixels
		for (int p = 0; p < BORDER_PADDING; p++) {
			for (int i = 0; i < texWidth; i++) {
				int pixel = texture.getRGB(i, texHeight - 1);
				g.setColor(new Color(pixel, true));
				g.fillRect(textureX + i, textureY + texHeight + p, 1, 1);
			}
		}
		
		// Draw left padding: duplicate the left column of pixels
		for (int p = 1; p <= BORDER_PADDING; p++) {
			for (int i = 0; i < texHeight; i++) {
				int pixel = texture.getRGB(0, i);
				g.setColor(new Color(pixel, true));
				g.fillRect(textureX - p, textureY + i, 1, 1);
			}
		}
		
		// Draw right padding: duplicate the right column of pixels
		for (int p = 0; p < BORDER_PADDING; p++) {
			for (int i = 0; i < texHeight; i++) {
				int pixel = texture.getRGB(texWidth - 1, i);
				g.setColor(new Color(pixel, true));
				g.fillRect(textureX + texWidth + p, textureY + i, 1, 1);
			}
		}
		
		// Draw corner padding: duplicate corner pixels
		// Top-left corner
		int topLeftPixel = texture.getRGB(0, 0);
		g.setColor(new Color(topLeftPixel, true));
		for (int py = 1; py <= BORDER_PADDING; py++) {
			for (int px = 1; px <= BORDER_PADDING; px++) {
				g.fillRect(textureX - px, textureY - py, 1, 1);
			}
		}
		
		// Top-right corner
		int topRightPixel = texture.getRGB(texWidth - 1, 0);
		g.setColor(new Color(topRightPixel, true));
		for (int py = 1; py <= BORDER_PADDING; py++) {
			for (int px = 0; px < BORDER_PADDING; px++) {
				g.fillRect(textureX + texWidth + px, textureY - py, 1, 1);
			}
		}
		
		// Bottom-left corner
		int bottomLeftPixel = texture.getRGB(0, texHeight - 1);
		g.setColor(new Color(bottomLeftPixel, true));
		for (int py = 0; py < BORDER_PADDING; py++) {
			for (int px = 1; px <= BORDER_PADDING; px++) {
				g.fillRect(textureX - px, textureY + texHeight + py, 1, 1);
			}
		}
		
		// Bottom-right corner
		int bottomRightPixel = texture.getRGB(texWidth - 1, texHeight - 1);
		g.setColor(new Color(bottomRightPixel, true));
		for (int py = 0; py < BORDER_PADDING; py++) {
			for (int px = 0; px < BORDER_PADDING; px++) {
				g.fillRect(textureX + texWidth + px, textureY + texHeight + py, 1, 1);
			}
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
	 * 
	 * @return the number of textures with UV mappings
	 */
	public int getTextureCount() {
		return uvMappings.size();
	}
	
	/**
	 * Check if this atlas has any animated textures.
	 * 
	 * @return true if there are animated textures
	 */
	public boolean hasAnimatedTextures() {
		return !animatedTextures.isEmpty();
	}
	
	/**
	 * Get the number of animated textures in this atlas.
	 * 
	 * @return the number of animated textures
	 */
	public int getAnimatedTextureCount() {
		return animatedTextures.size();
	}
	
	/**
	 * Tick all animated textures and update the atlas.
	 * Call this once per game tick to advance animations.
	 * 
	 * <p>This method updates the OpenGL texture directly using glTexSubImage2D
	 * for efficient partial updates. Mipmaps are regenerated once after all
	 * texture updates are complete for better performance.
	 */
	public void tickAnimations() {
		if (animatedTextures.isEmpty()) {
			return;
		}
		
		boolean anyChanged = false;
		
		// Bind atlas texture once for all updates
		glBindTexture(GL_TEXTURE_2D, atlasTextureId);
		
		for (Map.Entry<String, AnimatedTextureData> entry : animatedTextures.entrySet()) {
			AnimatedTextureData animData = entry.getValue();
			if (animData.tick()) {
				// Frame changed, need to update atlas
				anyChanged = true;
				updateAnimatedTextureNoMipmap(entry.getKey(), animData);
			}
		}
		
		// Regenerate mipmaps once after all updates (more efficient than per-texture)
		if (anyChanged) {
			glGenerateMipmap(GL_TEXTURE_2D);
		}
		
		glBindTexture(GL_TEXTURE_2D, 0);
	}
	
	/**
	 * Update a single animated texture in the atlas (without mipmap regeneration).
	 * Called during tickAnimations() with the atlas already bound.
	 * Also updates the border padding to prevent mipmap bleeding.
	 */
	private void updateAnimatedTextureNoMipmap(String texturePath, AnimatedTextureData animData) {
		int[] position = textureAtlasPositions.get(texturePath);
		if (position == null) {
			return;
		}
		
		// position[0] and position[1] are the inner texture position (inside the padding)
		int textureX = position[0];
		int textureY = position[1];
		
		// Get the current frame image
		BufferedImage currentFrame;
		
		if (animData.isInterpolated()) {
			// Interpolate between current and next frame
			currentFrame = interpolateFrames(animData);
		} else {
			currentFrame = animData.getCurrentFrame();
		}
		
		if (currentFrame == null) {
			return;
		}
		
		// Upload the new frame to the atlas (texture already bound)
		uploadTextureRegionNoBindUnbind(currentFrame, textureX, textureY);
		
		// Also update the border padding to prevent mipmap bleeding
		uploadBorderPaddingNoBindUnbind(currentFrame, textureX, textureY);
	}
	
	/**
	 * Interpolate between two frames for smooth animation.
	 */
	private BufferedImage interpolateFrames(AnimatedTextureData animData) {
		BufferedImage current = animData.getCurrentFrame();
		BufferedImage next = animData.getNextFrame();
		float progress = animData.getInterpolationProgress();
		
		if (current == null || next == null || progress == 0.0f) {
			return current;
		}
		
		int width = current.getWidth();
		int height = current.getHeight();
		
		BufferedImage interpolated = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		
		for (int py = 0; py < height; py++) {
			for (int px = 0; px < width; px++) {
				int c1 = current.getRGB(px, py);
				int c2 = next.getRGB(px, py);
				
				// Interpolate each color channel
				int a1 = (c1 >> 24) & 0xFF;
				int r1 = (c1 >> 16) & 0xFF;
				int g1 = (c1 >> 8) & 0xFF;
				int b1 = c1 & 0xFF;
				
				int a2 = (c2 >> 24) & 0xFF;
				int r2 = (c2 >> 16) & 0xFF;
				int g2 = (c2 >> 8) & 0xFF;
				int b2 = c2 & 0xFF;
				
				int a = mix(progress, a1, a2);
				int r = mix(progress, r1, r2);
				int g = mix(progress, g1, g2);
				int b = mix(progress, b1, b2);
				
				interpolated.setRGB(px, py, (a << 24) | (r << 16) | (g << 8) | b);
			}
		}
		
		return interpolated;
	}
	
	/**
	 * Mix two color values based on interpolation progress.
	 */
	private int mix(float progress, int c1, int c2) {
		return (int) ((1.0f - progress) * c1 + progress * c2);
	}
	
	/**
	 * Upload a texture region to the atlas (assumes texture is already bound).
	 * Used during batch animation updates for better performance.
	 */
	private void uploadTextureRegionNoBindUnbind(BufferedImage image, int x, int y) {
		int width = Math.min(image.getWidth(), textureSize);
		int height = Math.min(image.getHeight(), textureSize);
		
		// Convert image to ByteBuffer
		int[] pixels = new int[width * height];
		image.getRGB(0, 0, width, height, pixels, 0, width);
		
		ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
		for (int py = 0; py < height; py++) {
			for (int px = 0; px < width; px++) {
				int pixel = pixels[py * width + px];
				buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red
				buffer.put((byte) ((pixel >> 8) & 0xFF));  // Green
				buffer.put((byte) (pixel & 0xFF));         // Blue
				buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha
			}
		}
		buffer.flip();
		
		// Upload to GPU (texture already bound, mipmap regeneration handled by caller)
		glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
	}
	
	/**
	 * Upload border padding for an animated texture (assumes texture is already bound).
	 * Uploads edge-duplicated pixels around the texture to prevent mipmap bleeding.
	 * 
	 * @param image the source texture image
	 * @param textureX the X coordinate of the inner texture (inside padding)
	 * @param textureY the Y coordinate of the inner texture (inside padding)
	 */
	private void uploadBorderPaddingNoBindUnbind(BufferedImage image, int textureX, int textureY) {
		int texWidth = Math.min(image.getWidth(), textureSize);
		int texHeight = Math.min(image.getHeight(), textureSize);
		
		// Upload top padding (rows above the texture)
		for (int p = 1; p <= BORDER_PADDING; p++) {
			ByteBuffer buffer = BufferUtils.createByteBuffer(texWidth * 4);
			for (int i = 0; i < texWidth; i++) {
				int pixel = image.getRGB(i, 0);
				buffer.put((byte) ((pixel >> 16) & 0xFF));
				buffer.put((byte) ((pixel >> 8) & 0xFF));
				buffer.put((byte) (pixel & 0xFF));
				buffer.put((byte) ((pixel >> 24) & 0xFF));
			}
			buffer.flip();
			glTexSubImage2D(GL_TEXTURE_2D, 0, textureX, textureY - p, texWidth, 1, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
		}
		
		// Upload bottom padding (rows below the texture)
		for (int p = 0; p < BORDER_PADDING; p++) {
			ByteBuffer buffer = BufferUtils.createByteBuffer(texWidth * 4);
			for (int i = 0; i < texWidth; i++) {
				int pixel = image.getRGB(i, texHeight - 1);
				buffer.put((byte) ((pixel >> 16) & 0xFF));
				buffer.put((byte) ((pixel >> 8) & 0xFF));
				buffer.put((byte) (pixel & 0xFF));
				buffer.put((byte) ((pixel >> 24) & 0xFF));
			}
			buffer.flip();
			glTexSubImage2D(GL_TEXTURE_2D, 0, textureX, textureY + texHeight + p, texWidth, 1, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
		}
		
		// Upload left padding (columns to the left of the texture)
		for (int p = 1; p <= BORDER_PADDING; p++) {
			ByteBuffer buffer = BufferUtils.createByteBuffer(texHeight * 4);
			for (int i = 0; i < texHeight; i++) {
				int pixel = image.getRGB(0, i);
				buffer.put((byte) ((pixel >> 16) & 0xFF));
				buffer.put((byte) ((pixel >> 8) & 0xFF));
				buffer.put((byte) (pixel & 0xFF));
				buffer.put((byte) ((pixel >> 24) & 0xFF));
			}
			buffer.flip();
			glTexSubImage2D(GL_TEXTURE_2D, 0, textureX - p, textureY, 1, texHeight, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
		}
		
		// Upload right padding (columns to the right of the texture)
		for (int p = 0; p < BORDER_PADDING; p++) {
			ByteBuffer buffer = BufferUtils.createByteBuffer(texHeight * 4);
			for (int i = 0; i < texHeight; i++) {
				int pixel = image.getRGB(texWidth - 1, i);
				buffer.put((byte) ((pixel >> 16) & 0xFF));
				buffer.put((byte) ((pixel >> 8) & 0xFF));
				buffer.put((byte) (pixel & 0xFF));
				buffer.put((byte) ((pixel >> 24) & 0xFF));
			}
			buffer.flip();
			glTexSubImage2D(GL_TEXTURE_2D, 0, textureX + texWidth + p, textureY, 1, texHeight, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
		}
		
		// Upload corner padding
		// Top-left corner
		int topLeftPixel = image.getRGB(0, 0);
		uploadCornerPadding(topLeftPixel, textureX - BORDER_PADDING, textureY - BORDER_PADDING, BORDER_PADDING, BORDER_PADDING);
		
		// Top-right corner
		int topRightPixel = image.getRGB(texWidth - 1, 0);
		uploadCornerPadding(topRightPixel, textureX + texWidth, textureY - BORDER_PADDING, BORDER_PADDING, BORDER_PADDING);
		
		// Bottom-left corner
		int bottomLeftPixel = image.getRGB(0, texHeight - 1);
		uploadCornerPadding(bottomLeftPixel, textureX - BORDER_PADDING, textureY + texHeight, BORDER_PADDING, BORDER_PADDING);
		
		// Bottom-right corner
		int bottomRightPixel = image.getRGB(texWidth - 1, texHeight - 1);
		uploadCornerPadding(bottomRightPixel, textureX + texWidth, textureY + texHeight, BORDER_PADDING, BORDER_PADDING);
	}
	
	/**
	 * Upload a corner padding region filled with a single color.
	 */
	private void uploadCornerPadding(int pixel, int x, int y, int width, int height) {
		ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
		byte r = (byte) ((pixel >> 16) & 0xFF);
		byte g = (byte) ((pixel >> 8) & 0xFF);
		byte b = (byte) (pixel & 0xFF);
		byte a = (byte) ((pixel >> 24) & 0xFF);
		
		for (int i = 0; i < width * height; i++) {
			buffer.put(r);
			buffer.put(g);
			buffer.put(b);
			buffer.put(a);
		}
		buffer.flip();
		glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
	}
}
